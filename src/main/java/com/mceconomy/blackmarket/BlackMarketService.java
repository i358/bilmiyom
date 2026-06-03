package com.mceconomy.blackmarket;

import com.mceconomy.config.EconomyConfig;
import com.mceconomy.facility.FacilityDepotService;
import com.mceconomy.facility.FacilityItemTags;
import com.mceconomy.facility.FacilityType;
import com.mceconomy.security.SecurityWeapon;
import com.mceconomy.economy.CurrencyService;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.economy.TransactionType;
import com.mceconomy.market.MarketService;
import com.mceconomy.regulation.MasakService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class BlackMarketService {
	private final CurrencyService currencyService;
	private final MarketService marketService;
	private final MasakService masakService;
	private FacilityDepotService depotService;

	public BlackMarketService(CurrencyService currencyService, MarketService marketService, MasakService masakService) {
		this.currencyService = currencyService;
		this.marketService = marketService;
		this.masakService = masakService;
	}

	public long getSellPrice(IllegalGood good) {
		long legal = marketService.priceEngine().getUnitPrice(good.priceReference());
		long freeMarket = Math.max(good.basePriceMg(), legal);
		return (long) (freeMarket * EconomyConfig.blackMarketSellMultiplier());
	}

	public void bindDepot(FacilityDepotService depotService) {
		this.depotService = depotService;
	}

	public long getWeaponBuyPrice(SecurityWeapon weapon) {
		return (long) (weapon.bonusDamage() * 500_000 * EconomyConfig.blackMarketBuyPremium());
	}

	public long getWeaponSellPrice(SecurityWeapon weapon) {
		return (long) (weapon.bonusDamage() * 400_000 * EconomyConfig.blackMarketSellMultiplier());
	}

	public boolean buyWeapon(ServerPlayer player, SecurityWeapon weapon) {
		long cost = getWeaponBuyPrice(weapon);
		if (!currencyService.withdrawDirty(player.getUUID(), cost, TransactionType.BLACK_MARKET_BUY)) {
			return false;
		}
		ItemStack stack = new ItemStack(weapon.item());
		stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
				net.minecraft.network.chat.Component.literal(weapon.displayName()));
		if (!player.getInventory().add(stack)) {
			currencyService.depositDirty(player.getUUID(), cost, TransactionType.BLACK_MARKET_BUY);
			return false;
		}
		masakService.onBlackMarketActivity(player.getUUID(), cost);
		return true;
	}

	public long getBuyPrice(IllegalGood good) {
		long sell = getSellPrice(good);
		return (long) (sell * EconomyConfig.blackMarketBuyPremium());
	}

	public boolean sell(ServerPlayer player, IllegalGood good, int quantity) {
		if (quantity <= 0) {
			return false;
		}
		if (countItems(player, good) < quantity) {
			return false;
		}
		int stolenSold = countStolenItems(player, good.item(), quantity);
		long payout = getSellPrice(good) * quantity;
		if (!currencyService.depositDirty(player.getUUID(), payout, TransactionType.BLACK_MARKET_SELL)) {
			return false;
		}
		removeItems(player, good, quantity);
		depositToBlackMarketDepot(player, good.item(), quantity);
		masakService.onBlackMarketActivity(player.getUUID(), payout);
		if (stolenSold > 0) {
			masakService.onStolenGoodsBlackMarketSale(player.getUUID(), stolenSold,
					getSellPrice(good) * stolenSold);
		}
		return true;
	}

	public boolean buy(ServerPlayer player, IllegalGood good, int quantity) {
		if (quantity <= 0) {
			return false;
		}
		long cost = getBuyPrice(good) * quantity;
		if (!currencyService.withdrawDirty(player.getUUID(), cost, TransactionType.BLACK_MARKET_BUY)) {
			return false;
		}
		int taken = withdrawFromBlackMarketDepot(player, good.item(), quantity);
		ItemStack stack = new ItemStack(good.item(), quantity);
		if (!player.getInventory().add(stack)) {
			currencyService.depositDirty(player.getUUID(), cost, TransactionType.BLACK_MARKET_BUY);
			if (taken > 0 && depotService != null) {
				depositToBlackMarketDepot(player, good.item(), taken);
			}
			return false;
		}
		masakService.onBlackMarketActivity(player.getUUID(), cost);
		return true;
	}

	public boolean sellCustom(ServerPlayer player, CustomBlackMarketGood good, int quantity) {
		if (quantity <= 0 || !good.valid()) {
			return false;
		}
		int remaining = quantity;
		int total = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(good.resolveItem())) {
				total += stack.getCount();
			}
		}
		if (total < quantity) {
			return false;
		}
		int stolenSold = countStolenItems(player, good.resolveItem(), quantity);
		long payout = (long) (good.priceMg() * EconomyConfig.blackMarketSellMultiplier()) * quantity;
		if (!currencyService.depositDirty(player.getUUID(), payout, TransactionType.BLACK_MARKET_SELL)) {
			return false;
		}
		for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(good.resolveItem())) {
				int take = Math.min(stack.getCount(), remaining);
				stack.shrink(take);
				remaining -= take;
			}
		}
		depositToBlackMarketDepot(player, good.resolveItem(), quantity);
		masakService.onBlackMarketActivity(player.getUUID(), payout);
		if (stolenSold > 0) {
			masakService.onStolenGoodsBlackMarketSale(player.getUUID(), stolenSold,
					(long) (good.priceMg() * EconomyConfig.blackMarketSellMultiplier()) * stolenSold);
		}
		return true;
	}

	public boolean buyCustom(ServerPlayer player, CustomBlackMarketGood good, int quantity) {
		if (quantity <= 0 || !good.valid()) {
			return false;
		}
		long cost = (long) (good.priceMg() * EconomyConfig.blackMarketBuyPremium()) * quantity;
		if (!currencyService.withdrawDirty(player.getUUID(), cost, TransactionType.BLACK_MARKET_BUY)) {
			return false;
		}
		ItemStack stack = new ItemStack(good.resolveItem(), quantity);
		if (!player.getInventory().add(stack)) {
			currencyService.depositDirty(player.getUUID(), cost, TransactionType.BLACK_MARKET_BUY);
			return false;
		}
		masakService.onBlackMarketActivity(player.getUUID(), cost);
		return true;
	}

	private int countItems(ServerPlayer player, IllegalGood good) {
		int total = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(good.item())) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private int removeItems(ServerPlayer player, IllegalGood good, int quantity) {
		int remaining = quantity;
		for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(good.item())) {
				int take = Math.min(stack.getCount(), remaining);
				stack.shrink(take);
				remaining -= take;
			}
		}
		return quantity - remaining;
	}

	private int countStolenItems(ServerPlayer player, net.minecraft.world.item.Item item, int maxQuantity) {
		int stolen = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize() && stolen < maxQuantity; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(item) && FacilityItemTags.matchesWantedSerial(stack)) {
				stolen += Math.min(stack.getCount(), maxQuantity - stolen);
			}
		}
		return stolen;
	}

	public String formatPrice(long mg) {
		return GoldStandard.formatMilligrams(mg) + " (kara para)";
	}

	private void depositToBlackMarketDepot(ServerPlayer player, net.minecraft.world.item.Item item, int quantity) {
		if (depotService == null || quantity <= 0) {
			return;
		}
		depotService.depositItem((ServerLevel) player.level(), FacilityType.BLACK_MARKET, item, quantity);
	}

	private int withdrawFromBlackMarketDepot(ServerPlayer player, net.minecraft.world.item.Item item, int quantity) {
		if (depotService == null) {
			return 0;
		}
		return depotService.withdrawItem((ServerLevel) player.level(), FacilityType.BLACK_MARKET, item, quantity);
	}
}
