package com.mceconomy.gui;

import com.mceconomy.McEconomyMod;
import com.mceconomy.panel.EconomyPanelService;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.economy.PhysicalGoldService;
import com.mceconomy.market.Commodity;
import com.mceconomy.util.Messages;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Items;

public final class BankGuiManager {
	public static final int SLOT_INFO = 10;
	public static final int SLOT_CREATE = 11;
	public static final int SLOT_DEPOSIT = 12;
	public static final int SLOT_WITHDRAW = 13;
	public static final int SLOT_WALLET = 14;
	public static final int SLOT_SELL = 15;
	public static final int SLOT_BUY = 16;
	public static final int SLOT_WALLET_TO_BANK = 17;
	public static final int SLOT_BANK_TO_WALLET = 18;
	public static final int SLOT_ILLEGAL = 20;
	public static final int SLOT_EXCHANGE = 21;
	public static final int SLOT_PRIVATE_BANK = 23;
	public static final int SLOT_APPEAL = 24;
	public static final int SLOT_BACK = 19;
	public static final int SLOT_CLOSE = 22;

	private static final int[] INGOT_OPTIONS = {1, 5, 10, 64};
	private static final int[] GRAM_OPTIONS = {1, 10, 100, 1000};

	private BankGuiManager() {
	}

