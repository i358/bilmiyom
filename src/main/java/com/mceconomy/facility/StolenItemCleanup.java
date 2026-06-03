package com.mceconomy.facility;

import com.mceconomy.world.CentralBankPlacer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Sifirlama sonrasi dunyada kalan calinti NBT isaretlerini temizler. */
public final class StolenItemCleanup {
	private StolenItemCleanup() {
	}

	public static void purgeAll(MinecraftServer server) {
		if (server == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			purgeContainer(player.getInventory());
		}
		ServerLevel level = server.overworld();
		for (FacilityType type : FacilityType.values()) {
			BlockPos depot = CentralBankPlacer.depotPos(type);
			if (depot != null) {
				purgeChestAt(level, depot);
			}
		}
	}

	public static int purgeContainer(Container container) {
		int cleared = 0;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (!stack.isEmpty() && FacilityItemTags.isStolen(stack)) {
				FacilityItemTags.clearStolen(stack);
				container.setItem(slot, stack);
				cleared++;
			}
		}
		return cleared;
	}

	private static void purgeChestAt(ServerLevel level, BlockPos pos) {
		BlockEntity entity = level.getBlockEntity(pos);
		if (entity instanceof ChestBlockEntity chest) {
			purgeContainer(chest);
			chest.setChanged();
		}
	}
}
