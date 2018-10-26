package net.fabricmc.loom.mixin;

import com.google.common.io.ByteStreams;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.mojang.MixinServiceLaunchWrapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public class MixinServiceGradle extends MixinServiceLaunchWrapper implements IClassBytecodeProvider {

	private static List<JarFile> jars = new ArrayList<>();


	@Override
	public String getName() {
		return "FabricGradle";
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		for(JarFile file : jars){
			ZipEntry entry = file.getEntry(name);
			if(entry != null){
				try {
					InputStream stream = file.getInputStream(entry);
					return stream;
				} catch (IOException e) {
					throw new RuntimeException("Failed to read mod file", e);
				}
			}
		}
		return super.getResourceAsStream(name);
	}

	public static void setupModFiles(Set<File> mods, File minecraft) throws IOException {
		jars.clear();
		for(File mod : mods){
			JarFile jarFile = new JarFile(mod);
			jars.add(jarFile);
		}
		jars.add(new JarFile(minecraft));
	}

	@Override
	public IClassBytecodeProvider getBytecodeProvider() {
		return this;
	}

	public byte[] getClassBytes(String name, String transformedName) throws IOException {
		InputStream inputStream = getResourceAsStream(name.replace(".", "/") + ".class");
		byte[] classBytes = ByteStreams.toByteArray(inputStream);
		if(classBytes == null){
			return super.getClassBytes(name, transformedName);
		}
		return classBytes;
	}
}
