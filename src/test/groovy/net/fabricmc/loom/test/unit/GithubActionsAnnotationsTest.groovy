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

package net.fabricmc.loom.test.unit

import spock.lang.Specification

import net.fabricmc.loom.util.github.GithubActionsAnnotations

class GithubActionsAnnotationsTest extends Specification {
	def "test command strings"() {
		when:
		def builder = GithubActionsAnnotations.error(message)

		if (file) {
			builder.file(file)
		}

		if (title) {
			builder.title(title)
		}

		if (positions) {
			if (positions.lines) {
				if (positions.lines.size() == 2) {
					builder.line(positions.lines[0], positions.lines[1])
				} else {
					builder.line(positions.lines[0])
				}
			}

			if (positions.columns) {
				if (positions.columns.size() == 2) {
					builder.column(positions.columns[0], positions.columns[1])
				} else {
					builder.column(positions.columns[0])
				}
			}
		}

		then:
		builder.build().toCommandString() == expected

		where:
		message | file | title | positions | expected
		'Hello, world!' | null | null | null | '::error::Hello, world!'
		'Hello, world!' | '/Hello.java' | null | null | '::error file=/Hello.java,line=1::Hello, world!'
		'Hello, world!' | '/Hello.java' | null | [lines: [10]] | '::error file=/Hello.java,line=10::Hello, world!'
		'Hello, world!' | '/Hello.java' | null | [lines: [10], columns: [32]] | '::error file=/Hello.java,line=10,col=32::Hello, world!'
		'Hello, world!' | '/Hello.java' | null | [lines: [10, 12]] | '::error file=/Hello.java,line=10,endLine=12::Hello, world!'
		'Hello,\nworld!' | 'C:\\Hello.java' | 'Custom,Title' | [lines: [10, 12], columns: [44, 47]] | '::error file=C%3A\\Hello.java,line=10,endLine=12,col=44,endColumn=47,title=Custom%2CTitle::Hello,%0Aworld!'
	}
}
