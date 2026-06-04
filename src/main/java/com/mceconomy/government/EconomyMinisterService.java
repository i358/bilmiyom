package com.mceconomy.government;

import com.mceconomy.McEconomyMod;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.EconomyManager;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.news.EconomyBulletinService;
import com.mceconomy.player.PlayerEconomyProfile;
import com.mceconomy.persistence.repo.PlayerRepository;
import com.mceconomy.web.AdminEconomyService;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EconomyMinisterService {
	public record Application(long id, UUID applicantUuid, String applicantName, String reason, String status) {
	}

	public record Decree(long id, String type, String payloadJson, String status, long createdAt) {
	}

	private final Connection connection;
	private final Map<UUID, PlayerEconomyProfile> profiles;
	private final PlayerRepository playerRepository;

	public EconomyMinisterService(Connection connection, Map<UUID, PlayerEconomyProfile> profiles,
			PlayerRepository playerRepository) {
		this.connection = connection;
		this.profiles = profiles;
		this.playerRepository = playerRepository;
	}

	public void load() throws SQLException {
		// profiles already loaded with economy_minister flag
	}

	public boolean isMinister(UUID uuid) {
		PlayerEconomyProfile p = profiles.get(uuid);
		return p != null && p.economyMinister();
	}

	public int ministerCount() {
		return (int) profiles.values().stream().filter(PlayerEconomyProfile::economyMinister).count();
	}

	public void appoint(ServerPlayer target, boolean value) throws SQLException {
		PlayerEconomyProfile p = profiles.get(target.getUUID());
		if (p == null) {
			return;
		}
		p.setEconomyMinister(value);
		playerRepository.save(p);
	}

	public String apply(ServerPlayer player, String reason) throws SQLException {
		if (isMinister(player.getUUID())) {
			return "Zaten ekonomi bakani siniz.";
		}
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO economy_minister_applications(applicant_uuid, applicant_name, reason, status, created_at)
				VALUES(?, ?, ?, 'PENDING', ?)
				""")) {
			ps.setString(1, player.getUUID().toString());
			ps.setString(2, player.getName().getString());
			ps.setString(3, reason == null ? "" : reason);
			ps.setLong(4, System.currentTimeMillis());
			ps.executeUpdate();
		}
		return "Basvurunuz alindi.";
	}

	public List<Application> pendingApplications() throws SQLException {
		List<Application> list = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT * FROM economy_minister_applications WHERE status = 'PENDING' ORDER BY created_at");
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				list.add(new Application(rs.getLong("id"),
						UUID.fromString(rs.getString("applicant_uuid")),
						rs.getString("applicant_name"),
						rs.getString("reason"),
						rs.getString("status")));
			}
		}
		return list;
	}

	public String approveApplication(String playerName, UUID ministerUuid) throws SQLException {
		if (!isMinister(ministerUuid) && !isOp(ministerUuid)) {
			return "Yetkisiz.";
		}
		if (ministerCount() >= EconomyConfig.maxEconomyMinisters()) {
			return "Bakan kotasi dolu.";
		}
		ServerPlayer target = McEconomyMod.getEconomyManager().server().getPlayerList().getPlayerByName(playerName);
		if (target == null) {
			return "Oyuncu cevrimici degil.";
		}
		appoint(target, true);
		try (PreparedStatement ps = connection.prepareStatement(
				"UPDATE economy_minister_applications SET status='APPROVED' WHERE applicant_uuid=? AND status='PENDING'")) {
			ps.setString(1, target.getUUID().toString());
			ps.executeUpdate();
		}
		target.sendSystemMessage(Component.literal("§6[Devlet] §aEkonomi Bakani olarak atandiniz."));
		return "Onaylandi.";
	}

	public String rejectApplication(String playerName) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement(
				"UPDATE economy_minister_applications SET status='REJECTED' WHERE applicant_name=? AND status='PENDING'")) {
			ps.setString(1, playerName);
			ps.executeUpdate();
		}
		return "Reddedildi.";
	}

	public String issueDecree(UUID ministerUuid, String type, JsonObject payload) throws SQLException {
		if (!isMinister(ministerUuid)) {
			return "Yalnizca ekonomi bakani emir verebilir.";
		}
		EconomyManager manager = McEconomyMod.getEconomyManager();
		String result = switch (type == null ? "" : type) {
			case "interest" -> applyInterestDecree(manager, payload);
			case "tax" -> applyTaxDecree(payload);
			case "bulletin" -> applyBulletin(manager, payload);
			case "market_multiplier" -> applyMarketMultiplier(manager, payload);
			default -> "Bilinmeyen emir: " + type;
		};
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO economy_decrees(type, payload_json, status, created_at, issued_by)
				VALUES(?, ?, 'ACTIVE', ?, ?)
				""")) {
			ps.setString(1, type);
			ps.setString(2, payload != null ? payload.toString() : "{}");
			ps.setLong(3, System.currentTimeMillis());
			ps.setString(4, ministerUuid.toString());
			ps.executeUpdate();
		}
		return result;
	}

	private static String applyInterestDecree(EconomyManager manager, JsonObject payload) {
		if (payload == null || !payload.has("baseRate")) {
			return "baseRate gerekli.";
		}
		JsonObject body = new JsonObject();
		body.addProperty("baseRate", payload.get("baseRate").getAsDouble());
		var r = AdminEconomyService.centralBankUpdate(body);
		return r.success() ? "Faiz emri uygulandi." : r.message();
	}

	private static String applyTaxDecree(JsonObject payload) {
		if (payload == null) {
			return "payload gerekli.";
		}
		if (payload.has("incomeTaxRate")) {
			EconomyConfig.setIncomeTaxRate(payload.get("incomeTaxRate").getAsDouble());
		}
		if (payload.has("cityTaxRate")) {
			EconomyConfig.setCityTaxRate(payload.get("cityTaxRate").getAsDouble());
		}
		return "Vergi oranlari guncellendi.";
	}

	private static String applyBulletin(EconomyManager manager, JsonObject payload) {
		if (payload == null || !payload.has("message")) {
			return "message gerekli.";
		}
		EconomyBulletinService bulletin = manager.bulletinService();
		if (bulletin == null || manager.server() == null) {
			return "Bulten servisi yok.";
		}
		String msg = payload.get("message").getAsString();
		bulletin.publishMacro(manager.server(), "Ekonomi Bakani", msg);
		return "Bulten yayinlandi.";
	}

	private static String applyMarketMultiplier(EconomyManager manager, JsonObject payload) {
		if (payload == null || !payload.has("multiplier")) {
			return "multiplier gerekli.";
		}
		double m = payload.get("multiplier").getAsDouble();
		manager.marketService().priceEngine().setGlobalMultiplier(m);
		return "Piyasa carpani: " + m;
	}

	private static boolean isOp(UUID uuid) {
		var p = McEconomyMod.getEconomyManager().server().getPlayerList().getPlayer(uuid);
		return p != null && McEconomyMod.getEconomyManager().server().getPlayerList().isOp(p.nameAndId());
	}

	public List<Decree> recentDecrees(int limit) throws SQLException {
		List<Decree> list = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT * FROM economy_decrees ORDER BY created_at DESC LIMIT ?")) {
			ps.setInt(1, limit);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					list.add(new Decree(rs.getLong("id"), rs.getString("type"),
							rs.getString("payload_json"), rs.getString("status"), rs.getLong("created_at")));
				}
			}
		}
		return list;
	}
}
