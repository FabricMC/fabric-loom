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

package net.fabricmc.loom.tasks;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.FileCollection;
import org.gradle.process.ExecResult;
import org.gradle.process.JavaExecSpec;

/**
 * Simple trait like interface for a Task that wishes to execute a java process
 * with the classpath of the gradle plugin plus groovy.
 *
 * This is written like a Trait, its only intended to be used on a Task.
 *
 * Created by covers1624 on 11/02/19.
 */
public interface TForkingJavaExecTask {

    default ExecResult javaexec(Action<? super JavaExecSpec> action) {
        if (!(this instanceof Task)) {
            throw new IllegalStateException("Wot are you doing.");
        }
        Task task = (Task) this;
        ConfigurationContainer configurations = task.getProject().getBuildscript().getConfigurations();
        DependencyHandler handler = task.getProject().getDependencies();
        FileCollection classpath = configurations.getByName("classpath")//
                .plus(configurations.detachedConfiguration(handler.localGroovy()));
        return task.getProject().javaexec(spec -> {
            spec.classpath(classpath);
            action.execute(spec);
        });
    }
}
