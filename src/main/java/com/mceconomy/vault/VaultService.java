package com.mceconomy.vault;

import com.mceconomy.McEconomyMod;
import com.mceconomy.persistence.repo.VaultRepository;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Kisiye ozel, yer altinda kirilmaz bedrock ile cevrili kilitli kasa sistemi. */
public final class VaultService {
	private static final int BASE_X = 2_000_000;
	private static final int VAULT_Y = -50;
	private static final int VAULT_Z = 64;
	private static final int SPACING = 16;

	private final Map<UUID, PlayerVault> vaults = new HashMap<>();
	private final VaultRepository repository;
	private final MinecraftServer server;
	private int nextIndex;

	public VaultService(VaultRepository repository, MinecraftServer server) {
		this.repository = repository;
		this.server = server;
	}

	public void load() throws SQLException {
		vaults.clear();
		for (PlayerVault vault : repository.loadAll()) {
			vaults.put(vault.ownerUuid(), vault);
		}
		nextIndex = repository.nextIndex();
	}

	public PlayerVault getVault(UUID uuid) {
		return vaults.get(uuid);
	}

	public java.util.Collection<PlayerVault> allVaultsForReset() {
		return java.util.List.copyOf(vaults.values());
	}

	public boolean hasVault(UUID uuid) {
		return vaults.containsKey(uuid);
	}

	public PlayerVault ensureVault(ServerPlayer player) throws SQLException {
		PlayerVault existing = vaults.get(player.getUUID());
		if (existing != null) {
			return existing;
		}
		int index = nextIndex++;
		int cx = BASE_X + index * SPACING;
		PlayerVault vault = new PlayerVault(player.getUUID(), index, cx, VAULT_Y, VAULT_Z,
				null, null, null, null, System.currentTimeMillis());
		buildVault(server.overworld(), vault);
		vaults.put(player.getUUID(), vault);
		repository.save(vault);
		return vault;
	}

	public boolean teleportToVault(ServerPlayer player) {
		try {
			PlayerVault vault = ensureVault(player);
			ServerLevel overworld = server.overworld();
			ensureBuilt(overworld, vault);
			vault.setReturn(player.getX(), player.getY(), player.getZ(),
					player.level().dimension().identifier().toString());
			repository.save(vault);
			player.teleportTo(overworld, vault.chestX() + 1.5, vault.chestY(), vault.chestZ() + 0.5,
					java.util.Set.of(), player.getYRot(), player.getXRot(), false);
			return true;
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Kasa isinlanmasi basarisiz", e);
			return false;
		}
	}

	public boolean teleportBack(ServerPlayer player) {
		PlayerVault vault = vaults.get(player.getUUID());
		if (vault == null || !vault.hasReturn()) {
			return false;
		}
		ServerLevel level = resolveLevel(vault.returnDim());
		player.teleportTo(level, vault.returnX(), vault.returnY(), vault.returnZ(),
				java.util.Set.of(), player.getYRot(), player.getXRot(), false);
		vault.clearReturn();
		try {
			repository.save(vault);
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Kasa donus kaydi basarisiz", e);
		}
		return true;
	}

	public Container openChest(UUID owner, ServerLevel level) {
		PlayerVault vault = vaults.get(owner);
		if (vault == null) {
			return null;
		}
		ensureBuilt(level, vault);
		var entity = level.getBlockEntity(new net.minecraft.core.BlockPos(vault.chestX(), vault.chestY(), vault.chestZ()));
		if (entity instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
			return chest;
		}
		return null;
	}

	public PlayerVault vaultRegionAt(int x, int y, int z) {
		for (PlayerVault vault : vaults.values()) {
			if (vault.contains(x, y, z)) {
				return vault;
			}
		}
		return null;
	}

	private ServerLevel resolveLevel(String dimId) {
		try {
			ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimId));
			ServerLevel level = server.getLevel(key);
			if (level != null) {
				return level;
			}
		} catch (Exception ignored) {
		}
		return server.overworld();
	}

	private void ensureBuilt(ServerLevel level, PlayerVault vault) {
		BlockPos chest = new BlockPos(vault.chestX(), vault.chestY(), vault.chestZ());
		if (!level.getBlockState(chest).is(Blocks.CHEST)) {
			buildVault(level, vault);
		}
	}

	private void buildVault(ServerLevel level, PlayerVault vault) {
		int cx = vault.chestX();
		int cy = vault.chestY();
		int cz = vault.chestZ();
		BlockState wall = Blocks.BEDROCK.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();

		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				for (int y = -1; y <= 4; y++) {
					BlockPos pos = new BlockPos(cx + x, cy + y, cz + z);
					boolean shell = x == -3 || x == 3 || z == -3 || z == 3 || y == -1 || y == 4;
					level.setBlockAndUpdate(pos, shell ? wall : air);
				}
			}
		}
		level.setBlockAndUpdate(new BlockPos(cx - 2, cy + 3, cz - 2), Blocks.GLOWSTONE.defaultBlockState());
		level.setBlockAndUpdate(new BlockPos(cx + 2, cy + 3, cz + 2), Blocks.GLOWSTONE.defaultBlockState());
		level.setBlockAndUpdate(new BlockPos(cx, cy, cz), Blocks.CHEST.defaultBlockState());
		level.setBlockAndUpdate(new BlockPos(cx, cy - 1, cz), Blocks.BEDROCK.defaultBlockState());
	}
}
