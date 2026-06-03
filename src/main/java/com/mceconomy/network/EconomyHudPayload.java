package com.mceconomy.network;

import com.mceconomy.McEconomyMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record EconomyHudPayload(
		long walletMg,
		long bankMg,
		long dirtyMg,
		boolean frozen,
		boolean blacklisted,
		String jobLabel
) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<EconomyHudPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(McEconomyMod.MOD_ID, "economy_hud"));

	public static final StreamCodec<RegistryFriendlyByteBuf, EconomyHudPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, EconomyHudPayload::walletMg,
			ByteBufCodecs.VAR_LONG, EconomyHudPayload::bankMg,
			ByteBufCodecs.VAR_LONG, EconomyHudPayload::dirtyMg,
			ByteBufCodecs.BOOL, EconomyHudPayload::frozen,
			ByteBufCodecs.BOOL, EconomyHudPayload::blacklisted,
			ByteBufCodecs.STRING_UTF8, EconomyHudPayload::jobLabel,
			EconomyHudPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
