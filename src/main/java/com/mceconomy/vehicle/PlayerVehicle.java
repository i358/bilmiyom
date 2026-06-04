package com.mceconomy.vehicle;

import net.minecraft.core.BlockPos;

import java.util.UUID;

public record PlayerVehicle(
		long id,
		UUID ownerUuid,
		String model,
		BlockPos garagePos,
		double fuel,
		UUID entityUuid,
		boolean spawned) {
}
