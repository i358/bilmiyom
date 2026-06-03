package com.mceconomy.reserve;

import com.mceconomy.McEconomyMod;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.tax.CentralBank;
import com.mceconomy.world.CentralBankPlacer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Fiziksel altin rezervini sayar; para arzina karsi destek oranini hesaplar. */
public final class GoldReserveService {
	/** 1 altin blogu = 9 kulce. */
	public static final int INGOTS_PER_GOLD_BLOCK = 9;

	private int cachedGoldBlocks;
	private long lastScanMs;
	private DepotLedgerService depotLedger;

	public void bindDepotLedger(DepotLedgerService depotLedger) {
		this.depotLedger = depotLedger;
	}

	public int countGoldBlocks(ServerLevel level) {
		if (CentralBankPlacer.reserveMin() == null || CentralBankPlacer.reserveMax() == null) {
			return 0;
		}
		int count = 0;
		var min = CentralBankPlacer.reserveMin();
		var max = CentralBankPlacer.reserveMax();
		for (int x = min.getX(); x <= max.getX(); x++) {
			for (int y = min.getY(); y <= max.getY(); y++) {
				for (int z = min.getZ(); z <= max.getZ(); z++) {
					BlockState state = level.getBlockState(new net.minecraft.core.BlockPos(x, y, z));
					if (state.is(Blocks.GOLD_BLOCK)) {
						count++;
					}
				}
			}
		}
		cachedGoldBlocks = count;
		lastScanMs = System.currentTimeMillis();
		return count;
	}

	public void refresh(MinecraftServer server) {
		if (server == null) {
			return;
		}
		countGoldBlocks(server.overworld());
	}

	public int cachedGoldBlocks() {
		return cachedGoldBlocks;
	}

	public long backingMilligrams() {
		return (long) cachedGoldBlocks * INGOTS_PER_GOLD_BLOCK * GoldStandard.MILLIGRAMS_PER_INGOT;
	}

	/**
	 * Para arzina karsi fiziksel altin destek orani (0..1+). Dusukse enflasyon baskisi artar.
	 */
	public double coverageRatio(long moneySupplyMg) {
		if (moneySupplyMg <= 0) {
			return 1.0;
		}
		return backingMilligrams() / (double) moneySupplyMg;
	}

	/** Rezervden fiziksel altin blogu cikarir (basarili soygun). */
	public int withdrawGoldBlocks(ServerLevel level, int maxBlocks) {
		if (CentralBankPlacer.reserveMin() == null || maxBlocks <= 0) {
			return 0;
		}
		int removed = 0;
		var min = CentralBankPlacer.reserveMin();
		var max = CentralBankPlacer.reserveMax();
		for (int y = max.getY(); y >= min.getY() && removed < maxBlocks; y--) {
			for (int x = min.getX(); x <= max.getX() && removed < maxBlocks; x++) {
				for (int z = min.getZ(); z <= max.getZ() && removed < maxBlocks; z++) {
					var pos = new net.minecraft.core.BlockPos(x, y, z);
					if (level.getBlockState(pos).is(Blocks.GOLD_BLOCK)) {
						level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
						removed++;
					}
				}
			}
		}
		countGoldBlocks(level);
		if (removed > 0 && depotLedger != null) {
			try {
				depotLedger.onGoldBlocksRemoved(removed);
			} catch (java.sql.SQLException e) {
				McEconomyMod.LOGGER.error("Rezerv defteri guncellenemedi", e);
			}
		}
		return removed;
	}

	public void applyReservePressure(CentralBank centralBank, long moneySupplyMg) {
		double coverage = coverageRatio(moneySupplyMg);
		double target = com.mceconomy.config.EconomyConfig.targetGoldReserveCoverage();
		if (coverage < target) {
			double deficit = (target - coverage) / target;
			double bump = 1.0 + Math.min(0.08, deficit * 0.15);
			double newFactor = Math.max(1.0, centralBank.getGoldFactor() * bump);
			centralBank.setGoldFactor(newFactor);
			GoldStandard.setGoldFactor(newFactor);
		} else if (coverage > target * 1.5) {
			double newFactor = Math.max(1.0, centralBank.getGoldFactor() * 0.998);
			centralBank.setGoldFactor(newFactor);
			GoldStandard.setGoldFactor(newFactor);
		}
		McEconomyMod.LOGGER.debug("Altin rezervi: {} blok, destek {}%",
				cachedGoldBlocks, String.format("%.2f", coverage * 100));
	}
}
