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

package net.fabricmc.loom.task.launch.auth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loom.extension.LoomFiles;
import net.fabricmc.loom.util.EncryptedStringStore;
import net.fabricmc.loom.util.nativeplatform.EncryptionKeyStore;
import net.fabricmc.loom.util.nativeplatform.LoomNativePlatformException;

/// Stores the durable part of a Microsoft Minecraft login in an encrypted file.
///
/// The short-lived Minecraft access token is deliberately not persisted. It is obtained from the
/// refresh token each time the game starts.
public final class MicrosoftAccountStore {
	private static final int VERSION = 1;
	private static final Gson GSON = new Gson();

	private final Path path;
	private final EncryptionKeyStore keyStore;
	private final EncryptedStringStore encryptedStringStore;

	public MicrosoftAccountStore(Path path, EncryptionKeyStore keyStore) {
		this.path = Objects.requireNonNull(path, "path");
		this.keyStore = Objects.requireNonNull(keyStore, "keyStore");
		this.encryptedStringStore = new EncryptedStringStore(keyStore);
	}

	public static Path defaultPath(LoomFiles files) {
		Objects.requireNonNull(files, "files");
		return files.getUserCache().toPath().resolve("microsoft-auth.json");
	}

	public boolean exists() {
		return Files.exists(path);
	}

	public void prepare() throws LoomNativePlatformException {
		keyStore.prepare();
	}

	public Account read() throws IOException, LoomNativePlatformException {
		return parse(encryptedStringStore.read(path));
	}

	public void write(Account account) throws IOException, LoomNativePlatformException {
		Objects.requireNonNull(account, "account");
		Path parent = path.getParent();

		if (parent != null) {
			Files.createDirectories(parent);
		}

		StoredAccount storedAccount = new StoredAccount(
				VERSION,
				account.clientId(),
				account.refreshToken(),
				account.profileId(),
				account.profileName()
		);
		encryptedStringStore.write(path, GSON.toJson(storedAccount));
	}

	public void delete() throws IOException, LoomNativePlatformException {
		Files.deleteIfExists(path);
		keyStore.delete();
	}

	private static Account parse(String json) throws IOException {
		JsonObject object;

		try {
			JsonElement element = JsonParser.parseString(json);

			if (!element.isJsonObject()) {
				throw new IllegalStateException("Microsoft account must contain a JSON object");
			}

			object = element.getAsJsonObject();
		} catch (RuntimeException e) {
			throw new IOException("Invalid stored Microsoft account", e);
		}

		int version = parseVersion(object);

		if (version != VERSION) {
			throw new IOException("Unsupported stored Microsoft account version: " + version);
		}

		try {
			StoredAccount storedAccount = GSON.fromJson(object, StoredAccount.class);
			return new Account(
					storedAccount.clientId(),
					storedAccount.refreshToken(),
					storedAccount.profileId(),
					storedAccount.profileName()
			);
		} catch (RuntimeException e) {
			throw new IOException("Invalid stored Microsoft account", e);
		}
	}

	private static int parseVersion(JsonObject object) throws IOException {
		JsonElement version = object.get("version");

		if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()) {
			throw new IOException("Invalid stored Microsoft account version");
		}

		try {
			return Integer.parseInt(version.getAsString());
		} catch (NumberFormatException e) {
			throw new IOException("Invalid stored Microsoft account version", e);
		}
	}

	public record Account(String clientId, String refreshToken, String profileId, String profileName) {
		public Account {
			clientId = requireNonBlank(clientId, "clientId");
			refreshToken = requireNonBlank(refreshToken, "refreshToken");
			profileId = requireNonBlank(profileId, "profileId");
			profileName = requireNonBlank(profileName, "profileName");
		}

		public Account withRefreshToken(String refreshToken) {
			return new Account(clientId, refreshToken, profileId, profileName);
		}
	}

	private static String requireNonBlank(String value, String name) {
		Objects.requireNonNull(value, name);

		if (value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}

		return value;
	}

	private record StoredAccount(int version, String clientId, String refreshToken, String profileId, String profileName) {
		private StoredAccount {
			Objects.requireNonNull(clientId, "clientId");
			Objects.requireNonNull(refreshToken, "refreshToken");
			Objects.requireNonNull(profileId, "profileId");
			Objects.requireNonNull(profileName, "profileName");
		}
	}
}
