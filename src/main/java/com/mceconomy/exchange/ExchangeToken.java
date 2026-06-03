package com.mceconomy.exchange;

import java.util.UUID;

public final class ExchangeToken {
	private int id;
	private final String symbol;
	private final String displayName;
	private final UUID creatorUuid;
	private final int totalSupply;
	private int circulating;
	private long priceMg;
	private long treasuryMg;
	private final long createdAt;

	public ExchangeToken(int id, String symbol, String displayName, UUID creatorUuid,
			int totalSupply, int circulating, long priceMg, long treasuryMg, long createdAt) {
		this.id = id;
		this.symbol = symbol;
		this.displayName = displayName;
		this.creatorUuid = creatorUuid;
		this.totalSupply = totalSupply;
		this.circulating = circulating;
		this.priceMg = priceMg;
		this.treasuryMg = treasuryMg;
		this.createdAt = createdAt;
	}

	public static ExchangeToken create(String symbol, String displayName, UUID creator,
			int totalSupply, long priceMg) {
		return new ExchangeToken(0, symbol, displayName, creator, totalSupply, 0, priceMg, 0,
				System.currentTimeMillis());
	}

	public int id() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String symbol() {
		return symbol;
	}

	public String displayName() {
		return displayName;
	}

	public UUID creatorUuid() {
		return creatorUuid;
	}

	public int totalSupply() {
		return totalSupply;
	}

	public int circulating() {
		return circulating;
	}

	public void addCirculating(int amount) {
		circulating += amount;
	}

	public void removeCirculating(int amount) {
		circulating = Math.max(0, circulating - amount);
	}

	public long priceMg() {
		return priceMg;
	}

	public void setPriceMg(long priceMg) {
		this.priceMg = Math.max(1, priceMg);
	}

	public long treasuryMg() {
		return treasuryMg;
	}

	public void depositTreasury(long amount) {
		treasuryMg += amount;
	}

	public void withdrawTreasury(long amount) {
		treasuryMg = Math.max(0, treasuryMg - amount);
	}

	public long createdAt() {
		return createdAt;
	}
}
