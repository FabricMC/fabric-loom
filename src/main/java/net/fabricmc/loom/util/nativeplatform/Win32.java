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
import java.lang.foreign.AddressLayout;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

final class Win32 {
	static final int ERROR_SUCCESS = 0;
	static final int ERROR_MORE_DATA = 234;
	static final int PROCESS_QUERY_LIMITED_INFORMATION = 0x1000;
	static final int GW_OWNER = 4;
	static final int FORMAT_MESSAGE_ALLOCATE_BUFFER = 0x00000100;
	static final int FORMAT_MESSAGE_IGNORE_INSERTS = 0x00000200;
	static final int FORMAT_MESSAGE_FROM_SYSTEM = 0x00001000;
	static final int NTE_EXISTS = 0x8009000F;
	static final int NTE_BAD_KEYSET = 0x80090016;
	static final int NCRYPT_ALLOW_DECRYPT_FLAG = 0x00000001;
	static final int NCRYPT_UI_PROTECT_KEY_FLAG = 0x00000001;
	static final int NCRYPT_PAD_OAEP_FLAG = 0x00000004;
	static final int NCRYPT_SILENT_FLAG = 0x00000040;
	static final int LANG_NEUTRAL = 0x0000;
	static final int LANG_ENGLISH = 0x0009;
	static final int SUBLANG_ENGLISH_US = 0x0001;
	static final int SUBLANG_NEUTRAL = 0x0000;
	static final int ENGLISH_US = makeLangId(LANG_ENGLISH, SUBLANG_ENGLISH_US);
	static final int NEUTRAL_LANGUAGE = makeLangId(LANG_NEUTRAL, SUBLANG_NEUTRAL);
	static final String MS_PLATFORM_CRYPTO_PROVIDER = "Microsoft Platform Crypto Provider";
	static final String MS_KEY_STORAGE_PROVIDER = "Microsoft Software Key Storage Provider";
	static final String NCRYPT_RSA_ALGORITHM = "RSA";
	static final String BCRYPT_SHA256_ALGORITHM = "SHA256";
	static final String NCRYPT_LENGTH_PROPERTY = "Length";
	static final String NCRYPT_KEY_USAGE_PROPERTY = "Key Usage";
	static final String NCRYPT_UI_POLICY_PROPERTY = "UI Policy";

	static final ValueLayout.OfInt DWORD = ValueLayout.JAVA_INT;
	static final ValueLayout.OfInt BOOL = ValueLayout.JAVA_INT;
	static final ValueLayout.OfLong LONG_PTR = ValueLayout.JAVA_LONG;
	static final AddressLayout HANDLE = ValueLayout.ADDRESS;
	static final AddressLayout HWND = ValueLayout.ADDRESS;

	static final MemoryLayout FILETIME = MemoryLayout.structLayout(
			DWORD.withName("dwLowDateTime"),
			DWORD.withName("dwHighDateTime")
	).withName("FILETIME");
	static final MemoryLayout RM_UNIQUE_PROCESS = MemoryLayout.structLayout(
			DWORD.withName("dwProcessId"),
			FILETIME.withName("ProcessStartTime")
	).withName("RM_UNIQUE_PROCESS");
	static final MemoryLayout RM_PROCESS_INFO = MemoryLayout.structLayout(
			RM_UNIQUE_PROCESS.withName("Process"),
			MemoryLayout.sequenceLayout(256, ValueLayout.JAVA_CHAR).withName("strAppName"),
			MemoryLayout.sequenceLayout(64, ValueLayout.JAVA_CHAR).withName("strServiceShortName"),
			DWORD.withName("ApplicationType"),
			DWORD.withName("AppStatus"),
			DWORD.withName("TSSessionId"),
			BOOL.withName("bRestartable")
	).withName("RM_PROCESS_INFO");
	static final MemoryLayout NCRYPT_UI_POLICY = MemoryLayout.structLayout(
			DWORD.withName("dwVersion"),
			DWORD.withName("dwFlags"),
			ValueLayout.ADDRESS.withName("pszCreationTitle"),
			ValueLayout.ADDRESS.withName("pszFriendlyName"),
			ValueLayout.ADDRESS.withName("pszDescription")
	).withName("NCRYPT_UI_POLICY");
	static final MemoryLayout BCRYPT_OAEP_PADDING_INFO = createOaepPaddingInfoLayout();

