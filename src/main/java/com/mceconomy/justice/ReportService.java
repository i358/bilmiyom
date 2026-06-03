package com.mceconomy.justice;

import com.mceconomy.McEconomyMod;
import com.mceconomy.command.BalanceCommand;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.CurrencyService;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.economy.TransactionType;
import com.mceconomy.persistence.repo.ReportRepository;
import com.mceconomy.player.PlayerEconomyProfile;
import com.mceconomy.tax.CentralBank;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ReportService {
	private final ReportRepository repository;
	private final Map<UUID, PlayerEconomyProfile> profiles;
	private CurrencyService currencyService;
	private CentralBank centralBank;

	public ReportService(ReportRepository repository, Map<UUID, PlayerEconomyProfile> profiles) {
		this.repository = repository;
		this.profiles = profiles;
	}

	public void bindEconomy(CurrencyService currencyService, CentralBank centralBank) {
		this.currencyService = currencyService;
		this.centralBank = centralBank;
	}

	public boolean submitComplaint(UUID reporterUuid, String reporterName, String targetName,
			String category, String subject, String message) throws SQLException {
		UUID targetUuid = resolveTarget(targetName);
		if (targetUuid == null || targetUuid.equals(reporterUuid)) {
			return false;
		}
		CitizenReport report = CitizenReport.open(ReportType.COMPLAINT, reporterUuid, reporterName,
				targetUuid, targetName, category, subject, message);
		long id = repository.insert(report);
		notifyAutomaticInvestigation(reporterName, targetName, id, subject, true);
		markInvestigatingForTarget(targetUuid);
		triggerRobberyInvestigation(targetUuid);
		return id > 0;
	}

	public void payTipRewardForReport(CitizenReport report) throws SQLException {
		payTipRewardIfEligible(report);
	}

	public boolean submitTipOff(UUID reporterUuid, String reporterName, String targetName,
			String category, String message) throws SQLException {
		UUID targetUuid = targetName != null && !targetName.isBlank() ? resolveTarget(targetName) : null;
		String subject = category;
		CitizenReport report = CitizenReport.open(ReportType.TIP_OFF, reporterUuid, reporterName,
				targetUuid, targetName, category, subject, message);
		long id = repository.insert(report);
		notifyAutomaticInvestigation(reporterName, targetName, id, category, false);
		if (targetUuid != null) {
			markInvestigatingForTarget(targetUuid);
			triggerRobberyInvestigation(targetUuid);
		}
		return id > 0;
	}

	public List<CitizenReport> openReports() {
		try {
			return repository.loadOpen();
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Raporlar yuklenemedi", e);
			return List.of();
		}
	}

	public List<CitizenReport> reporterHistory(UUID uuid) {
		try {
			return repository.loadForReporter(uuid);
		} catch (SQLException e) {
			return List.of();
		}
	}

	public Optional<CitizenReport> find(long id) {
		try {
			return repository.findById(id);
		} catch (SQLException e) {
			return Optional.empty();
		}
	}

	public boolean dismiss(long id, String note) throws SQLException {
		Optional<CitizenReport> opt = repository.findById(id);
		if (opt.isEmpty() || opt.get().status() == ReportStatus.GUILTY || opt.get().status() == ReportStatus.DISMISSED) {
			return false;
		}
		repository.update(opt.get().withStatus(ReportStatus.DISMISSED, note, null));
		return true;
	}

	public boolean markGuilty(long id, String note, Long prisonSentenceId) throws SQLException {
		Optional<CitizenReport> opt = repository.findById(id);
		if (opt.isEmpty()) {
			return false;
		}
		CitizenReport report = opt.get();
		repository.update(report.withStatus(ReportStatus.GUILTY, note, prisonSentenceId));
		if (report.targetUuid() != null) {
			PlayerEconomyProfile profile = profiles.get(report.targetUuid());
			if (profile != null) {
				profile.setAccountFrozen(true);
				profile.creditScore().adjust(-25);
			}
		}
		payTipRewardIfEligible(report);
		return true;
	}

	private void payTipRewardIfEligible(CitizenReport report) throws SQLException {
		if (report.type() != ReportType.TIP_OFF || currencyService == null || centralBank == null) {
			return;
		}
		if (repository.hasTipReward(report.id())) {
			return;
		}
		long reward = EconomyConfig.tipRewardMg();
		if (reward <= 0) {
			return;
		}
		if (!centralBank.spendMunicipalBudget(reward)) {
			return;
		}
		currencyService.deposit(report.reporterUuid(), reward, TransactionType.COMPANY);
		repository.recordTipReward(report.id(), report.reporterUuid(), reward);
		MinecraftServer server = McEconomyMod.getEconomyManager().server();
		if (server != null) {
			ServerPlayer reporter = server.getPlayerList().getPlayer(report.reporterUuid());
			if (reporter != null) {
				reporter.sendSystemMessage(Component.literal(
						"§a[Ihbar Odulu] §fDogru ihbar icin " + GoldStandard.formatMilligrams(reward)
								+ " belediye butcesinden odendi."));
			}
		}
	}

	public boolean markInvestigating(long id) throws SQLException {
		Optional<CitizenReport> opt = repository.findById(id);
		if (opt.isEmpty() || opt.get().status() != ReportStatus.OPEN) {
			return false;
		}
		repository.update(opt.get().withStatus(ReportStatus.INVESTIGATING, null, null));
		if (opt.get().targetUuid() != null) {
			triggerRobberyInvestigation(opt.get().targetUuid());
		}
		return true;
	}

	private void triggerRobberyInvestigation(UUID targetUuid) {
		var manager = McEconomyMod.getEconomyManager();
		if (manager != null && manager.bankRobberyJusticeService() != null) {
			manager.bankRobberyJusticeService().onReportAgainstTarget(targetUuid);
		}
	}

	private UUID resolveTarget(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		return BalanceCommand.findPlayerUuid(name);
	}

	private void markInvestigatingForTarget(UUID targetUuid) throws SQLException {
		for (CitizenReport report : repository.loadOpenForTarget(targetUuid)) {
			if (report.status() == ReportStatus.OPEN) {
				repository.update(report.withStatus(ReportStatus.INVESTIGATING, "Sistem otomatik tarama", null));
			}
		}
	}

	private void notifyAutomaticInvestigation(String reporterName, String targetName, long id,
			String category, boolean complaint) {
		String kind = complaint ? "Sikayet" : "Ihbar";
		String target = targetName != null && !targetName.isBlank() ? targetName : "—";
		notifyStaff(kind + " #" + id + " [" + category + "] → " + target
				+ " — §7MASAK otomatik kasa taramasi baslatti (OP gerekmez)");
		MinecraftServer server = McEconomyMod.getEconomyManager().server();
		if (server == null || targetName == null || targetName.isBlank()) {
			return;
		}
		UUID targetUuid = resolveTarget(targetName);
		if (targetUuid == null) {
			return;
		}
		ServerPlayer targetPlayer = server.getPlayerList().getPlayer(targetUuid);
		if (targetPlayer != null) {
			targetPlayer.sendSystemMessage(Component.literal(
					"§e[MASAK] §fHakkınızda " + kind.toLowerCase() + " var — kisisel kasaniz otomatik denetlenecek."));
		}
	}

	private void notifyStaff(String message) {
		MinecraftServer server = McEconomyMod.getEconomyManager().server();
		if (server == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (server.getPlayerList().isOp(player.nameAndId())) {
				player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
						"§c[Adalet] §f" + message));
			}
		}
	}
}
