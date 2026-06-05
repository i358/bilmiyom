package com.mceconomy.persistence.repo;

import com.mceconomy.market.MarketItemState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public final class MarketItemRepository {
	private final Connection connection;

	public MarketItemRepository(Connection connection) {
		this.connection = connection;
	}

	public Map<String, MarketItemState> loadAll() throws SQLException {
		Map<String, MarketItemState> states = new HashMap<>();
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM market_item_state");
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				String itemId = rs.getString("item_id");
				states.put(itemId, new MarketItemState(
						itemId,
						rs.getDouble("price"),
						rs.getDouble("base_price"),
						rs.getDouble("supply_index"),
						rs.getDouble("demand_index")
				));
			}
		}
		return states;
	}

	public void save(MarketItemState state) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO market_item_state(item_id, price, base_price, supply_index, demand_index)
				VALUES(?, ?, ?, ?, ?)
				ON CONFLICT(item_id) DO UPDATE SET
					price=excluded.price,
					base_price=excluded.base_price,
					supply_index=excluded.supply_index,
					demand_index=excluded.demand_index
				""")) {
			ps.setString(1, state.itemId());
			ps.setDouble(2, state.price());
			ps.setDouble(3, state.basePrice());
			ps.setDouble(4, state.supplyIndex());
			ps.setDouble(5, state.demandIndex());
			ps.executeUpdate();
		}
	}

	public void saveAll(Map<String, MarketItemState> states) throws SQLException {
		for (MarketItemState state : states.values()) {
			save(state);
		}
	}
}
