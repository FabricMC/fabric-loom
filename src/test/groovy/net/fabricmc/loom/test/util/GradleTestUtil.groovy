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

package net.fabricmc.loom.test.util

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.tasks.DefaultSourceSet
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.util.PatternFilterable
import org.jetbrains.annotations.Nullable
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

import net.fabricmc.loom.LoomGradleExtension
import net.fabricmc.loom.extension.LoomFiles
import net.fabricmc.loom.test.LoomTestConstants
import net.fabricmc.loom.util.download.Download

import static org.mockito.ArgumentMatchers.any
import static org.mockito.Mockito.*

class GradleTestUtil {
	static <T> Property<T> mockProperty(T value) {
		def mock = mock(Property.class)
		when(mock.get()).thenReturn(Objects.requireNonNull(value))
		when(mock.isPresent()).thenReturn(true)
		return mock
	}

	static SourceSet mockSourceSet(String name) {
		def sourceSet = new DefaultSourceSet(name, mockObjectFactory()) {
					final ExtensionContainer extensions = null
				}
		return sourceSet
	}

	static Project mockProject() {
		def mock = mock(Project.class)
		def extensions = mockExtensionContainer()
		when(mock.getExtensions()).thenReturn(extensions)
		return mock
	}

	static ExtensionContainer mockExtensionContainer() {
		def mock = mock(ExtensionContainer.class)
		def extension = mockLoomGradleExtension()
		when(mock.getByName("loom")).thenReturn(extension)
		return mock
	}

	static LoomGradleExtension mockLoomGradleExtension() {
		def mock = mock(LoomGradleExtension.class)
		def loomFiles = mockLoomFiles()
		when(mock.refreshDeps()).thenReturn(false)
		when(mock.getFiles()).thenReturn(loomFiles)
		when(mock.download(any())).thenAnswer {
			Download.create(it.getArgument(0))
		}
		return mock
	}

	static LoomFiles mockLoomFiles() {
		def mock = mock(LoomFiles.class, new RequiresStubAnswer())
		doReturn(LoomTestConstants.TEST_DIR).when(mock).getUserCache()
		return mock
	}

	static ObjectFactory mockObjectFactory() {
		def mock = mock(ObjectFactory.class)
		def mockSourceDirectorySet = mockSourceDirectorySet()
		when(mock.sourceDirectorySet(any(), any())).thenReturn(mockSourceDirectorySet)
		return mock
	}

	static SourceDirectorySet mockSourceDirectorySet() {
		def mock = mock(SourceDirectorySet.class)
		def mockPatternFilterable = mockPatternFilterable()
		when(mock.getFilter()).thenReturn(mockPatternFilterable)
		return mock
	}

	static PatternFilterable mockPatternFilterable() {
		def mock = mock(PatternFilterable.class)
		return mock
	}

	static RegularFile mockRegularFile(File file) {
		def mock = mock(RegularFile.class)
		when(mock.getAsFile()).thenReturn(file)
		return mock
	}

	static RegularFileProperty mockRegularFileProperty(@Nullable File file) {
		if (file == null) {
			def mock = mock(RegularFileProperty.class)
			when(mock.isPresent()).thenReturn(false)
			return mock
		}

		def regularFile = mockRegularFile(file.getAbsoluteFile())

		def mock = mock(RegularFileProperty.class)
		when(mock.get()).thenReturn(regularFile)
		when(mock.isPresent()).thenReturn(true)
		return mock
	}

	static ConfigurableFileCollection mockConfigurableFileCollection(File... files) {
		def mock = mock(ConfigurableFileCollection.class)
		when(mock.getFiles()).thenReturn(Set.of(files))
		return mock
	}

	static RepositoryHandler mockRepositoryHandler() {
		def mock = mock(RepositoryHandler.class)
		return mock
	}

	static class RequiresStubAnswer implements Answer<Object> {
		Object answer(InvocationOnMock invocation) throws Throwable {
			throw new RuntimeException("${invocation.getMethod().getName()} is not stubbed")
		}
	}
}
