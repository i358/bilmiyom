package com.mceconomy.justice;

import java.util.UUID;

public record PrisonSentence(
		long id,
		UUID playerUuid,
		String playerName,
		String reason,
		String sentencedBy,
		long jailedAt,
		long releaseAt,
		boolean active,
		Double returnX,
		Double returnY,
		Double returnZ,
		String returnDimension,
		int cellIndex
) {
	public boolean isActiveNow() {
		return active && System.currentTimeMillis() < releaseAt;
	}

	public long remainingMs() {
		return Math.max(0, releaseAt - System.currentTimeMillis());
	}
}
