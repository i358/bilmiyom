package com.mceconomy.justice;

import com.mceconomy.McEconomyMod;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.persistence.repo.PrisonRepository;
import com.mceconomy.player.PlayerEconomyProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Merkezi hapishane: bedrock hucreler, sureli hapis, otomatik tahliye. */
public final class PrisonService {
	private static final int BASE_X = 3_000_000;
	private static final int BASE_Y = -60;
	private static final int BASE_Z = 0;
	private static final int CELL_SPACING = 14;

	public static int mapAnchorX() {
		return BASE_X;
	}

	public static int mapAnchorY() {
		return BASE_Y;
	}

	public static int mapAnchorZ() {
		return BASE_Z;
	}

	private final PrisonRepository repository;
	private final Map<UUID, PrisonSentence> active = new HashMap<>();
	private final MinecraftServer server;
	private int nextCellIndex;

	public PrisonService(PrisonRepository repository, MinecraftServer server) {
		this.repository = repository;
		this.server = server;
	}

	public void load() throws SQLException {
		active.clear();
		for (PrisonSentence sentence : repository.loadActive()) {
			if (sentence.isActiveNow()) {
				active.put(sentence.playerUuid(), sentence);
				nextCellIndex = Math.max(nextCellIndex, sentence.cellIndex() + 1);
			} else {
				releaseInternal(sentence, false);
			}
		}
	}

	public boolean isJailed(UUID uuid) {
		PrisonSentence s = active.get(uuid);
		return s != null && s.isActiveNow();
	}

	public Optional<PrisonSentence> sentenceFor(UUID uuid) {
		return Optional.ofNullable(active.get(uuid));
	}

	public boolean imprison(ServerPlayer player, int minutes, String reason, String adminName) throws SQLException {
		if (minutes <= 0 || isJailed(player.getUUID())) {
			return false;
		}
		int cell = nextCellIndex++;
		long now = System.currentTimeMillis();
		long releaseAt = now + minutes * 60_000L;
		PrisonSentence sentence = new PrisonSentence(
				0, player.getUUID(), player.getName().getString(), reason, adminName,
				now, releaseAt, true,
				player.getX(), player.getY(), player.getZ(),
				player.level().dimension().identifier().toString(),
				cell);
		long id = repository.insert(sentence);
		sentence = new PrisonSentence(id, sentence.playerUuid(), sentence.playerName(), sentence.reason(),
				sentence.sentencedBy(), sentence.jailedAt(), sentence.releaseAt(), true,
				sentence.returnX(), sentence.returnY(), sentence.returnZ(), sentence.returnDimension(), cell);
		active.put(player.getUUID(), sentence);
		applyJailState(player, sentence);
		broadcast("§4[Hapishane] §c" + player.getName().getString() + " §f"
				+ minutes + " dakika hapse gonderildi. Sebep: " + reason);
		return true;
	}

	public boolean imprisonByName(String playerName, int minutes, String reason, String adminName) throws SQLException {
		UUID uuid = com.mceconomy.command.BalanceCommand.findPlayerUuid(playerName);
		if (uuid == null) {
			return false;
		}
		if (isJailed(uuid)) {
			return false;
		}
		ServerPlayer online = server.getPlayerList().getPlayer(uuid);
		if (online != null) {
			return imprison(online, minutes, reason, adminName);
		}
		PlayerEconomyProfile profile = McEconomyMod.getEconomyManager().profiles().get(uuid);
		if (profile == null) {
			return false;
		}
		int cell = nextCellIndex++;
		long now = System.currentTimeMillis();
		PrisonSentence sentence = new PrisonSentence(
				0, uuid, profile.name(), reason, adminName, now, now + minutes * 60_000L, true,
				null, null, null, null, cell);
		long id = repository.insert(sentence);
		sentence = new PrisonSentence(id, sentence.playerUuid(), sentence.playerName(), sentence.reason(),
				sentence.sentencedBy(), sentence.jailedAt(), sentence.releaseAt(), true,
				null, null, null, null, cell);
		active.put(uuid, sentence);
		profile.setAccountFrozen(true);
		return true;
	}

	public boolean release(UUID uuid) throws SQLException {
		PrisonSentence sentence = active.get(uuid);
		if (sentence == null) {
			return false;
		}
		releaseInternal(sentence, true);
		ServerPlayer player = server.getPlayerList().getPlayer(uuid);
		if (player != null) {
			player.sendSystemMessage(Component.literal("§a[Hapishane] §fTahliye edildiniz."));
		}
		return true;
	}

