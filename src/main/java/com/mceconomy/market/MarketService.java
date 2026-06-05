package com.mceconomy.market;

import com.google.gson.JsonObject;
import com.mceconomy.company.CeoProfitSplit;
import com.mceconomy.debug.DebugSessionLog;
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
import com.mceconomy.persistence.repo.MarketItemRepository;
import com.mceconomy.persistence.repo.MarketRepository;
import com.mceconomy.tax.TaxService;
import net.minecraft.network.chat.Component;
import com.mceconomy.job.JobItemTags;
import com.mceconomy.job.JobManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.sql.SQLException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class MarketService {
	public enum SellFailure {
		NONE,
		NOT_SELLABLE,
		INSUFFICIENT_ITEMS,
		REMOVE_FAILED,
		DEPOT_FULL
	}

	private SellFailure lastSellFailure = SellFailure.NONE;
	private final Map<Commodity, CommodityState> legacyStates = new EnumMap<>(Commodity.class);
	private final Map<String, MarketItemState> itemStates = new HashMap<>();
	private final MarketRepository legacyRepository;
	private final MarketItemRepository itemRepository;
	private final MarketCatalogService catalog = new MarketCatalogService();
	private final MarketPriceEngine priceEngine;
	private final EconomyIndex economyIndex;
	private final CurrencyService currencyService;
	private final TaxService taxService;
	private JobManager jobManager;
	private FacilityDepotService depotService;
	private PlayerEmploymentService playerEmploymentService;
	private CompanyManager companyManager;

	public MarketService(MarketRepository legacyRepository, MarketItemRepository itemRepository,
			CurrencyService currencyService, TaxService taxService) {
		this.legacyRepository = legacyRepository;
		this.itemRepository = itemRepository;
		this.currencyService = currencyService;
		this.taxService = taxService;
		catalog.bootstrap();
		for (MarketItemEntry entry : catalog.allSorted()) {
			itemStates.put(entry.itemId(), MarketItemState.createDefault(entry));
		}
		for (Commodity commodity : Commodity.values()) {
			legacyStates.put(commodity, CommodityState.createDefault(commodity));
		}
		this.priceEngine = new MarketPriceEngine(itemStates, catalog);
		this.economyIndex = new EconomyIndex(priceEngine, catalog);
	}

	public void load() throws SQLException {
		catalog.bootstrap();
		Map<Commodity, CommodityState> loadedLegacy = legacyRepository.loadAll();
		for (Commodity commodity : Commodity.values()) {
			CommodityState state = loadedLegacy.get(commodity);
			if (state == null || (long) state.basePrice() != commodity.basePrice()) {
				legacyStates.put(commodity, CommodityState.createDefault(commodity));
			} else {
				legacyStates.put(commodity, state);
				String itemId = ItemPriceHeuristic.itemId(commodity.item());
				itemStates.put(itemId, new MarketItemState(
						itemId, state.price(), state.basePrice(), state.supplyIndex(), state.demandIndex()));
			}
		}
		Map<String, MarketItemState> loadedItems = itemRepository.loadAll();
		for (MarketItemEntry entry : catalog.allSorted()) {
			MarketItemState loaded = loadedItems.get(entry.itemId());
			if (loaded != null && Math.abs(loaded.basePrice() - entry.basePriceMg()) < 1) {
				itemStates.put(entry.itemId(), loaded);
			} else if (!itemStates.containsKey(entry.itemId())) {
				itemStates.put(entry.itemId(), MarketItemState.createDefault(entry));
			}
		}
		if (loadedLegacy.isEmpty() && loadedItems.isEmpty()) {
			saveAll();
		}
	}

	public void saveAll() throws SQLException {
		legacyRepository.saveAll(legacyStates);
		itemRepository.saveAll(itemStates);
	}

	public MarketPriceEngine priceEngine() {
		return priceEngine;
	}

	public MarketCatalogService catalog() {
		return catalog;
	}

	public CommodityState commodityState(Commodity commodity) {
		return legacyStates.get(commodity);
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
		return legacyStates;
	}

	public boolean buy(ServerPlayer player, Commodity commodity, int quantity) {
		return commodity != null && buy(player, commodity.item(), quantity);
	}

	public boolean buy(ServerPlayer player, Item item, int quantity) {
		MarketItemEntry entry = catalog.resolve(item);
		if (entry == null || !entry.buyable() || quantity <= 0) {
			return false;
		}
		long unitPrice = priceEngine.getUnitPrice(entry.itemId());
		long total = unitPrice * quantity;
		long tax = taxService.calculateTradeTax(total);
		long cityTax = taxService.calculateCityTax(total);
		long grandTotal = total + tax + cityTax;

		if (!currencyService.withdraw(player.getUUID(), grandTotal, TransactionType.MARKET_BUY)) {
			return false;
		}

		int takenFromDepot = deliverFromDepot(player, item, quantity);
		int remaining = quantity - takenFromDepot;
		if (remaining > 0) {
			ItemStack stack = new ItemStack(item, remaining);
			if (!player.getInventory().add(stack)) {
				currencyService.deposit(player.getUUID(), grandTotal, TransactionType.MARKET_BUY);
				returnDepotStacks(player, item, takenFromDepot);
				return false;
			}
		}
		priceEngine.onBuy(entry.itemId(), quantity);
		syncLegacyCommodity(entry.itemId());
		taxService.collectTax(tax + cityTax);
		com.mceconomy.network.EconomyHudSync.syncPlayer(player);
		return true;
	}

	public boolean sell(ServerPlayer player, Commodity commodity, int quantity) {
		return commodity != null && sell(player, commodity.item(), quantity);
	}

	public SellFailure lastSellFailure() {
		return lastSellFailure;
	}

	public boolean sell(ServerPlayer player, Item item, int quantity) {
		lastSellFailure = SellFailure.NONE;
		MarketItemEntry entry = catalog.resolve(item);
		if (entry == null || !entry.sellable() || quantity <= 0) {
			lastSellFailure = SellFailure.NOT_SELLABLE;
			// #region agent log
			JsonObject pre = new JsonObject();
			pre.addProperty("item", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString());
			pre.addProperty("quantity", quantity);
			pre.addProperty("hasEntry", entry != null);
			pre.addProperty("sellable", entry != null && entry.sellable());
			DebugSessionLog.log("MarketService.sell", "precheck failed", "H3-H4", pre);
			// #endregion
			return false;
		}
		int sellableCount = countItems(player, item);
		if (sellableCount < quantity) {
			if (hasWantedItem(player, item)) {
				player.sendSystemMessage(Component.literal(
						"§c[Piyasa] §fKayip MB seri numarali zimmetli esya satilamaz."));
			}
			// #region agent log
			JsonObject inv = new JsonObject();
			inv.addProperty("item", entry.itemId());
			inv.addProperty("requested", quantity);
			inv.addProperty("sellableCount", sellableCount);
			inv.addProperty("hasWanted", hasWantedItem(player, item));
			DebugSessionLog.log("MarketService.sell", "insufficient sellable count", "H2", inv);
			// #endregion
			lastSellFailure = SellFailure.INSUFFICIENT_ITEMS;
			return false;
		}
		int removed = removeItems(player, item, quantity);
		if (removed < quantity) {
			// #region agent log
			JsonObject rem = new JsonObject();
			rem.addProperty("item", entry.itemId());
			rem.addProperty("requested", quantity);
			rem.addProperty("removed", removed);
			DebugSessionLog.log("MarketService.sell", "remove partial", "H2", rem);
			// #endregion
			lastSellFailure = SellFailure.REMOVE_FAILED;
			return false;
		}
		int effectiveQty = quantity;
		if (depotService != null) {
			ServerLevel level = (ServerLevel) player.level();
			int freeSlots = depotService.freeSlotCount(level, FacilityType.MARKET);
			int depotTotal = depotService.totalItemCount(level, FacilityType.MARKET);
			int stored = depotService.depositItem(level, FacilityType.MARKET, item, quantity);
			int overflow = quantity - stored;
			if (overflow > 0) {
				// #region agent log
				JsonObject depot = new JsonObject();
				depot.addProperty("item", entry.itemId());
				depot.addProperty("requested", quantity);
				depot.addProperty("stored", stored);
				depot.addProperty("overflow", overflow);
				depot.addProperty("depotFreeSlots", freeSlots);
				depot.addProperty("depotTotalItems", depotTotal);
				depot.addProperty("virtualSupply", true);
				DebugSessionLog.log("MarketService.sell", "depot overflow to virtual supply", "H1", depot);
				// #endregion
				if (stored == 0) {
					player.sendSystemMessage(Component.literal(
							"§e[Piyasa] §fMarket deposu dolu — satis sanal arza islendi."));
				} else {
					player.sendSystemMessage(Component.literal(
							"§e[Piyasa] §fDepoya " + stored + "/" + quantity + " sigdi; kalan sanal arza eklendi."));
				}
			}
		}

		long unitPrice = priceEngine.getUnitPrice(entry.itemId());
		Commodity commodity = Commodity.fromItem(item);
		if (jobManager != null && commodity != null) {
			unitPrice = jobManager.applySellBonus(player.getUUID(), commodity, unitPrice);
		}
		long total = unitPrice * effectiveQty;
		long tax = taxService.calculateTradeTax(total);
		long cityTax = taxService.calculateCityTax(total);
		long payout = total - tax - cityTax;
		long playerPayout = commodity != null
				? applyEmployedCompanyShare(player, commodity, payout)
				: payout;

		currencyService.deposit(player.getUUID(), playerPayout, TransactionType.MARKET_SELL);
		priceEngine.onSell(entry.itemId(), effectiveQty);
		syncLegacyCommodity(entry.itemId());
		taxService.collectTax(tax + cityTax);
		syncInventory(player);
		com.mceconomy.network.EconomyHudSync.syncPlayer(player);
		return true;
	}

	public boolean sellAll(ServerPlayer player, Item item) {
		int count = countItems(player, item);
		// #region agent log
		JsonObject all = new JsonObject();
		all.addProperty("item", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString());
		all.addProperty("sellableCount", count);
		DebugSessionLog.log("MarketService.sellAll", "sellAll attempt", "H2", all);
		// #endregion
		if (count <= 0) {
			return false;
		}
		return sell(player, item, count);
	}

	private void syncLegacyCommodity(String itemId) {
		MarketItemState itemState = itemStates.get(itemId);
		if (itemState == null) {
			return;
		}
		Item item = ItemPriceHeuristic.resolveItem(itemId);
		Commodity commodity = Commodity.fromItem(item);
		if (commodity != null) {
			legacyStates.put(commodity, new CommodityState(
					commodity, itemState.price(), itemState.basePrice(),
					itemState.supplyIndex(), itemState.demandIndex()));
		}
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
			} catch (SQLException e) {
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
		} catch (SQLException e) {
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

	private int countItems(ServerPlayer player, Item item) {
		int total = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item) && !JobItemTags.isJobLoan(stack)
					&& !FacilityItemTags.matchesWantedSerial(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static boolean hasWantedItem(ServerPlayer player, Item item) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item) && FacilityItemTags.matchesWantedSerial(stack)) {
				return true;
			}
		}
		return false;
	}

	private int removeItems(ServerPlayer player, Item item, int quantity) {
		int remaining = quantity;
		for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item) && !JobItemTags.isJobLoan(stack)
					&& !FacilityItemTags.matchesWantedSerial(stack)) {
				int take = Math.min(stack.getCount(), remaining);
				stack.shrink(take);
				remaining -= take;
			}
		}
		return quantity - remaining;
	}

	private static void syncInventory(ServerPlayer player) {
		player.getInventory().setChanged();
		if (player.containerMenu != null) {
			player.containerMenu.broadcastChanges();
		}
	}

	public void decayPrices() {
		priceEngine.decayAll();
	}

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
		syncLegacyCommodity(ItemPriceHeuristic.itemId(commodity.item()));
		taxService.collectTax(tax + cityTax);
		var mcServer = com.mceconomy.McEconomyMod.getEconomyManager().server();
		if (depotService != null && mcServer != null) {
			depotService.depositItem(mcServer.overworld(), FacilityType.MARKET, commodity.item(), quantity);
		}
		return payout;
	}

	private int deliverFromDepot(ServerPlayer player, Item item, int quantity) {
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

	private void returnDepotStacks(ServerPlayer player, Item item, int quantity) {
		if (depotService == null || quantity <= 0) {
			return;
		}
		depotService.depositItem((ServerLevel) player.level(), FacilityType.MARKET, item, quantity);
	}

	private static void giveItems(ServerPlayer player, Item item, int quantity) {
		int maxStack = new ItemStack(item).getMaxStackSize();
		int remaining = quantity;
		while (remaining > 0) {
			int chunk = Math.min(remaining, maxStack);
			ItemStack stack = new ItemStack(item, chunk);
			player.getInventory().add(stack);
			remaining -= chunk;
		}
	}
}
