package com.mceconomy.web;

import com.google.gson.JsonObject;
import com.mceconomy.McEconomyMod;
import com.mceconomy.bank.BankAccountType;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.command.BalanceCommand;
import com.mceconomy.economy.EconomyManager;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.job.JobType;
import com.mceconomy.player.PlayerEconomyProfile;
import com.mceconomy.web.DashboardActionService.ActionResult;

import java.util.UUID;

public final class AdminEconomyService {
	private AdminEconomyService() {
	}

	private static long mgForDisplayMc(long displayMc) {
		if (displayMc == 0) {
			return 0;
		}
		return GoldStandard.milligramsForDisplayMc(displayMc);
	}

	private static UUID resolvePlayer(String playerName, String uuidStr) {
		if (uuidStr != null && !uuidStr.isBlank()) {
			try {
				return UUID.fromString(uuidStr.trim());
			} catch (IllegalArgumentException ignored) {
				return null;
			}
		}
		if (playerName == null || playerName.isBlank()) {
			return null;
		}
		return BalanceCommand.findPlayerUuid(playerName.trim());
	}

	private static PlayerEconomyProfile requireProfile(UUID uuid) {
		if (uuid == null) {
			return null;
		}
		return McEconomyMod.getEconomyManager().profiles().get(uuid);
	}

	private static ActionResult failNoPlayer() {
		return ActionResult.fail("Oyuncu bulunamadı.");
	}

	private static ActionResult saveProfile(PlayerEconomyProfile profile) {
		try {
			McEconomyMod.getEconomyManager().playerRepository().save(profile);
			return ActionResult.ok("Profil güncellendi: " + profile.name());
		} catch (Exception e) {
			McEconomyMod.LOGGER.error("OP profil kaydi", e);
			return ActionResult.fail("Kayıt başarısız.");
		}
	}

	public static ActionResult walletSet(String player, String uuidStr, long displayMc) {
		UUID id = resolvePlayer(player, uuidStr);
		if (requireProfile(id) == null) {
			return failNoPlayer();
		}
		var cs = McEconomyMod.getEconomyManager().currencyService();
		if (!cs.adminSetWallet(id, mgForDisplayMc(displayMc))) {
			return failNoPlayer();
		}
		return saveProfile(requireProfile(id));
	}

	public static ActionResult walletAdjust(String player, String uuidStr, long displayMc) {
		UUID id = resolvePlayer(player, uuidStr);
		PlayerEconomyProfile profile = requireProfile(id);
		if (profile == null) {
			return failNoPlayer();
		}
		var cs = McEconomyMod.getEconomyManager().currencyService();
		if (!cs.adminAdjustWallet(id, mgForDisplayMc(displayMc))) {
			return failNoPlayer();
		}
		return saveProfile(profile);
	}

	public static ActionResult dirtySet(String player, String uuidStr, long displayMc) {
		UUID id = resolvePlayer(player, uuidStr);
		PlayerEconomyProfile profile = requireProfile(id);
		if (profile == null) {
			return failNoPlayer();
		}
		if (!McEconomyMod.getEconomyManager().currencyService().adminSetDirty(id, mgForDisplayMc(displayMc))) {
			return failNoPlayer();
		}
		return saveProfile(profile);
	}

	public static ActionResult bankSet(String player, String uuidStr, String typeStr, long displayMc) {
		UUID id = resolvePlayer(player, uuidStr);
		if (requireProfile(id) == null) {
			return failNoPlayer();
		}
		BankAccountType type = "term".equalsIgnoreCase(typeStr) ? BankAccountType.TERM : BankAccountType.CHECKING;
		try {
			if (McEconomyMod.getEconomyManager().bankService().adminSetBalance(id, type, mgForDisplayMc(displayMc))) {
				return ActionResult.ok("Banka bakiyesi güncellendi.");
			}
		} catch (Exception e) {
			McEconomyMod.LOGGER.error("OP banka set", e);
			return ActionResult.fail("Banka güncellenemedi.");
		}
		return ActionResult.fail("Banka hesabı yok.");
	}

	public static ActionResult bankOpenChecking(String player, String uuidStr) {
		UUID id = resolvePlayer(player, uuidStr);
		if (requireProfile(id) == null) {
			return failNoPlayer();
		}
		return DashboardActionService.bankOpenChecking(id);
	}