	public static void openMainMenu(ServerPlayer player) {
		var profile = McEconomyMod.getEconomyManager().profiles().get(player.getUUID());
		if (profile != null && profile.accountFrozen()) {
			player.sendSystemMessage(Messages.tr("command.mceconomy.masak.frozen"));
		}
		if (profile != null && profile.blacklisted()) {
			player.sendSystemMessage(Messages.tr("command.mceconomy.masak.blacklisted"));
		}

		SimpleContainer container = new SimpleContainer(27);
		fillBackgroundPublic(container);

		var economy = McEconomyMod.getEconomyManager();
		long walletMg = economy.currencyService().getBalance(player.getUUID());
		long bankMg = economy.bankService().getBankBalanceMg(player.getUUID());
		int goldInInv = PhysicalGoldService.countGoldIngots(player);

		container.setItem(SLOT_INFO, GuiItems.button(Items.BOOK,
				"Altın Standardı",
				GoldStandard.formatWheatExchange(),
				"Envanter: " + goldInInv + " külçe",
				"Cüzdan: " + GoldStandard.formatMilligrams(walletMg),
				"Banka: " + GoldStandard.formatMilligrams(bankMg),
				"Ürün sat → cüzdan → banka → külçe çek"));

		container.setItem(SLOT_CREATE, GuiItems.button(Items.PAPER, "Hesap Aç", "Ücretsiz vadesiz hesap"));
		String ingotMcLine = "1 kulce = " + GoldStandard.CURRENCY_NAME + String.format("%,.0f", GoldStandard.ingotPriceMc());
		String gramMcLine = "1 gram = " + GoldStandard.CURRENCY_NAME + String.format("%,.0f", GoldStandard.gramPriceMc());
		container.setItem(SLOT_DEPOSIT, GuiItems.button(Items.GOLD_INGOT, "Külçe Yatır",
				"Envanterdeki altını bankaya", ingotMcLine));
		container.setItem(SLOT_WITHDRAW, GuiItems.button(Items.GOLD_BLOCK, "Altın Çek",
				"Külçe veya gram parça", gramMcLine));
		container.setItem(SLOT_WALLET, GuiItems.button(Items.EMERALD, "Cüzdan",
				GoldStandard.formatMilligrams(walletMg), "Market satış geliri buraya"));
		container.setItem(SLOT_SELL, GuiItems.button(Items.WHEAT, "Ürün Sat",
				"Buğday, sebze, maden sat", "Altın kazan (cüzdana)"));
		container.setItem(SLOT_BUY, GuiItems.button(Items.IRON_INGOT, "Mal Al",
				"Altın harcayarak mal al", "Altın külçesi satılmaz/alınmaz"));
		container.setItem(SLOT_WALLET_TO_BANK, GuiItems.button(Items.CHEST,
				"Cüzdan → Banka", "1K / 10K / 100K / 1M $", "Tutar seçerek aktar"));
		container.setItem(SLOT_BANK_TO_WALLET, GuiItems.button(Items.GOLD_NUGGET,
				"Banka → Cüzdan", "1K / 10K / 100K / 1M $", "Tutar seçerek çek"));
		container.setItem(SLOT_ILLEGAL, GuiItems.button(Items.SKELETON_SKULL,
				"Yeraltı", "Karaborsa + aklama", "§cRiskli işlemler"));
		container.setItem(SLOT_EXCHANGE, GuiItems.button(Items.EMERALD_BLOCK,
				"Borsa", "Hisse + coin", "Al / sat / listele"));
		container.setItem(SLOT_PRIVATE_BANK, GuiItems.button(Items.DIAMOND,
				"Özel Banka", "Sertifika + mevduat", "Banka kur / yatır"));
		container.setItem(SLOT_APPEAL, GuiItems.button(Items.WRITABLE_BOOK,
				"MASAK İtiraz", "Hesap dondurma / ceza", "Komut: /itiraz ac ..."));
		container.setItem(SLOT_CLOSE, GuiItems.closeButton());

		open(player, container, "§6§lMerkez Bankası", (slotId, button, p) -> {
			if (!(p instanceof ServerPlayer sp)) {
				return;
			}
			switch (slotId) {
				case SLOT_CREATE -> {
					if (requireLegalSpending(sp)) handleCreateAccount(sp);
				}
				case SLOT_DEPOSIT -> {
					if (requireLegal(sp)) openIngotMenu(sp, true);
				}
				case SLOT_WITHDRAW -> {
					if (requireLegal(sp)) openIngotMenu(sp, false);
				}
				case SLOT_SELL -> {
					if (requireLegal(sp)) {
						sp.closeContainer();
						EconomyPanelService.openPanel(sp, "market");
					}
				}
				case SLOT_BUY -> {
					if (requireLegalSpending(sp)) {
						sp.closeContainer();
						EconomyPanelService.openPanel(sp, "market");
					}
				}
				case SLOT_WALLET_TO_BANK -> {
					if (requireLegalSpending(sp)) openWalletTransferMenu(sp, true);
				}
				case SLOT_BANK_TO_WALLET -> {
					if (requireLegal(sp)) openWalletTransferMenu(sp, false);
				}
				case SLOT_ILLEGAL -> {
					sp.closeContainer();
					EconomyPanelService.openPanel(sp, "illegal");
				}
				case SLOT_EXCHANGE -> {
					if (requireLegalSpending(sp)) {
						sp.closeContainer();
						EconomyPanelService.openPanel(sp, "exchange");
					}
				}
				case SLOT_PRIVATE_BANK -> {
					if (requireLegalSpending(sp)) PrivateBankGuiManager.openHub(sp);
				}
				case SLOT_APPEAL -> sp.sendSystemMessage(Messages.tr("command.mceconomy.appeal.hint"));
				case SLOT_CLOSE -> sp.closeContainer();
				default -> {
				}
			}
		});
	}

	private static boolean requireLegal(ServerPlayer player) {
		var profile = McEconomyMod.getEconomyManager().profiles().get(player.getUUID());
		if (profile != null && !profile.canEarnLegalIncome()) {
			player.sendSystemMessage(Messages.tr("command.mceconomy.masak.restricted"));
			return false;
		}
		return true;
	}

	private static boolean requireLegalSpending(ServerPlayer player) {
		var profile = McEconomyMod.getEconomyManager().profiles().get(player.getUUID());
		if (profile != null && !profile.canUseLegalEconomy()) {
			player.sendSystemMessage(Messages.tr("command.mceconomy.masak.restricted"));
			return false;
		}
		return true;
	}

