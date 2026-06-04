package com.mceconomy.web;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WebSessionManager {
	private static final long SESSION_MS = 4L * 60 * 60 * 1000;
	private final Map<String, WebSession> sessions = new ConcurrentHashMap<>();

	public String create(UUID playerUuid, String playerName, boolean op) {
		String token = UUID.randomUUID().toString();
		sessions.put(token, new WebSession(playerUuid, playerName, op, System.currentTimeMillis() + SESSION_MS));
		return token;
	}

	public Optional<WebSession> get(String token) {
		if (token == null || token.isBlank()) {
			return Optional.empty();
		}
		WebSession session = sessions.get(token);
		if (session == null || session.expired()) {
			sessions.remove(token);
			return Optional.empty();
		}
		return Optional.of(session);
	}

	public void revoke(String token) {
		sessions.remove(token);
	}

	public void clearAll() {
		sessions.clear();
	}
}
