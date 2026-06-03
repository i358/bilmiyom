package com.mceconomy.regulation;

import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.economy.TransactionLedger;
import com.mceconomy.economy.TransactionType;
import com.mceconomy.persistence.repo.MasakRepository;
import com.mceconomy.player.PlayerEconomyProfile;
import net.minecraft.server.level.ServerPlayer;

import java.sql.SQLException;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class MasakService {
	private final Map<UUID, PlayerEconomyProfile> profiles;
	private final TransactionLedger ledger;
	private final MasakRepository repository;
	private final Map<UUID, Deque<Long>> transferTimestamps = new ConcurrentHashMap<>();
	private final Map<UUID, Integer> blackMarketActivity = new ConcurrentHashMap<>();
	private final Map<UUID, Integer> launderingAttempts = new ConcurrentHashMap<>();

	public MasakService(Map<UUID, PlayerEconomyProfile> profiles, TransactionLedger ledger,
			MasakRepository repository) {
		this.profiles = profiles;
		this.ledger = ledger;
		this.repository = repository;
	}

	public void onTransfer(UUID player, long amountMg) {
		if (!EconomyConfig.masakEnabled()) {
			return;
		}
		Deque<Long> window = transferTimestamps.computeIfAbsent(player, k -> new ConcurrentLinkedDeque<>());
		long now = System.currentTimeMillis();
		window.addLast(now);
		while (!window.isEmpty() && now - window.peekFirst() > EconomyConfig.masakTransferWindowMs()) {
			window.pollFirst();
		}
		long grams = amountMg / GoldStandard.MILLIGRAMS_PER_GRAM;
		if (window.size() >= EconomyConfig.masakMaxTransfersInWindow()) {
			flag(player, "Kısa sürede çok transfer", 75, amountMg, true);
		} else if (grams >= EconomyConfig.masakLargeTransferGrams()) {
			flag(player, "Yüksek tutarlı transfer", 55, amountMg, false);
		}
	}

	public void onBlackMarketActivity(UUID player, long amountMg) {
		if (!EconomyConfig.masakEnabled()) {
			return;
		}
		int count = blackMarketActivity.merge(player, 1, Integer::sum);
		if (count >= EconomyConfig.masakBlackMarketThreshold()) {
			flag(player, "Yoğun karaborsa aktivitesi", 65, amountMg, false);
		}
	}

	public void onGoldSmeltCaught(UUID player, long amountMg) {
		if (!EconomyConfig.masakEnabled()) {
			return;
		}
		flag(player, "Karaborsa altin eritme yakalandi", 92, amountMg, true);
	}

	public void onGoldSmeltSuccess(UUID player, long amountMg) {
		if (!EconomyConfig.masakEnabled()) {
			return;
		}
		blackMarketActivity.merge(player, 1, Integer::sum);
		int count = blackMarketActivity.getOrDefault(player, 0);
		if (count >= EconomyConfig.masakBlackMarketThreshold()) {
			flag(player, "Karaborsa altin eritme aktivitesi", 72, amountMg, false);
		}
	}

	public void onLaunderingAttempt(UUID player, long amountMg, boolean caught) {
		if (!EconomyConfig.masakEnabled()) {
			return;
		}
		launderingAttempts.merge(player, 1, Integer::sum);
		if (caught) {
			flag(player, "Kara para aklama girişimi yakalandı", 90, amountMg, true);
		} else {
			int attempts = launderingAttempts.getOrDefault(player, 0);
			if (attempts >= EconomyConfig.masakLaunderAttemptThreshold()) {
				flag(player, "Tekrarlayan aklama girişimleri", 70, amountMg, false);
			}
		}
	}

	public void onTaxEvasionSuspect(UUID player, long dirtyMg, double dirtyRatio) {
		if (!EconomyConfig.masakEnabled()) {
			return;
		}
		int risk = (int) Math.min(95, 50 + dirtyRatio * 60);
		flag(player, "Vergi kacakciligi suphesi (kara para orani %"
				+ Math.round(dirtyRatio * 100) + ")", risk, dirtyMg, false);
	}

	/** Kara borsada calinti mal satisi — uyari (ihbar gerekmez). */
	public void onStolenGoodsBlackMarketSale(UUID player, int stolenCount, long estimatedValueMg) {
		if (!EconomyConfig.masakEnabled() || stolenCount <= 0) {
			return;
		}
		flag(player, "Kara borsada calinti mal izi (" + stolenCount + " adet)", 55, estimatedValueMg, false);
		var manager = com.mceconomy.McEconomyMod.getEconomyManager();
		if (manager != null && manager.bankRobberyJusticeService() != null) {
			manager.bankRobberyJusticeService().onBlackMarketFence(player, estimatedValueMg, true);
		}
	}

	private void flag(UUID player, String reason, int riskScore, long amountMg, boolean freeze) {
		PlayerEconomyProfile profile = profiles.get(player);
		if (profile == null) {
			return;
		}
		if (freeze && riskScore >= EconomyConfig.masakAutoFreezeRisk()) {
			profile.setAccountFrozen(true);
		}
		profile.creditScore().adjust(-EconomyConfig.masakCreditPenalty());
		try {
			repository.save(MasakAlert.open(player, reason, riskScore, amountMg));
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public boolean isRestricted(UUID uuid) {
		PlayerEconomyProfile profile = profiles.get(uuid);
		return profile != null && !profile.canUseLegalEconomy();
	}

	public List<MasakAlert> openAlerts() {
		try {
			return repository.loadOpenAlerts();
		} catch (SQLException e) {
			return List.of();
		}
	}

	public boolean resolveAlert(long alertId, UUID playerUuid) {
		try {
			for (MasakAlert alert : repository.loadAlertsForPlayer(playerUuid)) {
				if (alert.id() == alertId && !alert.resolved()) {
					repository.save(alert.markResolved());
					PlayerEconomyProfile profile = profiles.get(playerUuid);
					if (profile != null) {
						profile.setAccountFrozen(false);
					}
					return true;
				}
			}
		} catch (SQLException e) {
			return false;
		}
		return false;
	}

	public boolean resolvePlayerAlerts(UUID playerUuid) {
		try {
			boolean any = false;
			for (MasakAlert alert : repository.loadAlertsForPlayer(playerUuid)) {
				if (!alert.resolved()) {
					repository.save(alert.markResolved());
					any = true;
				}
			}
			PlayerEconomyProfile profile = profiles.get(playerUuid);
			if (profile != null) {
				profile.setAccountFrozen(false);
			}
			return any;
		} catch (SQLException e) {
			return false;
		}
	}

	public void applyFine(UUID player, long fineMg) {
		PlayerEconomyProfile profile = profiles.get(player);
		if (profile == null || fineMg <= 0) {
			return;
		}
		long fromClean = Math.min(profile.wallet().balance(), fineMg);
		if (fromClean > 0) {
			profile.wallet().withdraw(fromClean);
		}
		long remaining = fineMg - fromClean;
		if (remaining > 0) {
			profile.dirtyWallet().withdraw(Math.min(profile.dirtyWallet().balance(), remaining));
		}
		ledger.record(player, null, fineMg, TransactionType.MASAK_FINE, "masak_ceza");
		profile.creditScore().adjust(-20);
	}

	public void blacklist(UUID player) {
		PlayerEconomyProfile profile = profiles.get(player);
		if (profile != null) {
			profile.setBlacklisted(true);
			profile.setAccountFrozen(true);
		}
	}

	public void notifyPlayer(ServerPlayer player, String message) {
		player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[MASAK] §f" + message));
	}

	public int calculateLaunderRiskPercent(UUID player, long amountMg) {
		double risk = EconomyConfig.launderBaseDetectionRisk();
		long grams = amountMg / GoldStandard.MILLIGRAMS_PER_GRAM;
		risk += (grams / 100.0) * EconomyConfig.launderRiskPer100Grams();
		risk += launderingAttempts.getOrDefault(player, 0) * EconomyConfig.launderRepeatRiskBonus();
		risk += blackMarketActivity.getOrDefault(player, 0) * 0.01;
		return (int) Math.min(95, Math.round(risk * 100));
	}
}
