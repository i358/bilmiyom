package com.mceconomy.network;

import com.mceconomy.McEconomyMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record EconomyPanelOpenPayload(String initialTab) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<EconomyPanelOpenPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(McEconomyMod.MOD_ID, "economy_panel_open"));

	public static final StreamCodec<RegistryFriendlyByteBuf, EconomyPanelOpenPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, EconomyPanelOpenPayload::initialTab,
			EconomyPanelOpenPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
