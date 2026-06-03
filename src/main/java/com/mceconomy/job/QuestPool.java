package com.mceconomy.job;

import com.mceconomy.job.QuestManager.QuestType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public final class QuestPool {
	public record QuestTemplate(
			QuestType type,
			String target,
			int required,
			long rewardMg,
			String title,
			JobCategory category
	) {
	}

	private static final List<QuestTemplate> TEMPLATES = List.of(
			// Madencilik
			new QuestTemplate(QuestType.DELIVER_ITEM, "komur", 32, 150_000, "32 komur teslim et", JobCategory.MINING),
			new QuestTemplate(QuestType.DELIVER_ITEM, "ham_demir", 16, 200_000, "16 ham demir teslim et", JobCategory.MINING),
			new QuestTemplate(QuestType.DELIVER_ITEM, "demir", 8, 250_000, "8 demir külçesi teslim et", JobCategory.MINING),
			new QuestTemplate(QuestType.DELIVER_ITEM, "tas", 64, 120_000, "64 kırık taş teslim et", JobCategory.MINING),
			new QuestTemplate(QuestType.DELIVER_ITEM, "kizil_tas", 32, 180_000, "32 kırmızı taş teslim et", JobCategory.MINING),
			new QuestTemplate(QuestType.DELIVER_ITEM, "lapis", 16, 220_000, "16 lapis teslim et", JobCategory.MINING),
			new QuestTemplate(QuestType.DELIVER_ITEM, "elmas", 4, 800_000, "4 elmas teslim et", JobCategory.MINING),
			// Tarım
			new QuestTemplate(QuestType.DELIVER_ITEM, "bugday", 64, 100_000, "64 buğday teslim et", JobCategory.FARMING),
			new QuestTemplate(QuestType.DELIVER_ITEM, "patates", 32, 90_000, "32 patates teslim et", JobCategory.FARMING),
			new QuestTemplate(QuestType.DELIVER_ITEM, "havuc", 32, 90_000, "32 havuç teslim et", JobCategory.FARMING),
			new QuestTemplate(QuestType.DELIVER_ITEM, "pancar", 32, 95_000, "32 pancar teslim et", JobCategory.FARMING),
			new QuestTemplate(QuestType.DELIVER_ITEM, "seker_kamisi", 48, 110_000, "48 şeker kamışı teslim et", JobCategory.FARMING),
			new QuestTemplate(QuestType.DELIVER_ITEM, "balkabagi", 16, 130_000, "16 balkabağı teslim et", JobCategory.FARMING),
			// Ormancılık
			new QuestTemplate(QuestType.DELIVER_ITEM, "mese", 32, 100_000, "32 meşe kütüğü teslim et", JobCategory.LUMBER),
			new QuestTemplate(QuestType.DELIVER_ITEM, "ladin", 32, 100_000, "32 ladın kütüğü teslim et", JobCategory.LUMBER),
			// Balıkçılık
			new QuestTemplate(QuestType.DELIVER_ITEM, "morina", 16, 150_000, "16 morina teslim et", JobCategory.FISHING),
			new QuestTemplate(QuestType.DELIVER_ITEM, "somon", 12, 180_000, "12 somon teslim et", JobCategory.FISHING),
			new QuestTemplate(QuestType.DELIVER_ITEM, "tropikal_balik", 8, 200_000, "8 tropikal balık teslim et", JobCategory.FISHING),
			// Avcılık
			new QuestTemplate(QuestType.DELIVER_ITEM, "sigit_eti", 24, 180_000, "24 sigir eti teslim et", JobCategory.HUNTING),
			new QuestTemplate(QuestType.DELIVER_ITEM, "domuz_eti", 24, 175_000, "24 domuz eti teslim et", JobCategory.HUNTING),
			new QuestTemplate(QuestType.DELIVER_ITEM, "tavuk_eti", 32, 150_000, "32 tavuk eti teslim et", JobCategory.HUNTING),
			new QuestTemplate(QuestType.DELIVER_ITEM, "deri", 16, 160_000, "16 deri teslim et", JobCategory.HUNTING),
			new QuestTemplate(QuestType.DELIVER_ITEM, "kemik", 32, 100_000, "32 kemik teslim et", JobCategory.HUNTING),
			new QuestTemplate(QuestType.DELIVER_ITEM, "ip", 24, 120_000, "24 ip teslim et", JobCategory.HUNTING),
			new QuestTemplate(QuestType.DELIVER_ITEM, "barut", 8, 200_000, "8 barut teslim et", JobCategory.HUNTING),
			new QuestTemplate(QuestType.KILL_MOB, "minecraft:zombie", 10, 180_000, "10 zombi öldür", JobCategory.HUNTING),
			new QuestTemplate(QuestType.KILL_MOB, "minecraft:skeleton", 8, 200_000, "8 iskelet öldür", JobCategory.HUNTING),
			new QuestTemplate(QuestType.KILL_MOB, "minecraft:spider", 12, 170_000, "12 örümcek öldür", JobCategory.HUNTING),
			// Genel / tüccar
			new QuestTemplate(QuestType.DELIVER_ITEM, "bakir", 16, 180_000, "16 bakır teslim et", JobCategory.TRADING),
			new QuestTemplate(QuestType.DELIVER_ITEM, "zumrut", 2, 500_000, "2 zümrüt teslim et", JobCategory.TRADING),
			new QuestTemplate(QuestType.DELIVER_ITEM, "bugday", 32, 80_000, "32 buğday teslim et", JobCategory.GENERAL),
			new QuestTemplate(QuestType.DELIVER_ITEM, "komur", 16, 70_000, "16 kömür teslim et", JobCategory.GENERAL)
	);

	private QuestPool() {
	}

	public static QuestTemplate randomForJob(JobType jobType) {
		JobCategory category = jobType != null ? jobType.category() : JobCategory.GENERAL;
		List<QuestTemplate> matching = new ArrayList<>();
		for (QuestTemplate template : TEMPLATES) {
			if (template.category() == category || template.category() == JobCategory.GENERAL) {
				matching.add(template);
			}
		}
		if (matching.isEmpty()) {
			matching = TEMPLATES;
		}
		Random random = ThreadLocalRandom.current();
		return matching.get(random.nextInt(matching.size()));
	}

	public static List<QuestTemplate> forCategory(JobCategory category) {
		List<QuestTemplate> result = new ArrayList<>();
		for (QuestTemplate template : TEMPLATES) {
			if (template.category() == category) {
				result.add(template);
			}
		}
		return result;
	}
}
