package com.mceconomy.trade;

import com.mceconomy.McEconomyMod;
import com.mceconomy.command.BalanceCommand;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.CurrencyService;
import com.mceconomy.economy.EconomyEventCategory;
import com.mceconomy.economy.EconomyEventDirection;
import com.mceconomy.economy.EconomyEventService;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.economy.TransactionType;
import com.mceconomy.persistence.repo.TradeRepository;
import com.mceconomy.tax.CentralBank;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PlayerTradeService {
	private final TradeRepository repository;
	private final CurrencyService currencyService;
	private final CentralBank centralBank;
	private EconomyEventService economyEventService;

	public PlayerTradeService(TradeRepository repository, CurrencyService currencyService, CentralBank centralBank) {
		this.repository = repository;
		this.currencyService = currencyService;
		this.centralBank = centralBank;
	}

	public void bindEconomyEventService(EconomyEventService economyEventService) {
		this.economyEventService = economyEventService;
	}

	public boolean invite(ServerPlayer initiator, String partnerName) {
		UUID partnerUuid = BalanceCommand.findPlayerUuid(partnerName);
		if (partnerUuid == null || partnerUuid.equals(initiator.getUUID())) {
			initiator.sendSystemMessage(Component.literal("?cGecersiz oyuncu."));
			return false;
		}
		try {
			if (repository.findActiveForPlayer(initiator.getUUID()).isPresent()
					|| repository.findActiveForPlayer(partnerUuid).isPresent()) {
				initiator.sendSystemMessage(Component.literal("?cAktif takas var."));
				return false;
			}
			PlayerTrade trade = PlayerTrade.open(initiator.getUUID(), initiator.getName().getString(),
					partnerUuid, partnerName);
			repository.saveTrade(trade);
			initiator.sendSystemMessage(Component.literal(
					"?aTakas daveti gonderildi: ?f" + partnerName + " ?7(#" + trade.id() + ")"));
			ServerPlayer partner = server().getPlayerList().getPlayer(partnerUuid);
			if (partner != null) {
				partner.sendSystemMessage(Component.literal(
						"?e[Takas] ?f" + initiator.getName().getString()
								+ " sizinle takas baslatmak istiyor. ?7/takas kabul"));
			}
			return true;
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Takas daveti basarisiz", e);
			return false;
		}
	}

	public boolean accept(ServerPlayer partner) {
		try {
			Optional<PlayerTrade> opt = repository.findActiveForPlayer(partner.getUUID());
			if (opt.isEmpty() || !opt.get().partnerUuid().equals(partner.getUUID())) {
				partner.sendSystemMessage(Component.literal("?cBekleyen takas daveti yok."));
				return false;
			}
			partner.sendSystemMessage(Component.literal(
					"?aTakas #" + opt.get().id() + " aktif. ?7/takas para|el|hazir|iptal"));
			ServerPlayer initiator = server().getPlayerList().getPlayer(opt.get().initiatorUuid());
			if (initiator != null) {
				initiator.sendSystemMessage(Component.literal(
						"?a" + partner.getName().getString() + " takasi kabul etti."));
			}
			return true;
		} catch (SQLException e) {
			return false;
		}
	}

	public boolean addGold(ServerPlayer player, long amountMg) {
		if (amountMg <= 0) {
			return false;
		}
		try {
			PlayerTrade trade = requireActive(player);
			if (trade == null) {
				return false;
			}
			if (!currencyService.withdraw(player.getUUID(), amountMg, TransactionType.TRANSFER)) {
				player.sendSystemMessage(Component.literal("?cYetersiz bakiye."));
				return false;
			}
			if (trade.isInitiator(player.getUUID())) {
				trade.setInitiatorGoldMg(trade.initiatorGoldMg() + amountMg);
				trade.setInitiatorReady(false);
			} else {
				trade.setPartnerGoldMg(trade.partnerGoldMg() + amountMg);
				trade.setPartnerReady(false);
			}
			repository.saveTrade(trade);
			player.sendSystemMessage(Component.literal(
					"?aTakasa " + GoldStandard.formatMilligrams(amountMg) + " eklendi."));
			return true;
		} catch (SQLException e) {
			return false;
		}
	}

	public boolean addHandItem(ServerPlayer player) {
		ItemStack hand = player.getMainHandItem();
		if (hand.isEmpty() || hand.is(Items.AIR)) {
			player.sendSystemMessage(Component.literal("?cElinde esya yok."));
			return false;
		}
		try {
			PlayerTrade trade = requireActive(player);
			if (trade == null) {
				return false;
			}
			Item item = hand.getItem();
			int count = hand.getCount();
			if (!TradeItemCodec.removeItem(player, item, count)) {
				return false;
			}
			List<TradeItemCodec.StackEntry> stacks = trade.isInitiator(player.getUUID())
					? TradeItemCodec.parse(trade.initiatorItemsJson())
					: TradeItemCodec.parse(trade.partnerItemsJson());
			stacks = new ArrayList<>(stacks);
			String itemId = TradeItemCodec.normalizeItemId(BuiltInRegistries.ITEM.getKey(item).toString());
			mergeStack(stacks, itemId, count);
			if (trade.isInitiator(player.getUUID())) {
				trade.setInitiatorItemsJson(TradeItemCodec.encode(stacks));
				trade.setInitiatorReady(false);
			} else {
				trade.setPartnerItemsJson(TradeItemCodec.encode(stacks));
				trade.setPartnerReady(false);
			}
			repository.saveTrade(trade);
			player.sendSystemMessage(Component.literal("?aTakasa esya eklendi: ?f" + count + "x " + itemId));
			return true;
		} catch (SQLException e) {
			return false;
		}
	}

	public boolean setReady(ServerPlayer player) {
		try {
			PlayerTrade trade = requireActive(player);
			if (trade == null) {
				return false;
			}
			if (trade.isInitiator(player.getUUID())) {
				trade.setInitiatorReady(true);
			} else {
				trade.setPartnerReady(true);
			}
			repository.saveTrade(trade);
			player.sendSystemMessage(Component.literal("?eHazir isaretlendi. Karsi taraf da hazir olunca takas tamamlanir."));
			if (trade.initiatorReady() && trade.partnerReady()) {
				return complete(trade, server());
			}
			String readyMsg = player.getName().getString() + " hazir - siz de /takas hazir";
			notifyPartner(server(), trade, player.getUUID(), readyMsg);
			return true;
		} catch (SQLException e) {
			return false;
		}
	}

	public boolean cancel(ServerPlayer player) {
		try {
			PlayerTrade trade = requireActive(player);
			if (trade == null) {
				return false;
			}
			refundEscrow(trade, server());
			trade.setStatus(TradeStatus.CANCELLED);
			repository.saveTrade(trade);
			player.sendSystemMessage(Component.literal("?eTakas iptal edildi, esyalar iade edildi."));
			notifyPartner(server(), trade, player.getUUID(), "Takas iptal edildi.");
			return true;
		} catch (SQLException e) {
			return false;
		}
	}

	public boolean dispute(ServerPlayer reporter, long tradeId, String reason) {
		try {
			Optional<PlayerTrade> opt = repository.findTrade(tradeId);
			if (opt.isEmpty() || opt.get().status() != TradeStatus.COMPLETED) {
				reporter.sendSystemMessage(Component.literal("?cGecersiz takas."));
				return false;
			}
			PlayerTrade trade = opt.get();
			if (!trade.involves(reporter.getUUID())) {
				return false;
			}
			long windowMs = EconomyConfig.tradeDisputeWindowHours() * 3600_000L;
			if (System.currentTimeMillis() - trade.completedAt() > windowMs) {
				reporter.sendSystemMessage(Component.literal("?cSikayet suresi doldu."));
				return false;
			}
			UUID target = trade.isInitiator(reporter.getUUID()) ? trade.partnerUuid() : trade.initiatorUuid();
			String targetName = trade.isInitiator(reporter.getUUID()) ? trade.partnerName() : trade.initiatorName();
			TradeDispute dispute = TradeDispute.open(tradeId, reporter.getUUID(), reporter.getName().getString(),
					target, targetName, reason);
			repository.saveDispute(dispute);
			trade.setStatus(TradeStatus.DISPUTED);
			repository.saveTrade(trade);
			reporter.sendSystemMessage(Component.literal("?aTakas sikayeti #" + dispute.id() + " acildi. OP inceleyecek."));
			String disputeMsg = "Takas sikayeti #" + dispute.id() + ": " + reporter.getName().getString()
					+ " -> " + targetName;
			notifyOps(server(), disputeMsg);
			return true;
		} catch (SQLException e) {
			return false;
		}
	}

	public List<PlayerTrade> history(UUID uuid) {
		try {
			return repository.loadHistoryForPlayer(uuid, 20);
		} catch (SQLException e) {
			return List.of();
		}
	}

	public Optional<PlayerTrade> findTrade(long id) {
		try {
			return repository.findTrade(id);
		} catch (SQLException e) {
			return Optional.empty();
		}
	}

	public List<TradeDispute> openDisputes() {
		try {
			return repository.loadOpenDisputes();
		} catch (SQLException e) {
			return List.of();
		}
	}

	public boolean resolveDispute(String adminName, long disputeId, boolean refundReporter, String note) {
		try {
			Optional<TradeDispute> opt = repository.findDispute(disputeId);
			if (opt.isEmpty() || opt.get().status() != TradeDisputeStatus.OPEN) {
				return false;
			}
			TradeDispute dispute = opt.get();
			Optional<PlayerTrade> tradeOpt = repository.findTrade(dispute.tradeId());
			if (tradeOpt.isEmpty()) {
				return false;
			}
			PlayerTrade trade = tradeOpt.get();
			if (refundReporter) {
				long refund = estimateReporterLoss(trade, dispute.reporterUuid());
				if (refund > 0) {
					if (!currencyService.withdraw(dispute.targetUuid(), refund, TransactionType.TRANSFER)) {
						centralBank.spendMunicipalBudget(refund);
					}
					currencyService.deposit(dispute.reporterUuid(), refund, TransactionType.TRANSFER);
				}
				dispute.setStatus(TradeDisputeStatus.REFUNDED);
			} else {
				dispute.setStatus(TradeDisputeStatus.DISMISSED);
			}
			dispute.setAdminNote(note);
			dispute.setResolvedBy(adminName);
			dispute.setResolvedAt(System.currentTimeMillis());
			repository.saveDispute(dispute);
			trade.setStatus(TradeStatus.COMPLETED);
			repository.saveTrade(trade);
			return true;
		} catch (SQLException e) {
			return false;
		}
	}

	private long estimateReporterLoss(PlayerTrade trade, UUID reporter) {
		if (trade.isInitiator(reporter)) {
			return trade.initiatorGoldMg() / 2;
		}
		return trade.partnerGoldMg() / 2;
	}

	private boolean complete(PlayerTrade trade, MinecraftServer server) throws SQLException {
		ServerPlayer initiator = server.getPlayerList().getPlayer(trade.initiatorUuid());
		ServerPlayer partner = server.getPlayerList().getPlayer(trade.partnerUuid());
		if (initiator == null || partner == null) {
			if (initiator != null) {
				initiator.sendSystemMessage(Component.literal("?cTakas icin iki taraf da cevrimici olmali."));
			}
			if (partner != null) {
				partner.sendSystemMessage(Component.literal("?cTakas icin iki taraf da cevrimici olmali."));
			}
			trade.setInitiatorReady(false);
			trade.setPartnerReady(false);
			repository.saveTrade(trade);
			return false;
		}
		long initiatorGold = trade.initiatorGoldMg();
		long partnerGold = trade.partnerGoldMg();
		currencyService.deposit(partner.getUUID(), initiatorGold, TransactionType.TRANSFER);
		currencyService.deposit(initiator.getUUID(), partnerGold, TransactionType.TRANSFER);
		TradeItemCodec.giveEncoded(partner, trade.initiatorItemsJson());
		TradeItemCodec.giveEncoded(initiator, trade.partnerItemsJson());
		logTradeComplete(trade, initiatorGold, partnerGold);
		trade.setInitiatorGoldMg(0);
		trade.setPartnerGoldMg(0);
		trade.setInitiatorItemsJson("[]");
		trade.setPartnerItemsJson("[]");
		trade.setStatus(TradeStatus.COMPLETED);
		trade.setCompletedAt(System.currentTimeMillis());
		repository.saveTrade(trade);
		initiator.sendSystemMessage(Component.literal("?a?lTakas tamamlandi! ?7Dolandirildiginizi dusunurseniz /takas sikayet <id>"));
		partner.sendSystemMessage(Component.literal("?a?lTakas tamamlandi! ?7Dolandirildiginizi dusunurseniz /takas sikayet <id>"));
		return true;
	}

	private void logTradeComplete(PlayerTrade trade, long initiatorGold, long partnerGold) {
		if (economyEventService == null) {
			return;
		}
		if (partnerGold > 0) {
			economyEventService.recordPersonal(trade.initiatorUuid(), EconomyEventCategory.TRADE,
					EconomyEventDirection.IN, partnerGold, trade.partnerUuid(),
					null, 0, "TRADE_COMPLETE",
					trade.partnerName() + " ile takas - alinan: "
							+ GoldStandard.formatMilligrams(partnerGold));
		}
		if (initiatorGold > 0) {
			economyEventService.recordPersonal(trade.partnerUuid(), EconomyEventCategory.TRADE,
					EconomyEventDirection.IN, initiatorGold, trade.initiatorUuid(),
					null, 0, "TRADE_COMPLETE",
					trade.initiatorName() + " ile takas - alinan: "
							+ GoldStandard.formatMilligrams(initiatorGold));
		}
	}

	private void refundEscrow(PlayerTrade trade, MinecraftServer server) {
		ServerPlayer initiator = server.getPlayerList().getPlayer(trade.initiatorUuid());
		ServerPlayer partner = server.getPlayerList().getPlayer(trade.partnerUuid());
		if (initiator != null) {
			if (trade.initiatorGoldMg() > 0) {
				currencyService.deposit(initiator.getUUID(), trade.initiatorGoldMg(), TransactionType.TRANSFER);
			}
			TradeItemCodec.giveEncoded(initiator, trade.initiatorItemsJson());
		}
		if (partner != null) {
			if (trade.partnerGoldMg() > 0) {
				currencyService.deposit(partner.getUUID(), trade.partnerGoldMg(), TransactionType.TRANSFER);
			}
			TradeItemCodec.giveEncoded(partner, trade.partnerItemsJson());
		}
		trade.setInitiatorGoldMg(0);
		trade.setPartnerGoldMg(0);
		trade.setInitiatorItemsJson("[]");
		trade.setPartnerItemsJson("[]");
	}

	private PlayerTrade requireActive(ServerPlayer player) throws SQLException {
		Optional<PlayerTrade> opt = repository.findActiveForPlayer(player.getUUID());
		if (opt.isEmpty() || opt.get().status() != TradeStatus.PENDING) {
			player.sendSystemMessage(Component.literal("?cAktif takas yok."));
			return null;
		}
		return opt.get();
	}

	private void mergeStack(List<TradeItemCodec.StackEntry> stacks, String itemId, int count) {
		for (int i = 0; i < stacks.size(); i++) {
			if (stacks.get(i).itemId().equals(itemId)) {
				stacks.set(i, new TradeItemCodec.StackEntry(itemId, stacks.get(i).count() + count));
				return;
			}
		}
		stacks.add(new TradeItemCodec.StackEntry(itemId, count));
	}

	private void notifyPartner(MinecraftServer server, PlayerTrade trade, UUID from, String message) {
		UUID other = trade.isInitiator(from) ? trade.partnerUuid() : trade.initiatorUuid();
		ServerPlayer p = server.getPlayerList().getPlayer(other);
		if (p != null) {
			p.sendSystemMessage(Component.literal("?e[Takas] ?f" + message));
		}
	}

	private MinecraftServer server() {
		return McEconomyMod.getEconomyManager().server();
	}

	private void notifyOps(MinecraftServer server, String message) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (server.getPlayerList().isOp(player.nameAndId())) {
				player.sendSystemMessage(Component.literal("?c[OP Takas] ?f" + message));
			}
		}
	}
}
