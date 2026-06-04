package com.mceconomy.client;

public final class VehicleHudState {
	private static double speed;
	private static double fuel = 100;
	private static String model = "";

	private VehicleHudState() {
	}

	public static void update(double speedKmh, double fuelPct, String vehicleModel) {
		speed = speedKmh;
		fuel = fuelPct;
		model = vehicleModel != null ? vehicleModel : "";
	}

	public static void clear() {
		speed = 0;
		fuel = 100;
		model = "";
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
