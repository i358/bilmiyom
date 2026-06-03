package com.mceconomy.market;

import com.mceconomy.economy.GoldStandard;
import com.mceconomy.job.JobCategory;
import com.mceconomy.job.JobType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public enum Commodity {
	GOLD("altin", Items.GOLD_INGOT, GoldStandard.MILLIGRAMS_PER_INGOT, false, false, "Altin Kulcesi", JobCategory.GENERAL),
	COAL("komur", Items.COAL, 5 * GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Komur", JobCategory.MINING),
	RAW_COPPER("ham_bakir", Items.RAW_COPPER, 8 * GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Ham Bakir", JobCategory.MINING),
	COPPER_INGOT("bakir", Items.COPPER_INGOT, 15 * GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Bakir", JobCategory.MINING),
	RAW_IRON("ham_demir", Items.RAW_IRON, 20 * GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Ham Demir", JobCategory.MINING),
	IRON("demir", Items.IRON_INGOT, 50 * GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Demir", JobCategory.MINING),
	RAW_GOLD("ham_altin", Items.RAW_GOLD, 80 * GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Ham Altin", JobCategory.MINING),
	REDSTONE("kizil_tas", Items.REDSTONE, 12 * GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Kizil Tas", JobCategory.MINING),
	LAPIS("lapis", Items.LAPIS_LAZULI, 25 * GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Lapis", JobCategory.MINING),
	DIAMOND("elmas", Items.DIAMOND, 500 * GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Elmas", JobCategory.MINING),
	EMERALD("zumrut", Items.EMERALD, 300 * GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Zumrut", JobCategory.MINING),
	NETHERITE("netherite", Items.NETHERITE_INGOT, 2000 * GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, false, "Netherite", JobCategory.MINING),
	COBBLESTONE("tas", Items.COBBLESTONE, 2 * GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Kırık Tas", JobCategory.MINING),
	OAK_LOG("mese", Items.OAK_LOG, 3 * GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Mese Kütügü", JobCategory.LUMBER),
	SPRUCE_LOG("ladin", Items.SPRUCE_LOG, 3 * GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Ladin Kütügü", JobCategory.LUMBER),
	WHEAT("bugday", Items.WHEAT, GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Bugday", JobCategory.FARMING),
	POTATO("patates", Items.POTATO, GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Patates", JobCategory.FARMING),
	CARROT("havuc", Items.CARROT, GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Havuc", JobCategory.FARMING),
	BEETROOT("pancar", Items.BEETROOT, GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Pancar", JobCategory.FARMING),
	SUGAR_CANE("seker_kamisi", Items.SUGAR_CANE, 2 * GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Seker Kamisi", JobCategory.FARMING),
	PUMPKIN("balkabagi", Items.PUMPKIN, 4 * GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Balkabagi", JobCategory.FARMING),
	MELON_SLICE("karpuz", Items.MELON_SLICE, GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Karpuz Dilimi", JobCategory.FARMING),
	COD("morina", Items.COD, 6 * GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Morina", JobCategory.FISHING),
	SALMON("somon", Items.SALMON, 8 * GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Somon", JobCategory.FISHING),
	TROPICAL_FISH("tropikal_balik", Items.TROPICAL_FISH, 12 * GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Tropikal Balik", JobCategory.FISHING),
	LEATHER("deri", Items.LEATHER, 10 * GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Deri", JobCategory.HUNTING),
	BONE("kemik", Items.BONE, 3 * GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Kemik", JobCategory.HUNTING),
	STRING("ip", Items.STRING, 4 * GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Ip", JobCategory.HUNTING),
	FEATHER("tuy", Items.FEATHER, 2 * GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Tuy", JobCategory.HUNTING),
	GUNPOWDER("barut", Items.GUNPOWDER, 15 * GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Barut", JobCategory.HUNTING),
	ROTTEN_FLESH("curuk_et", Items.ROTTEN_FLESH, GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Curuk Et", JobCategory.HUNTING),
	BEEF("sigit_eti", Items.BEEF, 5 * GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Sigir Eti", JobCategory.HUNTING),
	PORKCHOP("domuz_eti", Items.PORKCHOP, 5 * GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Domuz Eti", JobCategory.HUNTING),
	CHICKEN("tavuk_eti", Items.CHICKEN, 4 * GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Tavuk Eti", JobCategory.HUNTING),
	MUTTON("koyun_eti", Items.MUTTON, 5 * GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Koyun Eti", JobCategory.HUNTING),
	RABBIT("tavsan_eti", Items.RABBIT, 6 * GoldStandard.MILLIGRAMS_GOLD_PER_WHEAT, true, true, "Tavsan Eti", JobCategory.HUNTING);

	private final String id;
	private final Item item;
	private final long basePriceMg;
	private final boolean sellable;
	private final boolean buyable;
	private final String displayName;
	private final JobCategory jobCategory;

	Commodity(String id, Item item, long basePriceMg, boolean sellable, boolean buyable,
			String displayName, JobCategory jobCategory) {
		this.id = id;
		this.item = item;
		this.basePriceMg = basePriceMg;
		this.sellable = sellable;
		this.buyable = buyable;
		this.displayName = displayName;
		this.jobCategory = jobCategory;
	}

	public String id() {
		return id;
	}

	public Item item() {
		return item;
	}

	public long basePrice() {
		return basePriceMg;
	}

	public boolean sellable() {
		return sellable;
	}

	public boolean buyable() {
		return buyable;
	}

	public String displayName() {
		return displayName;
	}

	public JobCategory jobCategory() {
		return jobCategory;
	}

	public boolean matchesJob(JobType jobType) {
		return jobType != null && jobType.category() == jobCategory;
	}

	public static Commodity[] sellableCommodities() {
		return Arrays.stream(values()).filter(Commodity::sellable).toArray(Commodity[]::new);
	}

	public static Commodity[] buyableCommodities() {
		return Arrays.stream(values()).filter(Commodity::buyable).toArray(Commodity[]::new);
	}

	public static Commodity fromId(String id) {
		for (Commodity commodity : values()) {
			if (commodity.id.equalsIgnoreCase(id) || commodity.name().equalsIgnoreCase(id)) {
				return commodity;
			}
		}
		return null;
	}

	public static Commodity fromItem(Item item) {
		for (Commodity commodity : values()) {
			if (commodity.item == item) {
				return commodity;
			}
		}
		return null;
	}

	private static final Commodity[] HUNTING_MEATS = { BEEF, PORKCHOP, CHICKEN, MUTTON, RABBIT };

	public static Commodity randomHuntingMeat() {
		return HUNTING_MEATS[ThreadLocalRandom.current().nextInt(HUNTING_MEATS.length)];
	}

	public static Commodity randomForCategory(JobCategory category) {
		List<Commodity> pool = Arrays.stream(values())
				.filter(c -> c.jobCategory == category && c.sellable() && c != GOLD)
				.toList();
		if (pool.isEmpty()) {
			return null;
		}
		return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
	}

	public boolean isHuntingMeat() {
		return switch (this) {
			case BEEF, PORKCHOP, CHICKEN, MUTTON, RABBIT -> true;
			default -> false;
		};
	}
}
