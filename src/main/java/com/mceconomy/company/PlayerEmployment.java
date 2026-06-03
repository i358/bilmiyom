package com.mceconomy.company;

import java.util.UUID;

public final class PlayerEmployment {
	private long id;
	private final UUID playerUuid;
	private final String playerName;
	private final int companyId;
	private final String roleId;
	private long salaryMg;
	private final long hiredAt;
	private long lastPaidAt;

	public PlayerEmployment(long id, UUID playerUuid, String playerName, int companyId, String roleId,
			long salaryMg, long hiredAt, long lastPaidAt) {
		this.id = id;
		this.playerUuid = playerUuid;
		this.playerName = playerName;
		this.companyId = companyId;
		this.roleId = roleId;
		this.salaryMg = salaryMg;
		this.hiredAt = hiredAt;
		this.lastPaidAt = lastPaidAt;
	}

	public static PlayerEmployment hire(UUID playerUuid, String playerName, int companyId, String roleId,
			long salaryMg) {
		long now = System.currentTimeMillis();
		return new PlayerEmployment(0, playerUuid, playerName, companyId, roleId, salaryMg, now, now);
	}

	public long id() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public UUID playerUuid() {
		return playerUuid;
	}

	public String playerName() {
		return playerName;
	}

	public int companyId() {
		return companyId;
	}

	public String roleId() {
		return roleId;
	}

	public long salaryMg() {
		return salaryMg;
	}

	public void setSalaryMg(long salaryMg) {
		this.salaryMg = salaryMg;
	}

	public long hiredAt() {
		return hiredAt;
	}

	public long lastPaidAt() {
		return lastPaidAt;
	}

	public void setLastPaidAt(long lastPaidAt) {
		this.lastPaidAt = lastPaidAt;
	}
}
