package com.mceconomy.command;

import com.mceconomy.McEconomyMod;
import com.mceconomy.player.PlayerEconomyProfile;
import com.mceconomy.util.Messages;
import com.mceconomy.util.Permissions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class MbOpCommand {
	private MbOpCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("mbop")
				.then(literal("ver").then(argument("oyuncu", StringArgumentType.string())
						.executes(ctx -> {
							if (!Permissions.isServerOp(ctx.getSource())) {
								ctx.getSource().sendFailure(Messages.tr("command.mceconomy.mbop.op_only"));
								return 0;
							}
							return grant(ctx.getSource(), StringArgumentType.getString(ctx, "oyuncu"));
						})))
				.then(literal("al").then(argument("oyuncu", StringArgumentType.string())
						.executes(ctx -> {
							if (!Permissions.isServerOp(ctx.getSource())) {
								ctx.getSource().sendFailure(Messages.tr("command.mceconomy.mbop.op_only"));
								return 0;
							}
							return revoke(ctx.getSource(), StringArgumentType.getString(ctx, "oyuncu"));
						})))
				.then(literal("liste").executes(ctx -> {
					if (!Permissions.isServerOp(ctx.getSource())) {
						ctx.getSource().sendFailure(Messages.tr("command.mceconomy.mbop.op_only"));
						return 0;
					}
					return list(ctx.getSource());
				}))
				.executes(ctx -> {
					if (!Permissions.isServerOp(ctx.getSource())) {
						ctx.getSource().sendFailure(Messages.tr("command.mceconomy.mbop.op_only"));
						return 0;
					}
					ctx.getSource().sendSuccess(() -> Component.literal(
							"§6/mbop ver <oyuncu> §7| §6/mbop al <oyuncu> §7| §6/mbop liste"), false);
					return 1;
				}));
	}

	private static int grant(CommandSourceStack source, String name) {
		UUID uuid = BalanceCommand.findPlayerUuid(name);
		if (uuid == null) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
			return 0;
		}
		PlayerEconomyProfile profile = McEconomyMod.getEconomyManager().profiles().get(uuid);
		if (profile == null) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
			return 0;
		}
		profile.setCentralBankOfficial(true);
		try {
			McEconomyMod.getEconomyManager().playerRepository().save(profile);
		} catch (Exception e) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
			return 0;
		}
		source.sendSuccess(() -> Messages.tr("command.mceconomy.mbop.granted", name), true);
		return 1;
	}

	private static int revoke(CommandSourceStack source, String name) {
		UUID uuid = BalanceCommand.findPlayerUuid(name);
		if (uuid == null) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
			return 0;
		}
		PlayerEconomyProfile profile = McEconomyMod.getEconomyManager().profiles().get(uuid);
		if (profile == null) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
			return 0;
		}
		profile.setCentralBankOfficial(false);
		try {
			McEconomyMod.getEconomyManager().playerRepository().save(profile);
		} catch (Exception e) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
			return 0;
		}
		source.sendSuccess(() -> Messages.tr("command.mceconomy.mbop.revoked", name), true);
		return 1;
	}

	private static int list(CommandSourceStack source) {
		source.sendSuccess(() -> Component.literal("§6=== Merkez Bankası Yetkilileri ==="), false);
		for (PlayerEconomyProfile profile : McEconomyMod.getEconomyManager().profiles().values()) {
			if (profile.centralBankOfficial()) {
				source.sendSuccess(() -> Component.literal("§e- " + profile.name()), false);
			}
		}
		return 1;
	}
}
