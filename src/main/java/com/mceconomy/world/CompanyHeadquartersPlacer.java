package com.mceconomy.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

public final class CompanyHeadquartersPlacer {
	private static final int W = 11;
	private static final int D = 9;
	private static final int H = 5;

	private CompanyHeadquartersPlacer() {
	}

	public static BlockPos findOrigin(ServerLevel level, int companyId) {
		BlockPos spawn = level.getRespawnData().pos();
		int ring = 48 + (companyId % 12) * 20;
		int cx = spawn.getX() + ring;
		int cz = spawn.getZ() + (companyId % 5) * 16;
		BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(cx, 0, cz));
		return new BlockPos(cx, surface.getY(), cz);
	}

	public static StructureBuildQueue.BlockPlacer placer(BlockPos origin, Runnable onDone) {
		List<BlockPos> positions = new ArrayList<>();
		List<BlockState> states = new ArrayList<>();
		BlockState wall = Blocks.IRON_BLOCK.defaultBlockState();
		BlockState floor = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
		BlockState glass = Blocks.GLASS_PANE.defaultBlockState();
		for (int x = 0; x < W; x++) {
			for (int z = 0; z < D; z++) {
				positions.add(origin.offset(x, 0, z));
				states.add(floor);
			}
		}
		for (int y = 1; y <= H; y++) {
			for (int x = 0; x < W; x++) {
				for (int z = 0; z < D; z++) {
					boolean shell = x == 0 || z == 0 || x == W - 1 || z == D - 1 || y == H;
					boolean door = z == 0 && x == W / 2 && y <= 2;
					positions.add(origin.offset(x, y, z));
					states.add(door ? Blocks.AIR.defaultBlockState() : (shell ? (y == 2 && x % 3 == 0 ? glass : wall) : Blocks.AIR.defaultBlockState()));
				}
			}
		}
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

	public static boolean isProtected(int x, int y, int z, BlockPos origin) {
		return x >= origin.getX() && x < origin.getX() + W
				&& z >= origin.getZ() && z < origin.getZ() + D
				&& y >= origin.getY() && y <= origin.getY() + H;
	}
}
