/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019-2025 FabricMC
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

package net.fabricmc.devlaunchinjector;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Target program wrapper for injecting additional arguments or system properties based on a config file.
 *
 * <p>The config format is line based, a line is a section if it isn't indented with spaces or tabs, otherwise a value.
 * Sections have to start with {@code common} or a specific environment, followed by {@code Args} for additional program
 * arguments or {@code Properties} for additional system properties. Arguments are trimmed and otherwise taken as-is,
 * properties get split into key+value at {@code =} if present, otherwise taken as a key with an empty string value.
 * Both key and value get trimmed.
 *
 * <p>Example invocation:
 * {@code java
 * -Dfabric.dli.env=client
 * -Dfabric.dli.main=something.pkg.Main
 * -Dfabric.dli.config=/home/user/some/config.cfg
 * -cp [...]
 * net.fabricmc.devlaunchinjector.Main
 * [pass-through args...]}
 *
 * <p>Example config:
 * <pre> {@code
 * commonProperties
 *   fabric.development=true
 * clientProperties
 *   java.library.path=/home/user/.gradle/caches/fabric-loom/natives/1.14.4
 *   org.lwjgl.librarypat=/home/user/.gradle/caches/fabric-loom/natives/1.14.4
 * clientArgs
 *   --assetIndex=1.14.4-1.14
 *   --assetsDir=/home/user/.gradle/caches/fabric-loom/assets
 * }</pre>
 */
public final class Main {
	public static void main(String[] args) throws Throwable {
		String env = System.clearProperty("fabric.dli.env"); // desired environment, for config section selection
		String main = System.clearProperty("fabric.dli.main"); // main class to invoke afterwards
		String config = System.clearProperty("fabric.dli.config"); // config file location
		Path configFile;

		if (main == null) {
			System.err.println("error: missing fabric.dli.main property, can't launch");
			System.exit(1);
		} else if (env == null || config == null) {
			warnNoop("missing fabric.dli.env or fabric.dli.config properties");
		} else if (!Files.isRegularFile(configFile = Paths.get(decodeEscaped(config)))
				|| !Files.isReadable(configFile)) {
			warnNoop("missing or unreadable config file ("+configFile+")");
		} else {
			List<String> extraArgs = new ArrayList<>();
			Map<String, String> extraProperties = new HashMap<>();

			try {
				parseConfig(configFile, env, extraArgs, extraProperties);

				// apply args
				String[] newArgs = extraArgs.toArray(new String[args.length + extraArgs.size()]);
				System.arraycopy(args, 0, newArgs, extraArgs.size(), args.length);
				args = newArgs;

				// apply properties
				for (Map.Entry<String, String> e : extraProperties.entrySet()) {
					System.setProperty(e.getKey(), e.getValue());
				}
			} catch (IOException e) {
				warnNoop("parsing failed: "+e.toString());
			}
		}

		// invoke via method handle to minimize extra stack frames
		MethodHandle handle = MethodHandles.publicLookup().findStatic(Class.forName(main), "main", MethodType.methodType(void.class, String[].class));
		handle.invokeExact(args);
	}

	private static void parseConfig(Path file, String env, List<String> extraArgs, Map<String, String> extraProperties) throws IOException {
		final int STATE_NONE = 0;
		final int STATE_ARGS = 1;
		final int STATE_PROPERTIES = 2;
		final int STATE_SKIP = 3;

		try (BufferedReader reader = Files.newBufferedReader(file)) {
			String line;
			int state = STATE_NONE;

			while ((line = reader.readLine()) != null) {
				if (line.isEmpty()) continue;

				boolean indented = line.charAt(0) == ' ' || line.charAt(0) == '\t';
				line = line.trim();
				if (line.isEmpty()) continue;

				if (!indented) {
					int offset;

					// filter env
					if (line.startsWith("common")) {
						offset = "common".length();
					} else if (line.startsWith(env)) {
						offset = env.length();
					} else { // wrong env, skip
						state = STATE_SKIP;
						continue;
					}

					switch (line.substring(offset)) {
					case "Args":
						state = STATE_ARGS;
						break;
					case "Properties":
						state = STATE_PROPERTIES;
						break;
					default:
						throw new IOException("invalid attribute: "+line);
					}
				} else if (state == STATE_NONE) { // indented, no state/attribute
					throw new IOException("value without preceding attribute: "+line);
				} else if (state == STATE_ARGS) {
					extraArgs.add(line);
				} else if (state == STATE_PROPERTIES) {
					int pos = line.indexOf('=');
					String key = pos >= 0 ? line.substring(0, pos).trim() : line;
					String value = pos >= 0 ? line.substring(pos + 1).trim() : "";

					extraProperties.put(key, value);
				} else if (state == STATE_SKIP) {
					// Wrong environment for the section, skip the line
				} else { // shouldn't happen
					throw new IllegalStateException();
				}
			}
		}
	}

	private static void warnNoop(String msg) {
		System.out.printf("warning: dev-launch-injector in pass-through mode, %s%n", msg);
	}

	/**
	 * Decode tokens in the form @@x where x is 1-4 hex chars encoding an UTF-16 code unit.
	 *
	 * <p>Example: 'a@@20b' -> 'a b'
	 */
	private static String decodeEscaped(String s) {
		if (s.indexOf("@@") < 0) return s;

		Matcher matcher = Pattern.compile("@@([0-9a-fA-F]{1,4})").matcher(s);
		StringBuilder ret = new StringBuilder(s.length());
		int start = 0;

		while (matcher.find()) {
			ret.append(s, start, matcher.start());
			ret.append((char) Integer.parseInt(matcher.group(1), 16));
			start = matcher.end();
		}

		ret.append(s, start, s.length());

		return ret.toString();
	}
}
