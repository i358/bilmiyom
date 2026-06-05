package com.mceconomy.network;

import com.mceconomy.McEconomyMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record EconomyPanelSyncPayload(String tab, String json) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<EconomyPanelSyncPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(McEconomyMod.MOD_ID, "economy_panel_sync"));

	public static final StreamCodec<RegistryFriendlyByteBuf, EconomyPanelSyncPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, EconomyPanelSyncPayload::tab,
			ByteBufCodecs.STRING_UTF8, EconomyPanelSyncPayload::json,
			EconomyPanelSyncPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
