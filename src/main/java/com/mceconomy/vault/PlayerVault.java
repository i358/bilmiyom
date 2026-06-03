package com.mceconomy.vault;

import java.util.UUID;

/** Bir oyuncuya ait yer alti kasa kaydidir. */
public final class PlayerVault {
	private final UUID ownerUuid;
	private final int vaultIndex;
	private final int chestX;
	private final int chestY;
	private final int chestZ;
	private Double returnX;
	private Double returnY;
	private Double returnZ;
	private String returnDim;
	private final long createdAt;

	public PlayerVault(UUID ownerUuid, int vaultIndex, int chestX, int chestY, int chestZ,
			Double returnX, Double returnY, Double returnZ, String returnDim, long createdAt) {
		this.ownerUuid = ownerUuid;
		this.vaultIndex = vaultIndex;
		this.chestX = chestX;
		this.chestY = chestY;
		this.chestZ = chestZ;
		this.returnX = returnX;
		this.returnY = returnY;
		this.returnZ = returnZ;
		this.returnDim = returnDim;
		this.createdAt = createdAt;
	}

	public UUID ownerUuid() {
		return ownerUuid;
	}

	public int vaultIndex() {
		return vaultIndex;
	}

	public int chestX() {
		return chestX;
	}

	public int chestY() {
		return chestY;
	}

	public int chestZ() {
		return chestZ;
	}

	public Double returnX() {
		return returnX;
	}

	public Double returnY() {
		return returnY;
	}

	public Double returnZ() {
		return returnZ;
	}

	public String returnDim() {
		return returnDim;
	}

	public long createdAt() {
		return createdAt;
	}

	public boolean hasReturn() {
		return returnX != null && returnY != null && returnZ != null && returnDim != null;
	}

	public void setReturn(double x, double y, double z, String dim) {
		this.returnX = x;
		this.returnY = y;
		this.returnZ = z;
		this.returnDim = dim;
	}

	public void clearReturn() {
		this.returnX = null;
		this.returnY = null;
		this.returnZ = null;
		this.returnDim = null;
	}

	/** Verilen koordinat bu kasanin korunan bolgesinde mi (oda + duvarlar). */
	public boolean contains(int x, int y, int z) {
		return x >= chestX - 3 && x <= chestX + 3
				&& y >= chestY - 2 && y <= chestY + 4
				&& z >= chestZ - 3 && z <= chestZ + 3;
	}
}
