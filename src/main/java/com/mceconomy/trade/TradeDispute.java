package com.mceconomy.trade;

import java.util.UUID;

public final class TradeDispute {
	private long id;
	private final long tradeId;
	private final UUID reporterUuid;
	private final String reporterName;
	private final UUID targetUuid;
	private final String targetName;
	private final String reason;
	private TradeDisputeStatus status;
	private String adminNote;
	private String resolvedBy;
	private long resolvedAt;
	private final long createdAt;

	public TradeDispute(long id, long tradeId, UUID reporterUuid, String reporterName, UUID targetUuid,
			String targetName, String reason, TradeDisputeStatus status, String adminNote, String resolvedBy,
			long resolvedAt, long createdAt) {
		this.id = id;
		this.tradeId = tradeId;
		this.reporterUuid = reporterUuid;
		this.reporterName = reporterName;
		this.targetUuid = targetUuid;
		this.targetName = targetName;
		this.reason = reason;
		this.status = status;
		this.adminNote = adminNote;
		this.resolvedBy = resolvedBy;
		this.resolvedAt = resolvedAt;
		this.createdAt = createdAt;
	}

	public static TradeDispute open(long tradeId, UUID reporterUuid, String reporterName, UUID targetUuid,
			String targetName, String reason) {
		return new TradeDispute(0, tradeId, reporterUuid, reporterName, targetUuid, targetName, reason,
				TradeDisputeStatus.OPEN, null, null, 0, System.currentTimeMillis());
	}

	public long id() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public long tradeId() {
		return tradeId;
	}

	public UUID reporterUuid() {
		return reporterUuid;
	}

	public String reporterName() {
		return reporterName;
	}

	public UUID targetUuid() {
		return targetUuid;
	}

	public String targetName() {
		return targetName;
	}

	public String reason() {
		return reason;
	}

	public TradeDisputeStatus status() {
		return status;
	}

	public void setStatus(TradeDisputeStatus status) {
		this.status = status;
	}

	public String adminNote() {
		return adminNote;
	}

	public void setAdminNote(String adminNote) {
		this.adminNote = adminNote;
	}

	public String resolvedBy() {
		return resolvedBy;
	}

	public void setResolvedBy(String resolvedBy) {
		this.resolvedBy = resolvedBy;
	}

	public long resolvedAt() {
		return resolvedAt;
	}

	public void setResolvedAt(long resolvedAt) {
		this.resolvedAt = resolvedAt;
	}

	public long createdAt() {
		return createdAt;
	}
}
