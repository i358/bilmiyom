package com.mceconomy.persistence.repo;

import com.mceconomy.exchange.LeveragePosition;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LeverageRepository {
	private final Connection connection;

	public LeverageRepository(Connection connection) {
		this.connection = connection;
	}

	public List<LeveragePosition> loadOpen() throws SQLException {
		List<LeveragePosition> list = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM leverage_positions WHERE open = 1");
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				list.add(new LeveragePosition(
						rs.getInt("id"),
						UUID.fromString(rs.getString("owner_uuid")),
						rs.getString("symbol"),
						rs.getInt("is_long") == 1,
						rs.getInt("leverage"),
						rs.getLong("margin_mg"),
						rs.getLong("entry_price_mg"),
						rs.getLong("size_milli_tokens"),
						rs.getLong("opened_at"),
						true));
			}
		}
		return list;
	}

	public int insert(LeveragePosition pos) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO leverage_positions(owner_uuid, symbol, is_long, leverage, margin_mg,
					entry_price_mg, size_milli_tokens, opened_at, open)
				VALUES(?, ?, ?, ?, ?, ?, ?, ?, 1)
				""", Statement.RETURN_GENERATED_KEYS)) {
			ps.setString(1, pos.owner().toString());
			ps.setString(2, pos.symbol());
			ps.setInt(3, pos.isLong() ? 1 : 0);
			ps.setInt(4, pos.leverage());
			ps.setLong(5, pos.marginMg());
			ps.setLong(6, pos.entryPriceMg());
			ps.setLong(7, pos.sizeMilliTokens());
			ps.setLong(8, pos.openedAt());
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				if (keys.next()) {
					return keys.getInt(1);
				}
			}
		}
		return -1;
	}

	public void markClosed(int id) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement(
				"UPDATE leverage_positions SET open = 0 WHERE id = ?")) {
			ps.setInt(1, id);
			ps.executeUpdate();
		}
	}
}
