package com.mceconomy.company;

import com.mceconomy.McEconomyMod;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.CurrencyService;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.economy.TransactionType;
import com.mceconomy.job.JobCategory;
import com.mceconomy.job.JobType;
import com.mceconomy.market.Commodity;
import com.mceconomy.persistence.repo.WorkforceRepository;
import com.mceconomy.world.JobSeekerNpcSpawner;
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
import java.util.concurrent.ThreadLocalRandom;

public final class NpcWorkforceService {
	private final Map<Long, JobApplication> applications = new HashMap<>();
	private final Map<Long, NpcEmployee> employees = new HashMap<>();
	private final Map<Integer, List<Long>> applicationsByCompany = new HashMap<>();
	private final Map<Integer, List<Long>> employeesByCompany = new HashMap<>();

	private final WorkforceRepository repository;
	private final CompanyManager companyManager;
	private final CurrencyService currencyService;
	private CompanyProductPipeline productPipeline;
	private PlayerEmploymentService playerEmploymentService;

	public NpcWorkforceService(WorkforceRepository repository, CompanyManager companyManager,
			CurrencyService currencyService) {
		this.repository = repository;
		this.companyManager = companyManager;
		this.currencyService = currencyService;
	}

	public void bindProductPipeline(CompanyProductPipeline productPipeline) {
		this.productPipeline = productPipeline;
	}

	public void bindPlayerEmployment(PlayerEmploymentService playerEmploymentService) {
		this.playerEmploymentService = playerEmploymentService;
	}

	public int employeeCountForCompany(int companyId) {
		return employeesByCompany.getOrDefault(companyId, List.of()).size();
	}

	private int totalEmployeeCount(int companyId) {
		int players = playerEmploymentService != null
				? playerEmploymentService.employeeCountForCompany(companyId) : 0;
		return employeeCountForCompany(companyId) + players;
	}

	public void load() throws SQLException {
		applications.clear();
		employees.clear();
		applicationsByCompany.clear();
		employeesByCompany.clear();
		for (JobApplication app : repository.loadPendingApplications()) {
			registerApplication(app);
		}
		for (NpcEmployee employee : repository.loadAllEmployees()) {
			registerEmployee(employee);
		}
	}

	public void saveAll() throws SQLException {
		for (JobApplication app : applications.values()) {
			if (app.status() == ApplicationStatus.PENDING || app.id() > 0) {
				repository.saveApplication(app);
			}
		}
		for (NpcEmployee employee : employees.values()) {
			repository.saveEmployee(employee);
		}
	}

	public boolean hasMinimumWealth(UUID owner) {
		long wallet = currencyService.getBalance(owner);
		return wallet >= EconomyConfig.minCompanyWealthMg();
	}

	public long totalWealth(UUID owner, long bankMg) {
		return currencyService.getBalance(owner) + bankMg;
	}

	public void tickApplications(MinecraftServer server) {
		if (ThreadLocalRandom.current().nextDouble() > EconomyConfig.workforceApplicationChance()) {
			return;
		}
		for (Company company : companyManager.allCompanies()) {
			try {
				if (repository.countPendingForCompany(company.id()) >= EconomyConfig.maxPendingApplications()) {
					continue;
				}
				int employeeCount = employeesByCompany.getOrDefault(company.id(), List.of()).size();
				if (employeeCount >= EconomyConfig.maxEmployeesPerCompany()) {
					continue;
				}
				ServerPlayer owner = server.getPlayerList().getPlayer(company.ownerUuid());
				if (owner == null) {
					continue;
				}
				createRandomApplication(server, company, owner);
			} catch (SQLException e) {
				McEconomyMod.LOGGER.error("Is basvurusu olusturulamadi", e);
			}
		}
	}

	private void createRandomApplication(MinecraftServer server, Company company, ServerPlayer owner)
			throws SQLException {
		JobType role = NpcNameGenerator.randomRole();
		String name = NpcNameGenerator.randomName();
		long salary = EconomyConfig.baseNpcSalaryMg()
				+ ThreadLocalRandom.current().nextLong(EconomyConfig.maxNpcSalaryBonusMg());
		String message = NpcNameGenerator.randomPitch(role);
		String entityUuid = JobSeekerNpcSpawner.spawnNearPlayer(owner, name, role.displayName());
		JobApplication app = JobApplication.createPending(company.id(), name, role.id(), salary, message, entityUuid);
		repository.saveApplication(app);
		registerApplication(app);
		owner.sendSystemMessage(Component.literal(
				"§e[Is Basvurusu] §f" + name + " (" + role.displayName() + ") sirketinize basvurdu. §7/sirket basvurular"));
	}

