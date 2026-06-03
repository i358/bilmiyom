package com.mceconomy.heist;

import com.mceconomy.McEconomyMod;
import com.mceconomy.facility.FacilityType;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.reserve.GoldReserveService;
import com.mceconomy.world.CentralBankPlacer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Merkez bankasi soygunu: fiziksel muhafiz/soyguncu, rezervden altin, izinsiz giris cezasi. */
public final class HeistService {
	private static final String[] GUARD_NAMES = {
			"Komiser Demir", "Muhafiz Kaya", "Muhafiz Yildiz", "Sef Arslan", "Komiser Polat", "Muhafiz Tekin"
	};
	private final MinecraftServer server;
	private final GoldReserveService goldReserve;

	private final List<UUID> guardEntities = new ArrayList<>();
	private final List<UUID> robberEntities = new ArrayList<>();
	private boolean active;
	private long endsAtMs;
	private int tickCounter;
	private String initiatorName;
	private UUID initiatorUuid;
	private int messageRound;

	public HeistService(MinecraftServer server, GoldReserveService goldReserve) {
		this.server = server;
		this.goldReserve = goldReserve;
	}

	public boolean isActive() {
		return active;
	}

	public void forceEnd() {
		if (active) {
			finish();
		}
	}

	public boolean start(String initiator) {
		ServerPlayer player = server.getPlayerList().getPlayerByName(initiator);
		return start(initiator, player != null ? player.getUUID() : null);
	}

	public boolean start(String initiator, UUID uuid) {
		if (active) {
			return false;
		}
		active = true;
		initiatorName = initiator;
		initiatorUuid = uuid;
		endsAtMs = System.currentTimeMillis() + EconomyConfig.heistDurationSeconds() * 1000L;
		tickCounter = 0;
		messageRound = 0;
		spawnGuards();
		spawnRobbers();
		var manager = McEconomyMod.getEconomyManager();
		if (manager != null && manager.bankSecurityService() != null) {
			manager.bankSecurityService().setHighAlert(true);
		}
		int goldBlocks = goldReserve.cachedGoldBlocks();
		broadcast("§4§l[ALARM] §c" + initiator + " Merkez Bankasi soygun protokolunu baslatti!");
		broadcast("§c[Soygun] §fCelik kasa kilitlendi. Rezervde §6" + goldBlocks
				+ " §faltin blogu (" + GoldStandard.formatMilligrams(goldReserve.backingMilligrams()) + ") korunuyor.");
		var sec = manager != null ? manager.bankSecurityService() : null;
		if (sec != null && sec.guardsSleeping()) {
			broadcast("§7[Soygun] §fSure: " + EconomyConfig.heistDurationSeconds()
					+ " sn — §dgece: muhafizlar uyuyor, otomatik ates yok; sonuc RP ile belli olur.");
		} else {
			broadcast("§7[Soygun] §fSure: " + EconomyConfig.heistDurationSeconds()
					+ " sn — banka icinde izinsiz girenler hasar alabilir.");
		}
		return true;
	}

	public void tick() {
		if (!active) {
			return;
		}
		tickCounter++;
		if (System.currentTimeMillis() >= endsAtMs) {
			finish();
			return;
		}
		if (tickCounter % EconomyConfig.heistMessageIntervalTicks() == 0) {
			broadcast(contextualRoleplayLine());
			messageRound++;
		}
	}

