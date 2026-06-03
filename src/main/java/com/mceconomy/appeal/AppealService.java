package com.mceconomy.appeal;

import com.mceconomy.persistence.repo.AppealRepository;
import com.mceconomy.player.PlayerEconomyProfile;
import com.mceconomy.regulation.MasakService;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class AppealService {
	private final AppealRepository repository;
	private final MasakService masakService;
	private final Map<UUID, PlayerEconomyProfile> profiles;

	public AppealService(AppealRepository repository, MasakService masakService,
			Map<UUID, PlayerEconomyProfile> profiles) {
		this.repository = repository;
		this.masakService = masakService;
		this.profiles = profiles;
	}

	public boolean submit(UUID playerUuid, String playerName, String subject, String message, Long relatedAlertId)
			throws SQLException {
		if (message == null || message.isBlank()) {
			return false;
		}
		Appeal appeal = Appeal.open(playerUuid, playerName, subject, message.trim(), relatedAlertId);
		repository.save(appeal);
		return true;
	}

	public List<Appeal> openAppeals() {
		try {
			return repository.loadOpen();
		} catch (SQLException e) {
			return List.of();
		}
	}

	public boolean accept(long appealId, String adminNote) throws SQLException {
		Optional<Appeal> appealOpt = repository.findById(appealId);
		if (appealOpt.isEmpty() || appealOpt.get().status() != AppealStatus.OPEN) {
			return false;
		}
		Appeal appeal = appealOpt.get();
		Appeal resolved = appeal.withStatus(AppealStatus.ACCEPTED, adminNote);
		repository.save(resolved);

		PlayerEconomyProfile profile = profiles.get(appeal.playerUuid());
		if (profile != null) {
			profile.setAccountFrozen(false);
			if (profile.blacklisted()) {
				profile.setBlacklisted(false);
			}
			profile.creditScore().adjust(10);
		}
		if (appeal.relatedAlertId() != null) {
			masakService.resolveAlert(appeal.relatedAlertId(), appeal.playerUuid());
		}
		return true;
	}

	public boolean reject(long appealId, String adminNote) throws SQLException {
		Optional<Appeal> appealOpt = repository.findById(appealId);
		if (appealOpt.isEmpty() || appealOpt.get().status() != AppealStatus.OPEN) {
			return false;
		}
		repository.save(appealOpt.get().withStatus(AppealStatus.REJECTED, adminNote));
		return true;
	}

	public List<Appeal> playerAppeals(UUID uuid) {
		try {
			return repository.loadForPlayer(uuid);
		} catch (SQLException e) {
			return List.of();
		}
	}
}
