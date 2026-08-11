package com.spacesim.ui;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.Value;
import com.badlogic.gdx.utils.Align;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.ReputationComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.constants.Constants;

/**
 * Правая информационная панель с текущим состоянием станций и торговых флотов.
 *
 * <p>Панель получает представление Ashley-сущностей извне и формирует компактное
 * текстовое представление запасов, цен, состояния ИИ, кредитов и репутации.
 * Ширина вычисляется относительно окна и ограничена диапазоном, поэтому панель
 * сосуществует с лентой новостей даже при минимальном поддерживаемом разрешении.</p>
 *
 * <p>Класс не владеет переданным {@link Skin}; его жизненным циклом управляет
 * приложение.</p>
 */
public class EconomyStatusUI extends Table {
    private static final float MIN_PANEL_WIDTH = 280f;
    private static final float MAX_PANEL_WIDTH = 480f;
    private static final float HORIZONTAL_GAP = 20f;

    private final Skin skin;
    private final Label contentLabel;

    /**
     * Создаёт панель, закреплённую в правом верхнем углу родительской сцены.
     *
     * @param skin ненулевой скин Scene2D для заголовка и содержимого
     * @throws NullPointerException если {@code skin == null}
     */
    public EconomyStatusUI(Skin skin) {
        this.skin = skin;
        this.contentLabel = new Label("", skin);
        contentLabel.setWrap(true);
        contentLabel.setAlignment(Align.topRight);

        setFillParent(true);
        setTouchable(Touchable.childrenOnly);
        top().right();
        pad(10);
        add(new Label("Экономика", skin)).right().row();
        add(contentLabel).right().width(new Value() {
            @Override
            public float get(Actor context) {
                float availableHalf = EconomyStatusUI.this.getWidth() * 0.5f - HORIZONTAL_GAP;
                return Math.min(MAX_PANEL_WIDTH, Math.max(MIN_PANEL_WIDTH, availableHalf));
            }
        });
    }

    /**
     * Полностью перестраивает текст панели по актуальному набору сущностей.
     *
     * <p>Сущность с инвентарём и рынком считается станцией, а сущность с
     * инвентарём и торговым ИИ — флотом. Остальные сущности пропускаются.
     * Порядковые номера отражают порядок обхода переданной коллекции.</p>
     *
     * @param entities ненулевое представление сущностей экономической модели
     * @throws NullPointerException если {@code entities == null}
     */
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

    /** Добавляет в буфер состояние одной станции. */
    private void appendStation(StringBuilder text, int stationIndex, InventoryComponent inventory,
                               MarketComponent market, TransformComponent transform, FactionComponent faction) {
        text.append("Станция ").append(stationIndex).append(formatPosition(transform)).append("\n");
        text.append("  Фракция: ").append(faction == null ? "Нет" : faction.getFactionName()).append("\n");
        appendItemLine(text, inventory, market, Constants.ITEM_FOOD);
        appendItemLine(text, inventory, market, Constants.ITEM_STEEL);
        text.append("\n");
    }

    /** Добавляет в буфер состояние одного торгового флота. */
    private void appendFleet(StringBuilder text, int fleetIndex, InventoryComponent inventory,
                             TradeAIComponent tradeAI, TransformComponent transform, ReputationComponent reputation) {
        text.append("Флот ").append(fleetIndex).append(formatPosition(transform)).append("\n");
        text.append("  Состояние: ").append(tradeAI.state).append("\n");
        text.append("  Кредиты: ").append(formatFloat(tradeAI.credits)).append("\n");
        text.append("  Груз: ").append(inventory.getTotalStock()).append("/").append(tradeAI.cargoSpace).append("\n");
        appendReputationLine(text, reputation, Constants.FACTION_TRADE_LEAGUE);
        appendReputationLine(text, reputation, Constants.FACTION_MINERS);
        appendInventoryLine(text, inventory, Constants.ITEM_FOOD);
        appendInventoryLine(text, inventory, Constants.ITEM_STEEL);
        text.append("\n");
    }

    /** Добавляет строку репутации, если у флота есть соответствующий компонент. */
    private void appendReputationLine(StringBuilder text, ReputationComponent reputation, int factionId) {
        if (reputation == null) {
            return;
        }
        text.append("  Репутация ").append(Constants.FACTION_NAMES[factionId])
                .append(": ").append(formatFloat(reputation.getReputation(factionId)))
                .append("\n");
    }

    /** Добавляет строку рыночного товара с запасом, целью и обеими ценами. */
    private void appendItemLine(StringBuilder text, InventoryComponent inventory, MarketComponent market, int itemId) {
        text.append("  ").append(Constants.ITEM_NAMES[itemId])
                .append(": ").append(inventory.stock[itemId])
                .append(" / цель ").append(market.targetStock[itemId])
                .append(" | продажа ").append(formatFloat(market.sellPrices[itemId]))
                .append(" | покупка ").append(formatFloat(market.buyPrices[itemId]))
                .append("\n");
    }

    /** Добавляет строку товара из грузового инвентаря. */
    private void appendInventoryLine(StringBuilder text, InventoryComponent inventory, int itemId) {
        text.append("  ").append(Constants.ITEM_NAMES[itemId])
                .append(": ").append(inventory.stock[itemId])
                .append("\n");
    }

    /** Возвращает координаты сущности либо пустую строку при отсутствии позиции. */
    private String formatPosition(TransformComponent transform) {
        if (transform == null) {
            return "";
        }
        return " (" + formatFloat(transform.position.x) + ", " + formatFloat(transform.position.y) + ")";
    }

    /** Форматирует число с одним десятичным знаком для стабильного вида панели. */
    private String formatFloat(float value) {
        return String.format("%.1f", value);
    }
}
