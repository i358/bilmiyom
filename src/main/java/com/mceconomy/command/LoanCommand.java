package com.mceconomy.command;

import com.mceconomy.McEconomyMod;
import com.mceconomy.player.PlayerEconomyProfile;
import com.mceconomy.util.Messages;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class LoanCommand {
	private LoanCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("kredi")
				.then(literal("al").then(argument("miktar", IntegerArgumentType.integer(1))
						.executes(ctx -> takeLoan(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "miktar")))))
				.then(literal("ode").executes(ctx -> payLoan(ctx.getSource())))
				.then(literal("durum").executes(ctx -> loanStatus(ctx.getSource()))));
	}

	private static int takeLoan(CommandSourceStack source, int amount) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		PlayerEconomyProfile profile = McEconomyMod.getEconomyManager().profiles().get(player.getUUID());
		try {
			if (McEconomyMod.getEconomyManager().loanManager().takeLoan(profile, amount,
					McEconomyMod.getEconomyManager().centralBank())) {
				var loan = McEconomyMod.getEconomyManager().loanManager().getLoan(player.getUUID()).orElseThrow();
				source.sendSuccess(() -> Messages.tr("command.mceconomy.loan.taken", amount, loan.installment()), false);
				return 1;
			}
			source.sendFailure(Messages.tr("command.mceconomy.loan.denied"));
		} catch (Exception e) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
		}
		return 0;
	}

	private static int payLoan(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		PlayerEconomyProfile profile = McEconomyMod.getEconomyManager().profiles().get(player.getUUID());
		try {
			if (McEconomyMod.getEconomyManager().loanManager().payInstallment(profile)) {
				long remaining = McEconomyMod.getEconomyManager().loanManager().getLoan(player.getUUID())
						.map(l -> l.remaining()).orElse(0L);
				source.sendSuccess(() -> Messages.tr("command.mceconomy.loan.paid", "taksit", remaining), false);
				return 1;
			}
			source.sendFailure(Messages.tr("command.mceconomy.loan.no_loan"));
		} catch (Exception e) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
		}
		return 0;
	}

	private static int loanStatus(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		var loanOpt = McEconomyMod.getEconomyManager().loanManager().getLoan(player.getUUID());
		if (loanOpt.isEmpty()) {
			source.sendSuccess(() -> Messages.tr("command.mceconomy.loan.no_loan"), false);
			return 1;
		}
		var loan = loanOpt.get();
		source.sendSuccess(() -> Component.literal(
				"Kalan borç: " + loan.remaining() + " | Taksit: " + loan.installment()
						+ " | Skor: " + McEconomyMod.getEconomyManager().profiles().get(player.getUUID()).creditScore().score()), false);
		return 1;
	}
}
