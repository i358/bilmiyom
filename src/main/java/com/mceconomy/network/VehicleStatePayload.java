package com.mceconomy.network;

import com.mceconomy.McEconomyMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record VehicleStatePayload(
		double x,
		double y,
		double z,
		float yaw,
		double speed,
		double fuel,
		String model
) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<VehicleStatePayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(McEconomyMod.MOD_ID, "vehicle_state"));

	public static final StreamCodec<RegistryFriendlyByteBuf, VehicleStatePayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.DOUBLE, VehicleStatePayload::x,
			ByteBufCodecs.DOUBLE, VehicleStatePayload::y,
			ByteBufCodecs.DOUBLE, VehicleStatePayload::z,
			ByteBufCodecs.FLOAT, VehicleStatePayload::yaw,
			ByteBufCodecs.DOUBLE, VehicleStatePayload::speed,
			ByteBufCodecs.DOUBLE, VehicleStatePayload::fuel,
			ByteBufCodecs.STRING_UTF8, VehicleStatePayload::model,
			VehicleStatePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
