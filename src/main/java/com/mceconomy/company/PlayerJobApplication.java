package com.mceconomy.company;

import java.util.UUID;

public final class PlayerJobApplication {
	private long id;
	private final int companyId;
	private final UUID playerUuid;
	private final String playerName;
	private final String roleId;
	private final long requestedSalaryMg;
	private final String message;
	private ApplicationStatus status;
	private final long appliedAt;

	public PlayerJobApplication(long id, int companyId, UUID playerUuid, String playerName, String roleId,
			long requestedSalaryMg, String message, ApplicationStatus status, long appliedAt) {
		this.id = id;
		this.companyId = companyId;
		this.playerUuid = playerUuid;
		this.playerName = playerName;
		this.roleId = roleId;
		this.requestedSalaryMg = requestedSalaryMg;
		this.message = message;
		this.status = status;
		this.appliedAt = appliedAt;
	}

	public static PlayerJobApplication createPending(int companyId, UUID playerUuid, String playerName,
			String roleId, long requestedSalaryMg, String message) {
		return new PlayerJobApplication(0, companyId, playerUuid, playerName, roleId, requestedSalaryMg, message,
				ApplicationStatus.PENDING, System.currentTimeMillis());
	}

	public long id() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public int companyId() {
		return companyId;
	}

	public UUID playerUuid() {
		return playerUuid;
	}

	public String playerName() {
		return playerName;
	}

	public String roleId() {
		return roleId;
	}

	public long requestedSalaryMg() {
		return requestedSalaryMg;
	}

	public String message() {
		return message;
	}

	public ApplicationStatus status() {
		return status;
	}

	public void setStatus(ApplicationStatus status) {
		this.status = status;
	}

	public long appliedAt() {
		return appliedAt;
	}
}
