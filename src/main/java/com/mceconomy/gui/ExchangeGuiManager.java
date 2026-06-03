package com.mceconomy.gui;

import com.mceconomy.McEconomyMod;
import com.mceconomy.company.Company;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.exchange.ExchangeToken;
import com.mceconomy.util.Messages;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Items;

import java.util.List;

public final class ExchangeGuiManager {
	public static final int SLOT_BACK = 19;
	public static final int SLOT_CLOSE = 22;

	private static final int[] TRADE_AMOUNTS = {1, 10, 100};

	private ExchangeGuiManager() {
	}

	public static void openHub(ServerPlayer player) {
		SimpleContainer container = new SimpleContainer(27);
		BankGuiManager.fillBackgroundPublic(container);

		container.setItem(10, GuiItems.button(Items.EMERALD_BLOCK, "Hisse Senetleri",
				"Borsada listelenen şirketler", "Al / Sat"));
		container.setItem(12, GuiItems.button(Items.GOLD_NUGGET, "Coin / Token",
				"Oyuncu coinleri", "Al / Sat / Oluştur"));
		container.setItem(14, GuiItems.button(Items.WRITABLE_BOOK, "Şirket Listele",
				"Ücret: " + GoldStandard.formatMilligrams(EconomyConfig.exchangeListingFeeMg()),
				"Komut: /borsa listele <sirket> <ticker>"));
		container.setItem(16, GuiItems.button(Items.NETHER_STAR, "Coin Oluştur",
				"Ücret: " + GoldStandard.formatMilligrams(EconomyConfig.tokenCreationFeeMg()),
				"Komut: /borsa coin <sembol> <isim> <adet> <fiyat>"));
		container.setItem(SLOT_CLOSE, GuiItems.closeButton());

		BankGuiManager.openMenu(player, container, "§e§lBorsa İstanbul", (slotId, button, p) -> {
			if (!(p instanceof ServerPlayer sp)) {
				return;
			}
			switch (slotId) {
				case 10 -> openStockList(sp);
				case 12 -> openTokenList(sp);
				case 14 -> sp.sendSystemMessage(Messages.tr("command.mceconomy.exchange.list_hint"));
				case 16 -> sp.sendSystemMessage(Messages.tr("command.mceconomy.exchange.coin_hint"));
				case SLOT_CLOSE -> sp.closeContainer();
				default -> {
				}
			}
		});
	}

	private static void openStockList(ServerPlayer player) {
		var exchange = McEconomyMod.getEconomyManager().exchangeService();
		List<Company> listed = exchange.listedCompanies();
		SimpleContainer container = new SimpleContainer(27);
		BankGuiManager.fillBackgroundPublic(container);

		double index = McEconomyMod.getEconomyManager().marketService().economyIndex().calculate();
		for (int i = 0; i < listed.size() && i < 7; i++) {
			Company company = listed.get(i);
			long price = exchange.sharePrice(company, index);
			int owned = McEconomyMod.getEconomyManager().companyManager()
					.getShareCount(player.getUUID(), company);
			container.setItem(10 + i, GuiItems.button(Items.PAPER,
					"§6" + company.ticker(),
					company.name(),
					"Fiyat: " + GoldStandard.formatMilligrams(price),
					"Portföy: " + owned + " hisse"));
		}
		container.setItem(SLOT_BACK, GuiItems.backButton());
		container.setItem(SLOT_CLOSE, GuiItems.closeButton());

		BankGuiManager.openMenu(player, container, "§6Hisse Senetleri", (slotId, button, p) -> {
			if (!(p instanceof ServerPlayer sp)) {
				return;
			}
			if (slotId == SLOT_BACK) {
				openHub(sp);
				return;
			}
			if (slotId == SLOT_CLOSE) {
				sp.closeContainer();
				return;
			}
			if (slotId >= 10 && slotId < 10 + Math.min(listed.size(), 7)) {
				openStockTrade(sp, listed.get(slotId - 10));
			}
		});
	}

	private static void openStockTrade(ServerPlayer player, Company company) {
		SimpleContainer container = new SimpleContainer(27);
		BankGuiManager.fillBackgroundPublic(container);

		double index = McEconomyMod.getEconomyManager().marketService().economyIndex().calculate();
		long price = McEconomyMod.getEconomyManager().exchangeService().sharePrice(company, index);

		container.setItem(4, GuiItems.button(Items.BOOK,
				company.ticker() + " — " + company.name(),
				"Hisse fiyatı: " + GoldStandard.formatMilligrams(price)));

		for (int i = 0; i < TRADE_AMOUNTS.length; i++) {
			int qty = TRADE_AMOUNTS[i];
			container.setItem(10 + i, GuiItems.button(Items.LIME_DYE,
					"Al " + qty,
					"Toplam: " + GoldStandard.formatMilligrams(price * qty)));
			container.setItem(13 + i, GuiItems.button(Items.RED_DYE,
					"Sat " + qty,
					"Toplam: " + GoldStandard.formatMilligrams(price * qty)));
		}
		container.setItem(SLOT_BACK, GuiItems.backButton());
		container.setItem(SLOT_CLOSE, GuiItems.closeButton());

		BankGuiManager.openMenu(player, container, "§6" + company.ticker(), (slotId, button, p) -> {
			if (!(p instanceof ServerPlayer sp)) {
				return;
			}
			if (slotId == SLOT_BACK) {
				openStockList(sp);
				return;
			}
			if (slotId == SLOT_CLOSE) {
				sp.closeContainer();
				return;
			}
			if (slotId >= 10 && slotId < 10 + TRADE_AMOUNTS.length) {
				int qty = TRADE_AMOUNTS[slotId - 10];
				tradeStock(sp, company, qty, true);
				openStockTrade(sp, company);
			} else if (slotId >= 13 && slotId < 13 + TRADE_AMOUNTS.length) {
				int qty = TRADE_AMOUNTS[slotId - 13];
				tradeStock(sp, company, qty, false);
				openStockTrade(sp, company);
			}
		});
	}

