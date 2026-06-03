package com.mceconomy.justice;

public enum ReportType {
	COMPLAINT,
	TIP_OFF;

	public String displayName() {
		return switch (this) {
			case COMPLAINT -> "Sikayet";
			case TIP_OFF -> "Ihbar";
		};
	}
}
