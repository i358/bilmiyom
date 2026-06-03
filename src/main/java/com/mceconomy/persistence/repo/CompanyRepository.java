package com.mceconomy.persistence.repo;

import com.mceconomy.company.Company;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class CompanyRepository {
	private final Connection connection;

	public CompanyRepository(Connection connection) {
		this.connection = connection;
	}

	public List<Company> loadAll() throws SQLException {
		List<Company> companies = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM companies");
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				companies.add(mapCompany(rs));
			}
		}
		return companies;
	}

	public Map<Integer, Map<UUID, Integer>> loadAllShares() throws SQLException {
		Map<Integer, Map<UUID, Integer>> shares = new HashMap<>();
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM shares");
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				int companyId = rs.getInt("company_id");
				UUID owner = UUID.fromString(rs.getString("owner_uuid"));
				int amount = rs.getInt("amount");
				shares.computeIfAbsent(companyId, k -> new HashMap<>()).put(owner, amount);
			}
		}
		return shares;
	}

	public void save(Company company) throws SQLException {
		if (company.id() <= 0) {
			insert(company);
		} else {
			update(company);
		}
	}

	public void saveShare(com.mceconomy.company.ShareHolding holding) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO shares(company_id, owner_uuid, amount)
				VALUES(?, ?, ?)
				ON CONFLICT(company_id, owner_uuid) DO UPDATE SET amount=excluded.amount
				""")) {
			ps.setInt(1, holding.companyId());
			ps.setString(2, holding.ownerUuid().toString());
			ps.setInt(3, holding.amount());
			ps.executeUpdate();
		}
	}

	public Optional<Company> findByName(String name) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM companies WHERE name = ?")) {
			ps.setString(1, name);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return Optional.of(mapCompany(rs));
				}
			}
		}
		return Optional.empty();
	}

	public Optional<Company> findByTicker(String ticker) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM companies WHERE ticker = ?")) {
			ps.setString(1, ticker.toUpperCase());
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return Optional.of(mapCompany(rs));
				}
			}
		}
		return Optional.empty();
	}

	private void insert(Company company) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO companies(name, owner_uuid, treasury, outstanding_shares, created_at,
					listed_on_exchange, ticker)
				VALUES(?, ?, ?, ?, ?, ?, ?)
				""", PreparedStatement.RETURN_GENERATED_KEYS)) {
			ps.setString(1, company.name());
			ps.setString(2, company.ownerUuid().toString());
			ps.setLong(3, company.treasury());
			ps.setInt(4, company.outstandingShares());
			ps.setLong(5, company.createdAt());
			ps.setInt(6, company.listedOnExchange() ? 1 : 0);
			ps.setString(7, company.ticker());
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				if (keys.next()) {
					company.setId(keys.getInt(1));
				}
			}
		}
	}

	private void update(Company company) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				UPDATE companies SET treasury=?, outstanding_shares=?, listed_on_exchange=?, ticker=? WHERE id=?
				""")) {
			ps.setLong(1, company.treasury());
			ps.setInt(2, company.outstandingShares());
			ps.setInt(3, company.listedOnExchange() ? 1 : 0);
			ps.setString(4, company.ticker());
			ps.setInt(5, company.id());
			ps.executeUpdate();
		}
	}

	private Company mapCompany(ResultSet rs) throws SQLException {
		return new Company(
				rs.getInt("id"),
				rs.getString("name"),
				UUID.fromString(rs.getString("owner_uuid")),
				rs.getLong("treasury"),
				rs.getInt("outstanding_shares"),
				rs.getLong("created_at"),
				getIntColumn(rs, "listed_on_exchange", 0) == 1,
				getStringColumn(rs, "ticker", null)
		);
	}

	private static int getIntColumn(ResultSet rs, String column, int defaultValue) {
		try {
			return rs.getInt(column);
		} catch (SQLException e) {
			return defaultValue;
		}
	}

	private static String getStringColumn(ResultSet rs, String column, String defaultValue) {
		try {
			String value = rs.getString(column);
			return value;
		} catch (SQLException e) {
			return defaultValue;
		}
	}
}
