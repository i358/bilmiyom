package com.mceconomy.persistence.repo;

import com.mceconomy.market.Commodity;
import com.mceconomy.market.CommodityState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;

public final class MarketRepository {
	private final Connection connection;

	public MarketRepository(Connection connection) {
		this.connection = connection;
	}

	public Map<Commodity, CommodityState> loadAll() throws SQLException {
		Map<Commodity, CommodityState> states = new EnumMap<>(Commodity.class);
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM market_state");
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				Commodity commodity = Commodity.valueOf(rs.getString("commodity"));
				states.put(commodity, new CommodityState(
						commodity,
						rs.getDouble("price"),
						rs.getDouble("base_price"),
						rs.getDouble("supply_index"),
						rs.getDouble("demand_index")
				));
			}
		}
		return states;
	}

	public void save(CommodityState state) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO market_state(commodity, price, base_price, supply_index, demand_index)
				VALUES(?, ?, ?, ?, ?)
				ON CONFLICT(commodity) DO UPDATE SET
					price=excluded.price,
					base_price=excluded.base_price,
					supply_index=excluded.supply_index,
					demand_index=excluded.demand_index
				""")) {
			ps.setString(1, state.commodity().name());
			ps.setDouble(2, state.price());
			ps.setDouble(3, state.basePrice());
			ps.setDouble(4, state.supplyIndex());
			ps.setDouble(5, state.demandIndex());
			ps.executeUpdate();
		}
	}

	public void saveAll(Map<Commodity, CommodityState> states) throws SQLException {
		for (CommodityState state : states.values()) {
			save(state);
		}
	}
}
