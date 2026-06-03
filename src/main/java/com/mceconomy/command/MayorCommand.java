package com.mceconomy.command;

import com.mceconomy.McEconomyMod;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.municipal.MayorService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class MayorCommand {
	private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
			.withZone(ZoneId.systemDefault());

	private MayorCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("belediye")
				.executes(ctx -> status(ctx.getSource()))
				.then(literal("durum").executes(ctx -> status(ctx.getSource())))
				.then(literal("adayol").executes(ctx -> candidacy(ctx.getSource())))
				.then(literal("oy").then(argument("aday", StringArgumentType.word())
						.executes(ctx -> vote(ctx.getSource(), StringArgumentType.getString(ctx, "aday")))))
				.then(literal("harca")
						.then(argument("miktar", IntegerArgumentType.integer(1))
								.then(argument("aciklama", StringArgumentType.greedyString())
										.executes(ctx -> spend(ctx.getSource(),
												IntegerArgumentType.getInteger(ctx, "miktar"),
												StringArgumentType.getString(ctx, "aciklama")))))));
	}

	private static int status(CommandSourceStack source) {
		var mayor = McEconomyMod.getEconomyManager().mayorService();
		if (mayor == null) {
			source.sendFailure(Component.literal("§cBelediye sistemi kapali."));
			return 0;
		}
		MayorService.MayorState state = mayor.state();
		long now = System.currentTimeMillis();
		long budget = McEconomyMod.getEconomyManager().centralBank().getMunicipalBudgetMg();
		source.sendSuccess(() -> Component.literal("§6=== Belediye ==="), false);
		source.sendSuccess(() -> Component.literal("§eButce: §f" + GoldStandard.formatMilligrams(budget)), false);
		if (state.hasMayor()) {
			source.sendSuccess(() -> Component.literal("§eBaskan: §f" + state.mayorName()), false);
		} else {
			source.sendSuccess(() -> Component.literal("§eBaskan: §7Secim bekleniyor"), false);
		}
		if (state.termEndMs() > 0) {
			source.sendSuccess(() -> Component.literal("§eDonem sonu: §f" + FMT.format(Instant.ofEpochMilli(state.termEndMs()))), false);
		}
		source.sendSuccess(() -> Component.literal("§aSecim her zaman acik — /belediye adayol | /belediye oy <ad>"), false);
		return 1;
	}

	private static int candidacy(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		try {
			if (McEconomyMod.getEconomyManager().mayorService()
					.registerCandidate(player.getUUID(), player.getName().getString())) {
				source.sendSuccess(() -> Component.literal("§a[Secim] §fAday oldunuz."), false);
				return 1;
			}
		} catch (Exception e) {
			source.sendFailure(Component.literal("§cKayit basarisiz."));
			return 0;
		}
		source.sendFailure(Component.literal("§cSecim donemi acik degil."));
		return 0;
	}

	private static int vote(CommandSourceStack source, String candidate) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		try {
			if (McEconomyMod.getEconomyManager().mayorService().vote(player.getUUID(), candidate)) {
				source.sendSuccess(() -> Component.literal("§a[Secim] §fOyunuz kaydedildi: " + candidate), false);
				return 1;
			}
		} catch (Exception e) {
			source.sendFailure(Component.literal("§cOy kullanilamadi."));
			return 0;
		}
		source.sendFailure(Component.literal("§cGecersiz aday, secim kapali veya zaten oy kullandiniz."));
		return 0;
	}

	private static int spend(CommandSourceStack source, int amountMc, String purpose) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		long mg = amountMc * 1000L;
		try {
			if (McEconomyMod.getEconomyManager().mayorService()
					.spendBudget(player.getUUID(), mg, purpose)) {
				source.sendSuccess(() -> Component.literal("§a[Belediye] §fHarcama yapildi."), true);
				return 1;
			}
		} catch (Exception e) {
			source.sendFailure(Component.literal("§cHarcama basarisiz."));
			return 0;
		}
		source.sendFailure(Component.literal("§cBaskan degilsiniz veya butce yetersiz."));
		return 0;
	}
}
