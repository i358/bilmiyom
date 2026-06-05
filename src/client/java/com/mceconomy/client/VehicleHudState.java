package com.mceconomy.client;

public final class VehicleHudState {
	private static double speed;
	private static double fuel = 100;
	private static String model = "";
	private static long lastServerUpdateMs;

	private VehicleHudState() {
	}

	public static void update(double speedBlocksPerTick, double fuelPct, String vehicleModel) {
		speed = speedBlocksPerTick;
		fuel = fuelPct;
		model = vehicleModel != null ? vehicleModel : "";
		lastServerUpdateMs = System.currentTimeMillis();
	}

	public static void clear() {
		speed = 0;
		fuel = 100;
		model = "";
		lastServerUpdateMs = 0;
	}

	public static boolean recentlyDriving() {
		return lastServerUpdateMs > 0 && System.currentTimeMillis() - lastServerUpdateMs < 3000;
	}

	public static double speed() {
		return speed;
	}

	public static double fuel() {
		return fuel;
	}

	public static String model() {
		return model;
	}
}