	private static void openIngotMenu(ServerPlayer player, boolean deposit) {
		SimpleContainer container = new SimpleContainer(27);
		fillBackgroundPublic(container);

		String title = deposit ? "Külçe Yatır" : "Altın Çek";
		for (int i = 0; i < INGOT_OPTIONS.length; i++) {
			int ingots = INGOT_OPTIONS[i];
			long mg = GoldStandard.ingotsToMilligrams(ingots);
			container.setItem(10 + i, GuiItems.button(Items.GOLD_INGOT,
					ingots + " Külçe",
					GoldStandard.formatMilligrams(mg),
					deposit ? "Envanterden bankaya" : "Bankadan külçe"));
		}
		if (!deposit) {
			for (int i = 0; i < GRAM_OPTIONS.length; i++) {
				int grams = GRAM_OPTIONS[i];
				long mg = GoldStandard.gramsToMilligrams(grams);
				container.setItem(14 + i, GuiItems.button(Items.GOLD_NUGGET,
						grams + " gram",
						GoldStandard.formatMilligrams(mg),
						grams + " altın parçacığı"));
			}
		}
		container.setItem(SLOT_BACK, GuiItems.backButton());
		container.setItem(SLOT_CLOSE, GuiItems.closeButton());

		open(player, container, "§6" + title, (slotId, button, p) -> {
			if (!(p instanceof ServerPlayer sp)) {
				return;
			}
			if (slotId == SLOT_BACK) {
				openMainMenu(sp);
				return;
			}
			if (slotId == SLOT_CLOSE) {
				sp.closeContainer();
				return;
			}
			if (slotId >= 10 && slotId < 10 + INGOT_OPTIONS.length) {
				int ingots = INGOT_OPTIONS[slotId - 10];
				if (deposit) {
					handleDeposit(sp, ingots);
				} else {
					handleWithdraw(sp, ingots);
				}
				openIngotMenu(sp, deposit);
			} else if (!deposit && slotId >= 14 && slotId < 14 + GRAM_OPTIONS.length) {
				int grams = GRAM_OPTIONS[slotId - 14];
				handleWithdrawGrams(sp, grams);
				openIngotMenu(sp, false);
			}
		});
	}

	private static void handleWithdrawGrams(ServerPlayer player, int grams) {
		if (McEconomyMod.getEconomyManager().bankService().getChecking(player.getUUID()).isEmpty()) {
			player.sendSystemMessage(Messages.tr("command.mceconomy.bank.no_account"));
			return;
		}
		long mg = GoldStandard.gramsToMilligrams(grams);
		if (McEconomyMod.getEconomyManager().bankService().withdrawPhysicalGoldGrams(player.getUUID(), player, grams)) {
			player.sendSystemMessage(Component.literal(
					"§a[Banka] §f" + grams + " gram altin parcacik olarak cekildi (" + GoldStandard.formatMilligrams(mg) + ")."));
		} else {
			player.sendSystemMessage(Messages.tr("command.mceconomy.pay.insufficient"));
		}
	}

	private static void openSellMenu(ServerPlayer player) {
		openSellMenu(player, 0);
	}

	private static void openSellMenu(ServerPlayer player, int page) {
		SimpleContainer container = new SimpleContainer(27);
		fillBackgroundPublic(container);

		Commodity[] commodities = Commodity.sellableCommodities();
		int pageSize = 7;
		int start = page * pageSize;
		for (int i = 0; i < pageSize && start + i < commodities.length; i++) {
			Commodity commodity = commodities[start + i];
			long price = McEconomyMod.getEconomyManager().marketService().priceEngine().getUnitPrice(commodity);
			container.setItem(10 + i, GuiItems.button(commodity.item(),
					commodity.displayName(),
					"Satış: " + GoldStandard.formatMilligrams(price) + " / adet",
					"Sol tık: 1 adet sat",
					"Sağ tık: 64 adet sat"));
		}
		if (page > 0) {
			container.setItem(18, GuiItems.button(Items.ARROW, "Önceki Sayfa"));
		}
		if (start + pageSize < commodities.length) {
			container.setItem(26, GuiItems.button(Items.ARROW, "Sonraki Sayfa"));
		}
		container.setItem(SLOT_BACK, GuiItems.backButton());
		container.setItem(SLOT_CLOSE, GuiItems.closeButton());

		final int currentPage = page;
		open(player, container, "§aÜrün Satışı (" + (page + 1) + ")", (slotId, button, p) -> {
			if (!(p instanceof ServerPlayer sp)) {
				return;
			}
			if (slotId == SLOT_BACK) {
				openMainMenu(sp);
				return;
			}
			if (slotId == SLOT_CLOSE) {
				sp.closeContainer();
				return;
			}
			if (slotId == 18 && currentPage > 0) {
				openSellMenu(sp, currentPage - 1);
				return;
			}
			if (slotId == 26 && start + pageSize < commodities.length) {
				openSellMenu(sp, currentPage + 1);
				return;
			}
			if (slotId >= 10 && slotId < 10 + pageSize && start + (slotId - 10) < commodities.length) {
				Commodity commodity = commodities[start + (slotId - 10)];
				int qty = button == 0 ? 1 : 64;
				handleSell(sp, commodity, qty);
				openSellMenu(sp, currentPage);
			}
		});
	}

