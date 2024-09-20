/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 FabricMC
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

package net.fabricmc.loom.test.unit

import java.nio.file.Files
import java.nio.file.Path

import spock.lang.TempDir

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace
import net.fabricmc.loom.configuration.providers.mappings.IntermediateMappingsService
import net.fabricmc.loom.configuration.providers.mappings.tiny.MappingsMerger
import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch
import net.fabricmc.mappingio.tree.MemoryMappingTree

import static org.junit.jupiter.api.Assertions.*

class MappingsMergerTest {
	@TempDir
	Path tempDir

	def "mappings merger"() {
		given:
		Path intermediaryTiny = tempDir.resolve("intermediary.tiny")
		Path mappingsTiny = tempDir.resolve("mappings.tiny")
		Path mergedMappingsTiny = tempDir.resolve("merged_mappings.tiny")

		Files.writeString(intermediaryTiny, INTERMEDIARY_MAPPINGS)
		Files.writeString(mappingsTiny, NAMED_MAPPINGS)

		IntermediateMappingsService.Options intermediateMappingsServiceOptions = LoomMocks.intermediateMappingsServiceOptionsMock(intermediaryTiny, OFFICIAL)
		IntermediateMappingsService intermediateMappingsService = LoomMocks.intermediateMappingsServiceMock(intermediateMappingsServiceOptions)

		when:
		MappingsMerger.mergeAndSaveMappings(mappingsTiny, mergedMappingsTiny, intermediateMappingsService)

		def mappings = new MemoryMappingTree()
		MappingReader.read(mergedMappingsTiny, mappings)

		then:
		mappings.srcNamespace == OFFICIAL
		mappings.dstNamespaces == [INTERMEDIARY, NAMED]
		def namedNs = mappings.getNamespaceId(NAMED)
		mappings.classes.size() == 2
		mappings.classes[0].srcName == "a"
		mappings.classes[0].getDstName(namedNs) == "net/fabricmc/loom/test/unit/ObfuscatedClass"
		mappings.classes[0].comment == "class comment"
		mappings.classes[0].fields.size() == 1
		mappings.classes[0].fields[0].srcName == "a"
		mappings.classes[0].fields[0].getDstDesc(namedNs) == "obfuscatedField"
		mappings.classes[0].fields[0].comment == "field comment"
		mappings.classes[0].methods.size() == 1
		mappings.classes[0].methods[0].srcName == "a"
		mappings.classes[0].methods[0].getDstDesc(namedNs) == "obfuscatedMethod"
		mappings.classes[0].methods[0].comment == "method comment"
		mappings.classes[0].methods[0].args.size() == 1
		mappings.classes[0].methods[1].args[0].getDstName(namedNs) == "obfuscatedMethodParameter"
		mappings.classes[1].srcName == "net/fabricmc/loom/test/unit/UnobfuscatedClass"
		mappings.classes[1].getDstName(namedNs) == "net/fabricmc/loom/test/unit/UnobfuscatedClass"
		mappings.classes[1].comment == "class comment"
		mappings.classes[1].fields.size() == 1
		mappings.classes[1].fields[0].srcName == "unobfuscatedField"
		mappings.classes[1].fields[0].getDstDesc(namedNs) == "unobfuscatedField"
		mappings.classes[1].fields[0].comment == "field comment"
		mappings.classes[1].methods.size() == 1
		mappings.classes[1].methods[0].srcName == "unobfuscatedMethod"
		mappings.classes[1].methods[0].getDstDesc(namedNs) == "unobfuscatedMethod"
		mappings.classes[1].methods[0].comment == "method comment"
		mappings.classes[1].methods[0].args.size() == 1
		mappings.classes[1].methods[1].args[0].getDstName(namedNs) == "unobfuscatedMethodParameter"
	}

