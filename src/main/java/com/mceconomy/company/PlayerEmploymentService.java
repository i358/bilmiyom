package com.mceconomy.company;

import com.mceconomy.McEconomyMod;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.CurrencyService;
import com.mceconomy.economy.EconomyEventCategory;
import com.mceconomy.economy.EconomyEventDirection;
import com.mceconomy.economy.EconomyEventService;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.economy.TransactionType;
import com.mceconomy.tax.TaxService;
import com.mceconomy.job.JobType;
import com.mceconomy.persistence.repo.PlayerEmploymentRepository;
import com.mceconomy.persistence.repo.SalaryPaymentRepository;
import com.mceconomy.player.PlayerEconomyProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PlayerEmploymentService {
	private final Map<Long, PlayerJobApplication> applications = new HashMap<>();
	private final Map<Long, PlayerEmployment> employments = new HashMap<>();
	private final Map<UUID, Long> employmentByPlayer = new HashMap<>();
	private final Map<Integer, List<Long>> applicationsByCompany = new HashMap<>();
	private final Map<Integer, List<Long>> employmentsByCompany = new HashMap<>();

	private final PlayerEmploymentRepository repository;
	private final SalaryPaymentRepository salaryPaymentRepository;
	private final Map<UUID, PlayerEconomyProfile> profiles;
	private final CompanyManager companyManager;
	private final CurrencyService currencyService;
	private final NpcWorkforceService npcWorkforceService;
	private final TaxService taxService;
	private EconomyEventService economyEventService;

	public PlayerEmploymentService(PlayerEmploymentRepository repository, SalaryPaymentRepository salaryPaymentRepository,
			Map<UUID, PlayerEconomyProfile> profiles, CompanyManager companyManager,
			CurrencyService currencyService, NpcWorkforceService npcWorkforceService, TaxService taxService) {
		this.repository = repository;
		this.salaryPaymentRepository = salaryPaymentRepository;
		this.profiles = profiles;
		this.companyManager = companyManager;
		this.currencyService = currencyService;
		this.npcWorkforceService = npcWorkforceService;
		this.taxService = taxService;
	}

	public void bindEconomyEventService(EconomyEventService economyEventService) {
		this.economyEventService = economyEventService;
	}

	public void load() throws SQLException {
		applications.clear();
		employments.clear();
		employmentByPlayer.clear();
		applicationsByCompany.clear();
		employmentsByCompany.clear();
		for (PlayerJobApplication app : repository.loadPendingApplications()) {
			registerApplication(app);
		}
		for (PlayerEmployment employment : repository.loadAllEmployments()) {
			registerEmployment(employment);
		}
	}

	public void saveAll() throws SQLException {
		for (PlayerJobApplication app : applications.values()) {
			if (app.status() == ApplicationStatus.PENDING || app.id() > 0) {
				repository.saveApplication(app);
			}
		}
		for (PlayerEmployment employment : employments.values()) {
			repository.saveEmployment(employment);
		}
	}

	public Optional<PlayerEmployment> employmentForPlayer(UUID playerUuid) {
		Long id = employmentByPlayer.get(playerUuid);
		return id != null ? Optional.ofNullable(employments.get(id)) : Optional.empty();
	}

	/** Sirkette calisiyorsa sirket rolu, degilse kisisel meslek. */
	public Optional<JobType> resolveWorkJobType(UUID playerUuid) {
		Optional<PlayerEmployment> employment = employmentForPlayer(playerUuid);
		if (employment.isPresent()) {
			if (!EmploymentRole.isCeo(employment.get().roleId())) {
				JobType role = JobType.fromString(employment.get().roleId());
				if (role != null) {
					return Optional.of(role);
				}
			}
		}
		PlayerEconomyProfile profile = profiles.get(playerUuid);
		if (profile != null && profile.jobType() != null) {
			return Optional.of(profile.jobType());
		}
		return Optional.empty();
	}

	public Optional<Company> companyForPlayer(UUID playerUuid) {
		return employmentForPlayer(playerUuid)
				.flatMap(e -> companyManager.allCompanies().stream()
						.filter(c -> c.id() == e.companyId())
						.findFirst());
	}

	public Optional<PlayerJobApplication> pendingApplicationForPlayer(UUID playerUuid) {
		return applications.values().stream()
				.filter(a -> a.status() == ApplicationStatus.PENDING && a.playerUuid().equals(playerUuid))
				.findFirst();
	}

	public boolean apply(ServerPlayer player, MinecraftServer server, String companyName, String roleId,
			long requestedSalaryMg, String message) {
		if (employmentForPlayer(player.getUUID()).isPresent()) {
			player.sendSystemMessage(Component.literal("§cZaten bir sirkette calisiyorsunuz. §7/is ayril"));
			return false;
		}
		if (pendingApplicationForPlayer(player.getUUID()).isPresent()) {
			player.sendSystemMessage(Component.literal("§cBekleyen basvurunuz var. §7/is basvuru-iptal"));
			return false;
		}
		boolean ceoApplication = EmploymentRole.isCeo(roleId);
		JobType role = ceoApplication ? null : JobType.fromString(roleId);
		if (!ceoApplication && role == null) {
			player.sendSystemMessage(Component.literal("§cGecersiz rol. Ornek: madenci, ciftci, ceo"));
			return false;
		}
		if (ceoApplication) {
			if (requestedSalaryMg != 0) {
				player.sendSystemMessage(Component.literal("§cCEO basvurusunda maas belirtilmez (0)."));
				return false;
			}
		} else {
			if (requestedSalaryMg < EconomyConfig.baseNpcSalaryMg()) {
				player.sendSystemMessage(Component.literal("§cMinimum maas: "
						+ GoldStandard.formatMilligrams(EconomyConfig.baseNpcSalaryMg())));
				return false;
			}
			long maxSalary = EconomyConfig.baseNpcSalaryMg() + EconomyConfig.maxNpcSalaryBonusMg();
			if (requestedSalaryMg > maxSalary) {
				player.sendSystemMessage(Component.literal("§cMaksimum maas: "
						+ GoldStandard.formatMilligrams(maxSalary)));
				return false;
			}
		}
		Optional<Company> companyOpt = companyManager.find(companyName);
		if (companyOpt.isEmpty()) {
			player.sendSystemMessage(Component.literal("§cSirket bulunamadi: " + companyName));
			return false;
		}
		Company company = companyOpt.get();
		if (company.ownerUuid().equals(player.getUUID())) {
			player.sendSystemMessage(Component.literal("§cKendi sirketinize basvuramazsiniz."));
			return false;
		}
		try {
			if (ceoApplication && (companyHasCeo(company.id()) || companyHasPendingCeo(company.id()))) {
				player.sendSystemMessage(Component.literal("§cBu sirketin zaten bir CEO basvurusu veya ortagi var."));
				return false;
			}
			if (repository.countPendingForCompany(company.id()) >= EconomyConfig.maxPendingApplications()) {
				player.sendSystemMessage(Component.literal("§cBu sirket basvuru limitine ulasti."));
				return false;
			}
			String storedRole = ceoApplication ? EmploymentRole.CEO_ID : role.id();
			String pitch = message != null && !message.isBlank() ? message
					: ceoApplication
							? "CEO ortagi olmak istiyorum (kazanc yarisi sirket/oyuncu)."
							: role.displayName() + " olarak calismak istiyorum.";
			PlayerJobApplication app = PlayerJobApplication.createPending(
					company.id(), player.getUUID(), player.getName().getString(), storedRole, requestedSalaryMg, pitch);
			repository.saveApplication(app);
			registerApplication(app);
			if (ceoApplication) {
				player.sendSystemMessage(Component.literal(
						"§aCEO basvurusu gonderildi: §f" + company.name()
								+ " §7(kazancin %" + (int) (EmploymentRole.companyProfitShare() * 100)
								+ "'si sirket, %" + (int) (EmploymentRole.playerProfitShare() * 100) + "'si size)"));
			} else {
				player.sendSystemMessage(Component.literal(
						"§aBasvuru gonderildi: §f" + company.name() + " §7(" + role.displayName() + ", "
								+ GoldStandard.formatMilligrams(requestedSalaryMg) + ")"));
			}
			ServerPlayer owner = server.getPlayerList().getPlayer(company.ownerUuid());
			if (owner != null) {
				owner.sendSystemMessage(Component.literal(
						"§e[Oyuncu Basvurusu] §f" + player.getName().getString() + " ("
								+ EmploymentRole.displayName(storedRole) + ") — §7/sirket basvurular"));
			}
			return true;
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Oyuncu basvurusu kaydedilemedi", e);
			return false;
		}
	}

	public List<PlayerJobApplication> pendingForOwner(UUID ownerUuid, String companyNameOrNull) {
		List<PlayerJobApplication> result = new ArrayList<>();
		for (PlayerJobApplication app : applications.values()) {
			if (app.status() != ApplicationStatus.PENDING) {
				continue;
			}
			Company company = findCompanyById(app.companyId());
			if (company == null || !company.ownerUuid().equals(ownerUuid)) {
				continue;
			}
			if (companyNameOrNull != null && !company.name().equalsIgnoreCase(companyNameOrNull)) {
				continue;
			}
			result.add(app);
		}
		return result;
	}

	public List<PlayerEmployment> employeesForOwner(UUID ownerUuid, String companyNameOrNull) {
		List<PlayerEmployment> result = new ArrayList<>();
		for (PlayerEmployment employment : employments.values()) {
			Company company = findCompanyById(employment.companyId());
			if (company == null || !company.ownerUuid().equals(ownerUuid)) {
				continue;
			}
			if (companyNameOrNull != null && !company.name().equalsIgnoreCase(companyNameOrNull)) {
				continue;
			}
			result.add(employment);
		}
		return result;
	}

	public boolean acceptApplication(UUID ownerUuid, long applicationId, MinecraftServer server) {
		PlayerJobApplication app = applications.get(applicationId);
		if (app == null || app.status() != ApplicationStatus.PENDING) {
			return false;
		}
		Company company = findCompanyById(app.companyId());
		if (company == null || !company.ownerUuid().equals(ownerUuid)) {
			return false;
		}
		if (totalEmployeeCount(company.id()) >= EconomyConfig.maxEmployeesPerCompany()) {
			return false;
		}
		if (EmploymentRole.isCeo(app.roleId()) && companyHasCeo(company.id())) {
			return false;
		}
		if (employmentForPlayer(app.playerUuid()).isPresent()) {
			return false;
		}
		try {
			app.setStatus(ApplicationStatus.ACCEPTED);
			repository.saveApplication(app);
			applications.remove(app.id());
			removeFromCompanyList(applicationsByCompany, company.id(), app.id());

			PlayerEmployment employment = PlayerEmployment.hire(
					app.playerUuid(), app.playerName(), company.id(), app.roleId(), app.requestedSalaryMg());
			repository.saveEmployment(employment);
			registerEmployment(employment);

			if (EmploymentRole.isCeo(app.roleId())) {
				notifyPlayer(server, app.playerUuid(),
						"§a" + company.name() + " §6CEO ortagi §aoldunuz! Kazancin %"
								+ (int) (EmploymentRole.playerProfitShare() * 100)
								+ "'si size, %" + (int) (EmploymentRole.companyProfitShare() * 100)
								+ "'si sirket kasasina. §e/gorev al §7— kisisel mesleginizle uretin.");
				notifyOwner(server, ownerUuid, app.playerName() + " CEO ortagi olarak kabul edildi.");
			} else {
				notifyPlayer(server, app.playerUuid(),
						"§a" + company.name() + " sirketinde ise alindiniz! Maas: "
								+ GoldStandard.formatMilligrams(app.requestedSalaryMg())
								+ " §7(gunluk). §e/gorev al §7— sirket gorevi (uretim sirkete gider)");
				notifyOwner(server, ownerUuid, app.playerName() + " oyuncu olarak ise alindi.");
			}
			return true;
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Oyuncu basvurusu kabul edilemedi", e);
			return false;
		}
	}

	public boolean rejectApplication(UUID ownerUuid, long applicationId, MinecraftServer server) {
		PlayerJobApplication app = applications.get(applicationId);
		if (app == null || app.status() != ApplicationStatus.PENDING) {
			return false;
		}
		Company company = findCompanyById(app.companyId());
		if (company == null || !company.ownerUuid().equals(ownerUuid)) {
			return false;
		}
		try {
			app.setStatus(ApplicationStatus.REJECTED);
			repository.saveApplication(app);
			applications.remove(app.id());
			removeFromCompanyList(applicationsByCompany, company.id(), app.id());
			notifyPlayer(server, app.playerUuid(), "§c" + company.name() + " basvurunuzu reddetti.");
			return true;
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Oyuncu basvurusu reddedilemedi", e);
			return false;
		}
	}

	public boolean cancelPendingApplication(UUID playerUuid, MinecraftServer server) {
		PlayerJobApplication app = pendingApplicationForPlayer(playerUuid).orElse(null);
		if (app == null) {
			return false;
		}
		Company company = findCompanyById(app.companyId());
		try {
			app.setStatus(ApplicationStatus.REJECTED);
			repository.saveApplication(app);
			applications.remove(app.id());
			if (company != null) {
				removeFromCompanyList(applicationsByCompany, company.id(), app.id());
				notifyOwner(server, company.ownerUuid(),
						app.playerName() + " is basvurusunu geri cekti.");
			}
			ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
			if (player != null) {
				String companyName = company != null ? company.name() : "?";
				player.sendSystemMessage(Component.literal(
						"§eIs basvurunuz geri cekildi: §f" + companyName));
			}
			return true;
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Oyuncu basvurusu iptal edilemedi", e);
			return false;
		}
	}

	public boolean quit(UUID playerUuid, MinecraftServer server) {
		PlayerEmployment employment = employmentForPlayer(playerUuid).orElse(null);
		if (employment == null) {
			return false;
		}
		Company company = findCompanyById(employment.companyId());
		try {
			repository.deleteEmployment(employment.id());
			unregisterEmployment(employment);
			if (company != null) {
				notifyOwner(server, company.ownerUuid(), employment.playerName() + " isten ayrildi.");
			}
			return true;
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Istifa kaydedilemedi", e);
			return false;
		}
	}

	public boolean fireEmployee(UUID ownerUuid, long employeeId, MinecraftServer server) {
		PlayerEmployment employment = employments.get(employeeId);
		if (employment == null) {
			return false;
		}
		Company company = findCompanyById(employment.companyId());
		if (company == null || !company.ownerUuid().equals(ownerUuid)) {
			return false;
		}
		try {
			repository.deleteEmployment(employeeId);
			unregisterEmployment(employment);
			notifyPlayer(server, employment.playerUuid(), "§c" + company.name() + " sirketinden cikarildiniz.");
			return true;
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Oyuncu calisani kovulamadi", e);
			return false;
		}
	}

	public boolean raiseSalary(UUID ownerUuid, long employeeId, long newSalaryMg) {
		PlayerEmployment employment = employments.get(employeeId);
		if (employment == null || newSalaryMg <= 0) {
			return false;
		}
		Company company = findCompanyById(employment.companyId());
		if (company == null || !company.ownerUuid().equals(ownerUuid)) {
			return false;
		}
		try {
			employment.setSalaryMg(newSalaryMg);
			repository.saveEmployment(employment);
			return true;
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Oyuncu maasi guncellenemedi", e);
			return false;
		}
	}

	public List<SalaryPaymentRepository.SalaryPaymentRow> salaryHistory(UUID playerUuid) {
		try {
			return salaryPaymentRepository.loadForPlayer(playerUuid, 30);
		} catch (SQLException e) {
			return List.of();
		}
	}

	public void processPayroll(MinecraftServer server) {
		long now = System.currentTimeMillis();
		long interval = EconomyConfig.playerDailySalaryIntervalMs();
		var guildService = McEconomyMod.getEconomyManager().guildService();
		for (PlayerEmployment employment : new ArrayList<>(employments.values())) {
			if (EmploymentRole.isCeo(employment.roleId())) {
				continue;
			}
			if (now - employment.lastPaidAt() < interval) {
				continue;
			}
			if (guildService != null && guildService.isMemberOnStrike(employment.playerUuid())) {
				notifyPlayer(server, employment.playerUuid(),
						"§4[Grev] §cLonca grevi nedeniyle maas odemesi ertelendi.");
				continue;
			}
			Company company = findCompanyById(employment.companyId());
			if (company == null) {
				continue;
			}
			long salary = employment.salaryMg();
			long bonus = calculateJobBonus(employment);
			long total = salary + bonus;
			boolean paid = paySalary(company, employment.playerUuid(), total);
			if (paid) {
				employment.setLastPaidAt(now);
				try {
					repository.saveEmployment(employment);
					companyManager.saveCompany(company);
					salaryPaymentRepository.record(employment.playerUuid(), employment.playerName(),
							company.id(), salary, bonus);
				} catch (SQLException e) {
					McEconomyMod.LOGGER.error("Oyuncu maas kaydi guncellenemedi", e);
				}
				String msg = "§a[Maas] §f" + company.name() + " — " + GoldStandard.formatMilligrams(total)
						+ " cuzdaniniza yatirildi.";
				if (bonus > 0) {
					msg += " §7(meslek bonusu: " + GoldStandard.formatMilligrams(bonus) + ")";
				}
				notifyPlayer(server, employment.playerUuid(), msg);
			} else {
				try {
					repository.deleteEmployment(employment.id());
					unregisterEmployment(employment);
					notifyPlayer(server, employment.playerUuid(),
							"§c" + company.name() + " maas odenemedigi icin isten ayrildiniz.");
					notifyOwner(server, company.ownerUuid(),
							employment.playerName() + " maas odenemedigi icin ayrildi.");
				} catch (SQLException e) {
					McEconomyMod.LOGGER.error("Oyuncu isten cikarilamadi", e);
				}
			}
		}
	}

	private long calculateJobBonus(PlayerEmployment employment) {
		PlayerEconomyProfile profile = profiles.get(employment.playerUuid());
		JobType job = profile != null ? profile.jobType() : null;
		JobType role = JobType.fromString(employment.roleId());
		if (job == null || role == null || job.category() != role.category()) {
			return 0;
		}
		return (long) (employment.salaryMg() * EconomyConfig.companyJobBonusRate());
	}

	private boolean paySalary(Company company, UUID playerUuid, long gross) {
		long tax = taxService.calculateIncomeTax(gross);
		long net = gross - tax;
		if (net <= 0) {
			return false;
		}
		boolean paid;
		if (company.treasury() >= gross) {
			company.withdraw(gross);
			paid = currencyService.deposit(playerUuid, net, TransactionType.COMPANY);
		} else if (currencyService.withdraw(company.ownerUuid(), gross, TransactionType.COMPANY)) {
			paid = currencyService.deposit(playerUuid, net, TransactionType.COMPANY);
		} else {
			return false;
		}
		if (paid && tax > 0) {
			taxService.collectTax(tax, "INCOME_TAX", "Maas gelir vergisi");
		}
		if (paid && economyEventService != null) {
			economyEventService.recordPersonal(playerUuid, EconomyEventCategory.EMPLOYMENT,
					EconomyEventDirection.IN, net, company.ownerUuid(), company.name(), 0, "SALARY",
					company.name() + " maasi: " + GoldStandard.formatMilligrams(net));
			economyEventService.recordCompany(company.id(), company.ownerUuid(),
					EconomyEventCategory.TREASURY_OUT, EconomyEventDirection.OUT, gross,
					playerUuid, company.name(), 0, "SALARY",
					"Maas odemesi: " + economyEventService.resolveName(playerUuid));
			if (tax > 0) {
				economyEventService.recordPersonal(playerUuid, EconomyEventCategory.TAX_FEE,
						EconomyEventDirection.OUT, tax, "INCOME_TAX",
						"Maas vergisi: " + GoldStandard.formatMilligrams(tax));
			}
		}
		return paid;
	}

	public int employeeCountForCompany(int companyId) {
		return employmentsByCompany.getOrDefault(companyId, List.of()).size();
	}

	private int totalEmployeeCount(int companyId) {
		return npcWorkforceService.employeeCountForCompany(companyId) + employeeCountForCompany(companyId);
	}

	private boolean companyHasCeo(int companyId) {
		for (PlayerEmployment employment : employments.values()) {
			if (employment.companyId() == companyId && EmploymentRole.isCeo(employment.roleId())) {
				return true;
			}
		}
		return false;
	}

	private boolean companyHasPendingCeo(int companyId) {
		for (PlayerJobApplication app : applications.values()) {
			if (app.companyId() == companyId && app.status() == ApplicationStatus.PENDING
					&& EmploymentRole.isCeo(app.roleId())) {
				return true;
			}
		}
		return false;
	}

	public boolean isCeoPartner(UUID playerUuid) {
		return employmentForPlayer(playerUuid)
				.map(e -> EmploymentRole.isCeo(e.roleId()))
				.orElse(false);
	}

	private Company findCompanyById(int id) {
		return companyManager.allCompanies().stream().filter(c -> c.id() == id).findFirst().orElse(null);
	}

	private void registerApplication(PlayerJobApplication app) {
		applications.put(app.id(), app);
		applicationsByCompany.computeIfAbsent(app.companyId(), k -> new ArrayList<>()).add(app.id());
	}

	private void registerEmployment(PlayerEmployment employment) {
		employments.put(employment.id(), employment);
		employmentByPlayer.put(employment.playerUuid(), employment.id());
		employmentsByCompany.computeIfAbsent(employment.companyId(), k -> new ArrayList<>()).add(employment.id());
	}

	private void unregisterEmployment(PlayerEmployment employment) {
		employments.remove(employment.id());
		employmentByPlayer.remove(employment.playerUuid());
		removeFromCompanyList(employmentsByCompany, employment.companyId(), employment.id());
	}

	private void removeFromCompanyList(Map<Integer, List<Long>> map, int companyId, long id) {
		List<Long> list = map.get(companyId);
		if (list != null) {
			list.remove(id);
		}
	}

	private void notifyOwner(MinecraftServer server, UUID ownerUuid, String message) {
		if (server == null) {
			return;
		}
		ServerPlayer owner = server.getPlayerList().getPlayer(ownerUuid);
		if (owner != null) {
			owner.sendSystemMessage(Component.literal("§e[Sirket] §f" + message));
		}
	}

	private void notifyPlayer(MinecraftServer server, UUID playerUuid, String message) {
		if (server == null) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
		if (player != null) {
			player.sendSystemMessage(Component.literal(message));
		}
	}
}
