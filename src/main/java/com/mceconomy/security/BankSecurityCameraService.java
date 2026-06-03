package com.mceconomy.security;

import com.mceconomy.McEconomyMod;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.persistence.repo.SecurityCameraRepository;
import com.mceconomy.world.CentralBankPlacer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Gece MB bolgesinde GPS kaydi — ertesi gece onceki kayitlar silinir. */
public final class BankSecurityCameraService {
	private static final DateTimeFormatter NIGHT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private final SecurityCameraRepository repository;
	private String currentNightKey = "";
	private boolean recording;
	private int tickCounter;

	public BankSecurityCameraService(SecurityCameraRepository repository) {
		this.repository = repository;
	}

	public void onNightBegins() {
		try {
			repository.purgeAll();
			currentNightKey = LocalDate.now().format(NIGHT_FMT);
			recording = true;
			McEconomyMod.LOGGER.info("[Guvenlik Kamerasi] Gece kaydi basladi: {}", currentNightKey);
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Kamera kaydi baslatilamadi", e);
		}
	}

	public void onNightEnds() {
		recording = false;
	}

	public void tick(MinecraftServer server, boolean guardsSleeping) {
		if (!guardsSleeping || !recording || server == null) {
			return;
		}
		tickCounter++;
		if (tickCounter % EconomyConfig.securityCameraRecordIntervalTicks() != 0) {
			return;
		}
		int radius = EconomyConfig.securityCameraRadius();
		ServerLevel level = server.overworld();
		long now = System.currentTimeMillis();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isCreative() || player.isSpectator()) {
				continue;
			}
			BlockPos pos = player.blockPosition();
			if (!CentralBankPlacer.isInSurveillanceZone(pos, radius)) {
				continue;
			}
			try {
				repository.insert(currentNightKey, player.getUUID(), player.getName().getString(),
						pos.getX(), pos.getY(), pos.getZ(), now);
			} catch (SQLException e) {
				McEconomyMod.LOGGER.error("Kamera pozisyonu kaydi", e);
			}
		}
	}

	public String currentNightKey() {
		return currentNightKey;
	}

	public boolean isRecording() {
		return recording;
	}

	public List<SecurityCameraRepository.CameraLog> replayFrames() {
		String key = resolveReplayNightKey();
		return key == null ? List.of() : loadReplayForNight(key, 4000);
	}

	public List<SecurityCameraRepository.CameraLog> loadReplayForNight(String nightKey, int limit) {
		if (nightKey == null || nightKey.isBlank()) {
			return List.of();
		}
		try {
			return repository.loadReplayChronological(nightKey, limit);
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Radar replay", e);
			return List.of();
		}
	}

	private String resolveReplayNightKey() {
		if (!currentNightKey.isBlank()) {
			return currentNightKey;
		}
		try {
			List<String> nights = repository.listNightKeys();
			return nights.isEmpty() ? null : nights.get(0);
		} catch (SQLException e) {
			return null;
		}
	}

	public List<SecurityCameraRepository.CameraLog> radarContacts() {
		if (currentNightKey.isBlank()) {
			return List.of();
		}
		try {
			return repository.loadLatestPerPlayer(currentNightKey, 64);
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Radar kontaklari", e);
			return List.of();
		}
	}

	public List<SecurityCameraRepository.CameraLog> loadLogs(String nightKey, int limit) throws SQLException {
		String key = nightKey != null && !nightKey.isBlank() ? nightKey : currentNightKey;
		if (key.isBlank()) {
			List<String> keys = repository.listNightKeys();
			if (keys.isEmpty()) {
				return List.of();
			}
			key = keys.get(0);
		}
		return repository.loadByNight(key, limit);
	}

	public List<String> listNights() throws SQLException {
		return repository.listNightKeys();
	}
}
