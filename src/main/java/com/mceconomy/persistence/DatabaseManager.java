package com.mceconomy.persistence;

import com.mceconomy.McEconomyMod;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseManager implements AutoCloseable {
	private final Path dbPath;
	private Connection connection;

	public DatabaseManager(Path dbPath) {
		this.dbPath = dbPath;
	}

	public void open() throws SQLException {
		connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("PRAGMA journal_mode=WAL");
			stmt.execute("PRAGMA foreign_keys=ON");
		}
		new MigrationRunner(connection).runMigrations();
		McEconomyMod.LOGGER.info("SQLite veritabanı açıldı: {}", dbPath);
	}

	public Connection connection() {
		return connection;
	}

	public synchronized void execute(String sql) throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			stmt.execute(sql);
		}
	}

	@Override
	public void close() {
		if (connection != null) {
			try {
				connection.close();
			} catch (SQLException e) {
				McEconomyMod.LOGGER.error("Veritabanı kapatılamadı", e);
			}
		}
	}
}
