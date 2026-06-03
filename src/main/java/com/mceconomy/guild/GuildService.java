package com.mceconomy.guild;

import com.mceconomy.McEconomyMod;
import com.mceconomy.company.Company;
import com.mceconomy.config.EconomyConfig;
import com.mceconomy.economy.CurrencyService;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.economy.TransactionType;
import com.mceconomy.persistence.repo.GuildRepository;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class GuildService {
	private final GuildRepository repository;
	private final CurrencyService currencyService;

	public GuildService(GuildRepository repository, CurrencyService currencyService) {
		this.repository = repository;
		this.currencyService = currencyService;
	}

	public Optional<Guild> guildForPlayer(UUID uuid) {
		try {
			return repository.findByMember(uuid);
		} catch (SQLException e) {
			return Optional.empty();
		}
	}

	public boolean create(ServerPlayer leader, String name) {
		if (name == null || name.length() < 3) {
			leader.sendSystemMessage(Component.literal("§cLonca adi en az 3 karakter."));
			return false;
		}
		try {
			if (repository.findByMember(leader.getUUID()).isPresent()) {
				leader.sendSystemMessage(Component.literal("§cZaten bir loncadasiniz."));
				return false;
			}
			if (repository.findByName(name).isPresent()) {
				leader.sendSystemMessage(Component.literal("§cBu isim alinmis."));
				return false;
			}
			long fee = EconomyConfig.guildCreationFeeMg();
			if (fee > 0 && !currencyService.withdraw(leader.getUUID(), fee, TransactionType.COMPANY)) {
				leader.sendSystemMessage(Component.literal("§cYetersiz bakiye."));
				return false;
			}
			Guild guild = Guild.create(name, leader.getUUID());
			repository.saveGuild(guild);
			repository.addMember(guild.id(), leader.getUUID(), leader.getName().getString(), GuildRole.LEADER);
			leader.sendSystemMessage(Component.literal("§aLonca kuruldu: §f" + name));
			return true;
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Lonca kurulamadi", e);
			return false;
		}
	}

	public boolean join(ServerPlayer player, String guildName) {
		try {
			if (repository.findByMember(player.getUUID()).isPresent()) {
				player.sendSystemMessage(Component.literal("§cZaten bir loncadasiniz."));
				return false;
			}
			Optional<Guild> guildOpt = repository.findByName(guildName);
			if (guildOpt.isEmpty()) {
				player.sendSystemMessage(Component.literal("§cLonca bulunamadi."));
				return false;
			}
			Guild guild = guildOpt.get();
			if (repository.memberCount(guild.id()) >= EconomyConfig.guildMaxMembers()) {
				player.sendSystemMessage(Component.literal("§cLonca dolu."));
				return false;
			}
			repository.addMember(guild.id(), player.getUUID(), player.getName().getString(), GuildRole.MEMBER);
			player.sendSystemMessage(Component.literal("§a" + guild.name() + " loncasina katildiniz."));
			return true;
		} catch (SQLException e) {
			return false;
		}
	}

	public boolean leave(ServerPlayer player) {
		try {
			Optional<Guild> guildOpt = repository.findByMember(player.getUUID());
			if (guildOpt.isEmpty()) {
				return false;
			}
			Guild guild = guildOpt.get();
			if (guild.leaderUuid().equals(player.getUUID())) {
				player.sendSystemMessage(Component.literal("§cLider ayrilamaz — once liderligi devredin veya loncayi kapatin."));
				return false;
			}
			repository.removeMember(player.getUUID());
			player.sendSystemMessage(Component.literal("§eLoncadan ayrildiniz."));
			return true;
		} catch (SQLException e) {
			return false;
		}
	}

	public boolean deposit(ServerPlayer player, long amountMg) {
		try {
			Optional<Guild> guildOpt = repository.findByMember(player.getUUID());
			if (guildOpt.isEmpty() || amountMg <= 0) {
				return false;
			}
			if (!currencyService.withdraw(player.getUUID(), amountMg, TransactionType.COMPANY)) {
				return false;
			}
			Guild guild = guildOpt.get();
			guild.deposit(amountMg);
			repository.saveGuild(guild);
			player.sendSystemMessage(Component.literal("§aLonca kasasina " + GoldStandard.formatMilligrams(amountMg) + " yatirildi."));
			return true;
		} catch (SQLException e) {
			return false;
		}
	}

	public boolean withdraw(ServerPlayer player, long amountMg) {
		try {
			Optional<Guild> guildOpt = repository.findByMember(player.getUUID());
			if (guildOpt.isEmpty() || amountMg <= 0) {
				return false;
			}
			Guild guild = guildOpt.get();
			if (!guild.leaderUuid().equals(player.getUUID())) {
				player.sendSystemMessage(Component.literal("§cSadece lider cekim yapabilir."));
				return false;
			}
			if (!guild.withdraw(amountMg)) {
				player.sendSystemMessage(Component.literal("§cLonca kasasi yetersiz."));
				return false;
			}
			repository.saveGuild(guild);
			currencyService.deposit(player.getUUID(), amountMg, TransactionType.COMPANY);
			player.sendSystemMessage(Component.literal("§aLonca kasasindan " + GoldStandard.formatMilligrams(amountMg) + " cekildi."));
			return true;
		} catch (SQLException e) {
			return false;
		}
	}

	public boolean startStrike(ServerPlayer leader, int minutes) {
		try {
			Optional<Guild> guildOpt = repository.findByMember(leader.getUUID());
			if (guildOpt.isEmpty()) {
				return false;
			}
			Guild guild = guildOpt.get();
			if (!guild.leaderUuid().equals(leader.getUUID())) {
				leader.sendSystemMessage(Component.literal("§cSadece lider grev baslatabilir."));
				return false;
			}
			int capped = Math.min(minutes, EconomyConfig.guildStrikeMaxMinutes());
			guild.setStrikeActive(true);
			guild.setStrikeUntil(System.currentTimeMillis() + capped * 60_000L);
			repository.saveGuild(guild);
			broadcastGuild(guild, "§4§l[GREV] §c" + guild.name() + " grevde! (" + capped + " dk)");
			broadcastCompanyOwners("§e[Sendika] §f" + guild.name() + " toplu pazarlik talep ediyor — maas artisi!");
			return true;
		} catch (SQLException e) {
			return false;
		}
	}

	public boolean setBargain(ServerPlayer leader, String message) {
		try {
			Optional<Guild> guildOpt = repository.findByMember(leader.getUUID());
			if (guildOpt.isEmpty()) {
				return false;
			}
			Guild guild = guildOpt.get();
			if (!guild.leaderUuid().equals(leader.getUUID())) {
				return false;
			}
			guild.setBargainMessage(message);
			repository.saveGuild(guild);
			broadcastCompanyOwners("§e[Sendika] §f" + guild.name() + ": §7" + message);
			leader.sendSystemMessage(Component.literal("§aPazarlik mesaji sirket sahiplerine iletildi."));
			return true;
		} catch (SQLException e) {
			return false;
		}
	}

	public void tickStrikes() {
		try {
			for (Guild guild : repository.loadAll()) {
				if (guild.strikeActive() && System.currentTimeMillis() >= guild.strikeUntil()) {
					guild.setStrikeActive(false);
					guild.setStrikeUntil(0);
					repository.saveGuild(guild);
				}
			}
		} catch (SQLException e) {
			McEconomyMod.LOGGER.error("Grev tick basarisiz", e);
		}
	}

	public boolean isMemberOnStrike(UUID playerUuid) {
		try {
			Optional<Guild> guildOpt = repository.findByMember(playerUuid);
			return guildOpt.isPresent() && guildOpt.get().strikeActive();
		} catch (SQLException e) {
			return false;
		}
	}

	public List<Guild> allGuilds() {
		try {
			return repository.loadAll();
		} catch (SQLException e) {
			return List.of();
		}
	}

	private void broadcastGuild(Guild guild, String message) {
		MinecraftServer server = McEconomyMod.getEconomyManager().server();
		if (server == null) {
			return;
		}
		try {
			for (GuildRepository.MemberRow member : repository.members(guild.id())) {
				ServerPlayer p = server.getPlayerList().getPlayer(member.playerUuid());
				if (p != null) {
					p.sendSystemMessage(Component.literal(message));
				}
			}
		} catch (SQLException ignored) {
		}
	}

	private void broadcastCompanyOwners(String message) {
		MinecraftServer server = McEconomyMod.getEconomyManager().server();
		if (server == null) {
			return;
		}
		for (Company company : McEconomyMod.getEconomyManager().companyManager().allCompanies()) {
			ServerPlayer owner = server.getPlayerList().getPlayer(company.ownerUuid());
			if (owner != null) {
				owner.sendSystemMessage(Component.literal(message));
			}
		}
	}
}
