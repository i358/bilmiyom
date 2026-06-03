package com.mceconomy.persistence;

import com.mceconomy.McEconomyMod;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class MigrationRunner {
	private final Connection connection;

	public MigrationRunner(Connection connection) {
		this.connection = connection;
	}

	public void runMigrations() throws SQLException {
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS schema_version (
						version INTEGER PRIMARY KEY
					)
					""");
		}

		int current = getCurrentVersion();
		if (current < 1) {
			migrateV1();
			setVersion(1);
		}
		if (current < 2) {
			migrateV2();
			setVersion(2);
		}
		if (current < 3) {
			migrateV3();
			setVersion(3);
		}
		if (current < 4) {
			migrateV4();
			setVersion(4);
		}
		if (current < 5) {
			migrateV5();
			setVersion(5);
		}
		if (current < 6) {
			migrateV6();
			setVersion(6);
		}
		if (current < 7) {
			migrateV7();
			setVersion(7);
		}
		if (current < 8) {
			migrateV8();
			setVersion(8);
		}
		if (current < 9) {
			migrateV9();
			setVersion(9);
		}
		if (current < 10) {
			migrateV10();
			setVersion(10);
		}
		if (current < 11) {
			migrateV11();
			setVersion(11);
		}
		if (current < 12) {
			migrateV12();
			setVersion(12);
		}
		if (current < 13) {
			migrateV13();
			setVersion(13);
		}
		if (current < 14) {
			migrateV14();
			setVersion(14);
		}
		if (current < 15) {
			migrateV15();
			setVersion(15);
		}
		if (current < 16) {
			migrateV16();
			setVersion(16);
		}
		if (current < 17) {
			migrateV17();
			setVersion(17);
		}
		if (current < 18) {
			migrateV18();
			setVersion(18);
		}
		if (current < 19) {
			migrateV19();
			setVersion(19);
		}
		if (current < 20) {
			migrateV20();
			setVersion(20);
		}
		if (current < 21) {
			migrateV21();
			setVersion(21);
		}
		if (current < 22) {
			migrateV22();
			setVersion(22);
		}
	}

	private void migrateV22() throws SQLException {
		McEconomyMod.LOGGER.info("Migration V22: dashboard sifre sutunlari...");
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("ALTER TABLE players ADD COLUMN dashboard_password_hash TEXT");
		} catch (SQLException ignored) {
		}
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("ALTER TABLE players ADD COLUMN dashboard_password_salt TEXT");
		} catch (SQLException ignored) {
		}
	}

	private int getCurrentVersion() throws SQLException {
		try (Statement stmt = connection.createStatement();
			 ResultSet rs = stmt.executeQuery("SELECT MAX(version) FROM schema_version")) {
			return rs.next() && rs.getObject(1) != null ? rs.getInt(1) : 0;
		}
	}

	private void setVersion(int version) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("INSERT INTO schema_version(version) VALUES(?)")) {
			ps.setInt(1, version);
			ps.executeUpdate();
		}
	}

	private void migrateV1() throws SQLException {
		McEconomyMod.LOGGER.info("Migration V1 uygulanıyor...");
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS players (
						uuid TEXT PRIMARY KEY,
						name TEXT NOT NULL,
						coin_balance INTEGER NOT NULL DEFAULT 0,
						credit_score INTEGER NOT NULL DEFAULT 650,
						job_type TEXT,
						last_tax_at INTEGER NOT NULL DEFAULT 0
					)
					""");
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS bank_accounts (
						id INTEGER PRIMARY KEY AUTOINCREMENT,
						owner_uuid TEXT NOT NULL,
						type TEXT NOT NULL,
						balance INTEGER NOT NULL DEFAULT 0,
						interest_rate REAL NOT NULL DEFAULT 0,
						matures_at INTEGER NOT NULL DEFAULT 0,
						UNIQUE(owner_uuid, type)
					)
					""");
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS transactions (
						id INTEGER PRIMARY KEY AUTOINCREMENT,
						from_uuid TEXT,
						to_uuid TEXT,
						amount INTEGER NOT NULL,
						type TEXT NOT NULL,
						timestamp INTEGER NOT NULL,
						metadata_json TEXT
					)
					""");
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS market_state (
						commodity TEXT PRIMARY KEY,
						price REAL NOT NULL,
						base_price REAL NOT NULL,
						supply_index REAL NOT NULL DEFAULT 0,
						demand_index REAL NOT NULL DEFAULT 0
					)
					""");
		}
	}

	private void migrateV2() throws SQLException {
		McEconomyMod.LOGGER.info("Migration V2 uygulanıyor...");
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS loans (
						id INTEGER PRIMARY KEY AUTOINCREMENT,
						borrower_uuid TEXT NOT NULL UNIQUE,
						principal INTEGER NOT NULL,
						remaining INTEGER NOT NULL,
						installment INTEGER NOT NULL,
						due_at INTEGER NOT NULL,
						late_interest REAL NOT NULL DEFAULT 0,
						interest_rate REAL NOT NULL DEFAULT 0.05
					)
					""");
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS central_bank (
						id INTEGER PRIMARY KEY CHECK (id = 1),
						base_rate REAL NOT NULL DEFAULT 0.05,
						money_supply INTEGER NOT NULL DEFAULT 0,
						inflation_rate REAL NOT NULL DEFAULT 0,
						economy_index REAL NOT NULL DEFAULT 100
					)
					""");
			stmt.execute("INSERT OR IGNORE INTO central_bank(id) VALUES(1)");
		}
	}

	private void migrateV3() throws SQLException {
		McEconomyMod.LOGGER.info("Migration V3 uygulanıyor...");
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS companies (
						id INTEGER PRIMARY KEY AUTOINCREMENT,
						name TEXT NOT NULL UNIQUE,
						owner_uuid TEXT NOT NULL,
						treasury INTEGER NOT NULL DEFAULT 0,
						outstanding_shares INTEGER NOT NULL DEFAULT 100,
						created_at INTEGER NOT NULL
					)
					""");
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS shares (
						company_id INTEGER NOT NULL,
						owner_uuid TEXT NOT NULL,
						amount INTEGER NOT NULL DEFAULT 0,
						PRIMARY KEY (company_id, owner_uuid),
						FOREIGN KEY (company_id) REFERENCES companies(id)
					)
					""");
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS active_quests (
						id INTEGER PRIMARY KEY AUTOINCREMENT,
						player_uuid TEXT NOT NULL,
						quest_type TEXT NOT NULL,
						target TEXT NOT NULL,
						required_amount INTEGER NOT NULL,
						progress INTEGER NOT NULL DEFAULT 0,
						reward INTEGER NOT NULL,
						active INTEGER NOT NULL DEFAULT 1
					)
					""");
		}
	}

	private void migrateV4() throws SQLException {
		McEconomyMod.LOGGER.info("Migration V4 uygulanıyor: market fiyatları altın standardına sıfırlanıyor...");
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("DELETE FROM market_state");
		}
	}

	private void migrateV5() throws SQLException {
		McEconomyMod.LOGGER.info("Migration V5 uygulanıyor: MASAK, kara para, karaborsa...");
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("ALTER TABLE players ADD COLUMN dirty_balance INTEGER NOT NULL DEFAULT 0");
			stmt.execute("ALTER TABLE players ADD COLUMN account_frozen INTEGER NOT NULL DEFAULT 0");
			stmt.execute("ALTER TABLE players ADD COLUMN blacklisted INTEGER NOT NULL DEFAULT 0");
		} catch (SQLException ignored) {
			// Sütunlar zaten varsa devam et
		}
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS masak_alerts (
						id INTEGER PRIMARY KEY AUTOINCREMENT,
						player_uuid TEXT NOT NULL,
						reason TEXT NOT NULL,
						risk_score INTEGER NOT NULL,
						amount INTEGER NOT NULL DEFAULT 0,
						resolved INTEGER NOT NULL DEFAULT 0,
						created_at INTEGER NOT NULL
					)
					""");
		}
	}

	private void migrateV6() throws SQLException {
		McEconomyMod.LOGGER.info("Migration V6: borsa, özel bankalar, itirazlar...");
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("ALTER TABLE players ADD COLUMN bank_certified INTEGER NOT NULL DEFAULT 0");
		} catch (SQLException ignored) {
		}
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("ALTER TABLE companies ADD COLUMN listed_on_exchange INTEGER NOT NULL DEFAULT 0");
			stmt.execute("ALTER TABLE companies ADD COLUMN ticker TEXT");
		} catch (SQLException ignored) {
		}
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS exchange_tokens (
						id INTEGER PRIMARY KEY AUTOINCREMENT,
						symbol TEXT NOT NULL UNIQUE,
						display_name TEXT NOT NULL,
						creator_uuid TEXT NOT NULL,
						total_supply INTEGER NOT NULL,
						circulating INTEGER NOT NULL,
						price_mg INTEGER NOT NULL,
						treasury_mg INTEGER NOT NULL DEFAULT 0,
						created_at INTEGER NOT NULL
					)
					""");
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS token_holdings (
						token_id INTEGER NOT NULL,
						owner_uuid TEXT NOT NULL,
						amount INTEGER NOT NULL DEFAULT 0,
						PRIMARY KEY (token_id, owner_uuid)
					)
					""");
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS private_banks (
						id INTEGER PRIMARY KEY AUTOINCREMENT,
						name TEXT NOT NULL UNIQUE,
						owner_uuid TEXT NOT NULL,
						treasury_mg INTEGER NOT NULL DEFAULT 0,
						interest_rate REAL NOT NULL DEFAULT 0.03,
						created_at INTEGER NOT NULL
					)
					""");
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS private_bank_deposits (
						bank_id INTEGER NOT NULL,
						customer_uuid TEXT NOT NULL,
						balance_mg INTEGER NOT NULL DEFAULT 0,
						PRIMARY KEY (bank_id, customer_uuid)
					)
					""");
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS appeals (
						id INTEGER PRIMARY KEY AUTOINCREMENT,
						player_uuid TEXT NOT NULL,
						player_name TEXT NOT NULL,
						subject TEXT NOT NULL,
						message TEXT NOT NULL,
						related_alert_id INTEGER,
						status TEXT NOT NULL DEFAULT 'OPEN',
						admin_note TEXT,
						created_at INTEGER NOT NULL,
						resolved_at INTEGER
					)
					""");
		}
	}

	private void migrateV7() throws SQLException {
		McEconomyMod.LOGGER.info("Migration V7: dashboard, mb yetkili, fiyat geçmişi...");
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("ALTER TABLE players ADD COLUMN central_bank_official INTEGER NOT NULL DEFAULT 0");
			stmt.execute("ALTER TABLE players ADD COLUMN dashboard_password_hash TEXT");
			stmt.execute("ALTER TABLE players ADD COLUMN dashboard_password_salt TEXT");
		} catch (SQLException ignored) {
		}
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS price_history (
						id INTEGER PRIMARY KEY AUTOINCREMENT,
						symbol_type TEXT NOT NULL,
						symbol TEXT NOT NULL,
						price_mg INTEGER NOT NULL,
						recorded_at INTEGER NOT NULL
					)
					""");
			stmt.execute("CREATE INDEX IF NOT EXISTS idx_price_history_symbol ON price_history(symbol_type, symbol, recorded_at)");
		}
	}

	private void migrateV8() throws SQLException {
		McEconomyMod.LOGGER.info("Migration V8: sirket calisanlari ve is basvurulari...");
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS job_applications (
						id INTEGER PRIMARY KEY AUTOINCREMENT,
						company_id INTEGER NOT NULL,
						npc_name TEXT NOT NULL,
						role_id TEXT NOT NULL,
						requested_salary_mg INTEGER NOT NULL,
						message TEXT NOT NULL,
						status TEXT NOT NULL DEFAULT 'PENDING',
						applied_at INTEGER NOT NULL,
						entity_uuid TEXT,
						FOREIGN KEY (company_id) REFERENCES companies(id)
					)
					""");
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS company_employees (
						id INTEGER PRIMARY KEY AUTOINCREMENT,
						company_id INTEGER NOT NULL,
						npc_name TEXT NOT NULL,
						role_id TEXT NOT NULL,
						salary_mg INTEGER NOT NULL,
						hired_at INTEGER NOT NULL,
						last_paid_at INTEGER NOT NULL,
						total_produced_mg INTEGER NOT NULL DEFAULT 0,
						FOREIGN KEY (company_id) REFERENCES companies(id)
					)
					""");
		}
	}

	private void migrateV9() throws SQLException {
		McEconomyMod.LOGGER.info("Migration V9: kisiye ozel yer alti kasalari...");
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS player_vaults (
						owner_uuid TEXT PRIMARY KEY,
						vault_index INTEGER NOT NULL,
						chest_x INTEGER NOT NULL,
						chest_y INTEGER NOT NULL,
						chest_z INTEGER NOT NULL,
						return_x REAL,
						return_y REAL,
						return_z REAL,
						return_dim TEXT,
						created_at INTEGER NOT NULL
					)
					""");
		}
	}

	private void migrateV16() throws SQLException {
		McEconomyMod.LOGGER.info("Migration V16: belediye, lonca, takas, maas gecmisi...");
		try (Statement stmt = connection.createStatement()) {
			try {
				stmt.execute("ALTER TABLE central_bank ADD COLUMN municipal_budget_mg INTEGER NOT NULL DEFAULT 0");
			} catch (SQLException ignored) {
				// column may exist
			}
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS salary_payments(
						id INTEGER PRIMARY KEY AUTOINCREMENT,
						player_uuid TEXT NOT NULL,
						player_name TEXT NOT NULL,
						company_id INTEGER NOT NULL,
						amount_mg INTEGER NOT NULL,
						bonus_mg INTEGER NOT NULL DEFAULT 0,
						paid_at INTEGER NOT NULL
					)
					""");
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS guilds(
						id INTEGER PRIMARY KEY AUTOINCREMENT,
						name TEXT NOT NULL UNIQUE,
						leader_uuid TEXT NOT NULL,
						treasury_mg INTEGER NOT NULL DEFAULT 0,
						strike_active INTEGER NOT NULL DEFAULT 0,
						strike_until INTEGER NOT NULL DEFAULT 0,
						bargain_message TEXT NOT NULL DEFAULT '',
						created_at INTEGER NOT NULL
					)
					""");
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS guild_members(
						guild_id INTEGER NOT NULL,
						player_uuid TEXT NOT NULL UNIQUE,
						player_name TEXT NOT NULL,
						role TEXT NOT NULL DEFAULT 'MEMBER',
						joined_at INTEGER NOT NULL,
						PRIMARY KEY (guild_id, player_uuid)
					)
					""");
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS player_trades(
						id INTEGER PRIMARY KEY AUTOINCREMENT,
						initiator_uuid TEXT NOT NULL,
						initiator_name TEXT NOT NULL,
						partner_uuid TEXT NOT NULL,
						partner_name TEXT NOT NULL,
						initiator_gold_mg INTEGER NOT NULL DEFAULT 0,
						partner_gold_mg INTEGER NOT NULL DEFAULT 0,
						initiator_items_json TEXT NOT NULL DEFAULT '[]',
						partner_items_json TEXT NOT NULL DEFAULT '[]',
						initiator_ready INTEGER NOT NULL DEFAULT 0,
						partner_ready INTEGER NOT NULL DEFAULT 0,
						status TEXT NOT NULL,
						completed_at INTEGER NOT NULL DEFAULT 0,
						created_at INTEGER NOT NULL
					)
					""");
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS trade_disputes(
						id INTEGER PRIMARY KEY AUTOINCREMENT,
						trade_id INTEGER NOT NULL,
						reporter_uuid TEXT NOT NULL,
						reporter_name TEXT NOT NULL,
						target_uuid TEXT NOT NULL,
						target_name TEXT NOT NULL,
						reason TEXT NOT NULL,
						status TEXT NOT NULL DEFAULT 'OPEN',
						admin_note TEXT,
						resolved_by TEXT,
						resolved_at INTEGER NOT NULL DEFAULT 0,
						created_at INTEGER NOT NULL
					)
					""");
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS tip_rewards(
						id INTEGER PRIMARY KEY AUTOINCREMENT,
						report_id INTEGER NOT NULL UNIQUE,
						reporter_uuid TEXT NOT NULL,
						amount_mg INTEGER NOT NULL,
						paid_at INTEGER NOT NULL
					)
					""");
		}
	}

	private void migrateV17() throws SQLException {
		McEconomyMod.LOGGER.info("Migration V17: sirket uretim deposu...");
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS company_stash(
						company_id INTEGER NOT NULL,
						commodity_id TEXT NOT NULL,
						quantity INTEGER NOT NULL DEFAULT 0,
						PRIMARY KEY (company_id, commodity_id)
					)
					""");
		}
	}

	private void migrateV21() throws SQLException {
		McEconomyMod.LOGGER.info("Migration V21: guvenlik kamerasi, karaborsa calinti stogu...");
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS security_camera_logs(
						id INTEGER PRIMARY KEY AUTOINCREMENT,
						night_key TEXT NOT NULL,
						player_uuid TEXT NOT NULL,
						player_name TEXT NOT NULL,
						x INTEGER NOT NULL,
						y INTEGER NOT NULL,
						z INTEGER NOT NULL,
						recorded_at INTEGER NOT NULL
					)
					""");
			try {
				stmt.execute("ALTER TABLE player_blackmarket ADD COLUMN stolen_stock INTEGER NOT NULL DEFAULT 0");
			} catch (SQLException ignored) {
				// column may exist
			}
		}
	}

	private void migrateV20() throws SQLException {
		McEconomyMod.LOGGER.info("Migration V20: sigorta, belediye secimi...");
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS insurance_policies(
						owner_uuid TEXT NOT NULL,
						policy_type TEXT NOT NULL,
						company_id INTEGER NOT NULL DEFAULT 0,
						active INTEGER NOT NULL DEFAULT 0,
						coverage_percent REAL NOT NULL,
						monthly_premium_mg INTEGER NOT NULL,
						next_premium_due_ms INTEGER NOT NULL,
						PRIMARY KEY(owner_uuid, policy_type, company_id)
					)
					""");
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS municipal_state(
						id INTEGER PRIMARY KEY,
						mayor_uuid TEXT,
						mayor_name TEXT,
						term_end_ms INTEGER NOT NULL DEFAULT 0,
						election_start_ms INTEGER NOT NULL DEFAULT 0
					)
					""");
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS municipal_votes(
						term_id INTEGER NOT NULL,
						voter_uuid TEXT NOT NULL,
						candidate_uuid TEXT NOT NULL,
						PRIMARY KEY(term_id, voter_uuid)
					)
					""");
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS municipal_candidates(
						term_id INTEGER NOT NULL,
						candidate_uuid TEXT NOT NULL,
						candidate_name TEXT NOT NULL,
						PRIMARY KEY(term_id, candidate_uuid)
					)
					""");
		}
	}

	private void migrateV19() throws SQLException {
		McEconomyMod.LOGGER.info("Migration V19: ulusal rezerv, depo defteri, ekonomi bulteni...");
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS national_reserve(
						item_id TEXT PRIMARY KEY,
						quantity INTEGER NOT NULL DEFAULT 0
					)
					""");
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS depot_ledger(
						ledger_key TEXT PRIMARY KEY,
						value_int INTEGER NOT NULL DEFAULT 0
					)
					""");
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS economy_bulletins(
						id INTEGER PRIMARY KEY AUTOINCREMENT,
						category TEXT NOT NULL,
						headline TEXT NOT NULL,
						body TEXT NOT NULL,
						value_mg INTEGER NOT NULL DEFAULT 0,
						created_at INTEGER NOT NULL
					)
					""");
		}
	}

	private void migrateV18() throws SQLException {
		McEconomyMod.LOGGER.info("Migration V18: sirket gizli sandigi...");
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS company_vaults(
						company_id INTEGER PRIMARY KEY,
						owner_uuid TEXT NOT NULL,
						vault_index INTEGER NOT NULL,
						chest_x INTEGER NOT NULL,
						chest_y INTEGER NOT NULL,
						chest_z INTEGER NOT NULL,
						return_x REAL,
						return_y REAL,
						return_z REAL,
						return_dim TEXT,
						created_at INTEGER NOT NULL
					)
					""");
		}
	}

	private void migrateV15() throws SQLException {
		McEconomyMod.LOGGER.info("Migration V15: oyuncu istihdam ve maas...");
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS player_job_applications(
						id INTEGER PRIMARY KEY AUTOINCREMENT,
						company_id INTEGER NOT NULL,
						player_uuid TEXT NOT NULL,
						player_name TEXT NOT NULL,
						role_id TEXT NOT NULL,
						requested_salary_mg INTEGER NOT NULL,
						message TEXT NOT NULL,
						status TEXT NOT NULL,
						applied_at INTEGER NOT NULL,
						FOREIGN KEY (company_id) REFERENCES companies(id)
					)
					""");
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS player_employments(
						id INTEGER PRIMARY KEY AUTOINCREMENT,
						player_uuid TEXT NOT NULL UNIQUE,
						player_name TEXT NOT NULL,
						company_id INTEGER NOT NULL,
						role_id TEXT NOT NULL,
						salary_mg INTEGER NOT NULL,
						hired_at INTEGER NOT NULL,
						last_paid_at INTEGER NOT NULL,
						FOREIGN KEY (company_id) REFERENCES companies(id)
					)
					""");
		}
	}

	private void migrateV14() throws SQLException {
		McEconomyMod.LOGGER.info("Migration V14: sikayet, ihbar ve hapishane...");
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS citizen_reports(
						id INTEGER PRIMARY KEY AUTOINCREMENT,
						type TEXT NOT NULL,
						reporter_uuid TEXT NOT NULL,
						reporter_name TEXT NOT NULL,
						target_uuid TEXT,
						target_name TEXT,
						category TEXT NOT NULL,
						subject TEXT NOT NULL,
						message TEXT NOT NULL,
						status TEXT NOT NULL,
						admin_note TEXT,
						prison_sentence_id INTEGER,
						created_at INTEGER NOT NULL,
						resolved_at INTEGER NOT NULL DEFAULT 0
					)
					""");
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS prison_sentences(
						id INTEGER PRIMARY KEY AUTOINCREMENT,
						player_uuid TEXT NOT NULL,
						player_name TEXT NOT NULL,
						reason TEXT NOT NULL,
						sentenced_by TEXT NOT NULL,
						jailed_at INTEGER NOT NULL,
						release_at INTEGER NOT NULL,
						active INTEGER NOT NULL,
						return_x REAL,
						return_y REAL,
						return_z REAL,
						return_dimension TEXT,
						cell_index INTEGER NOT NULL
					)
					""");
		}
	}

	private void migrateV13() throws SQLException {
		McEconomyMod.LOGGER.info("Migration V13: oyuncu karaborsa ilanlari...");
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS player_blackmarket(
						id INTEGER PRIMARY KEY AUTOINCREMENT,
						seller_uuid TEXT NOT NULL,
						seller_name TEXT NOT NULL,
						item_id TEXT NOT NULL,
						display_name TEXT NOT NULL,
						price_mg INTEGER NOT NULL,
						stock INTEGER NOT NULL
					)
					""");
		}
	}

	private void migrateV12() throws SQLException {
		McEconomyMod.LOGGER.info("Migration V12: ozel karaborsa urunleri...");
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS custom_blackmarket(
						id TEXT PRIMARY KEY,
						display_name TEXT NOT NULL,
						item_id TEXT NOT NULL,
						price_mg INTEGER NOT NULL
					)
					""");
		}
	}

	private void migrateV11() throws SQLException {
		McEconomyMod.LOGGER.info("Migration V11: MC para birimi - altin deger faktoru...");
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("ALTER TABLE central_bank ADD COLUMN gold_factor REAL NOT NULL DEFAULT 1.0");
		} catch (SQLException ignored) {
		}
	}

	private void migrateV10() throws SQLException {
		McEconomyMod.LOGGER.info("Migration V10: kaldiracli borsa pozisyonlari...");
		try (Statement stmt = connection.createStatement()) {
			stmt.execute("""
					CREATE TABLE IF NOT EXISTS leverage_positions (
						id INTEGER PRIMARY KEY AUTOINCREMENT,
						owner_uuid TEXT NOT NULL,
						symbol TEXT NOT NULL,
						is_long INTEGER NOT NULL,
						leverage INTEGER NOT NULL,
						margin_mg INTEGER NOT NULL,
						entry_price_mg INTEGER NOT NULL,
						size_milli_tokens INTEGER NOT NULL,
						opened_at INTEGER NOT NULL,
						open INTEGER NOT NULL DEFAULT 1
					)
					""");
		}
	}
}
