package net.fabric.loom.task;

import net.fabric.loom.util.Constants;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.io.FileNotFoundException;

public class ExtractNativesTask extends DefaultTask {
    @TaskAction
    public void extractNatives() throws FileNotFoundException {
        if (!Constants.MINECRAFT_NATIVES.exists()) {
            for (File source : getProject().getConfigurations().getByName(Constants.CONFIG_NATIVES)) {
                ZipUtil.unpack(source, Constants.MINECRAFT_NATIVES);
            }
        }
    }
}
