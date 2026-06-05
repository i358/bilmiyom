package com.mceconomy.network;

import com.mceconomy.McEconomyMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record EconomyPanelActionPayload(String action, String bodyJson) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<EconomyPanelActionPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(McEconomyMod.MOD_ID, "economy_panel_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, EconomyPanelActionPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, EconomyPanelActionPayload::action,
			ByteBufCodecs.STRING_UTF8, EconomyPanelActionPayload::bodyJson,
			EconomyPanelActionPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
