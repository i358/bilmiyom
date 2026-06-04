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

	public record Decree(long id, String type, String payloadJson, String status, long createdAt, String issuedBy) {
	}

	public record DecreeVoteRow(UUID ministerUuid, boolean yes, long votedAt) {
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
			p = playerRepository.createIfAbsent(target.getUUID(), target.getName().getString());
			profiles.put(target.getUUID(), p);
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

	/** Kabine onayi icin emir teklifi (hemen uygulanmaz). */
	public String proposeDecree(UUID ministerUuid, String type, JsonObject payload) throws SQLException {
		if (!isMinister(ministerUuid)) {
			return "Yalnizca ekonomi bakani emir onerebilir.";
		}
		String validation = validateDecreePayload(type, payload);
		if (validation != null) {
			return validation;
		}
		long decreeId;
		long now = System.currentTimeMillis();
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO economy_decrees(type, payload_json, status, created_at, issued_by)
				VALUES(?, ?, 'PENDING', ?, ?)
				""", Statement.RETURN_GENERATED_KEYS)) {
			ps.setString(1, type);
			ps.setString(2, payload != null ? payload.toString() : "{}");
			ps.setLong(3, now);
			ps.setString(4, ministerUuid.toString());
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				if (!keys.next()) {
					return "Emir kaydedilemedi.";
				}
				decreeId = keys.getLong(1);
			}
		}
		recordVote(decreeId, ministerUuid, true);
		notifyMinistersPending(decreeId, type, ministerUuid);
		return tryRatify(decreeId);
	}

	public String voteDecree(UUID ministerUuid, long decreeId, boolean yes) throws SQLException {
		if (!isMinister(ministerUuid)) {
			return "Yalnizca ekonomi bakani oy kullanabilir.";
		}
		Decree decree = findDecree(decreeId);
		if (decree == null) {
			return "Emir bulunamadi.";
		}
		if (!"PENDING".equals(decree.status())) {
			return "Emir artik oylanmiyor (" + decree.status() + ").";
		}
		recordVote(decreeId, ministerUuid, yes);
		return tryRatify(decreeId);
	}

	public List<Decree> pendingDecrees() throws SQLException {
		List<Decree> list = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT * FROM economy_decrees WHERE status = 'PENDING' ORDER BY created_at");
			 ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				list.add(mapDecree(rs));
			}
		}
		return list;
	}

	public List<DecreeVoteRow> votesForDecree(long decreeId) throws SQLException {
		List<DecreeVoteRow> list = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement(
				"SELECT minister_uuid, vote, voted_at FROM economy_decree_votes WHERE decree_id = ?")) {
			ps.setLong(1, decreeId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					list.add(new DecreeVoteRow(
							UUID.fromString(rs.getString("minister_uuid")),
							"YES".equalsIgnoreCase(rs.getString("vote")),
							rs.getLong("voted_at")));
				}
			}
		}
		return list;
	}

	public int requiredYesVotes() {
		int ministers = Math.max(1, ministerCount());
		return (int) Math.ceil(ministers * EconomyConfig.decreeVoteApprovalRatio());
	}

	private void recordVote(long decreeId, UUID ministerUuid, boolean yes) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("""
				INSERT INTO economy_decree_votes(decree_id, minister_uuid, vote, voted_at)
				VALUES(?, ?, ?, ?)
				ON CONFLICT(decree_id, minister_uuid) DO UPDATE SET vote = excluded.vote, voted_at = excluded.voted_at
				""")) {
			ps.setLong(1, decreeId);
			ps.setString(2, ministerUuid.toString());
			ps.setString(3, yes ? "YES" : "NO");
			ps.setLong(4, System.currentTimeMillis());
			ps.executeUpdate();
		}
	}

	private String tryRatify(long decreeId) throws SQLException {
		Decree decree = findDecree(decreeId);
		if (decree == null || !"PENDING".equals(decree.status())) {
			return "Emir durumu guncel degil.";
		}
		int yes = 0;
		int no = 0;
		for (DecreeVoteRow v : votesForDecree(decreeId)) {
			if (v.yes()) {
				yes++;
			} else {
				no++;
			}
		}
		int required = requiredYesVotes();
		int ministers = Math.max(1, ministerCount());
		if (yes >= required) {
			String result = executeDecree(decree);
			updateDecreeStatus(decreeId, "ACTIVE");
			broadcastRatified(decree, result);
			return "Emir yururluge girdi: " + result;
		}
		if (no > ministers - required) {
			updateDecreeStatus(decreeId, "REJECTED");
			return "Emir reddedildi (yeterli ret oyu).";
		}
		if (yes + no >= ministers) {
			updateDecreeStatus(decreeId, "REJECTED");
			return "Emir reddedildi (onay yetersiz: " + yes + "/" + required + ").";
		}
		return "Oy kaydedildi. Onay: " + yes + "/" + required + " (bekleyen emir #" + decreeId + ").";
	}

	private void updateDecreeStatus(long decreeId, String status) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement(
				"UPDATE economy_decrees SET status = ? WHERE id = ?")) {
			ps.setString(1, status);
			ps.setLong(2, decreeId);
			ps.executeUpdate();
		}
	}

	private Decree findDecree(long id) throws SQLException {
		try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM economy_decrees WHERE id = ?")) {
			ps.setLong(1, id);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? mapDecree(rs) : null;
			}
		}
	}

	private static Decree mapDecree(ResultSet rs) throws SQLException {
		return new Decree(rs.getLong("id"), rs.getString("type"),
				rs.getString("payload_json"), rs.getString("status"),
				rs.getLong("created_at"), rs.getString("issued_by"));
	}

	private static String validateDecreePayload(String type, JsonObject payload) {
		return switch (type == null ? "" : type) {
			case "interest" -> payload != null && payload.has("baseRate") ? null : "baseRate gerekli.";
			case "tax" -> payload != null && (payload.has("incomeTaxRate") || payload.has("cityTaxRate"))
					? null : "incomeTaxRate veya cityTaxRate gerekli.";
			case "bulletin" -> payload != null && payload.has("message") ? null : "message gerekli.";
			case "market_multiplier" -> payload != null && payload.has("multiplier") ? null : "multiplier gerekli.";
			default -> "Bilinmeyen emir: " + type;
		};
	}

	private String executeDecree(Decree decree) {
		JsonObject payload = com.google.gson.JsonParser.parseString(
				decree.payloadJson() != null ? decree.payloadJson() : "{}").getAsJsonObject();
		EconomyManager manager = McEconomyMod.getEconomyManager();
		return switch (decree.type()) {
			case "interest" -> applyInterestDecree(manager, payload);
			case "tax" -> applyTaxDecree(payload);
			case "bulletin" -> applyBulletin(manager, payload);
			case "market_multiplier" -> applyMarketMultiplier(manager, payload);
			default -> "Bilinmeyen emir: " + decree.type();
		};
	}

	private void notifyMinistersPending(long decreeId, String type, UUID proposer) {
		var server = McEconomyMod.getEconomyManager().server();
		if (server == null) {
			return;
		}
		for (PlayerEconomyProfile p : profiles.values()) {
			if (!p.economyMinister() || p.uuid().equals(proposer)) {
				continue;
			}
			ServerPlayer mp = server.getPlayerList().getPlayer(p.uuid());
			if (mp != null) {
				mp.sendSystemMessage(Component.literal(
						"§6[Bakanlik] §eYeni emir teklifi #" + decreeId + " (" + type + ") — /ekonomi bakan emir oy "
								+ decreeId + " evet|hayir"));
			}
		}
	}

	private void broadcastRatified(Decree decree, String result) {
		var server = McEconomyMod.getEconomyManager().server();
		if (server == null) {
			return;
		}
		server.getPlayerList().broadcastSystemMessage(
				Component.literal("§6[Ekonomi Bakani] §fEmir #" + decree.id() + " (" + decree.type() + "): " + result),
				false);
	}

	/** Geriye uyumluluk: aninda teklif + oylama akisi. */
	public String issueDecree(UUID ministerUuid, String type, JsonObject payload) throws SQLException {
		return proposeDecree(ministerUuid, type, payload);
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
					list.add(mapDecree(rs));
				}
			}
		}
		return list;
	}
}
