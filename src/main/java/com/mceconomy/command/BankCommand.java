package com.mceconomy.command;

import com.mceconomy.McEconomyMod;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.gui.BankGuiManager;
import com.mceconomy.util.Messages;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class BankCommand {
	private BankCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("banka")
				.executes(ctx -> openGui(ctx.getSource()))
				.then(literal("ac").executes(ctx -> createAccount(ctx.getSource())))
				.then(literal("vadeli").executes(ctx -> createTerm(ctx.getSource())))
				.then(literal("yatir").then(argument("miktar", IntegerArgumentType.integer(1))
						.executes(ctx -> depositIngots(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "miktar")))))
				.then(literal("cek").then(argument("miktar", IntegerArgumentType.integer(1))
						.executes(ctx -> withdrawIngots(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "miktar")))))
				.then(literal("transfer").then(argument("oyuncu", StringArgumentType.string())
						.then(argument("miktar", IntegerArgumentType.integer(1))
								.executes(ctx -> transfer(ctx.getSource(),
										StringArgumentType.getString(ctx, "oyuncu"),
										IntegerArgumentType.getInteger(ctx, "miktar")))))));
	}

	private static int openGui(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		BankGuiManager.openMainMenu(player);
		return 1;
	}

	private static int createAccount(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		try {
			if (McEconomyMod.getEconomyManager().bankService().createCheckingAccount(player.getUUID())) {
				source.sendSuccess(() -> Messages.tr("command.mceconomy.bank.created"), false);
				return 1;
			}
			source.sendFailure(Messages.tr("command.mceconomy.bank.already_exists"));
		} catch (Exception e) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
		}
		return 0;
	}

	private static int createTerm(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		try {
			double rate = McEconomyMod.getEconomyManager().centralBank().getBaseRate();
			if (McEconomyMod.getEconomyManager().bankService().createTermAccount(player.getUUID(), rate)) {
				source.sendSuccess(() -> Messages.tr("command.mceconomy.bank.term_created", (int) (rate * 100)), false);
				return 1;
			}
			source.sendFailure(Messages.tr("command.mceconomy.bank.already_exists"));
		} catch (Exception e) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
		}
		return 0;
	}

	private static int depositIngots(CommandSourceStack source, int ingots) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		if (McEconomyMod.getEconomyManager().bankService().getChecking(player.getUUID()).isEmpty()) {
			source.sendFailure(Messages.tr("command.mceconomy.bank.no_account"));
			return 0;
		}
		long mg = GoldStandard.ingotsToMilligrams(ingots);
		if (com.mceconomy.economy.PhysicalGoldService.hasWantedGoldIngots(player)
				&& com.mceconomy.economy.PhysicalGoldService.countDepositEligibleGoldIngots(player) < ingots) {
			source.sendFailure(Messages.tr("command.mceconomy.bank.wanted_gold_deposit"));
			return 0;
		}
		if (McEconomyMod.getEconomyManager().bankService().depositPhysicalGold(player.getUUID(), player, ingots)) {
			source.sendSuccess(() -> Messages.tr("command.mceconomy.bank.physical_deposit", ingots, mg), false);
			return 1;
		}
		source.sendFailure(Messages.tr("command.mceconomy.bank.no_gold"));
		return 0;
	}

	private static int withdrawIngots(CommandSourceStack source, int ingots) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		if (McEconomyMod.getEconomyManager().bankService().getChecking(player.getUUID()).isEmpty()) {
			source.sendFailure(Messages.tr("command.mceconomy.bank.no_account"));
			return 0;
		}
		long mg = GoldStandard.ingotsToMilligrams(ingots);
		if (McEconomyMod.getEconomyManager().bankService().withdrawPhysicalGold(player.getUUID(), player, ingots)) {
			source.sendSuccess(() -> Messages.tr("command.mceconomy.bank.physical_withdraw", ingots, mg), false);
			return 1;
		}
		if (McEconomyMod.getEconomyManager().bankService().getBankBalanceMg(player.getUUID()) < mg) {
			source.sendFailure(Messages.tr("command.mceconomy.pay.insufficient"));
		} else {
			source.sendFailure(Messages.tr("command.mceconomy.bank.inventory_full"));
		}
		return 0;
	}

	private static int transfer(CommandSourceStack source, String targetName, int amountGrams) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		UUID target = BalanceCommand.findPlayerUuid(targetName);
		if (target == null) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
			return 0;
		}
		long milligrams = GoldStandard.gramsToMilligrams(amountGrams);
		if (McEconomyMod.getEconomyManager().bankService().getChecking(player.getUUID()).isEmpty()) {
			source.sendFailure(Messages.tr("command.mceconomy.bank.no_account"));
			return 0;
		}
		if (McEconomyMod.getEconomyManager().bankService().transferFromBank(player.getUUID(), target, milligrams)) {
			source.sendSuccess(() -> Messages.tr("command.mceconomy.bank.transfer", targetName, milligrams), false);
			return 1;
		}
		source.sendFailure(Messages.tr("command.mceconomy.pay.insufficient"));
		return 0;
	}
}
