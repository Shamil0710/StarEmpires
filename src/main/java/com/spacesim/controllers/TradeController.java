package com.spacesim.controllers;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.spacesim.components.*;
import com.spacesim.constants.Constants;

public class TradeController {
    private final ComponentMapper<InventoryComponent> im = ComponentMapper.getFor(InventoryComponent.class);
    private final ComponentMapper<MarketComponent> mm = ComponentMapper.getFor(MarketComponent.class);
    private final ComponentMapper<FactionComponent> fm = ComponentMapper.getFor(FactionComponent.class);

    // Пример профиля игрока
    public static class PlayerProfile { public float credits; public int[] cargo = new int[20]; }

    public boolean buy(Entity station, int itemId, int amount, PlayerProfile player) {
        if (station == null
                || player == null
                || player.cargo == null
                || !isValidBalance(player.credits)
                || amount <= 0
                || itemId < 0
                || itemId >= Constants.MAX_ITEMS
                || itemId >= player.cargo.length
                || !im.has(station)
                || !mm.has(station)) {
            return false;
        }

        InventoryComponent inv = im.get(station);
        MarketComponent market = mm.get(station);

        if (!isValidInventory(inv)
                || !isValidMarket(market)
                || !market.isTradable(itemId)
                || player.cargo == inv.stock
                || player.cargo[itemId] < 0) {
            return false;
        }

        float unitPrice = market.sellPrices[itemId];
        float cost = calculateTotalPrice(unitPrice, amount);
        float resultingBalance = player.credits - cost;

        if (isValidPrice(unitPrice)
                && isValidPrice(cost)
                && isValidBalance(resultingBalance)
                && resultingBalance < player.credits
                && inv.stock[itemId] >= amount
                && player.credits >= cost
                && player.cargo[itemId] <= Integer.MAX_VALUE - amount) {
            inv.stock[itemId] -= amount;
            player.credits = resultingBalance;
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
        if (!isValidTradeRequest(station, buyerInventory, itemId, amount)
                || buyerCredits == null
                || !isValidBalance(buyerCredits.credits)) {
            return false;
        }

        InventoryComponent stationInventory = im.get(station);
        float unitPrice = getEffectiveSellPrice(station, itemId, buyerReputation);
        float cost = calculateTotalPrice(unitPrice, amount);
        float resultingBalance = buyerCredits.credits - cost;

        if (!isValidPrice(unitPrice)
                || !isValidPrice(cost)
                || !isValidBalance(resultingBalance)
                || resultingBalance >= buyerCredits.credits
                || stationInventory.stock[itemId] < amount
                || buyerCredits.credits < cost
                || getFreeCapacity(buyerInventory) < amount) {
            return false;
        }

        stationInventory.stock[itemId] -= amount;
        buyerInventory.stock[itemId] += amount;
        buyerCredits.credits = resultingBalance;
        mm.get(station).isDirty = true;
        increaseReputation(station, buyerReputation);
        return true;
    }

    public boolean sellToStation(Entity station, InventoryComponent sellerInventory, int itemId, int amount, CreditAccount sellerCredits) {
        return sellToStation(station, sellerInventory, itemId, amount, sellerCredits, null);
    }

    public boolean sellToStation(Entity station, InventoryComponent sellerInventory, int itemId, int amount,
                                 CreditAccount sellerCredits, ReputationComponent sellerReputation) {
        if (!isValidTradeRequest(station, sellerInventory, itemId, amount)
                || sellerCredits == null
                || !isValidBalance(sellerCredits.credits)) {
            return false;
        }

        InventoryComponent stationInventory = im.get(station);
        float unitPrice = getEffectiveBuyPrice(station, itemId, sellerReputation);
        float revenue = calculateTotalPrice(unitPrice, amount);
        float resultingBalance = sellerCredits.credits + revenue;

        if (!isValidPrice(unitPrice)
                || !isValidPrice(revenue)
                || !isValidBalance(resultingBalance)
                || resultingBalance <= sellerCredits.credits
                || sellerInventory.stock[itemId] < amount
                || getFreeCapacity(stationInventory) < amount) {
            return false;
        }

        sellerInventory.stock[itemId] -= amount;
        stationInventory.stock[itemId] += amount;
        sellerCredits.credits = resultingBalance;
        mm.get(station).isDirty = true;
        increaseReputation(station, sellerReputation);
        return true;
    }

    public float getEffectiveSellPrice(Entity station, int itemId, ReputationComponent reputation) {
        if (!isValidPriceRequest(station, itemId)) {
            return Float.NaN;
        }
        MarketComponent market = mm.get(station);
        return market.sellPrices[itemId] * (1f - getReputationPriceBonus(station, reputation));
    }

    public float getEffectiveBuyPrice(Entity station, int itemId, ReputationComponent reputation) {
        if (!isValidPriceRequest(station, itemId)) {
            return Float.NaN;
        }
        MarketComponent market = mm.get(station);
        return market.buyPrices[itemId] * (1f + getReputationPriceBonus(station, reputation));
    }

    private float getReputationPriceBonus(Entity station, ReputationComponent reputation) {
        if (station == null || reputation == null || !fm.has(station)) {
            return 0f;
        }
        float reputationValue = reputation.getReputation(fm.get(station).factionId);
        if (!Float.isFinite(reputationValue)) {
            return 0f;
        }
        float normalized = Math.min(1f, Math.max(0f, reputationValue) / Constants.MAX_REPUTATION);
        return normalized * Constants.MAX_REPUTATION_PRICE_BONUS;
    }

    private void increaseReputation(Entity station, ReputationComponent reputation) {
        if (station != null && reputation != null && fm.has(station)) {
            reputation.addReputation(fm.get(station).factionId, Constants.REPUTATION_TRADE_GAIN);
        }
    }

    public int getFreeCapacity(InventoryComponent inventory) {
        return inventory == null ? 0 : inventory.getFreeCapacity();
    }

    public int getTotalStock(InventoryComponent inventory) {
        return inventory == null ? 0 : inventory.getTotalStock();
    }

    private boolean isValidTradeRequest(Entity station, InventoryComponent participantInventory, int itemId, int amount) {
        return station != null
                && participantInventory != null
                && im.has(station)
                && mm.has(station)
                && itemId >= 0
                && itemId < Constants.MAX_ITEMS
                && amount > 0
                && im.get(station) != participantInventory
                && isValidInventory(im.get(station))
                && isValidInventory(participantInventory)
                && isValidMarket(mm.get(station))
                && mm.get(station).isTradable(itemId);
    }

    private boolean isValidPriceRequest(Entity station, int itemId) {
        return station != null
                && itemId >= 0
                && itemId < Constants.MAX_ITEMS
                && mm.has(station)
                && isValidMarket(mm.get(station));
    }

    private boolean isValidInventory(InventoryComponent inventory) {
        if (inventory == null
                || inventory.stock == null
                || inventory.stock.length < Constants.MAX_ITEMS
                || inventory.capacity < 0) {
            return false;
        }
        for (int amount : inventory.stock) {
            if (amount < 0) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidMarket(MarketComponent market) {
        return market != null
                && market.sellPrices != null
                && market.buyPrices != null
                && market.sellPrices.length >= Constants.MAX_ITEMS
                && market.buyPrices.length >= Constants.MAX_ITEMS
                && market.targetStock != null
                && market.targetStock.length >= Constants.MAX_ITEMS
                && market.tradableItems != null
                && market.tradableItems.length >= Constants.MAX_ITEMS;
    }

    private boolean isValidBalance(float balance) {
        return Float.isFinite(balance) && balance >= 0f;
    }

    private boolean isValidPrice(float price) {
        return Float.isFinite(price) && price > 0f;
    }

    private float calculateTotalPrice(float unitPrice, int amount) {
        double total = (double) unitPrice * amount;
        if (!Double.isFinite(total) || total <= 0d || total > Float.MAX_VALUE) {
            return Float.NaN;
        }
        return (float) total;
    }

    public static class CreditAccount {
        public float credits;

        public CreditAccount(float credits) {
            if (!Float.isFinite(credits) || credits < 0f) {
                throw new IllegalArgumentException("Баланс должен быть конечным и неотрицательным");
            }
            this.credits = credits;
        }
    }
}
