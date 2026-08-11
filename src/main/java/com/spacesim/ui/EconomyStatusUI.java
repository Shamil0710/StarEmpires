package com.spacesim.ui;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.ReputationComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.constants.Constants;

public class EconomyStatusUI extends Table {
    private final Skin skin;
    private final Label contentLabel;

    public EconomyStatusUI(Skin skin) {
        this.skin = skin;
        this.contentLabel = new Label("", skin);

        setFillParent(true);
        top().right();
        pad(10);
        add(new Label("Экономика", skin)).right().row();
        add(contentLabel).right();
    }

    public void update(Iterable<Entity> entities) {
        StringBuilder text = new StringBuilder();
        int stationIndex = 1;
        int fleetIndex = 1;

        for (Entity entity : entities) {
            InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
            MarketComponent market = entity.getComponent(MarketComponent.class);
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            TradeAIComponent tradeAI = entity.getComponent(TradeAIComponent.class);
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            ReputationComponent reputation = entity.getComponent(ReputationComponent.class);

            if (inventory == null) {
                continue;
            }

            if (market != null) {
                appendStation(text, stationIndex++, inventory, market, transform, faction);
            } else if (tradeAI != null) {
                appendFleet(text, fleetIndex++, inventory, tradeAI, transform, reputation);
            }
        }

        contentLabel.setText(text.length() == 0 ? "Нет экономических сущностей" : text.toString());
    }

    private void appendStation(StringBuilder text, int stationIndex, InventoryComponent inventory,
                               MarketComponent market, TransformComponent transform, FactionComponent faction) {
        text.append("Станция ").append(stationIndex).append(formatPosition(transform)).append("\n");
        text.append("  Фракция: ").append(faction == null ? "Нет" : faction.getFactionName()).append("\n");
        appendItemLine(text, inventory, market, Constants.ITEM_FOOD);
        appendItemLine(text, inventory, market, Constants.ITEM_STEEL);
        text.append("\n");
    }

    private void appendFleet(StringBuilder text, int fleetIndex, InventoryComponent inventory,
                             TradeAIComponent tradeAI, TransformComponent transform, ReputationComponent reputation) {
        text.append("Флот ").append(fleetIndex).append(formatPosition(transform)).append("\n");
        text.append("  Состояние: ").append(tradeAI.state).append("\n");
        text.append("  Кредиты: ").append(formatFloat(tradeAI.credits)).append("\n");
        text.append("  Груз: ").append(tradeAI.cargoAmount).append("/").append(tradeAI.cargoSpace).append("\n");
        appendReputationLine(text, reputation, Constants.FACTION_TRADE_LEAGUE);
        appendReputationLine(text, reputation, Constants.FACTION_MINERS);
        appendInventoryLine(text, inventory, Constants.ITEM_FOOD);
        appendInventoryLine(text, inventory, Constants.ITEM_STEEL);
        text.append("\n");
    }

    private void appendReputationLine(StringBuilder text, ReputationComponent reputation, int factionId) {
        if (reputation == null) {
            return;
        }
        text.append("  Репутация ").append(Constants.FACTION_NAMES[factionId])
                .append(": ").append(formatFloat(reputation.getReputation(factionId)))
                .append("\n");
    }

    private void appendItemLine(StringBuilder text, InventoryComponent inventory, MarketComponent market, int itemId) {
        text.append("  ").append(Constants.ITEM_NAMES[itemId])
                .append(": ").append(inventory.stock[itemId])
                .append(" / цель ").append(market.targetStock[itemId])
                .append(" | продажа ").append(formatFloat(market.sellPrices[itemId]))
                .append(" | покупка ").append(formatFloat(market.buyPrices[itemId]))
                .append("\n");
    }

    private void appendInventoryLine(StringBuilder text, InventoryComponent inventory, int itemId) {
        text.append("  ").append(Constants.ITEM_NAMES[itemId])
                .append(": ").append(inventory.stock[itemId])
                .append("\n");
    }

    private String formatPosition(TransformComponent transform) {
        if (transform == null) {
            return "";
        }
        return " (" + formatFloat(transform.position.x) + ", " + formatFloat(transform.position.y) + ")";
    }

    private String formatFloat(float value) {
        return String.format("%.1f", value);
    }
}
