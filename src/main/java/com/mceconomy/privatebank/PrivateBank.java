package com.mceconomy.privatebank;

import java.util.UUID;

public final class PrivateBank {
	private int id;
	private final String name;
	private final UUID ownerUuid;
	private long treasuryMg;
	private double interestRate;
	private final long createdAt;

	public PrivateBank(int id, String name, UUID ownerUuid, long treasuryMg, double interestRate, long createdAt) {
		this.id = id;
		this.name = name;
		this.ownerUuid = ownerUuid;
		this.treasuryMg = treasuryMg;
		this.interestRate = interestRate;
		this.createdAt = createdAt;
	}

	public static PrivateBank create(String name, UUID owner) {
		return new PrivateBank(0, name, owner, 0, 0.03, System.currentTimeMillis());
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

	public long treasuryMg() {
		return treasuryMg;
	}

	public void depositTreasury(long amount) {
		treasuryMg += amount;
	}

	public void withdrawTreasury(long amount) {
		treasuryMg = Math.max(0, treasuryMg - amount);
	}

	public double interestRate() {
		return interestRate;
	}

	public void setInterestRate(double interestRate) {
		this.interestRate = interestRate;
	}

	public long createdAt() {
		return createdAt;
	}
}
