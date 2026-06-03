package com.mceconomy.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mceconomy.McEconomyMod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EconomyConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static EconomyConfigData data = new EconomyConfigData();

	public static void load() {
		Path path = Path.of("config", McEconomyMod.MOD_ID + ".json");
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				EconomyConfigData loaded = GSON.fromJson(reader, EconomyConfigData.class);
				if (loaded != null) {
					data = loaded;
				}
			} catch (IOException e) {
				McEconomyMod.LOGGER.error("Config okunamadı", e);
			}
		} else {
			save();
		}
	}

	public static void save() {
		Path path = Path.of("config", McEconomyMod.MOD_ID + ".json");
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(data, writer);
			}
		} catch (IOException e) {
			McEconomyMod.LOGGER.error("Config kaydedilemedi", e);
		}
	}

	public static long startingBalance() {
		return data.startingBalance;
	}

	public static double demandFactor() {
		return data.demandFactor;
	}

	public static double supplyFactor() {
		return data.supplyFactor;
	}

	public static double minPriceMultiplier() {
		return data.minPriceMultiplier;
	}

	public static double maxPriceMultiplier() {
		return data.maxPriceMultiplier;
	}

	public static double baseInterestRate() {
		return data.baseInterestRate;
	}

	public static double lateInterestRate() {
		return data.lateInterestRate;
	}

	public static int minCreditScoreForLoan() {
		return data.minCreditScoreForLoan;
	}

	public static long maxLoanAmount() {
		return data.maxLoanAmount;
	}

	public static int loanInstallmentCount() {
		return data.loanInstallmentCount;
	}

	public static boolean loanConfiscationEnabled() {
		return data.loanConfiscationEnabled;
	}

	public static double incomeTaxRate() {
		return data.incomeTaxRate;
	}

	public static double tradeTaxRate() {
		return data.tradeTaxRate;
	}

	public static double cityTaxRate() {
		return data.cityTaxRate;
	}

	public static long tipRewardMg() {
		return data.tipRewardMg;
	}

	public static double companyJobBonusRate() {
		return data.companyJobBonusRate;
	}

	public static int npcEconomyIntervalTicks() {
		return data.npcEconomyIntervalTicks;
	}

	public static double npcEconomyActivityChance() {
		return data.npcEconomyActivityChance;
	}

	public static int taxEvasionAuditIntervalTicks() {
		return data.taxEvasionAuditIntervalTicks;
	}

	public static double taxEvasionDirtyRatioThreshold() {
		return data.taxEvasionDirtyRatioThreshold;
	}

	public static long guildCreationFeeMg() {
		return data.guildCreationFeeMg;
	}

	public static int guildMaxMembers() {
		return data.guildMaxMembers;
	}

	public static int guildStrikeMaxMinutes() {
		return data.guildStrikeMaxMinutes;
	}

	public static int tradeDisputeWindowHours() {
		return data.tradeDisputeWindowHours;
	}

	public static double wealthTaxRate() {
		return data.wealthTaxRate;
	}

	public static boolean wealthTaxEnabled() {
		return data.wealthTaxEnabled;
	}

	public static double targetInflationRate() {
		return data.targetInflationRate;
	}

	public static int marketDecayIntervalTicks() {
		return data.marketDecayIntervalTicks;
	}

	public static int interestIntervalTicks() {
		return data.interestIntervalTicks;
	}

	public static int inflationIntervalTicks() {
		return data.inflationIntervalTicks;
	}

	public static int eventCheckIntervalTicks() {
		return data.eventCheckIntervalTicks;
	}

	public static double randomEventChance() {
		return data.randomEventChance;
	}

	public static double jobBonusMultiplier() {
		return data.jobBonusMultiplier;
	}

	public static int playerSaveIntervalTicks() {
		return data.playerSaveIntervalTicks;
	}

	public static boolean spawnBankEnabled() {
		return data.spawnBankEnabled;
	}

	public static boolean spawnBankBuilt() {
		return data.spawnBankBuilt;
	}

	public static void setSpawnBankBuilt(boolean built) {
		data.spawnBankBuilt = built;
	}

	public static int spawnBankOffsetX() {
		return data.spawnBankOffsetX;
	}

	public static int spawnBankOffsetZ() {
		return data.spawnBankOffsetZ;
	}

	/** Yer yuzeyinin uzerinde MB platformu (blok). */
	public static int centralBankElevationBlocks() {
		return data.centralBankElevationBlocks;
	}

	public static boolean masakEnabled() {
		return data.masakEnabled;
	}

	public static long masakTransferWindowMs() {
		return data.masakTransferWindowMs;
	}

	public static int masakMaxTransfersInWindow() {
		return data.masakMaxTransfersInWindow;
	}

	public static long masakLargeTransferGrams() {
		return data.masakLargeTransferGrams;
	}

	public static int masakAutoFreezeRisk() {
		return data.masakAutoFreezeRisk;
	}

	public static int masakCreditPenalty() {
		return data.masakCreditPenalty;
	}

	public static int masakBlackMarketThreshold() {
		return data.masakBlackMarketThreshold;
	}

	public static int masakLaunderAttemptThreshold() {
		return data.masakLaunderAttemptThreshold;
	}

	public static double launderBaseDetectionRisk() {
		return data.launderBaseDetectionRisk;
	}

	public static double launderRiskPer100Grams() {
		return data.launderRiskPer100Grams;
	}

	public static double launderRepeatRiskBonus() {
		return data.launderRepeatRiskBonus;
	}

	public static double launderFinePercent() {
		return data.launderFinePercent;
	}

	public static double launderServiceFeePercent() {
		return data.launderServiceFeePercent;
	}

	public static double blackMarketSellMultiplier() {
		return data.blackMarketSellMultiplier;
	}

	public static double blackMarketBuyPremium() {
		return data.blackMarketBuyPremium;
	}

	public static long exchangeListingFeeMg() {
		return data.exchangeListingFeeMg;
	}

	public static long tokenCreationFeeMg() {
		return data.tokenCreationFeeMg;
	}

	public static long bankCertificateCostMg() {
		return data.bankCertificateCostMg;
	}

	public static double exchangePriceImpact() {
		return data.exchangePriceImpact;
	}

	public static boolean webDashboardEnabled() {
		return data.webDashboardEnabled;
	}

	public static int webPort() {
		return data.webPort;
	}

	public static String webBindAddress() {
		return data.webBindAddress;
	}

	public static long minCompanyWealthMg() {
		return data.minCompanyWealthMg;
	}

	public static long companyCreationFeeMg() {
		return data.companyCreationFeeMg;
	}

	public static int maxPendingApplications() {
		return data.maxPendingApplications;
	}

	public static int maxEmployeesPerCompany() {
		return data.maxEmployeesPerCompany;
	}

	public static double workforceApplicationChance() {
		return data.workforceApplicationChance;
	}

	public static int workforceApplicationIntervalTicks() {
		return data.workforceApplicationIntervalTicks;
	}

	public static int workforcePayrollIntervalTicks() {
		return data.workforcePayrollIntervalTicks;
	}

	public static long baseNpcSalaryMg() {
		return data.baseNpcSalaryMg;
	}

	public static long maxNpcSalaryBonusMg() {
		return data.maxNpcSalaryBonusMg;
	}

	public static long playerDailySalaryIntervalMs() {
		return data.playerDailySalaryIntervalMs;
	}

	public static long baseNpcProductionMg() {
		return data.baseNpcProductionMg;
	}

	public static boolean npcProductDeliveryEnabled() {
		return data.npcProductDeliveryEnabled;
	}

	public static int npcDeliveryMaxItems() {
		return data.npcDeliveryMaxItems;
	}

	public static double hunterMeatDeliveryBias() {
		return data.hunterMeatDeliveryBias;
	}

	public static double companyOreReservePercent() {
		return data.companyOreReservePercent;
	}

	public static double employedQuestPlayerPayShare() {
		return data.employedQuestPlayerPayShare;
	}

	public static double employedMarketCompanyShare() {
		return data.employedMarketCompanyShare;
	}

	public static int depotReserveFreeStacks() {
		return data.depotReserveFreeStacks;
	}

	public static int companyVaultReserveFreeStacks() {
		return data.companyVaultReserveFreeStacks;
	}

	public static int depotArchiveBulletinMinItems() {
		return data.depotArchiveBulletinMinItems;
	}

	public static long robberyShockReferenceMg() {
		return data.robberyShockReferenceMg;
	}

	public static double robberyInflationBump() {
		return data.robberyInflationBump;
	}

	public static double robberyRateBump() {
		return data.robberyRateBump;
	}

	public static double blackMarketSmeltCommission() {
		return data.blackMarketSmeltCommission;
	}

	public static double goldSmeltBaseRisk() {
		return data.goldSmeltBaseRisk;
	}

	public static double goldSmeltRiskPer100Grams() {
		return data.goldSmeltRiskPer100Grams;
	}

	public static int goldSmeltCaughtPrisonMinutes() {
		return data.goldSmeltCaughtPrisonMinutes;
	}

	public static double launderInflationBump() {
		return data.launderInflationBump;
	}

	public static double launderRateBump() {
		return data.launderRateBump;
	}

	public static double launderMacroTickInflation() {
		return data.launderMacroTickInflation;
	}

	public static double launderMacroTickRate() {
		return data.launderMacroTickRate;
	}

	public static long robberyShockCooldownMs() {
		return data.robberyShockCooldownMs;
	}

	public static boolean bankRobberyJusticeEnabled() {
		return data.bankRobberyJusticeEnabled;
	}

	public static long bankRobberyInvestigationCooldownMs() {
		return data.bankRobberyInvestigationCooldownMs;
	}

	public static long bankRobberyMinimumDebtMg() {
		return data.bankRobberyMinimumDebtMg;
	}

	/** Kayip MB seri numarali esya sabah ust aramasi kac Minecraft gunu surer. */
	public static int wantedSerialSearchDays() {
		return Math.max(1, data.wantedSerialSearchDays);
	}

	public static long falseTipPenaltyMg() {
		return data.falseTipPenaltyMg;
	}

	public static int falseTipScanThreshold() {
		return data.falseTipScanThreshold;
	}

	public static long insurancePersonalPremiumMg() {
		return data.insurancePersonalPremiumMg;
	}

	public static long insuranceCompanyPremiumMg() {
		return data.insuranceCompanyPremiumMg;
	}

	public static double insuranceCoveragePercent() {
		return data.insuranceCoveragePercent;
	}

	public static long insurancePremiumIntervalMs() {
		return data.insurancePremiumIntervalMs;
	}

	public static double insurancePayoutShareOfLoss() {
		return data.insurancePayoutShareOfLoss;
	}

	public static double reserveBonusStrongCoverageMultiplier() {
		return data.reserveBonusStrongCoverageMultiplier;
	}

	public static double reserveBonusRateReduction() {
		return data.reserveBonusRateReduction;
	}

	public static long reserveBulletinIntervalMs() {
		return data.reserveBulletinIntervalMs;
	}

	public static double stolenBlackMarketAlertChance() {
		return data.stolenBlackMarketAlertChance;
	}

	public static int mayorTermDays() {
		return data.mayorTermDays;
	}

	public static long mayorElectionWindowMs() {
		return data.mayorElectionWindowMs;
	}

	public static long bankRobberyDirtyMinimumMg() {
		return data.bankRobberyDirtyMinimumMg;
	}

	public static long bankRobberySuspectDurationMs() {
		return data.bankRobberySuspectDurationMs;
	}

	public static int securityCameraRadius() {
		return data.securityCameraRadius;
	}

	public static int securityCameraRecordIntervalTicks() {
		return data.securityCameraRecordIntervalTicks;
	}

	public static int heistDurationSeconds() {
		return data.heistDurationSeconds;
	}

	public static int heistMessageIntervalTicks() {
		return data.heistMessageIntervalTicks;
	}

	public static int heistGuardCount() {
		return data.heistGuardCount;
	}

	public static int bankGuardCount() {
		return data.bankGuardCount;
	}

	public static int bankGuardSleepMinutes() {
		return data.bankGuardSleepMinutes;
	}

	public static int theftPrisonMinutes() {
		return data.theftPrisonMinutes;
	}

	public static boolean foreignInvestorEnabled() {
		return data.foreignInvestorEnabled;
	}

	public static int foreignInvestorIntervalTicks() {
		return data.foreignInvestorIntervalTicks;
	}

	public static long foreignInvestorCapitalMg() {
		return data.foreignInvestorCapitalMg;
	}

	public static int foreignInvestorMaxTokens() {
		return data.foreignInvestorMaxTokens;
	}

	public static double foreignInvestorPlayerCompanyBias() {
		return data.foreignInvestorPlayerCompanyBias;
	}

	public static int foreignInvestorMaxTokenBuy() {
		return data.foreignInvestorMaxTokenBuy;
	}

	public static int foreignInvestorMaxShareBuy() {
		return data.foreignInvestorMaxShareBuy;
	}

	public static boolean securityDamageOps() {
		return data.securityDamageOps;
	}

	public static long heistLootMg() {
		return data.heistLootMg;
	}

	public static boolean bankOriginStored() {
		return data.bankOriginX >= 0 && data.bankOriginY >= 0 && data.bankOriginZ >= 0;
	}

	public static int bankOriginX() {
		return data.bankOriginX;
	}

	public static int bankOriginY() {
		return data.bankOriginY;
	}

	public static int bankOriginZ() {
		return data.bankOriginZ;
	}

	public static void setBankOrigin(int x, int y, int z) {
		data.bankOriginX = x;
		data.bankOriginY = y;
		data.bankOriginZ = z;
	}

	public static double targetGoldReserveCoverage() {
		return data.targetGoldReserveCoverage;
	}

	public static final class EconomyConfigData {
		/** Başlangıç cüzdan bakiyesi (altın miligramı). 0 = fiziksel altın ile başla. */
		public long startingBalance = 0;
		public boolean spawnBankEnabled = true;
		public boolean spawnBankBuilt = false;
		public int bankOriginX = -1;
		public int bankOriginY = -1;
		public int bankOriginZ = -1;
		/** Para arzinin en az bu kadari fiziksel altin rezervi ile desteklenmeli (0.08 = %8). */
		public double targetGoldReserveCoverage = 0.08;
		public int spawnBankOffsetX = 24;
		public int spawnBankOffsetZ = 0;
		public int centralBankElevationBlocks = 36;
		public boolean masakEnabled = true;
		public long masakTransferWindowMs = 600_000;
		public int masakMaxTransfersInWindow = 5;
		public long masakLargeTransferGrams = 500;
		public int masakAutoFreezeRisk = 80;
		public int masakCreditPenalty = 15;
		public int masakBlackMarketThreshold = 8;
		public int masakLaunderAttemptThreshold = 3;
		public double launderBaseDetectionRisk = 0.12;
		public double launderRiskPer100Grams = 0.03;
		public double launderRepeatRiskBonus = 0.05;
		public double launderFinePercent = 0.50;
		public double launderServiceFeePercent = 0.08;
		public double blackMarketSellMultiplier = 0.85;
		public double blackMarketBuyPremium = 1.25;
		public long exchangeListingFeeMg = 5_000_000;
		public long tokenCreationFeeMg = 10_000_000;
		public long bankCertificateCostMg = 20_000_000;
		public double exchangePriceImpact = 0.02;
		public boolean webDashboardEnabled = true;
		public int webPort = 8765;
		public String webBindAddress = "0.0.0.0";
		public long minCompanyWealthMg = 500_000;
		public long companyCreationFeeMg = 100_000;
		public int maxPendingApplications = 5;
		public int maxEmployeesPerCompany = 8;
		public double workforceApplicationChance = 0.35;
		public int workforceApplicationIntervalTicks = 2400;
		public int workforcePayrollIntervalTicks = 1200;
		public long baseNpcSalaryMg = 50_000;
		public long maxNpcSalaryBonusMg = 150_000;
		/** Oyuncu maas odeme araligi (ms). Varsayilan 30 dk = oyun icinde "gunluk" maas. */
		public long playerDailySalaryIntervalMs = 1_800_000;
		public long baseNpcProductionMg = 80_000;
		public boolean npcProductDeliveryEnabled = true;
		public int npcDeliveryMaxItems = 4;
		/** 0-1: avci NPC'lerin et teslim etme olasiligi */
		public double hunterMeatDeliveryBias = 0.72;
		/** Maden uretiminin sirket sandigina ayrilan orani (0.02 = %2) */
		public double companyOreReservePercent = 0.02;
		/** Sirket gorevi bitince oyuncunun aldigi nakit payi (0-1) */
		public double employedQuestPlayerPayShare = 0.35;
		/** Istihdamda uygun emtia market satisinin sirkete giden payi */
		public double employedMarketCompanyShare = 0.65;
		public int depotReserveFreeStacks = 2;
		public int companyVaultReserveFreeStacks = 2;
		public int depotArchiveBulletinMinItems = 128;
		public long robberyShockReferenceMg = 50_000_000;
		public double robberyInflationBump = 0.04;
		public double blackMarketSmeltCommission = 0.02;
		public double goldSmeltBaseRisk = 0.08;
		public double goldSmeltRiskPer100Grams = 0.04;
		public int goldSmeltCaughtPrisonMinutes = 8;
		public double launderInflationBump = 0.06;
		public double launderRateBump = 0.012;
		public double launderMacroTickInflation = 0.008;
		public double launderMacroTickRate = 0.003;
		public double robberyRateBump = 0.015;
		public long robberyShockCooldownMs = 600_000;
		public boolean bankRobberyJusticeEnabled = true;
		public long bankRobberyInvestigationCooldownMs = 60_000;
		public long bankRobberyMinimumDebtMg = 1_000_000;
		public int wantedSerialSearchDays = 3;
		public long falseTipPenaltyMg = 150_000;
		public int falseTipScanThreshold = 3;
		public long insurancePersonalPremiumMg = 500_000;
		public long insuranceCompanyPremiumMg = 2_000_000;
		public double insuranceCoveragePercent = 0.25;
		public long insurancePremiumIntervalMs = 2_592_000_000L;
		public double insurancePayoutShareOfLoss = 0.5;
		public double reserveBonusStrongCoverageMultiplier = 1.5;
		public double reserveBonusRateReduction = 0.008;
		public long reserveBulletinIntervalMs = 3_600_000;
		public double stolenBlackMarketAlertChance = 0.35;
		public int mayorTermDays = 14;
		public long mayorElectionWindowMs = 86_400_000L;
		public long bankRobberyDirtyMinimumMg = 50_000;
		public long bankRobberySuspectDurationMs = 604_800_000L;
		public int securityCameraRadius = 48;
		public int securityCameraRecordIntervalTicks = 60;
		public int heistDurationSeconds = 180;
		public int heistMessageIntervalTicks = 100;
		public int heistGuardCount = 3;
		public int bankGuardCount = 4;
		public int bankGuardSleepMinutes = 5;
		public int theftPrisonMinutes = 5;
		public boolean securityDamageOps = false;
		public boolean foreignInvestorEnabled = true;
		public int foreignInvestorIntervalTicks = 160;
		public long foreignInvestorCapitalMg = 80_000_000;
		public int foreignInvestorMaxTokens = 32;
		/** 0-1: listede oyuncu sirketi varken ona yatirim olasiligi */
		public double foreignInvestorPlayerCompanyBias = 0.78;
		public int foreignInvestorMaxTokenBuy = 24;
		public int foreignInvestorMaxShareBuy = 6;
		public long heistLootMg = 2_000_000;
		public double demandFactor = 0.02;
		public double supplyFactor = 0.015;
		public double minPriceMultiplier = 0.25;
		public double maxPriceMultiplier = 4.0;
		public double baseInterestRate = 0.05;
		public double lateInterestRate = 0.02;
		public int minCreditScoreForLoan = 500;
		public long maxLoanAmount = 10000;
		public int loanInstallmentCount = 6;
		public boolean loanConfiscationEnabled = false;
		public double incomeTaxRate = 0.05;
		public double tradeTaxRate = 0.03;
		public double cityTaxRate = 0.02;
		public double wealthTaxRate = 0.01;
		public boolean wealthTaxEnabled = true;
		public long tipRewardMg = 200_000;
		public double companyJobBonusRate = 0.15;
		public int npcEconomyIntervalTicks = 200;
		public double npcEconomyActivityChance = 0.28;
		public int taxEvasionAuditIntervalTicks = 2400;
		public double taxEvasionDirtyRatioThreshold = 0.35;
		public long guildCreationFeeMg = 50_000;
		public int guildMaxMembers = 12;
		public int guildStrikeMaxMinutes = 30;
		public int tradeDisputeWindowHours = 48;
		public double targetInflationRate = 0.02;
		public int marketDecayIntervalTicks = 20;
		public int interestIntervalTicks = 1200;
		public int inflationIntervalTicks = 6000;
		public int eventCheckIntervalTicks = 24000;
		public double randomEventChance = 0.001;
		public double jobBonusMultiplier = 1.15;
		public int playerSaveIntervalTicks = 6000;
	}
}
