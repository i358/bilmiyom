package com.mceconomy.economy;

import com.mceconomy.McEconomyMod;
import com.mceconomy.bank.BankService;
import com.mceconomy.bank.InterestEngine;
import com.mceconomy.bank.LoanManager;
import com.mceconomy.company.CompanyManager;
import com.mceconomy.company.CompanyProductPipeline;
import com.mceconomy.company.CompanyStashService;
import com.mceconomy.company.CompanyVaultService;
import com.mceconomy.company.NpcWorkforceService;
import com.mceconomy.company.PlayerEmploymentService;
import com.mceconomy.persistence.repo.CompanyStashRepository;
import com.mceconomy.persistence.repo.CompanyVaultRepository;
import com.mceconomy.persistence.repo.WorkforceRepository;
import com.mceconomy.persistence.repo.PlayerEmploymentRepository;
import com.mceconomy.persistence.repo.SalaryPaymentRepository;
import com.mceconomy.persistence.repo.TradeRepository;
import com.mceconomy.persistence.repo.GuildRepository;
import com.mceconomy.trade.PlayerTradeService;
import com.mceconomy.guild.GuildService;
import com.mceconomy.economy.NpcEconomyActivityService;
import com.mceconomy.regulation.TaxEvasionService;
import com.mceconomy.blackmarket.BlackMarketService;
import com.mceconomy.blackmarket.CustomBlackMarketRegistry;
import com.mceconomy.blackmarket.PlayerBlackMarketRegistry;
import com.mceconomy.reserve.GoldReserveService;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.event.EconomyEventManager;
import com.mceconomy.job.JobManager;
import com.mceconomy.job.QuestManager;
import com.mceconomy.market.MarketService;
import com.mceconomy.persistence.DatabaseManager;
import com.mceconomy.persistence.repo.BankRepository;
import com.mceconomy.persistence.repo.CompanyRepository;
import com.mceconomy.persistence.repo.LoanRepository;
import com.mceconomy.persistence.repo.MarketRepository;
import com.mceconomy.persistence.repo.MasakRepository;
import com.mceconomy.appeal.AppealService;
import com.mceconomy.exchange.ExchangeService;
import com.mceconomy.exchange.ForeignInvestorMarketService;
import com.mceconomy.exchange.LeverageService;
import com.mceconomy.persistence.repo.LeverageRepository;
import com.mceconomy.casino.CasinoService;
import com.mceconomy.privatebank.PrivateBankService;
import com.mceconomy.persistence.repo.AppealRepository;
import com.mceconomy.persistence.repo.ExchangeRepository;
import com.mceconomy.persistence.repo.PrivateBankRepository;
import com.mceconomy.persistence.repo.PlayerRepository;
import com.mceconomy.player.PlayerEconomyProfile;
import com.mceconomy.regulation.LaunderingService;
import com.mceconomy.regulation.MasakService;
import com.mceconomy.tax.CentralBank;
import com.mceconomy.tax.TaxService;
import com.mceconomy.vault.VaultService;
import com.mceconomy.persistence.repo.VaultRepository;
import com.mceconomy.bootstrap.EconomyBootstrap;
import com.mceconomy.news.EconomyBulletinService;
import com.mceconomy.persistence.repo.EconomyBulletinRepository;
import com.mceconomy.persistence.repo.NationalReserveRepository;
import com.mceconomy.reserve.DepotLedgerService;
import com.mceconomy.reserve.NationalReserveService;
import com.mceconomy.facility.FacilityDepotService;
import com.mceconomy.facility.FacilityType;
import com.mceconomy.heist.HeistService;
import com.mceconomy.security.BankSecurityCameraService;
import com.mceconomy.security.BankSecurityService;
import com.mceconomy.persistence.repo.SecurityCameraRepository;
import com.mceconomy.justice.BankRobberyJusticeService;
import com.mceconomy.justice.PrisonService;
import com.mceconomy.justice.ReportService;
import com.mceconomy.insurance.InsuranceService;
import com.mceconomy.municipal.MayorService;
import com.mceconomy.persistence.repo.InsuranceRepository;
import com.mceconomy.persistence.repo.MunicipalRepository;
import com.mceconomy.persistence.repo.PrisonRepository;
import com.mceconomy.persistence.repo.ReportRepository;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import com.mceconomy.persistence.repo.PriceHistoryRepository;
import com.mceconomy.web.EconomyWebServer;
import com.mceconomy.web.PriceHistoryService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EconomyManager {
	private final Map<UUID, PlayerEconomyProfile> profiles = new ConcurrentHashMap<>();

	private DatabaseManager database;
	private PlayerRepository playerRepository;
	private CurrencyService currencyService;
	private TransactionLedger ledger;
	private BankService bankService;
	private LoanManager loanManager;
	private MarketService marketService;
	private CentralBank centralBank;
	private TaxService taxService;
	private InflationSystem inflationSystem;
	private EconomyEventManager eventManager;
	private CompanyManager companyManager;
	private JobManager jobManager;
	private QuestManager questManager;
	private MasakService masakService;
	private BlackMarketService blackMarketService;
	private CustomBlackMarketRegistry customBlackMarket;
	private PlayerBlackMarketRegistry playerBlackMarket;
	private GoldReserveService goldReserveService;
	private LaunderingService launderingService;
	private ExchangeService exchangeService;
	private LeverageService leverageService;
	private PrivateBankService privateBankService;
	private AppealService appealService;
	private NpcWorkforceService workforceService;
	private CompanyStashService companyStashService;
	private CompanyVaultService companyVaultService;
	private CompanyProductPipeline companyProductPipeline;
	private PlayerEmploymentService playerEmploymentService;
	private PlayerTradeService playerTradeService;
	private GuildService guildService;
	private NpcEconomyActivityService npcEconomyActivityService;
	private TaxEvasionService taxEvasionService;
	private VaultService vaultService;
	private HeistService heistService;
	private CasinoService casinoService;
	private ReportService reportService;
	private PrisonService prisonService;
	private FacilityDepotService facilityDepotService;
	private NationalReserveService nationalReserveService;
	private DepotLedgerService depotLedgerService;
	private EconomyBulletinService bulletinService;
	private BankRobberyJusticeService bankRobberyJusticeService;
	private BankSecurityService bankSecurityService;
	private BankSecurityCameraService securityCameraService;
	private InsuranceService insuranceService;
	private MayorService mayorService;
	private ForeignInvestorMarketService foreignInvestorMarket;
	private PriceHistoryService priceHistoryService;
	private EconomyWebServer economyWebServer;

	private MinecraftServer server;
	private boolean loaded;

	public void initialize(MinecraftServer server) {
		this.server = server;
		try {
			Path dbDir = server.getWorldPath(LevelResource.ROOT).resolve("mceconomy");
			Files.createDirectories(dbDir);
			database = new DatabaseManager(dbDir.resolve("economy.db"));
			database.open();

			playerRepository = new PlayerRepository(database.connection());
			BankRepository bankRepository = new BankRepository(database.connection());
			MarketRepository marketRepository = new MarketRepository(database.connection());
			LoanRepository loanRepository = new LoanRepository(database.connection());
			CompanyRepository companyRepository = new CompanyRepository(database.connection());

			profiles.putAll(playerRepository.loadAll());
			ledger = new TransactionLedger(database);
			MasakRepository masakRepository = new MasakRepository(database.connection());
			masakService = new MasakService(profiles, ledger, masakRepository);
			currencyService = new CurrencyService(profiles, ledger);
			currencyService.bindMasak(masakService);
			taxService = new TaxService();
			bankService = new BankService(bankRepository, currencyService);
			bankService.load();

			InterestEngine interestEngine = new InterestEngine();
			loanManager = new LoanManager(loanRepository, currencyService, interestEngine);
			loanManager.load();

			marketService = new MarketService(marketRepository, currencyService, taxService);
			marketService.load();

			blackMarketService = new BlackMarketService(currencyService, marketService, masakService);
			customBlackMarket = new CustomBlackMarketRegistry(database);
			customBlackMarket.load();
			playerBlackMarket = new PlayerBlackMarketRegistry(database);
			playerBlackMarket.load();
			goldReserveService = new GoldReserveService();
			launderingService = new LaunderingService(currencyService, masakService);

			centralBank = new CentralBank(database);
			centralBank.load();
			GoldStandard.setGoldFactor(centralBank.getGoldFactor());
			taxService.bindCentralBank(centralBank);
			inflationSystem = new InflationSystem();
			eventManager = new EconomyEventManager();

			companyManager = new CompanyManager(companyRepository, currencyService);
			companyManager.load();

			CompanyStashRepository companyStashRepository = new CompanyStashRepository(database.connection());
			companyStashService = new CompanyStashService(companyStashRepository, companyManager);
			companyStashService.load();

			CompanyVaultRepository companyVaultRepository = new CompanyVaultRepository(database.connection());
			companyVaultService = new CompanyVaultService(companyVaultRepository, companyManager, server);
			companyVaultService.load();
			companyProductPipeline = new CompanyProductPipeline(marketService, companyVaultService, companyManager);

			WorkforceRepository workforceRepository = new WorkforceRepository(database.connection());
			workforceService = new NpcWorkforceService(workforceRepository, companyManager, currencyService);
			workforceService.bindProductPipeline(companyProductPipeline);
			workforceService.load();

			PlayerEmploymentRepository playerEmploymentRepository = new PlayerEmploymentRepository(database.connection());
			SalaryPaymentRepository salaryPaymentRepository = new SalaryPaymentRepository(database.connection());
			playerEmploymentService = new PlayerEmploymentService(
					playerEmploymentRepository, salaryPaymentRepository, profiles,
					companyManager, currencyService, workforceService);
			playerEmploymentService.load();
			workforceService.bindPlayerEmployment(playerEmploymentService);

			jobManager = new JobManager(profiles, currencyService, taxService);
			marketService.bindJobManager(jobManager);
			questManager = new QuestManager(jobManager);
			questManager.bindEmployment(playerEmploymentService);
			questManager.bindCompanyWork(companyProductPipeline, companyManager);
			marketService.bindEmployment(playerEmploymentService, companyManager);

			TradeRepository tradeRepository = new TradeRepository(database.connection());
			playerTradeService = new PlayerTradeService(tradeRepository, currencyService, centralBank);

			GuildRepository guildRepository = new GuildRepository(database.connection());
			guildService = new GuildService(guildRepository, currencyService);

			ExchangeRepository exchangeRepository = new ExchangeRepository(database.connection());
			exchangeService = new ExchangeService(exchangeRepository, currencyService, companyManager, masakService);
			exchangeService.load();

			LeverageRepository leverageRepository = new LeverageRepository(database.connection());
			leverageService = new LeverageService(leverageRepository, currencyService, exchangeService);
			leverageService.bindServer(server);
			leverageService.load();

			PrivateBankRepository privateBankRepository = new PrivateBankRepository(database.connection());
			privateBankService = new PrivateBankService(privateBankRepository, currencyService, profiles, masakService);
			privateBankService.load();

			AppealRepository appealRepository = new AppealRepository(database.connection());
			appealService = new AppealService(appealRepository, masakService, profiles);

			ReportRepository reportRepository = new ReportRepository(database.connection());
			reportService = new ReportService(reportRepository, profiles);
			reportService.bindEconomy(currencyService, centralBank);
			bankRobberyJusticeService = new BankRobberyJusticeService(reportRepository, profiles);
			PrisonRepository prisonRepository = new PrisonRepository(database.connection());
			prisonService = new PrisonService(prisonRepository, server);
			prisonService.load();

			VaultRepository vaultRepository = new VaultRepository(database.connection());
			vaultService = new VaultService(vaultRepository, server);
			vaultService.load();

			facilityDepotService = new FacilityDepotService();
			NationalReserveRepository nationalReserveRepository = new NationalReserveRepository(database.connection());
			nationalReserveService = new NationalReserveService(nationalReserveRepository);
			nationalReserveService.load();
			depotLedgerService = new DepotLedgerService(nationalReserveRepository);
			depotLedgerService.load();
			EconomyBulletinRepository bulletinRepository = new EconomyBulletinRepository(database.connection());
			bulletinService = new EconomyBulletinService(bulletinRepository);
			InsuranceRepository insuranceRepository = new InsuranceRepository(database.connection());
			insuranceService = new InsuranceService(insuranceRepository, currencyService, companyManager, centralBank);
			insuranceService.load();
			MunicipalRepository municipalRepository = new MunicipalRepository(database.connection());
			mayorService = new MayorService(municipalRepository, centralBank);
			mayorService.load();
			mayorService.ensureElectionScheduled();
			goldReserveService.bindDepotLedger(depotLedgerService);
			bankSecurityService = new BankSecurityService(server, facilityDepotService);
			SecurityCameraRepository cameraRepository = new SecurityCameraRepository(database.connection());
			securityCameraService = new BankSecurityCameraService(cameraRepository);
			foreignInvestorMarket = new ForeignInvestorMarketService();
			marketService.bindDepot(facilityDepotService);
			blackMarketService.bindDepot(facilityDepotService);
			bankService.bindDepot(facilityDepotService);
			bankService.bindDepotLedger(depotLedgerService);

			heistService = new HeistService(server, goldReserveService);
			casinoService = new CasinoService(currencyService);
			taxEvasionService = new TaxEvasionService(masakService, profiles);
			npcEconomyActivityService = new NpcEconomyActivityService();
			goldReserveService.refresh(server);
			try {
				depotLedgerService.syncGoldReserveBlocks(goldReserveService.cachedGoldBlocks());
				int physicalGold = facilityDepotService.countItem(server.overworld(), FacilityType.PHYSICAL_GOLD,
						net.minecraft.world.item.Items.GOLD_INGOT);
				if (depotLedgerService.expectedPhysicalGoldIngots() == 0 && physicalGold > 0) {
					depotLedgerService.onPhysicalGoldDeposited(physicalGold);
				}
			} catch (SQLException e) {
				McEconomyMod.LOGGER.error("Depo defteri baslatilamadi", e);
			}

			EconomyBootstrap.seed(this);

			PriceHistoryRepository priceHistoryRepository = new PriceHistoryRepository(database.connection());
			priceHistoryService = new PriceHistoryService(priceHistoryRepository);

			economyWebServer = new EconomyWebServer();
			economyWebServer.start();

			registerQuestEvents();
			registerSecurityEvents();
			loaded = true;
		} catch (Exception e) {
			McEconomyMod.LOGGER.error("Ekonomi sistemi başlatılamadı", e);
			throw new RuntimeException(e);
		}
	}

	private void registerQuestEvents() {
		ServerLivingEntityEvents.AFTER_DEATH.register((LivingEntity entity, DamageSource damageSource) -> {
			if (damageSource.getEntity() instanceof ServerPlayer player) {
				questManager.onMobKill(player.getUUID(), entity.getType());
			}
		});
	}

	private void registerSecurityEvents() {
		ServerLivingEntityEvents.AFTER_DEATH.register((LivingEntity entity, DamageSource damageSource) -> {
			if (bankSecurityService == null) {
				return;
			}
			if (com.mceconomy.security.BankSecurityService.isBankGuard(entity)) {
				bankSecurityService.onGuardDeath((net.minecraft.world.entity.npc.villager.Villager) entity);
			}
		});
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, target, hit) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer attacker)) {
				return net.minecraft.world.InteractionResult.PASS;
			}
			if (target instanceof net.minecraft.world.entity.npc.villager.Villager villager
					&& com.mceconomy.security.BankSecurityService.isBankGuard(villager)
					&& bankSecurityService != null) {
				bankSecurityService.onGuardHurt(villager, attacker);
			}
			return net.minecraft.world.InteractionResult.PASS;
		});
		com.mceconomy.security.SecurityWeaponCombat.register();
	}

	public void ensurePlayer(UUID uuid, String name) {
		try {
			PlayerEconomyProfile profile = playerRepository.createIfAbsent(uuid, name);
			profile.setName(name);
			profiles.put(uuid, profile);
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Oyuncu profili oluşturulamadı: {}", uuid, e);
		}
	}

	public void savePlayers() {
		for (PlayerEconomyProfile profile : profiles.values()) {
			try {
				playerRepository.save(profile);
			} catch (SQLException e) {
				McEconomyMod.LOGGER.error("Oyuncu kaydedilemedi: {}", profile.uuid(), e);
			}
		}
	}

	public void shutdown() {
		if (!loaded) {
			return;
		}
		try {
			savePlayers();
			bankService.saveAll();
			marketService.saveAll();
			loanManager.saveAll();
			companyManager.saveAll();
			if (workforceService != null) {
				workforceService.saveAll();
			}
			if (companyStashService != null) {
				companyStashService.saveAll();
			}
			exchangeService.saveAll();
			privateBankService.saveAll();
			centralBank.save();
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Ekonomi verisi kaydedilemedi", e);
		} finally {
			if (economyWebServer != null) {
				economyWebServer.stop();
			}
			database.close();
			loaded = false;
		}
	}

	public void onPriceHistoryTick() {
		if (priceHistoryService != null) {
			priceHistoryService.recordSnapshot();
		}
	}

	public void syncHudForOnlinePlayers() {
		if (server == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			com.mceconomy.network.EconomyHudSync.syncPlayer(player);
		}
	}

	public void onMarketTick() {
		marketService.decayPrices();
		onLeverageTick();
	}

	public void onInterestTick() {
		bankService.applyTermInterest(centralBank.getBaseRate());
	}

	public void onInflationTick() {
		if (goldReserveService != null && server != null) {
			goldReserveService.refresh(server);
		}
		inflationSystem.update(centralBank, bankService, profiles,
				marketService.economyIndex(), marketService.priceEngine(), goldReserveService);
		auditReserveIntegrity();
		if (bulletinService != null && goldReserveService != null && server != null) {
			long walletTotal = profiles.values().stream().mapToLong(p -> p.wallet().balance()).sum();
			long moneySupply = walletTotal + bankService.totalBankBalance();
			bulletinService.publishReserveReport(server, centralBank, goldReserveService, moneySupply);
		}
		try {
			centralBank.save();
			marketService.saveAll();
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Enflasyon tick kaydı başarısız", e);
		}
	}

	public void onStorageTick() {
		if (server == null) {
			return;
		}
		ServerLevel level = server.overworld();
		try {
			if (nationalReserveService != null && facilityDepotService != null) {
				int archived = 0;
				for (FacilityType type : FacilityType.values()) {
					archived += nationalReserveService.consolidateDepot(level, facilityDepotService, type);
				}
				if (archived >= EconomyConfig.depotArchiveBulletinMinItems() && bulletinService != null) {
					bulletinService.publishStorageNotice(server,
							"Merkez Bankasi depolari dolu — ulusal rezerve aktarildi",
							archived + " esya fiziksel sandiklardan ulusal rezerve kaydedildi ve depodan silindi.");
				}
			}
			if (companyProductPipeline != null) {
				companyProductPipeline.liquidateFullVaults(server);
			}
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Depo yonetim tick basarisiz", e);
		}
	}

	private void auditReserveIntegrity() {
		if (server == null || depotLedgerService == null || facilityDepotService == null
				|| goldReserveService == null || bulletinService == null) {
			return;
		}
		ServerLevel level = server.overworld();
		try {
			int actualBlocks = goldReserveService.cachedGoldBlocks();
			int blockDeficit = depotLedgerService.goldReserveDeficit(actualBlocks);
			if (blockDeficit > 0) {
				long value = GoldStandard.ingotsToMilligrams(
						blockDeficit * GoldReserveService.INGOTS_PER_GOLD_BLOCK);
				bulletinService.publishRobbery(server, centralBank, marketService.priceEngine(),
						"ALTIN REZERVINDE YETKISIZ EKSIKLIK TESPIT EDILDI!",
						blockDeficit + " altin blogu rezervden kayip — fiziksel envanter defterle uyusmuyor.",
						value);
				depotLedgerService.reconcileGoldReserve(actualBlocks);
			}

			int actualIngots = facilityDepotService.countItem(level, FacilityType.PHYSICAL_GOLD,
					net.minecraft.world.item.Items.GOLD_INGOT);
			int ingotDeficit = depotLedgerService.physicalGoldDeficit(actualIngots);
			if (ingotDeficit > 0) {
				long value = GoldStandard.ingotsToMilligrams(ingotDeficit);
				bulletinService.publishRobbery(server, centralBank, marketService.priceEngine(),
						"FIZIKSEL ALTIN KASASINDA SOYGUN TESPIT EDILDI!",
						ingotDeficit + " altin kulcesi kasada eksik — banka alis/satis kayitlariyla uyusmuyor.",
						value);
				depotLedgerService.reconcilePhysicalGold(actualIngots);
			}
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Rezerv denetimi basarisiz", e);
		}
	}

	public void onEventTick() {
		eventManager.tick(server, marketService.priceEngine(), centralBank);
	}

	public void onLoanTick() {
		if (server == null) {
			return;
		}
		ServerLevel level = server.overworld();
		try {
			loanManager.processOverdueLoans(profiles, level);
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Kredi gecikme işlemi başarısız", e);
		}
	}

	public void onWealthTaxTick() {
		if (!EconomyConfig.wealthTaxEnabled()) {
			return;
		}
		long now = System.currentTimeMillis();
		for (PlayerEconomyProfile profile : profiles.values()) {
			if (now - profile.lastTaxAt() < 24L * 60 * 60 * 1000) {
				continue;
			}
			long wealth = profile.wallet().balance();
			var checking = bankService.getChecking(profile.uuid());
			if (checking.isPresent()) {
				wealth += checking.get().balance();
			}
			long tax = taxService.calculateWealthTax(wealth);
			if (tax > 0) {
				currencyService.withdraw(profile.uuid(), tax, TransactionType.TAX);
				taxService.collectTax(tax);
			}
			profile.setLastTaxAt(now);
		}
		try {
			centralBank.save();
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Belediye butcesi kaydedilemedi", e);
		}
	}

	public boolean isLoaded() {
		return loaded;
	}

	public Map<UUID, PlayerEconomyProfile> profiles() {
		return profiles;
	}

	public CurrencyService currencyService() {
		return currencyService;
	}

	public BankService bankService() {
		return bankService;
	}

	public LoanManager loanManager() {
		return loanManager;
	}

	public MarketService marketService() {
		return marketService;
	}

	public CentralBank centralBank() {
		return centralBank;
	}

	public EconomyEventManager eventManager() {
		return eventManager;
	}

	public CompanyManager companyManager() {
		return companyManager;
	}

	public NpcWorkforceService workforceService() {
		return workforceService;
	}

	public CompanyStashService companyStashService() {
		return companyStashService;
	}

	public CompanyVaultService companyVaultService() {
		return companyVaultService;
	}

	public PlayerEmploymentService playerEmploymentService() {
		return playerEmploymentService;
	}

	public PlayerTradeService playerTradeService() {
		return playerTradeService;
	}

	public GuildService guildService() {
		return guildService;
	}

	public void onGuildTick() {
		if (guildService != null) {
			guildService.tickStrikes();
		}
	}

	public void onNpcEconomyTick() {
		if (npcEconomyActivityService != null && server != null) {
			npcEconomyActivityService.tick(server);
		}
	}

	public void onTaxEvasionTick() {
		if (taxEvasionService != null && server != null) {
			taxEvasionService.tick(server);
		}
	}

	public void onWorkforceApplicationTick() {
		if (workforceService != null && server != null) {
			workforceService.tickApplications(server);
		}
	}

	public void onWorkforcePayrollTick() {
		if (workforceService != null && server != null) {
			workforceService.processWorkAndPayroll(server);
			try {
				companyManager.saveAll();
				workforceService.saveAll();
			} catch (SQLException e) {
				McEconomyMod.LOGGER.error("Is gucu kaydi basarisiz", e);
			}
		}
		if (playerEmploymentService != null && server != null) {
			playerEmploymentService.processPayroll(server);
			try {
				companyManager.saveAll();
				playerEmploymentService.saveAll();
			} catch (SQLException e) {
				McEconomyMod.LOGGER.error("Oyuncu maas kaydi basarisiz", e);
			}
		}
	}

	public VaultService vaultService() {
		return vaultService;
	}

	public HeistService heistService() {
		return heistService;
	}

	public CasinoService casinoService() {
		return casinoService;
	}

	public void onHeistTick() {
		if (heistService != null) {
			heistService.tick();
		}
		if (bankSecurityService != null) {
			bankSecurityService.tick();
			if (securityCameraService != null && server != null) {
				securityCameraService.tick(server, bankSecurityService.guardsSleeping());
			}
		}
		if (bankRobberyJusticeService != null && server != null) {
			bankRobberyJusticeService.tick(server);
		}
		if (foreignInvestorMarket != null) {
			foreignInvestorMarket.tick(this);
		}
	}

	public void onInsuranceTick() {
		if (insuranceService != null) {
			insuranceService.tickPremiums();
		}
	}

	public void onMayorTick() {
		if (mayorService != null && server != null) {
			mayorService.tick(server);
		}
	}

	public long seizePlayerAssets(UUID targetUuid) throws SQLException {
		long total = 0;
		if (currencyService != null && centralBank != null) {
			long wallet = currencyService.getBalance(targetUuid);
			if (wallet > 0 && currencyService.withdraw(targetUuid, wallet, TransactionType.TAX)) {
				centralBank.addMunicipalBudget(wallet);
				total += wallet;
			}
			long dirty = currencyService.getDirtyBalance(targetUuid);
			if (dirty > 0 && currencyService.withdrawDirty(targetUuid, dirty, TransactionType.TAX)) {
				centralBank.addMunicipalBudget(dirty);
				total += dirty;
			}
		}
		if (bankService != null && centralBank != null) {
			total += bankService.seizeAllAccounts(targetUuid, centralBank);
		}
		if (exchangeService != null && centralBank != null) {
			total += exchangeService.seizeAllTokens(targetUuid, centralBank);
		}
		if (companyManager != null && centralBank != null) {
			double index = marketService.economyIndex().calculate();
			total += companyManager.seizeAllShares(targetUuid, index, centralBank);
		}
		return total;
	}

	public ForeignInvestorMarketService foreignInvestorMarket() {
		return foreignInvestorMarket;
	}

	public MinecraftServer server() {
		return server;
	}

	public DatabaseManager database() {
		return database;
	}

	/** Veritabani tam sifirlamadan sonra bellekteki ekonomi durumunu yeniden yukler. */
	public void reloadAfterDatabaseReset(MinecraftServer server) throws SQLException {
		this.server = server;
		profiles.clear();
		profiles.putAll(playerRepository.loadAll());
		bankService.load();
		loanManager.load();
		marketService.load();
		customBlackMarket.load();
		playerBlackMarket.load();
		centralBank.load();
		companyManager.load();
		companyStashService.load();
		companyVaultService.load();
		workforceService.load();
		playerEmploymentService.load();
		exchangeService.load();
		leverageService.load();
		privateBankService.load();
		prisonService.load();
		vaultService.load();
		nationalReserveService.load();
		depotLedgerService.load();
		insuranceService.load();
		mayorService.load();
		mayorService.ensureElectionScheduled();
		if (securityCameraService != null) {
			securityCameraService.onNightEnds();
		}
		goldReserveService.refresh(server);
		try {
			depotLedgerService.syncGoldReserveBlocks(goldReserveService.cachedGoldBlocks());
			int physicalGold = facilityDepotService.countItem(server.overworld(), FacilityType.PHYSICAL_GOLD,
					net.minecraft.world.item.Items.GOLD_INGOT);
			if (depotLedgerService.expectedPhysicalGoldIngots() == 0 && physicalGold > 0) {
				depotLedgerService.onPhysicalGoldDeposited(physicalGold);
			}
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Depo defteri yeniden senkron", e);
		}
		com.mceconomy.bootstrap.EconomyBootstrap.seed(this);
	}

	public FacilityDepotService facilityDepotService() {
		return facilityDepotService;
	}

	public NationalReserveService nationalReserveService() {
		return nationalReserveService;
	}

	public DepotLedgerService depotLedgerService() {
		return depotLedgerService;
	}

	public EconomyBulletinService bulletinService() {
		return bulletinService;
	}

	public BankRobberyJusticeService bankRobberyJusticeService() {
		return bankRobberyJusticeService;
	}

	public InsuranceService insuranceService() {
		return insuranceService;
	}

	public MayorService mayorService() {
		return mayorService;
	}

	public BankSecurityService bankSecurityService() {
		return bankSecurityService;
	}

	public BankSecurityCameraService securityCameraService() {
		return securityCameraService;
	}

	public void onPrisonTick() {
		if (prisonService != null) {
			prisonService.tick();
		}
	}

	public ReportService reportService() {
		return reportService;
	}

	public PrisonService prisonService() {
		return prisonService;
	}

	public JobManager jobManager() {
		return jobManager;
	}

	public QuestManager questManager() {
		return questManager;
	}

	public MasakService masakService() {
		return masakService;
	}

	public BlackMarketService blackMarketService() {
		return blackMarketService;
	}

	public CustomBlackMarketRegistry customBlackMarket() {
		return customBlackMarket;
	}

	public PlayerBlackMarketRegistry playerBlackMarket() {
		return playerBlackMarket;
	}

	public GoldReserveService goldReserveService() {
		return goldReserveService;
	}

	public LaunderingService launderingService() {
		return launderingService;
	}

	public ExchangeService exchangeService() {
		return exchangeService;
	}

	public LeverageService leverageService() {
		return leverageService;
	}

	public void onLeverageTick() {
		if (leverageService != null) {
			leverageService.liquidationTick();
		}
	}

	public PrivateBankService privateBankService() {
		return privateBankService;
	}

	public AppealService appealService() {
		return appealService;
	}

	public PlayerRepository playerRepository() {
		return playerRepository;
	}

	public PriceHistoryRepository priceHistoryRepository() {
		return priceHistoryService.repository();
	}

	public PriceHistoryService priceHistoryService() {
		return priceHistoryService;
	}
}
