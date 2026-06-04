package com.mceconomy.command;

import com.mceconomy.McEconomyMod;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.event.EconomyEventType;
import com.mceconomy.util.Messages;
import com.mceconomy.util.Permissions;
import com.mceconomy.world.CentralBankPlacer;
import com.mceconomy.world.ModWorldResetService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class AdminCommand {
	private static final SuggestionProvider<CommandSourceStack> EVENT_SUGGESTIONS = (ctx, builder) -> {
		for (EconomyEventType type : EconomyEventType.values()) {
			builder.suggest(type.id());
		}
		return builder.buildFuture();
	};

	private AdminCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("merkezbanka")
				.then(literal("rapor").executes(ctx -> {
					if (!Permissions.isMbStaff(ctx.getSource())) {
						return 0;
					}
					return report(ctx.getSource());
				}))
				.then(literal("kur").executes(ctx -> {
					if (!Permissions.isServerOp(ctx.getSource())) {
						return 0;
					}
					var player = ctx.getSource().getPlayer();
					CentralBankPlacer.rebuild(ctx.getSource().getServer(), player);
					var manager = McEconomyMod.getEconomyManager();
					if (manager != null && manager.goldReserveService() != null) {
						manager.goldReserveService().refresh(ctx.getSource().getServer());
					}
					ctx.getSource().sendSuccess(() -> Component.literal(player != null
							? "§aMerkez Bankası konumunuzun 4 blok önünde, aynı yükseklikte kuruldu."
							: "§aMerkez Bankası yeniden kuruldu (spawn/config)."), true);
					return 1;
				}))
				.then(literal("rezerv-doldur").executes(ctx -> {
					if (!Permissions.isServerOp(ctx.getSource())) {
						return 0;
					}
					var server = ctx.getSource().getServer();
					int placed = CentralBankPlacer.refillGoldReserveVault(server.overworld());
					var manager = McEconomyMod.getEconomyManager();
					int total = 0;
					if (manager != null && manager.goldReserveService() != null) {
						total = manager.goldReserveService().countGoldBlocks(server.overworld());
						try {
							if (manager.depotLedgerService() != null) {
								manager.depotLedgerService().setExpectedGoldReserveBlocks(total);
							}
						} catch (java.sql.SQLException e) {
							McEconomyMod.LOGGER.error("Rezerv defteri guncellenemedi", e);
						}
					}
					int finalTotal = total;
					ctx.getSource().sendSuccess(() -> Component.literal(
							"§aAltin rezerv: §f" + placed + " blok yerlestirildi. Toplam: §6" + finalTotal), true);
					return 1;
				}))
				.then(literal("muhafiz-temizle").executes(ctx -> {
					if (!Permissions.isServerOp(ctx.getSource())) {
						return 0;
					}
					var sec = McEconomyMod.getEconomyManager().bankSecurityService();
					if (sec == null) {
						ctx.getSource().sendFailure(Component.literal("§cGuvenlik servisi hazir degil."));
						return 0;
					}
					int removed = sec.purgeExcessGuards();
					ctx.getSource().sendSuccess(() -> Component.literal(
							"§a[Merkez Bankasi] §f" + removed + " fazla muhafiz/soygun NPC'si silindi. "
									+ "Aktif devriye: " + EconomyConfig.bankGuardCount() + " (config)."), true);
					return 1;
				})));

		EkonomiCommand.registerBakan(dispatcher);

		dispatcher.register(literal("ekonomi")
				.then(literal("sifirla")
						.executes(ctx -> {
							if (!Permissions.isServerOp(ctx.getSource())) {
								return 0;
							}
							return resetModWorld(ctx.getSource());
						}))
				.then(literal("olay").then(argument("tip", StringArgumentType.string())
						.suggests(EVENT_SUGGESTIONS)
						.executes(ctx -> {
							if (!Permissions.isServerOp(ctx.getSource())) {
								return 0;
							}
							return triggerEvent(ctx.getSource(),
									StringArgumentType.getString(ctx, "tip"), 5 * 60 * 1000);
						})
						.then(argument("sure_saniye", IntegerArgumentType.integer(10, 3600))
								.executes(ctx -> {
									if (!Permissions.isServerOp(ctx.getSource())) {
										return 0;
									}
									return triggerEvent(ctx.getSource(),
											StringArgumentType.getString(ctx, "tip"),
											IntegerArgumentType.getInteger(ctx, "sure_saniye") * 1000L);
								})))));
	}

	private static int resetModWorld(CommandSourceStack source) {
		try {
			var report = ModWorldResetService.resetModStructures(source.getServer(), source.getPlayer());
			source.sendSuccess(() -> Component.literal(
					"§a[MC Economy] Tam sifirlama: veritabani "
							+ (report.databaseWiped() ? "temizlendi" : "HATA")
							+ ", MB yenilendi, "
							+ report.personalVaultsCleared() + " kisisel kasa, "
							+ report.companyVaultsCleared() + " sirket sandigi, "
							+ report.prisonCellsCleared() + " hucre, "
							+ report.entitiesRemoved() + " NPC silindi, "
							+ report.prisonersReleased() + " mahkum tahliye. "
							+ "Bakiye/borc/sirket verileri sifirlandi. Para birimi: "
							+ com.mceconomy.economy.GoldStandard.CURRENCY_NAME), true);
			return 1;
		} catch (Exception e) {
			McEconomyMod.LOGGER.error("Mod dunya sifirlama", e);
			source.sendFailure(Component.literal("§cSifirlama basarisiz: " + e.getMessage()));
			return 0;
		}
	}

	private static int report(CommandSourceStack source) {
		var cb = McEconomyMod.getEconomyManager().centralBank();
		var market = McEconomyMod.getEconomyManager().marketService();
		source.sendSuccess(() -> Messages.tr("command.mceconomy.centralbank.report"), false);
		source.sendSuccess(() -> Component.literal(
				"Para arzi (" + com.mceconomy.economy.GoldStandard.CURRENCY_NAME + "): " + cb.getMoneySupply()
						+ " | Endeks: " + String.format("%.2f", cb.getEconomyIndex())
						+ " | Enflasyon: " + String.format("%.2f%%", cb.getInflationRate() * 100)
						+ " | Faiz: " + String.format("%.2f%%", cb.getBaseRate() * 100)
						+ " | Market Endeksi: " + String.format("%.2f", market.economyIndex().calculate())), false);
		return 1;
	}

	private static int triggerEvent(CommandSourceStack source, String typeId, long durationMs) {
		EconomyEventType type = EconomyEventType.fromId(typeId);
		if (type == null) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
			return 0;
		}
		var manager = McEconomyMod.getEconomyManager();
		if (manager.eventManager().triggerEvent(type, durationMs,
				manager.marketService().priceEngine(), manager.centralBank(), source.getServer())) {
			source.sendSuccess(() -> Messages.tr("command.mceconomy.event.triggered", type.id()), true);
			return 1;
		}
		source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
		return 0;
	}
}
