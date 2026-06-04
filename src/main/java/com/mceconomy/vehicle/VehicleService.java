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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.phys.Vec3;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VehicleService {
	public static final String VEHICLE_TAG = "mceconomy_vehicle";

	private final VehicleRepository repository;
	private final CurrencyService currencyService;
	private final Map<Long, PlayerVehicle> vehicles = new HashMap<>();
	private final Map<UUID, VehicleInput> inputByPlayer = new ConcurrentHashMap<>();
	private final Map<UUID, Long> drivingVehicleId = new ConcurrentHashMap<>();

	public VehicleService(VehicleRepository repository, CurrencyService currencyService) {
		this.repository = repository;
		this.currencyService = currencyService;
	}

	public void load() throws SQLException {
		vehicles.clear();
		for (PlayerVehicle v : repository.loadAll()) {
			vehicles.put(v.id(), v);
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
		PlayerVehicle v = repository.insert(player.getUUID(), model == null ? "sedan" : model, garage);
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
		try {
			if (repository.spawnedCount() >= EconomyConfig.maxActiveVehicles()) {
				return "Sunucu arac limiti dolu.";
			}
		} catch (SQLException e) {
			return "DB hatasi.";
		}
		ServerLevel level = (ServerLevel) player.level();
		Boat boat = net.minecraft.world.entity.EntityType.OAK_BOAT.create(
				level, null, player.blockPosition(), EntitySpawnReason.COMMAND, false, false);
		if (boat == null) {
			return "Spawn basarisiz.";
		}
		boat.setPos(player.getX(), player.getY() + 0.05, player.getZ());
		boat.setYRot(player.getYRot());
		boat.setYBodyRot(player.getYRot());
		boat.addTag(VEHICLE_TAG);
		boat.setCustomName(net.minecraft.network.chat.Component.literal(
				"§6[Arac] §f" + (v.model() == null ? "sedan" : v.model())));
		boat.setCustomNameVisible(true);
		level.addFreshEntity(boat);
		player.startRiding(boat);
		PlayerVehicle updated = new PlayerVehicle(v.id(), v.ownerUuid(), v.model(), v.garagePos(),
				v.fuel(), boat.getUUID(), true);
		vehicles.put(v.id(), updated);
		repository.update(updated);
		drivingVehicleId.put(player.getUUID(), v.id());
		return "OK";
	}

	public void onDriveTick(MinecraftServer server) {
		for (Map.Entry<UUID, Long> entry : new HashMap<>(drivingVehicleId).entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			PlayerVehicle v = vehicles.get(entry.getValue());
			if (player == null || v == null || !v.spawned() || v.entityUuid() == null) {
				drivingVehicleId.remove(entry.getKey());
				continue;
			}
			Entity entity = ((ServerLevel) player.level()).getEntity(v.entityUuid());
			if (!(entity instanceof Boat boat) || !player.isPassenger()) {
				parkVehicle(v, (ServerLevel) player.level());
				drivingVehicleId.remove(entry.getKey());
				continue;
			}
			VehicleInput input = inputByPlayer.getOrDefault(player.getUUID(), VehicleInput.EMPTY);
			double fuel = v.fuel();
			if (fuel <= 0) {
				player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
						"§c[Arac] Yakit bitti."));
				parkVehicle(v, (ServerLevel) player.level());
				drivingVehicleId.remove(entry.getKey());
				continue;
			}
			VehiclePhysicsSystem.tick(boat, input, fuel);
			long fuelCost = EconomyConfig.vehicleFuelCostPerSecondMg() / 20;
			if (server.getTickCount() % 20 == 0 && fuelCost > 0) {
				fuel = Math.max(0, fuel - 1);
				if (currencyService.withdraw(player.getUUID(), fuelCost, TransactionType.MARKET_BUY)) {
					fuel = Math.max(0, v.fuel() - 0.5);
				}
			}
			Vec3 vel = boat.getDeltaMovement();
			double speed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
			PlayerVehicle next = new PlayerVehicle(v.id(), v.ownerUuid(), v.model(), v.garagePos(),
					fuel, v.entityUuid(), true);
			vehicles.put(v.id(), next);
			try {
				repository.update(next);
			} catch (SQLException ignored) {
			}
			VehicleStatePayload state = new VehicleStatePayload(
					boat.getX(), boat.getY(), boat.getZ(), boat.getYRot(), speed, fuel);
			ServerPlayNetworking.send(player, state);
		}
	}

	private void parkVehicle(PlayerVehicle v, ServerLevel level) {
		if (v.entityUuid() != null) {
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

	public java.util.List<PlayerVehicle> forOwner(UUID owner) {
		return vehicles.values().stream().filter(v -> v.ownerUuid().equals(owner)).toList();
	}
}