	private void finish() {
		ServerLevel level = server.overworld();
		int reserveGold = goldReserve.countGoldBlocks(level);
		boolean robbersWin = reserveGold > 0 && ThreadLocalRandom.current().nextDouble() < computeWinChance(reserveGold);
		if (robbersWin) {
			int lootBlocks = Math.min(reserveGold, Math.max(1, reserveGold / 4));
			int taken = goldReserve.withdrawGoldBlocks(level, lootBlocks);
			int ingots = taken * GoldReserveService.INGOTS_PER_GOLD_BLOCK;
			ServerPlayer initiator = initiatorUuid != null ? server.getPlayerList().getPlayer(initiatorUuid) : null;
			var manager = McEconomyMod.getEconomyManager();
			if (initiator != null && ingots > 0 && manager != null && manager.bankAssetSerialRegistry() != null) {
				int remaining = ingots;
				while (remaining > 0) {
					int chunk = Math.min(remaining, 64);
					ItemStack stack = new ItemStack(Items.GOLD_INGOT, chunk);
					String serial = manager.bankAssetSerialRegistry().assignSerial(stack, FacilityType.PHYSICAL_GOLD);
					manager.bankAssetSerialRegistry().registerWantedSerial(serial);
					initiator.getInventory().add(stack);
					remaining -= chunk;
				}
			}
			long stolenMg = GoldStandard.ingotsToMilligrams(ingots);
			if (initiatorUuid != null && manager != null && manager.bankRobberyJusticeService() != null) {
				manager.bankRobberyJusticeService().scheduleMorningInvestigation(initiatorUuid);
			}
			if (manager != null && manager.bulletinService() != null) {
				manager.bulletinService().publishRobbery(server, manager.centralBank(),
						manager.marketService().priceEngine(),
						"GECE MERKEZ BANKASI ALTIN REZERVINE SOYGUN DUzenlendi!",
						initiatorName + " ekibi celik kasayi acti. Rezervden " + taken
								+ " altin blogu (" + ingots + " kulce) cikarildi.",
						stolenMg);
			} else {
				broadcast("§4§l[SOYGUN BASARILI] §c" + initiatorName + " ekibi celik kasayi acti! §6"
						+ taken + " §faltin blogu rezervden cikarildi (" + ingots + " kulce).");
			}
		} else {
			broadcast("§2§l[SOYGUN PUSKURTULDU] §aMuhafizlar fiziksel olarak kasayi korudu! Rezervde §6"
					+ reserveGold + " §faltin blogu guvende.");
		}
		removeHeistEntities();
		clearHighAlert();
		active = false;
		initiatorName = null;
		initiatorUuid = null;
	}

	private void clearHighAlert() {
		var manager = McEconomyMod.getEconomyManager();
		if (manager != null && manager.bankSecurityService() != null) {
			manager.bankSecurityService().setHighAlert(false);
		}
	}

	private double computeWinChance(int reserveGold) {
		double base = 0.25 + Math.min(0.15, reserveGold * 0.01);
		return Math.min(0.55, base);
	}

	public void forceStop() {
		if (!active) {
			return;
		}
		broadcast("§e[Soygun] §fProtokol sonlandirildi.");
		removeHeistEntities();
		clearHighAlert();
		active = false;
		initiatorName = null;
		initiatorUuid = null;
	}

