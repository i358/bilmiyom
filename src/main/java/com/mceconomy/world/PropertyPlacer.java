package com.mceconomy.world;

import com.mceconomy.config.EconomyConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class PropertyPlacer {
	public enum Tier {
		COTTAGE(11, 9, 5, 8, EconomyConfig::propertyCottagePriceMg),
		HOUSE(13, 11, 6, 10, EconomyConfig::propertyHousePriceMg),
		VILLA(16, 14, 7, 12, EconomyConfig::propertyVillaPriceMg);

		public final int width;
		public final int depth;
		public final int height;
		public final int clearHeight;
		private final Supplier<Long> priceSupplier;

		Tier(int width, int depth, int height, int clearHeight, Supplier<Long> priceSupplier) {
			this.width = width;
			this.depth = depth;
			this.height = height;
			this.clearHeight = clearHeight;
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
		int ring = 40 + (offsetIndex % 10) * 28;
		int angle = (offsetIndex * 41) % 360;
		int cx = spawn.getX() + (int) (Math.cos(Math.toRadians(angle)) * ring);
		int cz = spawn.getZ() + (int) (Math.sin(Math.toRadians(angle)) * ring);
		BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(cx, 0, cz));
		return new BlockPos(cx - 4, surface.getY(), cz - 4);
	}

	public static void demolish(ServerLevel level, BlockPos origin, Tier tier) {
		if (origin == null || level == null) {
			return;
		}
		BlockState air = Blocks.AIR.defaultBlockState();
		int margin = 3;
		for (int x = -margin; x < tier.width + margin; x++) {
			for (int z = -margin; z < tier.depth + margin; z++) {
				for (int y = -2; y <= tier.clearHeight; y++) {
					level.setBlockAndUpdate(origin.offset(x, y, z), air);
				}
			}
		}
	}

	public static StructureBuildQueue.BlockPlacer placer(Tier tier, BlockPos origin, Runnable onDone) {
		BlueprintBuilder b = new BlueprintBuilder();
		switch (tier) {
			case COTTAGE -> buildCottage(b, tier);
			case HOUSE -> buildHouse(b, tier);
			case VILLA -> buildVilla(b, tier);
		}
		return b.toPlacer(origin, onDone);
	}

	private static void buildCottage(BlueprintBuilder b, Tier tier) {
		BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
		BlockState log = Blocks.SPRUCE_LOG.defaultBlockState();
		BlockState plank = Blocks.OAK_PLANKS.defaultBlockState();
		BlockState dark = Blocks.SPRUCE_PLANKS.defaultBlockState();
		BlockState glass = Blocks.GLASS_PANE.defaultBlockState();
		BlockState stair = Blocks.SPRUCE_STAIRS.defaultBlockState();
		BlockState slab = Blocks.SPRUCE_SLAB.defaultBlockState();
		BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
		int w = tier.width;
		int d = tier.depth;
		int h = tier.height;
		// Temel + veranda
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < d; z++) {
				b.set(x, 0, z, z < 2 ? stone : dark);
			}
		}
		for (int x = 0; x < 3; x++) {
			b.set(x, 0, -1, stone);
			b.set(x, 1, -1, fence);
		}
		// Kose kirişleri
		for (int y = 1; y <= h; y++) {
			b.set(0, y, 0, log);
			b.set(w - 1, y, 0, log);
			b.set(0, y, d - 1, log);
			b.set(w - 1, y, d - 1, log);
		}
		// Duvarlar + pencere
		for (int y = 1; y <= h; y++) {
			for (int x = 0; x < w; x++) {
				for (int z = 0; z < d; z++) {
					boolean shell = x == 0 || z == 0 || x == w - 1 || z == d - 1;
					boolean door = z == 0 && x == w / 2 && y <= 2;
					boolean window = y == 2 && (x == w / 2 || z == d - 1) && x > 0 && x < w - 1;
					if (door) {
						b.set(x, y, z, Blocks.AIR.defaultBlockState());
					} else if (shell) {
						b.set(x, y, z, window ? glass : plank);
					} else if (y == 1) {
						b.set(x, y, z, dark);
					}
				}
			}
		}
		// Çatı
		for (int x = -1; x <= w; x++) {
			for (int z = 0; z < d; z++) {
				b.set(x, h + 1, z, stair);
				b.set(x, h + 2, z, slab);
			}
		}
		// Baca + fener
		b.set(w - 2, h + 1, d - 2, stone);
		b.set(w - 2, h + 2, d - 2, stone);
		b.set(w - 2, h + 3, d - 2, stone);
		b.set(w / 2, 1, d / 2, Blocks.LANTERN.defaultBlockState());
		b.set(2, 1, d - 2, Blocks.CHEST.defaultBlockState());
		b.set(w - 3, 1, 2, Blocks.WHITE_BED.defaultBlockState());
	}

	private static void buildHouse(BlueprintBuilder b, Tier tier) {
		BlockState brick = Blocks.BRICKS.defaultBlockState();
		BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
		BlockState plank = Blocks.DARK_OAK_PLANKS.defaultBlockState();
		BlockState glass = Blocks.GLASS_PANE.defaultBlockState();
		BlockState stair = Blocks.DARK_OAK_STAIRS.defaultBlockState();
		int w = tier.width;
		int d = tier.depth;
		int h = tier.height;
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < d; z++) {
				b.set(x, 0, z, stone);
			}
		}
		for (int y = 1; y <= h; y++) {
			for (int x = 0; x < w; x++) {
				for (int z = 0; z < d; z++) {
					boolean shell = x == 0 || z == 0 || x == w - 1 || z == d - 1;
					boolean door = z == 0 && x == w / 2 && y <= 2;
					boolean window = (y == 2 || y == 4) && shell && !door
							&& (x + z) % 3 == 0 && x > 0 && x < w - 1 && z > 0 && z < d - 1;
					if (door) {
						b.set(x, y, z, Blocks.AIR.defaultBlockState());
					} else if (shell) {
						b.set(x, y, z, window ? glass : (x % 4 == 0 ? brick : plank));
					} else if (y == 1 || y == 4) {
						b.set(x, y, z, plank);
					}
				}
			}
		}
		// İkinci kat döşeme
		for (int x = 1; x < w - 1; x++) {
			for (int z = 1; z < d - 1; z++) {
				b.set(x, 4, z, plank);
			}
		}
		// Merdiven boşluğu
		for (int y = 1; y <= 3; y++) {
			b.set(1, y, 1, Blocks.AIR.defaultBlockState());
			b.set(1, y, 2, Blocks.AIR.defaultBlockState());
		}
		b.set(1, 1, 2, Blocks.OAK_STAIRS.defaultBlockState());
		// Çatı + balkon
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < d; z++) {
				b.set(x, h + 1, z, stair);
			}
		}
		for (int x = w / 2 - 1; x <= w / 2 + 1; x++) {
			b.set(x, 2, -1, Blocks.OAK_FENCE.defaultBlockState());
			b.set(x, 3, -1, Blocks.OAK_FENCE.defaultBlockState());
		}
		b.set(w / 2, 1, d / 2, Blocks.LANTERN.defaultBlockState());
		b.set(w - 2, 1, d - 2, Blocks.CHEST.defaultBlockState());
		b.set(3, 5, d - 3, Blocks.WHITE_BED.defaultBlockState());
	}

	private static void buildVilla(BlueprintBuilder b, Tier tier) {
		BlockState white = Blocks.QUARTZ_BLOCK.defaultBlockState();
		BlockState dark = Blocks.DARK_OAK_PLANKS.defaultBlockState();
		BlockState pillar = Blocks.QUARTZ_PILLAR.defaultBlockState();
		BlockState glass = Blocks.GLASS.defaultBlockState();
		BlockState pool = Blocks.WATER.defaultBlockState();
		BlockState stair = Blocks.QUARTZ_STAIRS.defaultBlockState();
		int w = tier.width;
		int d = tier.depth;
		int h = tier.height;
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < d; z++) {
				b.set(x, 0, z, white);
			}
		}
		// Havuz (arka bahçe)
		for (int x = w - 6; x < w - 1; x++) {
			for (int z = d - 5; z < d - 1; z++) {
				b.set(x, 0, z, pool);
			}
		}
		// Kolonlu giriş
		for (int y = 1; y <= 3; y++) {
			b.set(2, y, 0, pillar);
			b.set(w - 3, y, 0, pillar);
		}
		for (int y = 1; y <= h; y++) {
			for (int x = 0; x < w; x++) {
				for (int z = 0; z < d; z++) {
					boolean shell = x == 0 || z == 0 || x == w - 1 || z == d - 1;
					boolean door = z == 0 && x >= w / 2 - 1 && x <= w / 2 + 1 && y <= 2;
					boolean bigWindow = y >= 2 && y <= 4 && shell && (x == w / 2 || z == d / 2);
					if (door) {
						b.set(x, y, z, Blocks.AIR.defaultBlockState());
					} else if (shell) {
						b.set(x, y, z, bigWindow ? glass : white);
					} else if (y == 1 || y == 4) {
						b.set(x, y, z, dark);
					}
				}
			}
		}
		for (int x = 1; x < w - 1; x++) {
			for (int z = 1; z < d - 1; z++) {
				b.set(x, 4, z, dark);
			}
		}
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < d; z++) {
				b.set(x, h + 1, z, stair);
				b.set(x, h + 2, z, Blocks.QUARTZ_SLAB.defaultBlockState());
			}
		}
		b.set(w / 2, 1, 1, Blocks.SEA_LANTERN.defaultBlockState());
		b.set(2, 1, d - 3, Blocks.CHEST.defaultBlockState());
		b.set(w - 4, 1, 3, Blocks.CHEST.defaultBlockState());
		b.set(w / 2, 5, d / 2, Blocks.WHITE_BED.defaultBlockState());
	}

	public static boolean isProtected(ServerLevel level, int x, int y, int z, BlockPos origin, Tier tier) {
		if (origin == null) {
			return false;
		}
		return x >= origin.getX() - 2 && x < origin.getX() + tier.width + 2
				&& z >= origin.getZ() - 2 && z < origin.getZ() + tier.depth + 2
				&& y >= origin.getY() - 1 && y <= origin.getY() + tier.clearHeight;
	}

	private static final class BlueprintBuilder {
		private final List<BlockPos> positions = new ArrayList<>();
		private final List<BlockState> states = new ArrayList<>();

		void set(int x, int y, int z, BlockState state) {
			if (state == null || state.isAir()) {
				positions.add(new BlockPos(x, y, z));
				states.add(Blocks.AIR.defaultBlockState());
				return;
			}
			positions.add(new BlockPos(x, y, z));
			states.add(state);
		}

		StructureBuildQueue.BlockPlacer toPlacer(BlockPos origin, Runnable onDone) {
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
					return origin.offset(positions.get(index));
				}

				@Override
				public void onComplete(ServerLevel level) {
					onDone.run();
				}
			};
		}
	}
}