	public static ActionResult bankOpenTerm(String player, String uuidStr) {
		UUID id = resolvePlayer(player, uuidStr);
		if (requireProfile(id) == null) {
			return failNoPlayer();
		}
		return DashboardActionService.bankOpenTerm(id);
	}

	public static ActionResult bankDelete(String player, String uuidStr, String typeStr) {
		UUID id = resolvePlayer(player, uuidStr);
		if (requireProfile(id) == null) {
			return failNoPlayer();
		}
		BankAccountType type = "term".equalsIgnoreCase(typeStr) ? BankAccountType.TERM : BankAccountType.CHECKING;
		try {
			if (McEconomyMod.getEconomyManager().bankService().adminDeleteAccount(id, type)) {
				return ActionResult.ok("Banka hesabı silindi.");
			}
		} catch (Exception e) {
			McEconomyMod.LOGGER.error("OP banka sil", e);
			return ActionResult.fail("Hesap silinemedi.");
		}
		return ActionResult.fail("Banka hesabı bulunamadı.");
	}

	public static ActionResult profileUpdate(String player, String uuidStr, JsonObject body) {
		UUID id = resolvePlayer(player, uuidStr);
		PlayerEconomyProfile profile = requireProfile(id);
		if (profile == null) {
			return failNoPlayer();
		}
		if (body.has("accountFrozen")) {
			profile.setAccountFrozen(body.get("accountFrozen").getAsBoolean());
		}
		if (body.has("blacklisted")) {
			profile.setBlacklisted(body.get("blacklisted").getAsBoolean());
		}
		if (body.has("bankCertified")) {
			profile.setBankCertified(body.get("bankCertified").getAsBoolean());
		}
		if (body.has("creditScore")) {
			profile.creditScore().setScore(body.get("creditScore").getAsInt());
		}
		if (body.has("jobId")) {
			String jobId = body.get("jobId").getAsString();
			if (jobId == null || jobId.isBlank()) {
				profile.setJobType(null);
			} else {
				JobType job = JobType.fromString(jobId);
				if (job != null) {
					profile.setJobType(job);
				}
			}
		}
		return saveProfile(profile);
	}

	public static ActionResult loanUpsert(String player, String uuidStr, long remainingMc, long installmentMc,
			long dueAt) {
		UUID id = resolvePlayer(player, uuidStr);
		if (requireProfile(id) == null) {
			return failNoPlayer();
		}
		try {
			double rate = McEconomyMod.getEconomyManager().centralBank().getBaseRate();
			if (McEconomyMod.getEconomyManager().loanManager().adminUpsertLoan(id,
					mgForDisplayMc(remainingMc), mgForDisplayMc(installmentMc), dueAt, rate)) {
				return ActionResult.ok("Kredi güncellendi.");
			}
		} catch (Exception e) {
			McEconomyMod.LOGGER.error("OP kredi", e);
			return ActionResult.fail("Kredi kaydedilemedi.");
		}
		return ActionResult.fail("Kredi işlemi başarısız.");
	}

	public static ActionResult loanDelete(String player, String uuidStr) {
		UUID id = resolvePlayer(player, uuidStr);
		if (requireProfile(id) == null) {
			return failNoPlayer();
		}
		try {
			if (McEconomyMod.getEconomyManager().loanManager().adminClearLoan(id)) {
				return ActionResult.ok("Kredi silindi.");
			}
		} catch (Exception e) {
			McEconomyMod.LOGGER.error("OP kredi sil", e);
			return ActionResult.fail("Kredi silinemedi.");
		}
		return ActionResult.fail("Aktif kredi yok.");
	}

	public static ActionResult sharesSet(String player, String uuidStr, String ticker, int amount) {
		UUID id = resolvePlayer(player, uuidStr);
		if (requireProfile(id) == null) {
			return failNoPlayer();
		}
		try {
			if (McEconomyMod.getEconomyManager().companyManager().adminSetShareCount(id, ticker, amount)) {
				return ActionResult.ok("Hisse güncellendi: " + ticker + " → " + amount);
			}
		} catch (Exception e) {
			McEconomyMod.LOGGER.error("OP hisse", e);
			return ActionResult.fail("Hisse güncellenemedi.");
		}
		return ActionResult.fail("Geçersiz şirket veya miktar.");
	}

