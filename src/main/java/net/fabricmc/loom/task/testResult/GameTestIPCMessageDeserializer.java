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

package net.fabricmc.loom.task.testResult;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.LoomGradlePlugin;

public record GameTestIPCMessageDeserializer(Consumer<GameTestIPCMessage> messageConsumer) implements Consumer<String> {
	private static final Map<String, Function<JsonObject, GameTestIPCMessage>> MESSAGE_TYPES = Map.of(
			"push_group", GameTestIPCMessageDeserializer::pushGroup,
			"pop_group", GameTestIPCMessageDeserializer::popGroup,
			"start_test", GameTestIPCMessageDeserializer::startTest,
			"complete_test", GameTestIPCMessageDeserializer::completeTest
	);
	private static final Logger LOGGER = LoggerFactory.getLogger(GameTestIPCMessageDeserializer.class);

	@Override
	public void accept(String message) {
		LOGGER.debug("Received IPC message: {}", message);

		JsonObject json = LoomGradlePlugin.GSON.fromJson(message, JsonObject.class);
		int version = getInt(json, "version");

		if (version != 1) {
			throw new GameTestMessageException("Unsupported IPC message version: %d. Try updating Loom.".formatted(version));
		}

		String type = getString(json, "type");

		if (!MESSAGE_TYPES.containsKey(type)) {
			throw new GameTestMessageException("Unknown IPC message type: %s.".formatted(type));
		}

		GameTestIPCMessage messageObject = MESSAGE_TYPES.get(type).apply(json);
		messageConsumer.accept(messageObject);

		LOGGER.debug("Processed IPC message: {}", messageObject);
	}

	private static GameTestIPCMessage.PushGroup pushGroup(JsonObject json) {
		return new GameTestIPCMessage.PushGroup(getString(json, "name"));
	}

	private static GameTestIPCMessage.PopGroup popGroup(JsonObject json) {
		return new GameTestIPCMessage.PopGroup();
	}

	private static GameTestIPCMessage.StartTest startTest(JsonObject json) {
		return new GameTestIPCMessage.StartTest(getString(json, "name"));
	}

	private static GameTestIPCMessage.CompleteTest completeTest(JsonObject json) {
		String result = getString(json, "result");
		return new GameTestIPCMessage.CompleteTest(getResult(result));
	}

	private static GameTestIPCMessage.TestResult getResult(String result) {
		return switch (result) {
		case "succeeded" -> GameTestIPCMessage.TestResult.SUCCEEDED;
		case "failed" -> GameTestIPCMessage.TestResult.FAILED;
		default -> throw new GameTestMessageException("Unknown test result: %s.".formatted(result));
		};
	}

	private static int getInt(JsonObject json, String key) {
		if (json.has(key)) {
			return json.get(key).getAsInt();
		} else {
			throw new GameTestMessageException("Missing key: " + key);
		}
	}

	private static String getString(JsonObject json, String key) {
		if (json.has(key)) {
			return json.get(key).getAsString();
		} else {
			throw new GameTestMessageException("Missing key: " + key);
		}
	}

	public static class GameTestMessageException extends RuntimeException {
		public GameTestMessageException(String message) {
			super(message);
		}
	}
}
