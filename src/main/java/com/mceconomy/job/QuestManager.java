package com.mceconomy.job;

import com.mceconomy.McEconomyMod;
import com.mceconomy.company.Company;
import com.mceconomy.company.CompanyManager;
import com.mceconomy.company.CompanyProductPipeline;
import com.mceconomy.company.PlayerEmployment;
import com.mceconomy.company.PlayerEmploymentService;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.market.Commodity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class QuestManager {
	public enum QuestType {
		DELIVER_ITEM,
		KILL_MOB,
		CRAFT_ITEM
	}

	public record ActiveQuest(
			int id,
			UUID playerUuid,
			QuestType type,
			String target,
			int required,
			int progress,
			long reward,
			String title,
			int companyId
	) {
		public ActiveQuest withProgress(int newProgress) {
			return new ActiveQuest(id, playerUuid, type, target, required, newProgress, reward, title, companyId);
		}

		public boolean isComplete() {
			return progress >= required;
		}

		public boolean isCompanyQuest() {
			return companyId >= 0;
		}
	}

	private final Map<UUID, ActiveQuest> activeQuests = new HashMap<>();
	private int nextId = 1;
	private final JobManager jobManager;
	private PlayerEmploymentService playerEmploymentService;
	private CompanyProductPipeline companyProductPipeline;
	private CompanyManager companyManager;

	public QuestManager(JobManager jobManager) {
		this.jobManager = jobManager;
	}

	public void bindEmployment(PlayerEmploymentService playerEmploymentService) {
		this.playerEmploymentService = playerEmploymentService;
	}

	public void bindCompanyWork(CompanyProductPipeline companyProductPipeline, CompanyManager companyManager) {
		this.companyProductPipeline = companyProductPipeline;
		this.companyManager = companyManager;
	}

	public ActiveQuest assignRandomQuest(UUID player, JobType jobType, ServerPlayer online) {
		int companyId = -1;
		String prefix = "";
		JobType effectiveRole = jobType;
		if (playerEmploymentService != null) {
			Optional<PlayerEmployment> employment = playerEmploymentService.employmentForPlayer(player);
			if (employment.isPresent()) {
				companyId = employment.get().companyId();
				JobType employmentRole = JobType.fromString(employment.get().roleId());
				if (employmentRole != null) {
					effectiveRole = employmentRole;
				}
				Company company = findCompany(companyId);
				prefix = company != null ? "[Sirket: " + company.name() + "] " : "[Sirket] ";
			}
		}
		if (effectiveRole == null) {
			return null;
		}
		return assignRandomQuest(player, effectiveRole, online, companyId, prefix);
	}

	public ActiveQuest assignRandomQuest(UUID player, JobType jobType) {
		return assignRandomQuest(player, jobType, null);
	}

	private ActiveQuest assignRandomQuest(UUID player, JobType jobType, ServerPlayer online, int companyId,
			String titlePrefix) {
		QuestPool.QuestTemplate template = QuestPool.randomForJob(jobType);
		ActiveQuest quest = new ActiveQuest(
				nextId++,
				player,
				template.type(),
				template.target(),
				template.required(),
				0,
				template.rewardMg(),
				titlePrefix + template.title(),
				companyId
		);
		activeQuests.put(player, quest);
		if (online != null) {
			JobKitService.giveKit(online, jobType);
		}
		return quest;
	}

	public ActiveQuest assignQuest(UUID player, QuestType type, String target, int required, long reward, String title) {
		ActiveQuest quest = new ActiveQuest(nextId++, player, type, target, required, 0, reward, title, -1);
		activeQuests.put(player, quest);
		return quest;
	}

	public ActiveQuest getQuest(UUID player) {
		return activeQuests.get(player);
	}

	public boolean cancelQuest(UUID player) {
		return activeQuests.remove(player) != null;
	}

	public boolean cancelQuest(ServerPlayer player) {
		if (player == null) {
			return false;
		}
		if (activeQuests.remove(player.getUUID()) != null) {
			JobKitService.reclaimKit(player);
			return true;
		}
		return false;
	}

	public boolean completeQuest(ServerPlayer player) {
		ActiveQuest quest = activeQuests.get(player.getUUID());
		if (quest == null) {
			return false;
		}
		if (quest.isCompanyQuest()) {
			return completeCompanyQuest(player, quest);
		}
		if (quest.type() == QuestType.DELIVER_ITEM) {
			if (!deliverItems(player, quest)) {
				return false;
			}
		} else if (!quest.isComplete()) {
			return false;
		}
		jobManager.calculateReward(player.getUUID(), quest.reward());
		JobKitService.reclaimKit(player);
		activeQuests.remove(player.getUUID());
		return true;
	}

	private boolean completeCompanyQuest(ServerPlayer player, ActiveQuest quest) {
		if (companyProductPipeline == null || companyManager == null) {
			return false;
		}
		Company company = findCompany(quest.companyId());
		if (company == null) {
			return false;
		}
		JobType role = resolveEmploymentRole(player.getUUID(), quest.companyId());
		if (role == null) {
			return false;
		}
		if (quest.type() == QuestType.DELIVER_ITEM) {
			if (!deliverItems(player, quest)) {
				return false;
			}
			Commodity commodity = Commodity.fromId(quest.target());
			if (commodity == null) {
				return false;
			}
			try {
				MinecraftServer server = McEconomyMod.getEconomyManager().server();
				companyProductPipeline.processDelivery(server, company, player.getName().getString(), role,
						commodity, quest.required());
				companyManager.saveCompany(company);
			} catch (SQLException e) {
				McEconomyMod.LOGGER.error("Sirket gorevi teslimi basarisiz", e);
				return false;
			}
		} else {
			if (!quest.isComplete()) {
				return false;
			}
			Commodity commodity = pickCompanyQuestPayout(role);
			if (commodity != null) {
				try {
					int amount = Math.max(1, quest.required() / 2);
					MinecraftServer server = McEconomyMod.getEconomyManager().server();
					companyProductPipeline.processDelivery(server, company, player.getName().getString(),
							role, commodity, amount);
					companyManager.saveCompany(company);
				} catch (SQLException e) {
					McEconomyMod.LOGGER.error("Sirket av gorevi basarisiz", e);
					return false;
				}
			}
		}
		long playerPay = (long) (quest.reward() * EconomyConfig.employedQuestPlayerPayShare());
		if (playerPay > 0) {
			jobManager.calculateReward(player.getUUID(), playerPay);
		}
		JobKitService.reclaimKit(player);
		activeQuests.remove(player.getUUID());
		notifyCompanyOwner(McEconomyMod.getEconomyManager().server(), company,
				player.getName().getString() + " sirket gorevini tamamladi: " + quest.title());
		player.sendSystemMessage(Component.literal(
				"§a[Sirket Gorevi] §fUretim sirkete aktarildi. Sizin pay: "
						+ GoldStandard.formatMilligrams(playerPay)));
		return true;
	}

	private Commodity pickCompanyQuestPayout(JobType role) {
		if (role.category() == com.mceconomy.job.JobCategory.HUNTING) {
			return Commodity.randomHuntingMeat();
		}
		return Commodity.randomForCategory(role.category());
	}

	private JobType resolveEmploymentRole(UUID playerUuid, int companyId) {
		if (playerEmploymentService == null) {
			return null;
		}
		return playerEmploymentService.employmentForPlayer(playerUuid)
				.filter(e -> e.companyId() == companyId)
				.map(e -> JobType.fromString(e.roleId()))
				.orElse(null);
	}

	private Company findCompany(int companyId) {
		if (companyManager == null) {
			return null;
		}
		return companyManager.allCompanies().stream()
				.filter(c -> c.id() == companyId)
				.findFirst()
				.orElse(null);
	}

	private void notifyCompanyOwner(MinecraftServer server, Company company, String message) {
		if (server == null) {
			return;
		}
		var owner = server.getPlayerList().getPlayer(company.ownerUuid());
		if (owner != null) {
			owner.sendSystemMessage(Component.literal("§e[Sirket] §f" + message));
		}
	}

	private boolean deliverItems(ServerPlayer player, ActiveQuest quest) {
		Commodity commodity = Commodity.fromId(quest.target());
		if (commodity == null) {
			return false;
		}
		int available = countItems(player, commodity.item());
		if (available < quest.required()) {
			return false;
		}
		removeItems(player, commodity.item(), quest.required());
		return true;
	}

	public void onMobKill(UUID player, EntityType<?> entityType) {
		ActiveQuest quest = activeQuests.get(player);
		if (quest == null || quest.type() != QuestType.KILL_MOB) {
			return;
		}
		String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType).toString();
		if (quest.target().equalsIgnoreCase(entityId)) {
			activeQuests.put(player, quest.withProgress(quest.progress() + 1));
		}
	}

	public void onCraft(UUID player, Item item) {
		ActiveQuest quest = activeQuests.get(player);
		if (quest == null || quest.type() != QuestType.CRAFT_ITEM) {
			return;
		}
		Commodity commodity = Commodity.fromId(quest.target());
		if (commodity != null && commodity.item() == item) {
			activeQuests.put(player, quest.withProgress(quest.progress() + 1));
		}
	}

	private static int countItems(ServerPlayer player, Item item) {
		int total = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item) && !JobItemTags.isJobLoan(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static void removeItems(ServerPlayer player, Item item, int amount) {
		int remaining = amount;
		for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item) && !JobItemTags.isJobLoan(stack)) {
				int take = Math.min(stack.getCount(), remaining);
				stack.shrink(take);
				remaining -= take;
			}
		}
	}
}
