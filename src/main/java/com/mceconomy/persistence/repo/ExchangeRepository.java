package com.mceconomy.persistence.repo;

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

	public void saveToken(ExchangeToken token) throws SQLException {
		if (token.id() <= 0) {
			insertToken(token);
		} else {
			updateToken(token);
		}
	}

	public void saveHolding(int tokenId, UUID owner, int amount) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO token_holdings(token_id, owner_uuid, amount)
				VALUES(?, ?, ?)
				ON CONFLICT(token_id, owner_uuid) DO UPDATE SET amount=excluded.amount
				""")) {
			ps.setInt(1, tokenId);
			ps.setString(2, owner.toString());
			ps.setInt(3, amount);
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
