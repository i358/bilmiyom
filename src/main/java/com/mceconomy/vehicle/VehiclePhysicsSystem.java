package com.mceconomy.vehicle;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/** Surus fiziği — minecart/tekne yok. */
public final class VehiclePhysicsSystem {
	public static final double MAX_SPEED = 1.35;
	public static final double ACCEL = 0.09;
	public static final double FRICTION = 0.88;
	public static final double BRAKE = 0.65;
	public static final double TURN_RATE = 3.4;

	private VehiclePhysicsSystem() {
	}

	public static boolean blockedAhead(ServerLevel level, Vec3 pos, double vx, double vz) {
		return blockedAhead(level, pos, vx, vz, Set.of());
	}

	public static boolean blockedAhead(ServerLevel level, Vec3 pos, double vx, double vz, Set<BlockPos> ignore) {
		if (Math.abs(vx) < 0.01 && Math.abs(vz) < 0.01) {
			return false;
		}
		var ahead = pos.add(vx * 2.5, 0.5, vz * 2.5);
		BlockPos center = BlockPos.containing(ahead);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dy = 0; dy <= 1; dy++) {
					BlockPos check = center.offset(dx, dy, dz);
					if (ignore.contains(check)) {
						continue;
					}
					BlockState state = level.getBlockState(check);
					if (state.isSolid() && !state.canBeReplaced()) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public record VehicleInput(boolean forward, boolean backward, boolean left, boolean right,
			boolean brake, boolean handbrake) {
		public static final VehicleInput EMPTY = new VehicleInput(false, false, false, false, false, false);
	}
}
