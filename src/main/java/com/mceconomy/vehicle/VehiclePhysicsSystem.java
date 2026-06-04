package com.mceconomy.vehicle;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.phys.Vec3;

/** Basit surus fiziği — minecart kullanilmaz. */
public final class VehiclePhysicsSystem {
	private static final double MAX_SPEED = 0.55;
	private static final double ACCEL = 0.04;
	private static final double FRICTION = 0.92;
	private static final double BRAKE = 0.75;
	private static final double TURN_RATE = 2.8;

	private VehiclePhysicsSystem() {
	}

	public static void tick(Boat boat, VehicleInput input, double fuel) {
		if (boat == null || fuel <= 0) {
			return;
		}
		Vec3 vel = boat.getDeltaMovement();
		double speed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
		float yaw = boat.getYRot();
		if (input.forward()) {
			speed = Math.min(MAX_SPEED, speed + ACCEL);
		}
		if (input.backward()) {
			speed = Math.max(-MAX_SPEED * 0.4, speed - ACCEL);
		}
		if (input.brake()) {
			speed *= BRAKE;
		}
		if (input.handbrake()) {
			speed *= 0.5;
		}
		double turn = TURN_RATE * (0.35 + Math.min(1.0, speed / MAX_SPEED));
		if (input.left()) {
			yaw += (float) turn;
		}
		if (input.right()) {
			yaw -= (float) turn;
		}
		boat.setYRot(yaw);
		boat.setYBodyRot(yaw);
		double rad = Math.toRadians(yaw);
		double vx = -Math.sin(rad) * speed;
		double vz = Math.cos(rad) * speed;
		if (!input.forward() && !input.backward()) {
			vx *= FRICTION;
			vz *= FRICTION;
		}
		if (blockedAhead(boat, vx, vz)) {
			vx *= 0.2;
			vz *= 0.2;
			speed *= 0.2;
		}
		boat.setDeltaMovement(vx, vel.y, vz);
	}

	private static boolean blockedAhead(Boat boat, double vx, double vz) {
		if (Math.abs(vx) < 0.01 && Math.abs(vz) < 0.01) {
			return false;
		}
		ServerLevel level = (ServerLevel) boat.level();
		var ahead = boat.position().add(vx * 2, 0, vz * 2);
		var state = level.getBlockState(net.minecraft.core.BlockPos.containing(ahead));
		return state.isSolid() && !state.canBeReplaced();
	}

	public record VehicleInput(boolean forward, boolean backward, boolean left, boolean right,
			boolean brake, boolean handbrake) {
		public static final VehicleInput EMPTY = new VehicleInput(false, false, false, false, false, false);
	}
}
