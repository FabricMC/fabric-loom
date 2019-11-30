package net.fabricmc.loom.util;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FabricModUtils
{

    private FabricModUtils()
    {
        throw new IllegalStateException("Tried to initialize: FabricModUtils but this is a Utility class.");
    }

    /**
     * Checks if an artifact is a fabric mod, according to the presence of a fabric.mod.json.
     */
    public static boolean isFabricMod(Project project, Logger logger, File artifact, String notation) {
        AtomicBoolean fabricMod = new AtomicBoolean(false);
        project.zipTree(artifact).visit(f -> {
            if (f.getName().endsWith("fabric.mod.json")) {
                logger.info("Found Fabric mod in modCompile: {}", notation);
                fabricMod.set(true);
                f.stopVisiting();
            }
        });
        return fabricMod.get();
    }
}
