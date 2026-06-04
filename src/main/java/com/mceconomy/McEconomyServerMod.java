package com.mceconomy;

import com.mceconomy.command.BulletinCommand;
import com.mceconomy.command.AdminCommand;
import com.mceconomy.command.AppealCommand;
import com.mceconomy.command.BalanceCommand;
import com.mceconomy.command.BankCommand;
import com.mceconomy.command.CompanyCommand;
import com.mceconomy.command.DashboardCommand;
import com.mceconomy.command.ExchangeCommand;
import com.mceconomy.command.FiatCommand;
import com.mceconomy.command.HeistCommand;
import com.mceconomy.command.InsuranceCommand;
import com.mceconomy.command.JusticeCommand;
import com.mceconomy.command.MayorCommand;
import com.mceconomy.command.PropertyCommand;
import com.mceconomy.command.VehicleCommand;
import com.mceconomy.network.VehicleInputPayload;
import com.mceconomy.network.VehicleStatePayload;
import com.mceconomy.command.HelpCommand;
import com.mceconomy.command.JobCommand;
import com.mceconomy.command.LoanCommand;
import com.mceconomy.command.MarketCommand;
import com.mceconomy.command.MasakCommand;
import com.mceconomy.command.MbOpCommand;
import com.mceconomy.command.PayCommand;
import com.mceconomy.command.PrivateBankCommand;
import com.mceconomy.command.VaultCommand;
import com.mceconomy.command.TradeCommand;
import com.mceconomy.command.GuildCommand;
import com.mceconomy.command.WorkCommand;
import com.mceconomy.company.CompanyVault;
import com.mceconomy.vault.PlayerVault;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.EconomyManager;
import com.mceconomy.gui.BankGuiManager;
import com.mceconomy.gui.ExchangeGuiManager;
import com.mceconomy.gui.IllegalGuiManager;
import com.mceconomy.network.EconomyHudPayload;
import com.mceconomy.network.EconomyHudSync;
import com.mceconomy.tick.EconomyTickScheduler;
import com.mceconomy.world.CentralBankPlacer;
import com.mceconomy.world.JobSeekerNpcSpawner;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

public class McEconomyServerMod implements DedicatedServerModInitializer {
	private static EconomyTickScheduler tickScheduler;

