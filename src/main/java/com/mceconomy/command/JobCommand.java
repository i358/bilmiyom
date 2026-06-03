package com.mceconomy.command;

import com.mceconomy.McEconomyMod;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.job.JobKitService;
import com.mceconomy.job.JobType;
import com.mceconomy.job.QuestManager;
import com.mceconomy.player.PlayerEconomyProfile;
import com.mceconomy.util.Messages;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class JobCommand {
	private static final SuggestionProvider<CommandSourceStack> JOB_SUGGESTIONS = (ctx, builder) -> {
		for (JobType type : JobType.values()) {
			builder.suggest(type.id());
		}
		return builder.buildFuture();
	};

	private JobCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("meslek")
				.then(literal("sec").then(argument("tip", StringArgumentType.string())
						.suggests(JOB_SUGGESTIONS)
						.executes(ctx -> setJob(ctx.getSource(), StringArgumentType.getString(ctx, "tip")))))
				.then(literal("istifa").executes(ctx -> resign(ctx.getSource()))));

		dispatcher.register(literal("gorev")
				.then(literal("al").executes(ctx -> assignQuest(ctx.getSource())))
				.then(literal("durum").executes(ctx -> showQuest(ctx.getSource())))
				.then(literal("teslim").executes(ctx -> completeQuest(ctx.getSource())))
				.then(literal("iptal").executes(ctx -> cancelQuest(ctx.getSource()))));
	}

	private static int setJob(CommandSourceStack source, String jobId) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		JobType job = JobType.fromString(jobId);
		if (job == null) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
			return 0;
		}
		if (McEconomyMod.getEconomyManager().jobManager().setJob(player.getUUID(), job)) {
			JobKitService.giveKit(player, job);
			source.sendSuccess(() -> Messages.tr("command.mceconomy.job.set", job.displayName()), false);
			return 1;
		}
		return 0;
	}

	private static int resign(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		if (McEconomyMod.getEconomyManager().jobManager().resignJob(player.getUUID())) {
			McEconomyMod.getEconomyManager().questManager().cancelQuest(player);
			JobKitService.reclaimKit(player);
			source.sendSuccess(() -> Component.literal("§eMesleginizden istifa ettiniz. Yeni meslek secebilirsiniz."), false);
			return 1;
		}
		source.sendFailure(Component.literal("§cZaten bir mesleginiz yok."));
		return 0;
	}

	private static int cancelQuest(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		if (McEconomyMod.getEconomyManager().questManager().cancelQuest(player)) {
			source.sendSuccess(() -> Component.literal("§eAktif goreviniz iptal edildi."), false);
			return 1;
		}
		source.sendFailure(Component.literal("§cIptal edilecek aktif gorev yok."));
		return 0;
	}

	private static int assignQuest(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		QuestManager questManager = McEconomyMod.getEconomyManager().questManager();
		if (questManager.getQuest(player.getUUID()) != null) {
			source.sendFailure(Messages.tr("command.mceconomy.quest.already_active"));
			return 0;
		}
		var workJob = McEconomyMod.getEconomyManager().playerEmploymentService().resolveWorkJobType(player.getUUID());
		if (workJob.isEmpty()) {
			source.sendFailure(Component.literal(
					"§cGorev almak icin bir sirkette calismali veya §e/meslek sec §cyapmalisiniz."));
			return 0;
		}
		var quest = questManager.assignRandomQuest(player.getUUID(), workJob.get(), player);
		if (quest == null) {
			source.sendFailure(Messages.tr("command.mceconomy.error.generic"));
			return 0;
		}
		source.sendSuccess(() -> Component.literal(
				"§aGörev: §f" + quest.title()
						+ (quest.isCompanyQuest() ? " §6[Sirket — uretim sirkete]" : "")
						+ " §7(" + quest.progress() + "/" + quest.required() + ") Ödül: "
						+ GoldStandard.formatMilligrams(quest.reward())), false);
		return 1;
	}

	private static int showQuest(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		QuestManager.ActiveQuest quest = McEconomyMod.getEconomyManager().questManager().getQuest(player.getUUID());
		if (quest == null) {
			source.sendFailure(Messages.tr("command.mceconomy.quest.none"));
			return 0;
		}
		String tag = quest.isCompanyQuest() ? " §6[Sirket]" : "";
		source.sendSuccess(() -> Component.literal(
				"§6Görev: §f" + quest.title() + tag
						+ " §7(" + quest.progress() + "/" + quest.required() + ")"
						+ " §aÖdül: " + GoldStandard.formatMilligrams(quest.reward())
						+ (quest.isCompanyQuest() ? " §7(uretim sirkete, size pay)" : "")), false);
		return 1;
	}

	private static int completeQuest(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return 0;
		}
		QuestManager questManager = McEconomyMod.getEconomyManager().questManager();
		QuestManager.ActiveQuest quest = questManager.getQuest(player.getUUID());
		if (quest == null) {
			source.sendFailure(Messages.tr("command.mceconomy.quest.none"));
			return 0;
		}
		if (questManager.completeQuest(player)) {
			source.sendSuccess(() -> Messages.tr("command.mceconomy.quest.completed",
					GoldStandard.formatMilligrams(quest.reward())), false);
			return 1;
		}
		if (quest.type() == QuestManager.QuestType.DELIVER_ITEM) {
			source.sendFailure(Messages.tr("command.mceconomy.quest.missing_items"));
		} else {
			source.sendFailure(Messages.tr("command.mceconomy.quest.not_complete"));
		}
		return 0;
	}
}
