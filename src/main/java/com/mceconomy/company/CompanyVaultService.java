package com.mceconomy.company;

import com.mceconomy.McEconomyMod;
import com.mceconomy.market.Commodity;
import com.mceconomy.persistence.repo.CompanyVaultRepository;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class CompanyVaultService {
	public record VaultItem(String itemId, String displayName, int quantity) {
	}

	private static final int BASE_X = 3_000_000;
	private static final int VAULT_Y = -55;
	private static final int VAULT_Z = 128;
	private static final int SPACING = 20;

	private final Map<Integer, CompanyVault> vaultsByCompany = new HashMap<>();
	private final CompanyVaultRepository repository;
	private final CompanyManager companyManager;
	private final MinecraftServer server;
	private int nextIndex;

	public CompanyVaultService(CompanyVaultRepository repository, CompanyManager companyManager,
			MinecraftServer server) {
		this.repository = repository;
		this.companyManager = companyManager;
		this.server = server;
	}

	public void load() throws SQLException {
		vaultsByCompany.clear();
		vaultsByCompany.putAll(repository.loadAll());
		nextIndex = repository.nextIndex();
	}

	public CompanyVault getVault(int companyId) {
		return vaultsByCompany.get(companyId);
	}

	public java.util.Collection<CompanyVault> allVaultsForReset() {
		return java.util.List.copyOf(vaultsByCompany.values());
	}

	public CompanyVault vaultRegionAt(int x, int y, int z) {
		for (CompanyVault vault : vaultsByCompany.values()) {
			if (vault.contains(x, y, z)) {
				return vault;
			}
		}
		return null;
	}

	public CompanyVault ensureVault(Company company) throws SQLException {
		CompanyVault existing = vaultsByCompany.get(company.id());
		if (existing != null) {
			return existing;
		}
		int index = nextIndex++;
		int cx = BASE_X + index * SPACING;
		CompanyVault vault = new CompanyVault(company.id(), company.ownerUuid(), index, cx, VAULT_Y, VAULT_Z,
				null, null, null, null, System.currentTimeMillis());
		buildVault(server.overworld(), vault);
		vaultsByCompany.put(company.id(), vault);
		repository.save(vault);
		return vault;
	}

	public int depositItem(Company company, Item item, int quantity) throws SQLException {
		if (quantity <= 0 || item == null) {
			return 0;
		}
		CompanyVault vault = ensureVault(company);
		ServerLevel level = server.overworld();
		ensureBuilt(level, vault);
		BlockEntity entity = level.getBlockEntity(new BlockPos(vault.chestX(), vault.chestY(), vault.chestZ()));
		if (!(entity instanceof ChestBlockEntity chest)) {
			return 0;
		}
		int remaining = quantity;
		for (int slot = 0; slot < chest.getContainerSize() && remaining > 0; slot++) {
			ItemStack stack = chest.getItem(slot);
			if (stack.isEmpty()) {
				int put = Math.min(remaining, item.getDefaultMaxStackSize());
				chest.setItem(slot, new ItemStack(item, put));
				remaining -= put;
			} else if (stack.is(item) && stack.getCount() < stack.getMaxStackSize()) {
				int space = stack.getMaxStackSize() - stack.getCount();
				int put = Math.min(remaining, space);
				stack.grow(put);
				remaining -= put;
			}
		}
		chest.setChanged();
		return quantity - remaining;
	}

	public boolean isNearFull(Company company) {
		CompanyVault vault = vaultsByCompany.get(company.id());
		if (vault == null) {
			return false;
		}
		ServerLevel level = server.overworld();
		ensureBuilt(level, vault);
		BlockEntity entity = level.getBlockEntity(new BlockPos(vault.chestX(), vault.chestY(), vault.chestZ()));
		if (!(entity instanceof ChestBlockEntity chest)) {
			return false;
		}
		int freeStacks = com.mceconomy.config.EconomyConfig.companyVaultReserveFreeStacks();
		int freeSlots = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (chest.getItem(i).isEmpty()) {
				freeSlots++;
			}
		}
		return freeSlots <= freeStacks;
	}

	public void liquidateIfFull(Company company, BiConsumer<Item, Integer> seller) {
		if (!isNearFull(company)) {
			return;
		}
		CompanyVault vault = vaultsByCompany.get(company.id());
		if (vault == null) {
			return;
		}
		ServerLevel level = server.overworld();
		ensureBuilt(level, vault);
		BlockEntity entity = level.getBlockEntity(new BlockPos(vault.chestX(), vault.chestY(), vault.chestZ()));
		if (!(entity instanceof ChestBlockEntity chest)) {
			return;
		}
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			ItemStack stack = chest.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			Commodity commodity = Commodity.fromItem(stack.getItem());
			if (commodity == null || !commodity.sellable()) {
				continue;
			}
			int amount = stack.getCount();
			chest.setItem(slot, ItemStack.EMPTY);
			seller.accept(stack.getItem(), amount);
		}
		chest.setChanged();
	}

	public List<VaultItem> listContents(int companyId) {
		CompanyVault vault = vaultsByCompany.get(companyId);
		if (vault == null) {
			return List.of();
		}
		ServerLevel level = server.overworld();
		ensureBuilt(level, vault);
		BlockEntity entity = level.getBlockEntity(new BlockPos(vault.chestX(), vault.chestY(), vault.chestZ()));
		if (!(entity instanceof ChestBlockEntity chest)) {
			return List.of();
		}
		Map<String, VaultItem> merged = new HashMap<>();
		for (int i = 0; i < chest.getContainerSize(); i++) {
			ItemStack stack = chest.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			String name = stack.getHoverName().getString();
			merged.merge(id, new VaultItem(id, name, stack.getCount()),
					(a, b) -> new VaultItem(id, a.displayName(), a.quantity() + b.quantity()));
		}
		return new ArrayList<>(merged.values());
	}

	public List<VaultItem> listContentsForOwner(UUID ownerUuid, String companyName) {
		return companyManager.find(companyName)
				.filter(c -> c.ownerUuid().equals(ownerUuid))
				.map(c -> listContents(c.id()))
				.orElse(List.of());
	}

	public boolean teleportToVault(ServerPlayer player, String companyName) {
		Company company = companyManager.find(companyName).orElse(null);
		if (company == null || !company.ownerUuid().equals(player.getUUID())) {
			return false;
		}
		try {
			CompanyVault vault = ensureVault(company);
			ServerLevel overworld = server.overworld();
			ensureBuilt(overworld, vault);
			vault.setReturn(player.getX(), player.getY(), player.getZ(),
					player.level().dimension().identifier().toString());
			repository.save(vault);
			player.teleportTo(overworld, vault.chestX() + 1.5, vault.chestY(), vault.chestZ() + 0.5,
					java.util.Set.of(), player.getYRot(), player.getXRot(), false);
			return true;
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Sirket sandigi isinlanmasi basarisiz", e);
			return false;
		}
	}

	public boolean teleportBack(ServerPlayer player) {
		for (CompanyVault vault : vaultsByCompany.values()) {
			if (!vault.ownerUuid().equals(player.getUUID()) || !vault.hasReturn()) {
				continue;
			}
			ServerLevel level = resolveLevel(vault.returnDim());
			player.teleportTo(level, vault.returnX(), vault.returnY(), vault.returnZ(),
					java.util.Set.of(), player.getYRot(), player.getXRot(), false);
			vault.clearReturn();
			try {
				repository.save(vault);
			} catch (SQLException e) {
				McEconomyMod.LOGGER.error("Sirket sandigi donus kaydi basarisiz", e);
			}
			return true;
		}
		return false;
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

	private void ensureBuilt(ServerLevel level, CompanyVault vault) {
		BlockPos chest = new BlockPos(vault.chestX(), vault.chestY(), vault.chestZ());
		if (!level.getBlockState(chest).is(Blocks.CHEST)) {
			buildVault(level, vault);
		}
	}

	private void buildVault(ServerLevel level, CompanyVault vault) {
		int cx = vault.chestX();
		int cy = vault.chestY();
		int cz = vault.chestZ();
		BlockState steel = Blocks.IRON_BLOCK.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();

		for (int x = -3; x <= 3; x++) {
			for (int z = -3; z <= 3; z++) {
				for (int y = -1; y <= 4; y++) {
					BlockPos pos = new BlockPos(cx + x, cy + y, cz + z);
					boolean shell = x == -3 || x == 3 || z == -3 || z == 3 || y == -1 || y == 4;
					level.setBlockAndUpdate(pos, shell ? steel : air);
				}
			}
		}
		level.setBlockAndUpdate(new BlockPos(cx - 2, cy + 3, cz - 2), Blocks.GLOWSTONE.defaultBlockState());
		level.setBlockAndUpdate(new BlockPos(cx + 2, cy + 3, cz + 2), Blocks.GLOWSTONE.defaultBlockState());
		level.setBlockAndUpdate(new BlockPos(cx, cy, cz), Blocks.CHEST.defaultBlockState());
		level.setBlockAndUpdate(new BlockPos(cx, cy - 1, cz), Blocks.IRON_BLOCK.defaultBlockState());
	}
}
