package com.mceconomy.company;

import com.mceconomy.job.JobType;

import java.util.concurrent.ThreadLocalRandom;

public final class NpcNameGenerator {
	private static final String[] FIRST = {
			"Ahmet", "Mehmet", "Ayse", "Fatma", "Emre", "Zeynep", "Can", "Elif", "Burak", "Selin",
			"Murat", "Deniz", "Kerem", "Merve", "Okan", "Pinar", "Serkan", "Gizem", "Tolga", "Esra"
	};
	private static final String[] LAST = {
			"Yilmaz", "Kaya", "Demir", "Celik", "Sahin", "Yildiz", "Aydin", "Arslan", "Dogan", "Koc",
			"Polat", "Eren", "Aslan", "Kurt", "Ozkan", "Tekin", "Guler", "Acar", "Unal", "Bozkurt"
	};

	private NpcNameGenerator() {
	}

	public static String randomName() {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		return FIRST[rnd.nextInt(FIRST.length)] + " " + LAST[rnd.nextInt(LAST.length)];
	}

	public static JobType randomRole() {
		JobType[] roles = JobType.values();
		return roles[ThreadLocalRandom.current().nextInt(roles.length)];
	}

	public static String randomPitch(JobType role) {
		return switch (role.category()) {
			case MINING -> "Madencilik tecrubem var, sirketinize katkı saglarim.";
			case FARMING -> "Tarim ve hasat konusunda deneyimliyim.";
			case LUMBER -> "Orman islerinde hizli calisirim.";
			case FISHING -> "Balikcilik ve deniz urunlerinde ustayim.";
			case HUNTING -> "Av ve deri isleme konusunda iyiyim.";
			case TRADING -> "Ticaret ve pazarlik yapabilirim.";
			default -> "Is aramaktayim, sirketinize deger katabilirim.";
		};
	}
}
