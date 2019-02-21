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

package net.fabricmc.loom;

import com.google.common.base.Strings;
import net.fabricmc.loom.util.RunConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Project;

import java.util.regex.Pattern;

import static java.text.MessageFormat.format;

/**
 * Created by covers1624 on 5/02/19.
 */
public class LoomGradleExtension {

    private final transient Project project;

    public String version;
    public String mappings;
    public String refmapName;
    public Pattern mixinArtifactRegex = Pattern.compile("org\\.spongepowered:mixin|net\\.fabricmc:sponge-mixin");
    public boolean experimentalThreadedFF = false;
    public String runDir = "run";

    public RunConfiguration clientRun = new RunConfiguration();
    public RunConfiguration serverRun = new RunConfiguration();

    public LoomGradleExtension(Project project) {
        this.project = project;
        clientRun.setName("Client");
        clientRun.setMainClass("net.fabricmc.loader.launch.knot.KnotClient");
        clientRun.addSysProp("fabric.development", "true");
        clientRun.addSysProp("mixin.env.remapRefMap", "true");
        clientRun.setSourceSet("main");

        serverRun.setName("Server");
        serverRun.setMainClass("net.fabricmc.loader.launch.knot.KnotServer");
        serverRun.setSourceSet("main");
    }

    public void setMixinArtifactRegex(String mixinArtifactRegex) {
        this.mixinArtifactRegex = Pattern.compile(mixinArtifactRegex);
    }

    public void setMappings(String mappings) {
        if (!mappings.contains(":")) {
            mappings = "net.fabricmc:yarn:" + mappings;
        }
        if (StringUtils.countMatches(mappings, ":") < 2) {
            throw new IllegalArgumentException(format("Invalid mappings artifact: '{0}'.", mappings));
        }
        this.mappings = mappings;
    }

    public String getRefmapName() {
        if (Strings.isNullOrEmpty(refmapName)) {
            return project.getName() + "-refmap.json";
        }
        return refmapName;
    }
}
