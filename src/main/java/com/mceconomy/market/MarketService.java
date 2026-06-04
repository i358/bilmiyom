package com.mceconomy.market;

import com.mceconomy.company.CeoProfitSplit;
import com.mceconomy.company.Company;
import com.mceconomy.company.EmploymentRole;
import com.mceconomy.company.PlayerEmploymentService;
import com.mceconomy.facility.FacilityDepotService;
import com.mceconomy.facility.FacilityItemTags;
import com.mceconomy.job.JobType;
import com.mceconomy.facility.FacilityType;
import com.mceconomy.company.CompanyManager;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.CurrencyService;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.economy.TransactionType;
import net.minecraft.network.chat.Component;
import com.mceconomy.job.JobItemTags;
import com.mceconomy.job.JobManager;
import com.mceconomy.persistence.repo.MarketRepository;
import com.mceconomy.tax.TaxService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;

public final class MarketService {
	private final Map<Commodity, CommodityState> states = new EnumMap<>(Commodity.class);
	private final MarketRepository repository;
	private final MarketPriceEngine priceEngine;
	private final EconomyIndex economyIndex;
	private final CurrencyService currencyService;
	private final TaxService taxService;
	private JobManager jobManager;
	private FacilityDepotService depotService;
	private PlayerEmploymentService playerEmploymentService;
	private CompanyManager companyManager;

	public MarketService(MarketRepository repository, CurrencyService currencyService, TaxService taxService) {
		this.repository = repository;
		this.currencyService = currencyService;
		this.taxService = taxService;
		for (Commodity commodity : Commodity.values()) {
			states.put(commodity, CommodityState.createDefault(commodity));
		}
		this.priceEngine = new MarketPriceEngine(states);
		this.economyIndex = new EconomyIndex(priceEngine);
	}

	public void load() throws SQLException {
		Map<Commodity, CommodityState> loaded = repository.loadAll();
		for (Commodity commodity : Commodity.values()) {
			CommodityState state = loaded.get(commodity);
			if (state == null || (long) state.basePrice() != commodity.basePrice()) {
				states.put(commodity, CommodityState.createDefault(commodity));
			} else {
				states.put(commodity, state);
			}
		}
		if (loaded.isEmpty()) {
			saveAll();
		}
	}

	public void saveAll() throws SQLException {
		repository.saveAll(states);
	}

	public MarketPriceEngine priceEngine() {
		return priceEngine;
	}

	public CommodityState commodityState(Commodity commodity) {
		return states.get(commodity);
	}

	public EconomyIndex economyIndex() {
		return economyIndex;
	}

	public void bindJobManager(JobManager jobManager) {
		this.jobManager = jobManager;
	}

	public void bindDepot(FacilityDepotService depotService) {
		this.depotService = depotService;
	}

	public void bindEmployment(PlayerEmploymentService playerEmploymentService, CompanyManager companyManager) {
		this.playerEmploymentService = playerEmploymentService;
		this.companyManager = companyManager;
	}

	public Map<Commodity, CommodityState> states() {
		return states;
	}

	public boolean buy(ServerPlayer player, Commodity commodity, int quantity) {
		if (!commodity.buyable() || quantity <= 0) {
			return false;
		}
		long unitPrice = priceEngine.getUnitPrice(commodity);
		long total = unitPrice * quantity;
		long tax = taxService.calculateTradeTax(total);
		long cityTax = taxService.calculateCityTax(total);
		long grandTotal = total + tax + cityTax;

		if (!currencyService.withdraw(player.getUUID(), grandTotal, TransactionType.MARKET_BUY)) {
			return false;
		}

		int takenFromDepot = deliverFromDepot(player, commodity.item(), quantity);
		int remaining = quantity - takenFromDepot;
		if (remaining > 0) {
			ItemStack stack = new ItemStack(commodity.item(), remaining);
			if (!player.getInventory().add(stack)) {
				currencyService.deposit(player.getUUID(), grandTotal, TransactionType.MARKET_BUY);
				returnDepotStacks(player, commodity.item(), takenFromDepot);
				return false;
			}
		}
		priceEngine.onBuy(commodity, quantity);
		taxService.collectTax(tax + cityTax);
		return true;
	}

	public boolean sell(ServerPlayer player, Commodity commodity, int quantity) {
		if (!commodity.sellable() || quantity <= 0) {
			return false;
		}
		if (countItems(player, commodity) < quantity) {
			if (hasWantedCommodity(player, commodity)) {
				player.sendSystemMessage(Component.literal(
						"§c[Piyasa] §fKayip MB seri numarali zimmetli esya satilamaz."));
			}
			return false;
		}
		removeItems(player, commodity, quantity);
		if (depotService != null) {
			int stored = depotService.depositItem((ServerLevel) player.level(), FacilityType.MARKET, commodity.item(), quantity);
			if (stored < quantity) {
				giveItems(player, commodity.item(), quantity - stored);
			}
		}

		long unitPrice = priceEngine.getUnitPrice(commodity);
		if (jobManager != null) {
			unitPrice = jobManager.applySellBonus(player.getUUID(), commodity, unitPrice);
		}
		long total = unitPrice * quantity;
		long tax = taxService.calculateTradeTax(total);
		long cityTax = taxService.calculateCityTax(total);
		long payout = total - tax - cityTax;
		long playerPayout = applyEmployedCompanyShare(player, commodity, payout);

		currencyService.deposit(player.getUUID(), playerPayout, TransactionType.MARKET_SELL);
		priceEngine.onSell(commodity, quantity);
		taxService.collectTax(tax + cityTax);
		return true;
	}

