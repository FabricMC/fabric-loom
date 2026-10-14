/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

import java.net.http.HttpTimeoutException
import java.nio.file.AccessDeniedException
import java.security.KeyStore
import java.security.cert.CertPathBuilderException
import java.security.cert.CertPathValidatorException
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import javax.net.ssl.SSLHandshakeException

import io.javalin.Javalin
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.util.ssl.SslContextFactory
import spock.lang.Specification

import net.fabricmc.loom.test.util.CertificateUtils
import net.fabricmc.loom.util.download.Download
import net.fabricmc.loom.util.download.DownloadException
import net.fabricmc.loom.util.download.DownloadExecutor
import net.fabricmc.loom.util.download.DownloadProgressListener

class DownloadFailureTest extends Specification {
	def "Untrusted HTTPS certificate reports certificate guidance"() {
		given:
		def keyPair = CertificateUtils.generateKeyPair()
		def certificate = CertificateUtils.createCert(keyPair, "CN=localhost")

		def keyStore = KeyStore.getInstance("PKCS12")
		keyStore.load(null, null)
		keyStore.setKeyEntry("localhost", keyPair.private, "password".toCharArray(), [certificate] as Certificate[])

		def sslContext = new SslContextFactory.Server()
		sslContext.setKeyStore(keyStore)
		sslContext.setKeyStorePassword("password")

		def server = Javalin.create { config ->
			config.jetty.addConnector { jettyServer, httpConfig ->
				def connector = new ServerConnector(jettyServer, sslContext)
				connector.setHost("127.0.0.1")
				connector.setPort(0)

				return connector
			}
		}.start()

		def url = "https://127.0.0.1:${server.port()}/client.jar"

		when:
		Download.create(url).downloadString()

		then:
		def exception = thrown(DownloadException)

		exception.cause.cause instanceof SSLHandshakeException
		exception.message.contains("Failed to download $url after 3 attempts:")
		exception.message.contains("trust certificates")
		exception.message.contains(System.getProperty("java.home"))

		cleanup:
		server?.stop()
	}

	def "Retry errors expose the underlying failure and only suggest certificate fixes for certificate errors"() {
		given:
		def originalFailure = new DownloadException("Request failed", failure)
		int attempts = 0
		def progress = new DownloadProgressListener() {
					@Override
					void onStart() {
						attempts++
						throw originalFailure
					}

					@Override
					void onProgress(long bytesTransferred, long contentLength) {
					}

					@Override
					void onEnd() {
					}
				}

		when:
		Download.create("https://example.invalid/client.jar").progress(progress).downloadString()

		then:
		def exception = thrown(DownloadException)
		attempts == 3
		exception.message.contains("Failed to download https://example.invalid/client.jar after 3 attempts:")
		exception.message.contains((failure.cause ?: failure).message)
		exception.cause.is(originalFailure)
		exception.message.contains("trust certificates") == certificateFailure
		!certificateFailure || exception.message.contains(System.getProperty("java.home"))

		where:
		failure | certificateFailure
		new SSLHandshakeException("Handshake failed").initCause(new CertificateException("Untrusted certificate")) | true
		new CertPathBuilderException("Unable to find a certificate path") | true
		new CertPathValidatorException("Certificate has expired") | true
		new SSLHandshakeException("Unsupported TLS protocol") | false
		new AccessDeniedException("client.jar") | false
		new HttpTimeoutException("Request timed out") | false
		new InterruptedException("Download interrupted") | false
	}

	def "Offline errors expose the URL without certificate advice"() {
		when:
		Download.create("https://example.invalid/client.jar").offline().downloadString()

		then:
		def exception = thrown(DownloadException)
		exception.message.contains("https://example.invalid/client.jar")
		exception.message.contains("offline mode")
		!exception.message.contains("trust certificates")
	}

	def "Parallel download errors retain the original failure ahead of cancelled siblings"() {
		given:
		def siblingStarted = new CountDownLatch(1)
		def waitForCancellation = new CountDownLatch(1)
		def originalFailure = new DownloadException("Failed to download https://example.invalid/client.jar: connection refused")
		def executor = new DownloadExecutor(2)
		executor.runAsync {
			if (!siblingStarted.await(10, TimeUnit.SECONDS)) {
				throw new DownloadException("Sibling download did not start")
			}

			throw originalFailure
		}
		executor.runAsync {
			siblingStarted.countDown()

			try {
				waitForCancellation.await(10, TimeUnit.SECONDS)
			} catch (InterruptedException exception) {
				throw new DownloadException("Sibling download cancelled", exception)
			}

			throw new DownloadException("Sibling download was not cancelled")
		}

		when:
		executor.close()

		then:
		def exception = thrown(DownloadException)
		exception.message == originalFailure.message
		exception.cause.is(originalFailure)
		exception.suppressed.length == 1
		exception.suppressed[0].message == "Sibling download cancelled"
		exception.suppressed[0].cause instanceof InterruptedException
	}
}
