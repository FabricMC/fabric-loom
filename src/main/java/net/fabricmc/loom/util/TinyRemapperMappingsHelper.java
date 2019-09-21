/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
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

package net.fabricmc.loom.util;

import net.fabricmc.mappings.*;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.MemberInstance;
import net.fabricmc.tinyv2.model.LocalVariable;
import net.fabricmc.tinyv2.model.LocalVariableEntry;
import net.fabricmc.tinyv2.model.MethodParameter;
import net.fabricmc.tinyv2.model.MethodParameterEntry;


public class TinyRemapperMappingsHelper {
	private TinyRemapperMappingsHelper() {

	}

	private static IMappingProvider.Member toMember(EntryTriple entryTriple){
		return new IMappingProvider.Member(entryTriple.getOwner(),entryTriple.getName(),entryTriple.getDesc());
	}

	public static IMappingProvider create(Mappings mappings, String from, String to) {
		return (acceptor) ->{
			for (ClassEntry entry : mappings.getClassEntries()) {
				acceptor.acceptClass(entry.get(from), entry.get(to));
			}

			for (FieldEntry entry : mappings.getFieldEntries()) {
				acceptor.acceptField(toMember(entry.get(from)),entry.get(to).getName());
			}

			for (MethodEntry entry : mappings.getMethodEntries()) {
				acceptor.acceptMethod(toMember(entry.get(from)),entry.get(to).getName());
			}
			for(MethodParameterEntry entry : mappings.getMethodParameterEntries()){
				MethodParameter parameter = entry.get(from);
				acceptor.acceptMethodArg(toMember(parameter.getMethod()),parameter.getLocalVariableIndex(),entry.get(to).getName());
			}
			for(LocalVariableEntry entry : mappings.getLocalVariableEntries()){
				LocalVariable localVariable = entry.get(from);
				acceptor.acceptMethodVar(toMember(localVariable.getMethod()),localVariable.getLocalVariableIndex(),
						localVariable.getLocalVariableStartOffset(),localVariable.getLocalVariableTableIndex(),entry.get(to).getName());
			}
		};
	}
}
