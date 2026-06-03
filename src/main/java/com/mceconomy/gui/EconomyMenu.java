package com.mceconomy.gui;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

public class EconomyMenu extends AbstractContainerMenu {
	private final Container container;
	private final MenuActionHandler actionHandler;

	public EconomyMenu(int syncId, Inventory playerInventory, Container container, MenuActionHandler actionHandler) {
		super(MenuType.GENERIC_9x3, syncId);
		this.container = container;
		this.actionHandler = actionHandler;
		checkContainerSize(container, 27);
		container.startOpen(playerInventory.player);

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new ReadOnlySlot(container, col + row * 9, 8 + col * 18, 18 + row * 18));
			}
		}
		addStandardInventorySlots(playerInventory, 8, 84);
	}

	@Override
	public void clicked(int slotId, int button, ContainerInput input, Player player) {
		if (slotId >= 0 && slotId < 27 && actionHandler != null && input == ContainerInput.PICKUP) {
			actionHandler.onSlotClick(slotId, button, player);
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		container.stopOpen(player);
	}

	public Container container() {
		return container;
	}

	@FunctionalInterface
	public interface MenuActionHandler {
		void onSlotClick(int slotId, int button, Player player);
	}
}
