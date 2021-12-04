/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

package net.fabricmc.loom.util.ipc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class IPCServer implements AutoCloseable {
	private final ExecutorService loggerReceiverService = Executors.newSingleThreadExecutor();
	private final Path path;
	private final Consumer<String> consumer;

	private final CountDownLatch startupLock = new CountDownLatch(1);

	public IPCServer(Path path, Consumer<String> consumer) {
		this.path = path;
		this.consumer = consumer;

		loggerReceiverService.submit(this::run);

		try {
			startupLock.await(10, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			throw new RuntimeException("Timed out waiting for IPC server thread to start", e);
		}
	}

	public void run() {
		UnixDomainSocketAddress address = UnixDomainSocketAddress.of(path);

		try (ServerSocketChannel serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
			serverChannel.bind(address);

			startupLock.countDown();

			try (SocketChannel clientChannel = serverChannel.accept();
					Scanner scanner = new Scanner(clientChannel, StandardCharsets.UTF_8)) {
				while (!Thread.currentThread().isInterrupted()) {
					if (scanner.hasNextLine()) {
						this.consumer.accept(scanner.nextLine());
					}
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to listen for IPC messages", e);
		}
	}

	@Override
	public void close() throws InterruptedException {
		loggerReceiverService.shutdownNow();
		loggerReceiverService.awaitTermination(10, TimeUnit.SECONDS);
	}
}
