package com.mceconomy.command;

import com.mceconomy.McEconomyMod;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.tax.CentralBank;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.literal;

/** Fiat (itibari) para durumu — MC degeri altin + devlet + yatirim. */
public final class FiatCommand {
	private FiatCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("para")
				.then(literal("durum").executes(ctx -> show(ctx.getSource()))));
	}

	private static int show(CommandSourceStack source) {
		var manager = McEconomyMod.getEconomyManager();
		if (manager == null || !manager.isLoaded()) {
			source.sendFailure(Component.literal("§cEkonomi henuz yuklenmedi."));
			return 0;
		}
		CentralBank cb = manager.centralBank();
		long supply = cb.getMoneySupply();
		final double coveragePct = manager.goldReserveService() != null && supply > 0
				? manager.goldReserveService().coverageRatio(supply) * 100
				: 0;
		source.sendSuccess(() -> Component.literal("§6§l═══ FIAT PARA ($) ═══"), false);
		source.sendSuccess(() -> Component.literal(String.format(
				"§eFiat gucu: §f%.2f §7(1.0 = notr, yuksek = $ guclu)", cb.getFiatStrength())), false);
		source.sendSuccess(() -> Component.literal(String.format(
				"§7Altin destegi: §f%d%% §7| Devlet guveni: §f%d%% §7| Yatirim: §f%d%%",
				pct(cb.getGoldBackingScore()), pct(cb.getStateCredibilityScore()),
				pct(cb.getInvestmentScore()))), false);
		source.sendSuccess(() -> Component.literal(String.format(
				"§7Fiziksel rezerv kapsama: §f%.1f%% §7| Altin kulce: §f%s",
				coveragePct, GoldStandard.CURRENCY_NAME + String.format("%,.0f", GoldStandard.ingotPriceMc()))), false);
		if (cb.getFiatShockPenalty() > 0.05) {
			source.sendSuccess(() -> Component.literal(String.format(
					"§cMakro sok cezasi aktif: §f%d%% §7(soygun/yikama sonrasi)", pct(cb.getFiatShockPenalty()))), false);
		}
		source.sendSuccess(() -> Component.literal(
				"§8MC sadece altinla degil; belediye butcesi, istikrar ve borsa/sirket yatirimi ile desteklenir."), false);
		return 1;
	}

	private static int pct(double score) {
		return (int) Math.round(Math.max(0, Math.min(1, score)) * 100);
	}
}
