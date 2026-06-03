package com.mceconomy.company;

import com.mceconomy.McEconomyMod;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.market.Commodity;
import com.mceconomy.market.CommodityProcessing;
import com.mceconomy.market.MarketService;
import com.mceconomy.job.JobType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.network.chat.Component;

import java.sql.SQLException;
import java.util.UUID;

/** Calisan uretimini erit, pisir, pazara sat veya sirket sandigina koy. */
public final class CompanyProductPipeline {
	private final MarketService marketService;
	private final CompanyVaultService companyVaultService;
	private final CompanyManager companyManager;

	public CompanyProductPipeline(MarketService marketService, CompanyVaultService companyVaultService,
			CompanyManager companyManager) {
		this.marketService = marketService;
		this.companyVaultService = companyVaultService;
		this.companyManager = companyManager;
	}

	public void processDelivery(MinecraftServer server, Company company, String workerName, JobType role,
			Commodity produced, int quantity) throws SQLException {
		processDelivery(server, company, workerName, role, produced, quantity, null);
	}

	public void processDelivery(MinecraftServer server, Company company, String workerName, JobType role,
			Commodity produced, int quantity, UUID workerUuid) throws SQLException {
		if (produced == null || quantity <= 0) {
			return;
		}

		if (CommodityProcessing.isCookable(produced)) {
			Item cooked = CommodityProcessing.cookedItem(produced).orElseThrow();
			int stored = companyVaultService.depositItem(company, cooked, quantity);
			sellOverflow(server, company, workerName, role, cooked, quantity - stored, workerUuid);
			notify(server, company, workerName, role,
					stored + "x pisirilmis " + produced.displayName() + " sirket sandigina kondu");
			return;
		}

		Commodity marketCommodity = CommodityProcessing.forMarket(produced);
		boolean ore = CommodityProcessing.isOre(produced) || CommodityProcessing.isOre(marketCommodity);

		if (ore) {
			int reserve = oreReserveAmount(quantity);
			int toSell = quantity - reserve;
			Item vaultItem = marketCommodity.item();

			if (reserve > 0) {
				int stored = companyVaultService.depositItem(company, vaultItem, reserve);
				sellOverflow(server, company, workerName, role, vaultItem, reserve - stored, workerUuid);
			}
			if (toSell > 0 && marketCommodity.sellable()) {
				long revenue = marketService.systemSellForCompany(company, marketCommodity, toSell, workerUuid);
				companyManager.saveCompany(company);
				String smeltNote = CommodityProcessing.isSmeltable(produced) ? " (eritildi)" : "";
				notify(server, company, workerName, role,
						toSell + "x " + marketCommodity.displayName() + smeltNote
								+ " pazara satildi +" + GoldStandard.formatMilligrams(revenue)
								+ (reserve > 0 ? " | " + reserve + "x sandik rezervi" : ""));
			} else if (reserve > 0) {
				notify(server, company, workerName, role, reserve + "x maden sirket sandigina kondu");
			}
			return;
		}

		if (marketCommodity.sellable()) {
			long revenue = marketService.systemSellForCompany(company, marketCommodity, quantity, workerUuid);
			companyManager.saveCompany(company);
			notify(server, company, workerName, role,
					quantity + "x " + marketCommodity.displayName() + " pazara satildi +"
							+ GoldStandard.formatMilligrams(revenue));
		}
	}

	private int oreReserveAmount(int quantity) {
		double percent = EconomyConfig.companyOreReservePercent();
		int reserve = (int) Math.round(quantity * percent);
		if (reserve < 1 && quantity > 0 && percent > 0) {
			reserve = 1;
		}
		return Math.min(reserve, quantity);
	}

	private void sellOverflow(MinecraftServer server, Company company, String workerName, JobType role,
			Item item, int overflow, UUID workerUuid) throws SQLException {
		if (overflow <= 0) {
			return;
		}
		Commodity commodity = Commodity.fromItem(item);
		if (commodity == null || !commodity.sellable()) {
			return;
		}
		long revenue = marketService.systemSellForCompany(company, commodity, overflow, workerUuid);
		companyManager.saveCompany(company);
		notify(server, company, workerName, role,
				overflow + "x " + commodity.displayName() + " sandik dolu — otomatik satildi +"
						+ GoldStandard.formatMilligrams(revenue));
	}

	public void liquidateFullVaults(MinecraftServer server) throws SQLException {
		for (Company company : companyManager.allCompanies()) {
			companyVaultService.liquidateIfFull(company, (item, amount) -> {
				try {
					sellOverflow(server, company, "Sistem", JobType.MINER, item, amount, null);
				} catch (SQLException e) {
					McEconomyMod.LOGGER.error("Sirket sandik tasfiyesi basarisiz", e);
				}
			});
		}
	}

	private void notify(MinecraftServer server, Company company, String workerName, JobType role, String detail) {
		if (server == null) {
			return;
		}
		ServerPlayer owner = server.getPlayerList().getPlayer(company.ownerUuid());
		if (owner == null) {
			return;
		}
		owner.sendSystemMessage(Component.literal(
				"§a[Sirket] §f" + workerName + " §7(" + role.displayName() + "): §e" + detail
						+ " §7| §e/sirket sandik " + company.name()));
	}
}
