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

package net.fabricmc.loom.test.unit.nativeplatform

import java.nio.file.Path

import spock.lang.Requires
import spock.lang.Specification
import spock.lang.TempDir

import net.fabricmc.loom.util.nativeplatform.EncryptionKeyStore
import net.fabricmc.loom.util.nativeplatform.EncryptionKeyStoreFactory

class EncryptionKeyStoreFactoryTest extends Specification {
	@TempDir
	Path tempDir

	def "uses a stable key name for a normalized account path"() {
		expect:
		EncryptionKeyStoreFactory.keyNameFor(tempDir.resolve("nested/../microsoft-auth.json")) ==
				EncryptionKeyStoreFactory.keyNameFor(tempDir.resolve("microsoft-auth.json"))
	}

	def "uses distinct key names for distinct Gradle user homes"() {
		expect:
		EncryptionKeyStoreFactory.keyNameFor(tempDir.resolve("first/caches/fabric-loom/microsoft-auth.json")) !=
				EncryptionKeyStoreFactory.keyNameFor(tempDir.resolve("second/caches/fabric-loom/microsoft-auth.json"))
	}

	@Requires({
		os.linux
	})
	def "uses the fallback key store on Linux"() {
		expect:
		EncryptionKeyStoreFactory.create(tempDir.resolve("microsoft-auth.json")).is(EncryptionKeyStore.FALLBACK)
	}

	@Requires({
		os.windows
	})
	def "uses the same key name for differently cased Windows paths"() {
		expect:
		EncryptionKeyStoreFactory.keyNameFor(tempDir.resolve("Fabric-Loom/Microsoft-Auth.json")) ==
				EncryptionKeyStoreFactory.keyNameFor(tempDir.resolve("fabric-loom/microsoft-auth.json"))
	}
}
