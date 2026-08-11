package com.spacesim.controllers;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.spacesim.components.*;
import com.spacesim.constants.Constants;

public class TradeController {
    private ComponentMapper<InventoryComponent> im = ComponentMapper.getFor(InventoryComponent.class);
    private ComponentMapper<MarketComponent> mm = ComponentMapper.getFor(MarketComponent.class);
    private ComponentMapper<FactionComponent> fm = ComponentMapper.getFor(FactionComponent.class);

    // Пример профиля игрока
    public static class PlayerProfile { public float credits; public int[] cargo = new int[20]; }

    public boolean buy(Entity station, int itemId, int amount, PlayerProfile player) {
        if (station == null
                || player == null
                || amount <= 0
                || itemId < 0
                || itemId >= Constants.MAX_ITEMS
                || itemId >= player.cargo.length
                || !im.has(station)
                || !mm.has(station)
                || !mm.get(station).isTradable(itemId)) {
            return false;
        }

        InventoryComponent inv = im.get(station);
        MarketComponent market = mm.get(station);

        float cost = market.sellPrices[itemId] * amount;

        if (inv.stock[itemId] >= amount && player.credits >= cost) {
            inv.stock[itemId] -= amount;
            player.credits -= cost;
            player.cargo[itemId] += amount;
            market.isDirty = true;
            return true;
        }
        return false;
    }

    public boolean buyFromStation(Entity station, InventoryComponent buyerInventory, int itemId, int amount, CreditAccount buyerCredits) {
        return buyFromStation(station, buyerInventory, itemId, amount, buyerCredits, null);
    }

    public boolean buyFromStation(Entity station, InventoryComponent buyerInventory, int itemId, int amount,
                                  CreditAccount buyerCredits, ReputationComponent buyerReputation) {
        if (!isValidTradeRequest(station, buyerInventory, itemId, amount) || buyerCredits == null) {
            return false;
        }

        InventoryComponent stationInventory = im.get(station);
        float cost = getEffectiveSellPrice(station, itemId, buyerReputation) * amount;

        if (stationInventory.stock[itemId] < amount || buyerCredits.credits < cost || getFreeCapacity(buyerInventory) < amount) {
            return false;
        }

        stationInventory.stock[itemId] -= amount;
        buyerInventory.stock[itemId] += amount;
        buyerCredits.credits -= cost;
        mm.get(station).isDirty = true;
        increaseReputation(station, buyerReputation);
        return true;
    }

    public boolean sellToStation(Entity station, InventoryComponent sellerInventory, int itemId, int amount, CreditAccount sellerCredits) {
        return sellToStation(station, sellerInventory, itemId, amount, sellerCredits, null);
    }

    public boolean sellToStation(Entity station, InventoryComponent sellerInventory, int itemId, int amount,
                                 CreditAccount sellerCredits, ReputationComponent sellerReputation) {
        if (!isValidTradeRequest(station, sellerInventory, itemId, amount) || sellerCredits == null) {
            return false;
        }

        InventoryComponent stationInventory = im.get(station);

        if (sellerInventory.stock[itemId] < amount || getFreeCapacity(stationInventory) < amount) {
            return false;
        }

        sellerInventory.stock[itemId] -= amount;
        stationInventory.stock[itemId] += amount;
        sellerCredits.credits += getEffectiveBuyPrice(station, itemId, sellerReputation) * amount;
        mm.get(station).isDirty = true;
        increaseReputation(station, sellerReputation);
        return true;
    }

    public float getEffectiveSellPrice(Entity station, int itemId, ReputationComponent reputation) {
        MarketComponent market = mm.get(station);
        return market.sellPrices[itemId] * (1f - getReputationPriceBonus(station, reputation));
    }

    public float getEffectiveBuyPrice(Entity station, int itemId, ReputationComponent reputation) {
        MarketComponent market = mm.get(station);
        return market.buyPrices[itemId] * (1f + getReputationPriceBonus(station, reputation));
    }

    private float getReputationPriceBonus(Entity station, ReputationComponent reputation) {
        if (station == null || reputation == null || !fm.has(station)) {
            return 0f;
        }
        float normalized = Math.max(0f, reputation.getReputation(fm.get(station).factionId)) / Constants.MAX_REPUTATION;
        return normalized * Constants.MAX_REPUTATION_PRICE_BONUS;
    }

    private void increaseReputation(Entity station, ReputationComponent reputation) {
        if (station != null && reputation != null && fm.has(station)) {
            reputation.addReputation(fm.get(station).factionId, Constants.REPUTATION_TRADE_GAIN);
        }
    }

    public int getFreeCapacity(InventoryComponent inventory) {
        return inventory.capacity - getTotalStock(inventory);
    }

    public int getTotalStock(InventoryComponent inventory) {
        int total = 0;
        for (int amount : inventory.stock) {
            total += amount;
        }
        return total;
    }

    private boolean isValidTradeRequest(Entity station, InventoryComponent participantInventory, int itemId, int amount) {
        return station != null
                && participantInventory != null
                && im.has(station)
                && mm.has(station)
                && itemId >= 0
                && itemId < Constants.MAX_ITEMS
                && mm.get(station).isTradable(itemId)
                && amount > 0;
    }

    public static class CreditAccount {
        public float credits;

        public CreditAccount(float credits) {
            this.credits = credits;
        }
    }
}
