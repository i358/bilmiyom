package com.mceconomy.security;

import com.mceconomy.McEconomyMod;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.facility.DepotSnapshot;
import com.mceconomy.facility.FacilityDepotService;
import com.mceconomy.facility.FacilityItemTags;
import com.mceconomy.facility.FacilityType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import com.mceconomy.justice.PrisonService;
import com.mceconomy.world.CentralBankPlacer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.core.registries.Registries;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Kalici muhafizlar, gece uykusu, izinsiz giris cezasi, sabah calinti aramasi.
 */
public final class BankSecurityService {
	private static final String[] GUARD_NAMES = {
			"Komiser Demir", "Muhafiz Kaya", "Muhafiz Yildiz", "Sef Arslan", "Komiser Polat", "Muhafiz Tekin"
	};
	private static final String[] WARNING_LINES = {
			"§4[Guvenlik] §e%s — §cEL CEK! Son uyari!",
			"§4[Guvenlik] §c%s kasa bolgesine yaklasti — muhafizlar nisan aldi!",
			"§4[Guvenlik] §c%s icin uyari atesi — geri cekilin!",
			"§4[Guvenlik] §cLazer taramasi %s uzerinde — izinsiz erisim!"
	};

	private final MinecraftServer server;
	private final FacilityDepotService depotService;
	private final List<UUID> guardIds = new ArrayList<>();
	private final Map<UUID, Integer> intrusionLevel = new HashMap<>();
	private final Map<UUID, Integer> intrusionCooldown = new HashMap<>();

	private boolean highAlert;
	private boolean guardsSleeping;
	private long guardsWakeAtMs;
	private final Map<FacilityType, Integer> sleepStartHashes = new EnumMap<>(FacilityType.class);
	private final Map<FacilityType, DepotSnapshot> sleepStartSnapshots = new EnumMap<>(FacilityType.class);
	private static final int GUARD_SYNC_RADIUS = 48;
	private static final int SPAWN_CHECK_INTERVAL_TICKS = 100;
	private static final int SPAWN_COOLDOWN_TICKS = 200;

	private int tickCounter;
	private int spawnCooldownTicks;
	private boolean wasDay = true;
	private boolean sleepMessageSent;

	public BankSecurityService(MinecraftServer server, FacilityDepotService depotService) {
		this.server = server;
		this.depotService = depotService;
	}

	/** Yalnizca resmi banka muhafizi etiketi — isim eslesmesi binlerce yanlis spawn'a yol aciyordu. */
	public static boolean isBankGuard(LivingEntity entity) {
		return entity instanceof Villager villager
				&& villager.entityTags().contains(CentralBankPlacer.BANK_GUARD_TAG);
	}

	private static boolean isEconomyNpc(Villager villager) {
		return villager.entityTags().contains(CentralBankPlacer.NPC_TAG)
				|| villager.entityTags().contains(CentralBankPlacer.MASAK_NPC_TAG)
				|| villager.entityTags().contains(CentralBankPlacer.EXCHANGE_NPC_TAG)
				|| villager.entityTags().contains(CentralBankPlacer.BLACK_MARKET_NPC_TAG);
	}

	private static boolean isLegacyGuardLike(Villager villager) {
		if (isEconomyNpc(villager)) {
			return false;
		}
		if (villager.entityTags().contains(CentralBankPlacer.HEIST_GUARD_TAG)
				|| villager.entityTags().contains(CentralBankPlacer.HEIST_ROBBER_TAG)) {
			return true;
		}
		Component name = villager.getCustomName();
		if (name == null) {
			return false;
		}
		String raw = name.getString().toLowerCase();
		return raw.contains("muhafiz") || raw.contains("komiser") || raw.contains("yedek muhafiz");
	}

	public void setHighAlert(boolean highAlert) {
		this.highAlert = highAlert;
	}

	public boolean guardsSleeping() {
		return guardsSleeping;
	}

	public void syncGuardsFromWorld() {
		guardIds.clear();
		BlockPos reserve = CentralBankPlacer.reservePos();
		if (reserve == null) {
			return;
		}
		ServerLevel level = server.overworld();
		for (Villager villager : level.getEntities(EntityTypeTest.forClass(Villager.class), BankSecurityService::isBankGuard)) {
			if (villager.blockPosition().closerThan(reserve, GUARD_SYNC_RADIUS)) {
				guardIds.add(villager.getUUID());
			}
		}
	}

