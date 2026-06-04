package com.mceconomy.world;

import com.mceconomy.McEconomyMod;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.facility.FacilityType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;

public final class CentralBankPlacer {
	private static final int BANK_WIDTH = 19;
	private static final int BANK_DEPTH = 11;
	private static final int BANK_HEIGHT = 6;
	private static final int RAIL_APPROACH_LENGTH = 42;
	public static final String NPC_TAG = "mceconomy_bank_teller";
	public static final String MASAK_NPC_TAG = "mceconomy_masak_officer";
	public static final String EXCHANGE_NPC_TAG = "mceconomy_exchange_broker";
	public static final String HEIST_GUARD_TAG = "mceconomy_heist_guard";
	public static final String HEIST_ROBBER_TAG = "mceconomy_heist_robber";
	public static final String BANK_GUARD_TAG = "mceconomy_bank_guard";
	public static final String BLACK_MARKET_NPC_TAG = "mceconomy_black_market_dealer";

	private static BlockPos bankOrigin;
	private static BlockPos depotMarket;
	private static BlockPos depotBlackMarket;
	private static BlockPos depotPhysicalGold;
	private static BlockPos reserveCenter;
	private static BlockPos reserveMin;
	private static BlockPos reserveMax;
	private static BlockPos bankMin;
	private static BlockPos bankMax;

	private CentralBankPlacer() {
	}

	public static BlockPos reservePos() {
		return reserveCenter;
	}

	public static BlockPos reserveMin() {
		return reserveMin;
	}

	public static BlockPos reserveMax() {
		return reserveMax;
	}

	public static BlockPos bankOrigin() {
		return bankOrigin;
	}

	public static BlockPos depotPos(FacilityType type) {
		return switch (type) {
			case MARKET -> depotMarket;
			case BLACK_MARKET -> depotBlackMarket;
			case PHYSICAL_GOLD -> depotPhysicalGold;
		};
	}

	public static boolean isDepotChest(BlockPos pos) {
		if (pos == null) {
			return false;
		}
		return pos.equals(depotMarket) || pos.equals(depotBlackMarket) || pos.equals(depotPhysicalGold);
	}

	/** MB binasi, rezerv ve depolar etrafi — guvenlik kamerasi bolgesi. */
	public static boolean isInSurveillanceZone(BlockPos pos, int extraRadius) {
		if (pos == null) {
			return false;
		}
		if (isInsideBank(pos.getX(), pos.getY(), pos.getZ())) {
			return true;
		}
		if (reserveMin != null && reserveMax != null) {
			int pad = extraRadius;
			if (pos.getX() >= reserveMin.getX() - pad && pos.getX() <= reserveMax.getX() + pad
					&& pos.getY() >= reserveMin.getY() - 2 && pos.getY() <= reserveMax.getY() + pad + 2
					&& pos.getZ() >= reserveMin.getZ() - pad && pos.getZ() <= reserveMax.getZ() + pad) {
				return true;
			}
		}
		return isNearAnyDepot(pos, extraRadius);
	}

	public static boolean isNearAnyDepot(BlockPos pos, int radius) {
		for (FacilityType type : FacilityType.values()) {
			BlockPos depot = depotPos(type);
			if (depot != null && pos.closerThan(depot, radius)) {
				return true;
			}
		}
		return false;
	}

	public static net.minecraft.world.phys.AABB bankSearchBounds(int expand) {
		if (bankMin == null || bankMax == null) {
			return null;
		}
		return new net.minecraft.world.phys.AABB(
				bankMin.getX() - expand, bankMin.getY() - 2, bankMin.getZ() - expand,
				bankMax.getX() + expand + 1, bankMax.getY() + expand + 2, bankMax.getZ() + expand + 1);
	}

	public static boolean isInsideBankPerimeter(double x, double y, double z, int expand) {
		if (bankMin == null || bankMax == null) {
			return false;
		}
		return x >= bankMin.getX() - expand && x <= bankMax.getX() + expand + 1
				&& y >= bankMin.getY() - 2 && y <= bankMax.getY() + expand + 2
				&& z >= bankMin.getZ() - expand && z <= bankMax.getZ() + expand + 1;
	}

