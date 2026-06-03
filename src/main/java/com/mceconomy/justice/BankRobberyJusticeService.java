package com.mceconomy.justice;

import com.mceconomy.McEconomyMod;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.EconomyManager;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.facility.FacilityDepotService;
import com.mceconomy.facility.FacilityItemTags;
import com.mceconomy.facility.FacilityType;
import com.mceconomy.market.MarketPriceEngine;
import com.mceconomy.persistence.repo.ReportRepository;
import com.mceconomy.player.PlayerEconomyProfile;
import com.mceconomy.regulation.MasakService;
import com.mceconomy.reserve.DepotLedgerService;
import com.mceconomy.reserve.NationalReserveService;
import com.mceconomy.vault.VaultService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Otomatik tarama + ihbar: calinti esya ve kara para → borc, el koyma. */
public final class BankRobberyJusticeService {
	public record StolenScan(int itemCount, long estimatedValueMg) {
		public static StolenScan empty() {
			return new StolenScan(0, 0);
		}

		public boolean hasStolen() {
			return itemCount > 0;
		}

		public StolenScan merge(StolenScan other) {
			return new StolenScan(itemCount + other.itemCount(), estimatedValueMg + other.estimatedValueMg());
		}
	}

	private final ReportRepository reportRepository;
	private final Map<UUID, PlayerEconomyProfile> profiles;
	private final Map<UUID, Long> lastInvestigationMs = new HashMap<>();
	private final Map<Long, Integer> failedScanCounts = new HashMap<>();
	private final Map<UUID, Long> suspectUntilMs = new HashMap<>();

	public BankRobberyJusticeService(ReportRepository reportRepository, Map<UUID, PlayerEconomyProfile> profiles) {
		this.reportRepository = reportRepository;
		this.profiles = profiles;
	}

	public void clearInvestigationState() {
		lastInvestigationMs.clear();
		suspectUntilMs.clear();
		failedScanCounts.clear();
	}

	public void markSuspect(UUID targetUuid, long durationMs) {
		if (targetUuid == null) {
			return;
		}
		long until = System.currentTimeMillis() + durationMs;
		suspectUntilMs.merge(targetUuid, until, Math::max);
	}

	public void onBlackMarketFence(UUID seller, long proceedsMg, boolean stolenGoods) {
		if (seller == null) {
			return;
		}
		markSuspect(seller, EconomyConfig.bankRobberySuspectDurationMs());
		if (stolenGoods || proceedsMg >= EconomyConfig.bankRobberyDirtyMinimumMg()) {
			autoScanPlayer(seller, true);
		}
	}

	public void requestAutoScan(UUID targetUuid) {
		autoScanPlayer(targetUuid, true);
	}

	public void onReportAgainstTarget(UUID targetUuid) {
		if (targetUuid != null) {
			investigateTarget(targetUuid);
		}
	}

	private boolean isSuspect(UUID uuid) {
		Long until = suspectUntilMs.get(uuid);
		return until != null && System.currentTimeMillis() < until;
	}

	public void tick(MinecraftServer server) {
		if (server == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			autoScanPlayer(player.getUUID());
		}
		try {
			for (CitizenReport report : reportRepository.loadOpen()) {
				if (report.targetUuid() == null) {
					continue;
				}
				long last = lastInvestigationMs.getOrDefault(report.targetUuid(), 0L);
				if (System.currentTimeMillis() - last < EconomyConfig.bankRobberyInvestigationCooldownMs()) {
					continue;
				}
				investigateTarget(report.targetUuid());
			}
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Banka soygunu adalet taramasi basarisiz", e);
		}
	}

	/** Ihbar beklenmeden cevrimici oyuncu taramasi (rezerv soygunu / kara para). */
	private void autoScanPlayer(UUID targetUuid) {
		autoScanPlayer(targetUuid, false);
	}

