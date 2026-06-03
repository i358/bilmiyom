package com.mceconomy.command;

import com.mceconomy.McEconomyMod;
import com.mceconomy.player.PlayerEconomyProfile;
import com.mceconomy.util.Messages;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class BalanceCommand {
	private BalanceCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("bakiye")
				.executes(ctx -> showSelf(ctx.getSource()))
				.then(argument("oyuncu", StringArgumentType.string())
						.executes(ctx -> showOther(ctx.getSource(), StringArgumentType.getString(ctx, "oyuncu")))));
	}

	private static int showSelf(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		long wallet = McEconomyMod.getEconomyManager().currencyService().getBalance(player.getUUID());
		long bank = McEconomyMod.getEconomyManager().bankService().getBankBalanceMg(player.getUUID());
		long dirty = McEconomyMod.getEconomyManager().currencyService().getDirtyBalance(player.getUUID());
		source.sendSuccess(() -> Messages.tr("command.mceconomy.balance.self", wallet), false);
		source.sendSuccess(() -> Messages.tr("command.mceconomy.balance.bank", bank), false);
		source.sendSuccess(() -> Messages.tr("command.mceconomy.dirty.balance", dirty), false);
		source.sendSuccess(() -> Messages.tr("command.mceconomy.balance.total", wallet + bank), false);
		source.sendSuccess(() -> Messages.tr("command.mceconomy.gui.standard"), false);
		return 1;
	}

	private static int showOther(CommandSourceStack source, String name) {
		UUID uuid = findPlayerUuid(name);
		if (uuid == null) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
			return 0;
		}
		long balance = McEconomyMod.getEconomyManager().currencyService().getBalance(uuid);
		source.sendSuccess(() -> Messages.tr("command.mceconomy.balance.other", name, balance), false);
		return 1;
	}

	public static UUID findPlayerUuid(String name) {
		for (PlayerEconomyProfile profile : McEconomyMod.getEconomyManager().profiles().values()) {
			if (profile.name().equalsIgnoreCase(name)) {
				return profile.uuid();
			}
		}
		return null;
	}
}