	public static boolean isInsideBank(int x, int y, int z) {
		if (bankMin == null || bankMax == null) {
			return false;
		}
		return x >= bankMin.getX() && x <= bankMax.getX()
				&& y >= bankMin.getY() && y <= bankMax.getY()
				&& z >= bankMin.getZ() && z <= bankMax.getZ();
	}

	public static boolean isInsideReserve(int x, int y, int z) {
		if (reserveMin == null || reserveMax == null) {
			return false;
		}
		return x >= reserveMin.getX() && x <= reserveMax.getX()
				&& y >= reserveMin.getY() && y <= reserveMax.getY()
				&& z >= reserveMin.getZ() && z <= reserveMax.getZ();
	}

	/** Celik kasa ic bolgesi (altinlarin oldugu hacim). */
	public static boolean isInsideReserveVault(int x, int y, int z) {
		if (reserveCenter == null) {
			return false;
		}
		return x >= reserveCenter.getX() - 1 && x <= reserveCenter.getX() + 1
				&& y >= reserveCenter.getY() && y <= reserveCenter.getY() + 2
				&& z >= reserveCenter.getZ() - 1 && z <= reserveCenter.getZ() + 1;
	}

	/** Rezerv bolgesi ve banka dis kabugu kirilamaz. */
	public static boolean isProtectedBlock(int x, int y, int z) {
		if (isInsideReserve(x, y, z)) {
			return true;
		}
		if (bankMin == null || bankMax == null) {
			return false;
		}
		return isInsideBank(x, y, z)
				&& (x == bankMin.getX() || x == bankMax.getX()
				|| z == bankMin.getZ() || z == bankMax.getZ()
				|| y == bankMin.getY() || y == bankMax.getY());
	}

	public static void setupIfNeeded(MinecraftServer server) {
		restoreBoundsFromConfig();
		if (!EconomyConfig.spawnBankEnabled()) {
			return;
		}
		ServerLevel level = server.overworld();
		if (EconomyConfig.spawnBankBuilt() && bankOrigin != null && isCentralBankStructureIntact(level, bankOrigin)) {
			return;
		}
		clearAllKnownSites(level);
		removeExisting(level);
		EconomyConfig.setSpawnBankBuilt(false);
		buildAt(level, computeRemoteFlatOrigin(level));
	}

	public static void rebuild(MinecraftServer server) {
		rebuild(server, null);
	}

	/** anchor: komutu kullanan oyuncu — ayni Y, 4 blok onde; null ise config/spawn. */
	public static void rebuild(MinecraftServer server, net.minecraft.server.level.ServerPlayer anchor) {
		ServerLevel level = server.overworld();
		clearAllKnownSites(level);
		removeExisting(level);
		EconomyConfig.setSpawnBankBuilt(false);
		BlockPos origin = anchor != null ? computeOriginNearPlayer(anchor) : computeRemoteFlatOrigin(level);
		buildAt(level, origin);
	}

	public static void clearAllKnownSites(ServerLevel level) {
		restoreBoundsFromConfig();
		if (bankOrigin != null) {
			clearBuiltStructure(level, bankOrigin);
		}
		if (EconomyConfig.bankOriginStored()) {
			BlockPos cfg = new BlockPos(
					EconomyConfig.bankOriginX(), EconomyConfig.bankOriginY(), EconomyConfig.bankOriginZ());
			if (bankOrigin == null || !cfg.equals(bankOrigin)) {
				clearBuiltStructure(level, cfg);
			}
		}
	}

