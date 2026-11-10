/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.test.unit.download

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir
import spock.util.concurrent.PollingConditions

import net.fabricmc.loom.util.download.DownloadCache
import net.fabricmc.loom.util.download.DownloadException
import net.fabricmc.loom.util.download.DownloadResult
import net.fabricmc.loom.util.download.DownloadService

class DownloadCacheTest extends Specification {
	@TempDir
	Path directory

	def "Projects share a build service without sharing state across builds"() {
		given:
		def root = ProjectBuilder.builder().withProjectDir(directory.resolve("root").toFile()).build()
		def child = ProjectBuilder.builder().withParent(root).withName("child").build()
		def otherBuild = ProjectBuilder.builder().withProjectDir(directory.resolve("other").toFile()).build()

		expect:
		DownloadService.get(root).cache.is(DownloadService.get(child).cache)
		!DownloadService.get(root).cache.is(DownloadService.get(otherBuild).cache)
	}

	def "Concurrent requests share one successful operation"() {
		given:
		def cache = new DownloadCache()
		def output = directory.resolve("file")
		def entered = new CountDownLatch(1)
		def release = new CountDownLatch(1)
		def calls = new AtomicInteger()
		def result = { true } as DownloadResult
		def results = (0..<8).collect { new CompletableFuture<DownloadResult>() }
		def threads = results.collect { future ->
			Thread.ofPlatform().unstarted {
				try {
					future.complete(cache.download(output, "sha1:hash", false, false) {
						calls.incrementAndGet()
						entered.countDown()
						assert release.await(10, TimeUnit.SECONDS)
						Files.writeString(output, "content")

						result
					})
				} catch (Throwable failure) {
					future.completeExceptionally(failure)
				}
			}
		}

		when:
		threads.each { it.start() }
		assert entered.await(10, TimeUnit.SECONDS)
		new PollingConditions(timeout: 5).eventually {
			assert threads.every {
				it.state in [
					Thread.State.BLOCKED,
					Thread.State.WAITING,
					Thread.State.TIMED_WAITING
				]
			}
		}
		release.countDown()

		then:
		results.every { it.get(10, TimeUnit.SECONDS).is(result) }

		calls.get() == 1

		cleanup:
		release.countDown()
		threads.each { it.join(10000) }
	}

	def "A waiting request retries after the previous operation fails"() {
		given:
		def cache = new DownloadCache()
		def output = directory.resolve("file")
		def firstEntered = new CountDownLatch(1)
		def release = new CountDownLatch(1)
		def secondRequested = new CountDownLatch(1)
		def failure = new DownloadException("Verification failed")
		def result = { true } as DownloadResult
		def first = CompletableFuture.supplyAsync {
			try {
				return cache.download(output, "sha1:hash", false, false) {
					firstEntered.countDown()
					assert release.await(10, TimeUnit.SECONDS)
					throw failure
				}
			} catch (DownloadException e) {
				throw new CompletionException(e)
			}
		}
		assert firstEntered.await(10, TimeUnit.SECONDS)
		def second = CompletableFuture.supplyAsync {
			secondRequested.countDown()
			cache.download(output, "sha1:hash", false, false) {
				Files.writeString(output, "valid")
				result
			}
		}
		assert secondRequested.await(10, TimeUnit.SECONDS)

		when:
		release.countDown()
		first.get(10, TimeUnit.SECONDS)

		then:
		def exception = thrown(ExecutionException)
		exception.cause.is(failure)
		second.get(10, TimeUnit.SECONDS).is(result)

		cleanup:
		release.countDown()
	}

