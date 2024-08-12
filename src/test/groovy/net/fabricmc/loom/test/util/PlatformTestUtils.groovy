/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2023 FabricMC
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

package net.fabricmc.loom.test.util

import groovy.transform.Immutable

import net.fabricmc.loom.util.Platform

@Immutable
class PlatformTestUtils implements Platform  {
	public static final Platform WINDOWS_X64 = platform(OperatingSystem.WINDOWS, false)
	public static final Platform WINDOWS_ARM64 = platform(OperatingSystem.WINDOWS, true)
	public static final Platform LINUX_X64 = platform(OperatingSystem.LINUX, false)
	public static final Platform LINUX_ARM64 = platform(OperatingSystem.LINUX, true)
	public static final Platform LINUX_RISCV = platform(OperatingSystem.LINUX, false, true, true)
	public static final Platform MAC_OS_X64 = platform(OperatingSystem.MAC_OS, false)
	public static final Platform MAC_OS_ARM64 = platform(OperatingSystem.MAC_OS, true)
	public static final List<Platform> ALL = [
		WINDOWS_X64,
		WINDOWS_ARM64,
		LINUX_X64,
		LINUX_ARM64,
		LINUX_RISCV,
		MAC_OS_X64,
		MAC_OS_ARM64
	]

	private static Platform platform(OperatingSystem operatingSystem, boolean isArm = false, boolean is64Bit = true, boolean isRiscV = false) {
		new PlatformTestUtils(operatingSystem: operatingSystem, architecture: new TestArchitecture(is64Bit: is64Bit, isArm: isArm, isRiscV: isRiscV), supportsUnixDomainSockets: true)
	}

	OperatingSystem operatingSystem
	TestArchitecture architecture
	boolean supportsUnixDomainSockets

	@Override
	boolean supportsUnixDomainSockets() {
		supportsUnixDomainSockets
	}

	@Override
	boolean isRaspberryPi() {
		return false
	}

	@Immutable
	static class TestArchitecture implements Architecture {
		boolean is64Bit
		boolean isArm
		boolean isRiscV

		@Override
		boolean is64Bit() {
			is64Bit
		}

		@Override
		boolean isArm() {
			isArm
		}

		@Override
		boolean isRiscV() {
			return isRiscV
		}
	}
}
