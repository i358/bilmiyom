package com.mceconomy.facility;

public enum FacilityType {
	MARKET("market", "Piyasa Deposu"),
	BLACK_MARKET("black_market", "Karaborsa Deposu"),
	PHYSICAL_GOLD("physical_gold", "Fiziksel Altin Kasasi");

	private final String id;
	private final String displayName;

	FacilityType(String id, String displayName) {
		this.id = id;
		this.displayName = displayName;
	}

	public String id() {
		return id;
	}

	public String displayName() {
		return displayName;
	}
}
