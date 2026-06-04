package com.mceconomy.world;

import com.mceconomy.McEconomyMod;
import com.mceconomy.economy.EconomyManager;
import com.mceconomy.persistence.EconomyDatabaseReset;
import com.mceconomy.facility.FacilityType;
import com.mceconomy.facility.StolenItemCleanup;
import net.minecraft.server.level.ServerPlayer;
import com.mceconomy.vault.PlayerVault;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Yalnizca modun olusturdugu yapilari siler ve yeniden kurar. */
public final class ModWorldResetService {
	public record ResetReport(
			int prisonersReleased,
			int entitiesRemoved,
			int personalVaultsCleared,
			int companyVaultsCleared,
			int prisonCellsCleared,
			boolean centralBankRebuilt,
			boolean databaseWiped) {
	}

	private ModWorldResetService() {
	}

	/** Tam ekonomi sifirlama: DB + dunya yapilari + MB yeniden kurulum. */
	public static ResetReport fullEconomyReset(MinecraftServer server) {
		return fullEconomyReset(server, null);
	}

	public static ResetReport fullEconomyReset(MinecraftServer server, ServerPlayer bankAnchor) {
		return resetModStructures(server, bankAnchor);
	}

	public static ResetReport resetModStructures(MinecraftServer server) {
		return resetModStructures(server, null);
	}

	public static ResetReport resetModStructures(MinecraftServer server, ServerPlayer bankAnchor) {
		EconomyManager manager = McEconomyMod.getEconomyManager();
		if (manager == null || !manager.isLoaded()) {
			throw new IllegalStateException("Ekonomi sistemi hazir degil");
		}
		ServerLevel level = server.overworld();
		int prisoners = releaseAllPrisoners(manager);
		if (manager.heistService() != null) {
			manager.heistService().forceEnd();
		}
		int entities = purgeModEntities(level, manager);
		int personal = clearPersonalVaults(level, manager);
		int company = clearCompanyVaults(level, manager);
		int cells = clearPrisonCells(level, manager);
		clearFacilityDepotChests(level);
		CentralBankPlacer.clearAllKnownSites(level);
		boolean dbWiped = wipeDatabase(manager, server);
		StolenItemCleanup.purgeAll(server);
		CentralBankPlacer.rebuild(server, bankAnchor);
		if (manager.bankSecurityService() != null) {
			manager.bankSecurityService().purgeExcessGuards();
			manager.bankSecurityService().syncGuardsFromWorld();
		}
		return new ResetReport(prisoners, entities, personal, company, cells, true, dbWiped);
	}

	private static boolean wipeDatabase(EconomyManager manager, MinecraftServer server) {
		try {
			EconomyDatabaseReset.wipeAllEconomyData(manager.database().connection());
			manager.reloadAfterDatabaseReset(server);
			if (manager.bankRobberyJusticeService() != null) {
				manager.bankRobberyJusticeService().clearInvestigationState();
			}
			return true;
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Ekonomi veritabani sifirlanamadi", e);
			throw new IllegalStateException("Veritabani sifirlanamadi: " + e.getMessage(), e);
		}
	}

	private static int releaseAllPrisoners(EconomyManager manager) {
		if (manager.prisonService() == null) {
			return 0;
		}
		int count = 0;
		for (var sentence : new ArrayList<>(manager.prisonService().activeSentences())) {
			try {
				if (manager.prisonService().release(sentence.playerUuid())) {
					count++;
				}
			} catch (SQLException e) {
				McEconomyMod.LOGGER.error("Hapishane tahliye", e);
			}
		}
		return count;
	}

	private static int purgeModEntities(ServerLevel level, EconomyManager manager) {
		List<Entity> toRemove = new ArrayList<>();
		for (Villager villager : level.getEntities(EntityTypeTest.forClass(Villager.class), e -> true)) {
			if (isModNpc(villager)) {
				toRemove.add(villager);
			}
		}
		for (Zombie zombie : level.getEntities(EntityTypeTest.forClass(Zombie.class), e -> true)) {
			if (zombie.entityTags().contains(CentralBankPlacer.HEIST_ROBBER_TAG)) {
				toRemove.add(zombie);
			}
		}
		for (Entity entity : toRemove) {
			entity.discard();
		}
		int removed = toRemove.size();
		if (manager.bankSecurityService() != null) {
			removed += manager.bankSecurityService().purgeExcessGuards();
		}
		return removed;
	}

	private static boolean isModNpc(Villager villager) {
		return villager.entityTags().contains(CentralBankPlacer.NPC_TAG)
				|| villager.entityTags().contains(CentralBankPlacer.MASAK_NPC_TAG)
				|| villager.entityTags().contains(CentralBankPlacer.EXCHANGE_NPC_TAG)
				|| villager.entityTags().contains(CentralBankPlacer.BLACK_MARKET_NPC_TAG)
				|| villager.entityTags().contains(CentralBankPlacer.BANK_GUARD_TAG)
				|| villager.entityTags().contains(CentralBankPlacer.HEIST_GUARD_TAG)
				|| villager.entityTags().contains(CentralBankPlacer.HEIST_ROBBER_TAG);
	}

	private static int clearPersonalVaults(ServerLevel level, EconomyManager manager) {
		if (manager.vaultService() == null) {
			return 0;
		}
		int count = 0;
		for (PlayerVault vault : manager.vaultService().allVaultsForReset()) {
			clearVaultRoom(level, vault.chestX(), vault.chestY(), vault.chestZ(), Blocks.BEDROCK.defaultBlockState());
			count++;
		}
		return count;
	}

	private static int clearCompanyVaults(ServerLevel level, EconomyManager manager) {
		if (manager.companyVaultService() == null) {
			return 0;
		}
		int count = 0;
		for (var vault : manager.companyVaultService().allVaultsForReset()) {
			clearVaultRoom(level, vault.chestX(), vault.chestY(), vault.chestZ(), Blocks.IRON_BLOCK.defaultBlockState());
			count++;
		}
		return count;
	}

	private static int clearPrisonCells(ServerLevel level, EconomyManager manager) {
		if (manager.prisonService() == null) {
			return 0;
		}
		manager.prisonService().clearAllCells(level);
		return manager.prisonService().maxCellIndexForReset() + 1;
	}

	private static void clearVaultRoom(ServerLevel level, int cx, int cy, int cz, BlockState shell) {
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				for (int y = -1; y <= 4; y++) {
					BlockPos pos = new BlockPos(cx + x, cy + y, cz + z);
					boolean isShell = x == -3 || x == 3 || z == -3 || z == 3 || y == -1 || y == 4;
					level.setBlockAndUpdate(pos, isShell ? shell : air);
				}
			}
		}
		emptyChestAt(level, cx, cy, cz);
	}

	private static void emptyChestAt(ServerLevel level, int x, int y, int z) {
		BlockEntity entity = level.getBlockEntity(new BlockPos(x, y, z));
		if (entity instanceof ChestBlockEntity chest) {
			for (int slot = 0; slot < chest.getContainerSize(); slot++) {
				chest.setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
			}
			chest.setChanged();
		}
	}

	private static void clearFacilityDepotChests(ServerLevel level) {
		for (FacilityType type : FacilityType.values()) {
			BlockPos depot = CentralBankPlacer.depotPos(type);
			if (depot != null) {
				emptyChestAt(level, depot.getX(), depot.getY(), depot.getZ());
			}
		}
	}
}
