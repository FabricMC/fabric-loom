/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016 FabricMC
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

package net.fabricmc.loom.task;

import com.google.gson.*;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.Version;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Install https://marketplace.visualstudio.com/items?itemName=georgewfraser.vscode-javac into vscode
 */
public class GenVSCodeProjectTask extends DefaultTask {

    @TaskAction
    public void genVsCodeProject() throws IOException {
        LoomGradleExtension extension = this.getProject().getExtensions().getByType(LoomGradleExtension.class);
        File classPathFile = new File("vscodeClasspath.txt");
        File configFile = new File("javaconfig.json");

        Gson gson = new Gson();
        Version version = gson.fromJson(new FileReader(Constants.MINECRAFT_JSON.get(extension)), Version.class);

        List<String> libs = new ArrayList<>();
        for (Version.Library library : version.libraries) {
            if (library.allowed() && library.getFile(extension) != null && library.getFile(extension).exists()) {
                libs.add(library.getFile(extension).getAbsolutePath());
            }
        }
        libs.add(Constants.MINECRAFT_MAPPED_JAR.get(extension).getAbsolutePath());
        for (File file : getProject().getConfigurations().getByName("compile").getFiles()) {
            libs.add(file.getAbsolutePath());
        }
        FileUtils.writeLines(classPathFile, libs);

        JsonObject jsonObject = new JsonObject();
        JsonArray jsonArray = new JsonArray();
        jsonArray.add("src/main/java");
        jsonArray.add("src/main/resorces");
        jsonArray.add("src/test/java");
        jsonArray.add("src/test/resorces");
        jsonObject.add("sourcePath", jsonArray);
        JsonElement element = new JsonPrimitive(classPathFile.getName());
        jsonObject.add("classPathFile", element);
        element = new JsonPrimitive("vscode");
        jsonObject.add("outputDirectory", element);

        FileUtils.write(configFile, gson.toJson(jsonObject), Charset.defaultCharset());
    }

}