	public static ActionResult tokensSet(String player, String uuidStr, String symbol, int amount) {
		UUID id = resolvePlayer(player, uuidStr);
		if (requireProfile(id) == null) {
			return failNoPlayer();
		}
		try {
			if (McEconomyMod.getEconomyManager().exchangeService().adminSetTokenHolding(id, symbol, amount)) {
				return ActionResult.ok("Coin güncellendi: " + symbol.toUpperCase() + " → " + amount);
			}
		} catch (Exception e) {
			McEconomyMod.LOGGER.error("OP coin", e);
			return ActionResult.fail("Coin güncellenemedi.");
		}
		return ActionResult.fail("Geçersiz coin veya miktar.");
	}

	public static ActionResult leverageClose(int positionId) {
		if (McEconomyMod.getEconomyManager().leverageService().adminForceClose(positionId)) {
			return ActionResult.ok("Kaldıraç pozisyonu kapatıldı.");
		}
		return ActionResult.fail("Pozisyon bulunamadı.");
	}

	public static ActionResult privateDepositSet(String player, String uuidStr, String bankName, long displayMc) {
		UUID id = resolvePlayer(player, uuidStr);
		if (requireProfile(id) == null) {
			return failNoPlayer();
		}
		try {
			if (McEconomyMod.getEconomyManager().privateBankService()
					.adminSetDeposit(id, bankName, mgForDisplayMc(displayMc))) {
				return ActionResult.ok("Özel banka mevduatı güncellendi.");
			}
		} catch (Exception e) {
			McEconomyMod.LOGGER.error("OP ozel banka", e);
			return ActionResult.fail("Mevduat güncellenemedi.");
		}
		return ActionResult.fail("Geçersiz banka.");
	}

	public static ActionResult centralBankUpdate(JsonObject body) {
		var cb = McEconomyMod.getEconomyManager().centralBank();
		if (cb == null) {
			return ActionResult.fail("Merkez Bankası hazır değil.");
		}
		try {
			if (body.has("baseRate")) {
				cb.setBaseRate(body.get("baseRate").getAsDouble());
			}
			if (body.has("inflationRate")) {
				cb.setInflationRate(body.get("inflationRate").getAsDouble());
			}
			if (body.has("economyIndex")) {
				cb.setEconomyIndex(body.get("economyIndex").getAsDouble());
			}
			if (body.has("goldFactor")) {
				double factor = body.get("goldFactor").getAsDouble();
				cb.setGoldFactor(factor);
				GoldStandard.setGoldFactor(factor);
			}
			if (body.has("moneySupply")) {
				cb.updateMoneySupply(body.get("moneySupply").getAsLong());
			}
			if (body.has("municipalBudgetMc")) {
				cb.setMunicipalBudgetMg(mgForDisplayMc(body.get("municipalBudgetMc").getAsLong()));
			} else if (body.has("municipalBudgetMg")) {
				cb.setMunicipalBudgetMg(body.get("municipalBudgetMg").getAsLong());
			}
			cb.save();
			return ActionResult.ok("Merkez Bankası parametreleri güncellendi.");
		} catch (Exception e) {
			McEconomyMod.LOGGER.error("OP MB guncelleme", e);
			return ActionResult.fail("MB güncellenemedi.");
		}
	}

	public static ActionResult companyCreate(String name, String ownerName, String ticker, long treasuryMc,
			boolean listed) {
		UUID owner = BalanceCommand.findPlayerUuid(ownerName);
		if (owner == null) {
			return ActionResult.fail("Sahip oyuncu bulunamadı.");
		}
		try {
			EconomyManager manager = McEconomyMod.getEconomyManager();
			if (listed && ticker != null && !ticker.isBlank()) {
				if (manager.companyManager().createPublicListedCompany(name, ticker, owner, mgForDisplayMc(treasuryMc))) {
					return ActionResult.ok("Listeli şirket oluşturuldu: " + name);
				}
			} else if (manager.companyManager().createCompany(name, owner)) {
				if (treasuryMc > 0) {
					var company = manager.companyManager().find(name).orElse(null);
					if (company != null) {
						company.setTreasury(mgForDisplayMc(treasuryMc));
						manager.companyManager().saveCompany(company);
					}
				}
				return ActionResult.ok("Şirket oluşturuldu: " + name);
			}
		} catch (Exception e) {
			McEconomyMod.LOGGER.error("OP sirket olustur", e);
			return ActionResult.fail("Şirket oluşturulamadı.");
		}
		return ActionResult.fail("Şirket oluşturulamadı (isim veya ticker çakışması).");
	}

