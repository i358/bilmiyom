package com.mceconomy.network;

import com.mceconomy.McEconomyMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record VehicleInputPayload(
		boolean forward,
		boolean backward,
		boolean left,
		boolean right,
		boolean brake,
		boolean handbrake
) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<VehicleInputPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(McEconomyMod.MOD_ID, "vehicle_input"));

	public static final StreamCodec<RegistryFriendlyByteBuf, VehicleInputPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, VehicleInputPayload::forward,
			ByteBufCodecs.BOOL, VehicleInputPayload::backward,
			ByteBufCodecs.BOOL, VehicleInputPayload::left,
			ByteBufCodecs.BOOL, VehicleInputPayload::right,
			ByteBufCodecs.BOOL, VehicleInputPayload::brake,
			ByteBufCodecs.BOOL, VehicleInputPayload::handbrake,
			VehicleInputPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
