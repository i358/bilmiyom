package com.mceconomy.job;

public enum JobType {
	MINER("madenci", "Madenci", JobCategory.MINING),
	FARMER("ciftci", "Ciftci", JobCategory.FARMING),
	LUMBERJACK("ormanci", "Ormanci", JobCategory.LUMBER),
	FISHER("balikci", "Balikci", JobCategory.FISHING),
	HUNTER("avci", "Avcı", JobCategory.HUNTING),
	SMITH("demirci", "Demirci", JobCategory.MINING),
	RANCHER("coban", "Coban", JobCategory.FARMING),
	TRADER("tuccar", "Tuccar", JobCategory.TRADING),
	BUILDER("insaatci", "Insaatci", JobCategory.GENERAL);

	private final String id;
	private final String displayName;
	private final JobCategory category;

	JobType(String id, String displayName, JobCategory category) {
		this.id = id;
		this.displayName = displayName;
		this.category = category;
	}

	public String id() {
		return id;
	}

	public String displayName() {
		return displayName;
	}

	public JobCategory category() {
		return category;
	}

	public static JobType fromString(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		for (JobType type : values()) {
			if (type.name().equalsIgnoreCase(value) || type.id.equalsIgnoreCase(value)) {
				return type;
			}
		}
		return null;
	}
}
