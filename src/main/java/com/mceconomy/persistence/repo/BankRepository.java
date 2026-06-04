package com.mceconomy.persistence.repo;

import com.mceconomy.bank.BankAccount;
import com.mceconomy.bank.BankAccountType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class BankRepository {
	private final Connection connection;

	public BankRepository(Connection connection) {
		this.connection = connection;
	}

	public List<BankAccount> loadAll() throws SQLException {
		List<BankAccount> accounts = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM bank_accounts");
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				accounts.add(mapRow(rs));
			}
		}
		return accounts;
	}

	public void save(BankAccount account) throws SQLException {
		if (account.id() <= 0) {
			insert(account);
		} else {
			update(account);
		}
	}

	private void insert(BankAccount account) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO bank_accounts(owner_uuid, type, balance, interest_rate, matures_at)
				VALUES(?, ?, ?, ?, ?)
				""", PreparedStatement.RETURN_GENERATED_KEYS)) {
			ps.setString(1, account.ownerUuid().toString());
			ps.setString(2, account.type().name());
			ps.setLong(3, account.balance());
			ps.setDouble(4, account.interestRate());
			ps.setLong(5, account.maturesAt());
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				if (keys.next()) {
					account.setId(keys.getInt(1));
				}
			}
		}
	}

	private void update(BankAccount account) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				UPDATE bank_accounts SET balance=?, interest_rate=?, matures_at=? WHERE id=?
				""")) {
			ps.setLong(1, account.balance());
			ps.setDouble(2, account.interestRate());
			ps.setLong(3, account.maturesAt());
			ps.setInt(4, account.id());
			ps.executeUpdate();
		}
	}

	public Optional<BankAccount> findChecking(UUID owner) throws SQLException {
		return findByType(owner, BankAccountType.CHECKING);
	}

	public Optional<BankAccount> findTerm(UUID owner) throws SQLException {
		return findByType(owner, BankAccountType.TERM);
	}

	public Optional<BankAccount> findByType(UUID owner, BankAccountType type) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT * FROM bank_accounts WHERE owner_uuid = ? AND type = ?")) {
			ps.setString(1, owner.toString());
			ps.setString(2, type.name());
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return Optional.of(mapRow(rs));
				}
			}
		}
		return Optional.empty();
	}

	private BankAccount mapRow(ResultSet rs) throws SQLException {
		return new BankAccount(
				rs.getInt("id"),
				UUID.fromString(rs.getString("owner_uuid")),
				BankAccountType.valueOf(rs.getString("type")),
				rs.getLong("balance"),
				rs.getDouble("interest_rate"),
				rs.getLong("matures_at")
		);
	}

	public void delete(int accountId) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("DELETE FROM bank_accounts WHERE id = ?")) {
			ps.setInt(1, accountId);
			ps.executeUpdate();
		}
	}
}
