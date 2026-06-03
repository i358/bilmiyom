package com.mceconomy.command;

import com.mceconomy.McEconomyMod;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.literal;

public final class VaultCommand {
	private VaultCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("kasa")
				.executes(ctx -> teleport(ctx.getSource()))
				.then(literal("git").executes(ctx -> teleport(ctx.getSource())))
				.then(literal("cik").executes(ctx -> back(ctx.getSource())))
				.then(literal("bilgi").executes(ctx -> info(ctx.getSource()))));
	}

	private static int teleport(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		if (McEconomyMod.getEconomyManager().vaultService().teleportToVault(player)) {
			source.sendSuccess(() -> Component.literal(
					"§6[Kasa] §fOzel kasaniza isinlandiniz. Geri donmek icin §e/kasa cik"), false);
			return 1;
		}
		source.sendFailure(Component.literal("§cKasaya isinlanilamadi."));
		return 0;
	}

	private static int back(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		if (McEconomyMod.getEconomyManager().vaultService().teleportBack(player)) {
			source.sendSuccess(() -> Component.literal("§6[Kasa] §fOnceki konumunuza dondunuz."), false);
			return 1;
		}
		source.sendFailure(Component.literal("§cDonus konumu bulunamadi. Once /kasa ile kasaya gidin."));
		return 0;
	}

	private static int info(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		boolean has = McEconomyMod.getEconomyManager().vaultService().hasVault(player.getUUID());
		source.sendSuccess(() -> Component.literal("§6=== Kisisel Kasa ==="), false);
		source.sendSuccess(() -> Component.literal(has
				? "§fKasaniz mevcut. §e/kasa §7ile gidin, §e/kasa cik §7ile donun."
				: "§fHenuz kasaniz yok. §e/kasa §7yazinca olusturulur."), false);
		source.sendSuccess(() -> Component.literal(
				"§7Kasaniz yer altinda, bedrock ile cevrili ve sadece size aciktir."), false);
		return 1;
	}
}
