package com.mceconomy.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mceconomy.McEconomyMod;
import com.mceconomy.company.CompanyVault;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.justice.PrisonService;
import com.mceconomy.persistence.repo.SecurityCameraRepository;
import com.mceconomy.world.CentralBankPlacer;
import com.mceconomy.facility.FacilityType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.UUID;

public final class WorldMapService {
	private static final int HUB_RADIUS = 96;

	private WorldMapService() {
	}

	public static JsonObject buildMapData(UUID viewerUuid, boolean op, String trackPlayer) {
		JsonObject data = new JsonObject();
		BlockPos hub = resolveHubCenter();
		int focusX = hub.getX();
		int focusZ = hub.getZ();
		int radius = Math.max(HUB_RADIUS, EconomyConfig.securityCameraRadius() + 32);

		JsonObject focus = new JsonObject();
		focus.addProperty("x", focusX);
		focus.addProperty("z", focusZ);
		focus.addProperty("radius", radius);
		data.add("focus", focus);

		JsonArray pois = new JsonArray();
		JsonArray remotePois = new JsonArray();
		addHubPoi(pois, "central_bank", "Merkez Bankasi", CentralBankPlacer.bankOrigin(), "bank");
		addHubPoi(pois, "gold_reserve", "Altin Rezervi", CentralBankPlacer.reservePos(), "reserve");
		addHubPoi(pois, "market_depot", "Piyasa Deposu", CentralBankPlacer.depotPos(FacilityType.MARKET), "depot");
		addHubPoi(pois, "black_depot", "Karaborsa Deposu", CentralBankPlacer.depotPos(FacilityType.BLACK_MARKET), "depot");
		addHubPoi(pois, "gold_depot", "Fiziksel Altin Kasasi", CentralBankPlacer.depotPos(FacilityType.PHYSICAL_GOLD), "depot");

		JsonObject prison = new JsonObject();
		prison.addProperty("id", "prison");
		prison.addProperty("name", "Merkez Hapishane (uzak bolge)");
		prison.addProperty("x", PrisonService.mapAnchorX());
		prison.addProperty("y", PrisonService.mapAnchorY());
		prison.addProperty("z", PrisonService.mapAnchorZ());
		prison.addProperty("type", "prison");
		remotePois.add(prison);

		var manager = McEconomyMod.getEconomyManager();
		if (manager != null && manager.companyVaultService() != null && viewerUuid != null) {
			for (var company : manager.companyManager().allCompanies()) {
				if (!company.ownerUuid().equals(viewerUuid)) {
					continue;
				}
				CompanyVault vault = manager.companyVaultService().getVault(company.id());
				if (vault == null) {
					continue;
				}
				addHubPoi(pois, "company_vault_" + company.id(),
						"Sirket Sandigi: " + company.name(),
						new BlockPos(vault.chestX(), vault.chestY(), vault.chestZ()),
						"company_vault");
			}
			var playerVault = manager.vaultService().getVault(viewerUuid);
			if (playerVault != null) {
				addHubPoi(pois, "personal_vault", "Kisisel Kasa",
						new BlockPos(playerVault.chestX(), playerVault.chestY(), playerVault.chestZ()),
						"personal_vault");
			}
		}
		data.add("pois", pois);
		data.add("remotePois", remotePois);
		data.add("players", buildLivePlayers(manager != null ? manager.server() : null, viewerUuid, op, trackPlayer));
		data.add("radar", buildRadar(manager));
		data.add("radarReplay", buildRadarReplay(manager));
		data.addProperty("world", "minecraft:overworld");
		boolean recording = manager != null && manager.securityCameraService() != null
				&& manager.securityCameraService().isRecording();
		data.addProperty("cameraRecording", recording);
		return data;
	}

	private static BlockPos resolveHubCenter() {
		BlockPos origin = CentralBankPlacer.bankOrigin();
		if (origin != null) {
			return origin;
		}
		if (EconomyConfig.bankOriginStored()) {
			return new BlockPos(EconomyConfig.bankOriginX(), EconomyConfig.bankOriginY(), EconomyConfig.bankOriginZ());
		}
		return BlockPos.ZERO;
	}

	private static JsonArray buildRadarReplay(com.mceconomy.economy.EconomyManager manager) {
		JsonArray frames = new JsonArray();
		if (manager == null || manager.securityCameraService() == null) {
			return frames;
		}
		for (SecurityCameraRepository.CameraLog log : manager.securityCameraService().replayFrames()) {
			JsonObject row = new JsonObject();
			row.addProperty("playerUuid", log.playerUuid().toString());
			row.addProperty("name", log.playerName());
			row.addProperty("x", log.x());
			row.addProperty("y", log.y());
			row.addProperty("z", log.z());
			row.addProperty("recordedAt", log.recordedAt());
			frames.add(row);
		}
		return frames;
	}

	private static JsonArray buildRadar(com.mceconomy.economy.EconomyManager manager) {
		JsonArray radar = new JsonArray();
		if (manager == null || manager.securityCameraService() == null) {
			return radar;
		}
		for (SecurityCameraRepository.CameraLog log : manager.securityCameraService().radarContacts()) {
			JsonObject row = new JsonObject();
			row.addProperty("name", log.playerName());
			row.addProperty("x", log.x());
			row.addProperty("y", log.y());
			row.addProperty("z", log.z());
			row.addProperty("recordedAt", log.recordedAt());
			row.addProperty("type", "camera");
			radar.add(row);
		}
		return radar;
	}

	private static void addHubPoi(JsonArray pois, String id, String name, BlockPos pos, String type) {
		if (pos == null) {
			return;
		}
		JsonObject row = new JsonObject();
		row.addProperty("id", id);
		row.addProperty("name", name);
		row.addProperty("x", pos.getX());
		row.addProperty("y", pos.getY());
		row.addProperty("z", pos.getZ());
		row.addProperty("type", type);
		pois.add(row);
	}

	private static JsonArray buildLivePlayers(MinecraftServer server, UUID viewerUuid, boolean op,
			String trackPlayer) {
		JsonArray players = new JsonArray();
		if (server == null) {
			return players;
		}
		String filter = trackPlayer != null ? trackPlayer.trim().toLowerCase(Locale.ROOT) : "";
		boolean trackAll = op && (filter.isEmpty() || "*".equals(filter) || "all".equals(filter));
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!trackAll) {
				if (!filter.isEmpty()) {
					if (!player.getName().getString().equalsIgnoreCase(filter)) {
						continue;
					}
				} else if (!player.getUUID().equals(viewerUuid)) {
					continue;
				}
			}
			JsonObject row = new JsonObject();
			row.addProperty("uuid", player.getUUID().toString());
			row.addProperty("name", player.getName().getString());
			row.addProperty("x", player.getX());
			row.addProperty("y", player.getY());
			row.addProperty("z", player.getZ());
			row.addProperty("online", true);
			players.add(row);
		}
		return players;
	}
}
