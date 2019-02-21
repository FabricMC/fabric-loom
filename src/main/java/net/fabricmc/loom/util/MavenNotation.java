package net.fabricmc.loom.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;

/**
 * Simple Immutable class for a Maven artifacts notation.
 *
 * Created by covers1624 on 19/02/19.
 */
public class MavenNotation {

    @Nonnull
    public final String group;
    @Nonnull
    public final String module;
    @Nonnull
    public final String version;
    @Nullable
    public final String classifier;
    @Nonnull
    public final String extension;

    public MavenNotation(String group, String module, String version, String classifier, String extension) {
        this.group = group;
        this.module = module;
        this.version = version;
        this.classifier = classifier;
        this.extension = extension;
    }

    public MavenNotation(MavenNotation other) {
        this(other.group, other.module, other.version, other.classifier, other.extension);
    }

    /**
     * Parses a Maven string to a MavenNotation instance.
     * Format: group:module:version:[classifier][@extension]
     *
     * @param str The string.
     * @return The new MavenNotation.
     */
    public static MavenNotation parse(String str) {
        String[] segs = str.split(":");
        if (segs.length > 4 || segs.length < 3) {
            throw new RuntimeException("Invalid maven string: " + str);
        }
        String ext = "jar";
        if (segs[segs.length - 1].contains("@")) {
            String s = segs[segs.length - 1];
            int at = s.indexOf("@");
            ext = s.substring(at + 1);
            segs[segs.length - 1] = s.substring(0, at);
        }
        return new MavenNotation(segs[0], segs[1], segs[2], segs.length > 3 ? segs[3] : "", ext);

    }

    public MavenNotation withGroup(String group) {
        return new MavenNotation(group, module, version, classifier, extension);
    }

    public MavenNotation withModule(String module) {
        return new MavenNotation(group, module, version, classifier, extension);
    }

    public MavenNotation withVersion(String version) {
        return new MavenNotation(group, module, version, classifier, extension);
    }

    public MavenNotation withClassifier(String classifier) {
        return new MavenNotation(group, module, version, classifier, extension);
    }

    public MavenNotation withExtension(String extension) {
        return new MavenNotation(group, module, version, classifier, extension);
    }

    /**
     * Runs a StringSubstitutor over each element of the MavenNotation.
     *
     * @param substr The Substitutor.
     * @return The new MavenNotation.
     */
    public MavenNotation subst(StringSubstitutor substr) {
        return new MavenNotation(substr.replace(group), substr.replace(module), substr.replace(version), substr.replace(classifier), substr.replace(extension));
    }

    /**
     * Converts this MavenNotation to a path segment, either for a URL or File path.
     *
     * Format: group(dot to slash)/module/version/module-version[-classifier].extension
     *
     * @return The path segment.
     */
    public String toPath() {
        String clss = !StringUtils.isEmpty(classifier) ? "-" + classifier : "";
        return MessageFormat.format("{0}/{1}/{2}/{1}-{2}{3}.{4}", group.replace(".", "/"), module, version, clss, extension);
    }

    /**
     * Converts this MavenNotation to a file from the given base directory.
     *
     * @param dir The base directory.
     * @return The new File.
     */
    public File toFile(File dir) {
        return new File(dir, toPath());
    }

    /**
     * Converts this MavenNotation to a URL from the given URL.
     *
     * @param repo The repo.
     * @return The new URL.
     */
    public URL toURL(String repo) {
        try {
            return new URL(StringUtils.appendIfMissing(repo, "/") + toPath());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + group.hashCode();
        result = 31 * result + module.hashCode();
        result = 31 * result + version.hashCode();
        result = 31 * result + (!StringUtils.isEmpty(classifier) ? classifier : "").hashCode();
        result = 31 * result + extension.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (super.equals(obj)) {
            return true;
        }
        if (!(obj instanceof MavenNotation)) {
            return false;
        }
        MavenNotation other = (MavenNotation) obj;
        return StringUtils.equals(group, other.group)//
                && StringUtils.equals(module, other.module)//
                && StringUtils.equals(version, other.version)//
                && StringUtils.equals(classifier, other.classifier)//
                && StringUtils.equals(extension, other.extension);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(group);
        builder.append(":");
        builder.append(module);
        builder.append(":");
        builder.append(version);
        if (!StringUtils.isEmpty(classifier)) {
            builder.append(":");
            builder.append(classifier);
        }
        if (!StringUtils.equals(extension, "jar")) {
            builder.append("@");
            builder.append(extension);
        }
        return builder.toString();
    }
}
