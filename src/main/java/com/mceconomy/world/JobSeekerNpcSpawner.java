package com.mceconomy.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class JobSeekerNpcSpawner {
	public static final String JOB_SEEKER_TAG = "mceconomy_job_seeker";

	private JobSeekerNpcSpawner() {
	}

	public static String spawnNearPlayer(ServerPlayer player, String displayName, String roleLabel) {
		ServerLevel level = (ServerLevel) player.level();
		var random = level.getRandom();
		Vec3 pos = player.position().add(
				random.nextDouble() * 4 - 2,
				0,
				random.nextDouble() * 4 - 2
		);
		Villager villager = EntityType.VILLAGER.create(level, EntitySpawnReason.EVENT);
		if (villager == null) {
			return null;
		}
		villager.setPos(pos.x, pos.y, pos.z);
		villager.setCustomName(net.minecraft.network.chat.Component.literal(displayName + " [" + roleLabel + "]"));
		villager.setCustomNameVisible(true);
		villager.setPersistenceRequired();
		villager.setVillagerData(new VillagerData(
				level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_TYPE)
						.getOrThrow(VillagerType.PLAINS),
				level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_PROFESSION)
						.getOrThrow(VillagerProfession.LIBRARIAN),
				1));
		villager.addTag(JOB_SEEKER_TAG);
		level.addFreshEntity(villager);
		return villager.getUUID().toString();
	}

	public static void removeSeeker(MinecraftServer server, String entityUuidStr) {
		if (entityUuidStr == null || entityUuidStr.isBlank()) {
			return;
		}
		try {
			UUID uuid = UUID.fromString(entityUuidStr);
			for (ServerLevel level : server.getAllLevels()) {
				var entity = level.getEntity(uuid);
				if (entity != null) {
					entity.discard();
					return;
				}
			}
		} catch (IllegalArgumentException ignored) {
		}
	}
}
