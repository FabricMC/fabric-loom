package net.fabricmc.loom.transformers;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loom.transformers.parameters.ProjectReferencingParameters;
import org.gradle.api.Project;
import org.gradle.api.artifacts.transform.*;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.CompileClasspath;
import org.slf4j.Logger;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.commons.FileUtils;
import org.zeroturnaround.zip.transform.StringZipEntryTransformer;
import org.zeroturnaround.zip.transform.ZipEntryTransformerEntry;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.util.zip.ZipEntry;

@CacheableTransform
public abstract class ContainedZipStrippingTransformer implements TransformAction<ProjectReferencingParameters>
{
    private static final Gson GSON = new Gson();



    /**
     * Gives access to the artifact this transformer needs to operate on.
     *
     * @return A provider who can resolve the location of the input for this transformer on disk.
     */
    @CompileClasspath
    @InputArtifact
    public abstract Provider<FileSystemLocation> getInput();

    /**
     * Executes the stripping of the contained jars.
     *
     * @param outputs The handler for the outputs that this transformer produces.
     */
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

        final String inputFileNameBase = inputFile.getName().substring(0, inputFile.getName().length() - 4);
        final File outputFile = outputs.file(inputFileNameBase + "-stripped.jar");
        try
        {
            FileUtils.copyFile(inputFile, outputFile);
        }
        catch (IOException e)
        {
            lifecycle("Failed to copy input file to output file.", e);
        }

        stripNestedJars(outputFile);
    }

    /**
     * Short circuit method to handle logging a lifecycle message to the projects logger.
     *
     * @param message The message to log, formatted.
     * @param objects The formatting parameters.
     */
    private void lifecycle(String message, Object... objects)
    {
        TransformerProjectManager.getInstance().get(getParameters().getProjectPathParameter().get()).getLogger().lifecycle("[ContainedZipStripping]: " + message, objects);
    }
    /**
     * Short circuit method to handle logging a lifecycle message to the projects logger.
     *
     * @param message The message to log, formatted.
     * @param exception The exception to log.
     */
    private void lifecycle(String message, Throwable exception)
    {
        TransformerProjectManager.getInstance().get(getParameters().getProjectPathParameter().get()).getLogger().lifecycle("[ContainedZipStripping]: " + message, exception);
    }

    /**
     * Handles the stripping of the jars entry from a given libraries 'fabric.mod.json' entry.
     *
     * @param jar The jar to process.
     */
    private void stripNestedJars(File jar) {
        //Strip out all contained jar info as we dont want loader to try and load the jars contained in dev.
        ZipUtil.transformEntries(jar, new ZipEntryTransformerEntry[] {(new ZipEntryTransformerEntry("fabric.mod.json", new StringZipEntryTransformer() {
            @Override
            protected String transform(ZipEntry zipEntry, String input) throws IOException
            {
                JsonObject json = GSON.fromJson(input, JsonObject.class);
                json.remove("jars");
                return GSON.toJson(json);
            }
        }))});
    }
}