	@Override
	public void onInitializeServer() {
		EconomyConfig.load();
		PayloadTypeRegistry.clientboundPlay().register(EconomyHudPayload.TYPE, EconomyHudPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(VehicleStatePayload.TYPE, VehicleStatePayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(VehicleInputPayload.TYPE, VehicleInputPayload.STREAM_CODEC);
		tickScheduler = new EconomyTickScheduler();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			EconomyManager manager = new EconomyManager();
			McEconomyMod.bindEconomyManager(manager);
			manager.initialize(server);
			CentralBankPlacer.setupIfNeeded(server);
			if (manager.bankSecurityService() != null) {
				int removed = manager.bankSecurityService().purgeExcessGuards();
				if (removed > 0) {
					McEconomyMod.LOGGER.warn("Sunucu acilisi: {} fazla muhafiz temizlendi", removed);
				}
			}
			if (manager.goldReserveService() != null) {
				manager.goldReserveService().refresh(server);
			}
			McEconomyMod.LOGGER.info("MC Economy sunucu modu yuklendi.");
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			EconomyManager manager = McEconomyMod.getEconomyManager();
			if (manager != null) {
				manager.shutdown();
			}
			McEconomyMod.bindEconomyManager(null);
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			EconomyManager manager = McEconomyMod.getEconomyManager();
			if (manager != null && manager.isLoaded()) {
				manager.ensurePlayer(handler.getPlayer().getUUID(), handler.getPlayer().getName().getString());
				if (manager.prisonService() != null) {
					manager.prisonService().onPlayerJoin(handler.getPlayer());
				}
				if (manager.questManager() != null && manager.questManager().getQuest(handler.getPlayer().getUUID()) != null) {
					var profile = manager.profiles().get(handler.getPlayer().getUUID());
					if (profile != null && profile.jobType() != null
							&& !com.mceconomy.job.JobKitService.hasLoanItems(handler.getPlayer())) {
						com.mceconomy.job.JobKitService.giveKit(handler.getPlayer(), profile.jobType());
					}
				}
				EconomyHudSync.syncPlayer(handler.getPlayer());
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			EconomyManager manager = McEconomyMod.getEconomyManager();
			if (manager != null && manager.isLoaded()) {
				tickScheduler.onServerTick(manager);
				manager.onHeistTick();
				manager.onPrisonTick();
			}
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			BalanceCommand.register(dispatcher);
			PayCommand.register(dispatcher);
			BankCommand.register(dispatcher);
			MarketCommand.register(dispatcher);
			LoanCommand.register(dispatcher);
			AdminCommand.register(dispatcher);
			CompanyCommand.register(dispatcher);
			JobCommand.register(dispatcher);
			WorkCommand.register(dispatcher);
			TradeCommand.register(dispatcher);
			GuildCommand.register(dispatcher);
			MasakCommand.register(dispatcher);
			ExchangeCommand.register(dispatcher);
			PrivateBankCommand.register(dispatcher);
			AppealCommand.register(dispatcher);
			HelpCommand.register(dispatcher);
			DashboardCommand.register(dispatcher);
			MbOpCommand.register(dispatcher);
			VaultCommand.register(dispatcher);
			HeistCommand.register(dispatcher);
			BulletinCommand.register(dispatcher);
			FiatCommand.register(dispatcher);
			JusticeCommand.register(dispatcher);
			InsuranceCommand.register(dispatcher);
			MayorCommand.register(dispatcher);
			PropertyCommand.register(dispatcher);
			VehicleCommand.register(dispatcher);
		});

		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
				VehicleInputPayload.TYPE, (payload, context) -> {
					var manager = McEconomyMod.getEconomyManager();
					if (manager != null && manager.vehicleService() != null) {
						manager.vehicleService().setInput(context.player().getUUID(), payload);
					}
				});

		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer)) {
				return true;
			}
			EconomyManager manager = McEconomyMod.getEconomyManager();
			if (manager == null || manager.vaultService() == null) {
				return true;
			}
			PlayerVault vault = manager.vaultService().vaultRegionAt(pos.getX(), pos.getY(), pos.getZ());
			if (vault != null) {
				player.sendSystemMessage(Component.literal("§c[Kasa] §fKasa bolgesinde blok kirilamaz."));
				return false;
			}
			if (manager.companyVaultService() != null) {
				CompanyVault companyVault = manager.companyVaultService().vaultRegionAt(pos.getX(), pos.getY(), pos.getZ());
				if (companyVault != null) {
					player.sendSystemMessage(Component.literal(
							"§c[Sirket Sandigi] §fCelik koruma — blok kirilamaz."));
					return false;
				}
			}
			if (CentralBankPlacer.isProtectedBlock(pos.getX(), pos.getY(), pos.getZ())) {
				player.sendSystemMessage(Component.literal(
						"§c[Merkez Bankasi] §fRezerv ve celik kasa korunuyor — blok kirilamaz."));
				return false;
			}
			if (manager.prisonService() != null && manager.prisonService().containsBlock(pos.getX(), pos.getY(), pos.getZ())) {
				player.sendSystemMessage(Component.literal("§c[Hapishane] §fHucre duvarlari kirilamaz."));
				return false;
			}
			if (CentralBankPlacer.isDepotChest(pos)) {
				return false;
			}
			if (manager.propertyService() != null
					&& manager.propertyService().isProtectedBlock(pos.getX(), pos.getY(), pos.getZ(),
					(net.minecraft.server.level.ServerLevel) world)) {
				player.sendSystemMessage(Component.literal("§c[Ev] §fYapi korunuyor."));
				return false;
			}
			if (manager.companyBuildingService() != null
					&& manager.companyBuildingService().isProtectedBlock(pos.getX(), pos.getY(), pos.getZ())) {
				player.sendSystemMessage(Component.literal("§c[Sirket] §fHQ korunuyor."));
				return false;
			}
			return true;
		});

		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}
			EconomyManager manager = McEconomyMod.getEconomyManager();
			if (manager == null || manager.vaultService() == null) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hitResult.getBlockPos();
			if (CentralBankPlacer.isDepotChest(pos) && manager.bankSecurityService() != null
					&& !manager.bankSecurityService().canOpenDepot(serverPlayer)) {
				serverPlayer.sendSystemMessage(Component.literal(
						"§c[Guvenlik] §fDepo kilitli — muhafizlar nobette. §5Gece §fsandiklar acilir."));
				return InteractionResult.FAIL;
			}
			PlayerVault vault = manager.vaultService().vaultRegionAt(pos.getX(), pos.getY(), pos.getZ());
			if (vault != null && !vault.ownerUuid().equals(serverPlayer.getUUID())) {
				serverPlayer.sendSystemMessage(Component.literal(
						"§c[Kasa] §fBu kilitli kasa size ait degil."));
				return InteractionResult.FAIL;
			}
			if (manager.companyVaultService() != null) {
				CompanyVault companyVault = manager.companyVaultService().vaultRegionAt(pos.getX(), pos.getY(), pos.getZ());
				if (companyVault != null && !companyVault.ownerUuid().equals(serverPlayer.getUUID())) {
					serverPlayer.sendSystemMessage(Component.literal(
							"§c[Sirket Sandigi] §fBu sandik size ait bir sirkete ait."));
					return InteractionResult.FAIL;
				}
			}
			return InteractionResult.PASS;
		});

		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}
			if (entity.entityTags().contains(CentralBankPlacer.NPC_TAG)) {
				BankGuiManager.openMainMenu(serverPlayer);
				return InteractionResult.SUCCESS;
			}
			if (entity.entityTags().contains(CentralBankPlacer.MASAK_NPC_TAG)) {
				serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
						"§c[MASAK] §fFinansal suçları önlemek için işlemleriniz izlenmektedir."));
				BankGuiManager.openMainMenu(serverPlayer);
				return InteractionResult.SUCCESS;
			}
			if (entity.entityTags().contains(CentralBankPlacer.EXCHANGE_NPC_TAG)) {
				ExchangeGuiManager.openHub(serverPlayer);
				return InteractionResult.SUCCESS;
			}
			if (entity.entityTags().contains(JobSeekerNpcSpawner.JOB_SEEKER_TAG)) {
				serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
						"§e[Is Arayan] §fBasvurular icin §e/sirket basvurular §7komutunu kullanin."));
				return InteractionResult.SUCCESS;
			}
			if (entity.entityTags().contains(CentralBankPlacer.BLACK_MARKET_NPC_TAG)) {
				IllegalGuiManager.openHub(serverPlayer);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}
}