	public void tick() {
		long now = System.currentTimeMillis();
		for (PrisonSentence sentence : new HashMap<>(active).values()) {
			if (now >= sentence.releaseAt()) {
				try {
					releaseInternal(sentence, true);
					ServerPlayer player = server.getPlayerList().getPlayer(sentence.playerUuid());
					if (player != null) {
						player.sendSystemMessage(Component.literal(
								"§a[Hapishane] §fCeza suresi doldu, serbest birakildiniz."));
					}
				} catch (SQLException e) {
					McEconomyMod.LOGGER.error("Tahliye basarisiz", e);
				}
			}
		}
	}

	public void onPlayerJoin(ServerPlayer player) {
		PrisonSentence sentence = active.get(player.getUUID());
		if (sentence != null && sentence.isActiveNow()) {
			applyJailState(player, sentence);
		}
	}

	public java.util.Collection<PrisonSentence> activeSentences() {
		return active.values();
	}

	public int maxCellIndexForReset() {
		return Math.max(0, nextCellIndex);
	}

	public void clearAllCells(ServerLevel level) {
		for (int i = 0; i <= maxCellIndexForReset(); i++) {
			clearCellBlocks(level, i);
		}
	}

	private void clearCellBlocks(ServerLevel level, int cellIndex) {
		int cx = cellX(cellIndex);
		int cz = cellZ(cellIndex);
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				for (int y = 0; y <= 4; y++) {
					level.setBlockAndUpdate(new BlockPos(cx + x, BASE_Y + y, cz + z),
							Blocks.AIR.defaultBlockState());
				}
			}
		}
	}

	public boolean containsBlock(int x, int y, int z) {
		for (PrisonSentence s : active.values()) {
			int cx = cellX(s.cellIndex());
			int cz = cellZ(s.cellIndex());
			if (x >= cx - 3 && x <= cx + 3 && z >= cz - 3 && z <= cz + 3
					&& y >= BASE_Y - 1 && y <= BASE_Y + 5) {
				return true;
			}
		}
		return false;
	}

	private void applyJailState(ServerPlayer player, PrisonSentence sentence) {
		PlayerEconomyProfile profile = McEconomyMod.getEconomyManager().profiles().get(player.getUUID());
		if (profile != null) {
			profile.setAccountFrozen(true);
		}
		ServerLevel level = server.overworld();
		buildCell(level, sentence.cellIndex());
		double tx = cellX(sentence.cellIndex()) + 0.5;
		double tz = cellZ(sentence.cellIndex()) + 0.5;
		player.teleportTo(level, tx, BASE_Y + 1, tz, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
		player.sendSystemMessage(Component.literal(
				"§4§l[HAPISHANE] §cHukum giymissiniz. Kalan: "
						+ formatRemaining(sentence.remainingMs()) + "\n§7Sebep: " + sentence.reason()));
	}

	private void releaseInternal(PrisonSentence sentence, boolean teleport) throws SQLException {
		active.remove(sentence.playerUuid());
		PrisonSentence released = new PrisonSentence(sentence.id(), sentence.playerUuid(), sentence.playerName(),
				sentence.reason(), sentence.sentencedBy(), sentence.jailedAt(), sentence.releaseAt(), false,
				sentence.returnX(), sentence.returnY(), sentence.returnZ(), sentence.returnDimension(),
				sentence.cellIndex());
		repository.update(released);
		PlayerEconomyProfile profile = McEconomyMod.getEconomyManager().profiles().get(sentence.playerUuid());
		if (profile != null && !profile.blacklisted()) {
			profile.setAccountFrozen(false);
		}
		if (!teleport) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(sentence.playerUuid());
		if (player == null || sentence.returnX() == null) {
			return;
		}
		ServerLevel level = server.overworld();
		player.teleportTo(level, sentence.returnX(), sentence.returnY(), sentence.returnZ(),
				java.util.Set.of(), player.getYRot(), player.getXRot(), false);
	}

	private void buildCell(ServerLevel level, int cellIndex) {
		int cx = cellX(cellIndex);
		int cz = cellZ(cellIndex);
		BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
		BlockState iron = Blocks.IRON_BARS.defaultBlockState();
		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				for (int y = 0; y <= 4; y++) {
					BlockPos pos = new BlockPos(cx + x, BASE_Y + y, cz + z);
					boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2 || y == 0 || y == 4;
					if (wall) {
						level.setBlockAndUpdate(pos, y == 0 || y == 4 ? bedrock : iron);
					} else {
						level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		level.setBlockAndUpdate(new BlockPos(cx, BASE_Y, cz), Blocks.RED_BED.defaultBlockState());
	}

	private int cellX(int index) {
		return BASE_X + index * CELL_SPACING;
	}

	private int cellZ(int index) {
		return BASE_Z;
	}

	private static String formatRemaining(long ms) {
		long min = ms / 60_000;
		long sec = (ms % 60_000) / 1000;
		return min + " dk " + sec + " sn";
	}

	private void broadcast(String msg) {
		server.getPlayerList().broadcastSystemMessage(Component.literal(msg), false);
	}
}
