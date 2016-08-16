package net.fabric.loom;

import net.fabric.loom.task.DownloadTask;
import net.fabric.loom.task.ExtractNativesTask;
import net.fabric.loom.task.GenIdeaProjectTask;
import net.fabric.loom.task.MapJarsTask;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;

public class LoomGradlePlugin extends AbstractPlugin {
    @Override
    public void apply(Project target) {
        super.apply(target);

        makeTask("download", DownloadTask.class);
        makeTask("mapJars", MapJarsTask.class).dependsOn("download");
        makeTask("setupFabric", DefaultTask.class).dependsOn("mapJars");

        makeTask("extractNatives", ExtractNativesTask.class).dependsOn("download");
        makeTask("genIdeaRuns", GenIdeaProjectTask.class).dependsOn("cleanIdea").dependsOn("idea").dependsOn("extractNatives");
    }
}
