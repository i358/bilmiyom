package com.mceconomy.world;

import com.mceconomy.config.EconomyConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

public final class PropertyPlacer {
	public enum Tier {
		COTTAGE(7, 7, 4, EconomyConfig::propertyCottagePriceMg),
		HOUSE(9, 9, 5, EconomyConfig::propertyHousePriceMg),
		VILLA(12, 12, 6, EconomyConfig::propertyVillaPriceMg);

		public final int width;
		public final int depth;
		public final int height;
		private final java.util.function.Supplier<Long> priceSupplier;

		Tier(int width, int depth, int height, java.util.function.Supplier<Long> priceSupplier) {
			this.width = width;
			this.depth = depth;
			this.height = height;
			this.priceSupplier = priceSupplier;
		}

		public long priceMg() {
			return priceSupplier.get();
		}

		public static Tier fromId(String id) {
			if (id == null) {
				return COTTAGE;
			}
			return switch (id.toLowerCase()) {
				case "ev", "house" -> HOUSE;
				case "villa" -> VILLA;
				default -> COTTAGE;
			};
		}
	}

	private PropertyPlacer() {
	}

	public static BlockPos findBuildOrigin(ServerLevel level, int offsetIndex) {
		BlockPos spawn = level.getRespawnData().pos();
		int ring = 32 + (offsetIndex % 8) * 24;
		int angle = (offsetIndex * 37) % 360;
		int cx = spawn.getX() + (int) (Math.cos(Math.toRadians(angle)) * ring);
		int cz = spawn.getZ() + (int) (Math.sin(Math.toRadians(angle)) * ring);
		BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(cx, 0, cz));
		return new BlockPos(cx - 3, surface.getY(), cz - 3);
	}

	public static StructureBuildQueue.BlockPlacer placer(Tier tier, BlockPos origin, Runnable onDone) {
		List<BlockPos> positions = new ArrayList<>();
		List<BlockState> states = new ArrayList<>();
		BlockState wall = Blocks.OAK_PLANKS.defaultBlockState();
		BlockState floor = Blocks.SPRUCE_PLANKS.defaultBlockState();
		BlockState roof = Blocks.SPRUCE_STAIRS.defaultBlockState();
		BlockState accent = Blocks.LANTERN.defaultBlockState();
		for (int x = 0; x < tier.width; x++) {
			for (int z = 0; z < tier.depth; z++) {
				positions.add(origin.offset(x, 0, z));
				states.add(floor);
			}
		}
		for (int y = 1; y <= tier.height; y++) {
			for (int x = 0; x < tier.width; x++) {
				for (int z = 0; z < tier.depth; z++) {
					boolean shell = x == 0 || z == 0 || x == tier.width - 1 || z == tier.depth - 1 || y == tier.height;
					positions.add(origin.offset(x, y, z));
					states.add(shell ? wall : Blocks.AIR.defaultBlockState());
				}
			}
		}
		for (int x = 0; x < tier.width; x++) {
			for (int z = 0; z < tier.depth; z++) {
				positions.add(origin.offset(x, tier.height + 1, z));
				states.add(roof);
			}
		}
		positions.add(origin.offset(tier.width / 2, 1, 0));
		states.add(accent);
		return new StructureBuildQueue.BlockPlacer() {
			@Override
			public int totalBlocks() {
				return positions.size();
			}

			@Override
			public BlockState blockAt(int index) {
				return states.get(index);
			}

			@Override
			public BlockPos posAt(int index) {
				return positions.get(index);
			}

			@Override
			public void onComplete(ServerLevel level) {
				onDone.run();
			}
		};
	}

	public static boolean isProtected(ServerLevel level, int x, int y, int z, BlockPos origin, Tier tier) {
		if (origin == null) {
			return false;
		}
		return x >= origin.getX() && x < origin.getX() + tier.width
				&& z >= origin.getZ() && z < origin.getZ() + tier.depth
				&& y >= origin.getY() && y <= origin.getY() + tier.height + 1;
	}
}
