package com.mceconomy.property;

import net.minecraft.core.BlockPos;

import java.util.UUID;

public record PlayerProperty(
		long id,
		UUID ownerUuid,
		String tier,
		BlockPos origin,
		int originY,
		long purchasedAt) {
}
