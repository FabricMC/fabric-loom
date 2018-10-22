package net.fabricmc.loom.task;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Constants;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;

public class FinaliseJar extends DefaultTask {

	@TaskAction
	public void finalisejar() throws IOException {
		LoomGradleExtension extension = this.getProject().getExtensions().getByType(LoomGradleExtension.class);
		if(Constants.MINECRAFT_FINAL_JAR.get(extension).exists()){
			Constants.MINECRAFT_FINAL_JAR.get(extension).delete();
		}
		FileUtils.copyFile(Constants.MINECRAFT_MAPPED_JAR.get(extension), Constants.MINECRAFT_FINAL_JAR.get(extension));
	}


}
