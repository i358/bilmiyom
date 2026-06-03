package com.mceconomy.regulation;

import java.util.UUID;

public record MasakAlert(
		long id,
		UUID playerUuid,
		String reason,
		int riskScore,
		long amount,
		boolean resolved,
		long createdAt
) {
	public static MasakAlert open(UUID playerUuid, String reason, int riskScore, long amount) {
		return new MasakAlert(0, playerUuid, reason, riskScore, amount, false, System.currentTimeMillis());
	}

	public MasakAlert markResolved() {
		return new MasakAlert(id, playerUuid, reason, riskScore, amount, true, createdAt);
	}
}
