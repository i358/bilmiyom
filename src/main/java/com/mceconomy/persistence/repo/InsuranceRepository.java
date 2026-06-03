package com.mceconomy.persistence.repo;

import com.mceconomy.insurance.InsurancePolicy;
import com.mceconomy.insurance.InsurancePolicy.PolicyType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class InsuranceRepository {
	private final Connection connection;

	public InsuranceRepository(Connection connection) {
		this.connection = connection;
	}

	public List<InsurancePolicy> loadAllActive() throws SQLException {
		List<InsurancePolicy> list = new ArrayList<>();
		try (Statement stmt = connection.createStatement();
			 ResultSet rs = stmt.executeQuery("SELECT * FROM insurance_policies WHERE active = 1")) {
			while (rs.next()) {
				list.add(map(rs));
			}
		}
		return list;
	}

	public InsurancePolicy find(UUID owner, PolicyType type, int companyId) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				SELECT * FROM insurance_policies WHERE owner_uuid = ? AND policy_type = ? AND company_id = ?
				""")) {
			ps.setString(1, owner.toString());
			ps.setString(2, type.name());
			ps.setInt(3, companyId);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? map(rs) : null;
			}
		}
	}

	public void save(InsurancePolicy policy) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO insurance_policies(owner_uuid, policy_type, company_id, active,
					coverage_percent, monthly_premium_mg, next_premium_due_ms)
				VALUES(?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT(owner_uuid, policy_type, company_id) DO UPDATE SET
					active=excluded.active,
					coverage_percent=excluded.coverage_percent,
					monthly_premium_mg=excluded.monthly_premium_mg,
					next_premium_due_ms=excluded.next_premium_due_ms
				""")) {
			ps.setString(1, policy.ownerUuid().toString());
			ps.setString(2, policy.type().name());
			ps.setInt(3, policy.companyId());
			ps.setInt(4, policy.active() ? 1 : 0);
			ps.setDouble(5, policy.coveragePercent());
			ps.setLong(6, policy.monthlyPremiumMg());
			ps.setLong(7, policy.nextPremiumDueMs());
			ps.executeUpdate();
		}
	}

	private InsurancePolicy map(ResultSet rs) throws SQLException {
		return new InsurancePolicy(
				UUID.fromString(rs.getString("owner_uuid")),
				PolicyType.valueOf(rs.getString("policy_type")),
				rs.getInt("company_id"),
				rs.getInt("active") == 1,
				rs.getDouble("coverage_percent"),
				rs.getLong("monthly_premium_mg"),
				rs.getLong("next_premium_due_ms"));
	}
}
