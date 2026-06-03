package com.mceconomy.news;

import com.mceconomy.McEconomyMod;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.market.MarketPriceEngine;
import com.mceconomy.persistence.repo.EconomyBulletinRepository;
import com.mceconomy.reserve.GoldReserveService;
import com.mceconomy.tax.CentralBank;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Resmi ekonomi bulteni — soygun ve makro sok haberleri. */
public final class EconomyBulletinService {
	public enum Category {
		ROBBERY("SOYGUN"),
		STORAGE("DEPO"),
		MACRO("MAKRO");

		private final String label;

		Category(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
			.withLocale(new Locale("tr", "TR"))
			.withZone(ZoneId.systemDefault());

	private final EconomyBulletinRepository repository;
	private final Map<String, Long> shockCooldownMs = new HashMap<>();
	private long lastReserveBulletinMs;

	public EconomyBulletinService(EconomyBulletinRepository repository) {
		this.repository = repository;
	}

	public List<EconomyBulletin> recent(int limit) {
		try {
			return repository.loadRecent(limit);
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Bulten yuklenemedi", e);
			return List.of();
		}
	}

	public List<EconomyBulletin> recentByCategory(String category, int limit) {
		try {
			if (category == null || category.isBlank()) {
				return repository.loadRecent(limit);
			}
			return repository.loadRecentByCategory(category.toUpperCase(), limit);
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Bulten yuklenemedi", e);
			return List.of();
		}
	}

	public void publishStorageNotice(MinecraftServer server, String headline, String detail) {
		publish(server, Category.STORAGE, headline, detail, 0, false);
	}

	public void publishRobbery(MinecraftServer server, CentralBank centralBank, MarketPriceEngine priceEngine,
			String headline, String detail, long stolenValueMg) {
		if (!applyRobberyShock(centralBank, priceEngine, stolenValueMg, "robbery")) {
			return;
		}
		publish(server, Category.ROBBERY, headline, detail, stolenValueMg, true);
		var manager = McEconomyMod.getEconomyManager();
		if (manager != null && manager.insuranceService() != null) {
			manager.insuranceService().payRobberyClaims(stolenValueMg, server);
		}
		try {
			centralBank.save();
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Soygun sonrasi MB kaydi basarisiz", e);
		}
	}

	public void publishReserveReport(MinecraftServer server, CentralBank centralBank, GoldReserveService goldReserve,
			long moneySupplyMg) {
		long now = System.currentTimeMillis();
		if (now - lastReserveBulletinMs < EconomyConfig.reserveBulletinIntervalMs()) {
			return;
		}
		lastReserveBulletinMs = now;
		double coverage = goldReserve.coverageRatio(moneySupplyMg);
		double target = EconomyConfig.targetGoldReserveCoverage();
		int blocks = goldReserve.cachedGoldBlocks();
		long backing = goldReserve.backingMilligrams();
		String headline = coverage >= target * EconomyConfig.reserveBonusStrongCoverageMultiplier()
				? "MB REZERV RAPORU: GUC LU REZERV — FAIZ INDIRIMI"
				: "MB REZERV RAPORU";
		String detail = blocks + " altin blogu, destek orani %"
				+ String.format("%.1f", coverage * 100)
				+ " (hedef %" + String.format("%.0f", target * 100) + "). "
				+ "Rezerv degeri: " + GoldStandard.formatMilligrams(backing) + ". "
				+ "Temel faiz: %" + String.format("%.2f", centralBank.getBaseRate() * 100) + ".";
		publish(server, Category.MACRO, headline, detail, backing, false);
	}

	private boolean applyRobberyShock(CentralBank centralBank, MarketPriceEngine priceEngine,
			long stolenValueMg, String cooldownKey) {
		long now = System.currentTimeMillis();
		long last = shockCooldownMs.getOrDefault(cooldownKey, 0L);
		if (now - last < EconomyConfig.robberyShockCooldownMs()) {
			return false;
		}
		shockCooldownMs.put(cooldownKey, now);

		double scale = Math.min(1.0, stolenValueMg / (double) Math.max(1L, EconomyConfig.robberyShockReferenceMg()));
		double inflationBump = EconomyConfig.robberyInflationBump() * (0.35 + scale * 0.65);
		double rateBump = EconomyConfig.robberyRateBump() * (0.35 + scale * 0.65);

		centralBank.setInflationRate(Math.min(0.5, centralBank.getInflationRate() + inflationBump));
		centralBank.setBaseRate(Math.min(0.25, centralBank.getBaseRate() + rateBump));

		double goldBump = 1.0 + Math.min(0.15, inflationBump * 0.9);
		double newGoldFactor = Math.max(1.0, centralBank.getGoldFactor() * goldBump);
		centralBank.setGoldFactor(newGoldFactor);
		GoldStandard.setGoldFactor(newGoldFactor);

		if (priceEngine != null) {
			priceEngine.setGlobalMultiplier(priceEngine.globalMultiplier() * (1.0 + inflationBump * 0.45));
		}
		return true;
	}

	private void publish(MinecraftServer server, Category category, String headline, String detail,
			long valueMg, boolean macroNote) {
		long now = System.currentTimeMillis();
		String body = detail + (macroNote
				? "\nMerkez Bankasi enflasyon ve faiz oranlarini yukari cekti."
				: "");
		try {
			repository.insert(new EconomyBulletin(0, category.name(), headline, body, valueMg, now));
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Bulten kaydi basarisiz", e);
		}
		broadcastBulletin(server, category, headline, body, valueMg, now);
	}

	private void broadcastBulletin(MinecraftServer server, Category category, String headline, String body,
			long valueMg, long createdAt) {
		if (server == null) {
			return;
		}
		String time = TIME_FMT.format(Instant.ofEpochMilli(createdAt));
		String valueLine = valueMg > 0
				? "§eCalinan / kayip deger: §6" + GoldStandard.formatMilligrams(valueMg)
				: "";
		String[] lines = {
				"§4§l════════════════════════════════",
				"§c§l!! BULTEN !! §7[" + category.label() + "] §8" + time,
				"§f§l" + headline,
				valueLine,
				"§7" + body.replace("\n", " §7| "),
				"§e/bulten §7— son haberler",
				"§4§l════════════════════════════════"
		};
		for (String line : lines) {
			if (line == null || line.isEmpty()) {
				continue;
			}
			server.getPlayerList().broadcastSystemMessage(Component.literal(line), false);
		}
	}
}
