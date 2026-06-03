package com.mceconomy.insurance;

import java.util.UUID;

public record InsurancePolicy(
		UUID ownerUuid,
		PolicyType type,
		int companyId,
		boolean active,
		double coveragePercent,
		long monthlyPremiumMg,
		long nextPremiumDueMs
) {
	public enum PolicyType {
		PERSONAL,
		COMPANY
	}
}
