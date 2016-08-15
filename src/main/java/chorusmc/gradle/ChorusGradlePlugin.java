package chorusmc.gradle;

import chorusmc.gradle.task.*;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;

public class ChorusGradlePlugin extends AbstractPlugin {
    @Override
    public void apply(Project target) {
        super.apply(target);

        makeTask("download", DownloadTask.class);
        makeTask("mapJars", MapJarsTask.class).dependsOn("download");
        makeTask("setupChorus", DefaultTask.class).dependsOn("mapJars");

        makeTask("extractNatives", ExtractNativesTask.class).dependsOn("download");
        makeTask("genIdeaRuns", GenIdeaProjectTask.class).dependsOn("cleanIdea").dependsOn("idea").dependsOn("extractNatives");
    }
}
