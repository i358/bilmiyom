package com.mceconomy.persistence.repo;

import com.mceconomy.privatebank.PrivateBank;

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

public final class PrivateBankRepository {
	private final Connection connection;

	public PrivateBankRepository(Connection connection) {
		this.connection = connection;
	}

	public List<PrivateBank> loadAllBanks() throws SQLException {
		List<PrivateBank> banks = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM private_banks");
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				banks.add(mapBank(rs));
			}
		}
		return banks;
	}

	public Map<Integer, Map<UUID, Long>> loadAllDeposits() throws SQLException {
		Map<Integer, Map<UUID, Long>> deposits = new HashMap<>();
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM private_bank_deposits");
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				int bankId = rs.getInt("bank_id");
				UUID customer = UUID.fromString(rs.getString("customer_uuid"));
				long balance = rs.getLong("balance_mg");
				deposits.computeIfAbsent(bankId, k -> new HashMap<>()).put(customer, balance);
			}
		}
		return deposits;
	}

	public void saveBank(PrivateBank bank) throws SQLException {
		if (bank.id() <= 0) {
			insertBank(bank);
		} else {
			updateBank(bank);
		}
	}

	public void saveDeposit(int bankId, UUID customer, long balanceMg) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO private_bank_deposits(bank_id, customer_uuid, balance_mg)
				VALUES(?, ?, ?)
				ON CONFLICT(bank_id, customer_uuid) DO UPDATE SET balance_mg=excluded.balance_mg
				""")) {
			ps.setInt(1, bankId);
			ps.setString(2, customer.toString());
			ps.setLong(3, balanceMg);
			ps.executeUpdate();
		}
	}

	public Optional<PrivateBank> findByName(String name) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM private_banks WHERE name = ?")) {
			ps.setString(1, name);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return Optional.of(mapBank(rs));
				}
			}
		}
		return Optional.empty();
	}

	private void insertBank(PrivateBank bank) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO private_banks(name, owner_uuid, treasury_mg, interest_rate, created_at)
				VALUES(?, ?, ?, ?, ?)
				""", Statement.RETURN_GENERATED_KEYS)) {
			ps.setString(1, bank.name());
			ps.setString(2, bank.ownerUuid().toString());
			ps.setLong(3, bank.treasuryMg());
			ps.setDouble(4, bank.interestRate());
			ps.setLong(5, bank.createdAt());
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				if (keys.next()) {
					bank.setId(keys.getInt(1));
				}
			}
		}
	}

	private void updateBank(PrivateBank bank) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				UPDATE private_banks SET treasury_mg=?, interest_rate=? WHERE id=?
				""")) {
			ps.setLong(1, bank.treasuryMg());
			ps.setDouble(2, bank.interestRate());
			ps.setInt(3, bank.id());
			ps.executeUpdate();
		}
	}

	private PrivateBank mapBank(ResultSet rs) throws SQLException {
		return new PrivateBank(
				rs.getInt("id"),
				rs.getString("name"),
				UUID.fromString(rs.getString("owner_uuid")),
				rs.getLong("treasury_mg"),
				rs.getDouble("interest_rate"),
				rs.getLong("created_at")
		);
	}
}
