package net.fabricmc.loom.ide.idea;

import java.util.IdentityHashMap;
import java.util.Map;

import org.gradle.api.Project;
import org.gradle.api.invocation.Gradle;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

public class LoomModelBuilderContext implements ModelBuilderContext {
    private final Map<DataProvider, Object> myMap = new IdentityHashMap<DataProvider, Object>();
    private final Gradle                    myGradle;

    public LoomModelBuilderContext(Project project) {
        myGradle = determineRootGradle(project.getGradle());
    }

    @Override
    public Gradle getRootGradle() {
        return myGradle;
    }

    @Override
    public <T> T getData(DataProvider<T> provider) {
        Object data = myMap.get(provider);
        if (data == null) {
            T value = provider.create(myGradle);
            myMap.put(provider, value);
            return value;
        }
        else {
            //noinspection unchecked
            return (T)data;
        }
    }

    private static Gradle determineRootGradle(Gradle gradle) {
        Gradle root = gradle;
        while (root.getParent() != null) {
            root = root.getParent();
        }
        return root;
    }
}
