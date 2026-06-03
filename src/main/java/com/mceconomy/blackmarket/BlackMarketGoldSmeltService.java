package com.mceconomy.blackmarket;

import com.mceconomy.McEconomyMod;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.facility.FacilityItemTags;
import com.mceconomy.news.EconomyBulletinService;
import com.mceconomy.regulation.MasakService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Karaborsada seri numarali altin eritme — parcacik cikti, makro sok (basarili), yakalaninca sok yok. */
public final class BlackMarketGoldSmeltService {
	public enum SmeltOutcome {
		SUCCESS,
		CAUGHT,
		INSUFFICIENT
	}

	public record SmeltResult(SmeltOutcome outcome, int ingots, int riskPercent, int nuggetsOut, long valueMg) {
	}

	private final MasakService masakService;
	private EconomyBulletinService bulletinService;

	public BlackMarketGoldSmeltService(MasakService masakService) {
		this.masakService = masakService;
	}

	public void bindBulletin(EconomyBulletinService bulletinService) {
		this.bulletinService = bulletinService;
	}

	public int countSmeltableIngots(ServerPlayer player) {
		int total = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(Items.GOLD_INGOT) && FacilityItemTags.isBankTrackedGold(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	public int previewRisk(UUID player, int ingots) {
		if (ingots <= 0) {
			return 0;
		}
		return calculateRisk(player, GoldStandard.ingotsToMilligrams(ingots));
	}

	public SmeltResult attempt(ServerPlayer player, int ingots) {
		if (ingots <= 0 || countSmeltableIngots(player) < ingots) {
			return new SmeltResult(SmeltOutcome.INSUFFICIENT, ingots, 0, 0, 0);
		}
		long valueMg = GoldStandard.ingotsToMilligrams(ingots);
		int riskPercent = calculateRisk(player.getUUID(), valueMg);
		boolean caught = ThreadLocalRandom.current().nextDouble() < riskPercent / 100.0;

		if (caught) {
			removeSmeltableIngots(player, ingots);
			masakService.onGoldSmeltCaught(player.getUUID(), valueMg);
			tryPunishCaught(player, ingots);
			player.sendSystemMessage(Component.literal(
					"§4[MASAK] §cAltin eritme operasyonunuz yakalandi! Altin el kondu."));
			return new SmeltResult(SmeltOutcome.CAUGHT, ingots, riskPercent, 0, valueMg);
		}

		int grossNuggets = ingots * 9;
		int nuggetsOut = (int) Math.floor(grossNuggets * (1.0 - EconomyConfig.blackMarketSmeltCommission()));
		if (nuggetsOut <= 0 || !removeSmeltableIngots(player, ingots)) {
			return new SmeltResult(SmeltOutcome.INSUFFICIENT, ingots, riskPercent, 0, valueMg);
		}
		giveGoldParticles(player, nuggetsOut);
		masakService.onGoldSmeltSuccess(player.getUUID(), valueMg);
		applyMacroShock(valueMg);
		player.sendSystemMessage(Component.literal(
				"§a[Karaborsa] §f" + ingots + " kulce eritildi → §e" + nuggetsOut
						+ " altin parcacigi §7(%" + (int) (EconomyConfig.blackMarketSmeltCommission() * 100)
						+ " komisyon). Evde 9 parcacik = 1 kulce."));
		return new SmeltResult(SmeltOutcome.SUCCESS, ingots, riskPercent, nuggetsOut, valueMg);
	}

	private void applyMacroShock(long valueMg) {
		var manager = McEconomyMod.getEconomyManager();
		if (manager == null || manager.server() == null || bulletinService == null) {
			return;
		}
		manager.activateGoldLaunderPressure();
		bulletinService.applyLaunderingShock(manager.centralBank(), manager.marketService().priceEngine(), valueMg);
		try {
			manager.centralBank().save();
		} catch (Exception e) {
			McEconomyMod.LOGGER.error("Eritme sonrasi MB kaydi", e);
		}
		bulletinService.publishStorageNotice(manager.server(),
				"KARABORSA ALTIN AKLAMA TESPIT EDILDI",
				"Izlenen altin eritilerek parcaciga donusturuldu. MB faiz ve enflasyon baskisi artirdi.");
	}

	private void tryPunishCaught(ServerPlayer player, int ingots) {
		var manager = McEconomyMod.getEconomyManager();
		if (manager == null) {
			return;
		}
		if (manager.bankRobberyJusticeService() != null) {
			manager.bankRobberyJusticeService().investigateTarget(player.getUUID());
		}
		if (manager.prisonService() != null) {
			try {
				manager.prisonService().imprison(player, EconomyConfig.goldSmeltCaughtPrisonMinutes(),
						"Karaborsa altin eritme — MASAK operasyonu", "MASAK");
			} catch (Exception e) {
				McEconomyMod.LOGGER.error("Eritme cezasi", e);
			}
		}
	}

	private int calculateRisk(UUID player, long valueMg) {
		double risk = EconomyConfig.goldSmeltBaseRisk();
		long grams = valueMg / GoldStandard.MILLIGRAMS_PER_GRAM;
		risk += (grams / 100.0) * EconomyConfig.goldSmeltRiskPer100Grams();
		return (int) Math.min(92, Math.round(risk * 100));
	}

	private boolean removeSmeltableIngots(ServerPlayer player, int ingots) {
		int remaining = ingots;
		for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (!stack.is(Items.GOLD_INGOT) || !FacilityItemTags.isBankTrackedGold(stack)) {
				continue;
			}
			int take = Math.min(stack.getCount(), remaining);
			stack.shrink(take);
			remaining -= take;
		}
		return remaining == 0;
	}

	private void giveGoldParticles(ServerPlayer player, int nuggets) {
		int remaining = nuggets;
		while (remaining > 0) {
			int size = Math.min(remaining, Items.GOLD_NUGGET.getDefaultMaxStackSize());
			ItemStack stack = new ItemStack(Items.GOLD_NUGGET, size);
			FacilityItemTags.markGoldParticle(stack);
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
			remaining -= size;
		}
	}
}
