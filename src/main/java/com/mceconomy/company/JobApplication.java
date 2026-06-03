package com.mceconomy.company;

public final class JobApplication {
	private long id;
	private final int companyId;
	private final String npcName;
	private final String roleId;
	private final long requestedSalaryMg;
	private final String message;
	private ApplicationStatus status;
	private final long appliedAt;
	private String entityUuid;

	public JobApplication(long id, int companyId, String npcName, String roleId, long requestedSalaryMg,
			String message, ApplicationStatus status, long appliedAt, String entityUuid) {
		this.id = id;
		this.companyId = companyId;
		this.npcName = npcName;
		this.roleId = roleId;
		this.requestedSalaryMg = requestedSalaryMg;
		this.message = message;
		this.status = status;
		this.appliedAt = appliedAt;
		this.entityUuid = entityUuid;
	}

	public static JobApplication createPending(int companyId, String npcName, String roleId,
			long requestedSalaryMg, String message, String entityUuid) {
		return new JobApplication(0, companyId, npcName, roleId, requestedSalaryMg, message,
				ApplicationStatus.PENDING, System.currentTimeMillis(), entityUuid);
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

	public String npcName() {
		return npcName;
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

	public String entityUuid() {
		return entityUuid;
	}

	public void setEntityUuid(String entityUuid) {
		this.entityUuid = entityUuid;
	}
}