	/** Fazla / eski / soygundaki muhafizlari siler; sunucu cokmesinden sonra bir kez calistirin. */
	public int purgeExcessGuards() {
		BlockPos reserve = CentralBankPlacer.reservePos();
		if (reserve == null) {
			return 0;
		}
		ServerLevel level = server.overworld();
		List<Villager> suspects = new ArrayList<>();
		for (Villager villager : level.getEntities(EntityTypeTest.forClass(Villager.class), v -> true)) {
			if (!villager.blockPosition().closerThan(reserve, GUARD_SYNC_RADIUS)) {
				continue;
			}
			if (isBankGuard(villager) || isLegacyGuardLike(villager)) {
				suspects.add(villager);
			}
		}
		suspects.sort((a, b) -> {
			boolean aOfficial = a.entityTags().contains(CentralBankPlacer.BANK_GUARD_TAG);
			boolean bOfficial = b.entityTags().contains(CentralBankPlacer.BANK_GUARD_TAG);
			if (aOfficial != bOfficial) {
				return aOfficial ? -1 : 1;
			}
			double da = a.distanceToSqr(reserve.getX() + 0.5, reserve.getY(), reserve.getZ() + 0.5);
			double db = b.distanceToSqr(reserve.getX() + 0.5, reserve.getY(), reserve.getZ() + 0.5);
			return Double.compare(da, db);
		});
		int maxKeep = EconomyConfig.bankGuardCount();
		int kept = 0;
		int removed = 0;
		for (Villager villager : suspects) {
			if (villager.entityTags().contains(CentralBankPlacer.BANK_GUARD_TAG) && kept < maxKeep) {
				kept++;
				continue;
			}
			villager.discard();
			removed++;
		}
		syncGuardsFromWorld();
		spawnCooldownTicks = SPAWN_COOLDOWN_TICKS;
		McEconomyMod.LOGGER.warn("[Guvenlik] Muhafiz temizligi: {} silindi, {} aktif", removed, guardIds.size());
		return removed;
	}

	public void spawnPermanentGuardsIfNeeded() {
		if (spawnCooldownTicks > 0) {
			spawnCooldownTicks--;
		}
		syncGuardsFromWorld();
		if (guardIds.size() >= EconomyConfig.bankGuardCount() || CentralBankPlacer.reservePos() == null) {
			return;
		}
		if (spawnCooldownTicks > 0) {
			return;
		}
		ServerLevel level = server.overworld();
		BlockPos reserve = CentralBankPlacer.reservePos();
		int count = EconomyConfig.bankGuardCount() - guardIds.size();
		for (int i = 0; i < count; i++) {
			double angle = (Math.PI * 2 / Math.max(count, 1)) * i;
			double gx = reserve.getX() + 0.5 + Math.cos(angle) * 5.5;
			double gz = reserve.getZ() + 0.5 + Math.sin(angle) * 5.5;
			Villager guard = EntityType.VILLAGER.create(level, null, BlockPos.containing(gx, reserve.getY(), gz),
					EntitySpawnReason.EVENT, false, false);
			if (guard == null) {
				continue;
			}
			guard.setPos(gx, reserve.getY(), gz);
			configureGuard(guard, level, GUARD_NAMES[i % GUARD_NAMES.length],
					SecurityWeapon.guardLoadout()[i % SecurityWeapon.guardLoadout().length]);
			level.addFreshEntity(guard);
			guardIds.add(guard.getUUID());
		}
		if (count > 0) {
			spawnCooldownTicks = SPAWN_COOLDOWN_TICKS;
			McEconomyMod.LOGGER.info("[Guvenlik] {} muhafiz devriyede (toplam {})", count, guardIds.size());
		}
	}

