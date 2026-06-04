package com.mceconomy.company;

import java.util.UUID;

public final class Company {
	private int id;
	private final String name;
	private final UUID ownerUuid;
	private long treasury;
	private int outstandingShares;
	private final long createdAt;
	private boolean listedOnExchange;
	private String ticker;

	public Company(int id, String name, UUID ownerUuid, long treasury, int outstandingShares, long createdAt,
			boolean listedOnExchange, String ticker) {
		this.id = id;
		this.name = name;
		this.ownerUuid = ownerUuid;
		this.treasury = treasury;
		this.outstandingShares = outstandingShares;
		this.createdAt = createdAt;
		this.listedOnExchange = listedOnExchange;
		this.ticker = ticker;
	}

	public static Company create(String name, UUID owner) {
		return new Company(0, name, owner, 0, 100, System.currentTimeMillis(), false, null);
	}

	public int id() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String name() {
		return name;
	}

	public UUID ownerUuid() {
		return ownerUuid;
	}

	public long treasury() {
		return treasury;
	}

	public void deposit(long amount) {
		treasury += amount;
	}

	public void withdraw(long amount) {
		treasury = Math.max(0, treasury - amount);
	}

	public void setTreasury(long treasury) {
		this.treasury = Math.max(0, treasury);
	}

	public int outstandingShares() {
		return outstandingShares;
	}

	public long createdAt() {
		return createdAt;
	}

	public boolean listedOnExchange() {
		return listedOnExchange;
	}

	public String ticker() {
		return ticker;
	}

	public void listOnExchange(String ticker) {
		this.listedOnExchange = true;
		this.ticker = ticker;
	}

	public void delistFromExchange() {
		this.listedOnExchange = false;
		this.ticker = null;
	}

	public long sharePrice(double economyIndex) {
		long value = treasury + (long) (economyIndex * 10);
		return Math.max(1, value / outstandingShares);
	}
}
