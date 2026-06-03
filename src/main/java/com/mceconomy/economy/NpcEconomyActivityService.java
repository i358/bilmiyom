package com.mceconomy.economy;

import com.mceconomy.McEconomyMod;
import com.mceconomy.blackmarket.IllegalGood;
import com.mceconomy.blackmarket.PlayerBlackMarketListing;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.market.Commodity;
import com.mceconomy.market.MarketService;
import com.mceconomy.tax.CentralBank;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Rastgele NPC alim/satim — market ve karaborsa canliligini artirir. */
public final class NpcEconomyActivityService {
	private static final String[] TRADER_NAMES = {
			"Tuccar Ahmet", "Simyaci Leyla", "Koleksiyoncu Boris", "Toptanci Nuri", "Gezgin Elif"
	};

	public void tick(MinecraftServer server) {
		if (ThreadLocalRandom.current().nextDouble() > EconomyConfig.npcEconomyActivityChance()) {
			return;
		}
		var manager = McEconomyMod.getEconomyManager();
		if (manager == null || !manager.isLoaded()) {
			return;
		}
		int roll = ThreadLocalRandom.current().nextInt(100);
		String trader = TRADER_NAMES[ThreadLocalRandom.current().nextInt(TRADER_NAMES.length)];
		try {
			if (roll < 40) {
				simulateMarketBuy(manager.marketService(), trader);
			} else if (roll < 70) {
				simulateMarketSell(manager.marketService(), trader);
			} else {
				simulateBlackMarketBuy(manager, trader);
			}
		} catch (Exception e) {
			McEconomyMod.LOGGER.debug("NPC ekonomi tick", e);
		}
	}

	private void simulateMarketBuy(MarketService market, String trader) {
		List<Commodity> sellable = java.util.Arrays.stream(Commodity.values()).filter(Commodity::sellable).toList();
		if (sellable.isEmpty()) {
			return;
		}
		Commodity commodity = sellable.get(ThreadLocalRandom.current().nextInt(sellable.size()));
		int qty = 1 + ThreadLocalRandom.current().nextInt(8);
		market.priceEngine().onBuy(commodity, qty);
		broadcast("§7[NPC] §f" + trader + " marketten §a" + qty + "x " + commodity.displayName() + " §7aldi.");
	}

	private void simulateMarketSell(MarketService market, String trader) {
		List<Commodity> sellable = java.util.Arrays.stream(Commodity.values()).filter(Commodity::sellable).toList();
		if (sellable.isEmpty()) {
			return;
		}
		Commodity commodity = sellable.get(ThreadLocalRandom.current().nextInt(sellable.size()));
		int qty = 1 + ThreadLocalRandom.current().nextInt(12);
		market.priceEngine().onSell(commodity, qty);
		broadcast("§7[NPC] §f" + trader + " markete §e" + qty + "x " + commodity.displayName() + " §7satti.");
	}

	private void simulateBlackMarketBuy(com.mceconomy.economy.EconomyManager manager, String trader) {
		List<PlayerBlackMarketListing> listings = manager.playerBlackMarket().all();
		if (listings.isEmpty()) {
			IllegalGood[] goods = IllegalGood.tradable();
			if (goods.length > 0) {
				IllegalGood good = goods[ThreadLocalRandom.current().nextInt(goods.length)];
				manager.marketService().priceEngine().onBuy(good.priceReference(), 2);
				broadcast("§8[NPC] §f" + trader + " karaborsadan §c" + good.displayName() + " §7aldi.");
			}
			return;
		}
		PlayerBlackMarketListing listing = listings.get(ThreadLocalRandom.current().nextInt(listings.size()));
		int qty = Math.min(listing.stock(), 1 + ThreadLocalRandom.current().nextInt(4));
		long cost = listing.priceMg() * qty;
		CentralBank bank = manager.centralBank();
		if (bank == null || !bank.spendMunicipalBudget(cost)) {
			return;
		}
		if (manager.playerBlackMarket().npcPurchase(listing, qty)) {
			broadcast("§8[NPC] §f" + trader + " oyuncu ilanindan §c" + qty + "x "
					+ listing.displayName() + " §7aldi.");
		} else {
			bank.addMunicipalBudget(cost);
		}
	}

	private void broadcast(String message) {
		MinecraftServer server = McEconomyMod.getEconomyManager().server();
		if (server == null) {
			return;
		}
		server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
	}
}