	private static void openBuyMenu(ServerPlayer player) {
		openBuyMenu(player, 0);
	}

	private static void openBuyMenu(ServerPlayer player, int page) {
		SimpleContainer container = new SimpleContainer(27);
		fillBackgroundPublic(container);

		Commodity[] commodities = Commodity.buyableCommodities();
		int pageSize = 7;
		int start = page * pageSize;
		for (int i = 0; i < pageSize && start + i < commodities.length; i++) {
			Commodity commodity = commodities[start + i];
			long price = McEconomyMod.getEconomyManager().marketService().priceEngine().getUnitPrice(commodity);
			container.setItem(10 + i, GuiItems.button(commodity.item(),
					commodity.displayName(),
					"Alış: " + GoldStandard.formatMilligrams(price) + " / adet",
					"Sol tık: 1 adet al",
					"Sağ tık: 16 adet al",
					"Cüzdan bakiyesinden düşülür"));
		}
		if (page > 0) {
			container.setItem(18, GuiItems.button(Items.ARROW, "Önceki Sayfa"));
		}
		if (start + pageSize < commodities.length) {
			container.setItem(26, GuiItems.button(Items.ARROW, "Sonraki Sayfa"));
		}
		container.setItem(SLOT_BACK, GuiItems.backButton());
		container.setItem(SLOT_CLOSE, GuiItems.closeButton());

		final int currentPage = page;
		open(player, container, "§bMal Alışı (" + (page + 1) + ")", (slotId, button, p) -> {
			if (!(p instanceof ServerPlayer sp)) {
				return;
			}
			if (slotId == SLOT_BACK) {
				openMainMenu(sp);
				return;
			}
			if (slotId == SLOT_CLOSE) {
				sp.closeContainer();
				return;
			}
			if (slotId == 18 && currentPage > 0) {
				openBuyMenu(sp, currentPage - 1);
				return;
			}
			if (slotId == 26 && start + pageSize < commodities.length) {
				openBuyMenu(sp, currentPage + 1);
				return;
			}
			if (slotId >= 10 && slotId < 10 + pageSize && start + (slotId - 10) < commodities.length) {
				Commodity commodity = commodities[start + (slotId - 10)];
				int qty = button == 0 ? 1 : 16;
				handleBuy(sp, commodity, qty);
				openBuyMenu(sp, currentPage);
			}
		});
	}

	private static void handleCreateAccount(ServerPlayer player) {
		try {
			if (McEconomyMod.getEconomyManager().bankService().createCheckingAccount(player.getUUID())) {
				player.sendSystemMessage(Messages.tr("command.mceconomy.bank.created"));
			} else {
				player.sendSystemMessage(Messages.tr("command.mceconomy.bank.already_exists"));
			}
		} catch (Exception e) {
			player.sendSystemMessage(Messages.tr("command.mceconomy.error.generic"));
		}
	}

