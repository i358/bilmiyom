package com.mceconomy.vehicle;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Blok araba govdesi — yerel koordinat (sag, yukari, ileri). */
public final class VehicleBodyBlueprint {
	public record BlockOffset(int right, int up, int forward, BlockState state) {
	}

	private final String id;
	private final List<BlockOffset> blocks;

	private VehicleBodyBlueprint(String id, List<BlockOffset> blocks) {
		this.id = id;
		this.blocks = List.copyOf(blocks);
	}

	public String id() {
		return id;
	}

	public List<BlockOffset> blocks() {
		return blocks;
	}

	public static VehicleBodyBlueprint fromModel(String model) {
		if (model != null && model.equalsIgnoreCase("suv")) {
			return suv();
		}
		return sedan();
	}

	public static VehicleBodyBlueprint sedan() {
		List<BlockOffset> b = new ArrayList<>();
		BlockState body = Blocks.GRAY_CONCRETE.defaultBlockState();
		BlockState trim = Blocks.BLACK_CONCRETE.defaultBlockState();
		BlockState glass = Blocks.GLASS.defaultBlockState();
		BlockState light = Blocks.SEA_LANTERN.defaultBlockState();
		// Tekerlekler
		for (int f : new int[] { -2, 2 }) {
			for (int r : new int[] { -1, 1 }) {
				b.add(new BlockOffset(r, 0, f, trim));
			}
		}
		// Alt govde
		for (int f = -2; f <= 2; f++) {
			for (int r = -1; r <= 1; r++) {
				if (Math.abs(r) == 1 || f != 0) {
					b.add(new BlockOffset(r, 0, f, body));
				}
			}
		}
		// Kabin
		for (int f = -1; f <= 1; f++) {
			for (int r = -1; r <= 1; r++) {
				boolean window = Math.abs(r) == 1 && f == 0;
				b.add(new BlockOffset(r, 1, f, window ? glass : body));
			}
		}
		// Tavan + farlar
		b.add(new BlockOffset(0, 2, 0, body));
		b.add(new BlockOffset(-1, 1, -2, light));
		b.add(new BlockOffset(1, 1, -2, light));
		b.add(new BlockOffset(0, 1, 2, trim));
		return new VehicleBodyBlueprint("sedan", b);
	}

	public static VehicleBodyBlueprint suv() {
		List<BlockOffset> b = new ArrayList<>();
		BlockState body = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
		BlockState trim = Blocks.BLACK_CONCRETE.defaultBlockState();
		BlockState glass = Blocks.TINTED_GLASS.defaultBlockState();
		BlockState light = Blocks.SEA_LANTERN.defaultBlockState();
		for (int f : new int[] { -2, 2 }) {
			for (int r : new int[] { -1, 1 }) {
				b.add(new BlockOffset(r, 0, f, trim));
			}
		}
		for (int f = -2; f <= 2; f++) {
			for (int r = -1; r <= 1; r++) {
				b.add(new BlockOffset(r, 0, f, body));
			}
		}
		for (int y = 1; y <= 2; y++) {
			for (int f = -1; f <= 1; f++) {
				for (int r = -1; r <= 1; r++) {
					boolean window = y == 1 && Math.abs(r) == 1;
					b.add(new BlockOffset(r, y, f, window ? glass : body));
				}
			}
		}
		b.add(new BlockOffset(-1, 1, -2, light));
		b.add(new BlockOffset(1, 1, -2, light));
		b.add(new BlockOffset(0, 2, 2, trim));
		return new VehicleBodyBlueprint("suv", b);
	}
}
