package com.mceconomy.justice;

import com.mceconomy.McEconomyMod;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.facility.FacilityItemTags;
import com.mceconomy.facility.FacilityType;
import com.mceconomy.facility.FacilityDepotService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** MB depo esyalarina seri no — adalet yalnizca kayip seri numaralariyla esleseni cezalandirir. */
public final class BankAssetSerialRegistry {
	public static final long TICKS_PER_MINECRAFT_DAY = 24_000L;

	private long nextSerial = 1;
	private final Set<String> wantedSerials = new HashSet<>();
	/** Overworld oyun zamani / 24000 — kayip listesi baslangic MC gunu. */
	private long investigationStartedWorldDay = -1L;
	/** Tamamlanan sabah ust aramasi sayisi (her gunduz gecisi = 1 MC gunu). */
	private int investigationMorningsCompleted;

	public void clearAll() {
		wantedSerials.clear();
		investigationStartedWorldDay = -1L;
		investigationMorningsCompleted = 0;
		nextSerial = 1;
	}

	/** Dunya yasindan gecen tam Minecraft gunu sayisi. */
	public static long worldDay(ServerLevel level) {
		return level.getGameTime() / TICKS_PER_MINECRAFT_DAY;
	}

	public String assignSerial(ItemStack stack, FacilityType depotType) {
		if (stack.isEmpty()) {
			return null;
		}
		String existing = FacilityItemTags.getSerial(stack);
		if (existing != null) {
			return existing;
		}
		String serial = "MB-" + String.format("%08d", nextSerial++);
		FacilityItemTags.markDepotWithSerial(stack, depotType, serial);
		FacilityItemTags.applySerialDisplayName(stack);
		return serial;
	}

	public void registerMissingBetween(List<ItemStack> before, List<ItemStack> after) {
		Set<String> beforeSet = serialsInStacks(before);
		Set<String> afterSet = serialsInStacks(after);
		for (String serial : beforeSet) {
			if (!afterSet.contains(serial)) {
				wantedSerials.add(serial);
			}
		}
		reconcileRecoveredInDepots(after);
		syncInvestigationClock();
		if (!wantedSerials.isEmpty()) {
			McEconomyMod.LOGGER.info("[Adalet] Gece deposundan {} kayip seri numarasi islendi.", wantedSerials.size());
		}
	}

	/** Sandiga geri konan zimmetli esya — arama listesinden dusur. */
	public void reconcileRecoveredInDepots(List<ItemStack> currentDepotContents) {
		Set<String> inDepot = serialsInStacks(currentDepotContents);
		wantedSerials.removeIf(inDepot::contains);
		syncInvestigationClock();
	}

	public void registerWantedSerial(String serial) {
		if (serial != null && !serial.isBlank()) {
			wantedSerials.add(serial);
			syncInvestigationClock();
		}
	}

	public boolean hasActiveInvestigation() {
		return !wantedSerials.isEmpty();
	}

	public long investigationStartedWorldDay() {
		return investigationStartedWorldDay;
	}

	/** Bu sabahki arama (1..N) — {@link #investigationMorningsCompleted} + 1. */
	public int getInvestigationDayIndex() {
		if (!hasActiveInvestigation()) {
			return 0;
		}
		return Math.min(investigationMorningsCompleted + 1, EconomyConfig.wantedSerialSearchDays());
	}

	/** Bu sabah aramadan sonra kalan sabah sayisi. */
	public int morningsRemainingAfterSearch() {
		return Math.max(0, EconomyConfig.wantedSerialSearchDays() - investigationMorningsCompleted - 1);
	}

	/** {@code wantedSerialSearchDays} sabah ust aramasi tamamlandi mi? */
	public boolean isInvestigationExpired() {
		return hasActiveInvestigation()
				&& investigationMorningsCompleted >= EconomyConfig.wantedSerialSearchDays();
	}

	/** Gunduz basinda bir sabah aramasi daha yapildi. */
	public void recordMorningSearch() {
		if (hasActiveInvestigation()) {
			investigationMorningsCompleted++;
		}
	}

	/** Sure doldu: kayip listesi kapatilir (esyalar artik 'aranmiyor'). */
	public int abandonInvestigation() {
		int abandoned = wantedSerials.size();
		wantedSerials.clear();
		investigationStartedWorldDay = -1L;
		investigationMorningsCompleted = 0;
		return abandoned;
	}

	private void syncInvestigationClock() {
		if (wantedSerials.isEmpty()) {
			investigationStartedWorldDay = -1L;
			investigationMorningsCompleted = 0;
		} else if (investigationStartedWorldDay < 0) {
			long day = resolveCurrentWorldDay();
			if (day >= 0) {
				investigationStartedWorldDay = day;
				investigationMorningsCompleted = 0;
			}
		}
	}

	private static long resolveCurrentWorldDay() {
		var manager = McEconomyMod.getEconomyManager();
		if (manager == null || manager.server() == null) {
			return -1L;
		}
		return worldDay(manager.server().overworld());
	}

	public int wantedCount() {
		return wantedSerials.size();
	}

	public boolean isWanted(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		String serial = FacilityItemTags.getSerial(stack);
		return serial != null && wantedSerials.contains(serial);
	}

	public Set<UUID> findPlayersHoldingWanted(MinecraftServer server) {
		Set<UUID> found = new HashSet<>();
		if (server == null || wantedSerials.isEmpty()) {
			return found;
		}
		var manager = McEconomyMod.getEconomyManager();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isCreative() || player.isSpectator()) {
				continue;
			}
			if (containerHasWanted(player.getInventory())) {
				found.add(player.getUUID());
				continue;
			}
			if (manager != null && manager.vaultService() != null) {
				Container chest = manager.vaultService().openChest(player.getUUID(), server.overworld());
				if (chest != null && containerHasWanted(chest)) {
					found.add(player.getUUID());
				}
			}
		}
		return found;
	}

	public int clearWantedFromContainer(Container container) {
		int cleared = 0;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			String serial = FacilityItemTags.getSerial(stack);
			if (serial != null && wantedSerials.contains(serial)) {
				wantedSerials.remove(serial);
			}
			if (FacilityItemTags.isStolen(stack) || serial != null) {
				FacilityItemTags.clearTheftMarks(stack);
				container.setItem(slot, stack);
				cleared++;
			}
		}
		syncInvestigationClock();
		return cleared;
	}

	public void clearWantedSerials() {
		wantedSerials.clear();
		investigationStartedWorldDay = -1L;
		investigationMorningsCompleted = 0;
	}

	/** Calinti altin MB kasasina geri konunca arama listesinden cikar. */
	public void recoverSerial(String serial) {
		if (serial != null && !serial.isBlank()) {
			wantedSerials.remove(serial);
			syncInvestigationClock();
		}
	}

	private boolean containerHasWanted(Container container) {
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			if (isWanted(container.getItem(slot))) {
				return true;
			}
		}
		return false;
	}

	private static Set<String> serialsInStacks(List<ItemStack> stacks) {
		Set<String> set = new HashSet<>();
		for (ItemStack stack : stacks) {
			String serial = FacilityItemTags.getSerial(stack);
			if (serial != null) {
				set.add(serial);
			}
		}
		return set;
	}

	public static void snapshotAllDepots(ServerLevel level, FacilityDepotService depot,
			List<ItemStack> into) {
		into.clear();
		for (FacilityType type : FacilityType.values()) {
			into.addAll(depot.snapshot(level, type));
		}
	}
}