	private static void handleDeposit(ServerPlayer player, int ingots) {
		if (McEconomyMod.getEconomyManager().bankService().getChecking(player.getUUID()).isEmpty()) {
			player.sendSystemMessage(Messages.tr("command.mceconomy.bank.no_account"));
			return;
		}
		long mg = GoldStandard.ingotsToMilligrams(ingots);
		int have = PhysicalGoldService.countGoldIngots(player);
		if (have < ingots) {
			player.sendSystemMessage(Messages.tr("command.mceconomy.bank.no_gold"));
			return;
		}
		if (PhysicalGoldService.hasBankTrackedGoldIngots(player)
				&& PhysicalGoldService.countDepositEligibleGoldIngots(player) < ingots) {
			player.sendSystemMessage(Messages.tr("command.mceconomy.bank.wanted_gold_deposit"));
			return;
		}
		if (McEconomyMod.getEconomyManager().bankService().depositPhysicalGold(player.getUUID(), player, ingots)) {
			player.sendSystemMessage(Messages.tr("command.mceconomy.bank.physical_deposit", ingots, mg));
		} else {
			player.sendSystemMessage(Messages.tr("command.mceconomy.bank.no_gold"));
		}
	}

	private static void handleWithdraw(ServerPlayer player, int ingots) {
		if (McEconomyMod.getEconomyManager().bankService().getChecking(player.getUUID()).isEmpty()) {
			player.sendSystemMessage(Messages.tr("command.mceconomy.bank.no_account"));
			return;
		}
		long mg = GoldStandard.ingotsToMilligrams(ingots);
		if (McEconomyMod.getEconomyManager().bankService().withdrawPhysicalGold(player.getUUID(), player, ingots)) {
			player.sendSystemMessage(Messages.tr("command.mceconomy.bank.physical_withdraw", ingots, mg));
		} else if (McEconomyMod.getEconomyManager().bankService().getBankBalanceMg(player.getUUID()) < mg) {
			player.sendSystemMessage(Messages.tr("command.mceconomy.pay.insufficient"));
		} else {
			player.sendSystemMessage(Messages.tr("command.mceconomy.bank.inventory_full"));
		}
	}

	private static final double[] WALLET_TRANSFER_MC = {1_000, 10_000, 100_000, 1_000_000};

	private static void openWalletTransferMenu(ServerPlayer player, boolean toBank) {
		if (McEconomyMod.getEconomyManager().bankService().getChecking(player.getUUID()).isEmpty()) {
			player.sendSystemMessage(Messages.tr("command.mceconomy.bank.no_account"));
			return;
		}
		SimpleContainer container = new SimpleContainer(27);
		fillBackgroundPublic(container);
		String title = toBank ? "Cüzdan → Banka" : "Banka → Cüzdan";
		var economy = McEconomyMod.getEconomyManager();
		long walletMg = economy.currencyService().getBalance(player.getUUID());
		long bankMg = economy.bankService().getBankBalanceMg(player.getUUID());
		for (int i = 0; i < WALLET_TRANSFER_MC.length; i++) {
			double displayMc = WALLET_TRANSFER_MC[i];
			long mg = GoldStandard.milligramsForDisplayMc(displayMc);
			container.setItem(10 + i, GuiItems.button(Items.GOLD_NUGGET,
					GoldStandard.CURRENCY_NAME + String.format("%,.0f", displayMc),
					GoldStandard.formatMilligrams(mg),
					toBank ? "Cüzdandan bankaya" : "Bankadan cüzdana"));
		}
		container.setItem(14, GuiItems.button(Items.EMERALD_BLOCK,
				"Tümü",
				toBank
						? "Cüzdan: " + GoldStandard.formatMilligrams(walletMg)
						: "Banka: " + GoldStandard.formatMilligrams(bankMg),
				toBank ? "Tüm cüzdan bakiyesi" : "Tüm banka bakiyesi"));
		container.setItem(SLOT_BACK, GuiItems.backButton());
		container.setItem(SLOT_CLOSE, GuiItems.closeButton());
		open(player, container, "§6" + title, (slotId, button, p) -> {
			if (!(p instanceof ServerPlayer sp)) {
				return;
			}
			if (slotId == SLOT_BACK) {
				openMainMenu(sp);
				return;
			}
			if (slotId == SLOT_CLOSE) {
				sp.closeContainer();
				return;
			}
			if (slotId == 14) {
				long amount = toBank ? walletMg : bankMg;
				if (toBank) {
					handleWalletToBank(sp, amount);
				} else {
					handleBankToWallet(sp, amount);
				}
				return;
			}
			if (slotId >= 10 && slotId < 10 + WALLET_TRANSFER_MC.length) {
				long mg = GoldStandard.milligramsForDisplayMc(WALLET_TRANSFER_MC[slotId - 10]);
				if (toBank) {
					handleWalletToBank(sp, mg);
				} else {
					handleBankToWallet(sp, mg);
				}
			}
		});
	}

