package com.mceconomy.guild;

public final class Guild {
	private int id;
	private final String name;
	private final java.util.UUID leaderUuid;
	private long treasuryMg;
	private boolean strikeActive;
	private long strikeUntil;
	private String bargainMessage;
	private final long createdAt;

	public Guild(int id, String name, java.util.UUID leaderUuid, long treasuryMg, boolean strikeActive,
			long strikeUntil, String bargainMessage, long createdAt) {
		this.id = id;
		this.name = name;
		this.leaderUuid = leaderUuid;
		this.treasuryMg = treasuryMg;
		this.strikeActive = strikeActive;
		this.strikeUntil = strikeUntil;
		this.bargainMessage = bargainMessage;
		this.createdAt = createdAt;
	}

	public static Guild create(String name, java.util.UUID leaderUuid) {
		return new Guild(0, name, leaderUuid, 0, false, 0, "", System.currentTimeMillis());
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

	public java.util.UUID leaderUuid() {
		return leaderUuid;
	}

	public long treasuryMg() {
		return treasuryMg;
	}

	public void deposit(long amount) {
		treasuryMg += amount;
	}

	public boolean withdraw(long amount) {
		if (treasuryMg < amount) {
			return false;
		}
		treasuryMg -= amount;
		return true;
	}

	public boolean strikeActive() {
		return strikeActive;
	}

	public void setStrikeActive(boolean strikeActive) {
		this.strikeActive = strikeActive;
	}

	public long strikeUntil() {
		return strikeUntil;
	}

	public void setStrikeUntil(long strikeUntil) {
		this.strikeUntil = strikeUntil;
	}

	public String bargainMessage() {
		return bargainMessage;
	}

	public void setBargainMessage(String bargainMessage) {
		this.bargainMessage = bargainMessage;
	}

	public long createdAt() {
		return createdAt;
	}
}
