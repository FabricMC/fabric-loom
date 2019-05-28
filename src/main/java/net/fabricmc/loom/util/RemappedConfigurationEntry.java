package net.fabricmc.loom.util;

import org.gradle.api.artifacts.ConfigurationContainer;

public class RemappedConfigurationEntry {
    private final String sourceConfiguration;
    private final String targetConfiguration;
    private final String mavenScope;
    private final boolean isOnModCompileClasspath;

    public RemappedConfigurationEntry(String sourceConfiguration, String targetConfiguration, boolean isOnModCompileClasspath, String mavenScope) {
        this.sourceConfiguration = sourceConfiguration;
        this.targetConfiguration = targetConfiguration;
        this.isOnModCompileClasspath = isOnModCompileClasspath;
        this.mavenScope = mavenScope;
    }

    public String getMavenScope() {
        return mavenScope;
    }

    public boolean hasMavenScope() {
        return mavenScope != null && !mavenScope.isEmpty();
    }

    public boolean isOnModCompileClasspath() {
        return isOnModCompileClasspath;
    }

    public String getSourceConfiguration() {
        return sourceConfiguration;
    }

    public String getRemappedConfiguration() {
        return sourceConfiguration + "Mapped";
    }

    public String getTargetConfiguration(ConfigurationContainer container) {
        if (container.findByName(targetConfiguration) == null) {
            return "compile";
        }

        return targetConfiguration;
    }
}
