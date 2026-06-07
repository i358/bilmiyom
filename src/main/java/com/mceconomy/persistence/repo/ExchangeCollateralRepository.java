package com.mceconomy.persistence.repo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class ExchangeCollateralRepository {
	private final Connection connection;

	public ExchangeCollateralRepository(Connection connection) {
		this.connection = connection;
	}

	public long getBalanceMg(UUID player) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT balance_mg FROM exchange_collateral WHERE player_uuid = ?")) {
			ps.setString(1, player.toString());
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return Math.max(0, rs.getLong("balance_mg"));
				}
			}
		}
		return 0L;
	}

	public void setBalanceMg(UUID player, long balanceMg) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO exchange_collateral(player_uuid, balance_mg)
				VALUES(?, ?)
				ON CONFLICT(player_uuid) DO UPDATE SET balance_mg = excluded.balance_mg
				""")) {
			ps.setString(1, player.toString());
			ps.setLong(2, Math.max(0, balanceMg));
			ps.executeUpdate();
		}
	}
}
