package com.mceconomy.gui;

import com.mceconomy.McEconomyMod;
import com.mceconomy.blackmarket.BlackMarketGoldSmeltService;
import com.mceconomy.blackmarket.IllegalGood;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.regulation.LaunderingService;
import com.mceconomy.security.SecurityWeapon;
import com.mceconomy.util.Messages;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Items;

public final class IllegalGuiManager {
	public static final int SLOT_BACK = 19;
	public static final int SLOT_CLOSE = 22;

	private IllegalGuiManager() {
	}

	public static void openHub(ServerPlayer player) {
		SimpleContainer container = new SimpleContainer(27);
		BankGuiManager.fillBackgroundPublic(container);

		long dirty = McEconomyMod.getEconomyManager().currencyService().getDirtyBalance(player.getUUID());
		container.setItem(11, GuiItems.button(Items.SKELETON_SKULL,
				"Karaborsa",
				"Kaçak zırh ve silah",
				"Serbest piyasa fiyatları",
				"Kara para ile işlem"));
		container.setItem(13, GuiItems.button(Items.CAULDRON,
				"Kara Para Aklama",
				"Kara → temiz altın",
				"Yakalanma riski var!",
				"Bakiye: " + GoldStandard.formatMilligrams(dirty)));
		container.setItem(14, GuiItems.button(Items.BLAST_FURNACE,
				"Altın Erit",
				"Seri nolu çalıntı külçe",
				"%2 komisyon, parçacık çıktı",
				"Yakalanırsa ceza — makro sok yok"));
		container.setItem(15, GuiItems.button(Items.CROSSBOW,
				"Kaçak Silahlar",
				"Ruhsatsiz silahlar",
				"Ek hasar — MASAK riski"));
		container.setItem(16, GuiItems.button(Items.PAPER,
				"Kara Para Durumu",
				GoldStandard.formatMilligrams(dirty),
				"Temiz cüzdan ayrı tutulur"));
		container.setItem(SLOT_BACK, GuiItems.backButton());
		container.setItem(SLOT_CLOSE, GuiItems.closeButton());

		BankGuiManager.openMenu(player, container, "§8§lYeraltı Ekonomisi", (slotId, button, p) -> {
			if (!(p instanceof ServerPlayer sp)) {
				return;
			}
			if (slotId == SLOT_BACK) {
				BankGuiManager.openMainMenu(sp);
				return;
			}
			if (slotId == SLOT_CLOSE) {
				sp.closeContainer();
				return;
			}
			switch (slotId) {
				case 11 -> openBlackMarket(sp);
				case 13 -> openLaunderMenu(sp);
				case 14 -> openGoldSmeltMenu(sp);
				case 15 -> openWeaponsMarket(sp);
				case 16 -> sp.sendSystemMessage(Messages.tr("command.mceconomy.dirty.balance",
						McEconomyMod.getEconomyManager().currencyService().getDirtyBalance(sp.getUUID())));
				default -> {
				}
			}
		});
	}

	public static void openBlackMarket(ServerPlayer player) {
		SimpleContainer container = new SimpleContainer(27);
		BankGuiManager.fillBackgroundPublic(container);

		var bm = McEconomyMod.getEconomyManager().blackMarketService();
		IllegalGood[] goods = IllegalGood.tradable();
		for (int i = 0; i < goods.length && i < 7; i++) {
			IllegalGood good = goods[i];
			long sell = bm.getSellPrice(good);
			long buy = bm.getBuyPrice(good);
			container.setItem(10 + i, GuiItems.button(good.item(),
					good.displayName(),
					"Sat: " + bm.formatPrice(sell),
					"Al: " + bm.formatPrice(buy),
					"Sol: 1 sat | Sağ: 1 al"));
		}
		container.setItem(SLOT_BACK, GuiItems.backButton());
		container.setItem(SLOT_CLOSE, GuiItems.closeButton());

		BankGuiManager.openMenu(player, container, "§8Karaborsa", (slotId, button, p) -> {
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
			if (slotId >= 10 && slotId < 10 + Math.min(goods.length, 7)) {
				IllegalGood good = goods[slotId - 10];
				if (button == 0) {
					if (bm.sell(sp, good, 1)) {
						sp.sendSystemMessage(Messages.tr("command.mceconomy.blackmarket.sell", 1, good.displayName(),
								bm.getSellPrice(good)));
					} else {
						sp.sendSystemMessage(Messages.tr("command.mceconomy.market.insufficient_items"));
					}
				} else if (bm.buy(sp, good, 1)) {
					sp.sendSystemMessage(Messages.tr("command.mceconomy.blackmarket.buy", 1, good.displayName(),
							bm.getBuyPrice(good)));
				} else {
					sp.sendSystemMessage(Messages.tr("command.mceconomy.dirty.insufficient"));
				}
				openBlackMarket(sp);
			}
		});
	}