	private static void tradeStock(ServerPlayer player, Company company, int qty, boolean buy) {
		var manager = McEconomyMod.getEconomyManager().companyManager();
		double index = McEconomyMod.getEconomyManager().marketService().economyIndex().calculate();
		try {
			if (buy) {
				if (manager.buyShares(player.getUUID(), company.ticker(), qty, index)) {
					player.sendSystemMessage(Messages.tr("command.mceconomy.company.shares_bought", qty));
				} else {
					player.sendSystemMessage(Messages.tr("command.mceconomy.pay.insufficient"));
				}
			} else if (manager.sellShares(player.getUUID(), company.ticker(), qty, index)) {
				player.sendSystemMessage(Messages.tr("command.mceconomy.company.shares_sold", qty));
			} else {
				player.sendSystemMessage(Messages.tr("command.mceconomy.error.generic"));
			}
		} catch (Exception e) {
			player.sendSystemMessage(Messages.tr("command.mceconomy.error.generic"));
		}
	}

	private static void openTokenList(ServerPlayer player) {
		var exchange = McEconomyMod.getEconomyManager().exchangeService();
		List<ExchangeToken> tokens = exchange.allTokens();
		SimpleContainer container = new SimpleContainer(27);
		BankGuiManager.fillBackgroundPublic(container);

		for (int i = 0; i < tokens.size() && i < 7; i++) {
			ExchangeToken token = tokens.get(i);
			int owned = exchange.tokenBalance(player.getUUID(), token);
			container.setItem(10 + i, GuiItems.button(Items.GOLD_INGOT,
					"§e" + token.symbol(),
					token.displayName(),
					"Fiyat: " + GoldStandard.formatMilligrams(token.priceMg()),
					"Portföy: " + owned,
					"Dolaşım: " + token.circulating() + "/" + token.totalSupply()));
		}
		container.setItem(SLOT_BACK, GuiItems.backButton());
		container.setItem(SLOT_CLOSE, GuiItems.closeButton());

		BankGuiManager.openMenu(player, container, "§eCoin / Token", (slotId, button, p) -> {
			if (!(p instanceof ServerPlayer sp)) {
				return;
			}
			if (slotId == SLOT_BACK) {
				openHub(sp);
				return;
			}
			if (slotId == SLOT_CLOSE) {
				sp.closeContainer();
				return;
			}
			if (slotId >= 10 && slotId < 10 + Math.min(tokens.size(), 7)) {
				openTokenTrade(sp, tokens.get(slotId - 10));
			}
		});
	}

	private static void openTokenTrade(ServerPlayer player, ExchangeToken token) {
		SimpleContainer container = new SimpleContainer(27);
		BankGuiManager.fillBackgroundPublic(container);

		container.setItem(4, GuiItems.button(Items.GOLD_INGOT,
				token.symbol() + " — " + token.displayName(),
				"Fiyat: " + GoldStandard.formatMilligrams(token.priceMg()),
				"Dolaşım: " + token.circulating() + "/" + token.totalSupply()));

		for (int i = 0; i < TRADE_AMOUNTS.length; i++) {
			int qty = TRADE_AMOUNTS[i];
			container.setItem(10 + i, GuiItems.button(Items.LIME_DYE,
					"Al " + qty,
					"Toplam: " + GoldStandard.formatMilligrams(token.priceMg() * qty)));
			container.setItem(13 + i, GuiItems.button(Items.RED_DYE,
					"Sat " + qty,
					"Toplam: " + GoldStandard.formatMilligrams(token.priceMg() * qty)));
		}
		container.setItem(SLOT_BACK, GuiItems.backButton());
		container.setItem(SLOT_CLOSE, GuiItems.closeButton());

		BankGuiManager.openMenu(player, container, "§e" + token.symbol(), (slotId, button, p) -> {
			if (!(p instanceof ServerPlayer sp)) {
				return;
			}
			if (slotId == SLOT_BACK) {
				openTokenList(sp);
				return;
			}
			if (slotId == SLOT_CLOSE) {
				sp.closeContainer();
				return;
			}
			if (slotId >= 10 && slotId < 10 + TRADE_AMOUNTS.length) {
				int qty = TRADE_AMOUNTS[slotId - 10];
				tradeToken(sp, token, qty, true);
				openTokenTrade(sp, token);
			} else if (slotId >= 13 && slotId < 13 + TRADE_AMOUNTS.length) {
				int qty = TRADE_AMOUNTS[slotId - 13];
				tradeToken(sp, token, qty, false);
				openTokenTrade(sp, token);
			}
		});
	}

	private static void tradeToken(ServerPlayer player, ExchangeToken token, int qty, boolean buy) {
		var exchange = McEconomyMod.getEconomyManager().exchangeService();
		try {
			if (buy) {
				if (exchange.buyToken(player.getUUID(), token.symbol(), qty)) {
					player.sendSystemMessage(Messages.tr("command.mceconomy.exchange.token_bought", qty, token.symbol()));
				} else {
					player.sendSystemMessage(Messages.tr("command.mceconomy.pay.insufficient"));
				}
			} else if (exchange.sellToken(player.getUUID(), token.symbol(), qty)) {
				player.sendSystemMessage(Messages.tr("command.mceconomy.exchange.token_sold", qty, token.symbol()));
			} else {
				player.sendSystemMessage(Messages.tr("command.mceconomy.error.generic"));
			}
		} catch (Exception e) {
			player.sendSystemMessage(Messages.tr("command.mceconomy.error.generic"));
		}
	}
}
