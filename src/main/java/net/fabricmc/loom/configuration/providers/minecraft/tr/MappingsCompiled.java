package net.fabricmc.loom.configuration.providers.minecraft.tr;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.fabricmc.tinyremapper.IMappingProvider;

public class MappingsCompiled {
	private final Map<String, String> classes;
	private final Map<String, String> fields;
	private final Map<String, String> methods;
	private final Map<String, String> methodArgs;

	public MappingsCompiled(Set<IMappingProvider> mappings) {
		this.classes = new HashMap<>();
		this.fields = new HashMap<>();
		this.methods = new HashMap<>();
		this.methodArgs = new HashMap<>();

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
					methodArgs.put(method.name + "|" + lvIndex, dstName);
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

	public String mapClass(String name) {
		return classes.getOrDefault(name, name);
	}

	public String mapField(String name) {
		return fields.getOrDefault(name, name);
	}

	public String mapMethod(String name) {
		return methods.getOrDefault(name, name);
	}

	public String mapMethodArg(String methodName, int lvIndex, String def) {
		return methodArgs.getOrDefault(methodName + "|" + lvIndex, def);
	}
}
