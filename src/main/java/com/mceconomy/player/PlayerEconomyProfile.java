package com.mceconomy.player;

import com.mceconomy.McEconomyMod;
import com.mceconomy.job.JobType;

import java.util.UUID;

public final class PlayerEconomyProfile {
	private final UUID uuid;
	private String name;
	private final PlayerWallet wallet;
	private final PlayerDirtyWallet dirtyWallet;
	private final CreditScore creditScore;
	private JobType jobType;
	private long lastTaxAt;
	private boolean accountFrozen;
	private boolean blacklisted;
	private boolean bankCertified;
	private boolean centralBankOfficial;
	private boolean economyMinister;
	private String dashboardPasswordHash;
	private String dashboardPasswordSalt;

	public PlayerEconomyProfile(UUID uuid, String name, long balance, long dirtyBalance, int creditScore,
			JobType jobType, long lastTaxAt, boolean accountFrozen, boolean blacklisted, boolean bankCertified,
			boolean centralBankOfficial, boolean economyMinister, String dashboardPasswordHash,
			String dashboardPasswordSalt) {
		this.uuid = uuid;
		this.name = name;
		this.wallet = new PlayerWallet(balance);
		this.dirtyWallet = new PlayerDirtyWallet(dirtyBalance);
		this.creditScore = new CreditScore(creditScore);
		this.jobType = jobType;
		this.lastTaxAt = lastTaxAt;
		this.accountFrozen = accountFrozen;
		this.blacklisted = blacklisted;
		this.bankCertified = bankCertified;
		this.centralBankOfficial = centralBankOfficial;
		this.economyMinister = economyMinister;
		this.dashboardPasswordHash = dashboardPasswordHash;
		this.dashboardPasswordSalt = dashboardPasswordSalt;
	}

	public static PlayerEconomyProfile createNew(UUID uuid, String name, long startingBalance) {
		return new PlayerEconomyProfile(uuid, name, startingBalance, 0, 650, null, 0,
				false, false, false, false, false, null, null);
	}

	public UUID uuid() {
		return uuid;
	}

	public String name() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public PlayerWallet wallet() {
		return wallet;
	}

	public PlayerDirtyWallet dirtyWallet() {
		return dirtyWallet;
	}

	public CreditScore creditScore() {
		return creditScore;
	}

	public JobType jobType() {
		return jobType;
	}

	public void setJobType(JobType jobType) {
		this.jobType = jobType;
	}

	public long lastTaxAt() {
		return lastTaxAt;
	}

	public void setLastTaxAt(long lastTaxAt) {
		this.lastTaxAt = lastTaxAt;
	}

	public boolean accountFrozen() {
		return accountFrozen;
	}

	public void setAccountFrozen(boolean accountFrozen) {
		this.accountFrozen = accountFrozen;
	}

	public boolean blacklisted() {
		return blacklisted;
	}

	public void setBlacklisted(boolean blacklisted) {
		this.blacklisted = blacklisted;
	}

	public boolean bankCertified() {
		return bankCertified;
	}

	public void setBankCertified(boolean bankCertified) {
		this.bankCertified = bankCertified;
	}

	public boolean centralBankOfficial() {
		return centralBankOfficial;
	}

	public void setCentralBankOfficial(boolean centralBankOfficial) {
		this.centralBankOfficial = centralBankOfficial;
	}

	public boolean economyMinister() {
		return economyMinister;
	}

	public void setEconomyMinister(boolean economyMinister) {
		this.economyMinister = economyMinister;
	}

	public String dashboardPasswordHash() {
		return dashboardPasswordHash;
	}

	public void setDashboardPasswordHash(String dashboardPasswordHash) {
		this.dashboardPasswordHash = dashboardPasswordHash;
	}

	public String dashboardPasswordSalt() {
		return dashboardPasswordSalt;
	}

	public void setDashboardPasswordSalt(String dashboardPasswordSalt) {
		this.dashboardPasswordSalt = dashboardPasswordSalt;
	}

	public boolean canUseLegalEconomy() {
		if (accountFrozen || blacklisted) {
			return false;
		}
		var manager = McEconomyMod.getEconomyManager();
		if (manager != null && manager.prisonService() != null && manager.prisonService().isJailed(uuid)) {
			return false;
		}
		return true;
	}

	/** Market satis, altin yatirma, bankadan cuzdana cekme — MASAK dondurmesi haric. */
	public boolean canEarnLegalIncome() {
		if (blacklisted) {
			return false;
		}
		var manager = McEconomyMod.getEconomyManager();
		if (manager != null && manager.prisonService() != null && manager.prisonService().isJailed(uuid)) {
			return false;
		}
		return true;
	}
}