	def "Successful verification is reused until the next build or file deletion"() {
		given:
		def cache = new DownloadCache()
		def output = directory.resolve("file")
		Files.writeString(output, "original")
		Files.setLastModifiedTime(output, FileTime.from(Instant.now().minusSeconds(10)))
		def calls = new AtomicInteger()
		def verify = {
			calls.incrementAndGet()
			return { false } as DownloadResult
		}

		when:
		cache.download(output, "sha1:hash", false, false, verify)
		cache.download(output, "sha1:hash", false, false, verify)

		then:
		calls.get() == 1

		when:
		def modified = Files.getLastModifiedTime(output)
		Files.writeString(output, "modified")
		Files.setLastModifiedTime(output, modified)
		cache.download(output, "sha1:hash", false, false, verify)

		then:
		calls.get() == 1

		when:
		def replacement = directory.resolve("replacement")
		Files.writeString(replacement, "replaced")
		Files.setLastModifiedTime(replacement, modified)
		Files.move(replacement, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
		cache.download(output, "sha1:hash", false, false, verify)

		then:
		calls.get() == 1

		when:
		new DownloadCache().download(output, "sha1:hash", false, false, verify)

		then:
		calls.get() == 2

		when:
		Files.delete(output)
		cache.download(output, "sha1:hash", false, false, verify)

		then:
		calls.get() == 3
	}

	def "Failed operations can be retried"() {
		given:
		def cache = new DownloadCache()
		def output = directory.resolve("file")

		when:
		cache.download(output, "sha1:hash", false, false) {
			throw new DownloadException("Verification failed")
		}

		then:
		thrown DownloadException

		when:
		def result = cache.download(output, "sha1:hash", false, false) {
			Files.writeString(output, "valid")
			return { true } as DownloadResult
		}

		then:
		result.didDownload()
	}

	def "Refresh is deduplicated independently of normal verification and build lifetime"() {
		given:
		def cache = new DownloadCache()
		def output = directory.resolve("file")
		Files.writeString(output, "valid")
		Files.setLastModifiedTime(output, FileTime.from(Instant.now().minusSeconds(10)))
		def calls = new AtomicInteger()
		def verify = {
			calls.incrementAndGet()
			return { false } as DownloadResult
		}

		when:
		cache.download(output, "sha1:hash", false, false, verify)
		cache.download(output, "sha1:hash", true, false, verify)
		cache.download(output, "sha1:hash", true, false, verify)

		then:
		calls.get() == 2

		when:
		cache.download(output, "sha1:other", false, false, verify)
		new DownloadCache().download(output, "sha1:hash", true, false, verify)

		then:
		calls.get() == 4
	}

	def "Another download invalidates previous verification (#hash)"() {
		given:
		def cache = new DownloadCache()
		def output = directory.resolve("file")
		Files.writeString(output, "original")
		def calls = new AtomicInteger()
		def verify = {
			calls.incrementAndGet()
			return { false } as DownloadResult
		}
		cache.download(output, "sha1:hash", false, false, verify)

		when:
		cache.download(output, hash, false, false) {
			Files.writeString(output, "replacement")
			return { true } as DownloadResult
		}
		cache.download(output, "sha1:hash", false, false, verify)

		then:
		calls.get() == 2

		where:
		hash << [null, "sha1:other"]
	}

	def "A failed refresh invalidates previous verification"() {
		given:
		def cache = new DownloadCache()
		def output = directory.resolve("file")
		Files.writeString(output, "original")
		def calls = new AtomicInteger()
		def verify = {
			calls.incrementAndGet()
			return { false } as DownloadResult
		}
		cache.download(output, "sha1:hash", false, false, verify)

		when:
		cache.download(output, "sha1:hash", true, false) {
			throw new DownloadException("Refresh failed")
		}

		then:
		thrown DownloadException

		when:
		cache.download(output, "sha1:hash", false, false, verify)

		then:
		calls.get() == 2
	}

	def "Verification does not depend on timestamp precision"() {
		given:
		def cache = new DownloadCache()
		def output = directory.resolve("file")
		Files.writeString(output, "valid")
		// A future timestamp also covers a clock moving backwards.
		Files.setLastModifiedTime(output, FileTime.from(Instant.now().plusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.SECONDS)))
		def calls = new AtomicInteger()
		def verify = {
			calls.incrementAndGet()
			return { false } as DownloadResult
		}

		when:
		cache.download(output, "sha1:hash", false, false, verify)
		cache.download(output, "sha1:hash", false, false, verify)

		then:
		calls.get() == 1
	}

	def "An abandoned download marker invalidates success"() {
		given:
		def cache = new DownloadCache()
		def output = directory.resolve("file")
		Files.writeString(output, "valid")
		Files.setLastModifiedTime(output, FileTime.from(Instant.now().minusSeconds(10)))
		def calls = new AtomicInteger()
		def verify = {
			calls.incrementAndGet()
			return { false } as DownloadResult
		}
		cache.download(output, "sha1:hash", false, false, verify)
		Files.createFile(directory.resolve("file.lock"))

		when:
		cache.download(output, "sha1:hash", false, false, verify)

		then:
		calls.get() == 2
	}

	def "Unrelated files can be downloaded concurrently"() {
		given:
		def cache = new DownloadCache()
		def entered = new CountDownLatch(2)
		def release = new CountDownLatch(1)
		def downloads = ["first", "second"].collect { name ->
			CompletableFuture.runAsync {
				cache.download(directory.resolve(name), "sha1:hash", false, false) {
					entered.countDown()
					assert release.await(10, TimeUnit.SECONDS)
					return { true } as DownloadResult
				}
			}
		}

		expect:
		entered.await(5, TimeUnit.SECONDS)

		cleanup:
		release.countDown()
		downloads.each { it.get(10, TimeUnit.SECONDS) }
	}

	def "Separate cache instances serialize mutations of the same physical file"() {
		given:
		def output = directory.resolve("file")
		def firstEntered = new CountDownLatch(1)
		def release = new CountDownLatch(1)
		def secondEntered = new CountDownLatch(1)
		def first = CompletableFuture.runAsync {
			new DownloadCache().download(output, "sha1:first", false, false) {
				firstEntered.countDown()
				assert release.await(10, TimeUnit.SECONDS)
				return { true } as DownloadResult
			}
		}
		assert firstEntered.await(10, TimeUnit.SECONDS)
		def second = CompletableFuture.runAsync {
			new DownloadCache().download(directory.resolve("sub/../file"), "sha1:second", false, false) {
				secondEntered.countDown()
				return { true } as DownloadResult
			}
		}

		expect:
		!secondEntered.await(100, TimeUnit.MILLISECONDS)

		when:
		release.countDown()
		first.get(10, TimeUnit.SECONDS)
		second.get(10, TimeUnit.SECONDS)

		then:
		secondEntered.count == 0

		cleanup:
		release.countDown()
	}
}
