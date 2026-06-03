package com.mceconomy.command;

import com.mceconomy.McEconomyMod;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.news.EconomyBulletin;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static net.minecraft.commands.Commands.literal;

public final class BulletinCommand {
	private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
			.withLocale(new Locale("tr", "TR"))
			.withZone(ZoneId.systemDefault());

	private BulletinCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("bulten")
				.executes(ctx -> show(ctx.getSource(), 5))
				.then(net.minecraft.commands.Commands.argument("adet", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 20))
						.executes(ctx -> show(ctx.getSource(),
								com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "adet")))));
	}

	private static int show(CommandSourceStack source, int count) {
		var manager = McEconomyMod.getEconomyManager();
		if (manager == null || manager.bulletinService() == null) {
			source.sendFailure(Component.literal("§cEkonomi bulteni su an kullanilamiyor."));
			return 0;
		}
		List<EconomyBulletin> bulletins = manager.bulletinService().recent(count);
		if (bulletins.isEmpty()) {
			source.sendSuccess(() -> Component.literal("§7[Bulten] §fHenuz yayinlanmis haber yok."), false);
			return 1;
		}
		source.sendSuccess(() -> Component.literal("§4§l═══ EKONOMI BULTENI ═══"), false);
		for (EconomyBulletin bulletin : bulletins) {
			String time = TIME_FMT.format(Instant.ofEpochMilli(bulletin.createdAt()));
			String value = bulletin.valueMg() > 0
					? " §6" + GoldStandard.formatMilligrams(bulletin.valueMg())
					: "";
			source.sendSuccess(() -> Component.literal(
					"§c§l[" + bulletin.category() + "] §f" + time + "\n§e" + bulletin.headline() + value
							+ "\n§7" + bulletin.body()), false);
		}
		return 1;
	}
}
