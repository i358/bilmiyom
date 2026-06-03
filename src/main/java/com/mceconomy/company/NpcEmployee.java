package com.mceconomy.company;

public final class NpcEmployee {
	private long id;
	private final int companyId;
	private final String npcName;
	private final String roleId;
	private long salaryMg;
	private final long hiredAt;
	private long lastPaidAt;
	private long totalProducedMg;

	public NpcEmployee(long id, int companyId, String npcName, String roleId, long salaryMg,
			long hiredAt, long lastPaidAt, long totalProducedMg) {
		this.id = id;
		this.companyId = companyId;
		this.npcName = npcName;
		this.roleId = roleId;
		this.salaryMg = salaryMg;
		this.hiredAt = hiredAt;
		this.lastPaidAt = lastPaidAt;
		this.totalProducedMg = totalProducedMg;
	}

	public static NpcEmployee hire(int companyId, String npcName, String roleId, long salaryMg) {
		long now = System.currentTimeMillis();
		return new NpcEmployee(0, companyId, npcName, roleId, salaryMg, now, now, 0);
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

	public long totalProducedMg() {
		return totalProducedMg;
	}

	public void addProduction(long amount) {
		totalProducedMg += amount;
	}
}
