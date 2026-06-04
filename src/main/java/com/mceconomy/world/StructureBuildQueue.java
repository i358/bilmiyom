package com.mceconomy.world;

import com.mceconomy.McEconomyMod;
import com.mceconomy.config.EconomyConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Sunucu tick'inde batch yapı inşaatı. */
public final class StructureBuildQueue {
	public interface BlockPlacer {
		int totalBlocks();

		BlockState blockAt(int index);

		BlockPos posAt(int index);

		void onComplete(ServerLevel level);
	}

	private record Job(UUID ownerUuid, BlockPlacer placer, int nextIndex, String label) {
		Job advance(int placed) {
			return new Job(ownerUuid, placer, nextIndex + placed, label);
		}
	}

	private static final StructureBuildQueue INSTANCE = new StructureBuildQueue();
	private final Deque<Job> queue = new ArrayDeque<>();
	private final Map<UUID, String> progressByOwner = new ConcurrentHashMap<>();

	public static StructureBuildQueue get() {
		return INSTANCE;
	}

	public boolean enqueue(UUID ownerUuid, BlockPlacer placer, String label) {
		if (queue.size() >= EconomyConfig.maxPendingStructureJobs()) {
			return false;
		}
		queue.add(new Job(ownerUuid, placer, 0, label));
		progressByOwner.put(ownerUuid, label + " — %0");
		return true;
	}

	public void tick(MinecraftServer server) {
		if (queue.isEmpty()) {
			return;
		}
		Job job = queue.peekFirst();
		if (job == null) {
			return;
		}
		ServerLevel level = server.overworld();
		int budget = EconomyConfig.structureBuildBlocksPerTick();
		int placed = 0;
		int index = job.nextIndex();
		while (placed < budget && index < job.placer.totalBlocks()) {
			BlockPos pos = job.placer.posAt(index);
			level.setBlockAndUpdate(pos, job.placer.blockAt(index));
			index++;
			placed++;
		}
		int pct = job.placer.totalBlocks() == 0 ? 100
				: (int) (100L * index / job.placer.totalBlocks());
		progressByOwner.put(job.ownerUuid(), job.label() + " — %" + pct);
		ServerPlayer player = server.getPlayerList().getPlayer(job.ownerUuid());
		if (player != null && placed > 0 && pct % 10 == 0) {
			player.sendSystemMessage(Component.literal("§6[Insaat] §f" + job.label() + " §7%" + pct));
		}
		if (index >= job.placer.totalBlocks()) {
			queue.pollFirst();
			job.placer.onComplete(level);
			progressByOwner.remove(job.ownerUuid());
			if (player != null) {
				player.sendSystemMessage(Component.literal("§a[Insaat] §f" + job.label() + " tamamlandi."));
			}
		} else {
			queue.pollFirst();
			queue.addFirst(job.advance(placed));
		}
	}

	public static int countBuiltPlots(MinecraftServer server) {
		int plots = 0;
		var manager = McEconomyMod.getEconomyManager();
		if (manager != null && manager.propertyService() != null) {
			plots += manager.propertyService().totalPlotCount();
		}
		if (manager != null && manager.companyBuildingService() != null) {
			plots += manager.companyBuildingService().totalPlotCount();
		}
		return plots;
	}

	public String progressFor(UUID uuid) {
		return progressByOwner.get(uuid);
	}

	public void purgeFor(UUID uuid) {
		progressByOwner.remove(uuid);
		for (Iterator<Job> it = queue.iterator(); it.hasNext(); ) {
			if (it.next().ownerUuid().equals(uuid)) {
				it.remove();
			}
		}
	}
}
