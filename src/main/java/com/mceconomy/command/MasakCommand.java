package com.mceconomy.command;

import com.mceconomy.McEconomyMod;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.gui.IllegalGuiManager;
import com.mceconomy.regulation.MasakAlert;
import com.mceconomy.util.Messages;
import com.mceconomy.util.Permissions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class MasakCommand {
	private MasakCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("masak")
				.then(literal("liste").executes(ctx -> {
					if (!Permissions.isMbStaff(ctx.getSource())) {
						return 0;
					}
					return listAlerts(ctx.getSource());
				}))
				.then(literal("coz").then(argument("oyuncu", StringArgumentType.string())
						.executes(ctx -> {
							if (!Permissions.isMbStaff(ctx.getSource())) {
								return 0;
							}
							return resolvePlayer(ctx.getSource(), StringArgumentType.getString(ctx, "oyuncu"));
						})))
				.then(literal("ceza").then(argument("oyuncu", StringArgumentType.string())
						.then(argument("gram", IntegerArgumentType.integer(1))
								.executes(ctx -> {
									if (!Permissions.isMbStaff(ctx.getSource())) {
										return 0;
									}
									return fine(ctx.getSource(),
											StringArgumentType.getString(ctx, "oyuncu"),
											IntegerArgumentType.getInteger(ctx, "gram"));
								}))))
				.then(literal("karaliste").then(argument("oyuncu", StringArgumentType.string())
						.executes(ctx -> {
							if (!Permissions.isMbStaff(ctx.getSource())) {
								return 0;
							}
							return blacklist(ctx.getSource(), StringArgumentType.getString(ctx, "oyuncu"));
						})))
				.executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayer();
					if (player == null) {
						return 0;
					}
					long dirty = McEconomyMod.getEconomyManager().currencyService().getDirtyBalance(player.getUUID());
					player.sendSystemMessage(Messages.tr("command.mceconomy.dirty.balance", dirty));
					var profile = McEconomyMod.getEconomyManager().profiles().get(player.getUUID());
					if (profile != null && profile.accountFrozen()) {
						player.sendSystemMessage(Messages.tr("command.mceconomy.masak.frozen"));
					}
					return 1;
				}));

		dispatcher.register(literal("karaborsa").executes(ctx -> {
			ServerPlayer player = ctx.getSource().getPlayer();
			if (player == null) {
				return 0;
			}
			IllegalGuiManager.openHub(player);
			return 1;
		}));
	}

	private static int listAlerts(CommandSourceStack source) {
		source.sendSuccess(() -> Component.literal("§c=== MASAK Açık Uyarılar ==="), false);
		for (MasakAlert alert : McEconomyMod.getEconomyManager().masakService().openAlerts()) {
			source.sendSuccess(() -> Component.literal(
					"#" + alert.id() + " | " + alert.playerUuid() + " | " + alert.reason()
							+ " | risk:" + alert.riskScore() + " | "
							+ GoldStandard.formatMilligrams(alert.amount())), false);
		}
		return 1;
	}

	private static int resolvePlayer(CommandSourceStack source, String name) {
		UUID uuid = BalanceCommand.findPlayerUuid(name);
		if (uuid == null) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
			return 0;
		}
		for (MasakAlert alert : McEconomyMod.getEconomyManager().masakService().openAlerts()) {
			if (alert.playerUuid().equals(uuid)) {
				McEconomyMod.getEconomyManager().masakService().resolveAlert(alert.id(), uuid);
			}
		}
		source.sendSuccess(() -> Component.literal(name + " hesabı çözüldü."), true);
		return 1;
	}

	private static int fine(CommandSourceStack source, String name, int grams) {
		UUID uuid = BalanceCommand.findPlayerUuid(name);
		if (uuid == null) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
			return 0;
		}
		long mg = GoldStandard.gramsToMilligrams(grams);
		McEconomyMod.getEconomyManager().masakService().applyFine(uuid, mg);
		source.sendSuccess(() -> Component.literal(name + " → " + GoldStandard.formatMilligrams(mg) + " ceza"), true);
		return 1;
	}

	private static int blacklist(CommandSourceStack source, String name) {
		UUID uuid = BalanceCommand.findPlayerUuid(name);
		if (uuid == null) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
			return 0;
		}
		McEconomyMod.getEconomyManager().masakService().blacklist(uuid);
		source.sendSuccess(() -> Component.literal(name + " kara listeye alındı."), true);
		return 1;
	}
}
