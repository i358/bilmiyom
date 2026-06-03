package com.mceconomy.persistence.repo;

import com.mceconomy.company.CompanyVault;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CompanyVaultRepository {
	private final Connection connection;

	public CompanyVaultRepository(Connection connection) {
		this.connection = connection;
	}

	public Map<Integer, CompanyVault> loadAll() throws SQLException {
		Map<Integer, CompanyVault> map = new HashMap<>();
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM company_vaults");
				ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				CompanyVault vault = map(rs);
				map.put(vault.companyId(), vault);
			}
		}
		return map;
	}

	public int nextIndex() throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("SELECT MAX(vault_index) FROM company_vaults");
				ResultSet rs = ps.executeQuery()) {
			if (rs.next() && rs.getObject(1) != null) {
				return rs.getInt(1) + 1;
			}
		}
		return 0;
	}

	public void save(CompanyVault vault) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO company_vaults(company_id, owner_uuid, vault_index, chest_x, chest_y, chest_z,
					return_x, return_y, return_z, return_dim, created_at)
				VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT(company_id) DO UPDATE SET
					return_x=excluded.return_x, return_y=excluded.return_y,
					return_z=excluded.return_z, return_dim=excluded.return_dim
				""")) {
			ps.setInt(1, vault.companyId());
			ps.setString(2, vault.ownerUuid().toString());
			ps.setInt(3, vault.vaultIndex());
			ps.setInt(4, vault.chestX());
			ps.setInt(5, vault.chestY());
			ps.setInt(6, vault.chestZ());
			setNullableDouble(ps, 7, vault.returnX());
			setNullableDouble(ps, 8, vault.returnY());
			setNullableDouble(ps, 9, vault.returnZ());
			if (vault.returnDim() != null) {
				ps.setString(10, vault.returnDim());
			} else {
				ps.setNull(10, java.sql.Types.VARCHAR);
			}
			ps.setLong(11, vault.createdAt());
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

	private CompanyVault map(ResultSet rs) throws SQLException {
		Double rx = (Double) rs.getObject("return_x");
		Double ry = (Double) rs.getObject("return_y");
		Double rz = (Double) rs.getObject("return_z");
		return new CompanyVault(
				rs.getInt("company_id"),
				UUID.fromString(rs.getString("owner_uuid")),
				rs.getInt("vault_index"),
				rs.getInt("chest_x"),
				rs.getInt("chest_y"),
				rs.getInt("chest_z"),
				rx, ry, rz,
				rs.getString("return_dim"),
				rs.getLong("created_at"));
	}
}
