package com.mceconomy.appeal;

import java.util.UUID;

public record Appeal(
		long id,
		UUID playerUuid,
		String playerName,
		String subject,
		String message,
		Long relatedAlertId,
		AppealStatus status,
		String adminNote,
		long createdAt,
		long resolvedAt
) {
	public static Appeal open(UUID playerUuid, String playerName, String subject, String message, Long relatedAlertId) {
		return new Appeal(0, playerUuid, playerName, subject, message, relatedAlertId,
				AppealStatus.OPEN, null, System.currentTimeMillis(), 0);
	}

	public Appeal withStatus(AppealStatus status, String adminNote) {
		return new Appeal(id, playerUuid, playerName, subject, message, relatedAlertId,
				status, adminNote, createdAt, System.currentTimeMillis());
	}
}
