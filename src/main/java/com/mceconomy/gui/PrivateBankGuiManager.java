package com.mceconomy.gui;

import com.mceconomy.McEconomyMod;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.privatebank.PrivateBank;
import com.mceconomy.util.Messages;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Items;

import java.util.List;

public final class PrivateBankGuiManager {
	public static final int SLOT_BACK = 19;
	public static final int SLOT_CLOSE = 22;

	private static final long[] DEPOSIT_OPTIONS = {
			GoldStandard.ingotsToMilligrams(1),
			GoldStandard.ingotsToMilligrams(5),
			GoldStandard.ingotsToMilligrams(10)
	};

	private PrivateBankGuiManager() {
	}

	public static void openHub(ServerPlayer player) {
		var service = McEconomyMod.getEconomyManager().privateBankService();
		boolean certified = service.hasCertificate(player.getUUID());

		SimpleContainer container = new SimpleContainer(27);
		BankGuiManager.fillBackgroundPublic(container);

		container.setItem(10, GuiItems.button(Items.WRITTEN_BOOK, "Bankacılık Sertifikası",
				certified ? "§aSertifikanız var" : "§cSertifika gerekli",
				"Ücret: " + GoldStandard.formatMilligrams(EconomyConfig.bankCertificateCostMg())));
		container.setItem(12, GuiItems.button(Items.CHEST, "Özel Banka Aç",
				"Sertifika + isim gerekli",
				"Komut: /ozelbanka ac <isim>"));
		container.setItem(14, GuiItems.button(Items.GOLD_BLOCK, "Bankalar",
				"Mevduat yatır / çek", "Tüm özel bankalar"));
		container.setItem(SLOT_CLOSE, GuiItems.closeButton());

		BankGuiManager.openMenu(player, container, "§b§lÖzel Bankacılık", (slotId, button, p) -> {
			if (!(p instanceof ServerPlayer sp)) {
				return;
			}
			switch (slotId) {
				case 10 -> handleCertificate(sp);
				case 12 -> sp.sendSystemMessage(Messages.tr("command.mceconomy.pbank.open_hint"));
				case 14 -> openBankList(sp);
				case SLOT_CLOSE -> sp.closeContainer();
				default -> {
				}
			}
		});
	}

	private static void handleCertificate(ServerPlayer player) {
		var service = McEconomyMod.getEconomyManager().privateBankService();
		if (service.hasCertificate(player.getUUID())) {
			player.sendSystemMessage(Messages.tr("command.mceconomy.pbank.already_certified"));
			return;
		}
		if (service.purchaseCertificate(player.getUUID())) {
			player.sendSystemMessage(Messages.tr("command.mceconomy.pbank.certified"));
		} else {
			player.sendSystemMessage(Messages.tr("command.mceconomy.pay.insufficient"));
		}
	}

	private static void openBankList(ServerPlayer player) {
		List<PrivateBank> banks = McEconomyMod.getEconomyManager().privateBankService().allBanks();
		SimpleContainer container = new SimpleContainer(27);
		BankGuiManager.fillBackgroundPublic(container);

		var service = McEconomyMod.getEconomyManager().privateBankService();
		for (int i = 0; i < banks.size() && i < 7; i++) {
			PrivateBank bank = banks.get(i);
			long balance = service.customerBalance(player.getUUID(), bank);
			container.setItem(10 + i, GuiItems.button(Items.EMERALD,
					bank.name(),
					"Hazine: " + GoldStandard.formatMilligrams(bank.treasuryMg()),
					"Mevduatınız: " + GoldStandard.formatMilligrams(balance),
					"Faiz: %" + (int) (bank.interestRate() * 100)));
		}
		container.setItem(SLOT_BACK, GuiItems.backButton());
		container.setItem(SLOT_CLOSE, GuiItems.closeButton());

		BankGuiManager.openMenu(player, container, "§bÖzel Bankalar", (slotId, button, p) -> {
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
			if (slotId >= 10 && slotId < 10 + Math.min(banks.size(), 7)) {
				openDepositMenu(sp, banks.get(slotId - 10));
			}
		});
	}

	private static void openDepositMenu(ServerPlayer player, PrivateBank bank) {
		SimpleContainer container = new SimpleContainer(27);
		BankGuiManager.fillBackgroundPublic(container);

		long balance = McEconomyMod.getEconomyManager().privateBankService()
				.customerBalance(player.getUUID(), bank);
		container.setItem(4, GuiItems.button(Items.BOOK,
				bank.name(),
				"Mevduat: " + GoldStandard.formatMilligrams(balance)));

		for (int i = 0; i < DEPOSIT_OPTIONS.length; i++) {
			long mg = DEPOSIT_OPTIONS[i];
			container.setItem(10 + i, GuiItems.button(Items.LIME_STAINED_GLASS_PANE,
					"Yatır " + GoldStandard.formatMilligrams(mg)));
			container.setItem(13 + i, GuiItems.button(Items.RED_STAINED_GLASS_PANE,
					"Çek " + GoldStandard.formatMilligrams(mg)));
		}
		container.setItem(SLOT_BACK, GuiItems.backButton());
		container.setItem(SLOT_CLOSE, GuiItems.closeButton());

		BankGuiManager.openMenu(player, container, "§b" + bank.name(), (slotId, button, p) -> {
			if (!(p instanceof ServerPlayer sp)) {
				return;
			}
			if (slotId == SLOT_BACK) {
				openBankList(sp);
				return;
			}
			if (slotId == SLOT_CLOSE) {
				sp.closeContainer();
				return;
			}
			if (slotId >= 10 && slotId < 10 + DEPOSIT_OPTIONS.length) {
				handleDeposit(sp, bank, DEPOSIT_OPTIONS[slotId - 10]);
				openDepositMenu(sp, bank);
			} else if (slotId >= 13 && slotId < 13 + DEPOSIT_OPTIONS.length) {
				handleWithdraw(sp, bank, DEPOSIT_OPTIONS[slotId - 13]);
				openDepositMenu(sp, bank);
			}
		});
	}

	private static void handleDeposit(ServerPlayer player, PrivateBank bank, long mg) {
		try {
			if (McEconomyMod.getEconomyManager().privateBankService()
					.deposit(player.getUUID(), bank.name(), mg)) {
				player.sendSystemMessage(Messages.tr("command.mceconomy.pbank.deposited", mg, bank.name()));
			} else {
				player.sendSystemMessage(Messages.tr("command.mceconomy.pay.insufficient"));
			}
		} catch (Exception e) {
			player.sendSystemMessage(Messages.tr("command.mceconomy.error.generic"));
		}
	}

	private static void handleWithdraw(ServerPlayer player, PrivateBank bank, long mg) {
		try {
			if (McEconomyMod.getEconomyManager().privateBankService()
					.withdraw(player.getUUID(), bank.name(), mg)) {
				player.sendSystemMessage(Messages.tr("command.mceconomy.pbank.withdrawn", mg, bank.name()));
			} else {
				player.sendSystemMessage(Messages.tr("command.mceconomy.error.generic"));
			}
		} catch (Exception e) {
			player.sendSystemMessage(Messages.tr("command.mceconomy.error.generic"));
		}
	}
}
