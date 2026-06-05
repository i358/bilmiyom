package com.mceconomy.facility;

import com.mceconomy.McEconomyMod;
import com.mceconomy.world.CentralBankPlacer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Merkez bankasi sandiklarinda fiziksel esya deposu. */
public final class FacilityDepotService {
	private static final int CHEST_SLOTS = 27;
	private static final int STACK_SIZE = 64;

	public static int maxCapacity(FacilityType type) {
		return CHEST_SLOTS * STACK_SIZE;
	}
	/** Gece oncesi: sandiktaki tum yiginlara MB seri numarasi (demir, bugday, altin vb.). */
	public int ensureAllDepotItemsSerialized(ServerLevel level) {
		var manager = McEconomyMod.getEconomyManager();
		if (manager == null || manager.bankAssetSerialRegistry() == null) {
			return 0;
		}
		var registry = manager.bankAssetSerialRegistry();
		int stamped = 0;
		for (FacilityType type : FacilityType.values()) {
			Container container = containerAt(level, type);
			if (container == null) {
				continue;
			}
			for (int slot = 0; slot < container.getContainerSize(); slot++) {
				ItemStack stack = container.getItem(slot);
				if (stack.isEmpty()) {
					continue;
				}
				if (FacilityItemTags.getSerial(stack) == null) {
					registry.assignSerial(stack, type);
					stamped++;
				} else {
					FacilityItemTags.markDepot(stack, type);
				}
				FacilityItemTags.applySerialDisplayName(stack);
				container.setItem(slot, stack);
			}
			markChestChanged(container);
		}
		return stamped;
	}

	public boolean deposit(ServerLevel level, FacilityType type, ItemStack stack) {
		if (stack.isEmpty()) {
			return true;
		}
		Container container = containerAt(level, type);
		if (container == null) {
			return false;
		}
		ItemStack copy = stack.copy();
		var manager = com.mceconomy.McEconomyMod.getEconomyManager();
		if (manager != null && manager.bankAssetSerialRegistry() != null) {
			manager.bankAssetSerialRegistry().assignSerial(copy, type);
		} else {
			FacilityItemTags.markDepot(copy, type);
		}
		return insert(container, copy);
	}

	public int depositItem(ServerLevel level, FacilityType type, Item item, int quantity) {
		if (quantity <= 0) {
			return 0;
		}
		int maxStack = new ItemStack(item).getMaxStackSize();
		int deposited = 0;
		int remaining = quantity;
		while (remaining > 0) {
			int chunk = Math.min(remaining, maxStack);
			ItemStack stack = new ItemStack(item, chunk);
			if (!deposit(level, type, stack)) {
				break;
			}
			deposited += chunk;
			remaining -= chunk;
		}
		return deposited;
	}