	private static void handleWalletToBank(ServerPlayer player, long milligrams) {
		if (McEconomyMod.getEconomyManager().bankService().getChecking(player.getUUID()).isEmpty()) {
			player.sendSystemMessage(Messages.tr("command.mceconomy.bank.no_account"));
			return;
		}
		if (milligrams <= 0) {
			return;
		}
		if (McEconomyMod.getEconomyManager().bankService().depositToBank(player.getUUID(), milligrams)) {
			player.sendSystemMessage(Messages.tr("command.mceconomy.bank.deposit", milligrams));
			openMainMenu(player);
		} else {
			player.sendSystemMessage(Messages.tr("command.mceconomy.pay.insufficient"));
		}
	}

	private static void handleBankToWallet(ServerPlayer player, long milligrams) {
		if (McEconomyMod.getEconomyManager().bankService().getChecking(player.getUUID()).isEmpty()) {
			player.sendSystemMessage(Messages.tr("command.mceconomy.bank.no_account"));
			return;
		}
		if (milligrams <= 0) {
			return;
		}
		if (McEconomyMod.getEconomyManager().bankService().withdrawFromBank(player.getUUID(), milligrams)) {
			player.sendSystemMessage(Messages.tr("command.mceconomy.bank.withdraw", milligrams));
			openMainMenu(player);
		} else {
			player.sendSystemMessage(Messages.tr("command.mceconomy.pay.insufficient"));
		}
	}

	private static void handleSell(ServerPlayer player, Commodity commodity, int quantity) {
		var market = McEconomyMod.getEconomyManager().marketService();
		long unitPrice = market.priceEngine().getUnitPrice(commodity);
		if (market.sell(player, commodity, quantity)) {
			long total = unitPrice * quantity;
			player.sendSystemMessage(Messages.tr("command.mceconomy.market.sell", quantity, commodity.displayName(), total));
		} else {
			player.sendSystemMessage(Messages.tr("command.mceconomy.market.insufficient_items"));
		}
	}

	private static void handleBuy(ServerPlayer player, Commodity commodity, int quantity) {
		var market = McEconomyMod.getEconomyManager().marketService();
		long unitPrice = market.priceEngine().getUnitPrice(commodity);
		if (market.buy(player, commodity, quantity)) {
			long total = unitPrice * quantity;
			player.sendSystemMessage(Messages.tr("command.mceconomy.market.buy", quantity, commodity.displayName(), total));
		} else {
			player.sendSystemMessage(Messages.tr("command.mceconomy.market.insufficient_coins"));
		}
	}

	public static void fillBackgroundPublic(SimpleContainer container) {
		for (int i = 0; i < 27; i++) {
			container.setItem(i, GuiItems.filler());
		}
	}

	public static void openMenu(ServerPlayer player, SimpleContainer container,
			String title, EconomyMenu.MenuActionHandler handler) {
		open(player, container, title, handler);
	}

	private static void fillBackground(SimpleContainer container) {
		fillBackgroundPublic(container);
	}

	private static void open(ServerPlayer player, SimpleContainer container,
			String title, EconomyMenu.MenuActionHandler handler) {
		player.openMenu(new net.minecraft.world.MenuProvider() {
			@Override
			public Component getDisplayName() {
				return Component.literal(title);
			}

			@Override
			public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
					int syncId, net.minecraft.world.entity.player.Inventory inv, net.minecraft.world.entity.player.Player p) {
				return new EconomyMenu(syncId, inv, container, handler);
			}
		});
	}
}
