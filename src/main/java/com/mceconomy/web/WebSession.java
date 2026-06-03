package com.mceconomy.web;

import java.util.UUID;

public record WebSession(UUID playerUuid, String playerName, boolean op, long expiresAt) {
	public boolean expired() {
		return System.currentTimeMillis() > expiresAt;
	}
}
