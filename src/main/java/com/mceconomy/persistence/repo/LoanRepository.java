package com.mceconomy.persistence.repo;

import com.mceconomy.bank.LoanRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class LoanRepository {
	private final Connection connection;

	public LoanRepository(Connection connection) {
		this.connection = connection;
	}

	public Map<UUID, LoanRecord> loadAll() throws SQLException {
		Map<UUID, LoanRecord> loans = new HashMap<>();
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM loans");
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				LoanRecord loan = mapRow(rs);
				loans.put(loan.borrowerUuid(), loan);
			}
		}
		return loans;
	}

	public void save(LoanRecord loan) throws SQLException {
		if (loan.id() <= 0) {
			insert(loan);
		} else {
			update(loan);
		}
	}

	public void delete(UUID borrower) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("DELETE FROM loans WHERE borrower_uuid = ?")) {
			ps.setString(1, borrower.toString());
			ps.executeUpdate();
		}
	}

	public Optional<LoanRecord> find(UUID borrower) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM loans WHERE borrower_uuid = ?")) {
			ps.setString(1, borrower.toString());
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return Optional.of(mapRow(rs));
				}
			}
		}
		return Optional.empty();
	}

	private void insert(LoanRecord loan) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO loans(borrower_uuid, principal, remaining, installment, due_at, late_interest, interest_rate)
				VALUES(?, ?, ?, ?, ?, ?, ?)
				""", PreparedStatement.RETURN_GENERATED_KEYS)) {
			ps.setString(1, loan.borrowerUuid().toString());
			ps.setLong(2, loan.principal());
			ps.setLong(3, loan.remaining());
			ps.setLong(4, loan.installment());
			ps.setLong(5, loan.dueAt());
			ps.setDouble(6, loan.lateInterest());
			ps.setDouble(7, loan.interestRate());
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				if (keys.next()) {
					loan.setId(keys.getInt(1));
				}
			}
		}
	}

	private void update(LoanRecord loan) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				UPDATE loans SET remaining=?, installment=?, due_at=?, late_interest=?, interest_rate=? WHERE id=?
				""")) {
			ps.setLong(1, loan.remaining());
			ps.setLong(2, loan.installment());
			ps.setLong(3, loan.dueAt());
			ps.setDouble(4, loan.lateInterest());
			ps.setDouble(5, loan.interestRate());
			ps.setInt(6, loan.id());
			ps.executeUpdate();
		}
	}

	private LoanRecord mapRow(ResultSet rs) throws SQLException {
		return new LoanRecord(
				rs.getInt("id"),
				UUID.fromString(rs.getString("borrower_uuid")),
				rs.getLong("principal"),
				rs.getLong("remaining"),
				rs.getLong("installment"),
				rs.getLong("due_at"),
				rs.getDouble("late_interest"),
				rs.getDouble("interest_rate")
		);
	}
}