	def "mappings merger legacy"() {
		given:
		Path intermediaryTiny = tempDir.resolve("intermediary.tiny")
		Path mappingsTiny = tempDir.resolve("mappings.tiny")
		Path mergedMappingsTiny = tempDir.resolve("merged_mappings.tiny")

		Files.writeString(intermediaryTiny, LEGACY_INTERMEDIARY_MAPPINGS)
		Files.writeString(mappingsTiny, LEGACY_NAMED_MAPPINGS)

		IntermediateMappingsService.Options intermediateMappingsServiceOptions = LoomMocks.intermediateMappingsServiceOptionsMock(intermediaryTiny, INTERMEDIARY)
		IntermediateMappingsService intermediateMappingsService = LoomMocks.intermediateMappingsServiceMock(intermediateMappingsServiceOptions)

		when:
		MappingsMerger.legacyMergeAndSaveMappings(mappingsTiny, mergedMappingsTiny, intermediateMappingsService)

		def mappings = new MemoryMappingTree()
		MappingReader.read(mergedMappingsTiny, mappings)

		def clientMappings = new MemoryMappingTree()
		def serverMappings = new MemoryMappingTree()

		mappings.accept(new MappingSourceNsSwitch(clientMappings, CLIENT_OFFICIAL, true))
		mappings.accept(new MappingSourceNsSwitch(serverMappings, SERVER_OFFICIAL, true))

		then:
		clientMappings.srcNamespace == CLIENT_OFFICIAL
		clientMappings.dstNamespaces == [
			INTERMEDIARY,
			SERVER_OFFICIAL,
			NAMED
		]
		def clientNamedNs = clientMappings.getNamespaceId(NAMED)
		clientMappings.classes.size() == 3
		clientMappings.classes[0].srcName == "a"
		clientMappings.classes[0].getDstName(namedNs) == "net/fabricmc/loom/test/unit/CommonObfuscatedClass"
		clientMappings.classes[0].comment == "class comment"
		clientMappings.classes[0].fields.size() == 1
		clientMappings.classes[0].fields[0].srcName == "a"
		clientMappings.classes[0].fields[0].getDstDesc(namedNs) == "commonObfuscatedField"
		clientMappings.classes[0].fields[0].comment == "field comment"
		clientMappings.classes[0].methods.size() == 1
		clientMappings.classes[0].methods[0].srcName == "a"
		clientMappings.classes[0].methods[0].getDstDesc(namedNs) == "commonObfuscatedMethod"
		clientMappings.classes[0].methods[0].comment == "method comment"
		clientMappings.classes[0].methods[0].args.size() == 1
		clientMappings.classes[0].methods[1].args[0].getDstName(namedNs) == "commonObfuscatedMethodParameter"
		clientMappings.classes[1].srcName == "b"
		clientMappings.classes[1].getDstName(namedNs) == "net/fabricmc/loom/test/unit/ClientObfuscatedClass"
		clientMappings.classes[1].comment == "class comment"
		clientMappings.classes[1].fields.size() == 1
		clientMappings.classes[1].fields[0].srcName == "a"
		clientMappings.classes[1].fields[0].getDstDesc(namedNs) == "clientObfuscatedField"
		clientMappings.classes[1].fields[0].comment == "field comment"
		clientMappings.classes[1].methods.size() == 1
		clientMappings.classes[1].methods[0].srcName == "a"
		clientMappings.classes[1].methods[0].getDstDesc(namedNs) == "clientObfuscatedMethod"
		clientMappings.classes[1].methods[0].comment == "method comment"
		clientMappings.classes[1].methods[0].args.size() == 1
		clientMappings.classes[1].methods[1].args[0].getDstName(namedNs) == "clientObfuscatedMethodParameter"
		clientMappings.classes[2].srcName == "net/fabricmc/loom/test/unit/UnobfuscatedClass"
		clientMappings.classes[2].getDstName(namedNs) == "net/fabricmc/loom/test/unit/UnobfuscatedClass"
		clientMappings.classes[2].comment == "class comment"
		clientMappings.classes[2].fields.size() == 1
		clientMappings.classes[2].fields[0].srcName == "unobfuscatedField"
		clientMappings.classes[2].fields[0].getDstDesc(namedNs) == "unobfuscatedField"
		clientMappings.classes[2].fields[0].comment == "field comment"
		clientMappings.classes[2].methods.size() == 1
		clientMappings.classes[2].methods[0].srcName == "unobfuscatedMethod"
		clientMappings.classes[2].methods[0].getDstDesc(namedNs) == "unobfuscatedMethod"
		clientMappings.classes[2].methods[0].comment == "method comment"
		clientMappings.classes[2].methods[0].args.size() == 1
		clientMappings.classes[2].methods[1].args[0].getDstName(namedNs) == "unobfuscatedMethodParameter"

		serverMappings.srcNamespace == SERVER_OFFICIAL
		serverMappings.dstNamespaces == [
			INTERMEDIARY,
			CLIENT_OFFICIAL,
			NAMED
		]
		def serverNamedNs = serverMappings.getNamespaceId(NAMED)
		serverMappings.classes.size() == 3
		serverMappings.classes[0].srcName == "a"
		serverMappings.classes[0].getDstName(namedNs) == "net/fabricmc/loom/test/unit/CommonObfuscatedClass"
		serverMappings.classes[0].comment == "class comment"
		serverMappings.classes[0].fields.size() == 1
		serverMappings.classes[0].fields[0].srcName == "a"
		serverMappings.classes[0].fields[0].getDstDesc(namedNs) == "commonObfuscatedField"
		serverMappings.classes[0].fields[0].comment == "field comment"
		serverMappings.classes[0].methods.size() == 1
		serverMappings.classes[0].methods[0].srcName == "a"
		serverMappings.classes[0].methods[0].getDstDesc(namedNs) == "commonObfuscatedMethod"
		serverMappings.classes[0].methods[0].comment == "method comment"
		serverMappings.classes[0].methods[0].args.size() == 1
		serverMappings.classes[0].methods[1].args[0].getDstName(namedNs) == "commonObfuscatedMethodParameter"
		serverMappings.classes[1].srcName == "b"
		serverMappings.classes[1].getDstName(namedNs) == "net/fabricmc/loom/test/unit/ClientObfuscatedClass"
		serverMappings.classes[1].comment == "class comment"
		serverMappings.classes[1].fields.size() == 1
		serverMappings.classes[1].fields[0].srcName == "a"
		serverMappings.classes[1].fields[0].getDstDesc(namedNs) == "clientObfuscatedField"
		serverMappings.classes[1].fields[0].comment == "field comment"
		serverMappings.classes[1].methods.size() == 1
		serverMappings.classes[1].methods[0].srcName == "a"
		serverMappings.classes[1].methods[0].getDstDesc(namedNs) == "clientObfuscatedMethod"
		serverMappings.classes[1].methods[0].comment == "method comment"
		serverMappings.classes[1].methods[0].args.size() == 1
		serverMappings.classes[1].methods[1].args[0].getDstName(namedNs) == "clientObfuscatedMethodParameter"
		serverMappings.classes[2].srcName == "net/fabricmc/loom/test/unit/UnobfuscatedClass"
		serverMappings.classes[2].getDstName(namedNs) == "net/fabricmc/loom/test/unit/UnobfuscatedClass"
		serverMappings.classes[2].comment == "class comment"
		serverMappings.classes[2].fields.size() == 1
		serverMappings.classes[2].fields[0].srcName == "unobfuscatedField"
		serverMappings.classes[2].fields[0].getDstDesc(namedNs) == "unobfuscatedField"
		serverMappings.classes[2].fields[0].comment == "field comment"
		serverMappings.classes[2].methods.size() == 1
		serverMappings.classes[2].methods[0].srcName == "unobfuscatedMethod"
		serverMappings.classes[2].methods[0].getDstDesc(namedNs) == "unobfuscatedMethod"
		serverMappings.classes[2].methods[0].comment == "method comment"
		serverMappings.classes[2].methods[0].args.size() == 1
		serverMappings.classes[2].methods[1].args[0].getDstName(namedNs) == "unobfuscatedMethodParameter"
	}

