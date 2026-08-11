package com.spacesim.ui;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TradeAIComponent;

import java.util.Objects;

/**
 * Компактная сводка состояния экономической симуляции.
 *
 * <p>Панель показывает количество станций и кораблей, число кораблей в пути,
 * общий объём груза и распределение корпусов по трём крупным ролям. Корабли
 * старого формата без {@link ShipComponent}, но с {@link TradeAIComponent},
 * учитываются как перевозчики. Краткая легенда условных обозначений остаётся
 * компактной и помещается рядом с картой при размере окна {@code 800x600}.
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
     * <p>Агрегация отделена от Scene2D в {@link #summarize(Iterable)}, поэтому правила подсчёта
     * типов можно проверять без графического контекста.</p>
     *
     * @param entities ненулевое представление сущностей экономической модели
     * @throws NullPointerException если {@code entities == null}
     */
    public void update(Iterable<Entity> entities) {
        contentLabel.setText(formatSummary(summarize(entities)));
    }

    /**
     * Собирает независимый от Scene2D снимок экономической сводки.
     *
     * <p>Явный {@link ShipComponent} определяет современный класс корабля. Только при его
     * отсутствии торговый ИИ считается признаком legacy-перевозчика. Груз суммируется для всех
     * распознанных кораблей и насыщается границей {@code int}.</p>
     *
     * @param entities ненулевое представление сущностей мира; отдельные элементы могут быть null
     * @return неизменяемые агрегированные счётчики
     * @throws NullPointerException если {@code entities == null}
     */
    static Summary summarize(Iterable<Entity> entities) {
        Objects.requireNonNull(entities, "Entities must not be null");
        int stationCount = 0;
        int shipCount = 0;
        int travellingCount = 0;
        int carrierCount = 0;
        int miningCount = 0;
        int combatCount = 0;
        long cargoUnits = 0L;

        for (Entity entity : entities) {
            if (entity == null) {
                continue;
            }
            InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
            MarketComponent market = entity.getComponent(MarketComponent.class);
            ShipComponent ship = entity.getComponent(ShipComponent.class);
            TradeAIComponent tradeAI = entity.getComponent(TradeAIComponent.class);

            if (market != null) {
                stationCount++;
            }
            boolean isLegacyShip = ship == null && tradeAI != null;
            if (ship != null || isLegacyShip) {
                shipCount++;
                if (ship == null) {
                    carrierCount++;
                } else if (ship.type != null) {
                    if (ship.type.isCarrier()) {
                        carrierCount++;
                    } else if (ship.type.isMining()) {
                        miningCount++;
                    } else if (ship.type.isCombat()) {
                        combatCount++;
                    }
                }
                if (tradeAI != null
                        && (tradeAI.state == TradeAIComponent.State.TRAVEL_TO_BUY
                        || tradeAI.state == TradeAIComponent.State.TRAVEL_TO_SELL)) {
                    travellingCount++;
                }
                if (inventory != null) {
                    cargoUnits = Math.min(Integer.MAX_VALUE,
                            cargoUnits + Math.max(0, inventory.getTotalStock()));
                }
            }
        }

        return new Summary(
                stationCount,
                shipCount,
                travellingCount,
                (int) cargoUnits,
                carrierCount,
                miningCount,
                combatCount);
    }

    /** Формирует компактный текст панели из чистой модели сводки. */
    static String formatSummary(Summary summary) {
        Objects.requireNonNull(summary, "Summary must not be null");
        return "Станции: " + summary.stationCount()
                + "   Корабли: " + summary.shipCount()
                + "\nВ пути: " + summary.travellingCount()
                + "   Груз: " + summary.cargoUnits()
                + "\nТранспорт: " + summary.carrierCount()
                + "   Добыча: " + summary.miningCount()
                + "   Боевые: " + summary.combatCount()
                + "\nФорма и цвет — класс корабля"
                + "\nЛиния — маршрут   ЛКМ — выбрать";
    }

    /**
     * Неизменяемый набор счётчиков для текста экономической панели.
     *
     * @param stationCount число рыночных станций
     * @param shipCount общее число современных и legacy-кораблей
     * @param travellingCount число торговых кораблей с активным перелётом
     * @param cargoUnits суммарное неотрицательное количество груза
     * @param carrierCount число коммерческих перевозчиков
     * @param miningCount число добывающих кораблей
     * @param combatCount число боевых кораблей
     */
    record Summary(
            int stationCount,
            int shipCount,
            int travellingCount,
            int cargoUnits,
            int carrierCount,
            int miningCount,
            int combatCount) {
    }
}
