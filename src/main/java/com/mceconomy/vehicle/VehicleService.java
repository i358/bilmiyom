package com.mceconomy.vehicle;

import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.CurrencyService;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.economy.TransactionType;
import com.mceconomy.network.VehicleInputPayload;
import com.mceconomy.network.VehicleStatePayload;
import com.mceconomy.persistence.repo.VehicleRepository;
import com.mceconomy.vehicle.VehiclePhysicsSystem.VehicleInput;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VehicleService {
	public static final String VEHICLE_TAG = "mceconomy_vehicle";

	private final VehicleRepository repository;
	private final CurrencyService currencyService;
	private final Map<Long, PlayerVehicle> vehicles = new HashMap<>();
	private final Map<UUID, VehicleInput> inputByPlayer = new ConcurrentHashMap<>();
	private final Map<UUID, Long> drivingVehicleId = new ConcurrentHashMap<>();
	private final Map<Long, BlockVehicleController> controllers = new HashMap<>();

	public VehicleService(VehicleRepository repository, CurrencyService currencyService) {
		this.repository = repository;
		this.currencyService = currencyService;
	}

	public void load() throws SQLException {
		vehicles.clear();
		controllers.clear();
		for (PlayerVehicle v : repository.loadAll()) {
			if (v.spawned()) {
				PlayerVehicle parked = new PlayerVehicle(v.id(), v.ownerUuid(), v.model(), v.garagePos(),
						v.fuel(), null, false);
				vehicles.put(v.id(), parked);
				repository.update(parked);
			} else {
				vehicles.put(v.id(), v);
			}
		}
	}

	public void setInput(UUID player, VehicleInputPayload payload) {
		inputByPlayer.put(player, new VehicleInput(
				payload.forward(), payload.backward(), payload.left(), payload.right(),
				payload.brake(), payload.handbrake()));
	}

	public String purchase(ServerPlayer player, String model) throws SQLException {
		if (repository.countForOwner(player.getUUID()) >= EconomyConfig.maxVehiclesPerPlayer()) {
			return "Garaj limiti: " + EconomyConfig.maxVehiclesPerPlayer();
		}
		long price = EconomyConfig.vehiclePurchasePriceMg();
		if (!currencyService.withdraw(player.getUUID(), price, TransactionType.MARKET_BUY)) {
			return "Yetersiz bakiye (" + GoldStandard.formatMilligrams(price) + ").";
		}
		BlockPos garage = player.blockPosition();
		String m = model == null ? "sedan" : model.toLowerCase();
		PlayerVehicle v = repository.insert(player.getUUID(), m, garage);
		vehicles.put(v.id(), v);
		return "OK";
	}

	public String spawn(ServerPlayer player, long vehicleId) throws SQLException {
		PlayerVehicle v = vehicles.get(vehicleId);
		if (v == null || !v.ownerUuid().equals(player.getUUID())) {
			return "Arac bulunamadi.";
		}
		if (v.spawned()) {
			return "Zaten yolda.";
		}
		if (repository.spawnedCount() >= EconomyConfig.maxActiveVehicles()) {
			return "Sunucu arac limiti dolu.";
		}
		ServerLevel level = (ServerLevel) player.level();
		BlockVehicleController ctrl = BlockVehicleController.spawn(
				level, player.getX(), player.getY(), player.getZ(), player.getYRot(), v.model());
		if (ctrl == null) {
			return "Spawn basarisiz.";
		}
		Entity chassis = level.getEntity(ctrl.chassisUuid());
		if (chassis == null) {
			return "Spawn basarisiz.";
		}
		player.startRiding(chassis);
		controllers.put(v.id(), ctrl);
		PlayerVehicle updated = new PlayerVehicle(v.id(), v.ownerUuid(), v.model(), v.garagePos(),
				v.fuel() > 0 ? v.fuel() : 100.0, ctrl.chassisUuid(), true);
		vehicles.put(v.id(), updated);
		repository.update(updated);
		drivingVehicleId.put(player.getUUID(), v.id());
		sendState(player, updated, ctrl.speed());
		return "OK";
	}

	public void onDriveTick(MinecraftServer server) {
		for (Map.Entry<UUID, Long> entry : new HashMap<>(drivingVehicleId).entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			PlayerVehicle v = vehicles.get(entry.getValue());
			BlockVehicleController ctrl = controllers.get(entry.getValue());
			if (player == null || v == null || ctrl == null || !v.spawned()) {
				cleanupDrive(entry.getKey(), entry.getValue(), player != null ? (ServerLevel) player.level() : null);
				continue;
			}
			ServerLevel level = (ServerLevel) player.level();
			Entity entity = level.getEntity(ctrl.chassisUuid());
			if (!(entity instanceof ArmorStand) || !player.isPassenger() || player.getVehicle() != entity) {
				parkVehicle(v, level);
				drivingVehicleId.remove(entry.getKey());
				continue;
			}
			double fuel = v.fuel();
			if (fuel <= 0) {
				player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[Arac] Yakit bitti."));
				parkVehicle(v, level);
				drivingVehicleId.remove(entry.getKey());
				continue;
			}
			VehicleInput input = inputByPlayer.getOrDefault(player.getUUID(), VehicleInput.EMPTY);
			ctrl.applyPhysics(level, input, fuel);
			boolean moving = Math.abs(ctrl.speed()) > 0.02
					&& (input.forward() || input.backward());
			if (moving && server.getTickCount() % 20 == 0) {
				fuel = Math.max(0, fuel - 0.35);
			}
			PlayerVehicle next = new PlayerVehicle(v.id(), v.ownerUuid(), v.model(), v.garagePos(),
					fuel, ctrl.chassisUuid(), true);
			vehicles.put(v.id(), next);
			try {
				repository.update(next);
			} catch (SQLException e) {
				com.mceconomy.McEconomyMod.LOGGER.warn("Arac yakit DB guncelleme", e);
			}
			sendState(player, next, Math.abs(ctrl.speed()));
		}
	}

	private void sendState(ServerPlayer player, PlayerVehicle v, double speed) {
		ServerPlayNetworking.send(player, new VehicleStatePayload(
				player.getX(), player.getY(), player.getZ(), player.getYRot(), speed, v.fuel(), v.model()));
	}

	private void cleanupDrive(UUID playerId, long vehicleId, ServerLevel level) {
		drivingVehicleId.remove(playerId);
		PlayerVehicle v = vehicles.get(vehicleId);
		if (v != null && level != null) {
			parkVehicle(v, level);
		}
	}

	private void parkVehicle(PlayerVehicle v, ServerLevel level) {
		BlockVehicleController ctrl = controllers.remove(v.id());
		if (ctrl != null) {
			ctrl.discard(level);
		} else if (v.entityUuid() != null) {
			Entity e = level.getEntity(v.entityUuid());
			if (e != null) {
				e.discard();
			}
		}
		PlayerVehicle parked = new PlayerVehicle(v.id(), v.ownerUuid(), v.model(), v.garagePos(),
				v.fuel(), null, false);
		vehicles.put(v.id(), parked);
		try {
			repository.update(parked);
		} catch (SQLException e) {
			com.mceconomy.McEconomyMod.LOGGER.error("Arac park", e);
		}
	}

	public boolean isProtectedVehicleBlock(BlockPos pos) {
		for (BlockVehicleController ctrl : controllers.values()) {
			if (ctrl.placedBlocks().contains(pos)) {
				return true;
			}
		}
		return false;
	}

	public Set<BlockPos> allVehicleBlocks() {
		Set<BlockPos> all = new HashSet<>();
		for (BlockVehicleController ctrl : controllers.values()) {
			all.addAll(ctrl.placedBlocks());
		}
		return all;
	}

	public java.util.List<PlayerVehicle> forOwner(UUID owner) {
		return vehicles.values().stream().filter(v -> v.ownerUuid().equals(owner)).toList();
	}
}
