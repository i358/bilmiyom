package com.mceconomy.municipal;

import com.mceconomy.McEconomyMod;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.persistence.repo.MunicipalRepository;
import com.mceconomy.tax.CentralBank;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MayorService {
	public record MayorState(UUID mayorUuid, String mayorName, long termEndMs, long electionStartMs) {
		public boolean hasMayor() {
			return mayorUuid != null;
		}

		public boolean electionOpen(long now) {
			return true;
		}
	}

	private final MunicipalRepository repository;
	private final CentralBank centralBank;
	private MayorState state = new MayorState(null, null, 0, 0);
	private final Map<UUID, UUID> votes = new HashMap<>();
	private final Map<UUID, String> candidates = new HashMap<>();

	public MayorService(MunicipalRepository repository, CentralBank centralBank) {
		this.repository = repository;
		this.centralBank = centralBank;
	}

	public void load() throws SQLException {
		state = repository.loadState();
		votes.clear();
		candidates.clear();
		votes.putAll(repository.loadVotes(state.termEndMs()));
		candidates.putAll(repository.loadCandidates(state.termEndMs()));
	}

	public MayorState state() {
		return state;
	}

	public boolean isMayor(UUID uuid) {
		return state.hasMayor() && uuid.equals(state.mayorUuid());
	}

	public boolean registerCandidate(UUID uuid, String name) throws SQLException {
		long now = System.currentTimeMillis();
		if (!state.electionOpen(now)) {
			return false;
		}
		candidates.put(uuid, name);
		repository.saveCandidate(state.termEndMs(), uuid, name);
		return true;
	}

	public boolean vote(UUID voter, String candidateName) throws SQLException {
		long now = System.currentTimeMillis();
		if (!state.electionOpen(now)) {
			return false;
		}
		if (votes.containsKey(voter)) {
			return false;
		}
		UUID candidate = candidates.entrySet().stream()
				.filter(e -> e.getValue().equalsIgnoreCase(candidateName))
				.map(Map.Entry::getKey).findFirst().orElse(null);
		if (candidate == null || candidate.equals(voter)) {
			return false;
		}
		votes.put(voter, candidate);
		repository.saveVote(state.termEndMs(), voter, candidate);
		return true;
	}

	public boolean spendBudget(UUID mayor, long amountMg, String purpose) throws SQLException {
		if (!isMayor(mayor) || amountMg <= 0 || purpose == null || purpose.isBlank()) {
			return false;
		}
		if (!centralBank.spendMunicipalBudget(amountMg)) {
			return false;
		}
		MinecraftServer server = McEconomyMod.getEconomyManager().server();
		if (server != null) {
			server.getPlayerList().broadcastSystemMessage(Component.literal(
					"§6[Belediye] §e" + state.mayorName() + " §fbütceden "
							+ GoldStandard.formatMilligrams(amountMg) + " harcadi: §7" + purpose), false);
		}
		return true;
	}

	public void tick(MinecraftServer server) {
		long now = System.currentTimeMillis();
		if (state.termEndMs() > 0 && now >= state.termEndMs()) {
			try {
				finalizeElection(server);
			} catch (SQLException e) {
				McEconomyMod.LOGGER.error("Belediye secimi", e);
			}
		}
	}

	private void finalizeElection(MinecraftServer server) throws SQLException {
		UUID winner = null;
		String winnerName = null;
		int best = 0;
		Map<UUID, Integer> tally = new HashMap<>();
		for (UUID candidate : votes.values()) {
			int c = tally.merge(candidate, 1, Integer::sum);
			if (c > best) {
				best = c;
				winner = candidate;
			}
		}
		if (winner != null) {
			winnerName = candidates.getOrDefault(winner, "?");
		}
		long termMs = EconomyConfig.mayorTermDays() * 24L * 60 * 60 * 1000;
		long nextEnd = System.currentTimeMillis() + termMs;
		state = new MayorState(winner, winnerName, nextEnd, nextEnd - EconomyConfig.mayorElectionWindowMs());
		repository.saveState(state);
		votes.clear();
		candidates.clear();
		repository.clearElectionData();
		if (server != null && winnerName != null) {
			server.getPlayerList().broadcastSystemMessage(Component.literal(
					"§6§l[SECIM] §e" + winnerName + " §fbelediye baskani secildi! §7/belediye durum"), false);
		} else if (server != null) {
			server.getPlayerList().broadcastSystemMessage(Component.literal(
					"§6[SECIM] §fSecim tamamlandi — aday yok veya oy yok."), false);
		}
	}

	public void ensureElectionScheduled() throws SQLException {
		if (state.termEndMs() <= 0) {
			long termMs = EconomyConfig.mayorTermDays() * 24L * 60 * 60 * 1000;
			long end = System.currentTimeMillis() + termMs;
			state = new MayorState(null, null, end, end - EconomyConfig.mayorElectionWindowMs());
			repository.saveState(state);
		}
	}
}
