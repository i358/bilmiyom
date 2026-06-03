package com.mceconomy.company;

import com.mceconomy.config.EconomyConfig;
import com.mceconomy.job.JobType;

/** Oyuncu sirket rolleri (uretim meslekleri + CEO ortakligi). */
public final class EmploymentRole {
	public static final String CEO_ID = "ceo";

	private EmploymentRole() {
	}

	public static boolean isCeo(String roleId) {
		return roleId != null && CEO_ID.equalsIgnoreCase(roleId.trim());
	}

	public static String displayName(String roleId) {
		if (isCeo(roleId)) {
			return "CEO (Ortak)";
		}
		JobType job = JobType.fromString(roleId);
		return job != null ? job.displayName() : roleId;
	}

	/** Sirket kasasina giden pay (0.5 = kazancin yarisi). */
	public static double companyProfitShare() {
		return EconomyConfig.ceoCompanyProfitShare();
	}

	/** Oyuncu cuzdanina giden pay. */
	public static double playerProfitShare() {
		return 1.0 - companyProfitShare();
	}
}
