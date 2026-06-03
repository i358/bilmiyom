package com.mceconomy.gui;

import com.mceconomy.McEconomyMod;
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
		container.setItem(SLOT_DEPOSIT, GuiItems.button(Items.GOLD_INGOT, "Külçe Yatır",
				"Envanterdeki altını bankaya", "1 külçe = 1000 gram"));
		container.setItem(SLOT_WITHDRAW, GuiItems.button(Items.GOLD_BLOCK, "Külçe Çek",
				"Bankadan fiziksel altın al", "1000 gram = 1 külçe"));
		container.setItem(SLOT_WALLET, GuiItems.button(Items.EMERALD, "Cüzdan",
				GoldStandard.formatMilligrams(walletMg), "Market satış geliri buraya"));
		container.setItem(SLOT_SELL, GuiItems.button(Items.WHEAT, "Ürün Sat",
				"Buğday, sebze, maden sat", "Altın kazan (cüzdana)"));
		container.setItem(SLOT_BUY, GuiItems.button(Items.IRON_INGOT, "Mal Al",
				"Altın harcayarak mal al", "Altın külçesi satılmaz/alınmaz"));
		container.setItem(SLOT_WALLET_TO_BANK, GuiItems.button(Items.CHEST,
				"Cüzdan → Banka", "1 külçe değeri aktar", "Külçe çekmeden önce"));
		container.setItem(SLOT_BANK_TO_WALLET, GuiItems.button(Items.GOLD_NUGGET,
				"Banka → Cüzdan", "1 külçe değeri aktar", "Mal almak için"));
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
					if (requireLegal(sp)) openSellMenu(sp);
				}
				case SLOT_BUY -> {
					if (requireLegalSpending(sp)) openBuyMenu(sp);
				}
				case SLOT_WALLET_TO_BANK -> {
					if (requireLegalSpending(sp)) handleWalletToBank(sp, 1);
				}
				case SLOT_BANK_TO_WALLET -> {
					if (requireLegal(sp)) handleBankToWallet(sp, 1);
				}
				case SLOT_ILLEGAL -> IllegalGuiManager.openHub(sp);
				case SLOT_EXCHANGE -> {
					if (requireLegalSpending(sp)) ExchangeGuiManager.openHub(sp);
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

		String title = deposit ? "Külçe Yatır" : "Külçe Çek";
		for (int i = 0; i < INGOT_OPTIONS.length; i++) {
			int ingots = INGOT_OPTIONS[i];
			long mg = GoldStandard.ingotsToMilligrams(ingots);
			container.setItem(10 + i, GuiItems.button(Items.GOLD_INGOT,
					ingots + " Külçe",
					GoldStandard.formatMilligrams(mg),
					deposit ? "Envanterden bankaya" : "Bankadan envantere"));
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
			}
		});
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

	private static void handleWalletToBank(ServerPlayer player, int ingots) {
		if (McEconomyMod.getEconomyManager().bankService().getChecking(player.getUUID()).isEmpty()) {
			player.sendSystemMessage(Messages.tr("command.mceconomy.bank.no_account"));
			return;
		}
		long mg = GoldStandard.ingotsToMilligrams(ingots);
		if (McEconomyMod.getEconomyManager().bankService().depositToBank(player.getUUID(), mg)) {
			player.sendSystemMessage(Messages.tr("command.mceconomy.bank.deposit", mg));
			openMainMenu(player);
		} else {
			player.sendSystemMessage(Messages.tr("command.mceconomy.pay.insufficient"));
		}
	}

	private static void handleBankToWallet(ServerPlayer player, int ingots) {
		if (McEconomyMod.getEconomyManager().bankService().getChecking(player.getUUID()).isEmpty()) {
			player.sendSystemMessage(Messages.tr("command.mceconomy.bank.no_account"));
			return;
		}
		long mg = GoldStandard.ingotsToMilligrams(ingots);
		if (McEconomyMod.getEconomyManager().bankService().withdrawFromBank(player.getUUID(), mg)) {
			player.sendSystemMessage(Messages.tr("command.mceconomy.bank.withdraw", mg));
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
