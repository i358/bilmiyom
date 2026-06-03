package com.mceconomy.job;

import com.mceconomy.market.Commodity;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.CurrencyService;
import com.mceconomy.economy.TransactionType;
import com.mceconomy.player.PlayerEconomyProfile;
import com.mceconomy.tax.TaxService;

import java.util.Map;
import java.util.UUID;

public final class JobManager {
	private final Map<UUID, PlayerEconomyProfile> profiles;
	private final CurrencyService currencyService;
	private final TaxService taxService;

	public JobManager(Map<UUID, PlayerEconomyProfile> profiles, CurrencyService currencyService, TaxService taxService) {
		this.profiles = profiles;
		this.currencyService = currencyService;
		this.taxService = taxService;
	}

	public boolean setJob(UUID uuid, JobType jobType) {
		PlayerEconomyProfile profile = profiles.get(uuid);
		if (profile == null) {
			return false;
		}
		profile.setJobType(jobType);
		return true;
	}

	public boolean resignJob(UUID uuid) {
		PlayerEconomyProfile profile = profiles.get(uuid);
		if (profile == null || profile.jobType() == null) {
			return false;
		}
		profile.setJobType(null);
		return true;
	}

	public long applySellBonus(java.util.UUID uuid, Commodity commodity, long unitPrice) {
		PlayerEconomyProfile profile = profiles.get(uuid);
		if (profile != null && commodity.matchesJob(profile.jobType())) {
			return (long) (unitPrice * EconomyConfig.jobBonusMultiplier());
		}
		return unitPrice;
	}

	public long calculateReward(UUID uuid, long baseReward) {
		PlayerEconomyProfile profile = profiles.get(uuid);
		if (profile == null) {
			return baseReward;
		}
		long reward = baseReward;
		if (profile.jobType() != null) {
			reward = (long) (reward * EconomyConfig.jobBonusMultiplier());
		}
		long tax = taxService.calculateIncomeTax(reward);
		long net = reward - tax;
		currencyService.deposit(uuid, net, TransactionType.QUEST_REWARD);
		taxService.collectTax(tax);
		return net;
	}
}
