package com.mceconomy.company;

import java.util.UUID;

public final class ShareHolding {
	private final int companyId;
	private final UUID ownerUuid;
	private int amount;

	public ShareHolding(int companyId, UUID ownerUuid, int amount) {
		this.companyId = companyId;
		this.ownerUuid = ownerUuid;
		this.amount = amount;
	}

	public int companyId() {
		return companyId;
	}

	public UUID ownerUuid() {
		return ownerUuid;
	}

	public int amount() {
		return amount;
	}

	public void add(int count) {
		amount += count;
	}

	public boolean remove(int count) {
		if (count > amount) {
			return false;
		}
		amount -= count;
		return true;
	}

	public void setAmount(int count) {
		amount = Math.max(0, count);
	}
}
