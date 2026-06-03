package com.mceconomy.casino;

import com.mceconomy.economy.CurrencyService;
import com.mceconomy.economy.GoldStandard;
import com.mceconomy.economy.TransactionType;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Basit, sunucu-otoriteli kumarhane: yazi-tura, zar ve slot. Ev avantaji payout'lara gomulu. */
public final class CasinoService {
	private final CurrencyService currencyService;

	public CasinoService(CurrencyService currencyService) {
		this.currencyService = currencyService;
	}

	public record Result(boolean success, boolean won, long payoutMg, String message) {
	}

	/** betMg = bahis (dahili birim). game: "coinflip" | "dice" | "slot". choice oyuna gore. */
	public Result play(UUID player, String game, long betMg, String choice) {
		if (betMg <= 0) {
			return new Result(false, false, 0, "Gecersiz bahis miktari.");
		}
		if (!currencyService.withdraw(player, betMg, TransactionType.MARKET_SELL)) {
			return new Result(false, false, 0, "Yetersiz bakiye. Bahis: " + GoldStandard.formatMilligrams(betMg));
		}
		return switch (game == null ? "" : game.toLowerCase()) {
			case "coinflip" -> coinflip(player, betMg, choice);
			case "dice" -> dice(player, betMg, choice);
			case "slot" -> slot(player, betMg);
			default -> {
				currencyService.deposit(player, betMg, TransactionType.MARKET_SELL);
				yield new Result(false, false, 0, "Bilinmeyen oyun.");
			}
		};
	}

	private Result coinflip(UUID player, long betMg, String choice) {
		boolean pickHeads = !"tura".equalsIgnoreCase(choice);
		boolean heads = ThreadLocalRandom.current().nextBoolean();
		boolean won = pickHeads == heads;
		String face = heads ? "YAZI" : "TURA";
		if (won) {
			long payout = (long) (betMg * 1.95);
			currencyService.deposit(player, payout, TransactionType.QUEST_REWARD);
			return new Result(true, true, payout, "🪙 " + face + " geldi! Kazandiniz: " + GoldStandard.formatMilligrams(payout));
		}
		return new Result(true, false, 0, "🪙 " + face + " geldi. Kaybettiniz: " + GoldStandard.formatMilligrams(betMg));
	}

	private Result dice(UUID player, long betMg, String choice) {
		int guess;
		try {
			guess = Math.max(1, Math.min(6, Integer.parseInt(choice)));
		} catch (Exception e) {
			guess = 1;
		}
		int roll = ThreadLocalRandom.current().nextInt(1, 7);
		if (roll == guess) {
			long payout = (long) (betMg * 5.5);
			currencyService.deposit(player, payout, TransactionType.QUEST_REWARD);
			return new Result(true, true, payout, "🎲 Zar " + roll + " geldi! Bildiniz, kazandiniz: " + GoldStandard.formatMilligrams(payout));
		}
		return new Result(true, false, 0, "🎲 Zar " + roll + " geldi. Tahmininiz " + guess + ". Kaybettiniz: " + GoldStandard.formatMilligrams(betMg));
	}

	private Result slot(UUID player, long betMg) {
		String[] symbols = {"🍒", "🍋", "🔔", "⭐", "💎", "7️⃣"};
		int a = ThreadLocalRandom.current().nextInt(symbols.length);
		int b = ThreadLocalRandom.current().nextInt(symbols.length);
		int c = ThreadLocalRandom.current().nextInt(symbols.length);
		String reel = symbols[a] + " " + symbols[b] + " " + symbols[c];
		double mult;
		if (a == b && b == c) {
			mult = a == 5 ? 25.0 : a == 4 ? 12.0 : 8.0;
		} else if (a == b || b == c || a == c) {
			mult = 1.8;
		} else {
			mult = 0;
		}
		if (mult > 0) {
			long payout = (long) (betMg * mult);
			currencyService.deposit(player, payout, TransactionType.QUEST_REWARD);
			return new Result(true, true, payout, reel + " — Kazandiniz (" + mult + "x): " + GoldStandard.formatMilligrams(payout));
		}
		return new Result(true, false, 0, reel + " — Kaybettiniz: " + GoldStandard.formatMilligrams(betMg));
	}
}