	/** Altin depodan seri numarali yiginlar halinde ceker. */
	public java.util.List<ItemStack> withdrawGoldIngots(ServerLevel level, int quantity) {
		java.util.List<ItemStack> out = new ArrayList<>();
		if (quantity <= 0) {
			return out;
		}
		Container container = containerAt(level, FacilityType.PHYSICAL_GOLD);
		if (container == null) {
			return out;
		}
		int taken = 0;
		for (int slot = 0; slot < container.getContainerSize() && taken < quantity; slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.isEmpty() || !stack.is(net.minecraft.world.item.Items.GOLD_INGOT)) {
				continue;
			}
			int remove = Math.min(stack.getCount(), quantity - taken);
			ItemStack withdrawn = stack.split(remove);
			container.setItem(slot, stack);
			FacilityItemTags.applySerialDisplayName(withdrawn);
			out.add(withdrawn);
			taken += remove;
		}
		return out;
	}

	public int withdrawItem(ServerLevel level, FacilityType type, Item item, int quantity) {
		int taken = 0;
		for (ItemStack stack : withdrawItemStacks(level, type, item, quantity)) {
			taken += stack.getCount();
		}
		return taken;
	}

	/** Seri numarali depo yiginlarini koruyarak ceker. */
	public List<ItemStack> withdrawItemStacks(ServerLevel level, FacilityType type, Item item, int quantity) {
		List<ItemStack> out = new ArrayList<>();
		Container container = containerAt(level, type);
		if (container == null || quantity <= 0) {
			return out;
		}
		int taken = 0;
		for (int slot = 0; slot < container.getContainerSize() && taken < quantity; slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.isEmpty() || !stack.is(item)) {
				continue;
			}
			int remove = Math.min(stack.getCount(), quantity - taken);
			ItemStack withdrawn = stack.split(remove);
			container.setItem(slot, stack);
			FacilityItemTags.applySerialDisplayName(withdrawn);
			out.add(withdrawn);
			taken += remove;
		}
		markChestChanged(container);
		return out;
	}

	public int countItem(ServerLevel level, FacilityType type, Item item) {
		Container container = containerAt(level, type);
		if (container == null) {
			return 0;
		}
		int total = 0;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	public int totalItemCount(ServerLevel level, FacilityType type) {
		Container container = containerAt(level, type);
		if (container == null) {
			return 0;
		}
		int total = 0;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			total += container.getItem(slot).getCount();
		}
		return total;
	}

	public int freeSlotCount(ServerLevel level, FacilityType type) {
		Container container = containerAt(level, type);
		if (container == null) {
			return 0;
		}
		int free = 0;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			if (container.getItem(slot).isEmpty()) {
				free++;
			}
		}
		return free;
	}

	public boolean isNearFull(ServerLevel level, FacilityType type, int reserveFreeStacks) {
		int maxKeep = maxCapacity(type) - reserveFreeStacks * STACK_SIZE;
		return totalItemCount(level, type) >= maxKeep;
	}

	public List<ItemStack> snapshot(ServerLevel level, FacilityType type) {
		List<ItemStack> list = new ArrayList<>();
		Container container = containerAt(level, type);
		if (container == null) {
			return list;
		}
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (!stack.isEmpty()) {
				list.add(stack.copy());
			}
		}
		return list;
	}

	public int snapshotHash(ServerLevel level, FacilityType type) {
		int hash = type.ordinal();
		for (ItemStack stack : snapshot(level, type)) {
			hash = 31 * hash + stack.getItem().hashCode();
			hash = 31 * hash + stack.getCount();
		}
		return hash;
	}

	public Optional<Container> openContainer(ServerLevel level, FacilityType type) {
		return Optional.ofNullable(containerAt(level, type));
	}

	private static boolean insert(Container container, ItemStack stack) {
		int remaining = stack.getCount();
		for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
			ItemStack existing = container.getItem(slot);
			if (existing.isEmpty()) {
				int put = Math.min(remaining, stack.getMaxStackSize());
				ItemStack placed = stack.copy();
				placed.setCount(put);
				container.setItem(slot, placed);
				remaining -= put;
			} else if (ItemStack.isSameItemSameComponents(existing, stack)
					&& existing.getCount() < existing.getMaxStackSize()) {
				int space = existing.getMaxStackSize() - existing.getCount();
				int put = Math.min(space, remaining);
				existing.grow(put);
				container.setItem(slot, existing);
				remaining -= put;
			}
		}
		return remaining == 0;
	}

	private static void markChestChanged(Container container) {
		if (container instanceof ChestBlockEntity chestEntity) {
			chestEntity.setChanged();
		}
	}

	private static Container containerAt(ServerLevel level, FacilityType type) {
		BlockPos pos = CentralBankPlacer.depotPos(type);
		if (pos == null) {
			return null;
		}
		if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
			McEconomyMod.LOGGER.warn("Depo sandigi bulunamadi: {} {}", type, pos);
			return null;
		}
		if (chest instanceof RandomizableContainerBlockEntity randomizable) {
			randomizable.unpackLootTable(null);
		}
		return chest;
	}
}
