package com.mceconomy.municipal;

import com.mceconomy.McEconomyMod;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.market.MarketService;
import com.mceconomy.economy.EconomyEventCategory;
import com.mceconomy.economy.EconomyEventDirection;
import com.mceconomy.economy.EconomyEventService;
import com.mceconomy.tax.CentralBank;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/** Belediye butcesinin otomatik ekonomiye geri harcanmasi. */
public final class MunicipalEconomyService {
	private final CentralBank centralBank;
	private EconomyEventService economyEventService;

	public MunicipalEconomyService(CentralBank centralBank) {
		this.centralBank = centralBank;
	}

	public void bindEconomyEventService(EconomyEventService economyEventService) {
		this.economyEventService = economyEventService;
	}

	public void tick(MinecraftServer server, MarketService marketService) {
		if (server == null || marketService == null) {
			return;
		}
		long budget = centralBank.getMunicipalBudgetMg();
		long minSpend = EconomyConfig.municipalAutoSpendMinBudgetMg();
		if (budget < minSpend) {
			return;
		}
		long spend = Math.min(budget / 10, Math.max(1L,
				(budget * EconomyConfig.municipalAutoSpendRateBps()) / 10_000L));
		if (!centralBank.spendMunicipalBudget(spend, "AUTO_SPEND", "Otomatik altyapi ve piyasa destegi")) {
			return;
		}
		if (economyEventService != null) {
			economyEventService.recordMunicipal(EconomyEventCategory.SUBSIDY, EconomyEventDirection.OUT, spend,
					"MARKET_SUBSIDY", "Piyasa destegi uygulandi: " + GoldStandard.formatMilligrams(spend));
		}
		marketService.applyMunicipalSubsidy(spend);
		server.getPlayerList().broadcastSystemMessage(Component.literal(
				"§6[Belediye] §fAltyapi ve piyasa destegi: "
						+ GoldStandard.formatMilligrams(spend) + " harcandi."), false);
		McEconomyMod.LOGGER.debug("Belediye otomatik harcama: {} mg", spend);
	}
}
