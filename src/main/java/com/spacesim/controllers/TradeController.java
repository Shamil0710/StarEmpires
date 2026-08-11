package com.spacesim.controllers;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.spacesim.components.*;

public class TradeController {
    private ComponentMapper<InventoryComponent> im = ComponentMapper.getFor(InventoryComponent.class);
    private ComponentMapper<MarketComponent> mm = ComponentMapper.getFor(MarketComponent.class);

    // Пример профиля игрока
    public static class PlayerProfile { public float credits; public int[] cargo = new int[20]; }

    public boolean buy(Entity station, int itemId, int amount, PlayerProfile player) {
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
}