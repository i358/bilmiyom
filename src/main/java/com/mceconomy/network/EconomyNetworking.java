package com.mceconomy.network;

import com.mceconomy.McEconomyMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Play fazı custom payload kayıtları — client ve sunucuda aynı codec gerekli. */
public final class EconomyNetworking {
	private EconomyNetworking() {
	}

	public static void registerPlayPayloads() {
		register(PayloadTypeRegistry.clientboundPlay(), EconomyHudPayload.TYPE, EconomyHudPayload.STREAM_CODEC);
		register(PayloadTypeRegistry.clientboundPlay(), VehicleStatePayload.TYPE, VehicleStatePayload.STREAM_CODEC);
		register(PayloadTypeRegistry.serverboundPlay(), VehicleInputPayload.TYPE, VehicleInputPayload.STREAM_CODEC);
		register(PayloadTypeRegistry.clientboundPlay(), EconomyPanelOpenPayload.TYPE, EconomyPanelOpenPayload.STREAM_CODEC);
		register(PayloadTypeRegistry.clientboundPlay(), EconomyPanelSyncPayload.TYPE, EconomyPanelSyncPayload.STREAM_CODEC);
		register(PayloadTypeRegistry.serverboundPlay(), EconomyPanelActionPayload.TYPE, EconomyPanelActionPayload.STREAM_CODEC);
	}

	private static <T extends CustomPacketPayload> void register(
			PayloadTypeRegistry<RegistryFriendlyByteBuf> registry,
			CustomPacketPayload.Type<T> type,
			StreamCodec<RegistryFriendlyByteBuf, T> codec) {
		try {
			registry.register(type, codec);
		} catch (IllegalArgumentException alreadyRegistered) {
			McEconomyMod.LOGGER.debug("Payload zaten kayitli: {}", type.id());
		}
	}
}
