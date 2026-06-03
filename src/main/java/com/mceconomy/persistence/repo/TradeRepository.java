package com.mceconomy.persistence.repo;

import com.mceconomy.trade.PlayerTrade;
import com.mceconomy.trade.TradeDispute;
import com.mceconomy.trade.TradeDisputeStatus;
import com.mceconomy.trade.TradeStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class TradeRepository {
	private final Connection connection;

	public TradeRepository(Connection connection) {
		this.connection = connection;
	}

	public void saveTrade(PlayerTrade trade) throws SQLException {
		if (trade.id() <= 0) {
			insertTrade(trade);
		} else {
			updateTrade(trade);
		}
	}

	public Optional<PlayerTrade> findTrade(long id) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM player_trades WHERE id = ?")) {
			ps.setLong(1, id);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? Optional.of(mapTrade(rs)) : Optional.empty();
			}
		}
	}

	public Optional<PlayerTrade> findActiveForPlayer(UUID uuid) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				SELECT * FROM player_trades
				WHERE status = 'PENDING' AND (initiator_uuid = ? OR partner_uuid = ?)
				ORDER BY created_at DESC LIMIT 1
				""")) {
			ps.setString(1, uuid.toString());
			ps.setString(2, uuid.toString());
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? Optional.of(mapTrade(rs)) : Optional.empty();
			}
		}
	}

	public List<PlayerTrade> loadHistoryForPlayer(UUID uuid, int limit) throws SQLException {
		List<PlayerTrade> list = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement("""
				SELECT * FROM player_trades
				WHERE initiator_uuid = ? OR partner_uuid = ?
				ORDER BY created_at DESC LIMIT ?
				""")) {
			ps.setString(1, uuid.toString());
			ps.setString(2, uuid.toString());
			ps.setInt(3, limit);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					list.add(mapTrade(rs));
				}
			}
		}
		return list;
	}

	public void saveDispute(TradeDispute dispute) throws SQLException {
		if (dispute.id() <= 0) {
			insertDispute(dispute);
		} else {
			updateDispute(dispute);
		}
	}

	public List<TradeDispute> loadOpenDisputes() throws SQLException {
		List<TradeDispute> list = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT * FROM trade_disputes WHERE status = 'OPEN' ORDER BY created_at")) {
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					list.add(mapDispute(rs));
				}
			}
		}
		return list;
	}

	public Optional<TradeDispute> findDispute(long id) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM trade_disputes WHERE id = ?")) {
			ps.setLong(1, id);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? Optional.of(mapDispute(rs)) : Optional.empty();
			}
		}
	}

	private void insertTrade(PlayerTrade trade) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO player_trades(initiator_uuid, initiator_name, partner_uuid, partner_name,
					initiator_gold_mg, partner_gold_mg, initiator_items_json, partner_items_json,
					initiator_ready, partner_ready, status, completed_at, created_at)
				VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", Statement.RETURN_GENERATED_KEYS)) {
			bindTrade(ps, trade);
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				if (keys.next()) {
					trade.setId(keys.getLong(1));
				}
			}
		}
	}

	private void updateTrade(PlayerTrade trade) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				UPDATE player_trades SET initiator_gold_mg=?, partner_gold_mg=?,
					initiator_items_json=?, partner_items_json=?, initiator_ready=?, partner_ready=?,
					status=?, completed_at=? WHERE id=?
				""")) {
			ps.setLong(1, trade.initiatorGoldMg());
			ps.setLong(2, trade.partnerGoldMg());
			ps.setString(3, trade.initiatorItemsJson());
			ps.setString(4, trade.partnerItemsJson());
			ps.setInt(5, trade.initiatorReady() ? 1 : 0);
			ps.setInt(6, trade.partnerReady() ? 1 : 0);
			ps.setString(7, trade.status().name());
			ps.setLong(8, trade.completedAt());
			ps.setLong(9, trade.id());
			ps.executeUpdate();
		}
	}

	private void bindTrade(PreparedStatement ps, PlayerTrade trade) throws SQLException {
		ps.setString(1, trade.initiatorUuid().toString());
		ps.setString(2, trade.initiatorName());
		ps.setString(3, trade.partnerUuid().toString());
		ps.setString(4, trade.partnerName());
		ps.setLong(5, trade.initiatorGoldMg());
		ps.setLong(6, trade.partnerGoldMg());
		ps.setString(7, trade.initiatorItemsJson());
		ps.setString(8, trade.partnerItemsJson());
		ps.setInt(9, trade.initiatorReady() ? 1 : 0);
		ps.setInt(10, trade.partnerReady() ? 1 : 0);
		ps.setString(11, trade.status().name());
		ps.setLong(12, trade.completedAt());
		ps.setLong(13, trade.createdAt());
	}

	private PlayerTrade mapTrade(ResultSet rs) throws SQLException {
		return new PlayerTrade(
				rs.getLong("id"),
				UUID.fromString(rs.getString("initiator_uuid")),
				rs.getString("initiator_name"),
				UUID.fromString(rs.getString("partner_uuid")),
				rs.getString("partner_name"),
				rs.getLong("initiator_gold_mg"),
				rs.getLong("partner_gold_mg"),
				rs.getString("initiator_items_json"),
				rs.getString("partner_items_json"),
				rs.getInt("initiator_ready") == 1,
				rs.getInt("partner_ready") == 1,
				TradeStatus.valueOf(rs.getString("status")),
				rs.getLong("completed_at"),
				rs.getLong("created_at")
		);
	}

	private void insertDispute(TradeDispute dispute) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO trade_disputes(trade_id, reporter_uuid, reporter_name, target_uuid, target_name,
					reason, status, admin_note, resolved_by, resolved_at, created_at)
				VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", Statement.RETURN_GENERATED_KEYS)) {
			bindDispute(ps, dispute);
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				if (keys.next()) {
					dispute.setId(keys.getLong(1));
				}
			}
		}
	}

	private void updateDispute(TradeDispute dispute) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				UPDATE trade_disputes SET status=?, admin_note=?, resolved_by=?, resolved_at=? WHERE id=?
				""")) {
			ps.setString(1, dispute.status().name());
			ps.setString(2, dispute.adminNote());
			ps.setString(3, dispute.resolvedBy());
			ps.setLong(4, dispute.resolvedAt());
			ps.setLong(5, dispute.id());
			ps.executeUpdate();
		}
	}

	private void bindDispute(PreparedStatement ps, TradeDispute dispute) throws SQLException {
		ps.setLong(1, dispute.tradeId());
		ps.setString(2, dispute.reporterUuid().toString());
		ps.setString(3, dispute.reporterName());
		ps.setString(4, dispute.targetUuid().toString());
		ps.setString(5, dispute.targetName());
		ps.setString(6, dispute.reason());
		ps.setString(7, dispute.status().name());
		ps.setString(8, dispute.adminNote());
		ps.setString(9, dispute.resolvedBy());
		ps.setLong(10, dispute.resolvedAt());
		ps.setLong(11, dispute.createdAt());
	}

	private TradeDispute mapDispute(ResultSet rs) throws SQLException {
		return new TradeDispute(
				rs.getLong("id"),
				rs.getLong("trade_id"),
				UUID.fromString(rs.getString("reporter_uuid")),
				rs.getString("reporter_name"),
				UUID.fromString(rs.getString("target_uuid")),
				rs.getString("target_name"),
				rs.getString("reason"),
				TradeDisputeStatus.valueOf(rs.getString("status")),
				rs.getString("admin_note"),
				rs.getString("resolved_by"),
				rs.getLong("resolved_at"),
				rs.getLong("created_at")
		);
	}
}
