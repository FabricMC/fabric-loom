package chorusmc.gradle.util;

import com.google.gson.JsonObject;

import java.io.File;
import java.util.List;
import java.util.Map;

public class Version {
    public List<Library> libraries;
    public Map<String, Downloads> downloads;
    public AssetIndex assetIndex;

    public class Downloads {
        public String url;
        public String sha1;
    }

    public class AssetIndex {
        public String id;
        public String sha1;
        public String url;
    }

    public class Library {
        public String name;
        public JsonObject natives;
        public JsonObject downloads;
        private Artifact artifact;
        public Rule[] rules;

        public String getURL() {
            String path;
            String[] parts = this.name.split(":", 3);
            path = parts[0].replace(".", "/") + "/" + parts[1] + "/" + parts[2] + "/" + parts[1] + "-" + parts[2] + getClassifier() + ".jar";
            return Constants.LIBRARIES_BASE + path;
        }

        public File getFile() {
            String[] parts = this.name.split(":", 3);
            return new File(Constants.MINECRAFT_LIBS, parts[0].replace(".", File.separator) + File.separator + parts[1] + File.separator + parts[2] + File.separator + parts[1] + "-" + parts[2] + getClassifier() + ".jar");
        }

        public String getSha1() {
            if (this.downloads == null) {
                return "";
            } else if (this.downloads.getAsJsonObject("artifact") == null) {
                return "";
            } else if (this.downloads.getAsJsonObject("artifact").get("sha1") == null) {
                return "";
            } else {
                return this.downloads.getAsJsonObject("artifact").get("sha1").getAsString();
            }
        }

        public String getClassifier() {
            if (natives == null) {
                return "";
            } else {
                return "-" + natives.get(OperatingSystem.getOS().replace("${arch}", OperatingSystem.getArch())).getAsString().replace("\"", "");
            }
        }

        public boolean allowed() {
            if (this.rules == null || this.rules.length <= 0) {
                return true;
            }

            boolean success = false;
            for (Rule rule : this.rules) {
                if (rule.os != null && rule.os.name != null) {
                    if (rule.os.name.equalsIgnoreCase(OperatingSystem.getOS())) {
                        return rule.action.equalsIgnoreCase("allow");
                    }
                } else {
                    success = rule.action.equalsIgnoreCase("allow");
                }
            }
            return success;
        }

        public String getArtifactName() {
            if (artifact == null) {
                artifact = new Artifact(name);
            }
            return artifact.getArtifact(natives == null ? artifact.getClassifier() : natives.get(OperatingSystem.getOS()).getAsString());
        }

        private class Artifact {
            private final String domain, name, version, classifier, ext;

            public Artifact(String name) {
                String[] splitedArtifact = name.split(":");
                int idx = splitedArtifact[splitedArtifact.length - 1].indexOf('@');
                if (idx != -1) {
                    ext = splitedArtifact[splitedArtifact.length - 1].substring(idx + 1);
                    splitedArtifact[splitedArtifact.length - 1] = splitedArtifact[splitedArtifact.length - 1].substring(0, idx);
                } else {
                    ext = "jar";
                }
                this.domain = splitedArtifact[0];
                this.name = splitedArtifact[1];
                this.version = splitedArtifact[2];
                this.classifier = splitedArtifact.length > 3 ? splitedArtifact[3] : null;
            }

            public String getArtifact(String classifier) {
                String ret = domain + ":" + name + ":" + version;
                if (classifier != null && classifier.indexOf('$') > -1) {
                    classifier = classifier.replace("${arch}", Constants.SYSTEM_ARCH);
                }
                if (classifier != null) {
                    ret += ":" + classifier;
                }
                if (!"jar".equals(ext)) {
                    ret += "@" + ext;
                }
                return ret;
            }

            public String getClassifier() {
                return classifier;
            }
        }
    }

    private class Rule {
        public String action;
        public OS os;

        private class OS {
            String name;
        }
    }
}
