package com.mceconomy.exchange;

import com.mceconomy.McEconomyMod;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.facility.FacilityDepotService;
import com.mceconomy.facility.FacilityItemTags;
import com.mceconomy.facility.FacilityType;
import com.mceconomy.reserve.DepotLedgerService;
import com.mceconomy.reserve.NationalReserveService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.sql.SQLException;

/** NPC yatirimci sermayesinin fiziksel altin karsiligi — sandik + ulusal rezerv. */
public final class InvestorPhysicalDepotService {
	private static final String GOLD_INGOT_ID = BuiltInRegistries.ITEM.getKey(Items.GOLD_INGOT).toString();

	private InvestorPhysicalDepotService() {
	}

	public static void creditGold(ServerLevel level, FacilityDepotService depot, NationalReserveService reserve,
			DepotLedgerService ledger, long milligrams) {
		if (level == null || depot == null || milligrams <= 0) {
			return;
		}
		int ingots = ingotsForMilligrams(milligrams);
		if (ingots <= 0) {
			return;
		}
		int placed = depositIngots(level, depot, reserve, ingots);
		if (placed > 0 && ledger != null) {
			try {
				ledger.onPhysicalGoldDeposited(placed);
			} catch (SQLException e) {
				McEconomyMod.LOGGER.error("Yatirimci altin defteri", e);
			}
		}
	}

	public static void debitGold(ServerLevel level, FacilityDepotService depot, DepotLedgerService ledger,
			long milligrams) {
		if (level == null || depot == null || milligrams <= 0) {
			return;
		}
		int ingots = ingotsForMilligrams(milligrams);
		if (ingots <= 0) {
			return;
		}
		int remaining = ingots;
		int withdrawn = 0;
		while (remaining > 0) {
			int taken = depot.withdrawItem(level, FacilityType.PHYSICAL_GOLD, Items.GOLD_INGOT, remaining);
			if (taken <= 0) {
				break;
			}
			remaining -= taken;
			withdrawn += taken;
		}
		if (withdrawn > 0 && ledger != null) {
			try {
				ledger.onPhysicalGoldWithdrawn(withdrawn);
			} catch (SQLException e) {
				McEconomyMod.LOGGER.error("Yatirimci altin defteri", e);
			}
		}
		if (remaining > 0) {
			McEconomyMod.LOGGER.debug("[Yatirimci Depo] Sandiktan {} kulce cekilemedi (islem cuzdan uzerinden)", remaining);
		}
	}

	private static int depositIngots(ServerLevel level, FacilityDepotService depot, NationalReserveService reserve,
			int ingots) {
		int remaining = ingots;
		int placed = 0;
		var manager = McEconomyMod.getEconomyManager();
		while (remaining > 0) {
			int stackSize = Math.min(remaining, Items.GOLD_INGOT.getDefaultMaxStackSize());
			ItemStack stack = new ItemStack(Items.GOLD_INGOT, stackSize);
			if (manager != null && manager.bankAssetSerialRegistry() != null) {
				manager.bankAssetSerialRegistry().assignSerial(stack, FacilityType.PHYSICAL_GOLD);
			} else {
				FacilityItemTags.markDepot(stack, FacilityType.PHYSICAL_GOLD);
			}
			if (depot.deposit(level, FacilityType.PHYSICAL_GOLD, stack)) {
				remaining -= stackSize;
				placed += stackSize;
				continue;
			}
			int viaItemApi = depot.depositItem(level, FacilityType.PHYSICAL_GOLD, Items.GOLD_INGOT, remaining);
			remaining -= viaItemApi;
			placed += viaItemApi;
			if (viaItemApi <= 0 && reserve != null) {
				archiveToReserve(reserve, remaining);
				placed += remaining;
				break;
			}
			if (viaItemApi <= 0) {
				McEconomyMod.LOGGER.warn("[Yatirimci Depo] {} kulce sandiga sigmadi, rezerv yok", remaining);
				break;
			}
		}
		return placed;
	}

	private static void archiveToReserve(NationalReserveService reserve, int ingots) {
		if (ingots <= 0) {
			return;
		}
		try {
			reserve.deposit(GOLD_INGOT_ID, ingots);
			McEconomyMod.LOGGER.info("[Yatirimci Depo] Sandik dolu — {} kulce ulusal rezerve aktarildi", ingots);
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Yatirimci altini rezerve yazilamadi", e);
		}
	}

	public static int ingotsForMilligrams(long milligrams) {
		long perIngot = Math.max(1L, Math.round(GoldStandard.MILLIGRAMS_PER_INGOT * GoldStandard.goldFactor()));
		return (int) Math.min(Integer.MAX_VALUE, (milligrams + perIngot - 1) / perIngot);
	}
}
