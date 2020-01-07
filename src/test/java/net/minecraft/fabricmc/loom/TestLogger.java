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

package net.minecraft.fabricmc.loom;

import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.slf4j.Marker;

public class TestLogger implements Logger {
	@Override
	public boolean isLifecycleEnabled() {
		return true;
	}

	@Override
	public String getName() {
		return "Test Logger";
	}

	@Override
	public boolean isTraceEnabled() {
		return false;
	}

	@Override
	public void trace(String s) {
	}

	@Override
	public void trace(String s, Object o) {
	}

	@Override
	public void trace(String s, Object o, Object o1) {
	}

	@Override
	public void trace(String s, Object... objects) {
	}

	@Override
	public void trace(String s, Throwable throwable) {
	}

	@Override
	public boolean isTraceEnabled(Marker marker) {
		return false;
	}

	@Override
	public void trace(Marker marker, String s) {
	}

	@Override
	public void trace(Marker marker, String s, Object o) {
	}

	@Override
	public void trace(Marker marker, String s, Object o, Object o1) {
	}

	@Override
	public void trace(Marker marker, String s, Object... objects) {
	}

	@Override
	public void trace(Marker marker, String s, Throwable throwable) {
	}

	@Override
	public boolean isDebugEnabled() {
		return false;
	}

	@Override
	public void debug(String s) {
	}

	@Override
	public void debug(String s, Object o) {
	}

	@Override
	public void debug(String s, Object o, Object o1) {
	}

	@Override
	public void debug(String message, Object... objects) {
	}

	@Override
	public void debug(String s, Throwable throwable) {
	}

	@Override
	public boolean isDebugEnabled(Marker marker) {
		return false;
	}

	@Override
	public void debug(Marker marker, String s) {
	}

	@Override
	public void debug(Marker marker, String s, Object o) {
	}

	@Override
	public void debug(Marker marker, String s, Object o, Object o1) {
	}

	@Override
	public void debug(Marker marker, String s, Object... objects) {
	}

	@Override
	public void debug(Marker marker, String s, Throwable throwable) {
	}

	@Override
	public boolean isInfoEnabled() {
		return false;
	}

	@Override
	public void info(String s) {
	}

	@Override
	public void info(String s, Object o) {
	}

	@Override
	public void info(String s, Object o, Object o1) {
	}

	@Override
	public void lifecycle(String message) {
		System.out.println(message);
	}

	@Override
	public void lifecycle(String message, Object... objects) {
	}

	@Override
	public void lifecycle(String message, Throwable throwable) {
	}

	@Override
	public boolean isQuietEnabled() {
		return false;
	}

	@Override
	public void quiet(String message) {
	}

	@Override
	public void quiet(String message, Object... objects) {
	}

	@Override
	public void info(String message, Object... objects) {
	}

	@Override
	public void info(String s, Throwable throwable) {
	}

	@Override
	public boolean isInfoEnabled(Marker marker) {
		return false;
	}

	@Override
	public void info(Marker marker, String s) {
	}

	@Override
	public void info(Marker marker, String s, Object o) {
	}

	@Override
	public void info(Marker marker, String s, Object o, Object o1) {
	}

	@Override
	public void info(Marker marker, String s, Object... objects) {
	}

	@Override
	public void info(Marker marker, String s, Throwable throwable) {
	}

	@Override
	public boolean isWarnEnabled() {
		return false;
	}

	@Override
	public void warn(String s) {
	}

	@Override
	public void warn(String s, Object o) {
	}

	@Override
	public void warn(String s, Object... objects) {
	}

	@Override
	public void warn(String s, Object o, Object o1) {
	}

	@Override
	public void warn(String s, Throwable throwable) {
	}

	@Override
	public boolean isWarnEnabled(Marker marker) {
		return false;
	}

	@Override
	public void warn(Marker marker, String s) {
	}

	@Override
	public void warn(Marker marker, String s, Object o) {
	}

	@Override
	public void warn(Marker marker, String s, Object o, Object o1) {
	}

	@Override
	public void warn(Marker marker, String s, Object... objects) {
	}

	@Override
	public void warn(Marker marker, String s, Throwable throwable) {
	}

	@Override
	public boolean isErrorEnabled() {
		return false;
	}

	@Override
	public void error(String s) {
	}

	@Override
	public void error(String s, Object o) {
	}

	@Override
	public void error(String s, Object o, Object o1) {
	}

	@Override
	public void error(String s, Object... objects) {
	}

	@Override
	public void error(String s, Throwable throwable) {
	}

	@Override
	public boolean isErrorEnabled(Marker marker) {
		return false;
	}

	@Override
	public void error(Marker marker, String s) {
	}

	@Override
	public void error(Marker marker, String s, Object o) {
	}

	@Override
	public void error(Marker marker, String s, Object o, Object o1) {
	}

	@Override
	public void error(Marker marker, String s, Object... objects) {
	}

	@Override
	public void error(Marker marker, String s, Throwable throwable) {
	}

	@Override
	public void quiet(String message, Throwable throwable) {
	}

	@Override
	public boolean isEnabled(LogLevel level) {
		return false;
	}

	@Override
	public void log(LogLevel level, String message) {
	}

	@Override
	public void log(LogLevel level, String message, Object... objects) {
	}

	@Override
	public void log(LogLevel level, String message, Throwable throwable) {
	}
}