	public static ActionResult companyUpdate(String name, Long treasuryMc, String ticker, Boolean listed) {
		try {
			Long treasuryMg = treasuryMc != null ? mgForDisplayMc(treasuryMc) : null;
			if (McEconomyMod.getEconomyManager().companyManager()
					.adminUpdateCompany(name, treasuryMg, ticker, listed)) {
				return ActionResult.ok("Şirket güncellendi: " + name);
			}
		} catch (Exception e) {
			McEconomyMod.LOGGER.error("OP sirket guncelle", e);
			return ActionResult.fail("Şirket güncellenemedi.");
		}
		return ActionResult.fail("Şirket bulunamadı.");
	}

	public static ActionResult companyDelist(String name) {
		try {
			if (McEconomyMod.getEconomyManager().companyManager().adminDelist(name)) {
				return ActionResult.ok("Şirket borsadan çıkarıldı: " + name);
			}
		} catch (Exception e) {
			McEconomyMod.LOGGER.error("OP sirket delist", e);
			return ActionResult.fail("Delist başarısız.");
		}
		return ActionResult.fail("Şirket listede değil veya bulunamadı.");
	}

	public static ActionResult tokenCreate(String symbol, String displayName, int supply, long priceMc) {
		try {
			if (McEconomyMod.getEconomyManager().exchangeService()
					.createPublicToken(symbol, displayName, mgForDisplayMc(priceMc), supply)) {
				return ActionResult.ok("Coin oluşturuldu: " + symbol.toUpperCase());
			}
		} catch (Exception e) {
			McEconomyMod.LOGGER.error("OP coin olustur", e);
			return ActionResult.fail("Coin oluşturulamadı.");
		}
		return ActionResult.fail("Coin oluşturulamadı (sembol veya parametre).");
	}

	public static ActionResult tokenUpdate(String symbol, Long priceMc, Integer circulating) {
		try {
			Long priceMg = priceMc != null ? mgForDisplayMc(priceMc) : null;
			if (McEconomyMod.getEconomyManager().exchangeService()
					.adminUpdateToken(symbol, priceMg, circulating)) {
				return ActionResult.ok("Coin güncellendi: " + symbol.toUpperCase());
			}
		} catch (Exception e) {
			McEconomyMod.LOGGER.error("OP coin guncelle", e);
			return ActionResult.fail("Coin güncellenemedi.");
		}
		return ActionResult.fail("Coin bulunamadı.");
	}

	public static ActionResult configRead() {
		try {
			JsonObject data = new JsonObject();
			data.addProperty("path", EconomyConfig.configPathDisplay());
			data.addProperty("json", EconomyConfig.readRawJson());
			return ActionResult.ok("Config yüklendi.", data);
		} catch (Exception e) {
			McEconomyMod.LOGGER.error("OP config okuma", e);
			return ActionResult.fail("Config okunamadı.");
		}
	}

	public static ActionResult configSave(String json) {
		if (json == null || json.isBlank()) {
			return ActionResult.fail("Boş config kaydedilemez.");
		}
		if (!EconomyConfig.applyRawJson(json)) {
			return ActionResult.fail("Geçersiz JSON — sözdizimini kontrol edin.");
		}
		return ActionResult.ok("Config kaydedildi: " + EconomyConfig.configPathDisplay()
				+ " (webPort / webBindAddress için sunucuyu yeniden başlatın).");
	}

	public static ActionResult tokenDelete(String symbol) {
		try {
			if (McEconomyMod.getEconomyManager().exchangeService().adminDeleteToken(symbol)) {
				return ActionResult.ok("Coin silindi: " + symbol.toUpperCase());
			}
		} catch (Exception e) {
			McEconomyMod.LOGGER.error("OP coin sil", e);
			return ActionResult.fail("Coin silinemedi.");
		}
		return ActionResult.fail("Coin silinemedi (holdings var veya bulunamadı).");
	}
}
