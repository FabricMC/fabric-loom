package net.fabricmc.loom.transformers.parameters;

import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

public interface ProjectReferencingParameters extends TransformParameters
{
    @Input
    Property<String> getProjectPathParameter();
}
