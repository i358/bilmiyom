package com.mceconomy.command;

import com.mceconomy.McEconomyMod;
import com.mceconomy.appeal.Appeal;
import com.mceconomy.util.Messages;
import com.mceconomy.util.Permissions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class AppealCommand {
	private AppealCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("itiraz")
				.executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayer();
					if (player == null) {
						return 0;
					}
					player.sendSystemMessage(Component.literal(
							"§e/itiraz ac <konu> <mesaj> §7— MASAK kararına itiraz"));
					player.sendSystemMessage(Component.literal(
							"§e/itiraz uyari <uyariId> <konu> <mesaj> §7— Belirli uyarıya itiraz"));
					player.sendSystemMessage(Component.literal("§e/itiraz durum §7— İtirazlarınızı görün"));
					return 1;
				})
				.then(literal("ac").then(argument("konu", StringArgumentType.string())
						.then(argument("mesaj", StringArgumentType.greedyString())
								.executes(ctx -> submit(ctx.getSource(),
										StringArgumentType.getString(ctx, "konu"),
										StringArgumentType.getString(ctx, "mesaj"),
										null)))))
				.then(literal("uyari").then(argument("uyariId", LongArgumentType.longArg(1))
						.then(argument("konu", StringArgumentType.string())
								.then(argument("mesaj", StringArgumentType.greedyString())
										.executes(ctx -> submit(ctx.getSource(),
												StringArgumentType.getString(ctx, "konu"),
												StringArgumentType.getString(ctx, "mesaj"),
												LongArgumentType.getLong(ctx, "uyariId")))))))
				.then(literal("durum").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayer();
					if (player == null) {
						return 0;
					}
					return listMine(ctx.getSource(), player);
				}))
				.then(literal("liste").executes(ctx -> {
					if (!Permissions.isMbStaff(ctx.getSource())) {
						return 0;
					}
					return listOpen(ctx.getSource());
				}))
				.then(literal("kabul").then(argument("id", LongArgumentType.longArg(1))
						.then(argument("not", StringArgumentType.greedyString())
								.executes(ctx -> {
									if (!Permissions.isMbStaff(ctx.getSource())) {
										return 0;
									}
									return accept(ctx.getSource(),
											LongArgumentType.getLong(ctx, "id"),
											StringArgumentType.getString(ctx, "not"));
								}))))
				.then(literal("red").then(argument("id", LongArgumentType.longArg(1))
						.then(argument("not", StringArgumentType.greedyString())
								.executes(ctx -> {
									if (!Permissions.isMbStaff(ctx.getSource())) {
										return 0;
									}
									return reject(ctx.getSource(),
											LongArgumentType.getLong(ctx, "id"),
											StringArgumentType.getString(ctx, "not"));
								})))));
	}

	private static int submit(CommandSourceStack source, String subject, String message, Long alertId) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		try {
			if (McEconomyMod.getEconomyManager().appealService()
					.submit(player.getUUID(), player.getName().getString(), subject, message, alertId)) {
				source.sendSuccess(() -> Messages.tr("command.mceconomy.appeal.submitted"), false);
				return 1;
			}
		} catch (Exception e) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
			return 0;
		}
		source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
		return 0;
	}

	private static int listMine(CommandSourceStack source, ServerPlayer player) {
		source.sendSuccess(() -> Component.literal("§e=== İtirazlarınız ==="), false);
		for (Appeal appeal : McEconomyMod.getEconomyManager().appealService().playerAppeals(player.getUUID())) {
			source.sendSuccess(() -> Component.literal(
					"#" + appeal.id() + " [" + appeal.status() + "] " + appeal.subject()
							+ " — " + appeal.message()), false);
		}
		return 1;
	}

	private static int listOpen(CommandSourceStack source) {
		source.sendSuccess(() -> Component.literal("§c=== Açık İtirazlar (OP) ==="), false);
		for (Appeal appeal : McEconomyMod.getEconomyManager().appealService().openAppeals()) {
			source.sendSuccess(() -> Component.literal(
					"#" + appeal.id() + " | " + appeal.playerName() + " | " + appeal.subject()
							+ " | uyarı:" + appeal.relatedAlertId()
							+ "\n  " + appeal.message()), false);
		}
		return 1;
	}

	private static int accept(CommandSourceStack source, long id, String note) {
		try {
			if (McEconomyMod.getEconomyManager().appealService().accept(id, note)) {
				source.sendSuccess(() -> Messages.tr("command.mceconomy.appeal.accepted", id), true);
				return 1;
			}
		} catch (Exception e) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
			return 0;
		}
		source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
		return 0;
	}

	private static int reject(CommandSourceStack source, long id, String note) {
		try {
			if (McEconomyMod.getEconomyManager().appealService().reject(id, note)) {
				source.sendSuccess(() -> Messages.tr("command.mceconomy.appeal.rejected", id), true);
				return 1;
			}
		} catch (Exception e) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
			return 0;
		}
		source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
		return 0;
	}
}
