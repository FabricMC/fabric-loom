/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
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

package net.fabricmc.loom.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.tinyremapper.api.TrLogger;

public final class TinyRemapperLoggerAdapter implements TrLogger {
	public static final TinyRemapperLoggerAdapter INSTANCE = new TinyRemapperLoggerAdapter();

	private static final Logger LOGGER = LoggerFactory.getLogger("TinyRemapper");

	private TinyRemapperLoggerAdapter() {
	}

	@Override
	public void log(Level level, String message) {
		switch (level) {
		case ERROR:
			LOGGER.error(message);
			break;
		case WARN:
			LOGGER.warn(message);
			break;
		case INFO:
			LOGGER.info(message);
			break;
		case DEBUG:
			LOGGER.debug(message);
			break;
		}
	}
}
