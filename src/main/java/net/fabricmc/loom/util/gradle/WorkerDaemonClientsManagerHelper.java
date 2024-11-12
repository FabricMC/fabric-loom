/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

package net.fabricmc.loom.util.gradle;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.gradle.api.Transformer;
import org.gradle.workers.internal.DaemonForkOptions;
import org.gradle.workers.internal.WorkerDaemonClientsManager;

public class WorkerDaemonClientsManagerHelper {
	public static final String MARKER_PROP = "fabric.loom.decompile.worker";

	public static boolean stopIdleJVM(WorkerDaemonClientsManager manager, String jvmMarkerValue) {
		AtomicBoolean stopped = new AtomicBoolean(false);

		/* Transformer<List<WorkerDaemonClient>, List<WorkerDaemonClient>> */
		Transformer<List<Object>, List<Object>> transformer = workerDaemonClients -> {
			for (Object /* WorkerDaemonClient */ client : workerDaemonClients) {
				DaemonForkOptions forkOptions = getForkOptions(client);
				Map<String, Object> systemProperties = getSystemProperties(forkOptions);

				if (systemProperties == null || !jvmMarkerValue.equals(systemProperties.get(MARKER_PROP))) {
					// Not the JVM we are looking for
					continue;
				}

				stopped.set(true);
				return Collections.singletonList(client);
			}

			return Collections.emptyList();
		};

		try {
			Method selectIdleClientsToStop = manager.getClass().getDeclaredMethod("selectIdleClientsToStop", Transformer.class);
			selectIdleClientsToStop.setAccessible(true);
			selectIdleClientsToStop.invoke(manager, transformer);
		} catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
			throw new RuntimeException("Failed to selectIdleClientsToStop", e);
		}

		return stopped.get();
	}

	private static Map<String, Object> getSystemProperties(DaemonForkOptions forkOptions) {
		try {
			Method getJavaForkOptionsMethod = forkOptions.getClass().getDeclaredMethod("getJavaForkOptions");
			getJavaForkOptionsMethod.setAccessible(true);
			Object /* JavaForkOptions */ javaForkOptions = getJavaForkOptionsMethod.invoke(forkOptions);
			Method getSystemPropertiesMethod = javaForkOptions.getClass().getDeclaredMethod("getSystemProperties");
			getSystemPropertiesMethod.setAccessible(true);
			//noinspection unchecked
			return (Map<String, Object>) getSystemPropertiesMethod.invoke(javaForkOptions);
		} catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
			// Gradle 8.11 and below
		}

		// Gradle 8.12+
		try {
			Method getJvmOptions = forkOptions.getClass().getDeclaredMethod("getJvmOptions");
			getJvmOptions.setAccessible(true);
			Object jvmOptions = getJvmOptions.invoke(forkOptions);
			Method getMutableSystemProperties = jvmOptions.getClass().getDeclaredMethod("getMutableSystemProperties");
			getMutableSystemProperties.setAccessible(true);
			//noinspection unchecked
			return (Map<String, Object>) getMutableSystemProperties.invoke(jvmOptions);
		} catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
			throw new RuntimeException("Failed to daemon system properties", e);
		}
	}

	private static DaemonForkOptions getForkOptions(Object /* WorkerDaemonClient */ client) {
		try {
			Method getForkOptionsMethod = client.getClass().getDeclaredMethod("getForkOptions");
			getForkOptionsMethod.setAccessible(true);
			return (DaemonForkOptions) getForkOptionsMethod.invoke(client);
		} catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}
}
