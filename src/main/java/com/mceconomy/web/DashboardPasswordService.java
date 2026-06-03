package com.mceconomy.web;

import com.mceconomy.player.PlayerEconomyProfile;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

public final class DashboardPasswordService {
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int ITERATIONS = 120_000;
	private static final int KEY_LENGTH = 256;

	private DashboardPasswordService() {
	}

	public static void setPassword(PlayerEconomyProfile profile, String password) {
		if (password == null || password.length() < 4) {
			throw new IllegalArgumentException("short");
		}
		byte[] salt = new byte[16];
		RANDOM.nextBytes(salt);
		String hash = hash(password, salt);
		profile.setDashboardPasswordSalt(Base64.getEncoder().encodeToString(salt));
		profile.setDashboardPasswordHash(hash);
	}

	public static boolean verify(PlayerEconomyProfile profile, String password) {
		if (profile.dashboardPasswordHash() == null || profile.dashboardPasswordSalt() == null) {
			return false;
		}
		byte[] salt = Base64.getDecoder().decode(profile.dashboardPasswordSalt());
		return hash(password, salt).equals(profile.dashboardPasswordHash());
	}

	public static boolean hasPassword(PlayerEconomyProfile profile) {
		return profile.dashboardPasswordHash() != null && !profile.dashboardPasswordHash().isBlank();
	}

	private static String hash(String password, byte[] salt) {
		try {
			PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
			SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
			return Base64.getEncoder().encodeToString(factory.generateSecret(spec).getEncoded());
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			throw new IllegalStateException(e);
		}
	}
}
