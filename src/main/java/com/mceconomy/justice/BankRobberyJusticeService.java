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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Sabah taramasi + seri no eslesmesi; gece aninda ceza yok. */
public final class BankRobberyJusticeService {
	public record StolenScan(int itemCount, long estimatedValueMg, int serialMatches) {
		public static StolenScan empty() {
			return new StolenScan(0, 0, 0);
		}

		public boolean hasStolen() {
			return serialMatches > 0;
		}

		public StolenScan merge(StolenScan other) {
			return new StolenScan(
					itemCount + other.itemCount(),
					estimatedValueMg + other.estimatedValueMg(),
					serialMatches + other.serialMatches());
		}
	}

	private final ReportRepository reportRepository;
	private final Map<UUID, PlayerEconomyProfile> profiles;
	private final BankAssetSerialRegistry serialRegistry;
	private final Map<UUID, Long> lastInvestigationMs = new HashMap<>();
	private final Map<Long, Integer> failedScanCounts = new HashMap<>();
	private final Set<UUID> pendingMorningInvestigation = new HashSet<>();

	public BankRobberyJusticeService(ReportRepository reportRepository,
			Map<UUID, PlayerEconomyProfile> profiles,
			BankAssetSerialRegistry serialRegistry) {
		this.reportRepository = reportRepository;
		this.profiles = profiles;
		this.serialRegistry = serialRegistry;
	}

	public BankAssetSerialRegistry serialRegistry() {
		return serialRegistry;
	}

	public void clearInvestigationState() {
		lastInvestigationMs.clear();
		failedScanCounts.clear();
		pendingMorningInvestigation.clear();
		serialRegistry.clearAll();
	}

	public void markSuspect(UUID targetUuid, long durationMs) {
		// Artik tum oyunculari supheli yapmiyoruz — sadece sabah seri no taramasi.
	}

	public void onBlackMarketFence(UUID seller, long proceedsMg, boolean stolenGoods) {
		if (seller == null || !stolenGoods) {
			return;
		}
		scheduleMorningInvestigation(seller);
	}

	public void requestAutoScan(UUID targetUuid) {
		scheduleMorningInvestigation(targetUuid);
	}

	public void scheduleMorningInvestigation(UUID targetUuid) {
		if (targetUuid != null) {
			pendingMorningInvestigation.add(targetUuid);
		}
	}

	public void onReportAgainstTarget(UUID targetUuid) {
		if (targetUuid != null) {
			scheduleMorningInvestigation(targetUuid);
		}
	}

