package com.spacesim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.model.ItemType;

/**
 * Преобразует непрерывную производительность добывающих кораблей в целый груз.
 *
 * <p>Система обрабатывает только сущности с {@link ShipComponent}, {@link MiningComponent} и
 * {@link InventoryComponent}. Фактическая добыча разрешена исключительно типу
 * {@link com.spacesim.model.ShipType#MINING_SHIP} и товару с признаком
 * {@link ItemType#isMineable()}. Координаты и запас месторождения намеренно не моделируются:
 * ресурс появляется в собственном трюме корабля, пока оборудование активно.</p>
 *
 * <p>Если у корабля есть {@link TradeAIComponent}, добыча выполняется только в состоянии
 * {@link TradeAIComponent.State#IDLE}; кораблю без торгового AI это ограничение не требуется.
 * Дробная часть выпуска переносится между корректными обновлениями. При заполнении трюма остаток
 * сбрасывается, поэтому освобождение места не создаёт скрытый накопленный груз.</p>
 *
 * <p>Число фактически добытых единиц ограничивается общей вместимостью инвентаря, диапазоном
 * {@code int} отдельного товарного запаса и диапазоном {@code long} счётчика
 * {@link MiningComponent#totalMined}. Большой шаг времени обрабатывается одной пакетной операцией
 * без цикла по каждой единице.</p>
 */
public class MiningSystem extends IteratingSystem {
    private final ComponentMapper<ShipComponent> shipMapper =
            ComponentMapper.getFor(ShipComponent.class);
    private final ComponentMapper<MiningComponent> miningMapper =
            ComponentMapper.getFor(MiningComponent.class);
    private final ComponentMapper<InventoryComponent> inventoryMapper =
            ComponentMapper.getFor(InventoryComponent.class);
    private final ComponentMapper<TradeAIComponent> tradeAIMapper =
            ComponentMapper.getFor(TradeAIComponent.class);
    private final ComponentMapper<MarketComponent> marketMapper =
            ComponentMapper.getFor(MarketComponent.class);

    /**
     * Создаёт систему для всех кораблей с добывающим оборудованием и собственным инвентарём.
     */
    public MiningSystem() {
        super(Family.all(
                ShipComponent.class,
                MiningComponent.class,
                InventoryComponent.class
        ).get());
    }

    /**
     * Продвигает добычу одной подходящей сущности.
     *
     * @param entity корабль с обязательными компонентами семейства системы
     * @param deltaTime прошедшее игровое время в секундах
     */
    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        if (!Float.isFinite(deltaTime) || deltaTime <= 0f) {
            return;
        }

        ShipComponent ship = shipMapper.get(entity);
        MiningComponent mining = miningMapper.get(entity);
        if (!mining.active || ship.type == null || !ship.type.isMining()) {
            return;
        }

        TradeAIComponent tradeAI = tradeAIMapper.get(entity);
        if (tradeAI != null && tradeAI.state != TradeAIComponent.State.IDLE) {
            return;
        }

        ItemType resource = ItemType.fromId(mining.resourceItem);
        if (resource == null
                || !resource.isMineable()
                || !ship.type.canCarry(resource)
                || !Float.isFinite(mining.extractionPerSecond)
                || mining.extractionPerSecond <= 0f) {
            return;
        }

        InventoryComponent inventory = inventoryMapper.get(entity);
        int currentStock = inventory.stock[mining.resourceItem];
        if (currentStock < 0) {
            return;
        }

        int freeCapacity = inventory.getFreeCapacity();
        int itemCapacity = Integer.MAX_VALUE - currentStock;
        int maximumExtraction = Math.min(freeCapacity, itemCapacity);
        if (maximumExtraction <= 0) {
            mining.extractionRemainder = 0d;
            return;
        }

        double remainder = normalizedRemainder(mining.extractionRemainder);
        double produced = remainder + (double) mining.extractionPerSecond * deltaTime;
        if (!Double.isFinite(produced) || produced <= 0d) {
            mining.extractionRemainder = 0d;
            return;
        }

        double completedUnits = Math.floor(produced);
        if (completedUnits < 1d) {
            mining.extractionRemainder = produced;
            return;
        }

        int extracted = completedUnits >= maximumExtraction
                ? maximumExtraction
                : (int) completedUnits;
        inventory.stock[mining.resourceItem] = currentStock + extracted;
        mining.totalMined = saturatedAdd(normalizedTotalMined(mining.totalMined), extracted);
        mining.extractionRemainder = completedUnits >= maximumExtraction
                ? 0d
                : produced - extracted;

        MarketComponent market = marketMapper.get(entity);
        if (market != null) {
            market.isDirty = true;
        }
    }

    private double normalizedRemainder(double remainder) {
        if (!Double.isFinite(remainder) || remainder < 0d || remainder >= 1d) {
            return 0d;
        }
        return remainder;
    }

    private long normalizedTotalMined(long totalMined) {
        return Math.max(0L, totalMined);
    }

    private long saturatedAdd(long totalMined, int extracted) {
        if (Long.MAX_VALUE - totalMined < extracted) {
            return Long.MAX_VALUE;
        }
        return totalMined + extracted;
    }
}