	private static final String OFFICIAL = MappingsNamespace.OFFICIAL.toString()
	private static final String CLIENT_OFFICIAL = MappingsNamespace.CLIENT_OFFICIAL.toString()
	private static final String SERVER_OFFICIAL = MappingsNamespace.SERVER_OFFICIAL.toString()
	private static final String INTERMEDIARY = MappingsNamespace.INTERMEDIARY.toString()
	private static final String NAMED = MappingsNamespace.NAMED.toString()

	private static final String INTERMEDIARY_MAPPINGS = """
tiny\t2\t0\tofficial\tintermediary
c\ta\tclass_1
\tf\tZ\ta\tfield_1
\tm\t(Z)V\ta\tmethod_1
""".trim()
	private static final String NAMED_MAPPINGS = """
tiny\t2\t0\tintermediary\tnamed
c\tclass_1\tnet/fabricmc/loom/test/unit/ObfuscatedClass
\tc\tclass comment
\tf\tZ\tfield_1\tobfuscatedField
\t\tc\tfield comment
\tm\t(Z)V\tmethod_1\tobfuscatedMethod
\t\tc\tmethod comment
\t\tp\t0\t\t\tobfuscatedMethodParameter
c\tnet/fabricmc/loom/test/unit/UnobfuscatedClass\tnet/fabricmc/loom/test/unit/UnobfuscatedClass
\tc\tclass comment
\tf\tZ\tunobfuscatedField\tunobfuscatedField
\t\tc\tfield comment
\tm\t(Z)V\tunobfuscatedMethod\tunobfuscatedMethod
\t\tc\tmethod comment
\t\tp\t0\t\t\tunobfuscatedMethodParameter
""".trim()