	public static void openWeaponsMarket(ServerPlayer player) {
		SimpleContainer container = new SimpleContainer(27);
		BankGuiManager.fillBackgroundPublic(container);
		var bm = McEconomyMod.getEconomyManager().blackMarketService();
		SecurityWeapon[] weapons = SecurityWeapon.blackMarketWeapons();
		for (int i = 0; i < weapons.length && i < 7; i++) {
			SecurityWeapon w = weapons[i];
			container.setItem(10 + i, GuiItems.button(w.item(),
					w.displayName(),
					"Fiyat: " + bm.formatPrice(bm.getWeaponBuyPrice(w)),
					"Tikla: satin al"));
		}
		container.setItem(SLOT_BACK, GuiItems.backButton());
		container.setItem(SLOT_CLOSE, GuiItems.closeButton());
		BankGuiManager.openMenu(player, container, "§4Kaçak Silahlar", (slotId, button, p) -> {
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
			if (slotId >= 10 && slotId < 10 + weapons.length) {
				SecurityWeapon w = weapons[slotId - 10];
				if (bm.buyWeapon(sp, w)) {
					sp.sendSystemMessage(Component.literal("§a[Karaborsa] §f" + w.displayName() + " alindi."));
				} else {
					sp.sendSystemMessage(Messages.tr("command.mceconomy.dirty.insufficient"));
				}
				openWeaponsMarket(sp);
			}
		});
	}

	public static void openGoldSmeltMenu(ServerPlayer player) {
		var smelt = McEconomyMod.getEconomyManager().blackMarketGoldSmeltService();
		if (smelt == null) {
			player.sendSystemMessage(Component.literal("§c[Karaborsa] Altin eritme kullanilamiyor."));
			return;
		}
		SimpleContainer container = new SimpleContainer(27);
		BankGuiManager.fillBackgroundPublic(container);
		int have = smelt.countSmeltableIngots(player);
		int[] options = {1, 5, 10, 32};
		for (int i = 0; i < options.length; i++) {
			int ingots = options[i];
			int risk = smelt.previewRisk(player.getUUID(), ingots);
			container.setItem(10 + i, GuiItems.button(Items.GOLD_INGOT,
					"Erit: " + ingots + " kulce",
					"Envanter: " + have + " izli kulce",
					"Risk: %" + risk,
					"Komisyon: %2 → altin parcacigi"));
		}
		container.setItem(SLOT_BACK, GuiItems.backButton());
		container.setItem(SLOT_CLOSE, GuiItems.closeButton());
		BankGuiManager.openMenu(player, container, "§4Karaborsa — Altin Erit", (slotId, button, p) -> {
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
			if (slotId >= 10 && slotId < 10 + options.length) {
				int ingots = options[slotId - 10];
				BlackMarketGoldSmeltService.SmeltResult result = smelt.attempt(sp, ingots);
				switch (result.outcome()) {
					case SUCCESS -> sp.sendSystemMessage(Component.literal(
							"§a[Karaborsa] §f" + result.nuggetsOut() + " parcacik alindi (risk %" + result.riskPercent() + ")."));
					case CAUGHT -> sp.sendSystemMessage(Component.literal(
							"§c[Karaborsa] §fEritme yakalandi (risk %" + result.riskPercent() + ")."));
					case INSUFFICIENT -> sp.sendSystemMessage(Component.literal(
							"§c[Karaborsa] §fYeterli izli altin kulceniz yok."));
				}
				openGoldSmeltMenu(sp);
			}
		});
	}

	public static void openLaunderMenu(ServerPlayer player) {
		SimpleContainer container = new SimpleContainer(27);
		BankGuiManager.fillBackgroundPublic(container);

		var launder = McEconomyMod.getEconomyManager().launderingService();
		long[] options = {
				GoldStandard.gramsToMilligrams(100),
				GoldStandard.gramsToMilligrams(500),
				GoldStandard.MILLIGRAMS_PER_INGOT,
				GoldStandard.ingotsToMilligrams(5)
		};
		String[] labels = {"100 gram", "500 gram", "1 külçe", "5 külçe"};
		for (int i = 0; i < options.length; i++) {
			int risk = launder.previewRisk(player.getUUID(), options[i]);
			container.setItem(10 + i, GuiItems.button(Items.GOLD_NUGGET,
					"Akla: " + labels[i],
					"Risk: %" + risk,
					"Başarısız → ceza + dondurma"));
		}
		container.setItem(SLOT_BACK, GuiItems.backButton());
		container.setItem(SLOT_CLOSE, GuiItems.closeButton());

		BankGuiManager.openMenu(player, container, "§8Kara Para Aklama", (slotId, button, p) -> {
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
			if (slotId >= 10 && slotId < 10 + options.length) {
				LaunderingService.LaunderResult result = launder.attempt(sp, options[slotId - 10]);
				switch (result.outcome()) {
					case SUCCESS -> sp.sendSystemMessage(Messages.tr("command.mceconomy.launder.success",
							result.cleanedMg(), result.riskPercent()));
					case CAUGHT -> sp.sendSystemMessage(Messages.tr("command.mceconomy.launder.caught",
							result.fineMg(), result.riskPercent()));
					case INSUFFICIENT -> sp.sendSystemMessage(Messages.tr("command.mceconomy.dirty.insufficient"));
				}
				openLaunderMenu(sp);
			}
		});
	}
}
