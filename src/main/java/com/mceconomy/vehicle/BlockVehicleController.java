package com.mceconomy.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Bloklardan olusan arac — surucu ArmorStand uzerinde. */
public final class BlockVehicleController {
	public static final String BLOCK_VEHICLE_TAG = "mceconomy_block_vehicle";

	private final UUID chassisUuid;
	private final VehicleBodyBlueprint blueprint;
	private final Set<BlockPos> placedBlocks = new HashSet<>();
	private double speed;
	private float yaw;
	private double lastSyncX = Double.NaN;
	private double lastSyncZ = Double.NaN;
	private float lastSyncYaw = Float.NaN;

	public BlockVehicleController(UUID chassisUuid, VehicleBodyBlueprint blueprint, float initialYaw) {
		this.chassisUuid = chassisUuid;
		this.blueprint = blueprint;
		this.yaw = initialYaw;
	}

	public UUID chassisUuid() {
		return chassisUuid;
	}

	public VehicleBodyBlueprint blueprint() {
		return blueprint;
	}

	public double speed() {
		return speed;
	}

	public float yaw() {
		return yaw;
	}

	public Set<BlockPos> placedBlocks() {
		return placedBlocks;
	}

	public static BlockVehicleController spawn(ServerLevel level, double x, double y, double z, float yaw,
			String model) {
		ArmorStand stand = EntityType.ARMOR_STAND.create(level, null, BlockPos.containing(x, y, z),
				EntitySpawnReason.COMMAND, false, false);
		if (stand == null) {
			return null;
		}
		stand.setPos(x, y, z);
		stand.setYRot(yaw);
		stand.setYBodyRot(yaw);
		stand.setInvisible(true);
		stand.setNoGravity(true);
		stand.setInvulnerable(true);
		stand.setSilent(true);
		stand.addTag(VehicleService.VEHICLE_TAG);
		stand.addTag(BLOCK_VEHICLE_TAG);
		stand.setCustomName(net.minecraft.network.chat.Component.literal(
				"§6[Arac] §f" + (model == null ? "sedan" : model)));
		stand.setCustomNameVisible(false);
		level.addFreshEntity(stand);
		BlockVehicleController ctrl = new BlockVehicleController(stand.getUUID(),
				VehicleBodyBlueprint.fromModel(model), yaw);
		double surfaceY = ctrl.resolveSurfaceY(level, x, z, y);
		stand.setPos(x, surfaceY, z);
		ctrl.syncBlocks(level, stand);
		return ctrl;
	}

	/** Arac bloklarini heightmap'ten sayma — yoksa her tick Y sonsuza yukselir. */
	private double resolveSurfaceY(ServerLevel level, double x, double z, double hintY) {
		int bx = BlockPos.containing(x, 0, z).getX();
		int bz = BlockPos.containing(x, 0, z).getZ();
		int startY = Math.min(level.getMaxY(), (int) Math.ceil(hintY) + 6);
		for (int y = startY; y >= level.getMinY(); y--) {
			BlockPos pos = new BlockPos(bx, y, bz);
			if (placedBlocks.contains(pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (!state.isAir() && !state.canBeReplaced()
					&& (state.isSolid() || state.blocksMotion())) {
				return y + 1.05;
			}
		}
		int heightmapY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				BlockPos.containing(x, 0, z)).getY();
		return heightmapY + 1.05;
	}

	public void applyPhysics(ServerLevel level, VehiclePhysicsSystem.VehicleInput input, double fuel) {
		Entity entity = level.getEntity(chassisUuid);
		if (entity == null || fuel <= 0) {
			return;
		}
		double currentSpeed = speed;
		if (input.forward()) {
			currentSpeed = Math.min(VehiclePhysicsSystem.MAX_SPEED, currentSpeed + VehiclePhysicsSystem.ACCEL);
		}
		if (input.backward()) {
			currentSpeed = Math.max(-VehiclePhysicsSystem.MAX_SPEED * 0.45, currentSpeed - VehiclePhysicsSystem.ACCEL);
		}
		if (input.brake()) {
			currentSpeed *= VehiclePhysicsSystem.BRAKE;
		}
		if (input.handbrake()) {
			currentSpeed *= 0.45;
		}
		double turn = VehiclePhysicsSystem.TURN_RATE * (0.4 + Math.min(1.0, Math.abs(currentSpeed) / VehiclePhysicsSystem.MAX_SPEED));
		if (input.left()) {
			yaw += (float) turn;
		}
		if (input.right()) {
			yaw -= (float) turn;
		}
		entity.setYRot(yaw);
		entity.setYBodyRot(yaw);
		double rad = Math.toRadians(yaw);
		double vx = -Math.sin(rad) * currentSpeed;
		double vz = Math.cos(rad) * currentSpeed;
		if (!input.forward() && !input.backward()) {
			vx *= VehiclePhysicsSystem.FRICTION;
			vz *= VehiclePhysicsSystem.FRICTION;
			currentSpeed *= VehiclePhysicsSystem.FRICTION;
		}
		if (VehiclePhysicsSystem.blockedAhead(level, entity.position(), vx, vz, placedBlocks)) {
			vx *= 0.15;
			vz *= 0.15;
			currentSpeed *= 0.15;
		}
		double nx = entity.getX() + vx;
		double nz = entity.getZ() + vz;
		double ny = resolveSurfaceY(level, nx, nz, entity.getY());
		entity.setPos(nx, ny, nz);
		entity.setDeltaMovement(vx, 0, vz);
		speed = currentSpeed;
		syncBlocks(level, entity);
	}

	public void syncBlocks(ServerLevel level, Entity anchor) {
		double ax = anchor.getX();
		double az = anchor.getZ();
		if (!placedBlocks.isEmpty()
				&& ax == lastSyncX && az == lastSyncZ && yaw == lastSyncYaw) {
			return;
		}
		lastSyncX = ax;
		lastSyncZ = az;
		lastSyncYaw = yaw;
		clearBlocks(level);
		double rad = Math.toRadians(yaw);
		double cos = Math.cos(rad);
		double sin = Math.sin(rad);
		int baseY = BlockPos.containing(anchor.getX(), anchor.getY(), anchor.getZ()).getY() - 1;
		for (VehicleBodyBlueprint.BlockOffset off : blueprint.blocks()) {
			double wx = anchor.getX() + off.right() * cos - off.forward() * sin;
			double wz = anchor.getZ() + off.right() * sin + off.forward() * cos;
			BlockPos pos = BlockPos.containing(wx, baseY + off.up(), wz);
			BlockState existing = level.getBlockState(pos);
			if (existing.isAir() || existing.canBeReplaced()) {
				level.setBlockAndUpdate(pos, off.state());
				placedBlocks.add(pos.immutable());
			}
		}
	}

	public void clearBlocks(ServerLevel level) {
		BlockState air = Blocks.AIR.defaultBlockState();
		for (BlockPos pos : new ArrayList<>(placedBlocks)) {
			level.setBlockAndUpdate(pos, air);
		}
		placedBlocks.clear();
	}

	public void discard(ServerLevel level) {
		clearBlocks(level);
		Entity entity = level.getEntity(chassisUuid);
		if (entity != null) {
			entity.discard();
		}
	}

	public static boolean isVehicleBlock(Set<BlockPos> allActive, BlockPos pos) {
		return allActive.contains(pos);
	}
}
