package com.mceconomy.reserve;

import com.mceconomy.persistence.repo.NationalReserveRepository;

import java.sql.SQLException;

/** Fiziksel altin kasasi ve rezerv blogu icin beklenen miktar defteri. */
public final class DepotLedgerService {
	public static final String KEY_PHYSICAL_GOLD_INGOTS = "physical_gold_expected_ingots";
	public static final String KEY_GOLD_RESERVE_BLOCKS = "gold_reserve_expected_blocks";

	private final NationalReserveRepository repository;
	private int expectedPhysicalGoldIngots;
	private int expectedGoldReserveBlocks;

	public DepotLedgerService(NationalReserveRepository repository) {
		this.repository = repository;
	}

	public void load() throws SQLException {
		expectedPhysicalGoldIngots = repository.loadLedger(KEY_PHYSICAL_GOLD_INGOTS, 0);
		expectedGoldReserveBlocks = repository.loadLedger(KEY_GOLD_RESERVE_BLOCKS, -1);
	}

	public int expectedPhysicalGoldIngots() {
		return expectedPhysicalGoldIngots;
	}

	public int expectedGoldReserveBlocks() {
		return expectedGoldReserveBlocks;
	}

	public void setExpectedGoldReserveBlocks(int blocks) throws SQLException {
		expectedGoldReserveBlocks = Math.max(0, blocks);
		repository.saveLedger(KEY_GOLD_RESERVE_BLOCKS, expectedGoldReserveBlocks);
	}

	public void onPhysicalGoldDeposited(int ingots) throws SQLException {
		if (ingots <= 0) {
			return;
		}
		expectedPhysicalGoldIngots += ingots;
		repository.saveLedger(KEY_PHYSICAL_GOLD_INGOTS, expectedPhysicalGoldIngots);
	}

	public void onPhysicalGoldWithdrawn(int ingots) throws SQLException {
		if (ingots <= 0) {
			return;
		}
		expectedPhysicalGoldIngots = Math.max(0, expectedPhysicalGoldIngots - ingots);
		repository.saveLedger(KEY_PHYSICAL_GOLD_INGOTS, expectedPhysicalGoldIngots);
	}

	public void onGoldBlocksRemoved(int blocks) throws SQLException {
		if (blocks <= 0) {
			return;
		}
		if (expectedGoldReserveBlocks < 0) {
			return;
		}
		expectedGoldReserveBlocks = Math.max(0, expectedGoldReserveBlocks - blocks);
		repository.saveLedger(KEY_GOLD_RESERVE_BLOCKS, expectedGoldReserveBlocks);
	}

	public void syncGoldReserveBlocks(int actualBlocks) throws SQLException {
		if (expectedGoldReserveBlocks < 0) {
			setExpectedGoldReserveBlocks(actualBlocks);
		}
	}

	public int physicalGoldDeficit(int actualIngots) {
		return Math.max(0, expectedPhysicalGoldIngots - actualIngots);
	}

	public int goldReserveDeficit(int actualBlocks) {
		if (expectedGoldReserveBlocks < 0) {
			return 0;
		}
		return Math.max(0, expectedGoldReserveBlocks - actualBlocks);
	}

	public void reconcileGoldReserve(int actualBlocks) throws SQLException {
		if (expectedGoldReserveBlocks >= 0 && actualBlocks < expectedGoldReserveBlocks) {
			expectedGoldReserveBlocks = actualBlocks;
			repository.saveLedger(KEY_GOLD_RESERVE_BLOCKS, expectedGoldReserveBlocks);
		}
	}

	public void reconcilePhysicalGold(int actualIngots) throws SQLException {
		if (actualIngots < expectedPhysicalGoldIngots) {
			expectedPhysicalGoldIngots = actualIngots;
			repository.saveLedger(KEY_PHYSICAL_GOLD_INGOTS, expectedPhysicalGoldIngots);
		}
	}
}
