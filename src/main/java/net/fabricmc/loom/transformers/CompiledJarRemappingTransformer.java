package net.fabricmc.loom.transformers;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.providers.MappingsProvider;
import net.fabricmc.loom.providers.MinecraftMappedProvider;
import net.fabricmc.loom.transformers.parameters.ProjectReferencingParameters;
import net.fabricmc.loom.util.*;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.transform.*;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.Input;
import org.zeroturnaround.zip.commons.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

@CacheableTransform
public abstract class CompiledJarRemappingTransformer implements TransformAction<ProjectReferencingParameters>
{

    @CompileClasspath
    @InputArtifact
    public abstract Provider<FileSystemLocation> getInput();

    @CompileClasspath
    @InputArtifactDependencies
    abstract FileCollection getDependencies();

    @Override
    public void transform(final TransformOutputs outputs)
    {
        final FileSystemLocation resolvedInput = getInput().getOrNull();
        if (resolvedInput == null)
        {
            lifecycle("Can not transform a jar who has no input.");
            return;
        }

        final File inputFile = resolvedInput.getAsFile();
        if (!inputFile.exists())
        {
            lifecycle("Can not transform jar who does not exists on disk.");
            return;
        }

        if (!FabricModUtils.isFabricMod(
          getProject(),
          getProject().getLogger(),
          inputFile,
          inputFile.getName()
        ))
        {
            final String inputFileNameBase = inputFile.getName().substring(0, inputFile.getName().length() - 4);
            final File outputFile = outputs.file(inputFileNameBase + "-not_remapped.jar");
            try
            {
                FileUtils.copyFile(inputFile, outputFile);
                return;
            }
            catch (IOException e)
            {
                lifecycle("Failed to copy not remappable file to output.", e);
            }
        }
    }

    /**
     * Short circuit method to handle logging a lifecycle message to the projects logger.
     *
     * @param message The message to log, formatted.
     * @param objects The formatting parameters.
     */
    private void lifecycle(String message, Object... objects)
    {
        getProject().getLogger().lifecycle("[ContainedZipStripping]: " + message, objects);
    }
    /**
     * Short circuit method to handle logging a lifecycle message to the projects logger.
     *
     * @param message The message to log, formatted.
     * @param exception The exception to log.
     */
    private void lifecycle(String message, Throwable exception)
    {
        getProject().getLogger().lifecycle("[ContainedZipStripping]: " + message, exception);
    }

    private Project getProject() {
        return TransformerProjectManager.getInstance().get(getParameters().getProjectPathParameter().get());
    }

    private void remapJar(File input, File output) throws IOException {
        final Project project = getProject();

        LoomGradleExtension extension = project.getExtensions().getByType(LoomGradleExtension.class);
        String fromM = "intermediary";
        String toM = "named";

        MinecraftMappedProvider mappedProvider = extension.getMinecraftMappedProvider();
        MappingsProvider mappingsProvider = extension.getMappingsProvider();

        Path inputPath = input.getAbsoluteFile().toPath();
        Path mc = mappedProvider.MINECRAFT_INTERMEDIARY_JAR.toPath();
        Path[] mcDeps = mappedProvider.getMapperPaths().stream().map(File::toPath).toArray(Path[]::new);
        Set<Path> modCompiles = new HashSet<>();

        getDependencies().forEach(f -> {
            if (f != input)
            {
                modCompiles.add(f.toPath());
            }
        });

        project.getLogger().lifecycle(":remapping " + input.getName() + " (TinyRemapper, " + fromM + " -> " + toM + ")");

        // If the sources don't exist, we want remapper to give nicer names to the missing variable names.
        // However, if the sources do exist, if remapper gives names to the parameters that prevents IDEs (at least IDEA)
        // from replacing the parameters with the actual names from the sources.
        boolean sourcesExist = false;//TODO: Fix me: ModCompileRemapper.findSources(project.getDependencies(), artifact) != null;

        TinyRemapper remapper = TinyRemapper.newRemapper()
                                  .withMappings(TinyRemapperMappingsHelper.create(mappingsProvider.getMappings(), fromM, toM, false))
                                  .renameInvalidLocals(!sourcesExist)
                                  .build();

        try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(Paths.get(output.getAbsolutePath())).build()) {
            outputConsumer.addNonClassFiles(inputPath);
            remapper.readClassPath(modCompiles.toArray(new Path[0]));
            remapper.readClassPath(mc);
            remapper.readClassPath(mcDeps);
            remapper.readInputs(inputPath);
            remapper.apply(outputConsumer);
        } finally {
            remapper.finish();
        }

        if (!output.exists()) {
            throw new RuntimeException("Failed to remap JAR to " + toM + " file not found: " + output.getAbsolutePath());
        }
    }

}