	private void autoScanPlayer(UUID targetUuid, boolean forceCooldownBypass) {
		if (targetUuid == null || !EconomyConfig.bankRobberyJusticeEnabled()) {
			return;
		}
		long last = lastInvestigationMs.getOrDefault(targetUuid, 0L);
		if (!forceCooldownBypass
				&& System.currentTimeMillis() - last < EconomyConfig.bankRobberyInvestigationCooldownMs()) {
			return;
		}
		EconomyManager manager = McEconomyMod.getEconomyManager();
		if (manager == null || manager.vaultService() == null || manager.server() == null) {
			return;
		}
		if (manager.server().getPlayerList().getPlayer(targetUuid) == null) {
			return;
		}
		try {
			lastInvestigationMs.put(targetUuid, System.currentTimeMillis());
			StolenScan combined = scanTarget(targetUuid, manager);
			StolenScan dirtyScan = scanDirtyProceeds(targetUuid, manager);
			combined = combined.merge(dirtyScan);
			if (!combined.hasStolen()) {
				return;
			}
			punishHolder(targetUuid, combined, manager, false);
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Otomatik adalet taramasi basarisiz: {}", targetUuid, e);
		}
	}

	private StolenScan scanDirtyProceeds(UUID targetUuid, EconomyManager manager) {
		if (manager.currencyService() == null) {
			return StolenScan.empty();
		}
		long dirty = manager.currencyService().getDirtyBalance(targetUuid);
		long min = EconomyConfig.bankRobberyDirtyMinimumMg();
		if (dirty < min) {
			return StolenScan.empty();
		}
		if (!isSuspect(targetUuid)) {
			return StolenScan.empty();
		}
		return new StolenScan(1, dirty);
	}

	public void investigateTarget(UUID targetUuid) {
		if (targetUuid == null || !EconomyConfig.bankRobberyJusticeEnabled()) {
			return;
		}
		EconomyManager manager = McEconomyMod.getEconomyManager();
		if (manager == null || manager.vaultService() == null) {
			return;
		}
		try {
			List<CitizenReport> openReports = reportRepository.loadOpenForTarget(targetUuid);
			if (openReports.isEmpty()) {
				return;
			}
			lastInvestigationMs.put(targetUuid, System.currentTimeMillis());

			StolenScan combined = scanTarget(targetUuid, manager);
			if (!combined.hasStolen()) {
				handleFalseTipScans(openReports, manager);
				return;
			}
			punishHolder(targetUuid, combined, manager, true);
			resolveOpenReports(targetUuid, combined.estimatedValueMg(), manager);
			clearFailedScans(openReports);
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Banka soygunu adalet islemi basarisiz: {}", targetUuid, e);
		}
	}

	private StolenScan scanTarget(UUID targetUuid, EconomyManager manager) {
		StolenScan vaultScan = scanPersonalVault(manager.vaultService(), targetUuid, manager);
		StolenScan inventoryScan = scanPlayerInventory(manager.server(), targetUuid, manager);
		return vaultScan.merge(inventoryScan).merge(scanDirtyProceeds(targetUuid, manager));
	}

	private void punishHolder(UUID targetUuid, StolenScan scan, EconomyManager manager, boolean fromReport)
			throws SQLException {
		StolenScan vaultScan = scanPersonalVault(manager.vaultService(), targetUuid, manager);
		StolenScan inventoryScan = scanPlayerInventory(manager.server(), targetUuid, manager);
		StolenScan dirtyScan = scanDirtyProceeds(targetUuid, manager);

		long returned = returnStolenFromVault(manager, targetUuid);
		returned += returnStolenFromInventory(manager, targetUuid);
		long stolenValue = Math.max(scan.estimatedValueMg(), returned);
		if (dirtyScan.hasStolen()) {
			stolenValue = Math.max(stolenValue, dirtyScan.estimatedValueMg());
		}
		long confiscated = manager.seizePlayerAssets(targetUuid);
		imposeDebt(targetUuid, stolenValue, confiscated);
		PlayerEconomyProfile profile = profiles.get(targetUuid);
		if (profile != null) {
			profile.creditScore().adjust(-40);
		}
		String location = buildLocationLabel(vaultScan, inventoryScan, dirtyScan);
		notifyCaught(targetUuid, stolenValue, confiscated, location, fromReport, manager.server());
		McEconomyMod.LOGGER.warn("[Adalet] {} — {} banka calintisi yakalandi (ihbar={})",
				targetUuid, location, fromReport);
	}

