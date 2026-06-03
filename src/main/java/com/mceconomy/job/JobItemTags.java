package com.mceconomy.job;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Meslek gorevi icin gecici odunc verilen esyalar. */
public final class JobItemTags {
	private static final String KEY_LOAN = "mceconomy_job_loan";
	private static final String KEY_JOB = "mceconomy_job_id";

	private JobItemTags() {
	}

	public static void markLoan(ItemStack stack, JobType job) {
		if (stack.isEmpty() || job == null) {
			return;
		}
		CompoundTag tag = copyOrNew(stack);
		tag.putBoolean(KEY_LOAN, true);
		tag.putString(KEY_JOB, job.id());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	public static boolean isJobLoan(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBoolean(KEY_LOAN).orElse(false);
	}

	private static CompoundTag copyOrNew(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null ? data.copyTag().copy() : new CompoundTag();
	}
}
