/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.data;

import com.google.common.hash.HashCode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loom.util.MavenNotation;
import net.fabricmc.loom.util.Utils;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Created by covers1624 on 5/02/19.
 */
public class VersionInfoJson {

    private static final Gson gson = new GsonBuilder()//
            .registerTypeAdapterFactory(Utils.lowerCaseEnumFactory)//
            .registerTypeAdapterFactory(Utils.hashCodeStringTypeFactory)//
            .enableComplexMapKeySerialization()//
            .create();

    public List<Library> libraries = new ArrayList<>();
    public Map<String, Download> downloads;
    public AssetIndex assetIndex;

    public static VersionInfoJson fromJson(File file) {
        return Utils.fromJson(gson, file, VersionInfoJson.class);
    }

    public class AssetIndex {

        public String id;
        public HashCode sha1;
        public URL url;

        public String getId(String version) {
            if (!version.equals(id)) {
                return version + "-" + id;
            }
            return id;
        }
    }

    public class Download {

        public URL url;
        public HashCode sha1;
    }

    public class Library {

        public String name;
        public Extract extract;
        public List<Rule> rules = new ArrayList<>();
        public Map<OS, String> natives = new HashMap<>();

        public boolean allowed() {
            if (rules == null || rules.isEmpty()) {
                return true;
            }
            boolean last = false;
            for (Rule rule : rules) {
                if (rule.os != null && !rule.os.isCurrent()) {
                    last = rule.action.equals("allow");
                }
            }
            return last;
        }

        public String getArtifact(boolean skipNatives) {
            if (natives == null || skipNatives) {
                return name;
            } else {
                String classifier = natives.get(OS.currentOS());
                if (classifier == null) {
                    return name;
                }
                return MavenNotation.parse(name).withClassifier(classifier).toString();
            }
        }

        public class Extract {

            public List<String> exclude = new ArrayList<>();
        }

        public class Rule {

            public String action;
            public OSRestriction os;

            public class OSRestriction {

                public OS os;
                public String version;
                public String arch;

                public boolean isCurrent() {
                    OS current = OS.currentOS();
                    if (os != null && os != current) {
                        return false;
                    }
                    if (version != null) {
                        try {
                            Pattern pattern = Pattern.compile(version);
                            if (!pattern.matcher(OS.VERSION).matches()) {
                                return false;
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                    if (arch != null) {
                        try {
                            Pattern pattern = Pattern.compile(arch);
                            if (!pattern.matcher(OS.ARCH).matches()) {
                                return false;
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                    return true;
                }
            }
        }

    }

    public enum OS {
        LINUX("linux", "bsd", "unix"),
        WINDOWS("windows", "win"),
        OSX("osx", "mac"),
        UNKNOWN("unknown");

        public static final String VERSION = System.getProperty("os.version");
        public static final String ARCH = System.getProperty("os.arch");
        private static OS currentOS;

        private String name;
        private String[] aliases;

        OS(String name, String... aliases) {
            this.name = name;
            this.aliases = aliases;
        }

        public static OS currentOS() {
            if (currentOS == null) {
                String osName = System.getProperty("os.name");
                for (OS os : values()) {
                    if (osName.contains(os.name)) {
                        return currentOS = os;
                    }
                    for (String alias : os.aliases) {
                        if (osName.contains(alias)) {
                            return currentOS = os;
                        }
                    }
                }
                currentOS = UNKNOWN;
            }
            return currentOS;
        }

        @Override
        public String toString() {
            return name;
        }
    }

}