	private void handleFalseTipScans(List<CitizenReport> openReports, EconomyManager manager) throws SQLException {
		int threshold = EconomyConfig.falseTipScanThreshold();
		long penalty = EconomyConfig.falseTipPenaltyMg();
		MasakService masak = manager.masakService();
		for (CitizenReport report : openReports) {
			int failures = failedScanCounts.merge(report.id(), 1, Integer::sum);
			if (failures < threshold) {
				continue;
			}
			if (report.reporterUuid() == null || penalty <= 0) {
				reportRepository.update(report.withStatus(ReportStatus.DISMISSED,
						"Asilsiz ihbar — calinti bulunamadi", null));
				failedScanCounts.remove(report.id());
				continue;
			}
			if (masak != null) {
				masak.applyFine(report.reporterUuid(), penalty);
			}
			reportRepository.update(report.withStatus(ReportStatus.DISMISSED,
					"Asilsiz ihbar cezasi: " + GoldStandard.formatMilligrams(penalty), null));
			failedScanCounts.remove(report.id());
			notifyFalseTip(report, penalty, manager.server());
		}
	}

	private void clearFailedScans(List<CitizenReport> reports) {
		for (CitizenReport report : reports) {
			failedScanCounts.remove(report.id());
		}
	}

	private void notifyFalseTip(CitizenReport report, long penalty, MinecraftServer server) {
		if (server == null || report.reporterUuid() == null) {
			return;
		}
		ServerPlayer reporter = server.getPlayerList().getPlayer(report.reporterUuid());
		if (reporter != null) {
			reporter.sendSystemMessage(Component.literal(
					"§c[Adalet] §fAsilsiz ihbar tespit edildi. Spam onleme cezasi: "
							+ GoldStandard.formatMilligrams(penalty)));
		}
	}

	public StolenScan scanPersonalVault(VaultService vaultService, UUID owner, EconomyManager manager) {
		if (manager.server() == null) {
			return StolenScan.empty();
		}
		Container chest = vaultService.openChest(owner, manager.server().overworld());
		if (chest == null) {
			return StolenScan.empty();
		}
		return scanContainer(chest, manager);
	}

	public StolenScan scanPlayerInventory(MinecraftServer server, UUID owner, EconomyManager manager) {
		if (server == null) {
			return StolenScan.empty();
		}
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		if (player == null) {
			return StolenScan.empty();
		}
		return scanInventory(player.getInventory(), manager);
	}

