package com.mceconomy.blackmarket;

import com.mceconomy.McEconomyMod;
import com.mceconomy.persistence.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Admin'in ekledigi ozel karaborsa urunlerini tutar ve veritabaninda saklar. */
public final class CustomBlackMarketRegistry {
	private final DatabaseManager database;
	private final Map<String, CustomBlackMarketGood> goods = new LinkedHashMap<>();

	public CustomBlackMarketRegistry(DatabaseManager database) {
		this.database = database;
	}

	public void load() throws SQLException {
		goods.clear();
		try (PreparedStatement ps = database.connection().prepareStatement(
				"SELECT id, display_name, item_id, price_mg FROM custom_blackmarket");
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				CustomBlackMarketGood good = new CustomBlackMarketGood(
						rs.getString("id"), rs.getString("display_name"),
						rs.getString("item_id"), rs.getLong("price_mg"));
				goods.put(good.id(), good);
			}
		}
	}

	public List<CustomBlackMarketGood> all() {
		return new ArrayList<>(goods.values());
	}

	public CustomBlackMarketGood get(String id) {
		return id == null ? null : goods.get(id.toLowerCase(Locale.ROOT));
	}

	public CustomBlackMarketGood add(String displayName, String itemId, long priceMg) {
		String id = displayName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
		if (id.isEmpty()) {
			id = "urun_" + (goods.size() + 1);
		}
		CustomBlackMarketGood good = new CustomBlackMarketGood(id, displayName, itemId, priceMg);
		if (!good.valid()) {
			return null;
		}
		goods.put(id, good);
		try (PreparedStatement ps = database.connection().prepareStatement("""
				INSERT INTO custom_blackmarket(id, display_name, item_id, price_mg)
				VALUES(?, ?, ?, ?)
				ON CONFLICT(id) DO UPDATE SET display_name=excluded.display_name,
					item_id=excluded.item_id, price_mg=excluded.price_mg
				""")) {
			ps.setString(1, id);
			ps.setString(2, displayName);
			ps.setString(3, itemId);
			ps.setLong(4, priceMg);
			ps.executeUpdate();
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Ozel karaborsa urunu kaydedilemedi", e);
		}
		return good;
	}

	public boolean remove(String id) {
		if (id == null || goods.remove(id.toLowerCase(Locale.ROOT)) == null) {
			return false;
		}
		try (PreparedStatement ps = database.connection().prepareStatement(
				"DELETE FROM custom_blackmarket WHERE id = ?")) {
			ps.setString(1, id.toLowerCase(Locale.ROOT));
			ps.executeUpdate();
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Ozel karaborsa urunu silinemedi", e);
		}
		return true;
	}
}