	public List<JobApplication> pendingForOwner(UUID ownerUuid, String companyNameOrNull) {
		List<JobApplication> result = new ArrayList<>();
		for (JobApplication app : applications.values()) {
			if (app.status() != ApplicationStatus.PENDING) {
				continue;
			}
			Company company = companyManager.allCompanies().stream()
					.filter(c -> c.id() == app.companyId()).findFirst().orElse(null);
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

	public List<NpcEmployee> employeesForOwner(UUID ownerUuid, String companyNameOrNull) {
		List<NpcEmployee> result = new ArrayList<>();
		for (NpcEmployee employee : employees.values()) {
			Company company = companyManager.allCompanies().stream()
					.filter(c -> c.id() == employee.companyId()).findFirst().orElse(null);
			if (company == null || !company.ownerUuid().equals(ownerUuid)) {
				continue;
			}
			if (companyNameOrNull != null && !company.name().equalsIgnoreCase(companyNameOrNull)) {
				continue;
			}
			result.add(employee);
		}
		return result;
	}

	public Optional<JobApplication> findApplication(long id) {
		return Optional.ofNullable(applications.get(id));
	}

	public boolean acceptApplication(UUID ownerUuid, long applicationId, MinecraftServer server) {
		JobApplication app = applications.get(applicationId);
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
		try {
			app.setStatus(ApplicationStatus.ACCEPTED);
			repository.saveApplication(app);
			applications.remove(app.id());
			removeFromCompanyList(applicationsByCompany, company.id(), app.id());

			NpcEmployee employee = NpcEmployee.hire(company.id(), app.npcName(), app.roleId(), app.requestedSalaryMg());
			repository.saveEmployee(employee);
			registerEmployee(employee);

			if (app.entityUuid() != null && server != null) {
				JobSeekerNpcSpawner.removeSeeker(server, app.entityUuid());
			}
			notifyOwner(server, ownerUuid, app.npcName() + " ise alindi. Maas: "
					+ GoldStandard.formatMilligrams(app.requestedSalaryMg()));
			return true;
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Basvuru kabul edilemedi", e);
			return false;
		}
	}

	public boolean rejectApplication(UUID ownerUuid, long applicationId, MinecraftServer server) {
		JobApplication app = applications.get(applicationId);
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
			if (app.entityUuid() != null && server != null) {
				JobSeekerNpcSpawner.removeSeeker(server, app.entityUuid());
			}
			return true;
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Basvuru reddedilemedi", e);
			return false;
		}
	}

	public boolean fireEmployee(UUID ownerUuid, long employeeId) {
		NpcEmployee employee = employees.get(employeeId);
		if (employee == null) {
			return false;
		}
		Company company = findCompanyById(employee.companyId());
		if (company == null || !company.ownerUuid().equals(ownerUuid)) {
			return false;
		}
		try {
			repository.deleteEmployee(employeeId);
			employees.remove(employeeId);
			removeFromCompanyList(employeesByCompany, company.id(), employeeId);
			return true;
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Calisan kovulamadi", e);
			return false;
		}
	}

	public boolean raiseSalary(UUID ownerUuid, long employeeId, long newSalaryMg) {
		NpcEmployee employee = employees.get(employeeId);
		if (employee == null || newSalaryMg <= 0) {
			return false;
		}
		Company company = findCompanyById(employee.companyId());
		if (company == null || !company.ownerUuid().equals(ownerUuid)) {
			return false;
		}
		employee.setSalaryMg(newSalaryMg);
		try {
			repository.saveEmployee(employee);
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Maas guncellenemedi", e);
			return false;
		}
		return true;
	}

	/** Sahibin cuzdanindan tum calisanlara bir kerelik ikramiye (bir maas) oder. */
	public long payBonus(UUID ownerUuid, String companyNameOrNull) {
		long totalPaid = 0;
		for (NpcEmployee employee : employeesForOwner(ownerUuid, companyNameOrNull)) {
			if (currencyService.withdraw(ownerUuid, employee.salaryMg(), TransactionType.COMPANY)) {
				employee.setLastPaidAt(System.currentTimeMillis());
				totalPaid += employee.salaryMg();
				try {
					repository.saveEmployee(employee);
				} catch (SQLException e) {
					McEconomyMod.LOGGER.error("Ikramiye kaydi basarisiz", e);
				}
			}
		}
		return totalPaid;
	}

	public void processWorkAndPayroll(MinecraftServer server) {
		for (NpcEmployee employee : new ArrayList<>(employees.values())) {
			Company company = findCompanyById(employee.companyId());
			if (company == null) {
				continue;
			}
			JobType role = JobType.fromString(employee.roleId());
			long production = EconomyConfig.baseNpcProductionMg();
			if (role != null) {
				production = (long) (production * roleProductivity(role));
			}
			company.deposit(production);
			employee.addProduction(production);

			long salary = employee.salaryMg();
			boolean paid = false;
			if (company.treasury() >= salary) {
				company.withdraw(salary);
				paid = true;
			} else if (currencyService.withdraw(company.ownerUuid(), salary, TransactionType.COMPANY)) {
				paid = true;
			}

			if (paid) {
				employee.setLastPaidAt(System.currentTimeMillis());
				deliverWorkProducts(server, employee, company, role);
				try {
					repository.saveEmployee(employee);
					companyManager.saveCompany(company);
				} catch (SQLException e) {
					McEconomyMod.LOGGER.error("Calisan kaydi guncellenemedi", e);
				}
			} else {
				try {
					repository.deleteEmployee(employee.id());
					employees.remove(employee.id());
					removeFromCompanyList(employeesByCompany, company.id(), employee.id());
					notifyOwner(server, company.ownerUuid(),
							employee.npcName() + " maas odenemedigi icin ayrildi.");
				} catch (SQLException e) {
					McEconomyMod.LOGGER.error("Calisan silinemedi", e);
				}
			}
		}
	}

	private double roleProductivity(JobType role) {
		return switch (role.category()) {
			case MINING -> 1.4;
			case FARMING -> 1.2;
			case LUMBER -> 1.1;
			case FISHING -> 1.0;
			case HUNTING -> 1.15;
			case TRADING -> 1.25;
			default -> 1.0;
		};
	}

	private void deliverWorkProducts(MinecraftServer server, NpcEmployee employee, Company company, JobType role) {
		if (!EconomyConfig.npcProductDeliveryEnabled() || productPipeline == null || role == null) {
			return;
		}
		Commodity commodity = pickDeliveryCommodity(role);
		if (commodity == null) {
			return;
		}
		int quantity = rollDeliveryQuantity(role);
		if (quantity <= 0) {
			return;
		}
		try {
			productPipeline.processDelivery(server, company, employee.npcName(), role, commodity, quantity);
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Calisan urun teslimi basarisiz", e);
		}
	}

	private Commodity pickDeliveryCommodity(JobType role) {
		if (role.category() == JobCategory.HUNTING
				&& ThreadLocalRandom.current().nextDouble() < EconomyConfig.hunterMeatDeliveryBias()) {
			return Commodity.randomHuntingMeat();
		}
		if (role.category() == JobCategory.MINING
				&& ThreadLocalRandom.current().nextDouble() < 0.55) {
			Commodity[] raw = { Commodity.RAW_IRON, Commodity.RAW_COPPER, Commodity.RAW_GOLD, Commodity.COAL };
			return raw[ThreadLocalRandom.current().nextInt(raw.length)];
		}
		Commodity picked = Commodity.randomForCategory(role.category());
		if (picked != null) {
			return picked;
		}
		return Commodity.randomForCategory(JobCategory.FARMING);
	}

	private int rollDeliveryQuantity(JobType role) {
		int max = EconomyConfig.npcDeliveryMaxItems();
		if (role != null) {
			max = (int) Math.max(1, Math.round(max * roleProductivity(role)));
		}
		return 1 + ThreadLocalRandom.current().nextInt(max);
	}

	private Company findCompanyById(int id) {
		return companyManager.allCompanies().stream().filter(c -> c.id() == id).findFirst().orElse(null);
	}

	private void registerApplication(JobApplication app) {
		applications.put(app.id(), app);
		applicationsByCompany.computeIfAbsent(app.companyId(), k -> new ArrayList<>()).add(app.id());
	}

	private void registerEmployee(NpcEmployee employee) {
		employees.put(employee.id(), employee);
		employeesByCompany.computeIfAbsent(employee.companyId(), k -> new ArrayList<>()).add(employee.id());
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
			owner.sendSystemMessage(Component.literal("§a[Sirket] §f" + message));
		}
	}
}
