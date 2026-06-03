package com.mceconomy.news;

public record EconomyBulletin(
		long id,
		String category,
		String headline,
		String body,
		long valueMg,
		long createdAt
) {
}
