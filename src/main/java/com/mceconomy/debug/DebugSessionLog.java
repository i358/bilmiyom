package com.mceconomy.debug;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class DebugSessionLog {
	private static final String SESSION_ID = "e75fb2";
	private static final Path LOG_PATH = Path.of("debug-e75fb2.log");
	private static final Gson GSON = new Gson();

	private DebugSessionLog() {
	}

	public static void log(String location, String message, String hypothesisId, JsonObject data) {
		try {
			JsonObject line = new JsonObject();
			line.addProperty("sessionId", SESSION_ID);
			line.addProperty("timestamp", System.currentTimeMillis());
			line.addProperty("location", location);
			line.addProperty("message", message);
			line.addProperty("hypothesisId", hypothesisId);
			if (data != null) {
				line.add("data", data);
			}
			Files.writeString(LOG_PATH, GSON.toJson(line) + System.lineSeparator(),
					StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (Exception ignored) {
		}
	}
}
