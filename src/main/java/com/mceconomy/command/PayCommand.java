package com.mceconomy.command;

import com.mceconomy.McEconomyMod;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.util.Messages;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class PayCommand {
	private PayCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("ode")
				.then(argument("oyuncu", StringArgumentType.string())
						.then(argument("miktar", IntegerArgumentType.integer(1))
								.executes(ctx -> {
									ServerPlayer player = ctx.getSource().getPlayer();
									if (player == null) {
										return 0;
									}
									String targetName = StringArgumentType.getString(ctx, "oyuncu");
									int amount = IntegerArgumentType.getInteger(ctx, "miktar");
									return pay(player, targetName, amount, ctx.getSource());
								}))));
	}

	private static int pay(ServerPlayer player, String targetName, int amountGrams, CommandSourceStack source) {
		var fromProfile = McEconomyMod.getEconomyManager().profiles().get(player.getUUID());
		if (fromProfile != null && !fromProfile.canUseLegalEconomy()) {
			source.sendFailure(Messages.tr("command.mceconomy.masak.restricted"));
			return 0;
		}
		UUID target = BalanceCommand.findPlayerUuid(targetName);
		if (target == null) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
			return 0;
		}
		long milligrams = GoldStandard.gramsToMilligrams(amountGrams);
		if (!McEconomyMod.getEconomyManager().currencyService().transfer(player.getUUID(), target, milligrams)) {
			source.sendFailure(Messages.tr("command.mceconomy.pay.insufficient"));
			return 0;
		}
		source.sendSuccess(() -> Messages.tr("command.mceconomy.pay.success", targetName, milligrams), false);
		ServerPlayer targetPlayer = source.getServer().getPlayerList().getPlayer(target);
		if (targetPlayer != null) {
			targetPlayer.sendSystemMessage(Messages.tr("command.mceconomy.pay.received", player.getName().getString(), milligrams));
		}
		return 1;
	}
}