	private void configureGuard(Villager guard, ServerLevel level, String name, SecurityWeapon weapon) {
		var registries = level.registryAccess();
		guard.setCustomName(Component.literal("§b§l" + name));
		guard.setCustomNameVisible(true);
		guard.setPersistenceRequired();
		guard.setInvulnerable(true);
		guard.setVillagerData(new VillagerData(
				registries.lookupOrThrow(Registries.VILLAGER_TYPE).getOrThrow(VillagerType.PLAINS),
				registries.lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(VillagerProfession.WEAPONSMITH),
				1));
		guard.addTag(CentralBankPlacer.BANK_GUARD_TAG);
		guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon.item()));
		guard.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
	}

	private void hardenExistingGuards() {
		ServerLevel level = server.overworld();
		for (UUID guardId : new ArrayList<>(guardIds)) {
			var entity = level.getEntity(guardId);
			if (entity instanceof Villager guard && guard.isAlive()) {
				guard.setInvulnerable(true);
				guard.setPersistenceRequired();
				if (!guard.entityTags().contains(CentralBankPlacer.BANK_GUARD_TAG)) {
					guard.addTag(CentralBankPlacer.BANK_GUARD_TAG);
				}
			} else {
				guardIds.remove(guardId);
			}
		}
	}

	public void tick() {
		tickCounter++;
		if (tickCounter % SPAWN_CHECK_INTERVAL_TICKS == 0) {
			spawnPermanentGuardsIfNeeded();
			hardenExistingGuards();
		}
		updateSleepCycle();
		if (!guardsSleeping) {
			checkIntruders();
			if (highAlert && tickCounter % 15 == 0) {
				guardProximityVolleys();
			}
		}
		if (guardsSleeping && tickCounter % 20 == 0) {
			trackDepotTheftDuringSleep();
		}
	}

	private void updateSleepCycle() {
		ServerLevel level = server.overworld();
		boolean day = level.getSkyDarken() < 4;
		if (wasDay && !day && !guardsSleeping) {
			beginGuardSleep(level);
		}
		if (!wasDay && day && guardsSleeping) {
			onDawn(level);
		}
		wasDay = day;
		if (guardsSleeping && System.currentTimeMillis() >= guardsWakeAtMs) {
			endGuardSleep(level);
		}
	}

	private void beginGuardSleep(ServerLevel level) {
		guardsSleeping = true;
		var manager = McEconomyMod.getEconomyManager();
		if (manager != null && manager.securityCameraService() != null) {
			manager.securityCameraService().onNightBegins();
		}
		sleepMessageSent = true;
		guardsWakeAtMs = System.currentTimeMillis() + EconomyConfig.bankGuardSleepMinutes() * 60_000L;
		for (FacilityType type : FacilityType.values()) {
			sleepStartHashes.put(type, depotService.snapshotHash(level, type));
			sleepStartSnapshots.put(type, DepotSnapshot.fromStacks(depotService.snapshot(level, type)));
		}
		broadcast("§5§l[Gece Vardiyasi] §dMuhafizlar " + EconomyConfig.bankGuardSleepMinutes()
				+ " dk uyuyor — §fates yok, depo sandiklari acik!");
		broadcast("§7[Gece Soygunu] §fPiyasa / kara / altin §edepo sandiklarindan §fesya alabilirsiniz.");
		broadcast("§7[Gece Soygunu] §fAltin rezerv icin: §e/soygun baslat §7(RP, hasarsiz gece) veya §egunduz §7kasaya girin.");
		broadcast("§c[Sabah] §fDepoda eksiklik varsa tum sehirde ust arama yapilir!");
		for (UUID id : guardIds) {
			var entity = level.getEntity(id);
			if (entity instanceof Villager villager) {
				villager.setCustomName(Component.literal("§7§o" + stripSleepSuffix(villager) + " (uyuyor)"));
			}
		}
	}

	private void endGuardSleep(ServerLevel level) {
		guardsSleeping = false;
		var manager = McEconomyMod.getEconomyManager();
		if (manager != null && manager.securityCameraService() != null) {
			manager.securityCameraService().onNightEnds();
		}
		sleepMessageSent = false;
		for (UUID id : guardIds) {
			var entity = level.getEntity(id);
			if (entity instanceof Villager villager) {
				villager.setCustomName(Component.literal("§b§l" + stripSleepSuffix(villager)));
			}
		}
		broadcast("§a[Guvenlik] §fMuhafizlar uyandi ve devriyeye dondu.");
	}

	private static String stripSleepSuffix(Villager villager) {
		String name = villager.getName().getString();
		return name.replace("§7§o", "").replace(" (uyuyor)", "").replace("§b§l", "").trim();
	}

	private void onDawn(ServerLevel level) {
		boolean loss = false;
		List<DepotSnapshot.ItemFingerprint> allMissing = new ArrayList<>();
		for (FacilityType type : FacilityType.values()) {
			DepotSnapshot before = sleepStartSnapshots.get(type);
			DepotSnapshot after = DepotSnapshot.fromStacks(depotService.snapshot(level, type));
			if (before != null && before.hasLossComparedTo(after)) {
				loss = true;
				allMissing.addAll(before.missingFrom(after));
			} else {
				int hashBefore = sleepStartHashes.getOrDefault(type, 0);
				int hashAfter = depotService.snapshotHash(level, type);
				if (hashBefore != hashAfter && hashAfter < hashBefore) {
					loss = true;
				}
			}
		}
		if (loss) {
			long stolenValue = estimateMissingValue(allMissing);
			publishDepotRobberyBulletin(level, stolenValue);
			runCityWideSearch(level, allMissing);
			var manager = McEconomyMod.getEconomyManager();
			if (manager != null && manager.bankRobberyJusticeService() != null) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					manager.bankRobberyJusticeService().markSuspect(player.getUUID(),
							EconomyConfig.bankRobberySuspectDurationMs());
					manager.bankRobberyJusticeService().requestAutoScan(player.getUUID());
				}
			}
		}
		endGuardSleep(level);
	}

	private long estimateMissingValue(List<DepotSnapshot.ItemFingerprint> missing) {
		var manager = McEconomyMod.getEconomyManager();
		if (manager == null || manager.nationalReserveService() == null) {
			return 0;
		}
		var priceEngine = manager.marketService().priceEngine();
		long total = 0;
		for (DepotSnapshot.ItemFingerprint fp : missing) {
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(fp.itemId()));
			if (item == null) {
				continue;
			}
			total += manager.nationalReserveService().estimateItemValueMg(item, fp.count(), priceEngine);
		}
		return total;
	}

	private void publishDepotRobberyBulletin(ServerLevel level, long stolenValue) {
		var manager = McEconomyMod.getEconomyManager();
		if (manager == null || manager.bulletinService() == null) {
			broadcast("§4§l§n[OLAGANUSTU HAL] §c§lMerkez Bankasi deposunda eksiklik!");
			return;
		}
		manager.bulletinService().publishRobbery(server, manager.centralBank(),
				manager.marketService().priceEngine(),
				"GECE MERKEZ BANKASI DEPOSUNDA SOYGUN OLDU!",
				"Muhafiz uyku doneminde depo sandiklarindan esya calindi. Tum sehirde ust arama baslatildi.",
				stolenValue);
	}

	private void runCityWideSearch(ServerLevel level, List<DepotSnapshot.ItemFingerprint> missingItems) {
		var manager = McEconomyMod.getEconomyManager();
		if (manager == null) {
			return;
		}
		int prisonMin = EconomyConfig.theftPrisonMinutes();
		broadcast("§c§lTUM SEHIRDE UST ARAMA BASLATILDI — kimse guvenli degil!");
		broadcast("§7MASAK ve muhafizlar her oyuncuyu tarayacak...");
		int caught = 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isCreative() || player.isSpectator()) {
				continue;
			}
			player.sendSystemMessage(Component.literal(
					"§4§l[UST ARAMA] §cPolis sizi durdurdu ve envanterinizi ariyor!"));
			List<ItemStack> confiscated = new ArrayList<>();
			if (confiscateStolen(player, confiscated) || confiscateMissing(player, missingItems, confiscated)) {
				caught++;
				for (ItemStack stack : confiscated) {
					depotService.deposit(level, FacilityType.MARKET, stack);
				}
				try {
					manager.prisonService().imprison(player, prisonMin,
							"Sehir geneli ust arama — depo soygunu suphesi", "Guvenlik Sistemi");
				} catch (Exception e) {
					McEconomyMod.LOGGER.error("Ust arama cezasi", e);
				}
				broadcast("§4[Adalet] §c" + player.getName().getString()
						+ " ust aramada supheli esya ile yakalandi — " + prisonMin + " dk hapis!");
			} else {
				player.sendSystemMessage(Component.literal("§a[UST ARAMA] §fBu tur temiz ciktiniz."));
			}
		}
		if (caught == 0) {
			broadcast("§e[SABAH RAPORU] §fUst arama tamamlandi — supheli yakalanmadi, sorusturma suruyor.");
		} else {
			broadcast("§c[SABAH RAPORU] §f" + caught + " kisi ust aramada yakalandi ve hapse gonderildi.");
		}
	}

	private boolean confiscateStolen(ServerPlayer player, List<ItemStack> out) {
		boolean found = false;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (FacilityItemTags.isStolen(stack)) {
				out.add(stack.copy());
				player.getInventory().setItem(slot, ItemStack.EMPTY);
				found = true;
			}
		}
		return found;
	}

	private boolean confiscateMissing(ServerPlayer player, List<DepotSnapshot.ItemFingerprint> missing,
			List<ItemStack> out) {
		if (missing.isEmpty()) {
			return false;
		}
		boolean found = false;
		for (DepotSnapshot.ItemFingerprint fp : missing) {
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(fp.itemId()));
			if (item == null) {
				continue;
			}
			int toFind = fp.count();
			for (int slot = 0; slot < player.getInventory().getContainerSize() && toFind > 0; slot++) {
				ItemStack stack = player.getInventory().getItem(slot);
				if (!stack.is(item)) {
					continue;
				}
				int take = Math.min(stack.getCount(), toFind);
				out.add(stack.copyWithCount(take));
				stack.shrink(take);
				if (stack.isEmpty()) {
					player.getInventory().setItem(slot, ItemStack.EMPTY);
				}
				toFind -= take;
				found = true;
			}
		}
		return found;
	}

	private void trackDepotTheftDuringSleep() {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!CentralBankPlacer.isNearAnyDepot(player.blockPosition(), 6)) {
				continue;
			}
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				ItemStack stack = player.getInventory().getItem(i);
				if (!stack.isEmpty()) {
					FacilityItemTags.markStolen(stack);
				}
			}
		}
	}

	/**
	 * Gece uyku: kalici muhafizlar ates etmez (depo soygunu serbest).
	 * Gunduz: kasa cekirdegi, aktif soygun alarmi (highAlert) veya calinti ile kacis.
	 */
	private boolean shouldUseLethalForce(ServerPlayer player, BlockPos pos,
			boolean inVault, boolean inReserve, boolean inBank, boolean nearDepot) {
		if (guardsSleeping) {
			return false;
		}
		if (inVault) {
			return true;
		}
		if (hasStolenGoods(player) && (inBank || inReserve || nearDepot)) {
			return true;
		}
		if (highAlert) {
			return inBank || inReserve || inVault;
		}
		return false;
	}

	private static boolean hasStolenGoods(ServerPlayer player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (FacilityItemTags.isStolen(player.getInventory().getItem(slot))) {
				return true;
			}
		}
		return false;
	}

	private void checkIntruders() {
		ServerLevel level = server.overworld();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isCreative() || player.isSpectator()) {
				continue;
			}
			BlockPos pos = player.blockPosition();
			boolean inVault = CentralBankPlacer.isInsideReserveVault(pos.getX(), pos.getY(), pos.getZ());
			boolean inReserve = CentralBankPlacer.isInsideReserve(pos.getX(), pos.getY(), pos.getZ());
			boolean inBank = CentralBankPlacer.isInsideBank(pos.getX(), pos.getY(), pos.getZ());
			boolean nearDepot = CentralBankPlacer.isNearAnyDepot(pos, 4);

			if (!shouldUseLethalForce(player, pos, inVault, inReserve, inBank, nearDepot)) {
				intrusionLevel.remove(player.getUUID());
				intrusionCooldown.remove(player.getUUID());
				continue;
			}

			int cd = intrusionCooldown.getOrDefault(player.getUUID(), 0);
			if (cd > 0) {
				intrusionCooldown.put(player.getUUID(), cd - 1);
				continue;
			}
			intrusionCooldown.put(player.getUUID(), highAlert ? 12 : 28);
			int levelWarn = intrusionLevel.merge(player.getUUID(), 1, Integer::sum);
			String line = String.format(WARNING_LINES[ThreadLocalRandom.current().nextInt(WARNING_LINES.length)],
					player.getName().getString());
			if (highAlert) {
				broadcast(line);
			}
			player.sendSystemMessage(Component.literal("§c§l[ALARM] §4Yasak bolge! Muhafizlar ates aciyor!"));
			fireDamagingVolley(level, player, inVault);
			float damage = switch (levelWarn) {
				case 1 -> highAlert ? 2.5f : 1.0f;
				case 2 -> highAlert ? 4.0f : 2.5f;
				default -> inVault ? 7.0f : (highAlert ? 5.5f : 4.0f);
			};
			if (inVault) {
				damage += 2.0f;
			}
			applySecurityDamage(player, level, damage);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1.2f, 0.7f);
		}
	}

	private void applySecurityDamage(ServerPlayer player, ServerLevel level, float damage) {
		DamageSources sources = level.damageSources();
		if (!player.hurtServer(level, sources.generic(), damage)) {
			player.hurt(sources.generic(), damage);
		}
	}

	private void fireDamagingVolley(ServerLevel level, ServerPlayer target, boolean lethal) {
		float arrowDamage = lethal ? 3.5f : 2.0f;
		BlockPos from = CentralBankPlacer.reservePos() != null
				? CentralBankPlacer.reservePos() : target.blockPosition();
		for (int i = 0; i < 2; i++) {
			Arrow arrow = EntityType.ARROW.create(level, null, from, EntitySpawnReason.EVENT, false, false);
			if (arrow == null) {
				continue;
			}
			arrow.setPos(from.getX() + 0.5, from.getY() + 2, from.getZ() + 0.5);
			double dx = target.getX() - arrow.getX();
			double dy = (target.getEyeY() - arrow.getY()) * 0.35;
			double dz = target.getZ() - arrow.getZ();
			arrow.shoot(dx, dy, dz, 2.2f, 1.0f);
			arrow.setBaseDamage(arrowDamage);
			level.addFreshEntity(arrow);
		}
		level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getEyeY(), target.getZ(), 16, 0.5, 0.4, 0.5, 0.08);
	}

	/** Extra volleys only during active heist (gunduz nobet), inside bank/reserve. */
	private void guardProximityVolleys() {
		if (!highAlert || guardsSleeping) {
			return;
		}
		ServerLevel level = server.overworld();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isCreative() || player.isSpectator()) {
				continue;
			}
			BlockPos pos = player.blockPosition();
			boolean inBank = CentralBankPlacer.isInsideBank(pos.getX(), pos.getY(), pos.getZ());
			boolean inReserve = CentralBankPlacer.isInsideReserve(pos.getX(), pos.getY(), pos.getZ());
			boolean inVault = CentralBankPlacer.isInsideReserveVault(pos.getX(), pos.getY(), pos.getZ());
			if (!inBank && !inReserve && !inVault) {
				continue;
			}
			for (UUID guardId : guardIds) {
				var guard = level.getEntity(guardId);
				if (guard == null || guard.distanceToSqr(player) > 144) {
					continue;
				}
				if (ThreadLocalRandom.current().nextInt(4) == 0) {
					fireDamagingVolley(level, player, inVault);
					applySecurityDamage(player, level, highAlert ? 2.0f : 1.0f);
				}
			}
		}
	}

	public void onGuardHurt(Villager guard, ServerPlayer attacker) {
		if (guardsSleeping) {
			if (attacker != null) {
				attacker.sendSystemMessage(Component.literal(
						"§5[Gece] §dMuhafiz uyuyor — ates etmez. Depo sandiklarini kullanin veya /soygun baslat."));
			}
			return;
		}
		String guardName = guard.getName().getString();
		McEconomyMod.LOGGER.warn("[Guvenlik] Muhafiz {} saldirildi: {}", guardName,
				attacker != null ? attacker.getName().getString() : "?");
		broadcast("§c§l[ALARM] §4Muhafiz " + guardName + " yaralandi"
				+ (attacker != null ? "! Saldiri: " + attacker.getName().getString() : "!"));
		if (attacker != null && guard.level() instanceof ServerLevel guardLevel) {
			applySecurityDamage(attacker, guardLevel, 4.0f);
		}
	}

	public void onGuardDeath(Villager guard) {
		guardIds.remove(guard.getUUID());
		McEconomyMod.LOGGER.warn("[Guvenlik] Muhafiz kayboldu: {} — sessizce yenileniyor", guard.getName().getString());
		spawnCooldownTicks = 0;
	}

	public boolean canOpenDepot(ServerPlayer player) {
		return guardsSleeping;
	}

	private void broadcast(String message) {
		server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
	}
}
