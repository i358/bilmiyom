package com.mceconomy.trade;

import java.util.UUID;

public final class PlayerTrade {
	private long id;
	private final UUID initiatorUuid;
	private final String initiatorName;
	private final UUID partnerUuid;
	private final String partnerName;
	private long initiatorGoldMg;
	private long partnerGoldMg;
	private String initiatorItemsJson;
	private String partnerItemsJson;
	private boolean initiatorReady;
	private boolean partnerReady;
	private TradeStatus status;
	private long completedAt;
	private final long createdAt;

	public PlayerTrade(long id, UUID initiatorUuid, String initiatorName, UUID partnerUuid, String partnerName,
			long initiatorGoldMg, long partnerGoldMg, String initiatorItemsJson, String partnerItemsJson,
			boolean initiatorReady, boolean partnerReady, TradeStatus status, long completedAt, long createdAt) {
		this.id = id;
		this.initiatorUuid = initiatorUuid;
		this.initiatorName = initiatorName;
		this.partnerUuid = partnerUuid;
		this.partnerName = partnerName;
		this.initiatorGoldMg = initiatorGoldMg;
		this.partnerGoldMg = partnerGoldMg;
		this.initiatorItemsJson = initiatorItemsJson;
		this.partnerItemsJson = partnerItemsJson;
		this.initiatorReady = initiatorReady;
		this.partnerReady = partnerReady;
		this.status = status;
		this.completedAt = completedAt;
		this.createdAt = createdAt;
	}

	public static PlayerTrade open(UUID initiatorUuid, String initiatorName, UUID partnerUuid, String partnerName) {
		return new PlayerTrade(0, initiatorUuid, initiatorName, partnerUuid, partnerName,
				0, 0, "[]", "[]", false, false, TradeStatus.PENDING, 0, System.currentTimeMillis());
	}

	public long id() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public UUID initiatorUuid() {
		return initiatorUuid;
	}

	public String initiatorName() {
		return initiatorName;
	}

	public UUID partnerUuid() {
		return partnerUuid;
	}

	public String partnerName() {
		return partnerName;
	}

	public long initiatorGoldMg() {
		return initiatorGoldMg;
	}

	public void setInitiatorGoldMg(long initiatorGoldMg) {
		this.initiatorGoldMg = initiatorGoldMg;
	}

	public long partnerGoldMg() {
		return partnerGoldMg;
	}

	public void setPartnerGoldMg(long partnerGoldMg) {
		this.partnerGoldMg = partnerGoldMg;
	}

	public String initiatorItemsJson() {
		return initiatorItemsJson;
	}

	public void setInitiatorItemsJson(String initiatorItemsJson) {
		this.initiatorItemsJson = initiatorItemsJson;
	}

	public String partnerItemsJson() {
		return partnerItemsJson;
	}

	public void setPartnerItemsJson(String partnerItemsJson) {
		this.partnerItemsJson = partnerItemsJson;
	}

	public boolean initiatorReady() {
		return initiatorReady;
	}

	public void setInitiatorReady(boolean initiatorReady) {
		this.initiatorReady = initiatorReady;
	}

	public boolean partnerReady() {
		return partnerReady;
	}

	public void setPartnerReady(boolean partnerReady) {
		this.partnerReady = partnerReady;
	}

	public TradeStatus status() {
		return status;
	}

	public void setStatus(TradeStatus status) {
		this.status = status;
	}

	public long completedAt() {
		return completedAt;
	}

	public void setCompletedAt(long completedAt) {
		this.completedAt = completedAt;
	}

	public long createdAt() {
		return createdAt;
	}

	public boolean involves(UUID uuid) {
		return initiatorUuid.equals(uuid) || partnerUuid.equals(uuid);
	}

	public boolean isInitiator(UUID uuid) {
		return initiatorUuid.equals(uuid);
	}
}
