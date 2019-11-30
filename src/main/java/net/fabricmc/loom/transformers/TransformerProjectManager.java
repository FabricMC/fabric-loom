package net.fabricmc.loom.transformers;

import com.google.common.collect.Maps;
import org.gradle.api.Project;

import java.util.Map;

public class TransformerProjectManager
{
    private static final TransformerProjectManager INSTANCE = new TransformerProjectManager();

    public static TransformerProjectManager getInstance() {
        return INSTANCE;
    }

    private Map<String, Project> projects = Maps.newConcurrentMap();

    public void register(Project project)
    {
        projects.put(project.getPath(), project);
    }

    public Project get(String path)
    {
        return projects.get(path);
    }
}