	private String contextualRoleplayLine() {
		int gold = goldReserve.cachedGoldBlocks();
		String robber = randomRobberName();
		String guard = GUARD_NAMES[ThreadLocalRandom.current().nextInt(GUARD_NAMES.length)];
		List<ServerPlayer> nearReserve = new ArrayList<>();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			BlockPos pp = p.blockPosition();
			if (CentralBankPlacer.isInsideBank(pp.getX(), pp.getY(), pp.getZ())) {
				nearReserve.add(p);
			}
		}
		if (!nearReserve.isEmpty()) {
			ServerPlayer intruder = nearReserve.get(ThreadLocalRandom.current().nextInt(nearReserve.size()));
			return "§c[Soygun] §f" + intruder.getName().getString()
					+ " rezerv koridorunda! Muhafiz " + guard + " mudahale etti!";
		}
		String[] lines = {
				"§c[Soygun] §f" + robber + " celik kapiyi termit ile eritmeye calisiyor!",
				"§c[Soygun] §f" + guard + " otomatik turege kilitlendi, mermiler firlatiyor!",
				"§c[Soygun] §fRezervdeki §6" + gold + " §faltin blogu icin catisma siddetlendi!",
				"§c[Soygun] §f" + robber + " gomlek zirhini delip iceri sizdi — " + guard + " yaralandi!",
				"§c[Soygun] §fSoyguncu ekibi kasa dairesine BFG ile saldiriyor!",
				"§c[Soygun] §f" + guard + " son muhafiz hatti: 'Rezerv dokunulmaz!'",
				"§c[Soygun] §f" + robber + " elektrik sistemini kesti, karanlikta yumruk yumruga!",
				"§c[Soygun] §fHelikopter sesleri — ancak muhafizlar kapidan girmiyor!",
				"§c[Soygun] §f" + guard + " bir soyguncunun bacaginden vurdu, agri cigi duyuldu!",
				"§c[Soygun] §fCelik duvar titredi ama §6" + gold + " §faltin blogu yerinde duruyor!"
		};
		return lines[ThreadLocalRandom.current().nextInt(lines.length)];
	}

	private String randomRobberName() {
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		if (!players.isEmpty() && ThreadLocalRandom.current().nextBoolean()) {
			return players.get(ThreadLocalRandom.current().nextInt(players.size())).getName().getString();
		}
		String[] aliases = {"Maskeli Soyguncu", "Kara Eldiven", "Profesyonel Hirsiz", "Hayalet Hirsiz", "Rezerv Avcisi"};
		return aliases[ThreadLocalRandom.current().nextInt(aliases.length)];
	}

	private void spawnGuards() {
		BlockPos reserve = CentralBankPlacer.reservePos();
		if (reserve == null) {
			return;
		}
		ServerLevel level = server.overworld();
		int count = EconomyConfig.heistGuardCount();
		var registries = level.registryAccess();
		for (int i = 0; i < count; i++) {
			double angle = (Math.PI * 2 / count) * i;
			double gx = reserve.getX() + 0.5 + Math.cos(angle) * 4;
			double gz = reserve.getZ() + 0.5 + Math.sin(angle) * 4;
			BlockPos pos = BlockPos.containing(gx, reserve.getY(), gz);
			Villager guard = EntityType.VILLAGER.create(level, null, pos, EntitySpawnReason.EVENT, false, false);
			if (guard == null) {
				continue;
			}
			guard.setPos(gx, reserve.getY(), gz);
			guard.setCustomName(Component.literal("§b§l" + GUARD_NAMES[i % GUARD_NAMES.length]));
			guard.setCustomNameVisible(true);
			guard.setPersistenceRequired();
			guard.setVillagerData(new VillagerData(
					registries.lookupOrThrow(Registries.VILLAGER_TYPE).getOrThrow(VillagerType.PLAINS),
					registries.lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(VillagerProfession.WEAPONSMITH),
					1));
			guard.addTag(CentralBankPlacer.HEIST_GUARD_TAG);
			level.addFreshEntity(guard);
			guardEntities.add(guard.getUUID());
		}
	}

	private void spawnRobbers() {
		BlockPos origin = CentralBankPlacer.bankOrigin();
		if (origin == null) {
			return;
		}
		ServerLevel level = server.overworld();
		int count = Math.max(2, EconomyConfig.heistGuardCount() - 1);
		for (int i = 0; i < count; i++) {
			double ox = origin.getX() + 4.5 + (i - 1) * 2;
			double oz = origin.getZ() - 2;
			BlockPos pos = BlockPos.containing(ox, origin.getY() + 1, oz);
			Zombie robber = EntityType.ZOMBIE.create(level, null, pos, EntitySpawnReason.EVENT, false, false);
			if (robber == null) {
				continue;
			}
			robber.setPos(ox, origin.getY() + 1, oz);
			robber.setCustomName(Component.literal("§4§lSoyguncu #" + (i + 1)));
			robber.setCustomNameVisible(true);
			robber.setPersistenceRequired();
			robber.setAggressive(true);
			robber.addTag(CentralBankPlacer.HEIST_ROBBER_TAG);
			level.addFreshEntity(robber);
			robberEntities.add(robber.getUUID());
		}
	}

	private void removeHeistEntities() {
		ServerLevel level = server.overworld();
		for (UUID id : guardEntities) {
			var entity = level.getEntity(id);
			if (entity != null) {
				entity.discard();
			}
		}
		guardEntities.clear();
		for (UUID id : robberEntities) {
			var entity = level.getEntity(id);
			if (entity != null) {
				entity.discard();
			}
		}
		robberEntities.clear();
	}

	private void broadcast(String message) {
		server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
	}
}
