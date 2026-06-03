package com.mceconomy.economy;

import com.mceconomy.persistence.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public final class TransactionLedger {
	private final DatabaseManager database;

	public TransactionLedger(DatabaseManager database) {
		this.database = database;
	}

	public void record(UUID from, UUID to, long amount, TransactionType type, String metadata) {
		try (PreparedStatement ps = database.connection().prepareStatement("""
				INSERT INTO transactions(from_uuid, to_uuid, amount, type, timestamp, metadata_json)
				VALUES(?, ?, ?, ?, ?, ?)
				""")) {
			ps.setString(1, from != null ? from.toString() : null);
			ps.setString(2, to != null ? to.toString() : null);
			ps.setLong(3, amount);
			ps.setString(4, type.name());
			ps.setLong(5, System.currentTimeMillis());
			ps.setString(6, metadata);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new RuntimeException("Transaction kaydedilemedi", e);
		}
	}
}
