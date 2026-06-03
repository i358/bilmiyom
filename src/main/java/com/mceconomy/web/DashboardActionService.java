package com.mceconomy.web;

import com.google.gson.JsonObject;
import com.mceconomy.McEconomyMod;
import com.mceconomy.blackmarket.IllegalGood;
import com.mceconomy.command.BalanceCommand;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.event.EconomyEventType;
import com.mceconomy.job.JobKitService;
import com.mceconomy.job.JobType;
import com.mceconomy.job.QuestManager;
import com.mceconomy.market.Commodity;
import com.mceconomy.player.PlayerEconomyProfile;
import com.mceconomy.regulation.LaunderingService;
import com.mceconomy.world.CentralBankPlacer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class DashboardActionService {
	private DashboardActionService() {
	}

	/** Web paneldeki "Tutar (MC)" alanlarini dahili mg birimine cevirir. */
	private static long mgForDisplayMc(long displayMc) {
		if (displayMc <= 0) {
			return 0;
		}
		return GoldStandard.milligramsForDisplayMc(displayMc);
	}

	public record ActionResult(boolean success, String message, JsonObject data) {
		public static ActionResult ok(String message) {
			return new ActionResult(true, message, null);
		}

		public static ActionResult ok(String message, JsonObject data) {
			return new ActionResult(true, message, data);
		}

		public static ActionResult fail(String message) {
			return new ActionResult(false, message, null);
		}

		public JsonObject toJson() {
			JsonObject obj = new JsonObject();
			obj.addProperty("success", success);
			obj.addProperty("message", message);
			if (data != null) {
				obj.add("data", data);
			}
			return obj;
		}
	}

	public static ActionResult pay(UUID from, String targetName, long displayMc) {
		var manager = McEconomyMod.getEconomyManager();
		PlayerEconomyProfile fromProfile = manager.profiles().get(from);
		if (fromProfile != null && !fromProfile.canUseLegalEconomy()) {
			return ActionResult.fail("MASAK kısıtlaması nedeniyle transfer yapılamaz.");
		}
		UUID target = BalanceCommand.findPlayerUuid(targetName);
		if (target == null) {
			return ActionResult.fail("Oyuncu bulunamadı.");
		}
		long mg = mgForDisplayMc(displayMc);
		if (mg <= 0) {
			return ActionResult.fail("Geçersiz tutar.");
		}
		if (!manager.currencyService().transfer(from, target, mg)) {
			return ActionResult.fail("Yetersiz bakiye.");
		}
		notifyPlayer(target, fromProfile.name() + " size " + GoldStandard.formatMilligrams(mg) + " gönderdi.");
		return ActionResult.ok("Ödeme başarılı: " + targetName);
	}

	public static ActionResult bankOpenChecking(UUID uuid) {
		try {
			if (McEconomyMod.getEconomyManager().bankService().createCheckingAccount(uuid)) {
				return ActionResult.ok("Vadesiz hesap açıldı.");
			}
			return ActionResult.fail("Hesap zaten mevcut.");
		} catch (Exception e) {
			return ActionResult.fail("İşlem başarısız.");
		}
	}

	public static ActionResult bankOpenTerm(UUID uuid) {
		try {
			double rate = McEconomyMod.getEconomyManager().centralBank().getBaseRate();
			if (McEconomyMod.getEconomyManager().bankService().createTermAccount(uuid, rate)) {
				return ActionResult.ok("Vadeli hesap açıldı. Faiz: %" + (int) (rate * 100));
			}
			return ActionResult.fail("Vadeli hesap zaten mevcut veya açılamadı.");
		} catch (Exception e) {
			return ActionResult.fail("İşlem başarısız.");
		}
	}

	public static ActionResult bankTransfer(UUID from, String targetName, long displayMc) {
		UUID target = BalanceCommand.findPlayerUuid(targetName);
		if (target == null) {
			return ActionResult.fail("Oyuncu bulunamadı.");
		}
		if (McEconomyMod.getEconomyManager().bankService().getChecking(from).isEmpty()) {
			return ActionResult.fail("Banka hesabınız yok.");
		}
		long mg = mgForDisplayMc(displayMc);
		if (mg <= 0) {
			return ActionResult.fail("Geçersiz tutar.");
		}
		if (McEconomyMod.getEconomyManager().bankService().transferFromBank(from, target, mg)) {
			return ActionResult.ok("Banka transferi başarılı.");
		}
		return ActionResult.fail("Yetersiz banka bakiyesi.");
	}

	public static ActionResult bankWalletDeposit(UUID uuid, long displayMc) {
		if (McEconomyMod.getEconomyManager().bankService().getChecking(uuid).isEmpty()) {
			return ActionResult.fail("Banka hesabınız yok.");
		}
		long mg = mgForDisplayMc(displayMc);
		if (mg <= 0) {
			return ActionResult.fail("Geçersiz tutar.");
		}
		if (McEconomyMod.getEconomyManager().bankService().depositToBank(uuid, mg)) {
			return ActionResult.ok(GoldStandard.formatMilligrams(mg) + " bankaya yatırıldı.");
		}
		return ActionResult.fail("Yetersiz cüzdan bakiyesi.");
	}

	public static ActionResult bankWalletWithdraw(UUID uuid, long displayMc) {
		if (McEconomyMod.getEconomyManager().bankService().getChecking(uuid).isEmpty()) {
			return ActionResult.fail("Banka hesabınız yok.");
		}
		long mg = mgForDisplayMc(displayMc);
		if (mg <= 0) {
			return ActionResult.fail("Geçersiz tutar.");
		}
		if (McEconomyMod.getEconomyManager().bankService().withdrawFromBank(uuid, mg)) {
			return ActionResult.ok(GoldStandard.formatMilligrams(mg) + " cüzdana çekildi.");
		}
		return ActionResult.fail("Yetersiz banka bakiyesi.");
	}

	public static ActionResult bankDepositIngots(ServerPlayer player, int ingots) {
		UUID uuid = player.getUUID();
		if (McEconomyMod.getEconomyManager().bankService().getChecking(uuid).isEmpty()) {
			return ActionResult.fail("Banka hesabınız yok.");
		}
		if (com.mceconomy.economy.PhysicalGoldService.hasWantedGoldIngots(player)
				&& com.mceconomy.economy.PhysicalGoldService.countDepositEligibleGoldIngots(player) < ingots) {
			return ActionResult.fail(
					"Kayıp MB seri numaralı altın bankaya yatırılamaz. Karaborsada eriterek aklayın.");
		}
		if (McEconomyMod.getEconomyManager().bankService().depositPhysicalGold(uuid, player, ingots)) {
			return ActionResult.ok(ingots + " altın külçe yatırıldı.");
		}
		return ActionResult.fail("Envanterde yeterli altın külçe yok.");
	}

	public static ActionResult bankWithdrawIngots(ServerPlayer player, int ingots) {
		UUID uuid = player.getUUID();
		if (McEconomyMod.getEconomyManager().bankService().getChecking(uuid).isEmpty()) {
			return ActionResult.fail("Banka hesabınız yok.");
		}
		long mg = GoldStandard.ingotsToMilligrams(ingots);
		if (McEconomyMod.getEconomyManager().bankService().withdrawPhysicalGold(uuid, player, ingots)) {
			return ActionResult.ok(ingots + " altın külçe çekildi (" + GoldStandard.formatMilligrams(mg) + ").");
		}
		if (McEconomyMod.getEconomyManager().bankService().getBankBalanceMg(uuid) < mg) {
			return ActionResult.fail("Yetersiz banka bakiyesi.");
		}
		return ActionResult.fail("Envanter dolu — oyunda yer açın.");
	}

	public static ActionResult marketBuy(ServerPlayer player, String commodityId, int quantity) {
		Commodity commodity = Commodity.fromId(commodityId);
		if (commodity == null) {
			return ActionResult.fail("Geçersiz emtia.");
		}
		if (!commodity.buyable()) {
			return ActionResult.fail(commodity.displayName() + " satın alınamaz.");
		}
		if (McEconomyMod.getEconomyManager().marketService().buy(player, commodity, quantity)) {
			return ActionResult.ok(quantity + "x " + commodity.displayName() + " satın alındı.");
		}
		return ActionResult.fail("Yetersiz bakiye veya envanter dolu.");
	}

	public static ActionResult marketSell(ServerPlayer player, String commodityId, int quantity) {
		Commodity commodity = Commodity.fromId(commodityId);
		if (commodity == null) {
			return ActionResult.fail("Geçersiz emtia.");
		}
		if (!commodity.sellable()) {
			return ActionResult.fail(commodity.displayName() + " satılamaz.");
		}
		if (McEconomyMod.getEconomyManager().marketService().sell(player, commodity, quantity)) {
			return ActionResult.ok(quantity + "x " + commodity.displayName() + " satıldı.");
		}
		return ActionResult.fail("Envanterde yeterli eşya yok.");
	}

	public static ActionResult loanTake(UUID uuid, long displayMc) {
		PlayerEconomyProfile profile = McEconomyMod.getEconomyManager().profiles().get(uuid);
		if (profile == null) {
			return ActionResult.fail("Profil bulunamadı.");
		}
		try {
			long mg = mgForDisplayMc(displayMc);
			if (mg <= 0) {
				return ActionResult.fail("Geçersiz tutar.");
			}
			if (McEconomyMod.getEconomyManager().loanManager().takeLoan(profile, mg,
					McEconomyMod.getEconomyManager().centralBank())) {
				var loan = McEconomyMod.getEconomyManager().loanManager().getLoan(uuid).orElseThrow();
				return ActionResult.ok("Kredi alındı. Taksit: " + GoldStandard.formatMilligrams(loan.installment()));
			}
			return ActionResult.fail("Kredi reddedildi (skor, limit veya mevcut borç).");
		} catch (Exception e) {
			return ActionResult.fail("Kredi işlemi başarısız.");
		}
	}

	public static ActionResult loanPay(UUID uuid) {
		PlayerEconomyProfile profile = McEconomyMod.getEconomyManager().profiles().get(uuid);
		if (profile == null) {
			return ActionResult.fail("Profil bulunamadı.");
		}
		try {
			if (McEconomyMod.getEconomyManager().loanManager().payInstallment(profile)) {
				long remaining = McEconomyMod.getEconomyManager().loanManager().getLoan(uuid)
						.map(l -> l.remaining()).orElse(0L);
				return ActionResult.ok("Taksit ödendi. Kalan: " + GoldStandard.formatMilligrams(remaining));
			}
			return ActionResult.fail("Aktif kredi yok veya yetersiz bakiye.");
		} catch (Exception e) {
			return ActionResult.fail("Ödeme başarısız.");
		}
	}

	public static ActionResult setJob(UUID uuid, String jobId) {
		JobType job = JobType.fromString(jobId);
		if (job == null) {
			return ActionResult.fail("Geçersiz meslek.");
		}
		if (!McEconomyMod.getEconomyManager().jobManager().setJob(uuid, job)) {
			return ActionResult.fail("Meslek atanamadı.");
		}
		var server = McEconomyMod.getEconomyManager().server();
		ServerPlayer player = server != null ? server.getPlayerList().getPlayer(uuid) : null;
		if (player != null) {
			JobKitService.giveKit(player, job);
		}
		return ActionResult.ok("Meslek: " + job.displayName()
				+ (player != null ? " — geçici ekipman verildi." : " — oyuna girince ekipman alırsınız."));
	}

	public static ActionResult resignJob(UUID uuid) {
		if (!McEconomyMod.getEconomyManager().jobManager().resignJob(uuid)) {
			return ActionResult.fail("Zaten bir mesleginiz yok.");
		}
		var server = McEconomyMod.getEconomyManager().server();
		ServerPlayer player = server != null ? server.getPlayerList().getPlayer(uuid) : null;
		if (player != null) {
			McEconomyMod.getEconomyManager().questManager().cancelQuest(player);
			JobKitService.reclaimKit(player);
		} else {
			McEconomyMod.getEconomyManager().questManager().cancelQuest(uuid);
		}
		return ActionResult.ok("Mesleginizden istifa ettiniz.");
	}

	public static ActionResult cancelQuest(UUID uuid) {
		var server = McEconomyMod.getEconomyManager().server();
		ServerPlayer player = server != null ? server.getPlayerList().getPlayer(uuid) : null;
		if (player != null) {
			if (McEconomyMod.getEconomyManager().questManager().cancelQuest(player)) {
				return ActionResult.ok("Aktif goreviniz iptal edildi.");
			}
		} else if (McEconomyMod.getEconomyManager().questManager().cancelQuest(uuid)) {
			return ActionResult.ok("Aktif goreviniz iptal edildi.");
		}
		return ActionResult.fail("Iptal edilecek aktif gorev yok.");
	}

	public static ActionResult assignQuest(ServerPlayer player) {
		UUID uuid = player.getUUID();
		QuestManager questManager = McEconomyMod.getEconomyManager().questManager();
		if (questManager.getQuest(uuid) != null) {
			return ActionResult.fail("Zaten aktif göreviniz var.");
		}
		var workJob = McEconomyMod.getEconomyManager().playerEmploymentService().resolveWorkJobType(uuid);
		if (workJob.isEmpty()) {
			return ActionResult.fail("Sirkette calisin veya meslek secin.");
		}
		var quest = questManager.assignRandomQuest(uuid, workJob.get(), player);
		if (quest == null) {
			return ActionResult.fail("Gorev atanamadi.");
		}
		JsonObject data = new JsonObject();
		data.addProperty("title", quest.title());
		data.addProperty("progress", quest.progress());
		data.addProperty("required", quest.required());
		data.addProperty("reward", GoldStandard.formatMilligrams(quest.reward()));
		data.addProperty("companyQuest", quest.isCompanyQuest());
		String suffix = quest.isCompanyQuest() ? " (sirket gorevi)" : "";
		return ActionResult.ok("Görev alındı: " + quest.title() + suffix + " — ekipman verildi.", data);
	}

	public static ActionResult assignQuest(UUID uuid) {
		var server = McEconomyMod.getEconomyManager().server();
		ServerPlayer player = server != null ? server.getPlayerList().getPlayer(uuid) : null;
		if (player != null) {
			return assignQuest(player);
		}
		QuestManager questManager = McEconomyMod.getEconomyManager().questManager();
		if (questManager.getQuest(uuid) != null) {
			return ActionResult.fail("Zaten aktif göreviniz var.");
		}
		var workJob = McEconomyMod.getEconomyManager().playerEmploymentService().resolveWorkJobType(uuid);
		if (workJob.isEmpty()) {
			return ActionResult.fail("Sirkette calisin veya meslek secin.");
		}
		var quest = questManager.assignRandomQuest(uuid, workJob.get());
		if (quest == null) {
			return ActionResult.fail("Gorev atanamadi.");
		}
		JsonObject data = new JsonObject();
		data.addProperty("title", quest.title());
		data.addProperty("progress", quest.progress());
		data.addProperty("required", quest.required());
		data.addProperty("reward", GoldStandard.formatMilligrams(quest.reward()));
		data.addProperty("companyQuest", quest.isCompanyQuest());
		return ActionResult.ok("Görev alındı: " + quest.title() + " (çevrimiçi olunca ekipman).", data);
	}

	public static ActionResult completeQuest(ServerPlayer player) {
		QuestManager questManager = McEconomyMod.getEconomyManager().questManager();
		QuestManager.ActiveQuest quest = questManager.getQuest(player.getUUID());
		if (quest == null) {
			return ActionResult.fail("Aktif görev yok.");
		}
		if (questManager.completeQuest(player)) {
			String msg = quest.isCompanyQuest()
					? "Sirket gorevi tamamlandi — uretim sirkete aktarildi."
					: "Görev tamamlandı! Ödül: " + GoldStandard.formatMilligrams(quest.reward());
			return ActionResult.ok(msg);
		}
		if (quest.type() == QuestManager.QuestType.DELIVER_ITEM) {
			return ActionResult.fail("Gerekli eşyalar envanterde yok.");
		}
		return ActionResult.fail("Görev henüz tamamlanmadı.");
	}

	public static ActionResult createCompany(UUID uuid, String name) {
		try {
			if (McEconomyMod.getEconomyManager().companyManager().createCompany(name, uuid)) {
				return ActionResult.ok("Şirket kuruldu: " + name);
			}
		} catch (Exception e) {
			return ActionResult.fail("Şirket kurulamadı.");
		}
		return ActionResult.fail("Şirket kurulamadı (isim veya bakiye).");
	}

	public static ActionResult buyShares(UUID uuid, String companyName, int amount) {
		double index = McEconomyMod.getEconomyManager().marketService().economyIndex().calculate();
		try {
			if (McEconomyMod.getEconomyManager().companyManager().buyShares(uuid, companyName, amount, index)) {
				return ActionResult.ok(amount + " hisse alındı.");
			}
		} catch (Exception e) {
			return ActionResult.fail("Hisse alımı başarısız.");
		}
		return ActionResult.fail("Yetersiz bakiye veya geçersiz şirket.");
	}

	public static ActionResult sellShares(UUID uuid, String companyName, int amount) {
		double index = McEconomyMod.getEconomyManager().marketService().economyIndex().calculate();
		try {
			if (McEconomyMod.getEconomyManager().companyManager().sellShares(uuid, companyName, amount, index)) {
				return ActionResult.ok(amount + " hisse satıldı.");
			}
		} catch (Exception e) {
			return ActionResult.fail("Hisse satışı başarısız.");
		}
		return ActionResult.fail("Yeterli hisse yok.");
	}

	public static ActionResult buyToken(UUID uuid, String symbol, int amount) {
		try {
			if (McEconomyMod.getEconomyManager().exchangeService().buyToken(uuid, symbol, amount)) {
				return ActionResult.ok(amount + " " + symbol.toUpperCase() + " coin alındı.");
			}
		} catch (Exception e) {
			return ActionResult.fail("Coin alımı başarısız.");
		}
		return ActionResult.fail("Coin alınamadı (bakiye, arz veya kısıtlama).");
	}

	public static ActionResult fireEmployee(UUID uuid, long employeeId) {
		if (McEconomyMod.getEconomyManager().workforceService().fireEmployee(uuid, employeeId)) {
			return ActionResult.ok("Calisan isten cikarildi.");
		}
		return ActionResult.fail("Calisan bulunamadi veya yetkiniz yok.");
	}

	public static ActionResult raiseSalary(UUID uuid, long employeeId, long displayMc) {
		long mg = mgForDisplayMc(displayMc);
		if (mg <= 0) {
			return ActionResult.fail("Geçersiz tutar.");
		}
		if (McEconomyMod.getEconomyManager().workforceService().raiseSalary(uuid, employeeId, mg)) {
			return ActionResult.ok("Maas guncellendi: " + GoldStandard.formatMilligrams(mg));
		}
		return ActionResult.fail("Maas guncellenemedi.");
	}

	public static ActionResult payBonus(UUID uuid, String company) {
		long paid = McEconomyMod.getEconomyManager().workforceService().payBonus(uuid, company == null || company.isEmpty() ? null : company);
		if (paid > 0) {
			return ActionResult.ok("Ikramiye odendi: " + com.mceconomy.economy.GoldStandard.formatMilligrams(paid));
		}
		return ActionResult.fail("Ikramiye odenemedi (calisan yok veya yetersiz bakiye).");
	}

	public static ActionResult teleportCompanyVault(UUID uuid, String company) {
		if (company == null || company.isBlank()) {
			return ActionResult.fail("Sirket adi gerekli.");
		}
		var server = McEconomyMod.getEconomyManager().server();
		ServerPlayer player = server != null ? server.getPlayerList().getPlayer(uuid) : null;
		if (player == null) {
			return ActionResult.fail("Sandiga gitmek icin oyunda cevrimici olmalisiniz.");
		}
		if (McEconomyMod.getEconomyManager().companyVaultService().teleportToVault(player, company)) {
			return ActionResult.ok("Gizli sirket sandigina isinlandiniz. Sandiktan esya alin.");
		}
		return ActionResult.fail("Sandiga gidilemedi.");
	}

	public static ActionResult exitCompanyVault(UUID uuid) {
		var server = McEconomyMod.getEconomyManager().server();
		ServerPlayer player = server != null ? server.getPlayerList().getPlayer(uuid) : null;
		if (player == null) {
			return ActionResult.fail("Oyunda cevrimici olmalisiniz.");
		}
		if (McEconomyMod.getEconomyManager().companyVaultService().teleportBack(player)) {
			return ActionResult.ok("Onceki konumunuza dondunuz.");
		}
		return ActionResult.fail("Donus konumu yok.");
	}

	public static ActionResult acceptApplication(UUID uuid, long applicationId) {
		var server = McEconomyMod.getEconomyManager().server();
		var manager = McEconomyMod.getEconomyManager();
		if (manager.workforceService().acceptApplication(uuid, applicationId, server)
				|| manager.playerEmploymentService().acceptApplication(uuid, applicationId, server)) {
			return ActionResult.ok("Basvuru kabul edildi.");
		}
		return ActionResult.fail("Basvuru kabul edilemedi.");
	}

	public static ActionResult rejectApplication(UUID uuid, long applicationId) {
		var server = McEconomyMod.getEconomyManager().server();
		var manager = McEconomyMod.getEconomyManager();
		if (manager.workforceService().rejectApplication(uuid, applicationId, server)
				|| manager.playerEmploymentService().rejectApplication(uuid, applicationId, server)) {
			return ActionResult.ok("Basvuru reddedildi.");
		}
		return ActionResult.fail("Basvuru reddedilemedi.");
	}

	public static ActionResult casinoPlay(UUID uuid, String game, long displayMc, String choice) {
		long betMg = mgForDisplayMc(displayMc);
		if (betMg <= 0) {
			return ActionResult.fail("Geçersiz bahis.");
		}
		var result = McEconomyMod.getEconomyManager().casinoService().play(uuid, game, betMg, choice);
		if (!result.success()) {
			return ActionResult.fail(result.message());
		}
		return ActionResult.ok(result.message());
	}

	public static ActionResult sellAllShares(UUID uuid) {
		var manager = McEconomyMod.getEconomyManager();
		double index = manager.marketService().economyIndex().calculate();
		int soldCompanies = 0;
		try {
			for (var company : manager.companyManager().allCompanies()) {
				int owned = manager.companyManager().getShareCount(uuid, company);
				if (owned > 0 && manager.companyManager().sellShares(uuid, company.name(), owned, index)) {
					soldCompanies++;
				}
			}
		} catch (Exception e) {
			return ActionResult.fail("Toplu satis sirasinda hata olustu.");
		}
		if (soldCompanies == 0) {
			return ActionResult.fail("Satilacak hisse yok.");
		}
		return ActionResult.ok(soldCompanies + " sirketteki tum hisseleriniz satildi.");
	}

	public static ActionResult sellAllTokens(UUID uuid) {
		var manager = McEconomyMod.getEconomyManager();
		int sold = 0;
		try {
			for (var token : manager.exchangeService().allTokens()) {
				int owned = manager.exchangeService().tokenBalance(uuid, token);
				if (owned > 0 && manager.exchangeService().sellToken(uuid, token.symbol(), owned)) {
					sold++;
				}
			}
		} catch (Exception e) {
			return ActionResult.fail("Toplu coin satisi sirasinda hata olustu.");
		}
		if (sold == 0) {
			return ActionResult.fail("Satilacak coin yok.");
		}
		return ActionResult.ok(sold + " coin tamamen satildi.");
	}

	public static ActionResult sellToken(UUID uuid, String symbol, int amount) {
		if (amount <= 0) {
			return ActionResult.fail("Geçerli bir miktar girin.");
		}
		var exchange = McEconomyMod.getEconomyManager().exchangeService();
		var tokenOpt = exchange.findToken(symbol);
		if (tokenOpt.isEmpty()) {
			return ActionResult.fail("Coin bulunamadı.");
		}
		int owned = exchange.tokenBalance(uuid, tokenOpt.get());
		if (owned < amount) {
			return ActionResult.fail("Portföyünüzde " + owned + " "
					+ symbol.toUpperCase() + " var; " + amount + " satılamaz.");
		}
		if (McEconomyMod.getEconomyManager().masakService().isRestricted(uuid)) {
			return ActionResult.fail("MASAK kısıtlaması nedeniyle borsa işlemi yapılamaz.");
		}
		try {
			if (exchange.sellToken(uuid, symbol, amount)) {
				return ActionResult.ok(amount + " " + symbol.toUpperCase() + " coin satıldı.");
			}
		} catch (Exception e) {
			McEconomyMod.LOGGER.error("Coin satisi", e);
			return ActionResult.fail("Coin satışı başarısız.");
		}
		return ActionResult.fail("Satış gerçekleştirilemedi.");
	}

	public static ActionResult createToken(UUID uuid, String symbol, String name, int supply, long priceMg) {
		try {
			if (McEconomyMod.getEconomyManager().exchangeService()
					.createToken(uuid, symbol, name, supply, priceMg)) {
				return ActionResult.ok("Coin oluşturuldu: " + symbol.toUpperCase());
			}
		} catch (Exception e) {
			return ActionResult.fail("Coin oluşturulamadı.");
		}
		return ActionResult.fail("Coin oluşturulamadı (sembol, ücret veya bakiye).");
	}

	public static ActionResult listCompany(UUID uuid, String companyName, String ticker) {
		try {
			if (McEconomyMod.getEconomyManager().exchangeService().listCompany(uuid, companyName, ticker)) {
				return ActionResult.ok(companyName + " borsada listelendi (" + ticker + ").");
			}
		} catch (Exception e) {
			return ActionResult.fail("Listeleme başarısız.");
		}
		return ActionResult.fail("Listeleme başarısız (sahiplik, ücret veya ticker).");
	}

	public static ActionResult teleportVault(ServerPlayer player) {
		if (McEconomyMod.getEconomyManager().vaultService().teleportToVault(player)) {
			return ActionResult.ok("Kisisel kasaniza isinlandiniz.");
		}
		return ActionResult.fail("Kasaya isinlanilamadi.");
	}

	public static ActionResult vaultBack(ServerPlayer player) {
		if (McEconomyMod.getEconomyManager().vaultService().teleportBack(player)) {
			return ActionResult.ok("Onceki konumunuza dondunuz.");
		}
		return ActionResult.fail("Donus konumu yok. Once kasaya gidin.");
	}

	public static ActionResult startHeist(ServerPlayer player) {
		if (McEconomyMod.getEconomyManager().heistService().start(
				player.getName().getString(), player.getUUID())) {
			return ActionResult.ok("Soygun protokolu baslatildi! Chati izleyin.");
		}
		return ActionResult.fail("Zaten aktif bir soygun protokolu var.");
	}

	public static ActionResult inventoryMarketSell(ServerPlayer player, String itemId, int quantity) {
		var item = McEconomyMod.getEconomyManager().playerBlackMarket().resolveItem(itemId);
		if (item == net.minecraft.world.item.Items.AIR) {
			return ActionResult.fail("Gecersiz item.");
		}
		Commodity commodity = Commodity.fromItem(item);
		if (commodity == null || !commodity.sellable()) {
			return ActionResult.fail("Bu item markette satilamaz.");
		}
		if (McEconomyMod.getEconomyManager().marketService().sell(player, commodity, quantity)) {
			return ActionResult.ok(quantity + "x " + commodity.displayName() + " markette satildi.");
		}
		return ActionResult.fail("Envanterde yeterli item yok.");
	}

	public static ActionResult inventoryBlackMarketList(ServerPlayer player, String itemId, int quantity, long displayMc) {
		long priceMg = mgForDisplayMc(displayMc);
		if (priceMg <= 0) {
			return ActionResult.fail("Geçersiz fiyat.");
		}
		var listing = McEconomyMod.getEconomyManager().playerBlackMarket()
				.createListing(player, itemId, quantity, priceMg);
		if (listing.isPresent()) {
			return ActionResult.ok(quantity + "x " + listing.get().displayName()
					+ " karaborsaya kondu. Fiyat: " + GoldStandard.formatMilligrams(priceMg) + "/adet");
		}
		return ActionResult.fail("Envanterde yeterli item yok veya gecersiz item.");
	}

	public static ActionResult openLeverage(UUID uuid, String symbol, boolean isLong, int leverage, long displayMc) {
		long marginMg = mgForDisplayMc(displayMc);
		if (marginMg <= 0) {
			return ActionResult.fail("Geçersiz teminat.");
		}
		String result = McEconomyMod.getEconomyManager().leverageService()
				.openPosition(uuid, symbol, isLong, leverage, marginMg);
		boolean ok = result.startsWith("ACILDI");
		return ok ? ActionResult.ok(result) : ActionResult.fail(result);
	}

	public static ActionResult closeLeverage(UUID uuid, int positionId) {
		String result = McEconomyMod.getEconomyManager().leverageService().closePosition(uuid, positionId);
		boolean ok = result.startsWith("KAPANDI");
		return ok ? ActionResult.ok(result) : ActionResult.fail(result);
	}

	public static ActionResult delistCompany(UUID uuid, String companyName) {
		try {
			if (McEconomyMod.getEconomyManager().companyManager().delistCompany(companyName, uuid)) {
				return ActionResult.ok(companyName + " borsadan cikarildi.");
			}
		} catch (Exception e) {
			return ActionResult.fail("Cikarma basarisiz.");
		}
		return ActionResult.fail("Cikarma basarisiz (sahiplik veya listede degil).");
	}

	public static ActionResult purchaseCert(UUID uuid) {
		var service = McEconomyMod.getEconomyManager().privateBankService();
		if (service.hasCertificate(uuid)) {
			return ActionResult.ok("Zaten bankacılık sertifikanız var.");
		}
		long cost = com.mceconomy.config.EconomyConfig.bankCertificateCostMg();
		var profile = McEconomyMod.getEconomyManager().profiles().get(uuid);
		long balance = profile != null ? profile.wallet().balance() : 0;
		if (service.purchaseCertificate(uuid)) {
			return ActionResult.ok("Bankacılık sertifikası alındı. (Ücret: "
					+ com.mceconomy.economy.GoldStandard.formatMilligrams(cost) + ")");
		}
		return ActionResult.fail("Yetersiz bakiye! Gereken: "
				+ com.mceconomy.economy.GoldStandard.formatMilligrams(cost)
				+ " — Cüzdanınız: " + com.mceconomy.economy.GoldStandard.formatMilligrams(balance)
				+ " (Eksik: " + com.mceconomy.economy.GoldStandard.formatMilligrams(Math.max(0, cost - balance)) + ")");
	}

	public static ActionResult openPrivateBank(UUID uuid, String name) {
		if (!McEconomyMod.getEconomyManager().privateBankService().hasCertificate(uuid)) {
			return ActionResult.fail("Önce bankacılık sertifikası alın.");
		}
		try {
			if (McEconomyMod.getEconomyManager().privateBankService().openBank(uuid, name)) {
				return ActionResult.ok("Özel banka açıldı: " + name);
			}
		} catch (Exception e) {
			return ActionResult.fail("Banka açılamadı.");
		}
		return ActionResult.fail("Banka açılamadı.");
	}

	public static ActionResult privateDeposit(UUID uuid, String bankName, long displayMc) {
		try {
			long mg = mgForDisplayMc(displayMc);
			if (mg <= 0) {
				return ActionResult.fail("Geçersiz tutar.");
			}
			if (McEconomyMod.getEconomyManager().privateBankService().deposit(uuid, bankName, mg)) {
				return ActionResult.ok("Özel bankaya yatırıldı.");
			}
		} catch (Exception e) {
			return ActionResult.fail("Yatırma başarısız.");
		}
		return ActionResult.fail("Yetersiz bakiye veya geçersiz banka.");
	}

	public static ActionResult privateWithdraw(UUID uuid, String bankName, long displayMc) {
		try {
			long mg = mgForDisplayMc(displayMc);
			if (mg <= 0) {
				return ActionResult.fail("Geçersiz tutar.");
			}
			if (McEconomyMod.getEconomyManager().privateBankService().withdraw(uuid, bankName, mg)) {
				return ActionResult.ok("Özel bankadan çekildi.");
			}
		} catch (Exception e) {
			return ActionResult.fail("Çekme başarısız.");
		}
		return ActionResult.fail("Yetersiz mevduat.");
	}

	public static ActionResult submitAppeal(UUID uuid, String playerName, String subject, String message, Long alertId) {
		try {
			if (McEconomyMod.getEconomyManager().appealService()
					.submit(uuid, playerName, subject, message, alertId)) {
				return ActionResult.ok("İtiraz gönderildi.");
			}
		} catch (Exception e) {
			return ActionResult.fail("İtiraz gönderilemedi.");
		}
		return ActionResult.fail("İtiraz gönderilemedi.");
	}

	public static ActionResult submitComplaint(UUID uuid, String reporterName, String target, String category,
			String subject, String message) {
		try {
			if (McEconomyMod.getEconomyManager().reportService()
					.submitComplaint(uuid, reporterName, target, category, subject, message)) {
				return ActionResult.ok("Şikayetiniz kaydedildi.");
			}
		} catch (Exception e) {
			return ActionResult.fail("Şikayet gönderilemedi.");
		}
		return ActionResult.fail("Geçersiz hedef veya kendinizi şikayet edemezsiniz.");
	}

	public static ActionResult submitTipOff(UUID uuid, String reporterName, String target, String category, String message) {
		try {
			if (McEconomyMod.getEconomyManager().reportService()
					.submitTipOff(uuid, reporterName, target, category, message)) {
				return ActionResult.ok("İhbarınız kaydedildi.");
			}
		} catch (Exception e) {
			return ActionResult.fail("İhbar gönderilemedi.");
		}
		return ActionResult.fail("İhbar gönderilemedi.");
	}

	public static ActionResult justiceInvestigate(long reportId) {
		try {
			if (McEconomyMod.getEconomyManager().reportService().markInvestigating(reportId)) {
				return ActionResult.ok("Rapor soruşturmaya alındı.");
			}
		} catch (Exception e) {
			return ActionResult.fail("İşlem başarısız.");
		}
		return ActionResult.fail("Rapor güncellenemedi.");
	}

	public static ActionResult justiceDismiss(long reportId, String note) {
		try {
			if (McEconomyMod.getEconomyManager().reportService().dismiss(reportId, note)) {
				return ActionResult.ok("Rapor reddedildi.");
			}
		} catch (Exception e) {
			return ActionResult.fail("İşlem başarısız.");
		}
		return ActionResult.fail("Rapor kapatılamadı.");
	}

	public static ActionResult justiceGuilty(long reportId, String note, int prisonMinutes, String adminName) {
		try {
			var reports = McEconomyMod.getEconomyManager().reportService();
			var prison = McEconomyMod.getEconomyManager().prisonService();
			var opt = reports.find(reportId);
			if (opt.isEmpty()) {
				return ActionResult.fail("Rapor bulunamadı.");
			}
			Long sentenceId = null;
			if (prisonMinutes > 0 && opt.get().targetName() != null && !opt.get().targetName().isBlank()) {
				if (prison.imprisonByName(opt.get().targetName(), prisonMinutes,
						note != null && !note.isBlank() ? note : "Rapor #" + reportId, adminName)) {
					UUID targetUuid = opt.get().targetUuid() != null ? opt.get().targetUuid()
							: com.mceconomy.command.BalanceCommand.findPlayerUuid(opt.get().targetName());
					if (targetUuid != null) {
						sentenceId = prison.sentenceFor(targetUuid).map(s -> s.id()).orElse(null);
					}
				}
			}
			if (reports.markGuilty(reportId, note, sentenceId)) {
				return ActionResult.ok("Rapor suçlu bulundu"
						+ (prisonMinutes > 0 ? " ve hapis uygulandi." : "."));
			}
		} catch (Exception e) {
			McEconomyMod.LOGGER.error("Adalet karari", e);
			return ActionResult.fail("Karar uygulanamadi.");
		}
		return ActionResult.fail("Karar uygulanamadi.");
	}

	public static ActionResult justiceImprison(String playerName, int minutes, String reason, String adminName) {
		if (playerName == null || playerName.isBlank()) {
			return ActionResult.fail("Oyuncu adi gerekli.");
		}
		if (minutes <= 0) {
			return ActionResult.fail("Sure 0'dan buyuk olmali.");
		}
		try {
			if (McEconomyMod.getEconomyManager().prisonService()
					.imprisonByName(playerName.trim(), minutes,
							reason != null && !reason.isBlank() ? reason : "OP panel",
							adminName != null ? adminName : "Dashboard OP")) {
				return ActionResult.ok(playerName + " " + minutes + " dakika hapse gonderildi.");
			}
		} catch (Exception e) {
			McEconomyMod.LOGGER.error("Hapis uygulanamadi", e);
			return ActionResult.fail("Hapis uygulanamadi.");
		}
		return ActionResult.fail("Oyuncu bulunamadi veya zaten hapiste.");
	}

	public static ActionResult justiceReleasePrison(String playerName) {
		try {
			UUID uuid = com.mceconomy.command.BalanceCommand.findPlayerUuid(playerName);
			if (uuid == null) {
				return ActionResult.fail("Oyuncu bulunamadi.");
			}
			if (McEconomyMod.getEconomyManager().prisonService().release(uuid)) {
				return ActionResult.ok("Oyuncu tahliye edildi.");
			}
		} catch (Exception e) {
			return ActionResult.fail("Tahliye basarisiz.");
		}
		return ActionResult.fail("Aktif hapis cezasi yok.");
	}

	public static ActionResult blackMarketBuy(ServerPlayer player, String goodId, int quantity) {
		var playerListing = McEconomyMod.getEconomyManager().playerBlackMarket().get(goodId);
		if (playerListing.isPresent()) {
			if (McEconomyMod.getEconomyManager().playerBlackMarket().purchase(player, playerListing.get(), quantity)) {
				return ActionResult.ok(quantity + "x " + playerListing.get().displayName() + " alindi (oyuncu ilani).");
			}
			return ActionResult.fail("Yetersiz kara para, stok yok veya envanter dolu.");
		}
		var custom = McEconomyMod.getEconomyManager().customBlackMarket().get(goodId);
		if (custom != null) {
			if (McEconomyMod.getEconomyManager().blackMarketService().buyCustom(player, custom, quantity)) {
				return ActionResult.ok(quantity + "x " + custom.displayName() + " alındı.");
			}
			return ActionResult.fail("Yetersiz kara para veya envanter dolu.");
		}
		IllegalGood good = IllegalGood.fromId(goodId);
		if (good == null) {
			return ActionResult.fail("Geçersiz ürün.");
		}
		if (McEconomyMod.getEconomyManager().blackMarketService().buy(player, good, quantity)) {
			return ActionResult.ok(quantity + "x " + good.displayName() + " alındı.");
		}
		return ActionResult.fail("Yetersiz kara para veya envanter dolu.");
	}

	public static ActionResult blackMarketSell(ServerPlayer player, String goodId, int quantity) {
		var custom = McEconomyMod.getEconomyManager().customBlackMarket().get(goodId);
		if (custom != null) {
			if (McEconomyMod.getEconomyManager().blackMarketService().sellCustom(player, custom, quantity)) {
				return ActionResult.ok(quantity + "x " + custom.displayName() + " satıldı.");
			}
			return ActionResult.fail("Envanterde yeterli eşya yok.");
		}
		IllegalGood good = IllegalGood.fromId(goodId);
		if (good == null) {
			return ActionResult.fail("Geçersiz ürün.");
		}
		if (McEconomyMod.getEconomyManager().blackMarketService().sell(player, good, quantity)) {
			return ActionResult.ok(quantity + "x " + good.displayName() + " satıldı.");
		}
		return ActionResult.fail("Envanterde yeterli eşya yok.");
	}

	public static ActionResult addCustomBlackMarket(String name, String itemId, long displayMc) {
		long priceMg = mgForDisplayMc(displayMc);
		if (name == null || name.isBlank() || itemId == null || itemId.isBlank() || priceMg <= 0) {
			return ActionResult.fail("Geçerli isim, item id ve fiyat girin.");
		}
		var good = McEconomyMod.getEconomyManager().customBlackMarket().add(name, itemId, priceMg);
		if (good == null) {
			return ActionResult.fail("Geçersiz item id: " + itemId + " (örn: minecraft:diamond)");
		}
		return ActionResult.ok("Karaborsaya eklendi: " + good.displayName());
	}

	public static ActionResult removeCustomBlackMarket(String id) {
		if (McEconomyMod.getEconomyManager().customBlackMarket().remove(id)) {
			return ActionResult.ok("Ürün kaldırıldı.");
		}
		return ActionResult.fail("Ürün bulunamadı.");
	}

	public static ActionResult launder(ServerPlayer player, long displayMc) {
		long mg = mgForDisplayMc(displayMc);
		if (mg <= 0) {
			return ActionResult.fail("Geçersiz tutar.");
		}
		LaunderingService.LaunderResult result = McEconomyMod.getEconomyManager().launderingService().attempt(player, mg);
		return switch (result.outcome()) {
			case SUCCESS -> ActionResult.ok("Aklama başarılı: " + GoldStandard.formatMilligrams(result.cleanedMg()) + " temizlendi.");
			case CAUGHT -> ActionResult.fail("Tespit edildiniz! Ceza: " + GoldStandard.formatMilligrams(result.fineMg()));
			case INSUFFICIENT -> ActionResult.fail("Yetersiz kara para.");
		};
	}

	public static ActionResult masakResolve(String playerName) {
		UUID uuid = BalanceCommand.findPlayerUuid(playerName);
		if (uuid == null) {
			return ActionResult.fail("Oyuncu bulunamadı.");
		}
		for (var alert : McEconomyMod.getEconomyManager().masakService().openAlerts()) {
			if (alert.playerUuid().equals(uuid)) {
				McEconomyMod.getEconomyManager().masakService().resolveAlert(alert.id(), uuid);
			}
		}
		return ActionResult.ok(playerName + " hesabı çözüldü.");
	}

	public static ActionResult masakFine(String playerName, long displayMc) {
		UUID uuid = BalanceCommand.findPlayerUuid(playerName);
		if (uuid == null) {
			return ActionResult.fail("Oyuncu bulunamadı.");
		}
		long mg = mgForDisplayMc(displayMc);
		if (mg <= 0) {
			return ActionResult.fail("Geçersiz tutar.");
		}
		McEconomyMod.getEconomyManager().masakService().applyFine(uuid, mg);
		return ActionResult.ok(playerName + " → " + GoldStandard.formatMilligrams(mg) + " ceza uygulandı.");
	}

	public static ActionResult masakBlacklist(String playerName) {
		UUID uuid = BalanceCommand.findPlayerUuid(playerName);
		if (uuid == null) {
			return ActionResult.fail("Oyuncu bulunamadı.");
		}
		McEconomyMod.getEconomyManager().masakService().blacklist(uuid);
		return ActionResult.ok(playerName + " kara listeye alındı.");
	}

	public static ActionResult triggerEvent(String typeId, long durationMs) {
		EconomyEventType type = EconomyEventType.fromId(typeId);
		if (type == null) {
			return ActionResult.fail("Geçersiz olay tipi.");
		}
		var manager = McEconomyMod.getEconomyManager();
		if (manager.eventManager().triggerEvent(type, durationMs,
				manager.marketService().priceEngine(), manager.centralBank(), manager.server())) {
			return ActionResult.ok("Ekonomi olayı tetiklendi: " + type.id());
		}
		return ActionResult.fail("Olay tetiklenemedi.");
	}

	public static ActionResult mbopGrant(String playerName) {
		UUID uuid = BalanceCommand.findPlayerUuid(playerName);
		if (uuid == null) {
			return ActionResult.fail("Oyuncu bulunamadı.");
		}
		PlayerEconomyProfile profile = McEconomyMod.getEconomyManager().profiles().get(uuid);
		if (profile == null) {
			return ActionResult.fail("Profil bulunamadı.");
		}
		profile.setCentralBankOfficial(true);
		try {
			McEconomyMod.getEconomyManager().playerRepository().save(profile);
			return ActionResult.ok(playerName + " MB yetkisi verildi.");
		} catch (Exception e) {
			return ActionResult.fail("Kayıt başarısız.");
		}
	}

	public static ActionResult mbopRevoke(String playerName) {
		UUID uuid = BalanceCommand.findPlayerUuid(playerName);
		if (uuid == null) {
			return ActionResult.fail("Oyuncu bulunamadı.");
		}
		PlayerEconomyProfile profile = McEconomyMod.getEconomyManager().profiles().get(uuid);
		if (profile == null) {
			return ActionResult.fail("Profil bulunamadı.");
		}
		profile.setCentralBankOfficial(false);
		try {
			McEconomyMod.getEconomyManager().playerRepository().save(profile);
			return ActionResult.ok(playerName + " MB yetkisi alındı.");
		} catch (Exception e) {
			return ActionResult.fail("Kayıt başarısız.");
		}
	}

	public static ActionResult rebuildCentralBank() {
		var server = McEconomyMod.getEconomyManager().server();
		if (server == null) {
			return ActionResult.fail("Sunucu hazır değil.");
		}
		CentralBankPlacer.rebuild(server);
		return ActionResult.ok("Merkez Bankası yeniden kuruldu.");
	}

	public static ActionResult employmentApply(UUID uuid, String company, String role, long salaryMg) {
		ServerPlayer player = onlinePlayer(uuid);
		if (player == null) {
			return ActionResult.fail("Basvuru icin oyunda olmalisiniz.");
		}
		if (McEconomyMod.getEconomyManager().playerEmploymentService()
				.apply(player, McEconomyMod.getEconomyManager().server(), company, role, salaryMg, null)) {
			return ActionResult.ok("Is basvurusu gonderildi.");
		}
		return ActionResult.fail("Basvuru gonderilemedi.");
	}

	public static ActionResult employmentQuit(UUID uuid) {
		var server = McEconomyMod.getEconomyManager().server();
		if (McEconomyMod.getEconomyManager().playerEmploymentService().quit(uuid, server)) {
			return ActionResult.ok("Sirketten ayrildiniz.");
		}
		return ActionResult.fail("Bir sirkette calismiyorsunuz.");
	}

	public static ActionResult tradeDisputeResolve(String adminName, long disputeId, boolean refund, String note) {
		if (McEconomyMod.getEconomyManager().playerTradeService()
				.resolveDispute(adminName, disputeId, refund, note != null ? note : "")) {
			return ActionResult.ok(refund ? "Iade yapildi." : "Sikayet reddedildi.");
		}
		return ActionResult.fail("Sikayet cozulemedi.");
	}

	public static ServerPlayer onlinePlayer(UUID uuid) {
		var server = McEconomyMod.getEconomyManager().server();
		if (server == null) {
			return null;
		}
		return server.getPlayerList().getPlayer(uuid);
	}

	private static void notifyPlayer(UUID target, String message) {
		ServerPlayer player = onlinePlayer(target);
		if (player != null) {
			player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a" + message));
		}
	}
}
