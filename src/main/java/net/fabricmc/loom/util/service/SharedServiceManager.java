/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

package net.fabricmc.loom.util.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple manager for {@link SharedService} to be used across gradle (sub) projects.
 * This is a basic replacement for gradle's build service api.
 */
public abstract class SharedServiceManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(BuildSharedServiceManager.class);
	private final Map<String, SharedService> sharedServiceMap = new HashMap<>();

	private boolean shutdown = false;

	SharedServiceManager() {
		LOGGER.info("Creating new SharedServiceManager({})", hashCode());
	}

	public <S extends SharedService> S getOrCreateService(String id, Supplier<S> function) {
		synchronized (sharedServiceMap) {
			if (shutdown) {
				throw new UnsupportedOperationException("Cannot get or create service has the manager has been shutdown.");
			}

			//noinspection unchecked
			S sharedService = (S) sharedServiceMap.get(id);

			if (sharedService == null) {
				LOGGER.debug("Creating service for {}", id);
				sharedService = function.get();
				sharedServiceMap.put(id, sharedService);
			}

			return sharedService;
		}
	}

	protected void onFinish() {
		synchronized (sharedServiceMap) {
			shutdown = true;
		}

		LOGGER.info("Closing SharedServiceManager({})", hashCode());

		final List<IOException> exceptionList = new ArrayList<>();

		for (SharedService sharedService : sharedServiceMap.values()) {
			try {
				sharedService.close();
			} catch (IOException e) {
				exceptionList.add(e);
			}
		}

		sharedServiceMap.clear();

		// This is required to ensure that mercury releases all of the file handles.
		System.gc();

		if (!exceptionList.isEmpty()) {
			// Done to try and close all the services.
			RuntimeException exception = new RuntimeException("Failed to close all shared services");
			exceptionList.forEach(exception::addSuppressed);
			throw exception;
		}
	}
}
