package net.fabricmc.loom.configuration.providers.minecraft.tr;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import dev.architectury.tinyremapper.IMappingProvider;
import dev.architectury.tinyremapper.MethodRemapperProvider;
import org.objectweb.asm.commons.Remapper;

public class MappingsCompiled extends Remapper implements MethodRemapperProvider {
	private final Map<String, String> classes;
	private final Map<String, String> fields;
	private final Map<String, String> methods;
	private final Table<String, Integer, String> methodArgs;
	String lastSuperClass;
	String[] lastInterfaces;

	public MappingsCompiled(Set<IMappingProvider> mappings) {
		this.classes = new HashMap<>();
		this.fields = new HashMap<>();
		this.methods = new HashMap<>();
		this.methodArgs = HashBasedTable.create();

		for (IMappingProvider mapping : mappings) {
			mapping.load(new IMappingProvider.MappingAcceptor() {
				@Override
				public void acceptClass(String srcName, String dstName) {
					classes.put(srcName, dstName);
				}

				@Override
				public void acceptMethod(IMappingProvider.Member method, String dstName) {
					methods.put(method.name, dstName);
				}

				@Override
				public void acceptMethodArg(IMappingProvider.Member method, int lvIndex, String dstName) {
					methodArgs.put(method.owner + "|" + method.name, lvIndex, dstName);
				}

				@Override
				public void acceptMethodVar(IMappingProvider.Member method, int lvIndex, int startOpIdx, int asmIndex, String dstName) {
				}

				@Override
				public void acceptField(IMappingProvider.Member field, String dstName) {
					fields.put(field.name, dstName);
				}
			});
		}
	}

	@Override
	public String map(String name) {
		return classes.getOrDefault(name, name);
	}

	@Override
	public String mapFieldName(String owner, String name, String descriptor) {
		return mapField(name);
	}

	public String mapField(String name) {
		return fields.getOrDefault(name, name);
	}

	@Override
	public String mapMethodName(String owner, String name, String descriptor) {
		return mapMethod(name);
	}

	public String mapMethod(String name) {
		return methods.getOrDefault(name, name);
	}

	public String mapMethodArg(String methodOwner, String methodName, int lvIndex, String def) {
		String arg = methodArgs.get(methodOwner + "|" + methodName, lvIndex);
		if (arg != null) return arg;

		if (lastSuperClass != null) {
			arg = methodArgs.get(lastSuperClass + "|" + methodName, lvIndex);
			if (arg != null) return arg;
		}

		if (lastInterfaces != null) {
			for (String lastInterface : lastInterfaces) {
				arg = methodArgs.get(lastInterface + "|" + methodName, lvIndex);
				if (arg != null) return arg;
			}
		}

		return def;
	}

	@Override
	public String mapMethodVar(String methodOwner, String methodName, String methodDesc, int lvIndex, int startOpIdx, int asmIndex, String name) {
		return name;
	}

	@Override
	public String mapMethodArg(String methodOwner, String methodName, String methodDesc, int lvIndex, String name) {
		return mapMethodArg(methodOwner, methodName, lvIndex, name);
	}
}
