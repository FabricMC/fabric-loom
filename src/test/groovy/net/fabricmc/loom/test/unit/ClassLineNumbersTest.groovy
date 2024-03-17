/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2023 FabricMC
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

import net.fabricmc.loom.decompilers.ClassLineNumbers

class ClassLineNumbersTest extends Specification {
	def "read linemap"() {
		when:
		def reader = new BufferedReader(new StringReader(LINE_MAP))
		def lineNumbers = ClassLineNumbers.readMappings(reader)
		def lineMap = lineNumbers.lineMap()

		then:
		lineMap.size() == 2
		lineMap["net/minecraft/server/dedicated/ServerPropertiesHandler"].lineMap().size() == 39
		lineMap["net/minecraft/server/dedicated/ServerPropertiesHandler"].maxLine() == 203
		lineMap["net/minecraft/server/dedicated/ServerPropertiesHandler"].maxLineDest() == 187

		lineMap["net/minecraft/server/dedicated/ServerPropertiesLoader"].lineMap().size() == 6
		lineMap["net/minecraft/server/dedicated/ServerPropertiesLoader"].maxLine() == 25
		lineMap["net/minecraft/server/dedicated/ServerPropertiesLoader"].maxLineDest() == 30
	}

	private static final String LINE_MAP = """
net/minecraft/server/dedicated/ServerPropertiesHandler\t203\t187
\t48\t187
\t91\t92
\t96\t97
\t110\t108
\t112\t109
\t113\t110
\t115\t111
\t116\t112
\t118\t113
\t119\t113
\t120\t113
\t122\t114
\t130\t115
\t147\t129
\t149\t131
\t151\t133
\t154\t136
\t158\t141
\t159\t142
\t163\t144
\t164\t145
\t165\t146
\t166\t147
\t168\t149
\t169\t150
\t170\t151
\t172\t153
\t175\t155
\t176\t156
\t177\t157
\t178\t158
\t181\t160
\t186\t165
\t187\t166
\t192\t171
\t194\t173
\t195\t174
\t197\t176
\t203\t182

net/minecraft/server/dedicated/ServerPropertiesLoader\t25\t30
\t11\t15
\t12\t16
\t16\t20
\t20\t24
\t24\t28
\t25\t30
"""
}