	private static final Linker LINKER = Linker.nativeLinker();
	private static final SymbolLookup RSTRTMGR = SymbolLookup.libraryLookup("rstrtmgr", Arena.global());
	private static final SymbolLookup KERNEL32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
	private static final SymbolLookup USER32 = SymbolLookup.libraryLookup("user32", Arena.global());
	private static final SymbolLookup NCRYPT = SymbolLookup.libraryLookup("ncrypt", Arena.global());

	private static final MethodHandle RM_START_SESSION = downcall(RSTRTMGR, "RmStartSession",
			FunctionDescriptor.of(DWORD, ValueLayout.ADDRESS, DWORD, ValueLayout.ADDRESS));
	private static final MethodHandle RM_REGISTER_RESOURCES = downcall(RSTRTMGR, "RmRegisterResources",
			FunctionDescriptor.of(DWORD, DWORD, DWORD, ValueLayout.ADDRESS, DWORD, ValueLayout.ADDRESS, DWORD, ValueLayout.ADDRESS));
	private static final MethodHandle RM_GET_LIST = downcall(RSTRTMGR, "RmGetList",
			FunctionDescriptor.of(DWORD, DWORD, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	private static final MethodHandle RM_END_SESSION = downcall(RSTRTMGR, "RmEndSession",
			FunctionDescriptor.of(DWORD, DWORD));
	private static final MethodHandle OPEN_PROCESS = downcall(KERNEL32, "OpenProcess",
			FunctionDescriptor.of(HANDLE, DWORD, BOOL, DWORD));
	private static final MethodHandle CLOSE_HANDLE = downcall(KERNEL32, "CloseHandle",
			FunctionDescriptor.of(BOOL, HANDLE));
	private static final MethodHandle GET_PROCESS_TIMES = downcall(KERNEL32, "GetProcessTimes",
			FunctionDescriptor.of(BOOL, HANDLE, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	private static final MethodHandle COMPARE_FILE_TIME = downcall(KERNEL32, "CompareFileTime",
			FunctionDescriptor.of(DWORD, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	private static final MethodHandle GET_LAST_ERROR = downcall(KERNEL32, "GetLastError",
			FunctionDescriptor.of(DWORD));
	private static final MethodHandle FORMAT_MESSAGE_W = downcall(KERNEL32, "FormatMessageW",
			FunctionDescriptor.of(DWORD, DWORD, ValueLayout.ADDRESS, DWORD, DWORD, ValueLayout.ADDRESS, DWORD, ValueLayout.ADDRESS));
	private static final MethodHandle LOCAL_FREE = downcall(KERNEL32, "LocalFree",
			FunctionDescriptor.of(HANDLE, HANDLE));
	private static final MethodHandle ENUM_WINDOWS = downcall(USER32, "EnumWindows",
			FunctionDescriptor.of(BOOL, ValueLayout.ADDRESS, LONG_PTR));
	private static final MethodHandle GET_WINDOW_THREAD_PROCESS_ID = downcall(USER32, "GetWindowThreadProcessId",
			FunctionDescriptor.of(DWORD, HWND, ValueLayout.ADDRESS));
	private static final MethodHandle GET_WINDOW = downcall(USER32, "GetWindow",
			FunctionDescriptor.of(HWND, HWND, DWORD));
	private static final MethodHandle IS_WINDOW_VISIBLE = downcall(USER32, "IsWindowVisible",
			FunctionDescriptor.of(BOOL, HWND));
	private static final MethodHandle GET_WINDOW_TEXT_LENGTH_W = downcall(USER32, "GetWindowTextLengthW",
			FunctionDescriptor.of(DWORD, HWND));
	private static final MethodHandle GET_WINDOW_TEXT_W = downcall(USER32, "GetWindowTextW",
			FunctionDescriptor.of(DWORD, HWND, ValueLayout.ADDRESS, DWORD));
	private static final MethodHandle NCRYPT_OPEN_STORAGE_PROVIDER = downcall(NCRYPT, "NCryptOpenStorageProvider",
			FunctionDescriptor.of(DWORD, ValueLayout.ADDRESS, ValueLayout.ADDRESS, DWORD));
	private static final MethodHandle NCRYPT_OPEN_KEY = downcall(NCRYPT, "NCryptOpenKey",
			FunctionDescriptor.of(DWORD, HANDLE, ValueLayout.ADDRESS, ValueLayout.ADDRESS, DWORD, DWORD));
	private static final MethodHandle NCRYPT_CREATE_PERSISTED_KEY = downcall(NCRYPT, "NCryptCreatePersistedKey",
			FunctionDescriptor.of(DWORD, HANDLE, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, DWORD, DWORD));
	private static final MethodHandle NCRYPT_SET_PROPERTY = downcall(NCRYPT, "NCryptSetProperty",
			FunctionDescriptor.of(DWORD, HANDLE, ValueLayout.ADDRESS, ValueLayout.ADDRESS, DWORD, DWORD));
	private static final MethodHandle NCRYPT_FINALIZE_KEY = downcall(NCRYPT, "NCryptFinalizeKey",
			FunctionDescriptor.of(DWORD, HANDLE, DWORD));
	private static final MethodHandle NCRYPT_ENCRYPT = downcall(NCRYPT, "NCryptEncrypt",
			FunctionDescriptor.of(DWORD, HANDLE, ValueLayout.ADDRESS, DWORD, ValueLayout.ADDRESS, ValueLayout.ADDRESS, DWORD, ValueLayout.ADDRESS, DWORD));
	private static final MethodHandle NCRYPT_DECRYPT = downcall(NCRYPT, "NCryptDecrypt",
			FunctionDescriptor.of(DWORD, HANDLE, ValueLayout.ADDRESS, DWORD, ValueLayout.ADDRESS, ValueLayout.ADDRESS, DWORD, ValueLayout.ADDRESS, DWORD));
	private static final MethodHandle NCRYPT_FREE_OBJECT = downcall(NCRYPT, "NCryptFreeObject",
			FunctionDescriptor.of(DWORD, HANDLE));
	private static final MethodHandle NCRYPT_DELETE_KEY = downcall(NCRYPT, "NCryptDeleteKey",
			FunctionDescriptor.of(DWORD, HANDLE, DWORD));

	private Win32() {
	}

	static int rmStartSession(Arena arena, int sessionKeyLength) throws Throwable {
		MemorySegment session = arena.allocate(DWORD);
		MemorySegment key = arena.allocate((long) (sessionKeyLength + 1) * Character.BYTES, Character.BYTES);
		int result = (int) RM_START_SESSION.invokeExact(session, 0, key);
		checkRestartManager("RmStartSession", result);
		return session.get(DWORD, 0);
	}

	static void rmRegisterResources(int session, Arena arena, String path) throws Throwable {
		MemorySegment pathString = allocateWideString(arena, path);
		MemorySegment filePathPointer = arena.allocate(ValueLayout.ADDRESS);
		filePathPointer.set(ValueLayout.ADDRESS, 0, pathString);
		int result = (int) RM_REGISTER_RESOURCES.invokeExact(session, 1, filePathPointer, 0, MemorySegment.NULL, 0, MemorySegment.NULL);
		checkRestartManager("RmRegisterResources", result);
	}

	static int rmGetList(int session, MemorySegment procInfoNeeded, MemorySegment procInfo, MemorySegment processes, MemorySegment rebootReasons) throws Throwable {
		int result = (int) RM_GET_LIST.invokeExact(session, procInfoNeeded, procInfo, processes, rebootReasons);

		if (result != ERROR_MORE_DATA) {
			checkRestartManager("RmGetList", result);
		}

		return result;
	}

	static void rmEndSession(int session) throws Throwable {
		int result = (int) RM_END_SESSION.invokeExact(session);
		checkRestartManager("RmEndSession", result);
	}

	static MemorySegment openProcess(int access, int inheritHandle, int pid) throws Throwable {
		return (MemorySegment) OPEN_PROCESS.invokeExact(access, inheritHandle, pid);
	}

	static int closeHandle(MemorySegment handle) throws Throwable {
		return (int) CLOSE_HANDLE.invokeExact(handle);
	}

	static @Nullable ProcessTimes getProcessTimes(Arena arena, MemorySegment process) throws Throwable {
		MemorySegment createTime = arena.allocate(FILETIME);
		MemorySegment exitTime = arena.allocate(FILETIME);
		MemorySegment kernelTime = arena.allocate(FILETIME);
		MemorySegment userTime = arena.allocate(FILETIME);

		if ((int) GET_PROCESS_TIMES.invokeExact(process, createTime, exitTime, kernelTime, userTime) == 0) {
			return null;
		}

		return new ProcessTimes(createTime, exitTime, kernelTime, userTime);
	}

	static int compareFileTime(MemorySegment first, MemorySegment second) throws Throwable {
		return (int) COMPARE_FILE_TIME.invokeExact(first, second);
	}

	static int getLastError() throws Throwable {
		return (int) GET_LAST_ERROR.invokeExact();
	}

	static boolean enumWindows(MemorySegment callback, long data) throws Throwable {
		boolean result = (int) ENUM_WINDOWS.invokeExact(callback, data) != 0;

		if (!result) {
			throwLastError("EnumWindows");
		}

		return true;
	}

	static long getWindowThreadProcessId(MemorySegment hwnd) throws Throwable {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment pid = arena.allocate(DWORD);
			int ignored = (int) GET_WINDOW_THREAD_PROCESS_ID.invokeExact(hwnd, pid);
			return Integer.toUnsignedLong(pid.get(DWORD, 0));
		}
	}

	static MemorySegment getWindow(MemorySegment hwnd, int command) throws Throwable {
		return (MemorySegment) GET_WINDOW.invokeExact(hwnd, command);
	}

	static boolean isWindowVisible(MemorySegment hwnd) throws Throwable {
		return (int) IS_WINDOW_VISIBLE.invokeExact(hwnd) != 0;
	}

	static Optional<String> getWindowText(MemorySegment hwnd, Arena arena) throws Throwable {
		int length = (int) GET_WINDOW_TEXT_LENGTH_W.invokeExact(hwnd);

		if (length == 0) {
			return Optional.empty();
		}

		MemorySegment buffer = arena.allocate((long) (length + 1) * Character.BYTES, Character.BYTES);
		int copied = (int) GET_WINDOW_TEXT_W.invokeExact(hwnd, buffer, length + 1);

		if (copied == 0) {
			return Optional.empty();
		}

		return Optional.of(readWideString(buffer, copied));
	}

	static MemorySegment ncryptOpenStorageProvider(Arena arena, String providerName, int flags) throws Throwable {
		MemorySegment providerPointer = arena.allocate(ValueLayout.ADDRESS);
		MemorySegment providerNameString = allocateWideString(arena, providerName);
		int result = (int) NCRYPT_OPEN_STORAGE_PROVIDER.invokeExact(providerPointer, providerNameString, flags);
		checkNCrypt("NCryptOpenStorageProvider", result);
		return providerPointer.get(ValueLayout.ADDRESS, 0);
	}

	static MemorySegment ncryptOpenKey(Arena arena, MemorySegment provider, String keyName, int flags) throws Throwable {
		MemorySegment keyPointer = arena.allocate(ValueLayout.ADDRESS);
		MemorySegment keyNameString = allocateWideString(arena, keyName);
		int result = (int) NCRYPT_OPEN_KEY.invokeExact(provider, keyPointer, keyNameString, 0, flags);

		if (result == NTE_BAD_KEYSET) {
			return MemorySegment.NULL;
		}

		checkNCrypt("NCryptOpenKey", result);
		return keyPointer.get(ValueLayout.ADDRESS, 0);
	}

	static MemorySegment ncryptCreatePersistedKey(Arena arena, MemorySegment provider, String keyName, int flags) throws Throwable {
		MemorySegment keyPointer = arena.allocate(ValueLayout.ADDRESS);
		MemorySegment algorithmString = allocateWideString(arena, NCRYPT_RSA_ALGORITHM);
		MemorySegment keyNameString = allocateWideString(arena, keyName);
		int result = (int) NCRYPT_CREATE_PERSISTED_KEY.invokeExact(provider, keyPointer, algorithmString, keyNameString, 0, flags);

		if (result == NTE_EXISTS) {
			return MemorySegment.NULL;
		}

		checkNCrypt("NCryptCreatePersistedKey", result);
		return keyPointer.get(ValueLayout.ADDRESS, 0);
	}

	static void ncryptSetDwordProperty(Arena arena, MemorySegment key, String propertyName, int value) throws Throwable {
		MemorySegment valueSegment = arena.allocate(DWORD);
		valueSegment.set(DWORD, 0, value);
		ncryptSetProperty(arena, key, propertyName, valueSegment);
	}

	static void ncryptSetUiPolicy(Arena arena, MemorySegment key, String creationTitle, String friendlyName, String description) throws Throwable {
		MemorySegment policy = arena.allocate(NCRYPT_UI_POLICY);
		policy.set(DWORD, NCRYPT_UI_POLICY.byteOffset(MemoryLayout.PathElement.groupElement("dwVersion")), 1);
		policy.set(DWORD, NCRYPT_UI_POLICY.byteOffset(MemoryLayout.PathElement.groupElement("dwFlags")), NCRYPT_UI_PROTECT_KEY_FLAG);
		policy.set(ValueLayout.ADDRESS, NCRYPT_UI_POLICY.byteOffset(MemoryLayout.PathElement.groupElement("pszCreationTitle")), allocateWideString(arena, creationTitle));
		policy.set(ValueLayout.ADDRESS, NCRYPT_UI_POLICY.byteOffset(MemoryLayout.PathElement.groupElement("pszFriendlyName")), allocateWideString(arena, friendlyName));
		policy.set(ValueLayout.ADDRESS, NCRYPT_UI_POLICY.byteOffset(MemoryLayout.PathElement.groupElement("pszDescription")), allocateWideString(arena, description));
		ncryptSetProperty(arena, key, NCRYPT_UI_POLICY_PROPERTY, policy);
	}

	static void ncryptFinalizeKey(MemorySegment key, int flags) throws Throwable {
		int result = (int) NCRYPT_FINALIZE_KEY.invokeExact(key, flags);
		checkNCrypt("NCryptFinalizeKey", result);
	}

	static byte[] ncryptEncrypt(Arena arena, MemorySegment key, byte[] input) throws Throwable {
		return ncryptCrypt(arena, NCRYPT_ENCRYPT, "NCryptEncrypt", key, input, 0);
	}

	static byte[] ncryptDecrypt(Arena arena, MemorySegment key, byte[] input, int flags) throws Throwable {
		MemorySegment inputSegment = arena.allocate(input.length);
		MemorySegment.copy(input, 0, inputSegment, ValueLayout.JAVA_BYTE, 0, input.length);
		MemorySegment output = arena.allocate(input.length);
		MemorySegment outputSize = arena.allocate(DWORD);
		int result = (int) NCRYPT_DECRYPT.invokeExact(key, inputSegment, input.length, createOaepPaddingInfo(arena), output, input.length, outputSize, flags | NCRYPT_PAD_OAEP_FLAG);
		checkNCrypt("NCryptDecrypt", result);
		return output.asSlice(0, Integer.toUnsignedLong(outputSize.get(DWORD, 0))).toArray(ValueLayout.JAVA_BYTE);
	}

	static void ncryptFreeObject(MemorySegment object) throws Throwable {
		int result = (int) NCRYPT_FREE_OBJECT.invokeExact(object);
		checkNCrypt("NCryptFreeObject", result);
	}

	static void ncryptDeleteKey(MemorySegment key, int flags) throws Throwable {
		int result = (int) NCRYPT_DELETE_KEY.invokeExact(key, flags);
		checkNCrypt("NCryptDeleteKey", result);
	}

	static MemorySegment upcallStub(Object target, String methodName, FunctionDescriptor descriptor, Arena arena) {
		try {
			return LINKER.upcallStub(MethodHandles.lookup().findVirtual(target.getClass(), methodName, descriptor.toMethodType()).bindTo(target), descriptor, arena);
		} catch (ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	static MemorySegment allocateWideString(Arena arena, String value) {
		byte[] bytes = (value + "\0").getBytes(StandardCharsets.UTF_16LE);
		MemorySegment segment = arena.allocate(bytes.length, 2);
		MemorySegment.copy(bytes, 0, segment, ValueLayout.JAVA_BYTE, 0, bytes.length);
		return segment;
	}

	static String readWideString(MemorySegment segment, int length) {
		byte[] bytes = segment.reinterpret((long) length * Character.BYTES).toArray(ValueLayout.JAVA_BYTE);
		return new String(bytes, StandardCharsets.UTF_16LE);
	}

	static @Nullable String tryFormatMessage(int errorCode) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment messagePointer = arena.allocate(ValueLayout.ADDRESS);
			int chars = formatMessage(errorCode, ENGLISH_US, messagePointer);

			if (chars == 0) {
				chars = formatMessage(errorCode, NEUTRAL_LANGUAGE, messagePointer);
			}

			if (chars == 0) {
				return null;
			}

			MemorySegment message = messagePointer.get(ValueLayout.ADDRESS, 0).reinterpret((long) chars * Character.BYTES);

			try {
				return readWideString(message, chars).strip();
			} finally {
				MemorySegment ignored = (MemorySegment) LOCAL_FREE.invokeExact(messagePointer.get(ValueLayout.ADDRESS, 0));
			}
		} catch (Throwable e) {
			return null;
		}
	}

	private static int formatMessage(int errorCode, int languageId, MemorySegment messagePointer) throws Throwable {
		final int flags = FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS;
		return (int) FORMAT_MESSAGE_W.invokeExact(flags, MemorySegment.NULL, errorCode, languageId, messagePointer, 0, MemorySegment.NULL);
	}

	private static void ncryptSetProperty(Arena arena, MemorySegment key, String propertyName, MemorySegment value) throws Throwable {
		MemorySegment propertyNameString = allocateWideString(arena, propertyName);
		int result = (int) NCRYPT_SET_PROPERTY.invokeExact(key, propertyNameString, value, Math.toIntExact(value.byteSize()), 0);
		checkNCrypt("NCryptSetProperty(" + propertyName + ")", result);
	}

	private static byte[] ncryptCrypt(Arena arena, MethodHandle operation, String operationName, MemorySegment key, byte[] input, int flags) throws Throwable {
		MemorySegment inputSegment = arena.allocate(input.length);
		MemorySegment.copy(input, 0, inputSegment, ValueLayout.JAVA_BYTE, 0, input.length);
		MemorySegment paddingInfo = createOaepPaddingInfo(arena);
		MemorySegment outputSize = arena.allocate(DWORD);
		int paddedFlags = flags | NCRYPT_PAD_OAEP_FLAG;
		int result = (int) operation.invokeExact(key, inputSegment, input.length, paddingInfo, MemorySegment.NULL, 0, outputSize, paddedFlags);
		checkNCrypt(operationName + "(size)", result);

		int size = outputSize.get(DWORD, 0);
		MemorySegment output = arena.allocate(size);
		result = (int) operation.invokeExact(key, inputSegment, input.length, paddingInfo, output, size, outputSize, paddedFlags);
		checkNCrypt(operationName, result);
		return output.asSlice(0, Integer.toUnsignedLong(outputSize.get(DWORD, 0))).toArray(ValueLayout.JAVA_BYTE);
	}

	private static MemorySegment createOaepPaddingInfo(Arena arena) {
		MemorySegment paddingInfo = arena.allocate(BCRYPT_OAEP_PADDING_INFO);
		paddingInfo.set(ValueLayout.ADDRESS, BCRYPT_OAEP_PADDING_INFO.byteOffset(MemoryLayout.PathElement.groupElement("pszAlgId")), allocateWideString(arena, BCRYPT_SHA256_ALGORITHM));
		paddingInfo.set(ValueLayout.ADDRESS, BCRYPT_OAEP_PADDING_INFO.byteOffset(MemoryLayout.PathElement.groupElement("pbLabel")), MemorySegment.NULL);
		paddingInfo.set(DWORD, BCRYPT_OAEP_PADDING_INFO.byteOffset(MemoryLayout.PathElement.groupElement("cbLabel")), 0);
		return paddingInfo;
	}

	private static void checkRestartManager(String operation, int error) throws LoomNativePlatformException {
		if (error != ERROR_SUCCESS) {
			throw LoomNativePlatformException.fromWin32Error(operation, error);
		}
	}

	private static void checkNCrypt(String operation, int error) throws LoomNativePlatformException {
		if (error != ERROR_SUCCESS) {
			throw LoomNativePlatformException.fromWin32Error(operation, error);
		}
	}

	private static void throwLastError(String operation) throws Throwable {
		throw LoomNativePlatformException.fromWin32Error(operation, getLastError());
	}

	private static int makeLangId(int primaryLanguage, int subLanguage) {
		return (subLanguage << 10) | primaryLanguage;
	}

	private static MemoryLayout createOaepPaddingInfoLayout() {
		if (ValueLayout.ADDRESS.byteSize() == Long.BYTES) {
			return MemoryLayout.structLayout(
					ValueLayout.ADDRESS.withName("pszAlgId"),
					ValueLayout.ADDRESS.withName("pbLabel"),
					DWORD.withName("cbLabel"),
					MemoryLayout.paddingLayout(Integer.BYTES)
			).withName("BCRYPT_OAEP_PADDING_INFO");
		}

		return MemoryLayout.structLayout(
				ValueLayout.ADDRESS.withName("pszAlgId"),
				ValueLayout.ADDRESS.withName("pbLabel"),
				DWORD.withName("cbLabel")
		).withName("BCRYPT_OAEP_PADDING_INFO");
	}

	private static MethodHandle downcall(SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
		Optional<MemorySegment> symbol = lookup.find(name);

		if (symbol.isEmpty()) {
			throw new ExceptionInInitializerError("Could not find Win32 symbol: " + name);
		}

		return LINKER.downcallHandle(symbol.get(), descriptor);
	}

	record ProcessTimes(MemorySegment createTime, MemorySegment exitTime, MemorySegment kernelTime, MemorySegment userTime) {
	}
}
