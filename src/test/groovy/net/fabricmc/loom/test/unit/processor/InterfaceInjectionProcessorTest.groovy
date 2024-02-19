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

package net.fabricmc.loom.test.unit.processor

import net.fabricmc.loom.test.unit.processor.classes.GenericTargetClass
import net.fabricmc.loom.test.unit.processor.classes.PassingGenericTargetClass

import java.nio.file.Path
import java.util.function.Consumer

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import spock.lang.Specification
import spock.lang.TempDir

import net.fabricmc.loom.api.processor.ProcessorContext
import net.fabricmc.loom.api.processor.SpecContext
import net.fabricmc.loom.configuration.ifaceinject.InterfaceInjectionProcessor
import net.fabricmc.loom.test.unit.processor.classes.SimpleInterface
import net.fabricmc.loom.test.unit.processor.classes.SimpleTargetClass
import net.fabricmc.loom.util.Constants
import net.fabricmc.loom.util.Pair
import net.fabricmc.loom.util.ZipUtils
import net.fabricmc.loom.util.fmj.FabricModJson
import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.tree.MemoryMappingTree

class InterfaceInjectionProcessorTest extends Specification {
	@TempDir
	Path tempDir

	def "interface injection"() {
		given:
		def fmj = Mock(FabricModJson.Mockable)
		fmj.getId() >> "modid"
		fmj.getCustom(Constants.CustomModJsonKeys.INJECTED_INTERFACE) >> createCustomObject(key, value)

		def specContext = Mock(SpecContext)
		specContext.localMods() >> [fmj]
		specContext.modDependenciesCompileRuntime() >> []

		def processorContext = Mock(ProcessorContext)
		processorContext.getMappings() >> createMappings()

		def jar = tempDir.resolve("test.jar")
		packageJar(jar)

		when:
		def processor = new TestInterfaceInjectionProcessor()
		def spec = processor.buildSpec(specContext)
		processor.processJar(jar, spec, processorContext)

		then:
		spec != null

		withTargetClass(jar, target, validator)

		where:
		key | value | target | validator
		// Simple class with a simple interface
		"class_1" | "net/fabricmc/loom/test/unit/processor/classes/SimpleInterface" | SimpleTargetClass.class | { Class<?> loadedClass ->
			loadedClass.interfaces.first().name == "net/fabricmc/loom/test/unit/processor/classes/SimpleInterface"
			loadedClass.constructors.first().newInstance().injectedMethod() == 123
		}

		// Inner class with a simple interface
		"class_1\$class_2" | "net/fabricmc/loom/test/unit/processor/classes/SimpleInterface" | SimpleTargetClass.Inner.class | { Class<?> loadedClass ->
			loadedClass.interfaces.first().name == "net/fabricmc/loom/test/unit/processor/classes/SimpleInterface"
			loadedClass.constructors.first().newInstance().injectedMethod() == 123
		}

		"class_3" | "net/fabricmc/loom/test/unit/processor/classes/GenericInterface<Ljava/lang/String;>" | GenericTargetClass.class { Class<?> loadedClass ->
			loadedClass.interfaces.first().name == "net/fabricmc/loom/test/unit/processor/classes/GenericInterface"
			loadedClass.constructors.first().newInstance().genericInjectedMethod() == null
		}

		"class_4" | "net/fabricmc/loom/test/unit/processor/classes/GenericInterface<TT;>" | PassingGenericTargetClass.class { Class<?> loadedClass ->
			loadedClass.interfaces.first().name == "net/fabricmc/loom/test/unit/processor/classes/GenericInterface"
			loadedClass.constructors.first().newInstance().genericInjectedMethod() == null
		}
	}

	def "nothing to inject"() {
		given:
		def specContext = Mock(SpecContext)
		specContext.localMods() >> []
		specContext.modDependenciesCompileRuntime() >> []

		when:
		def processor = new TestInterfaceInjectionProcessor()
		def spec = processor.buildSpec(specContext)

		then:
		spec == null
	}

	// Create the custom FMJ entry for the injected interface
	static JsonObject createCustomObject(String key, String value) {
		def jsonObject = new JsonObject()
		def jsonArray = new JsonArray()
		jsonArray.add(value)
		jsonObject.add(key, jsonArray)
		return jsonObject
	}

	// Package the test classes into a jar file
	static void packageJar(Path path) {
		def entries = CLASSES_TO_PACKAGE.collect {
			def entryName = it.name.replace('.', '/') + ".class"
			new Pair(entryName, getClassBytes(it))
		}

		ZipUtils.add(path, entries)
	}

	static MemoryMappingTree createMappings() {
		def mappings = new MemoryMappingTree()
		new StringReader(MAPPINGS).withCloseable {
			MappingReader.read(it, mappings)
		}
		return mappings
	}

	// Load a class from a jar file and execute a closure with it
	static void withTargetClass(Path jar, Class<?> clazz, Consumer<Class<?>> closure) {
		// Groovy is needed as the test classes are compiled with it
		URL[] urls = [
			jar.toUri().toURL(),
			GroovyObject.class.protectionDomain.codeSource.location
		]

		new URLClassLoader("InterfaceInjectionTest", urls, null).withCloseable {
			def loadedClass = Class.forName(clazz.name, true, it)
			closure(loadedClass)
		}
	}

	static byte[] getClassBytes(Class<?> clazz) {
		return clazz.classLoader.getResourceAsStream(clazz.name.replace('.', '/') + ".class").withCloseable {
			it.bytes
		}
	}

	private class TestInterfaceInjectionProcessor extends InterfaceInjectionProcessor {
		TestInterfaceInjectionProcessor() {
			super("InterfaceInjection", true)
		}
	}

	private static final List<Class<?>> CLASSES_TO_PACKAGE = [
		SimpleTargetClass.class,
		SimpleTargetClass.Inner.class,
		SimpleInterface.class
	]
	private static final String MAPPINGS = """
tiny\t2\t0\tintermediary\tnamed
c\tclass_1\tnet/fabricmc/loom/test/unit/processor/classes/SimpleTargetClass
c\tclass_1\$class_2\tnet/fabricmc/loom/test/unit/processor/classes/SimpleTargetClass\$Inner
c\tclass_3\tnet/fabricmc/loom/test/unit/processor/classes/GenericTargetClass
c\tclass_4\tnet/fabricmc/loom/test/unit/processor/classes/PassingGenericTargetClass
""".trim()
}
