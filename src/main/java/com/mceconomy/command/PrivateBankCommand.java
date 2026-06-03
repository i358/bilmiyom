package com.mceconomy.command;

import com.mceconomy.McEconomyMod;
import com.mceconomy.gui.PrivateBankGuiManager;
import com.mceconomy.util.Messages;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class PrivateBankCommand {
	private PrivateBankCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("ozelbanka")
				.executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayer();
					if (player == null) {
						return 0;
					}
					PrivateBankGuiManager.openHub(player);
					return 1;
				})
				.then(literal("ac").then(argument("isim", StringArgumentType.string())
						.executes(ctx -> openBank(ctx.getSource(), StringArgumentType.getString(ctx, "isim")))))
				.then(literal("sertifika").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayer();
					if (player == null) {
						return 0;
					}
					var service = McEconomyMod.getEconomyManager().privateBankService();
					if (service.hasCertificate(player.getUUID())) {
						player.sendSystemMessage(Messages.tr("command.mceconomy.pbank.already_certified"));
						return 1;
					}
					if (service.purchaseCertificate(player.getUUID())) {
						player.sendSystemMessage(Messages.tr("command.mceconomy.pbank.certified"));
						return 1;
					}
					player.sendSystemMessage(Messages.tr("command.mceconomy.pay.insufficient"));
					return 0;
				})));
	}

	private static int openBank(CommandSourceStack source, String name) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		if (!McEconomyMod.getEconomyManager().privateBankService().hasCertificate(player.getUUID())) {
			source.sendFailure(Messages.tr("command.mceconomy.pbank.need_cert"));
			return 0;
		}
		try {
			if (McEconomyMod.getEconomyManager().privateBankService().openBank(player.getUUID(), name)) {
				source.sendSuccess(() -> Messages.tr("command.mceconomy.pbank.opened", name), false);
				return 1;
			}
		} catch (Exception e) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
			return 0;
		}
		source.sendFailure(Messages.tr("command.mceconomy.pbank.open_failed"));
		return 0;
	}
}
