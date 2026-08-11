package com.spacesim.ui;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.TradeAIComponent;

import java.util.Objects;

/**
 * Компактная сводка состояния экономической симуляции.
 *
 * <p>Панель показывает количество станций и кораблей, число кораблей в пути и
 * общий объём перевозимого груза, а также краткую легенду условных обозначений.
 * Подробные данные конкретной сущности выводит {@link EntityDetailsUI}, поэтому
 * сводка остаётся короткой и не закрывает карту.</p>
 *
 * <p>Класс не владеет переданным {@link Skin}; его жизненным циклом управляет
 * приложение.</p>
 */
public class EconomyStatusUI extends Table {
    private static final float PANEL_WIDTH = 340f;

    private final Label contentLabel;

    /**
     * Создаёт панель, закреплённую в левом нижнем углу родительской сцены.
     *
     * @param skin ненулевой скин Scene2D для заголовка и содержимого
    * @throws NullPointerException если {@code skin == null}
     */
    public EconomyStatusUI(Skin skin) {
        Skin checkedSkin = Objects.requireNonNull(skin, "Skin must not be null");
        this.contentLabel = new Label("", checkedSkin);
        contentLabel.setWrap(true);
        contentLabel.setAlignment(Align.topLeft);

        Table panel = new Table(checkedSkin);
        panel.setBackground(checkedSkin.getDrawable("window-bg"));
        panel.pad(10f);
        panel.add(new Label("Сводка", checkedSkin)).left().row();
        panel.add(contentLabel).left().growX();

        setFillParent(true);
        setTouchable(Touchable.childrenOnly);
        bottom().left();
        pad(12f);
        add(panel).width(PANEL_WIDTH);
    }

    /**
     * Пересчитывает агрегированную сводку по актуальному набору сущностей.
     *
     * <p>Сущность с рынком считается станцией, а сущность с торговым ИИ —
     * кораблём. Груз суммируется только для кораблей и насыщается границей
     * {@code int}, если модель была заполнена экстремальными значениями.</p>
     *
     * @param entities ненулевое представление сущностей экономической модели
     * @throws NullPointerException если {@code entities == null}
     */
    public void update(Iterable<Entity> entities) {
        Objects.requireNonNull(entities, "Entities must not be null");
        int stationCount = 0;
        int shipCount = 0;
        int travellingCount = 0;
        long cargoUnits = 0L;

        for (Entity entity : entities) {
            InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
            MarketComponent market = entity.getComponent(MarketComponent.class);
            TradeAIComponent tradeAI = entity.getComponent(TradeAIComponent.class);

            if (market != null) {
                stationCount++;
            }
            if (tradeAI != null) {
                shipCount++;
                if (tradeAI.state == TradeAIComponent.State.TRAVEL_TO_BUY
                        || tradeAI.state == TradeAIComponent.State.TRAVEL_TO_SELL) {
                    travellingCount++;
                }
                if (inventory != null) {
                    cargoUnits = Math.min(Integer.MAX_VALUE,
                            cargoUnits + Math.max(0, inventory.getTotalStock()));
                }
            }
        }

        contentLabel.setText("Станции: " + stationCount
                + "   Корабли: " + shipCount
                + "\nВ пути: " + travellingCount
                + "   Груз: " + cargoUnits
                + "\nКруг — станция   Треугольник — корабль"
                + "\nЛиния — текущий маршрут"
                + "\nЛКМ — выбрать объект");
    }
}
