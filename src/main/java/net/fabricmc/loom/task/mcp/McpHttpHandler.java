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

package net.fabricmc.loom.task.mcp;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loom.task.mcp.methods.McpMethod;
import net.fabricmc.loom.task.mcp.methods.McpMethods;

public class McpHttpHandler implements HttpHandler {
	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.create();
	private static final Logger LOGGER = LoggerFactory.getLogger(McpHttpHandler.class);

	private final Map<String, McpMethod> methods;

	public McpHttpHandler(ProjectContext context) {
		this.methods = McpMethods.getMethods(context);
	}

	@Override
	public void handle(HttpExchange exchange) throws IOException {
		LOGGER.info("Received MCP request: {} {}", exchange.getRequestMethod(), exchange.getRequestURI());

		if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
			exchange.sendResponseHeaders(204, -1);
			return;
		}

		try {
			if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
				exchange.sendResponseHeaders(405, -1);
				return;
			}

			if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
				throw new McpException(null, McpConstants.ERROR_INVALID_REQUEST, "Invalid Request: Only POST method is supported");
			}

			McpRequest request = parseRequest(exchange);
			validateRequest(request);
			handleMcpRequest(exchange, request);
		} catch (McpException e) {
			LOGGER.error("MCP Error (code {}): {}", e.getErrorCode(), e.getMessage(), e);

			McpResponse response = McpResponse.error(e.getRequestId(), e.getErrorCode(), e.getMessage());
			sendJsonResponse(exchange, response);
		}
	}

	private McpRequest parseRequest(HttpExchange exchange) throws McpException {
		try (InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
			return GSON.fromJson(reader, McpRequest.class);
		} catch (JsonSyntaxException e) {
			throw new McpException(null, McpConstants.ERROR_PARSE_ERROR, "Parse error: " + e.getMessage(), e);
		} catch (IOException e) {
			throw new McpException(null, McpConstants.ERROR_INTERNAL_ERROR, "Failed to read request body: " + e.getMessage(), e);
		}
	}

	private void validateRequest(McpRequest request) throws McpException {
		if (request == null || request.jsonrpc() == null || !request.jsonrpc().equals(McpConstants.JSON_RPC_VERSION)) {
			throw new McpException(null, McpConstants.ERROR_INVALID_REQUEST, "Invalid Request: jsonrpc must be: " + McpConstants.JSON_RPC_VERSION);
		}

		if (request.method() == null || request.method().isEmpty()) {
			throw new McpException(request.id(), McpConstants.ERROR_INVALID_REQUEST, "Invalid Request: method is required");
		}
	}

	private void handleMcpRequest(HttpExchange exchange, McpRequest request) throws McpException {
		McpMethod method = methods.get(request.method());

		if (method == null) {
			throw new McpException(request.id(), McpConstants.ERROR_METHOD_NOT_FOUND, "Method not found: " + request.method());
		}

		try {
			McpResult result = method.handle(request);
			McpResponse response = McpResponse.success(request.id(), result);
			sendJsonResponse(exchange, response);
		} catch (Exception e) {
			throw new McpException(request.id(), McpConstants.ERROR_INTERNAL_ERROR, "Internal error: " + e.getMessage(), e);
		}
	}

	private static void sendJsonResponse(HttpExchange exchange, Object response) throws IOException {
		String json = GSON.toJson(response);
		byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

		LOGGER.debug("Sending MCP response: {}", json);

		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(200, bytes.length);

		try (OutputStream os = exchange.getResponseBody()) {
			os.write(bytes);
		}
	}
}
