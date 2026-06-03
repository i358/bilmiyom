package com.mceconomy.command;

import com.mceconomy.McEconomyMod;
import com.mceconomy.justice.CitizenReport;
import com.mceconomy.justice.PrisonSentence;
import com.mceconomy.justice.ReportType;
import com.mceconomy.util.Permissions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class JusticeCommand {
	private JusticeCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("sikayet")
				.then(argument("hedef", StringArgumentType.word())
						.then(argument("konu", StringArgumentType.string())
								.then(argument("mesaj", StringArgumentType.greedyString())
										.executes(ctx -> complaint(ctx.getSource(),
												StringArgumentType.getString(ctx, "hedef"),
												StringArgumentType.getString(ctx, "konu"),
												StringArgumentType.getString(ctx, "mesaj")))))));
		dispatcher.register(literal("ihbar")
				.then(argument("kategori", StringArgumentType.word())
						.then(argument("mesaj", StringArgumentType.greedyString())
								.executes(ctx -> tipOff(ctx.getSource(), null,
										StringArgumentType.getString(ctx, "kategori"),
										StringArgumentType.getString(ctx, "mesaj")))))
				.then(literal("oyuncu").then(argument("hedef", StringArgumentType.word())
						.then(argument("kategori", StringArgumentType.word())
								.then(argument("mesaj", StringArgumentType.greedyString())
										.executes(ctx -> tipOff(ctx.getSource(),
												StringArgumentType.getString(ctx, "hedef"),
												StringArgumentType.getString(ctx, "kategori"),
												StringArgumentType.getString(ctx, "mesaj"))))))));
		dispatcher.register(literal("hapishane")
				.executes(ctx -> prisonHelp(ctx.getSource()))
				.then(literal("durum").executes(ctx -> prisonStatus(ctx.getSource())))
				.then(literal("liste").executes(ctx -> {
					if (!Permissions.isServerOp(ctx.getSource())) {
						return 0;
					}
					return prisonList(ctx.getSource());
				}))
				.then(literal("yatir").then(argument("oyuncu", StringArgumentType.word())
						.then(argument("dakika", IntegerArgumentType.integer(1, 10080))
								.then(argument("sebep", StringArgumentType.greedyString())
										.executes(ctx -> {
											if (!Permissions.isServerOp(ctx.getSource())) {
												ctx.getSource().sendFailure(Component.literal("§cSadece OP."));
												return 0;
											}
											return imprison(ctx.getSource(),
													StringArgumentType.getString(ctx, "oyuncu"),
													IntegerArgumentType.getInteger(ctx, "dakika"),
													StringArgumentType.getString(ctx, "sebep"));
										})))))
				.then(literal("serbest").then(argument("oyuncu", StringArgumentType.word())
						.executes(ctx -> {
							if (!Permissions.isServerOp(ctx.getSource())) {
								return 0;
							}
							return release(ctx.getSource(), StringArgumentType.getString(ctx, "oyuncu"));
						}))));
		dispatcher.register(literal("adalet")
				.executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayer();
					if (player == null) {
						return 0;
					}
					player.sendSystemMessage(Component.literal("§e=== Adalet ==="));
					player.sendSystemMessage(Component.literal("§e/sikayet <oyuncu> <konu> <mesaj>"));
					player.sendSystemMessage(Component.literal("§e/ihbar <kategori> <mesaj>"));
					player.sendSystemMessage(Component.literal("§e/ihbar oyuncu <ad> <kategori> <mesaj>"));
					player.sendSystemMessage(Component.literal(
					"§7Calinti kisisel kasada + acik ihbar → borc, mal varligi el koyma (§eotomatik§7)"));
			player.sendSystemMessage(Component.literal("§7Ihbar yoksa supheliye dokunulmaz — OP gerekmez"));
					player.sendSystemMessage(Component.literal("§e/hapishane durum §7— Hapis durumu"));
					player.sendSystemMessage(Component.literal("§e/itiraz §7— MASAK itirazi"));
					return 1;
				})
				.then(literal("raporlar").executes(ctx -> {
					if (!Permissions.isMbStaff(ctx.getSource()) && !Permissions.isServerOp(ctx.getSource())) {
						return 0;
					}
					return listReports(ctx.getSource());
				})));
	}

	private static int complaint(CommandSourceStack source, String target, String subject, String message) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		try {
			if (McEconomyMod.getEconomyManager().reportService().submitComplaint(
					player.getUUID(), player.getName().getString(), target, "GENEL", subject, message)) {
				source.sendSuccess(() -> Component.literal(
						"§a[Sikayet] §fKaydedildi. §7Sistem hedefin kisisel kasasini otomatik tarayacak."), false);
				return 1;
			}
		} catch (Exception e) {
			source.sendFailure(Component.literal("§cSikayet gonderilemedi."));
			return 0;
		}
		source.sendFailure(Component.literal("§cGecersiz hedef veya kendinizi sikayet edemezsiniz."));
		return 0;
	}

	private static int tipOff(CommandSourceStack source, String target, String category, String message) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		try {
			if (McEconomyMod.getEconomyManager().reportService().submitTipOff(
					player.getUUID(), player.getName().getString(), target, category, message)) {
				source.sendSuccess(() -> Component.literal(
						"§a[Ihbar] §fIhbariniz kaydedildi. §7Sistem suphelinin kisisel kasasini "
								+ "otomatik tarayacak — OP beklemenize gerek yok."), false);
				return 1;
			}
		} catch (Exception e) {
			source.sendFailure(Component.literal("§cIhbar gonderilemedi."));
			return 0;
		}
		source.sendFailure(Component.literal("§cIhbar kaydedilemedi."));
		return 0;
	}

	private static int prisonStatus(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		var prison = McEconomyMod.getEconomyManager().prisonService().sentenceFor(player.getUUID());
		if (prison.isEmpty()) {
			source.sendSuccess(() -> Component.literal("§a[Hapishane] §fHapis kaydiniz yok."), false);
			return 1;
		}
		PrisonSentence s = prison.get();
		source.sendSuccess(() -> Component.literal(
				"§c[Hapishane] §fHapistesiniz. Kalan: " + formatMs(s.remainingMs())
						+ "\n§7Sebep: " + s.reason()), false);
		return 1;
	}

	private static int prisonList(CommandSourceStack source) {
		source.sendSuccess(() -> Component.literal("§c=== Aktif Mahkumlar ==="), false);
		for (PrisonSentence s : McEconomyMod.getEconomyManager().prisonService().activeSentences()) {
			source.sendSuccess(() -> Component.literal(
					s.playerName() + " — kalan " + formatMs(s.remainingMs()) + " — " + s.reason()), false);
		}
		return 1;
	}

	private static int imprison(CommandSourceStack source, String target, int minutes, String reason) {
		String admin = source.getTextName();
		try {
			if (McEconomyMod.getEconomyManager().prisonService().imprisonByName(target, minutes, reason, admin)) {
				source.sendSuccess(() -> Component.literal(
						"§a[Hapishane] §f" + target + " → " + minutes + " dk. Sebep: " + reason), true);
				return 1;
			}
		} catch (Exception e) {
			source.sendFailure(Component.literal("§cHapis uygulanamadi."));
			return 0;
		}
		source.sendFailure(Component.literal("§cOyuncu bulunamadi veya zaten hapiste."));
		return 0;
	}

	private static int release(CommandSourceStack source, String target) {
		UUID uuid = BalanceCommand.findPlayerUuid(target);
		if (uuid == null) {
			source.sendFailure(Component.literal("§cOyuncu bulunamadi."));
			return 0;
		}
		try {
			if (McEconomyMod.getEconomyManager().prisonService().release(uuid)) {
				source.sendSuccess(() -> Component.literal("§a[Hapishane] §f" + target + " serbest birakildi."), true);
				return 1;
			}
		} catch (Exception e) {
			source.sendFailure(Component.literal("§cIslem basarisiz."));
			return 0;
		}
		source.sendFailure(Component.literal("§cOyuncu hapiste degil."));
		return 0;
	}

	private static int listReports(CommandSourceStack source) {
		source.sendSuccess(() -> Component.literal("§e=== Acik Sikayet / Ihbar ==="), false);
		for (CitizenReport r : McEconomyMod.getEconomyManager().reportService().openReports()) {
			String target = r.targetName() != null ? r.targetName() : "—";
			source.sendSuccess(() -> Component.literal(
					"#" + r.id() + " [" + r.type().displayName() + "] " + r.reporterName()
							+ " → " + target + " | " + r.category() + ": " + r.subject()), false);
		}
		return 1;
	}

	private static int prisonHelp(CommandSourceStack source) {
		source.sendSuccess(() -> Component.literal("§e/hapishane durum §7| §e/hapishane yatir <oyuncu> <dk> <sebep> §7(OP)"), false);
		return 1;
	}

	private static String formatMs(long ms) {
		return (ms / 60_000) + " dk";
	}
}