	public void tick(MinecraftServer server) {
		if (server == null || !EconomyConfig.bankRobberyJusticeEnabled()) {
			return;
		}
		if (!isDaytime(server)) {
			return;
		}
		if (!serialRegistry.hasActiveInvestigation() && pendingMorningInvestigation.isEmpty()) {
			try {
				for (CitizenReport report : reportRepository.loadOpen()) {
					if (report.targetUuid() != null) {
						pendingMorningInvestigation.add(report.targetUuid());
					}
				}
			} catch (SQLException e) {
				McEconomyMod.LOGGER.error("Acik ihbar yuklenemedi", e);
			}
			if (pendingMorningInvestigation.isEmpty()) {
				return;
			}
		}
		Set<UUID> toScan = new HashSet<>(pendingMorningInvestigation);
		pendingMorningInvestigation.clear();
		for (UUID targetUuid : toScan) {
			runInvestigation(targetUuid, false);
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

	private static boolean isDaytime(MinecraftServer server) {
		return server.overworld().getSkyDarken() < 4;
	}

	private void runInvestigation(UUID targetUuid, boolean fromReport) {
		if (targetUuid == null) {
			return;
		}
		EconomyManager manager = McEconomyMod.getEconomyManager();
		if (manager == null || manager.server() == null) {
			return;
		}
		if (manager.server().getPlayerList().getPlayer(targetUuid) == null) {
			pendingMorningInvestigation.add(targetUuid);
			return;
		}
		try {
			lastInvestigationMs.put(targetUuid, System.currentTimeMillis());
			StolenScan scan = scanTarget(targetUuid, manager);
			if (!scan.hasStolen()) {
				return;
			}
			punishHolder(targetUuid, scan, manager, fromReport);
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Sabah adalet taramasi basarisiz: {}", targetUuid, e);
		}
	}

	public void investigateTarget(UUID targetUuid) {
		if (targetUuid == null || !EconomyConfig.bankRobberyJusticeEnabled()) {
			return;
		}
		if (!isDaytime(McEconomyMod.getEconomyManager().server())) {
			scheduleMorningInvestigation(targetUuid);
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
		if (!serialRegistry.hasActiveInvestigation()) {
			return StolenScan.empty();
		}
		StolenScan vaultScan = scanPersonalVault(manager.vaultService(), targetUuid, manager);
		StolenScan inventoryScan = scanPlayerInventory(manager.server(), targetUuid, manager);
		return vaultScan.merge(inventoryScan);
	}

	private void punishHolder(UUID targetUuid, StolenScan scan, EconomyManager manager, boolean fromReport)
			throws SQLException {
		long returned = returnWantedFromVault(manager, targetUuid);
		returned += returnWantedFromInventory(manager, targetUuid);
		long stolenValue = Math.max(scan.estimatedValueMg(), returned);
		long confiscated = manager.seizePlayerAssets(targetUuid);
		imposeDebt(targetUuid, stolenValue, confiscated);
		PlayerEconomyProfile profile = profiles.get(targetUuid);
		if (profile != null) {
			profile.creditScore().adjust(-40);
		}
		notifyCaught(targetUuid, stolenValue, confiscated, scan.serialMatches(), fromReport, manager.server());
		McEconomyMod.LOGGER.warn("[Adalet] {} — {} eslesen seri no (ihbar={})",
				targetUuid, scan.serialMatches(), fromReport);
	}

	/** OP: yanlis ceza, borc sifirla, seri no ve suphe temizligi. */
	public boolean reevaluatePlayer(UUID targetUuid, boolean clearDebt) throws SQLException {
		EconomyManager manager = McEconomyMod.getEconomyManager();
		if (manager == null || targetUuid == null) {
			return false;
		}
		pendingMorningInvestigation.remove(targetUuid);
		lastInvestigationMs.remove(targetUuid);
		ServerPlayer player = manager.server() != null
				? manager.server().getPlayerList().getPlayer(targetUuid) : null;
		if (player != null) {
			serialRegistry.clearWantedFromContainer(player.getInventory());
		}
		Container vault = manager.vaultService() != null && manager.server() != null
				? manager.vaultService().openChest(targetUuid, manager.server().overworld()) : null;
		if (vault != null) {
			serialRegistry.clearWantedFromContainer(vault);
		}
		PlayerEconomyProfile profile = profiles.get(targetUuid);
		if (profile != null && clearDebt && profile.wallet().balance() < 0) {
			profile.wallet().setBalance(0);
			manager.playerRepository().save(profile);
		}
		return true;
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
						"Asilsiz ihbar — kayip seri no eslesmedi", null));
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
					"§c[Adalet] §fAsilsiz ihbar. Ceza: " + GoldStandard.formatMilligrams(penalty)));
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
		int serialMatches = 0;
		long value = 0;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.isEmpty() || !serialRegistry.isWanted(stack)) {
				continue;
			}
			count += stack.getCount();
			serialMatches++;
			if (reserve != null) {
				value += reserve.estimateItemValueMg(stack.getItem(), stack.getCount(), priceEngine);
			}
		}
		return new StolenScan(count, value, serialMatches);
	}

	private StolenScan scanInventory(Inventory inventory, EconomyManager manager) {
		MarketPriceEngine priceEngine = manager.marketService().priceEngine();
		NationalReserveService reserve = manager.nationalReserveService();
		int count = 0;
		int serialMatches = 0;
		long value = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || !serialRegistry.isWanted(stack)) {
				continue;
			}
			count += stack.getCount();
			serialMatches++;
			if (reserve != null) {
				value += reserve.estimateItemValueMg(stack.getItem(), stack.getCount(), priceEngine);
			}
		}
		return new StolenScan(count, value, serialMatches);
	}

	private long returnWantedFromVault(EconomyManager manager, UUID owner) throws SQLException {
		Container chest = manager.vaultService().openChest(owner, manager.server().overworld());
		if (chest == null) {
			return 0;
		}
		return returnWantedFromContainer(manager, chest);
	}

	private long returnWantedFromInventory(EconomyManager manager, UUID owner) throws SQLException {
		ServerPlayer player = manager.server().getPlayerList().getPlayer(owner);
		if (player == null) {
			return 0;
		}
		return returnWantedFromContainer(manager, player.getInventory());
	}

	private long returnWantedFromContainer(EconomyManager manager, Container container) throws SQLException {
		ServerLevel level = manager.server().overworld();
		FacilityDepotService depot = manager.facilityDepotService();
		DepotLedgerService ledger = manager.depotLedgerService();
		MarketPriceEngine priceEngine = manager.marketService().priceEngine();
		NationalReserveService reserve = manager.nationalReserveService();
		long value = 0;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.isEmpty() || !serialRegistry.isWanted(stack)) {
				continue;
			}
			Item item = stack.getItem();
			int amount = stack.getCount();
			if (reserve != null) {
				value += reserve.estimateItemValueMg(item, amount, priceEngine);
			}
			if (depot != null) {
				FacilityType type = FacilityItemTags.resolveRecoveryDepot(stack);
				ItemStack copy = stack.copy();
				if (depot.deposit(level, type, copy)) {
					serialRegistry.recoverSerial(FacilityItemTags.getSerial(copy));
				}
				if (ledger != null && item == Items.GOLD_INGOT) {
					ledger.onPhysicalGoldDeposited(amount);
				}
			}
			FacilityItemTags.clearTheftMarks(stack);
			container.setItem(slot, ItemStack.EMPTY);
		}
		if (container instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chestEntity) {
			chestEntity.setChanged();
		}
		return value;
	}

	private void imposeDebt(UUID targetUuid, long stolenValueMg, long confiscatedMg) {
		PlayerEconomyProfile profile = profiles.get(targetUuid);
		if (profile == null || stolenValueMg <= 0) {
			return;
		}
		long debt = Math.max(0, stolenValueMg - confiscatedMg);
		if (debt <= 0) {
			debt = EconomyConfig.bankRobberyMinimumDebtMg();
		}
		profile.wallet().setBalance(-debt);
	}

	private void resolveOpenReports(UUID targetUuid, long stolenValueMg, EconomyManager manager) throws SQLException {
		for (CitizenReport report : reportRepository.loadOpenForTarget(targetUuid)) {
			reportRepository.update(report.withStatus(ReportStatus.GUILTY,
					"Seri no eslesmesi (" + GoldStandard.formatMilligrams(stolenValueMg) + ")",
					null));
			manager.reportService().payTipRewardForReport(report);
		}
	}

	private void notifyCaught(UUID targetUuid, long stolenValue, long confiscated, int serialMatches,
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
					"§4§l[MASAK] §cSabah denetimi: §f" + serialMatches + " kayip seri no eslesti."));
			player.sendSystemMessage(Component.literal(
					"§cCalinti iade edildi. Varliklara el konuldu. §4Borc: "
							+ GoldStandard.formatMilligrams(debt)));
		}
		String name = profile != null ? profile.name() : targetUuid.toString();
		if (managerBulletin(server)) {
			String headline = fromReport
					? "IHBAR: BANKA SOYGUNU SERI NO ILE DOGRULANDI"
					: "SABAH TARAMASI: KAYIP SERI NO YAKALANDI";
			McEconomyMod.getEconomyManager().bulletinService().publishStorageNotice(server, headline,
					name + " uzerinde " + serialMatches + " kayip MB seri numarasi bulundu.");
		}
		for (ServerPlayer staff : server.getPlayerList().getPlayers()) {
			if (server.getPlayerList().isOp(staff.nameAndId())) {
				staff.sendSystemMessage(Component.literal(
						"§c[Adalet] §f" + name + " — " + serialMatches + " seri no"
								+ (fromReport ? " (ihbar)" : " (sabah)") + ". Borc: "
								+ GoldStandard.formatMilligrams(debt)));
			}
		}
	}

	private boolean managerBulletin(MinecraftServer server) {
		EconomyManager manager = McEconomyMod.getEconomyManager();
		return manager != null && manager.bulletinService() != null && server != null;
	}
}
