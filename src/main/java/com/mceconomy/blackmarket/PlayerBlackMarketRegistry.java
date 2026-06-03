package com.mceconomy.blackmarket;

import com.mceconomy.McEconomyMod;
import com.mceconomy.facility.FacilityItemTags;
import com.mceconomy.persistence.DatabaseManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Oyuncularin karaborsaya koydugu ilanlar. */
public final class PlayerBlackMarketRegistry {
	private final DatabaseManager database;
	private final List<PlayerBlackMarketListing> listings = new ArrayList<>();

	public PlayerBlackMarketRegistry(DatabaseManager database) {
		this.database = database;
	}

	public void load() throws SQLException {
		listings.clear();
		try (PreparedStatement ps = database.connection().prepareStatement(
				"SELECT id, seller_uuid, seller_name, item_id, display_name, price_mg, stock, stolen_stock FROM player_blackmarket WHERE stock > 0");
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				listings.add(new PlayerBlackMarketListing(
						rs.getLong("id"),
						UUID.fromString(rs.getString("seller_uuid")),
						rs.getString("seller_name"),
						rs.getString("item_id"),
						rs.getString("display_name"),
						rs.getLong("price_mg"),
						rs.getInt("stock"),
						rs.getInt("stolen_stock")));
			}
		} catch (SQLException e) {
			try (PreparedStatement ps = database.connection().prepareStatement(
					"SELECT id, seller_uuid, seller_name, item_id, display_name, price_mg, stock FROM player_blackmarket WHERE stock > 0");
				 ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					listings.add(new PlayerBlackMarketListing(
							rs.getLong("id"),
							UUID.fromString(rs.getString("seller_uuid")),
							rs.getString("seller_name"),
							rs.getString("item_id"),
							rs.getString("display_name"),
							rs.getLong("price_mg"),
							rs.getInt("stock")));
				}
			}
		}
	}

	public List<PlayerBlackMarketListing> all() {
		return List.copyOf(listings);
	}

	public Optional<PlayerBlackMarketListing> get(String catalogId) {
		if (catalogId == null || !catalogId.startsWith("player_")) {
			return Optional.empty();
		}
		try {
			long id = Long.parseLong(catalogId.substring("player_".length()));
			return listings.stream().filter(l -> l.id() == id).findFirst();
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	public Optional<PlayerBlackMarketListing> createListing(ServerPlayer seller, String itemId, int quantity, long priceMg) {
		if (quantity <= 0 || priceMg <= 0) {
			return Optional.empty();
		}
		Item item = resolveItem(itemId);
		if (item == Items.AIR) {
			return Optional.empty();
		}
		if (countItems(seller, item) < quantity) {
			return Optional.empty();
		}
		int stolenListed = countStolenItems(seller, item, quantity);
		removeItemsPreferStolen(seller, item, quantity);
		String displayName = new ItemStack(item).getHoverName().getString();
		try (PreparedStatement ps = database.connection().prepareStatement("""
				INSERT INTO player_blackmarket(seller_uuid, seller_name, item_id, display_name, price_mg, stock, stolen_stock)
				VALUES(?, ?, ?, ?, ?, ?, ?)
				""", Statement.RETURN_GENERATED_KEYS)) {
			ps.setString(1, seller.getUUID().toString());
			ps.setString(2, seller.getName().getString());
			ps.setString(3, normalizeItemId(itemId));
			ps.setString(4, displayName);
			ps.setLong(5, priceMg);
			ps.setInt(6, quantity);
			ps.setInt(7, stolenListed);
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				if (keys.next()) {
					long id = keys.getLong(1);
					PlayerBlackMarketListing listing = new PlayerBlackMarketListing(
							id, seller.getUUID(), seller.getName().getString(),
							normalizeItemId(itemId), displayName, priceMg, quantity, stolenListed);
					listings.add(listing);
					if (stolenListed > 0) {
						var masak = McEconomyMod.getEconomyManager().masakService();
						if (masak != null) {
							masak.onStolenGoodsBlackMarketSale(seller.getUUID(), stolenListed,
									priceMg * stolenListed);
						}
					}
					return Optional.of(listing);
				}
			}
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Karaborsa ilani kaydedilemedi", e);
			giveItems(seller, item, quantity);
		}
		return Optional.empty();
	}

	public boolean purchase(ServerPlayer buyer, PlayerBlackMarketListing listing, int quantity) {
		if (quantity <= 0 || listing.stock() < quantity) {
			return false;
		}
		if (listing.sellerUuid().equals(buyer.getUUID())) {
			return false;
		}
		Item item = resolveItem(listing.itemId());
		if (item == Items.AIR) {
			return false;
		}
		long cost = listing.priceMg() * quantity;
		var currency = McEconomyMod.getEconomyManager().currencyService();
		if (!currency.withdrawDirty(buyer.getUUID(), cost, com.mceconomy.economy.TransactionType.BLACK_MARKET_BUY)) {
			return false;
		}
		int stolenTake = Math.min(quantity, listing.stolenStock());
		ItemStack stack = new ItemStack(item, quantity);
		if (!buyer.getInventory().add(stack)) {
			currency.depositDirty(buyer.getUUID(), cost, com.mceconomy.economy.TransactionType.BLACK_MARKET_BUY);
			return false;
		}
		long payout = cost;
		currency.depositDirty(listing.sellerUuid(), payout, com.mceconomy.economy.TransactionType.BLACK_MARKET_SELL);
		listing.setStock(listing.stock() - quantity);
		listing.setStolenStock(Math.max(0, listing.stolenStock() - stolenTake));
		try (PreparedStatement ps = database.connection().prepareStatement(
				"UPDATE player_blackmarket SET stock = ?, stolen_stock = ? WHERE id = ?")) {
			ps.setInt(1, listing.stock());
			ps.setInt(2, listing.stolenStock());
			ps.setLong(3, listing.id());
			ps.executeUpdate();
			if (listing.stock() <= 0) {
				listings.remove(listing);
			}
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Ilan stok guncellenemedi", e);
		}
		var masak = McEconomyMod.getEconomyManager().masakService();
		masak.onBlackMarketActivity(listing.sellerUuid(), cost);
		if (stolenTake > 0) {
			masak.onStolenGoodsBlackMarketSale(listing.sellerUuid(), stolenTake, listing.priceMg() * stolenTake);
		}
		return true;
	}

	/** NPC alici — belediye butcesinden oder, satıcıya kirli para yatirilir. */
	public boolean npcPurchase(PlayerBlackMarketListing listing, int quantity) {
		if (quantity <= 0 || listing.stock() < quantity) {
			return false;
		}
		Item item = resolveItem(listing.itemId());
		if (item == Items.AIR) {
			return false;
		}
		long cost = listing.priceMg() * quantity;
		var currency = McEconomyMod.getEconomyManager().currencyService();
		currency.depositDirty(listing.sellerUuid(), cost, com.mceconomy.economy.TransactionType.BLACK_MARKET_SELL);
		listing.setStock(listing.stock() - quantity);
		try (PreparedStatement ps = database.connection().prepareStatement(
				"UPDATE player_blackmarket SET stock = ? WHERE id = ?")) {
			ps.setInt(1, listing.stock());
			ps.setLong(2, listing.id());
			ps.executeUpdate();
			if (listing.stock() <= 0) {
				listings.remove(listing);
			}
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("NPC ilan alimi basarisiz", e);
			return false;
		}
		return true;
	}

	public Item resolveItem(String itemId) {
		try {
			String full = itemId.contains(":") ? itemId : "minecraft:" + itemId;
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(full));
			return item != null ? item : Items.AIR;
		} catch (Exception e) {
			return Items.AIR;
		}
	}

	private static String normalizeItemId(String itemId) {
		return itemId.toLowerCase(Locale.ROOT);
	}

	private static int countItems(ServerPlayer player, Item item) {
		int total = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static void removeItems(ServerPlayer player, Item item, int quantity) {
		int remaining = quantity;
		for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item)) {
				int take = Math.min(stack.getCount(), remaining);
				stack.shrink(take);
				remaining -= take;
			}
		}
	}

	private static void giveItems(ServerPlayer player, Item item, int quantity) {
		player.getInventory().add(new ItemStack(item, quantity));
	}

	private static int countStolenItems(ServerPlayer player, Item item, int maxQuantity) {
		int stolen = 0;
		for (int i = 0; i < player.getInventory().getContainerSize() && stolen < maxQuantity; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item) && FacilityItemTags.matchesWantedSerial(stack)) {
				stolen += Math.min(stack.getCount(), maxQuantity - stolen);
			}
		}
		return stolen;
	}

	private static void removeItemsPreferStolen(ServerPlayer player, Item item, int quantity) {
		int remaining = quantity;
		for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item) && FacilityItemTags.matchesWantedSerial(stack)) {
				int take = Math.min(stack.getCount(), remaining);
				stack.shrink(take);
				remaining -= take;
			}
		}
		for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item)) {
				int take = Math.min(stack.getCount(), remaining);
				stack.shrink(take);
				remaining -= take;
			}
		}
	}
}