	private StolenScan scanContainer(Container container, EconomyManager manager) {
		MarketPriceEngine priceEngine = manager.marketService().priceEngine();
		NationalReserveService reserve = manager.nationalReserveService();
		int count = 0;
		long value = 0;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.isEmpty() || !FacilityItemTags.isStolen(stack)) {
				continue;
			}
			count += stack.getCount();
			if (reserve != null) {
				value += reserve.estimateItemValueMg(stack.getItem(), stack.getCount(), priceEngine);
			}
		}
		return new StolenScan(count, value);
	}

	private StolenScan scanInventory(Inventory inventory, EconomyManager manager) {
		MarketPriceEngine priceEngine = manager.marketService().priceEngine();
		NationalReserveService reserve = manager.nationalReserveService();
		int count = 0;
		long value = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || !FacilityItemTags.isStolen(stack)) {
				continue;
			}
			count += stack.getCount();
			if (reserve != null) {
				value += reserve.estimateItemValueMg(stack.getItem(), stack.getCount(), priceEngine);
			}
		}
		return new StolenScan(count, value);
	}

	private long returnStolenFromVault(EconomyManager manager, UUID owner) throws SQLException {
		Container chest = manager.vaultService().openChest(owner, manager.server().overworld());
		if (chest == null) {
			return 0;
		}
		return returnStolenFromContainer(manager, chest);
	}

	private long returnStolenFromInventory(EconomyManager manager, UUID owner) throws SQLException {
		ServerPlayer player = manager.server().getPlayerList().getPlayer(owner);
		if (player == null) {
			return 0;
		}
		return returnStolenFromContainer(manager, player.getInventory());
	}

	private long returnStolenFromContainer(EconomyManager manager, Container container) throws SQLException {
		ServerLevel level = manager.server().overworld();
		FacilityDepotService depot = manager.facilityDepotService();
		DepotLedgerService ledger = manager.depotLedgerService();
		MarketPriceEngine priceEngine = manager.marketService().priceEngine();
		NationalReserveService reserve = manager.nationalReserveService();
		long value = 0;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.isEmpty() || !FacilityItemTags.isStolen(stack)) {
				continue;
			}
			Item item = stack.getItem();
			int amount = stack.getCount();
			if (reserve != null) {
				value += reserve.estimateItemValueMg(item, amount, priceEngine);
			}
			if (depot != null) {
				FacilityType type = item == Items.GOLD_INGOT ? FacilityType.PHYSICAL_GOLD : FacilityType.MARKET;
				depot.depositItem(level, type, item, amount);
				if (ledger != null && item == Items.GOLD_INGOT) {
					ledger.onPhysicalGoldDeposited(amount);
				}
			}
			container.setItem(slot, ItemStack.EMPTY);
		}
		if (container instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chestEntity) {
			chestEntity.setChanged();
		}
		return value;
	}

	private void imposeDebt(UUID targetUuid, long stolenValueMg, long confiscatedMg) {
		PlayerEconomyProfile profile = profiles.get(targetUuid);
		if (profile == null) {
			return;
		}
		long debt = Math.max(0, stolenValueMg - confiscatedMg);
		if (debt <= 0 && stolenValueMg > 0) {
			debt = EconomyConfig.bankRobberyMinimumDebtMg();
		}
		profile.wallet().setBalance(-debt);
	}

	private void resolveOpenReports(UUID targetUuid, long stolenValueMg, EconomyManager manager) throws SQLException {
		for (CitizenReport report : reportRepository.loadOpenForTarget(targetUuid)) {
			reportRepository.update(report.withStatus(ReportStatus.GUILTY,
					"Banka calintisi (" + GoldStandard.formatMilligrams(stolenValueMg) + ")",
					null));
			manager.reportService().payTipRewardForReport(report);
		}
	}

	private void notifyCaught(UUID targetUuid, long stolenValue, long confiscated, String location,
			boolean fromReport, MinecraftServer server) {
		if (server == null) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(targetUuid);
		PlayerEconomyProfile profile = profiles.get(targetUuid);
		long debt = profile != null ? Math.max(0, -profile.wallet().balance())
				: Math.max(0, stolenValue - confiscated);
		if (player != null) {
			player.sendSystemMessage(Component.literal(
					"§4§l[MASAK] §cOtomatik denetim: " + location + " banka calintisi bulundu!"));
			player.sendSystemMessage(Component.literal(
					"§cCalinti iade edildi. Temiz + kara para ve varliklariniza el konuldu. §4Borc: "
							+ GoldStandard.formatMilligrams(debt)));
		}
		String name = profile != null ? profile.name() : targetUuid.toString();
		if (managerBulletin(server)) {
			String headline = fromReport
					? "IHBAR: BANKA SOYGUNU SUPHELISI " + location.toUpperCase() + " YAKALANDI"
					: "OTOMATIK TARAMA: BANKA CALINTISI " + location.toUpperCase() + " YAKALANDI";
			McEconomyMod.getEconomyManager().bulletinService().publishStorageNotice(server, headline,
					name + " uzerindeki calinti iade edildi; mal varligina el konuldu, borc yazildi.");
		}
		for (ServerPlayer staff : server.getPlayerList().getPlayers()) {
			if (server.getPlayerList().isOp(staff.nameAndId())) {
				staff.sendSystemMessage(Component.literal(
						"§c[Adalet] §f" + name + " — " + location
								+ (fromReport ? " (ihbar)" : " (otomatik)") + ". Borc: "
								+ GoldStandard.formatMilligrams(debt)));
			}
		}
	}

	private static String buildLocationLabel(StolenScan vault, StolenScan inv, StolenScan dirty) {
		boolean v = vault.hasStolen();
		boolean i = inv.hasStolen();
		boolean d = dirty.hasStolen();
		if (d && (v || i)) {
			return "kara para + fiziksel calinti";
		}
		if (d) {
			return "kara para (karaborsa)";
		}
		if (v && i) {
			return "kasada ve envanterde";
		}
		if (i) {
			return "envanterde";
		}
		return "kisisel kasada";
	}

	private boolean managerBulletin(MinecraftServer server) {
		EconomyManager manager = McEconomyMod.getEconomyManager();
		return manager != null && manager.bulletinService() != null && server != null;
	}
}
