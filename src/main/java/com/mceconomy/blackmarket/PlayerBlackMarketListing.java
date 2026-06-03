package com.mceconomy.blackmarket;

import java.util.UUID;

/** Oyuncunun karaborsaya koydugu ilan (stoklu). */
public final class PlayerBlackMarketListing {
	private long id;
	private final UUID sellerUuid;
	private final String sellerName;
	private final String itemId;
	private final String displayName;
	private final long priceMg;
	private int stock;
	private int stolenStock;

	public PlayerBlackMarketListing(long id, UUID sellerUuid, String sellerName, String itemId,
			String displayName, long priceMg, int stock) {
		this(id, sellerUuid, sellerName, itemId, displayName, priceMg, stock, 0);
	}

	public PlayerBlackMarketListing(long id, UUID sellerUuid, String sellerName, String itemId,
			String displayName, long priceMg, int stock, int stolenStock) {
		this.id = id;
		this.sellerUuid = sellerUuid;
		this.sellerName = sellerName;
		this.itemId = itemId;
		this.displayName = displayName;
		this.priceMg = priceMg;
		this.stock = stock;
		this.stolenStock = stolenStock;
	}

	public long id() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public UUID sellerUuid() {
		return sellerUuid;
	}

	public String sellerName() {
		return sellerName;
	}

	public String itemId() {
		return itemId;
	}

	public String displayName() {
		return displayName;
	}

	public long priceMg() {
		return priceMg;
	}

	public int stock() {
		return stock;
	}

	public void setStock(int stock) {
		this.stock = stock;
	}

	public int stolenStock() {
		return stolenStock;
	}

	public void setStolenStock(int stolenStock) {
		this.stolenStock = stolenStock;
	}

	public String catalogId() {
		return "player_" + id;
	}
}
