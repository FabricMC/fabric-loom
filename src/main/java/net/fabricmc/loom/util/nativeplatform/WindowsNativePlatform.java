/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2026 FabricMC
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

package net.fabricmc.loom.util.nativeplatform;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class WindowsNativePlatform implements LoomNativePlatform.NativePlatform {
	private static final Logger LOGGER = LoggerFactory.getLogger(WindowsNativePlatform.class);
	private static final int CCH_RM_SESSION_KEY = 32;

	private WindowsNativePlatform() {
	}

	static WindowsNativePlatform create() {
		return new WindowsNativePlatform();
	}

	@Override
	public List<ProcessHandle> getProcessesWithLockOn(Path path) throws LoomNativePlatformException {
		return getPidsHoldingFileHandles(path).stream()
				.map(ProcessHandle::of)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.toList();
	}

	@Override
	public List<String> getWindowTitlesForPid(long pid) throws LoomNativePlatformException {
		try (Arena arena = Arena.ofConfined()) {
			List<String> titles = new ArrayList<>();
			WindowEnumCallback callback = new WindowEnumCallback(pid, titles);
			MemorySegment callbackStub = Win32.upcallStub(callback, "accept", FunctionDescriptor.of(Win32.BOOL, Win32.HWND, Win32.LONG_PTR), arena);

			try {
				Win32.enumWindows(callbackStub, 0);
			} catch (LoomNativePlatformException e) {
				callback.throwIfFailed();
				throw e;
			}

			callback.throwIfFailed();
			return titles;
		} catch (LoomNativePlatformException e) {
			throw e;
		} catch (Throwable e) {
			LOGGER.error("Failed to query window titles for pid {}", pid, e);
			throw new LoomNativePlatformException("EnumWindows failed", e);
		}
	}

	private List<Long> getPidsHoldingFileHandles(Path path) throws LoomNativePlatformException {
		try (Arena arena = Arena.ofConfined()) {
			try (RmSession session = RmSession.open(arena)) {
				return getPidsHoldingFileHandles(arena, session, path);
			}
		} catch (LoomNativePlatformException e) {
			throw e;
		} catch (Throwable e) {
			LOGGER.error("Failed to query processes holding a lock on {}", path, e);
			throw new LoomNativePlatformException("Failed to query Restart Manager", e);
		}
	}

	private List<Long> getPidsHoldingFileHandles(Arena arena, RmSession session, Path path) throws Throwable {
		Win32.rmRegisterResources(session.handle(), arena, path.toString());

		MemorySegment procInfoNeeded = arena.allocate(Win32.DWORD);
		MemorySegment procInfo = arena.allocate(Win32.DWORD);
		MemorySegment rebootReasons = arena.allocate(Win32.DWORD);
		int procInfoCount = 64;
		MemorySegment processes;
		int error;

		do {
			procInfo.set(Win32.DWORD, 0, procInfoCount);
			procInfoNeeded.set(Win32.DWORD, 0, 0);
			processes = arena.allocate(Win32.RM_PROCESS_INFO, procInfoCount);
			error = Win32.rmGetList(session.handle(), procInfoNeeded, procInfo, processes, rebootReasons);
			procInfoCount = Math.max(procInfoNeeded.get(Win32.DWORD, 0), procInfoCount * 2);
		} while (error == Win32.ERROR_MORE_DATA);

		int returnedProcesses = procInfo.get(Win32.DWORD, 0);
		List<Long> pids = new ArrayList<>();

		for (int i = 0; i < returnedProcesses; i++) {
			MemorySegment processInfo = processes.asSlice(Win32.RM_PROCESS_INFO.byteSize() * i, Win32.RM_PROCESS_INFO.byteSize());
			int pid = processInfo.get(Win32.DWORD, 0);
			MemorySegment restartManagerStartTime = processInfo.asSlice(Win32.DWORD.byteSize(), Win32.FILETIME.byteSize());

			if (isSameLiveProcess(arena, pid, restartManagerStartTime)) {
				pids.add(Integer.toUnsignedLong(pid));
			}
		}

		return pids;
	}

	private boolean isSameLiveProcess(Arena arena, int pid, MemorySegment restartManagerStartTime) throws Throwable {
		try (Win32Process process = Win32Process.open(pid)) {
			if (!process.isValid()) {
				return false;
			}

			Win32.ProcessTimes processTimes = Win32.getProcessTimes(arena, process.handle());
			return processTimes != null && Win32.compareFileTime(restartManagerStartTime, processTimes.createTime()) == 0;
		}
	}

	private record RmSession(int handle) implements AutoCloseable {
		static RmSession open(Arena arena) throws Throwable {
			return new RmSession(Win32.rmStartSession(arena, CCH_RM_SESSION_KEY));
		}

		@Override
		public void close() throws LoomNativePlatformException {
			try {
				Win32.rmEndSession(handle);
			} catch (LoomNativePlatformException e) {
				throw e;
			} catch (Throwable e) {
				throw new LoomNativePlatformException("RmEndSession failed", e);
			}
		}
	}

	private record Win32Process(MemorySegment handle) implements AutoCloseable {
		static Win32Process open(int pid) throws Throwable {
			return new Win32Process(Win32.openProcess(Win32.PROCESS_QUERY_LIMITED_INFORMATION, 0, pid));
		}

		boolean isValid() {
			return !handle.equals(MemorySegment.NULL);
		}

		@Override
		public void close() throws LoomNativePlatformException {
			if (!isValid()) {
				return;
			}

			try {
				Win32.closeHandle(handle);
			} catch (Throwable e) {
				throw new LoomNativePlatformException("CloseHandle failed", e);
			}
		}
	}

	private static final class WindowEnumCallback {
		private final long pid;
		private final List<String> titles;
		private Throwable failure;

		WindowEnumCallback(long pid, List<String> titles) {
			this.pid = pid;
			this.titles = titles;
		}

		@SuppressWarnings("unused")
		public int accept(MemorySegment hwnd, long data) {
			try {
				if (isWindowOfPid(hwnd) && isMainWindow(hwnd)) {
					getWindowTitle(hwnd).ifPresent(titles::add);
				}

				return 1;
			} catch (Throwable e) {
				failure = e;
				return 0;
			}
		}

		private void throwIfFailed() throws LoomNativePlatformException {
			if (failure != null) {
				LOGGER.error("Failed to inspect window", failure);
				throw new LoomNativePlatformException("Failed to inspect window", failure);
			}
		}

		private boolean isWindowOfPid(MemorySegment hwnd) throws Throwable {
			return Win32.getWindowThreadProcessId(hwnd) == pid;
		}

		private boolean isMainWindow(MemorySegment hwnd) throws Throwable {
			return Win32.getWindow(hwnd, Win32.GW_OWNER).equals(MemorySegment.NULL) && Win32.isWindowVisible(hwnd);
		}

		private Optional<String> getWindowTitle(MemorySegment hwnd) throws Throwable {
			try (Arena arena = Arena.ofConfined()) {
				return Win32.getWindowText(hwnd, arena);
			}
		}
	}
}