	private long applyEmployedCompanyShare(ServerPlayer player, Commodity commodity, long payout) {
		if (playerEmploymentService == null || companyManager == null || payout <= 0) {
			return payout;
		}
		var employment = playerEmploymentService.employmentForPlayer(player.getUUID());
		if (employment.isEmpty()) {
			return payout;
		}
		if (EmploymentRole.isCeo(employment.get().roleId())) {
			Company company = companyManager.allCompanies().stream()
					.filter(c -> c.id() == employment.get().companyId())
					.findFirst()
					.orElse(null);
			if (company == null) {
				return payout;
			}
			CeoProfitSplit.distribute(currencyService, company, player.getUUID(), payout, TransactionType.MARKET_SELL);
			try {
				companyManager.saveCompany(company);
			} catch (java.sql.SQLException e) {
				com.mceconomy.McEconomyMod.LOGGER.error("CEO market payi kaydedilemedi", e);
			}
			return 0;
		}
		JobType role = JobType.fromString(employment.get().roleId());
		if (role == null || !commodity.matchesJob(role)) {
			return payout;
		}
		Company company = companyManager.allCompanies().stream()
				.filter(c -> c.id() == employment.get().companyId())
				.findFirst()
				.orElse(null);
		if (company == null) {
			return payout;
		}
		long companyShare = (long) (payout * EconomyConfig.employedMarketCompanyShare());
		if (companyShare <= 0) {
			return payout;
		}
		com.mceconomy.company.CompanyTreasuryHelper.creditCompanyOrOwnerDebt(
				currencyService, company, companyShare, TransactionType.MARKET_SELL);
		try {
			companyManager.saveCompany(company);
		} catch (java.sql.SQLException e) {
			com.mceconomy.McEconomyMod.LOGGER.error("Sirket market payi kaydedilemedi", e);
		}
		var mcServer = com.mceconomy.McEconomyMod.getEconomyManager().server();
		var owner = mcServer != null ? mcServer.getPlayerList().getPlayer(company.ownerUuid()) : null;
		if (owner != null) {
			owner.sendSystemMessage(Component.literal(
					"§e[Sirket] §f" + player.getName().getString() + " pazarda satti — kasaya +"
							+ GoldStandard.formatMilligrams(companyShare)));
		}
		player.sendSystemMessage(Component.literal(
				"§7[Sirket] Satisin " + GoldStandard.formatMilligrams(companyShare) + " sirket kasasina aktarildi."));
		return payout - companyShare;
	}

	private int countItems(ServerPlayer player, Commodity commodity) {
		int total = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(commodity.item()) && !JobItemTags.isJobLoan(stack)
					&& !FacilityItemTags.matchesWantedSerial(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static boolean hasWantedCommodity(ServerPlayer player, Commodity commodity) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(commodity.item()) && FacilityItemTags.matchesWantedSerial(stack)) {
				return true;
			}
		}
		return false;
	}

	private int removeItems(ServerPlayer player, Commodity commodity, int quantity) {
		int remaining = quantity;
		for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(commodity.item()) && !JobItemTags.isJobLoan(stack)
					&& !FacilityItemTags.matchesWantedSerial(stack)) {
				int take = Math.min(stack.getCount(), remaining);
				stack.shrink(take);
				remaining -= take;
			}
		}
		return quantity - remaining;
	}

	public void decayPrices() {
		priceEngine.decayAll();
	}

	/** NPC uretimi — piyasa fiyatindan sirket kasasina satis (vergi dahil). */
	public long systemSellForCompany(Company company, Commodity commodity, int quantity) {
		return systemSellForCompany(company, commodity, quantity, null);
	}

	public long systemSellForCompany(Company company, Commodity commodity, int quantity, java.util.UUID workerUuid) {
		if (company == null || !commodity.sellable() || quantity <= 0) {
			return 0;
		}
		long unitPrice = priceEngine.getUnitPrice(commodity);
		long total = unitPrice * quantity;
		long tax = taxService.calculateTradeTax(total);
		long cityTax = taxService.calculateCityTax(total);
		long payout = total - tax - cityTax;
		if (payout <= 0) {
			return 0;
		}
		boolean ceoSplit = workerUuid != null && playerEmploymentService != null
				&& playerEmploymentService.employmentForPlayer(workerUuid)
						.filter(e -> e.companyId() == company.id() && EmploymentRole.isCeo(e.roleId()))
						.isPresent();
		if (ceoSplit) {
			CeoProfitSplit.distribute(currencyService, company, workerUuid, payout, TransactionType.MARKET_SELL);
		} else {
			com.mceconomy.company.CompanyTreasuryHelper.creditCompanyOrOwnerDebt(
					currencyService, company, payout, TransactionType.MARKET_SELL);
		}
		priceEngine.onSell(commodity, quantity);
		taxService.collectTax(tax + cityTax);
		var mcServer = com.mceconomy.McEconomyMod.getEconomyManager().server();
		if (depotService != null && mcServer != null) {
			depotService.depositItem(mcServer.overworld(), FacilityType.MARKET, commodity.item(), quantity);
		}
		return payout;
	}

	private int deliverFromDepot(ServerPlayer player, net.minecraft.world.item.Item item, int quantity) {
		if (depotService == null) {
			return 0;
		}
		int taken = 0;
		for (ItemStack stack : depotService.withdrawItemStacks(
				(ServerLevel) player.level(), FacilityType.MARKET, item, quantity)) {
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
			taken += stack.getCount();
		}
		return taken;
	}

	private void returnDepotStacks(ServerPlayer player, net.minecraft.world.item.Item item, int quantity) {
		if (depotService == null || quantity <= 0) {
			return;
		}
		depotService.depositItem((ServerLevel) player.level(), FacilityType.MARKET, item, quantity);
	}

	private static void giveItems(ServerPlayer player, net.minecraft.world.item.Item item, int quantity) {
		player.getInventory().add(new ItemStack(item, quantity));
	}
}
