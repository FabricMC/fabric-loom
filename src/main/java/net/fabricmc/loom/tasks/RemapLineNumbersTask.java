package net.fabricmc.loom.tasks;

import net.fabricmc.loom.tasks.cache.CachedInput;
import net.fabricmc.loom.tasks.cache.CachedInputTask;
import net.fabricmc.loom.util.LineNumberRemapper;
import net.fabricmc.stitch.util.StitchUtil;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;

/**
 * Created by covers1624 on 18/02/19.
 */
public class RemapLineNumbersTask extends CachedInputTask {

    private Object input;
    private Object output;
    private Object lineMap;

    @TaskAction
    public void doTask() throws IOException {
        LineNumberRemapper remapper = new LineNumberRemapper();
        remapper.readMappings(getLineMap());
        try (StitchUtil.FileSystemDelegate inFs = StitchUtil.getJarFileSystem(getInput(), true)) {
            try (StitchUtil.FileSystemDelegate outFs = StitchUtil.getJarFileSystem(getOutput(), true)) {
                remapper.process(inFs.get().getPath("/"), outFs.get().getPath("/"));
            }
        }
    }

    //@formatter:off
    @CachedInput public File getInput() { return getProject().file(input); }
    @OutputFile public File getOutput() { return getProject().file(output); }
    @CachedInput public File getLineMap() { return getProject().file(lineMap); }
    public void setInput(Object input) { this.input = input; }
    public void setOutput(Object output) { this.output = output; }
    public void setLineMap(Object lineMap) { this.lineMap = lineMap; }
    //@foramtter:on
}
