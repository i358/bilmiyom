package com.mceconomy.panel;

import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Debug session e75fb2 — gecici panel sync loglari. */
public final class PanelDebugLog {
	private static final Path LOG = Path.of("debug-e75fb2.log");

	private PanelDebugLog() {
	}

	public static void log(String location, String message, String hypothesisId, JsonObject data) {
		// #region agent log
		try {
			JsonObject entry = new JsonObject();
			entry.addProperty("sessionId", "e75fb2");
			entry.addProperty("timestamp", System.currentTimeMillis());
			entry.addProperty("location", location);
			entry.addProperty("message", message);
			entry.addProperty("hypothesisId", hypothesisId);
			entry.add("data", data);
			Files.writeString(LOG, entry + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (Exception ignored) {
		}
		// #endregion
	}
}
