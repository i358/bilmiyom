package com.mceconomy.insurance;

import com.mceconomy.McEconomyMod;
import com.mceconomy.company.Company;
import com.mceconomy.company.CompanyManager;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.CurrencyService;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.economy.TransactionType;
import com.mceconomy.persistence.repo.InsuranceRepository;
import com.mceconomy.tax.CentralBank;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InsuranceService {
	private final InsuranceRepository repository;
	private final CurrencyService currencyService;
	private final CompanyManager companyManager;
	private final CentralBank centralBank;
	private final Map<String, InsurancePolicy> activePolicies = new HashMap<>();

	public InsuranceService(InsuranceRepository repository, CurrencyService currencyService,
			CompanyManager companyManager, CentralBank centralBank) {
		this.repository = repository;
		this.currencyService = currencyService;
		this.companyManager = companyManager;
		this.centralBank = centralBank;
	}

	public void load() throws SQLException {
		activePolicies.clear();
		for (InsurancePolicy policy : repository.loadAllActive()) {
			activePolicies.put(key(policy), policy);
		}
	}

	public List<InsurancePolicy> policiesFor(UUID owner) {
		List<InsurancePolicy> list = new ArrayList<>();
		for (InsurancePolicy p : activePolicies.values()) {
			if (p.ownerUuid().equals(owner) && p.active()) {
				list.add(p);
			}
		}
		return list;
	}

	public boolean subscribePersonal(UUID owner) throws SQLException {
		return subscribe(owner, InsurancePolicy.PolicyType.PERSONAL, 0);
	}

	public boolean subscribeCompany(UUID owner, String companyName) throws SQLException {
		Company company = companyManager.find(companyName).orElse(null);
		if (company == null || !company.ownerUuid().equals(owner)) {
			return false;
		}
		return subscribe(owner, InsurancePolicy.PolicyType.COMPANY, company.id());
	}

	public boolean cancel(UUID owner, InsurancePolicy.PolicyType type, int companyId) throws SQLException {
		InsurancePolicy existing = repository.find(owner, type, companyId);
		if (existing == null || !existing.active()) {
			return false;
		}
		InsurancePolicy cancelled = new InsurancePolicy(owner, type, companyId, false,
				existing.coveragePercent(), existing.monthlyPremiumMg(), existing.nextPremiumDueMs());
		repository.save(cancelled);
		activePolicies.remove(key(cancelled));
		return true;
	}

	private boolean subscribe(UUID owner, InsurancePolicy.PolicyType type, int companyId) throws SQLException {
		long premium = type == InsurancePolicy.PolicyType.PERSONAL
				? EconomyConfig.insurancePersonalPremiumMg()
				: EconomyConfig.insuranceCompanyPremiumMg();
		double coverage = EconomyConfig.insuranceCoveragePercent();
		long due = System.currentTimeMillis() + EconomyConfig.insurancePremiumIntervalMs();
		if (type == InsurancePolicy.PolicyType.PERSONAL) {
			if (!currencyService.withdraw(owner, premium, TransactionType.TAX)) {
				return false;
			}
			centralBank.addMunicipalBudget(premium);
		} else {
			Company company = companyManager.allCompanies().stream()
					.filter(c -> c.id() == companyId).findFirst().orElse(null);
			if (company == null || company.treasury() < premium) {
				return false;
			}
			company.withdraw(premium);
			centralBank.addMunicipalBudget(premium);
			companyManager.saveCompany(company);
		}
		InsurancePolicy policy = new InsurancePolicy(owner, type, companyId, true, coverage, premium, due);
		repository.save(policy);
		activePolicies.put(key(policy), policy);
		return true;
	}

	public void tickPremiums() {
		long now = System.currentTimeMillis();
		for (InsurancePolicy policy : new HashMap<>(activePolicies).values()) {
			if (now < policy.nextPremiumDueMs()) {
				continue;
			}
			try {
				if (!chargePremium(policy)) {
					cancel(policy.ownerUuid(), policy.type(), policy.companyId());
				}
			} catch (SQLException e) {
				McEconomyMod.LOGGER.error("Sigorta primi basarisiz", e);
			}
		}
	}

	private boolean chargePremium(InsurancePolicy policy) throws SQLException {
		long premium = policy.monthlyPremiumMg();
		if (policy.type() == InsurancePolicy.PolicyType.PERSONAL) {
			if (!currencyService.withdraw(policy.ownerUuid(), premium, TransactionType.TAX)) {
				return false;
			}
		} else {
			Company company = companyManager.allCompanies().stream()
					.filter(c -> c.id() == policy.companyId()).findFirst().orElse(null);
			if (company == null || company.treasury() < premium) {
				return false;
			}
			company.withdraw(premium);
			companyManager.saveCompany(company);
		}
		centralBank.addMunicipalBudget(premium);
		InsurancePolicy next = new InsurancePolicy(policy.ownerUuid(), policy.type(), policy.companyId(),
				true, policy.coveragePercent(), premium,
				System.currentTimeMillis() + EconomyConfig.insurancePremiumIntervalMs());
		repository.save(next);
		activePolicies.put(key(next), next);
		return true;
	}

	public void payRobberyClaims(long stolenValueMg, MinecraftServer server) {
		if (stolenValueMg <= 0) {
			return;
		}
		for (InsurancePolicy policy : activePolicies.values()) {
			long payout = (long) (stolenValueMg * policy.coveragePercent()
					* EconomyConfig.insurancePayoutShareOfLoss());
			if (payout <= 0 || !centralBank.spendMunicipalBudget(payout)) {
				continue;
			}
			if (policy.type() == InsurancePolicy.PolicyType.PERSONAL) {
				currencyService.deposit(policy.ownerUuid(), payout, TransactionType.COMPANY);
				notify(server, policy.ownerUuid(),
						"Sigorta tazminati: " + GoldStandard.formatMilligrams(payout));
			} else {
				Company company = companyManager.allCompanies().stream()
						.filter(c -> c.id() == policy.companyId()).findFirst().orElse(null);
				if (company != null) {
					com.mceconomy.company.CompanyTreasuryHelper.creditCompanyOrOwnerDebt(
							currencyService, company, payout, TransactionType.COMPANY);
					try {
						companyManager.saveCompany(company);
					} catch (SQLException e) {
						McEconomyMod.LOGGER.error("Sigorta sirket odemesi kaydi", e);
					}
					notify(server, policy.ownerUuid(),
							"Sirket sigortasi (" + company.name() + "): "
									+ GoldStandard.formatMilligrams(payout));
				}
			}
		}
	}

	private void notify(MinecraftServer server, UUID uuid, String msg) {
		if (server == null) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(uuid);
		if (player != null) {
			player.sendSystemMessage(Component.literal("§b[Sigorta] §f" + msg));
		}
	}

	private static String key(InsurancePolicy policy) {
		return policy.ownerUuid() + ":" + policy.type() + ":" + policy.companyId();
	}
}
