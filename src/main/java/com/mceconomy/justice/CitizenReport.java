package com.mceconomy.justice;

import java.util.UUID;

public record CitizenReport(
		long id,
		ReportType type,
		UUID reporterUuid,
		String reporterName,
		UUID targetUuid,
		String targetName,
		String category,
		String subject,
		String message,
		ReportStatus status,
		String adminNote,
		Long prisonSentenceId,
		long createdAt,
		long resolvedAt
) {
	public static CitizenReport open(ReportType type, UUID reporterUuid, String reporterName,
			UUID targetUuid, String targetName, String category, String subject, String message) {
		return new CitizenReport(0, type, reporterUuid, reporterName, targetUuid, targetName,
				category, subject, message, ReportStatus.OPEN, null, null,
				System.currentTimeMillis(), 0);
	}

	public CitizenReport withStatus(ReportStatus status, String adminNote, Long sentenceId) {
		return new CitizenReport(id, type, reporterUuid, reporterName, targetUuid, targetName,
				category, subject, message, status, adminNote, sentenceId, createdAt, System.currentTimeMillis());
	}
}
