package com.mceconomy.persistence.repo;

import com.mceconomy.vault.PlayerVault;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class VaultRepository {
	private final Connection connection;

	public VaultRepository(Connection connection) {
		this.connection = connection;
	}

	public List<PlayerVault> loadAll() throws SQLException {
		List<PlayerVault> list = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM player_vaults");
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				list.add(map(rs));
			}
		}
		return list;
	}

	public int nextIndex() throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("SELECT MAX(vault_index) FROM player_vaults");
			 ResultSet rs = ps.executeQuery()) {
			if (rs.next() && rs.getObject(1) != null) {
				return rs.getInt(1) + 1;
			}
		}
		return 0;
	}

	public void save(PlayerVault vault) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO player_vaults(owner_uuid, vault_index, chest_x, chest_y, chest_z,
					return_x, return_y, return_z, return_dim, created_at)
				VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT(owner_uuid) DO UPDATE SET
					return_x=excluded.return_x, return_y=excluded.return_y,
					return_z=excluded.return_z, return_dim=excluded.return_dim
				""")) {
			ps.setString(1, vault.ownerUuid().toString());
			ps.setInt(2, vault.vaultIndex());
			ps.setInt(3, vault.chestX());
			ps.setInt(4, vault.chestY());
			ps.setInt(5, vault.chestZ());
			setNullableDouble(ps, 6, vault.returnX());
			setNullableDouble(ps, 7, vault.returnY());
			setNullableDouble(ps, 8, vault.returnZ());
			if (vault.returnDim() != null) {
				ps.setString(9, vault.returnDim());
			} else {
				ps.setNull(9, java.sql.Types.VARCHAR);
			}
			ps.setLong(10, vault.createdAt());
			ps.executeUpdate();
		}
	}

	private static void setNullableDouble(PreparedStatement ps, int index, Double value) throws SQLException {
		if (value != null) {
			ps.setDouble(index, value);
		} else {
			ps.setNull(index, java.sql.Types.REAL);
		}
	}

	private PlayerVault map(ResultSet rs) throws SQLException {
		Double rx = (Double) rs.getObject("return_x");
		Double ry = (Double) rs.getObject("return_y");
		Double rz = (Double) rs.getObject("return_z");
		String dim = rs.getString("return_dim");
		return new PlayerVault(
				UUID.fromString(rs.getString("owner_uuid")),
				rs.getInt("vault_index"),
				rs.getInt("chest_x"),
				rs.getInt("chest_y"),
				rs.getInt("chest_z"),
				rx, ry, rz, dim,
				rs.getLong("created_at")
		);
	}
}