	/** Modun insa ettigi tum MB hacmini hava ile doldurur (eski yapi kalintisi birakmaz). */
	public static void clearBuiltStructure(ServerLevel level, BlockPos origin) {
		if (origin == null) {
			return;
		}
		clearRailApproach(level, origin);
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int x = -2; x <= BANK_WIDTH + 2; x++) {
			for (int z = -2; z <= BANK_DEPTH + 2; z++) {
				for (int y = -2; y <= BANK_HEIGHT + 2; y++) {
					level.setBlockAndUpdate(origin.offset(x, y, z), air);
				}
			}
		}
		BlockPos reserve = origin.offset(14, 1, 7);
		for (int x = -4; x <= 4; x++) {
			for (int z = -4; z <= 4; z++) {
				for (int y = -1; y <= 6; y++) {
					level.setBlockAndUpdate(reserve.offset(x, y, z), air);
				}
			}
		}
		BlockPos wing = origin.offset(11, 0, 5);
		for (int x = -1; x <= 8; x++) {
			for (int z = -1; z <= 6; z++) {
				for (int y = 0; y <= 6; y++) {
					level.setBlockAndUpdate(wing.offset(x, y, z), air);
				}
			}
		}
		for (int[] room : new int[][] { {2, 1, 2}, {5, 1, 2}, {14, 1, 8} }) {
			for (int x = 0; x < 4; x++) {
				for (int z = 0; z < 3; z++) {
					for (int y = 0; y <= 3; y++) {
						level.setBlockAndUpdate(origin.offset(room[0] + x, room[1] + y, room[2] + z), air);
					}
				}
			}
		}
		for (BlockPos depot : new BlockPos[] {
				origin.offset(3, 1, 3),
				origin.offset(15, 1, 9),
				origin.offset(6, 1, 3)
		}) {
			for (int y = 0; y <= 3; y++) {
				level.setBlockAndUpdate(depot.offset(0, y, 0), air);
				level.setBlockAndUpdate(depot.offset(0, y, 1), air);
			}
		}
	}

	private static void restoreBoundsFromConfig() {
		if (EconomyConfig.bankOriginStored()) {
			bankOrigin = new BlockPos(
					EconomyConfig.bankOriginX(), EconomyConfig.bankOriginY(), EconomyConfig.bankOriginZ());
			applyLayoutFromOrigin(bankOrigin);
		}
	}

	private static void applyLayoutFromOrigin(BlockPos origin) {
		bankMin = origin;
		bankMax = origin.offset(BANK_WIDTH - 1, BANK_HEIGHT - 1, BANK_DEPTH - 1);
		reserveCenter = origin.offset(14, 1, 7);
		reserveMin = reserveCenter.offset(-3, 0, -3);
		reserveMax = reserveCenter.offset(3, 4, 3);
		depotMarket = origin.offset(3, 1, 3);
		depotBlackMarket = origin.offset(15, 1, 9);
		depotPhysicalGold = origin.offset(6, 1, 3);
	}

	private static BlockPos computeBuildOrigin(ServerLevel level) {
		int x;
		int z;
		if (EconomyConfig.bankOriginStored()) {
			x = EconomyConfig.bankOriginX();
			z = EconomyConfig.bankOriginZ();
		} else {
			BlockPos spawn = level.getRespawnData().pos()
					.offset(EconomyConfig.spawnBankOffsetX(), 0, EconomyConfig.spawnBankOffsetZ());
			BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, spawn);
			x = surface.getX() - 9;
			z = surface.getZ() - 4;
		}
		return new BlockPos(x, resolvePlatformY(level, x, z), z);
	}

	private static BlockPos computeOriginNearPlayer(net.minecraft.server.level.ServerPlayer player) {
		BlockPos feet = player.blockPosition();
		double yawRad = Math.toRadians(player.getYRot());
		int dx = (int) Math.round(-Math.sin(yawRad));
		int dz = (int) Math.round(Math.cos(yawRad));
		if (dx == 0 && dz == 0) {
			dz = 1;
		}
		BlockPos front = feet.offset(dx * 4, 0, dz * 4);
		int x = front.getX() - 9;
		int z = front.getZ() - 4;
		return new BlockPos(x, resolvePlatformY((ServerLevel) player.level(), x, z), z);
	}

	private static int resolvePlatformY(ServerLevel level, int x, int z) {
		BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z));
		return surface.getY() + EconomyConfig.centralBankElevationBlocks();
	}

	private static void build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		buildAt(level, computeRemoteFlatOrigin(level));
	}

	/** Spawn'dan uzakta (config mesafesi) duz arazi arar. */
	public static BlockPos computeRemoteFlatOrigin(ServerLevel level) {
		BlockPos spawn = level.getRespawnData().pos();
		int dist = EconomyConfig.centralBankSpawnDistanceBlocks();
		int[][] dirs = { { dist, 0 }, { -dist, 0 }, { 0, dist }, { 0, -dist },
				{ dist, dist }, { dist, -dist }, { -dist, dist }, { -dist, -dist } };
		BlockPos best = null;
		int bestScore = Integer.MIN_VALUE;
		for (int[] d : dirs) {
			int cx = spawn.getX() + d[0];
			int cz = spawn.getZ() + d[1];
			BlockPos surface = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(cx, 0, cz));
			int flatScore = flatnessScore(level, surface.getX(), surface.getZ(), 8);
			if (flatScore > bestScore) {
				bestScore = flatScore;
				best = new BlockPos(surface.getX() - 9, surface.getY() + EconomyConfig.centralBankElevationBlocks(), surface.getZ() - 4);
			}
		}
		if (best != null) {
			return best;
		}
		return computeBuildOrigin(level);
	}

	private static int flatnessScore(ServerLevel level, int cx, int cz, int radius) {
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (int x = cx - radius; x <= cx + radius; x++) {
			for (int z = cz - radius; z <= cz + radius; z++) {
				int y = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z)).getY();
				minY = Math.min(minY, y);
				maxY = Math.max(maxY, y);
			}
		}
		return 100 - (maxY - minY) * 10;
	}

	/** MB ana yapisi (zemin + giris) yerinde mi? */
	public static boolean isCentralBankStructureIntact(ServerLevel level, BlockPos origin) {
		if (origin == null) {
			return false;
		}
		BlockState floor = Blocks.POLISHED_ANDESITE.defaultBlockState();
		BlockState trim = Blocks.GOLD_BLOCK.defaultBlockState();
		if (!level.getBlockState(origin.offset(9, 0, 5)).is(Blocks.POLISHED_ANDESITE)
				&& !level.getBlockState(origin.offset(9, 0, 5)).is(Blocks.GOLD_BLOCK)) {
			return false;
		}
		return level.getBlockState(origin).is(Blocks.STONE_BRICKS)
				|| level.getBlockState(origin.offset(BANK_WIDTH - 1, 1, 0)).is(Blocks.STONE_BRICKS);
	}

	private static void buildAt(ServerLevel level, BlockPos origin) {
		clearBuiltStructure(level, origin);

		buildElevatedSupports(level, origin);
		buildGrandStructure(level, origin);
		buildRailApproach(level, origin);
		BlockPos vaultCenter = origin.offset(14, 1, 7);
		buildGoldReserve(level, vaultCenter);
		buildFacilityDepots(level, origin);
		spawnBanker(level, origin.offset(9, 1, 6));
		spawnMasakOfficer(level, origin.offset(3, 1, 9));
		spawnExchangeBroker(level, origin.offset(12, 1, 9));
		spawnBlackMarketDealer(level, origin.offset(15, 1, 2));

		bankOrigin = origin;
		applyLayoutFromOrigin(origin);

		EconomyConfig.setBankOrigin(origin.getX(), origin.getY(), origin.getZ());
		EconomyConfig.setSpawnBankBuilt(true);
		EconomyConfig.save();
		McEconomyMod.LOGGER.info("Merkez Bankasi (genisletilmis) kuruldu: {}", origin);
	}

	/** Platform altinda yalnizca hava olan yerlere destek kolonlari. */
	private static void buildElevatedSupports(ServerLevel level, BlockPos origin) {
		BlockState pillar = Blocks.QUARTZ_PILLAR.defaultBlockState();
		BlockPos groundRef = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE,
				new BlockPos(origin.getX() + 9, 0, origin.getZ() + 5));
		int groundY = groundRef.getY();
		for (int x = 0; x < BANK_WIDTH; x += 4) {
			for (int z = 0; z < BANK_DEPTH; z += 4) {
				BlockPos top = origin.offset(x, -1, z);
				for (int y = top.getY() - 1; y > groundY; y--) {
					BlockPos col = new BlockPos(top.getX(), y, top.getZ());
					if (level.getBlockState(col).isAir()) {
						setIfClear(level, col, pillar);
					} else {
						break;
					}
				}
			}
		}
	}

	private static void clearRailApproach(ServerLevel level, BlockPos origin) {
		int railX = origin.getX() + 9;
		int endZ = origin.getZ();
		int startZ = endZ - RAIL_APPROACH_LENGTH;
		BlockPos groundRef = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE,
				new BlockPos(railX, 0, startZ));
		int startY = groundRef.getY() + 1;
		int endY = origin.getY();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int step = 0; step <= RAIL_APPROACH_LENGTH; step++) {
			int z = startZ + step;
			int y = startY + (step * Math.max(1, endY - startY)) / RAIL_APPROACH_LENGTH;
			for (int dx = -2; dx <= 2; dx++) {
				for (int dy = -1; dy <= 3; dy++) {
					level.setBlockAndUpdate(new BlockPos(railX + dx, y + dy, z), air);
				}
			}
		}
	}

	private static void buildRailApproach(ServerLevel level, BlockPos origin) {
		int railX = origin.getX() + 9;
		int endZ = origin.getZ();
		int startZ = endZ - RAIL_APPROACH_LENGTH;
		BlockPos groundRef = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE,
				new BlockPos(railX, 0, startZ));
		int startY = groundRef.getY() + 1;
		int endY = origin.getY();
		BlockState plank = Blocks.OAK_PLANKS.defaultBlockState();
		BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
		BlockState power = Blocks.REDSTONE_BLOCK.defaultBlockState();
		BlockState rail = Blocks.POWERED_RAIL.defaultBlockState()
				.setValue(net.minecraft.world.level.block.PoweredRailBlock.SHAPE, RailShape.NORTH_SOUTH);

		for (int step = 0; step <= RAIL_APPROACH_LENGTH; step++) {
			int z = startZ + step;
			int y = startY + (step * Math.max(1, endY - startY)) / RAIL_APPROACH_LENGTH;
			setIfClear(level, new BlockPos(railX - 1, y, z), plank);
			setIfClear(level, new BlockPos(railX + 1, y, z), plank);
			setIfClear(level, new BlockPos(railX - 1, y + 1, z), fence);
			setIfClear(level, new BlockPos(railX + 1, y + 1, z), fence);
			setIfClear(level, new BlockPos(railX, y - 1, z), power);
			setIfClear(level, new BlockPos(railX, y, z), rail);
		}
		setIfClear(level, new BlockPos(railX, startY - 1, startZ), Blocks.LECTERN.defaultBlockState());
		setIfClear(level, new BlockPos(railX, startY, startZ), Blocks.OAK_BUTTON.defaultBlockState());
	}

	private static void setIfClear(ServerLevel level, BlockPos pos, BlockState state) {
		BlockState existing = level.getBlockState(pos);
		if (existing.isAir() || existing.canBeReplaced()) {
			level.setBlockAndUpdate(pos, state);
		}
	}

	private static void buildGrandStructure(ServerLevel level, BlockPos origin) {
		BlockState foundation = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
		BlockState floorMain = Blocks.POLISHED_ANDESITE.defaultBlockState();
		BlockState floorAccent = Blocks.GOLD_BLOCK.defaultBlockState();
		BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
		BlockState pillar = Blocks.QUARTZ_PILLAR.defaultBlockState();
		BlockState trim = Blocks.GOLD_BLOCK.defaultBlockState();
		BlockState glass = Blocks.GLASS_PANE.defaultBlockState();
		BlockState roof = Blocks.DARK_PRISMARINE.defaultBlockState();
		BlockState roofTrim = Blocks.SEA_LANTERN.defaultBlockState();
		BlockState carpet = Blocks.RED_CARPET.defaultBlockState();

		for (int x = -1; x <= BANK_WIDTH; x++) {
			for (int z = -1; z <= BANK_DEPTH; z++) {
				BlockPos base = origin.offset(x, -1, z);
				boolean inFootprint = x >= 0 && x < BANK_WIDTH && z >= 0 && z < BANK_DEPTH;
				level.setBlockAndUpdate(base, inFootprint ? foundation : Blocks.COBBLESTONE.defaultBlockState());
				if (x == -1 || x == BANK_WIDTH || z == -1 || z == BANK_DEPTH) {
					level.setBlockAndUpdate(base.above(), Blocks.STONE_BRICK_STAIRS.defaultBlockState());
				}
			}
		}

		for (int x = 0; x < BANK_WIDTH; x++) {
			for (int z = 0; z < BANK_DEPTH; z++) {
				boolean aisle = x == 9 || z == 5;
				level.setBlockAndUpdate(origin.offset(x, 0, z), aisle ? floorAccent : floorMain);
			}
		}

		for (int y = 1; y <= 4; y++) {
			for (int x = 0; x < BANK_WIDTH; x++) {
				for (int z = 0; z < BANK_DEPTH; z++) {
					boolean shell = x == 0 || x == BANK_WIDTH - 1 || z == 0 || z == BANK_DEPTH - 1;
					boolean grandDoor = z == 0 && x >= 7 && x <= 11 && y <= 3;
					boolean window = shell && y >= 2 && y <= 3 && !grandDoor && (x + z + y) % 3 == 0;
					boolean column = (x == 0 || x == BANK_WIDTH - 1) && (z == 3 || z == BANK_DEPTH - 4) && y <= 3;
					if (grandDoor) {
						level.setBlockAndUpdate(origin.offset(x, y, z), y == 3 ? trim : Blocks.AIR.defaultBlockState());
						continue;
					}
					if (column) {
						level.setBlockAndUpdate(origin.offset(x, y, z), pillar);
						continue;
					}
					if (window) {
						level.setBlockAndUpdate(origin.offset(x, y, z), glass);
						continue;
					}
					if (shell && y <= 4) {
						level.setBlockAndUpdate(origin.offset(x, y, z), wall);
					} else {
						level.setBlockAndUpdate(origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
					}
				}
			}
		}

		for (int x = 0; x < BANK_WIDTH; x++) {
			for (int z = 0; z < BANK_DEPTH; z++) {
				boolean edge = x == 0 || x == BANK_WIDTH - 1 || z == 0 || z == BANK_DEPTH - 1;
				level.setBlockAndUpdate(origin.offset(x, 5, z), edge ? roofTrim : roof);
			}
		}

		for (int x = 7; x <= 11; x++) {
			level.setBlockAndUpdate(origin.offset(x, 0, 5), carpet);
		}
		level.setBlockAndUpdate(origin.offset(9, 1, 5), Blocks.LECTERN.defaultBlockState());
		level.setBlockAndUpdate(origin.offset(8, 2, 5), Blocks.LANTERN.defaultBlockState());
		level.setBlockAndUpdate(origin.offset(10, 2, 5), Blocks.LANTERN.defaultBlockState());
		level.setBlockAndUpdate(origin.offset(9, 3, 5), Blocks.END_ROD.defaultBlockState());
		level.setBlockAndUpdate(origin.offset(9, 0, 0), trim);
		level.setBlockAndUpdate(origin.offset(8, 0, 0), trim);
		level.setBlockAndUpdate(origin.offset(10, 0, 0), trim);

		buildVaultWingShell(level, origin.offset(11, 0, 5));
		buildDepotRooms(level, origin);
	}

	private static void buildVaultWingShell(ServerLevel level, BlockPos wingOrigin) {
		BlockState steel = Blocks.IRON_BLOCK.defaultBlockState();
		BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
		for (int x = 0; x <= 6; x++) {
			for (int z = 0; z <= 4; z++) {
				for (int y = 1; y <= 4; y++) {
					boolean shell = x == 0 || x == 6 || z == 0 || z == 4 || y == 4;
					BlockPos pos = wingOrigin.offset(x, y, z);
					if (shell) {
						level.setBlockAndUpdate(pos, (x == 0 || x == 6 || z == 0 || z == 4) && y < 4 ? steel : bedrock);
					} else if (y == 1) {
						level.setBlockAndUpdate(pos, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
					}
				}
			}
		}
		level.setBlockAndUpdate(wingOrigin.offset(3, 1, 0), Blocks.IRON_BARS.defaultBlockState());
	}

	private static void buildDepotRooms(ServerLevel level, BlockPos origin) {
		BlockState signGold = Blocks.GOLD_BLOCK.defaultBlockState();
		BlockState signRed = Blocks.REDSTONE_BLOCK.defaultBlockState();
		BlockState signIron = Blocks.IRON_BLOCK.defaultBlockState();
		paintRoom(level, origin.offset(2, 1, 2), 3, 2, signGold, "PIYASA");
		paintRoom(level, origin.offset(5, 1, 2), 3, 2, signIron, "ALTIN");
		paintRoom(level, origin.offset(14, 1, 8), 3, 2, signRed, "KARA");
	}

	private static void paintRoom(ServerLevel level, BlockPos corner, int w, int d, BlockState accent, String unused) {
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < d; z++) {
				level.setBlockAndUpdate(corner.offset(x, 0, z), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
				if (x == 0 || z == 0) {
					level.setBlockAndUpdate(corner.offset(x, 1, z), accent);
				}
			}
		}
	}

	private static void buildFacilityDepots(ServerLevel level, BlockPos origin) {
		depotMarket = origin.offset(3, 1, 3);
		depotBlackMarket = origin.offset(15, 1, 9);
		depotPhysicalGold = origin.offset(6, 1, 3);
		level.setBlockAndUpdate(depotMarket, Blocks.CHEST.defaultBlockState());
		level.setBlockAndUpdate(depotBlackMarket, Blocks.CHEST.defaultBlockState());
		level.setBlockAndUpdate(depotPhysicalGold, Blocks.CHEST.defaultBlockState());
	}

	/** Bedrock dis kabuk, demir (celik) ic duvar, ortada fiziksel altin blok rezervi. */
	private static void buildGoldReserve(ServerLevel level, BlockPos center) {
		BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
		BlockState steel = Blocks.IRON_BLOCK.defaultBlockState();
		BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();

		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				for (int y = 0; y <= 3; y++) {
					BlockPos pos = center.offset(x, y, z);
					boolean outerShell = Math.abs(x) == 2 || Math.abs(z) == 2 || y == 0 || y == 3;
					boolean innerSteel = Math.abs(x) == 1 || Math.abs(z) == 1 || y == 3;
					if (outerShell && (Math.abs(x) == 2 || Math.abs(z) == 2 || y == 0)) {
						level.setBlockAndUpdate(pos, bedrock);
					} else if (innerSteel && (Math.abs(x) == 1 || Math.abs(z) == 1 || y == 3)) {
						level.setBlockAndUpdate(pos, steel);
					} else if (Math.abs(x) <= 1 && Math.abs(z) <= 1 && y >= 1 && y <= 2) {
						level.setBlockAndUpdate(pos, gold);
					} else {
						level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		reserveCenter = center;
	}

	/** Celik kasa icindeki altin bloklari yeniden koyar (yapiyi silmez). */
	public static int refillGoldReserveVault(ServerLevel level) {
		if (reserveCenter == null) {
			restoreBoundsFromConfig();
		}
		if (reserveCenter == null) {
			return 0;
		}
		BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
		int placed = 0;
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				for (int y = 1; y <= 2; y++) {
					BlockPos pos = reserveCenter.offset(x, y, z);
					if (!level.getBlockState(pos).is(Blocks.GOLD_BLOCK)) {
						level.setBlockAndUpdate(pos, gold);
						placed++;
					}
				}
			}
		}
		return placed;
	}

	private static void spawnBanker(ServerLevel level, BlockPos pos) {
		Villager villager = EntityType.VILLAGER.create(
				level, null, pos, EntitySpawnReason.COMMAND, false, false);
		if (villager == null) {
			return;
		}
		villager.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		villager.setYRot(180.0F);
		villager.setXRot(0.0F);
		villager.setCustomName(Component.literal("§6§lMerkez Bankası"));
		villager.setCustomNameVisible(true);
		villager.setPersistenceRequired();
		villager.setInvulnerable(true);
		var registries = level.registryAccess();
		villager.setVillagerData(new VillagerData(
				registries.lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_TYPE).getOrThrow(VillagerType.PLAINS),
				registries.lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_PROFESSION).getOrThrow(VillagerProfession.LIBRARIAN),
				1));
		villager.addTag(NPC_TAG);
		level.addFreshEntity(villager);
	}

	private static void spawnMasakOfficer(ServerLevel level, BlockPos pos) {
		Villager villager = EntityType.VILLAGER.create(
				level, null, pos, EntitySpawnReason.COMMAND, false, false);
		if (villager == null) {
			return;
		}
		villager.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		villager.setYRot(90.0F);
		villager.setCustomName(Component.literal("§c§lMASAK Denetmeni"));
		villager.setCustomNameVisible(true);
		villager.setPersistenceRequired();
		villager.setInvulnerable(true);
		var registries = level.registryAccess();
		villager.setVillagerData(new VillagerData(
				registries.lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_TYPE).getOrThrow(VillagerType.PLAINS),
				registries.lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_PROFESSION).getOrThrow(VillagerProfession.CLERIC),
				1));
		villager.addTag(MASAK_NPC_TAG);
		level.addFreshEntity(villager);
	}

	private static void spawnExchangeBroker(ServerLevel level, BlockPos pos) {
		Villager villager = EntityType.VILLAGER.create(
				level, null, pos, EntitySpawnReason.COMMAND, false, false);
		if (villager == null) {
			return;
		}
		villager.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		villager.setYRot(0.0F);
		villager.setCustomName(Component.literal("§e§lBorsa Komisyoncusu"));
		villager.setCustomNameVisible(true);
		villager.setPersistenceRequired();
		villager.setInvulnerable(true);
		var registries = level.registryAccess();
		villager.setVillagerData(new VillagerData(
				registries.lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_TYPE).getOrThrow(VillagerType.PLAINS),
				registries.lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_PROFESSION).getOrThrow(VillagerProfession.CARTOGRAPHER),
				1));
		villager.addTag(EXCHANGE_NPC_TAG);
		level.addFreshEntity(villager);
	}

	private static void spawnBlackMarketDealer(ServerLevel level, BlockPos pos) {
		Villager villager = EntityType.VILLAGER.create(
				level, null, pos, EntitySpawnReason.COMMAND, false, false);
		if (villager == null) {
			return;
		}
		villager.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		villager.setYRot(270.0F);
		villager.setCustomName(Component.literal("§4§lKaraborsa Ajanı"));
		villager.setCustomNameVisible(true);
		villager.setPersistenceRequired();
		villager.setInvulnerable(true);
		var registries = level.registryAccess();
		villager.setVillagerData(new VillagerData(
				registries.lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_TYPE).getOrThrow(VillagerType.PLAINS),
				registries.lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_PROFESSION).getOrThrow(VillagerProfession.ARMORER),
				1));
		villager.addTag(BLACK_MARKET_NPC_TAG);
		level.addFreshEntity(villager);
	}

	private static void removeExisting(ServerLevel level) {
		for (Villager villager : level.getEntities(EntityTypeTest.forClass(Villager.class),
				entity -> entity.entityTags().contains(NPC_TAG)
						|| entity.entityTags().contains(MASAK_NPC_TAG)
						|| entity.entityTags().contains(EXCHANGE_NPC_TAG)
						|| entity.entityTags().contains(HEIST_GUARD_TAG)
						|| entity.entityTags().contains(BANK_GUARD_TAG)
						|| entity.entityTags().contains(BLACK_MARKET_NPC_TAG))) {
			villager.discard();
		}
	}
}