	private static final String LEGACY_INTERMEDIARY_MAPPINGS = """
tiny\t2\t0\tintermediary\tclientOfficial\tserverOfficial
c\tclass_1\ta\ta
\tf\tZ\tfield_1\ta\ta
\tm\t(Z)V\tmethod_1\ta\ta
c\tclass_2\tc\t
\tf\tZ\tfield_2\ta\t
\tm\t(Z)V\tmethod_2\ta\t
c\tclass_3\t\tc
\tf\tZ\tfield_3\t\ta
\tm\t(Z)V\tmethod_3\t\ta
""".trim()
	private static final String LEGACY_NAMED_MAPPINGS = """
tiny\t2\t0\tintermediary\tnamed
c\tclass_1\tnet/fabricmc/loom/test/unit/CommonObfuscatedClass
\tc\tclass comment
\tf\tZ\tfield_1\tcommonObfuscatedField
\t\tc\tfield comment
\tm\t(Z)V\tmethod_1\tcommonObfuscatedMethod
\t\tc\tmethod comment
\t\tp\t0\t\t\tcommonObfuscatedMethodParameter
c\tclass_2\tnet/fabricmc/loom/test/unit/ClientObfuscatedClass
\tc\tclass comment
\tf\tZ\tfield_2\tclientObfuscatedField
\t\tc\tfield comment
\tm\t(Z)V\tmethod_2\tclientObfuscatedMethod
\t\tc\tmethod comment
\t\tp\t0\t\t\tclientObfuscatedMethodParameter
c\tclass_3\tnet/fabricmc/loom/test/unit/ServerObfuscatedClass
\tc\tclass comment
\tf\tZ\tfield_3\tserverObfuscatedField
\t\tc\tfield comment
\tm\t(Z)V\tmethod_3\tserverObfuscatedMethod
\t\tc\tmethod comment
\t\tp\t0\t\t\tserverObfuscatedMethodParameter
c\tnet/fabricmc/loom/test/unit/UnobfuscatedClass\tnet/fabricmc/loom/test/unit/UnobfuscatedClass
\tc\tclass comment
\tf\tZ\tunobfuscatedField\tunobfuscatedField
\t\tc\tfield comment
\tm\t(Z)V\tunobfuscatedMethod\tunobfuscatedMethod
\t\tc\tmethod comment
\t\tp\t0\t\t\tunobfuscatedMethodParameter
""".trim()
}
