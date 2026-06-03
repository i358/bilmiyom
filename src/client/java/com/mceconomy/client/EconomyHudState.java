package com.mceconomy.client;

public final class EconomyHudState {
	private static long walletMg;
	private static long bankMg;
	private static long dirtyMg;
	private static boolean frozen;
	private static boolean blacklisted;
	private static String jobLabel = "-";

	private EconomyHudState() {
	}

	public static void update(long wallet, long bank, long dirty, boolean accountFrozen, boolean listed, String job) {
		walletMg = wallet;
		bankMg = bank;
		dirtyMg = dirty;
		frozen = accountFrozen;
		blacklisted = listed;
		jobLabel = job;
	}

	public static long walletMg() {
		return walletMg;
	}

	public static long bankMg() {
		return bankMg;
	}

	public static long dirtyMg() {
		return dirtyMg;
	}

	public static boolean frozen() {
		return frozen;
	}

	public static boolean blacklisted() {
		return blacklisted;
	}

	public static String jobLabel() {
		return jobLabel;
	}
}
