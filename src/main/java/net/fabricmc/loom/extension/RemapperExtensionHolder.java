package net.fabricmc.loom.extension;

import javax.inject.Inject;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.commons.Remapper;

import net.fabricmc.loom.api.remapping.RemapperContext;
import net.fabricmc.loom.api.remapping.RemapperExtension;
import net.fabricmc.loom.api.remapping.RemapperParameters;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.api.TrClass;

public abstract class RemapperExtensionHolder {
	private final RemapperParameters remapperParameters;

	@Inject
	public RemapperExtensionHolder(RemapperParameters remapperParameters) {
		this.remapperParameters = remapperParameters;
	}

	@Input
	public abstract Property<String> getRemapperExtensionClassName();

	@Nested
	public RemapperParameters getRemapperParameters() {
		return remapperParameters;
	}

	public void apply(TinyRemapper.Builder tinyRemapperBuilder) {
		RemapperExtension<?> remapperExtension = newInstance();

		tinyRemapperBuilder.extraPostApplyVisitor(new RemapperExtensionImpl(remapperExtension));

		if (remapperExtension instanceof TinyRemapper.AnalyzeVisitorProvider analyzeVisitorProvider) {
			tinyRemapperBuilder.extraAnalyzeVisitor(analyzeVisitorProvider);
		}

		if (remapperExtension instanceof TinyRemapper.ApplyVisitorProvider applyVisitorProvider) {
			// TODO allow having a pre apply visitor?
			tinyRemapperBuilder.extraPostApplyVisitor(applyVisitorProvider);
		}
	}

	private RemapperExtension<?> newInstance() {
		try {
			return Class.forName(getRemapperExtensionClassName().get())
					.asSubclass(RemapperExtension.class)
					.getConstructor()
					.newInstance();
		} catch (Exception e) {
			throw new RuntimeException("Failed to create remapper extension", e);
		}
	}

	private record RemapperExtensionImpl(RemapperExtension<?> remapperExtension) implements TinyRemapper.ApplyVisitorProvider {
		@Override
		public ClassVisitor insertApplyVisitor(TrClass cls, ClassVisitor next) {
			return remapperExtension.insertVisitor(cls.getName(), new RemapperContext() {
				// TODO dont create a new instance of this for every class
				@Override
				public Remapper getRemapper() {
					return cls.getEnvironment().getRemapper();
				}

				@Override
				public String sourceNamespace() {
					return null;
				}

				@Override
				public String targetNamespace() {
					return null;
				}
			}, next);
		}
	}
}
