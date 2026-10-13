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
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.fabricmc.loom.util.nativeplatform.EncryptionKeyStore.UserInteraction;

final class MacOS {
	private static final int ERR_SEC_SUCCESS = 0;
	private static final int ERR_SEC_DUPLICATE_ITEM = -25299;
	private static final int ERR_SEC_ITEM_NOT_FOUND = -25300;
	private static final int CF_STRING_ENCODING_UTF_8 = 0x08000100;

	private static final Linker LINKER = Linker.nativeLinker();
	private static final SymbolLookup CORE_FOUNDATION = SymbolLookup.libraryLookup(
			"/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", Arena.global());
	private static final SymbolLookup SECURITY = SymbolLookup.libraryLookup(
			"/System/Library/Frameworks/Security.framework/Security", Arena.global());

	private static final MethodHandle CF_ARRAY_CREATE = downcall(CORE_FOUNDATION, "CFArrayCreate",
			FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
	private static final MethodHandle CF_DATA_CREATE = downcall(CORE_FOUNDATION, "CFDataCreate",
			FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
	private static final MethodHandle CF_DATA_GET_BYTE_PTR = downcall(CORE_FOUNDATION, "CFDataGetBytePtr",
			FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	private static final MethodHandle CF_DATA_GET_LENGTH = downcall(CORE_FOUNDATION, "CFDataGetLength",
			FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
	private static final MethodHandle CF_DICTIONARY_CREATE_MUTABLE = downcall(CORE_FOUNDATION, "CFDictionaryCreateMutable",
			FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	private static final MethodHandle CF_DICTIONARY_SET_VALUE = downcall(CORE_FOUNDATION, "CFDictionarySetValue",
			FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	private static final MethodHandle CF_RELEASE = downcall(CORE_FOUNDATION, "CFRelease",
			FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
	private static final MethodHandle CF_STRING_CREATE_WITH_C_STRING = downcall(CORE_FOUNDATION, "CFStringCreateWithCString",
			FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
	private static final MethodHandle CF_STRING_GET_C_STRING = downcall(CORE_FOUNDATION, "CFStringGetCString",
			FunctionDescriptor.of(ValueLayout.JAVA_BYTE, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
	private static final MethodHandle CF_STRING_GET_LENGTH = downcall(CORE_FOUNDATION, "CFStringGetLength",
			FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
	private static final MethodHandle CF_STRING_GET_MAXIMUM_SIZE_FOR_ENCODING = downcall(CORE_FOUNDATION, "CFStringGetMaximumSizeForEncoding",
			FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));

	private static final MethodHandle SEC_ACCESS_CREATE = downcall(SECURITY, "SecAccessCreate",
			FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	private static final MethodHandle SEC_COPY_ERROR_MESSAGE_STRING = downcall(SECURITY, "SecCopyErrorMessageString",
			FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
	private static final MethodHandle SEC_ITEM_ADD = downcall(SECURITY, "SecItemAdd",
			FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	private static final MethodHandle SEC_ITEM_COPY_MATCHING = downcall(SECURITY, "SecItemCopyMatching",
			FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	private static final MethodHandle SEC_ITEM_DELETE = downcall(SECURITY, "SecItemDelete",
			FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

	private static final MemorySegment CF_BOOLEAN_TRUE = globalReference(CORE_FOUNDATION, "kCFBooleanTrue");
	private static final MemorySegment CF_TYPE_ARRAY_CALLBACKS = symbol(CORE_FOUNDATION, "kCFTypeArrayCallBacks");
	private static final MemorySegment CF_TYPE_DICTIONARY_KEY_CALLBACKS = symbol(CORE_FOUNDATION, "kCFTypeDictionaryKeyCallBacks");
	private static final MemorySegment CF_TYPE_DICTIONARY_VALUE_CALLBACKS = symbol(CORE_FOUNDATION, "kCFTypeDictionaryValueCallBacks");
	private static final MemorySegment SEC_ATTR_ACCESS = globalReference(SECURITY, "kSecAttrAccess");
	private static final MemorySegment SEC_ATTR_ACCOUNT = globalReference(SECURITY, "kSecAttrAccount");
	private static final MemorySegment SEC_ATTR_LABEL = globalReference(SECURITY, "kSecAttrLabel");
	private static final MemorySegment SEC_ATTR_SERVICE = globalReference(SECURITY, "kSecAttrService");
	private static final MemorySegment SEC_CLASS = globalReference(SECURITY, "kSecClass");
	private static final MemorySegment SEC_CLASS_GENERIC_PASSWORD = globalReference(SECURITY, "kSecClassGenericPassword");
	private static final MemorySegment SEC_MATCH_LIMIT = globalReference(SECURITY, "kSecMatchLimit");
	private static final MemorySegment SEC_MATCH_LIMIT_ONE = globalReference(SECURITY, "kSecMatchLimitOne");
	private static final MemorySegment SEC_RETURN_DATA = globalReference(SECURITY, "kSecReturnData");
	private static final MemorySegment SEC_USE_AUTHENTICATION_UI = globalReference(SECURITY, "kSecUseAuthenticationUI");
	private static final MemorySegment SEC_USE_AUTHENTICATION_UI_ALLOW = globalReference(SECURITY, "kSecUseAuthenticationUIAllow");
	private static final MemorySegment SEC_USE_AUTHENTICATION_UI_FAIL = globalReference(SECURITY, "kSecUseAuthenticationUIFail");
	private static final MemorySegment SEC_VALUE_DATA = globalReference(SECURITY, "kSecValueData");

	private MacOS() {
	}

	static boolean add(String serviceName, String accountName, String description, byte[] value, UserInteraction userInteraction) throws Throwable {
		try (Arena arena = Arena.ofConfined();
				CFObject service = createString(arena, serviceName);
				CFObject account = createString(arena, accountName);
				CFObject label = createString(arena, description);
				CFObject data = createData(arena, value);
				CFObject access = createAccess(arena, label, userInteraction);
				CFObject query = createDictionary()) {
			put(query, SEC_CLASS, SEC_CLASS_GENERIC_PASSWORD);
			put(query, SEC_ATTR_SERVICE, service.segment());
			put(query, SEC_ATTR_ACCOUNT, account.segment());
			put(query, SEC_ATTR_LABEL, label.segment());
			put(query, SEC_ATTR_ACCESS, access.segment());
			put(query, SEC_VALUE_DATA, data.segment());
			putAuthenticationUi(query, userInteraction);

			int status = (int) SEC_ITEM_ADD.invokeExact(query.segment(), MemorySegment.NULL);

			if (status == ERR_SEC_DUPLICATE_ITEM) {
				return false;
			}

			checkStatus("SecItemAdd", status);
			return true;
		}
	}

	static @Nullable byte[] read(String serviceName, String accountName, UserInteraction userInteraction) throws Throwable {
		try (Arena arena = Arena.ofConfined();
				CFObject service = createString(arena, serviceName);
				CFObject account = createString(arena, accountName);
				CFObject query = createDictionary()) {
			put(query, SEC_CLASS, SEC_CLASS_GENERIC_PASSWORD);
			put(query, SEC_ATTR_SERVICE, service.segment());
			put(query, SEC_ATTR_ACCOUNT, account.segment());
			put(query, SEC_MATCH_LIMIT, SEC_MATCH_LIMIT_ONE);
			put(query, SEC_RETURN_DATA, CF_BOOLEAN_TRUE);
			putAuthenticationUi(query, userInteraction);

			MemorySegment result = arena.allocate(ValueLayout.ADDRESS);
			int status = (int) SEC_ITEM_COPY_MATCHING.invokeExact(query.segment(), result);

			if (status == ERR_SEC_ITEM_NOT_FOUND) {
				return null;
			}

			checkStatus("SecItemCopyMatching", status);
			MemorySegment data = result.get(ValueLayout.ADDRESS, 0);

			if (data.equals(MemorySegment.NULL)) {
				throw new LoomNativePlatformException("SecItemCopyMatching returned no Keychain data");
			}

			try (CFObject dataObject = new CFObject(data)) {
				return readData(dataObject.segment());
			}
		}
	}

	static void delete(String serviceName, String accountName, UserInteraction userInteraction) throws Throwable {
		try (Arena arena = Arena.ofConfined();
				CFObject service = createString(arena, serviceName);
				CFObject account = createString(arena, accountName);
				CFObject query = createDictionary()) {
			put(query, SEC_CLASS, SEC_CLASS_GENERIC_PASSWORD);
			put(query, SEC_ATTR_SERVICE, service.segment());
			put(query, SEC_ATTR_ACCOUNT, account.segment());
			putAuthenticationUi(query, userInteraction);

			int status = (int) SEC_ITEM_DELETE.invokeExact(query.segment());

			if (status != ERR_SEC_ITEM_NOT_FOUND) {
				checkStatus("SecItemDelete", status);
			}
		}
	}

	private static CFObject createAccess(Arena arena, CFObject description, UserInteraction userInteraction) throws Throwable {
		MemorySegment access = arena.allocate(ValueLayout.ADDRESS);
		MemorySegment trustedApplications = MemorySegment.NULL;

		if (userInteraction == UserInteraction.REQUIRED) {
			try (CFObject emptyTrustedApplications = createEmptyArray()) {
				int status = (int) SEC_ACCESS_CREATE.invokeExact(description.segment(), emptyTrustedApplications.segment(), access);
				checkStatus("SecAccessCreate", status);
			}
		} else {
			int status = (int) SEC_ACCESS_CREATE.invokeExact(description.segment(), trustedApplications, access);
			checkStatus("SecAccessCreate", status);
		}

		MemorySegment result = access.get(ValueLayout.ADDRESS, 0);

		if (result.equals(MemorySegment.NULL)) {
			throw new LoomNativePlatformException("SecAccessCreate returned no Keychain access object");
		}

		return new CFObject(result);
	}

	private static CFObject createData(Arena arena, byte[] value) throws Throwable {
		MemorySegment bytes = arena.allocate(value.length);
		MemorySegment.copy(value, 0, bytes, ValueLayout.JAVA_BYTE, 0, value.length);
		MemorySegment data = (MemorySegment) CF_DATA_CREATE.invokeExact(MemorySegment.NULL, bytes, (long) value.length);
		return requireObject("CFDataCreate", data);
	}

	private static CFObject createDictionary() throws Throwable {
		MemorySegment dictionary = (MemorySegment) CF_DICTIONARY_CREATE_MUTABLE.invokeExact(
				MemorySegment.NULL, 0L, CF_TYPE_DICTIONARY_KEY_CALLBACKS, CF_TYPE_DICTIONARY_VALUE_CALLBACKS);
		return requireObject("CFDictionaryCreateMutable", dictionary);
	}

	private static CFObject createEmptyArray() throws Throwable {
		MemorySegment array = (MemorySegment) CF_ARRAY_CREATE.invokeExact(
				MemorySegment.NULL, MemorySegment.NULL, 0L, CF_TYPE_ARRAY_CALLBACKS);
		return requireObject("CFArrayCreate", array);
	}

	private static CFObject createString(Arena arena, String value) throws Throwable {
		byte[] bytes = (value + '\0').getBytes(StandardCharsets.UTF_8);
		MemorySegment cString = arena.allocate(bytes.length);
		MemorySegment.copy(bytes, 0, cString, ValueLayout.JAVA_BYTE, 0, bytes.length);
		MemorySegment string = (MemorySegment) CF_STRING_CREATE_WITH_C_STRING.invokeExact(
				MemorySegment.NULL, cString, CF_STRING_ENCODING_UTF_8);
		return requireObject("CFStringCreateWithCString", string);
	}

	private static byte[] readData(MemorySegment data) throws Throwable {
		long length = (long) CF_DATA_GET_LENGTH.invokeExact(data);

		if (length < 0 || length > Integer.MAX_VALUE) {
			throw new LoomNativePlatformException("Keychain data has invalid length: " + length);
		}

		MemorySegment bytes = (MemorySegment) CF_DATA_GET_BYTE_PTR.invokeExact(data);

		if (length != 0 && bytes.equals(MemorySegment.NULL)) {
			throw new LoomNativePlatformException("CFDataGetBytePtr returned no Keychain data");
		}

		return bytes.reinterpret(length).toArray(ValueLayout.JAVA_BYTE);
	}

	private static void put(CFObject dictionary, MemorySegment key, MemorySegment value) throws Throwable {
		CF_DICTIONARY_SET_VALUE.invokeExact(dictionary.segment(), key, value);
	}

	private static void putAuthenticationUi(CFObject dictionary, UserInteraction userInteraction) throws Throwable {
		put(dictionary, SEC_USE_AUTHENTICATION_UI, userInteraction == UserInteraction.REQUIRED
				? SEC_USE_AUTHENTICATION_UI_ALLOW
				: SEC_USE_AUTHENTICATION_UI_FAIL);
	}

	private static void checkStatus(String operation, int status) throws LoomNativePlatformException {
		if (status != ERR_SEC_SUCCESS) {
			throw new LoomNativePlatformException(formatStatus(operation, status));
		}
	}

	private static String formatStatus(String operation, int status) {
		String message = tryCopyErrorMessage(status);
		String result = "%s failed: macOS Security error %d".formatted(operation, status);
		return message == null || message.isBlank() ? result : result + ": " + message;
	}

	private static @Nullable String tryCopyErrorMessage(int status) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment string = (MemorySegment) SEC_COPY_ERROR_MESSAGE_STRING.invokeExact(status, MemorySegment.NULL);

			if (string.equals(MemorySegment.NULL)) {
				return null;
			}

			try (CFObject message = new CFObject(string)) {
				long length = (long) CF_STRING_GET_LENGTH.invokeExact(message.segment());
				long maximumSize = (long) CF_STRING_GET_MAXIMUM_SIZE_FOR_ENCODING.invokeExact(length, CF_STRING_ENCODING_UTF_8);

				if (maximumSize < 0 || maximumSize == Long.MAX_VALUE) {
					return null;
				}

				MemorySegment buffer = arena.allocate(maximumSize + 1);
				byte copied = (byte) CF_STRING_GET_C_STRING.invokeExact(
						message.segment(), buffer, maximumSize + 1, CF_STRING_ENCODING_UTF_8);
				return copied == 0 ? null : buffer.getString(0);
			}
		} catch (Throwable e) {
			return null;
		}
	}

	private static CFObject requireObject(String operation, MemorySegment object) throws LoomNativePlatformException {
		if (object.equals(MemorySegment.NULL)) {
			throw new LoomNativePlatformException(operation + " returned null");
		}

		return new CFObject(object);
	}

	private static MemorySegment globalReference(SymbolLookup lookup, String name) {
		return symbol(lookup, name).reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0);
	}

	private static MemorySegment symbol(SymbolLookup lookup, String name) {
		return lookup.find(name).orElseThrow(() -> new ExceptionInInitializerError("Could not find macOS symbol: " + name));
	}

	private static MethodHandle downcall(SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
		Optional<MemorySegment> symbol = lookup.find(name);

		if (symbol.isEmpty()) {
			throw new ExceptionInInitializerError("Could not find macOS symbol: " + name);
		}

		return LINKER.downcallHandle(symbol.get(), descriptor);
	}

	private static final class CFObject implements AutoCloseable {
		private MemorySegment segment;

		private CFObject(MemorySegment segment) {
			this.segment = segment;
		}

		private MemorySegment segment() {
			return segment;
		}

		@Override
		public void close() {
			if (segment.equals(MemorySegment.NULL)) {
				return;
			}

			try {
				CF_RELEASE.invokeExact(segment);
			} catch (Throwable e) {
				throw new AssertionError("CFRelease failed", e);
			} finally {
				segment = MemorySegment.NULL;
			}
		}
	}
}
