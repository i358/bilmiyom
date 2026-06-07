package com.mceconomy.persistence.repo;

import com.mceconomy.exchange.ExchangeLimitOrder;
import com.mceconomy.exchange.ExchangeToken;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ExchangeRepository {
	private final Connection connection;

	public ExchangeRepository(Connection connection) {
		this.connection = connection;
	}

	public List<ExchangeToken> loadAllTokens() throws SQLException {
		List<ExchangeToken> tokens = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM exchange_tokens");
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				tokens.add(mapToken(rs));
			}
		}
		return tokens;
	}

	public Map<Integer, Map<UUID, Integer>> loadAllHoldings() throws SQLException {
		Map<Integer, Map<UUID, Integer>> holdings = new HashMap<>();
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM token_holdings");
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				int tokenId = rs.getInt("token_id");
				UUID owner = UUID.fromString(rs.getString("owner_uuid"));
				int amount = rs.getInt("amount");
				holdings.computeIfAbsent(tokenId, k -> new HashMap<>()).put(owner, amount);
			}
		}
		return holdings;
	}

	public Map<Integer, Map<UUID, Long>> loadAllCostBasis() throws SQLException {
		Map<Integer, Map<UUID, Long>> basis = new HashMap<>();
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM token_holdings");
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				int tokenId = rs.getInt("token_id");
				UUID owner = UUID.fromString(rs.getString("owner_uuid"));
				long cost = getLongColumn(rs, "cost_basis_mg", 0);
				if (cost > 0) {
					basis.computeIfAbsent(tokenId, k -> new HashMap<>()).put(owner, cost);
				}
			}
		}
		return basis;
	}

	public void saveToken(ExchangeToken token) throws SQLException {
		if (token.id() <= 0) {
			insertToken(token);
		} else {
			updateToken(token);
		}
	}

	public void saveHolding(int tokenId, UUID owner, int amount) throws SQLException {
		saveHolding(tokenId, owner, amount, 0);
	}

	public void saveHolding(int tokenId, UUID owner, int amount, long costBasisMg) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO token_holdings(token_id, owner_uuid, amount, cost_basis_mg)
				VALUES(?, ?, ?, ?)
				ON CONFLICT(token_id, owner_uuid) DO UPDATE SET
					amount=excluded.amount, cost_basis_mg=excluded.cost_basis_mg
				""")) {
			ps.setInt(1, tokenId);
			ps.setString(2, owner.toString());
			ps.setInt(3, amount);
			ps.setLong(4, Math.max(0, costBasisMg));
			ps.executeUpdate();
		}
	}

	public List<ExchangeLimitOrder> loadOpenLimitOrders() throws SQLException {
		List<ExchangeLimitOrder> orders = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT * FROM exchange_limit_orders WHERE open = 1 ORDER BY created_at");
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				orders.add(mapLimitOrder(rs));
			}
		}
		return orders;
	}

	public int insertLimitOrder(ExchangeLimitOrder order) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO exchange_limit_orders(owner_uuid, symbol, is_buy, amount, limit_price_mg, created_at, open)
				VALUES(?, ?, ?, ?, ?, ?, 1)
				""", Statement.RETURN_GENERATED_KEYS)) {
			ps.setString(1, order.owner().toString());
			ps.setString(2, order.symbol());
			ps.setInt(3, order.isBuy() ? 1 : 0);
			ps.setInt(4, order.amount());
			ps.setLong(5, order.limitPriceMg());
			ps.setLong(6, order.createdAt());
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				if (keys.next()) {
					return keys.getInt(1);
				}
			}
		}
		return -1;
	}

	public void cancelLimitOrder(int id) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement(
				"UPDATE exchange_limit_orders SET open = 0 WHERE id = ?")) {
			ps.setInt(1, id);
			ps.executeUpdate();
		}
	}

	public Optional<ExchangeToken> findBySymbol(String symbol) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM exchange_tokens WHERE symbol = ?")) {
			ps.setString(1, symbol.toUpperCase());
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return Optional.of(mapToken(rs));
				}
			}
		}
		return Optional.empty();
	}

	private void insertToken(ExchangeToken token) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO exchange_tokens(symbol, display_name, creator_uuid, total_supply, circulating,
					price_mg, treasury_mg, created_at)
				VALUES(?, ?, ?, ?, ?, ?, ?, ?)
				""", Statement.RETURN_GENERATED_KEYS)) {
			ps.setString(1, token.symbol());
			ps.setString(2, token.displayName());
			ps.setString(3, token.creatorUuid().toString());
			ps.setInt(4, token.totalSupply());
			ps.setInt(5, token.circulating());
			ps.setLong(6, token.priceMg());
			ps.setLong(7, token.treasuryMg());
			ps.setLong(8, token.createdAt());
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				if (keys.next()) {
					token.setId(keys.getInt(1));
				}
			}
		}
	}

	private void updateToken(ExchangeToken token) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				UPDATE exchange_tokens SET circulating=?, price_mg=?, treasury_mg=? WHERE id=?
				""")) {
			ps.setInt(1, token.circulating());
			ps.setLong(2, token.priceMg());
			ps.setLong(3, token.treasuryMg());
			ps.setInt(4, token.id());
			ps.executeUpdate();
		}
	}

	private ExchangeToken mapToken(ResultSet rs) throws SQLException {
		return new ExchangeToken(
				rs.getInt("id"),
				rs.getString("symbol"),
				rs.getString("display_name"),
				UUID.fromString(rs.getString("creator_uuid")),
				rs.getInt("total_supply"),
				rs.getInt("circulating"),
				rs.getLong("price_mg"),
				rs.getLong("treasury_mg"),
				rs.getLong("created_at")
		);
	}

	private ExchangeLimitOrder mapLimitOrder(ResultSet rs) throws SQLException {
		return new ExchangeLimitOrder(
				rs.getInt("id"),
				UUID.fromString(rs.getString("owner_uuid")),
				rs.getString("symbol"),
				rs.getInt("is_buy") == 1,
				rs.getInt("amount"),
				rs.getLong("limit_price_mg"),
				rs.getLong("created_at"),
				rs.getInt("open") == 1);
	}

	private static long getLongColumn(ResultSet rs, String column, long defaultValue) {
		try {
			return rs.getLong(column);
		} catch (SQLException e) {
			return defaultValue;
		}
	}

	public void deleteToken(int tokenId) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("DELETE FROM token_holdings WHERE token_id = ?")) {
			ps.setInt(1, tokenId);
			ps.executeUpdate();
		}
		try (PreparedStatement ps = connection.prepareStatement("DELETE FROM exchange_tokens WHERE id = ?")) {
			ps.setInt(1, tokenId);
			ps.executeUpdate();
		}
	}
}
