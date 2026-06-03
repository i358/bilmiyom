package com.mceconomy.security;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** Ruhsatli / ruhsatsiz silahlarla ek hasar. */
public final class SecurityWeaponCombat {
	private SecurityWeaponCombat() {
	}

	public static void register() {
		AttackEntityCallback.EVENT.register((player, world, hand, target, hit) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer attacker) || !(target instanceof LivingEntity victim)) {
				return InteractionResult.PASS;
			}
			SecurityWeapon weapon = SecurityWeapon.fromItem(attacker.getMainHandItem().getItem());
			if (weapon == null) {
				return InteractionResult.PASS;
			}
			if (world instanceof ServerLevel serverLevel) {
				victim.hurtServer(serverLevel, serverLevel.damageSources().playerAttack(attacker), weapon.bonusDamage());
			}
			if (!weapon.licensed()) {
				attacker.sendSystemMessage(net.minecraft.network.chat.Component.literal(
						"§4[MASAK] §cRuhsatsiz silah kullanimi kayda gecirildi!"));
			}
			return InteractionResult.PASS;
		});
	}
}
