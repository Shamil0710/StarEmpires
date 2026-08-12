package com.spacesim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.constants.Constants;
import com.spacesim.economy.EconomicLedger;
import com.spacesim.events.EconomyEvent;
import com.spacesim.events.GlobalEventManager;

import java.util.Objects;

/**
 * Уменьшает запасы товаров в соответствии с базовой скоростью потребления рынка.
 *
 * <p>Фактически списанные единицы являются явным {@code RESOURCE_SINK} и записываются в
 * {@link EconomicLedger}. Дробная часть потребления сохраняется в
 * {@link MarketComponent#consumptionRemainder}, поэтому частота вызовов не меняет итоговую скорость.</p>
 */
public class ConsumptionSystem extends IteratingSystem {
    private final GlobalEventManager eventManager;
    private final EconomicLedger ledger;

    private final ComponentMapper<InventoryComponent> im = ComponentMapper.getFor(InventoryComponent.class);
    private final ComponentMapper<MarketComponent> mm = ComponentMapper.getFor(MarketComponent.class);
    private final ComponentMapper<TransformComponent> tm = ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<IdentityComponent> identityMapper = ComponentMapper.getFor(IdentityComponent.class);

    /**
     * Создаёт систему с собственным диагностическим ledger.
     *
     * @param eventManager менеджер экономических событий
     */
    public ConsumptionSystem(GlobalEventManager eventManager) {
        this(eventManager, new EconomicLedger());
    }

    /**
     * Создаёт систему, записывающую потребление в общий журнал игровой сессии.
     *
     * @param eventManager менеджер экономических событий
     * @param ledger общий экономический журнал
     * @throws NullPointerException если зависимость не задана
     */
    public ConsumptionSystem(GlobalEventManager eventManager, EconomicLedger ledger) {
        super(Family.all(InventoryComponent.class, MarketComponent.class, TransformComponent.class).get());
        this.eventManager = Objects.requireNonNull(eventManager, "GlobalEventManager не задан");
        this.ledger = Objects.requireNonNull(ledger, "EconomicLedger не задан");
    }

    /** @return ledger, в который записываются фактические resource sink операции */
    public EconomicLedger getLedger() {
        return ledger;
    }

    /**
     * Применяет накопившееся потребление к одной подходящей сущности.
     *
     * @param entity сущность рынка
     * @param deltaTime прошедшее игровое время в секундах
     */
    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        if (!Float.isFinite(deltaTime) || deltaTime <= 0f) {
            return;
        }

        InventoryComponent inv = im.get(entity);
        MarketComponent market = mm.get(entity);
        TransformComponent transform = tm.get(entity);

        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            if (inv.stock[itemId] <= 0) {
                market.consumptionRemainder[itemId] = 0d;
                continue;
            }

            float baseConsumption = market.baseConsumption[itemId];
            if (!Float.isFinite(baseConsumption) || baseConsumption <= 0f) {
                market.consumptionRemainder[itemId] = 0d;
                continue;
            }

            float multiplier = getConsumptionMultiplier(itemId, transform);
            if (!Float.isFinite(multiplier) || multiplier <= 0f) {
                continue;
            }

            double accumulatedConsumption = market.consumptionRemainder[itemId]
                    + (double) baseConsumption * multiplier * deltaTime;
            if (!Double.isFinite(accumulatedConsumption)) {
                market.consumptionRemainder[itemId] = 0d;
                continue;
            }

            double wholeConsumption = Math.floor(accumulatedConsumption);
            if (wholeConsumption < 1d) {
                market.consumptionRemainder[itemId] = accumulatedConsumption;
                continue;
            }

            int unitsToConsume = wholeConsumption >= Integer.MAX_VALUE
                    ? Integer.MAX_VALUE
                    : (int) wholeConsumption;
            int consumedUnits = Math.min(inv.stock[itemId], unitsToConsume);
            inv.stock[itemId] -= consumedUnits;
            market.consumptionRemainder[itemId] = accumulatedConsumption - wholeConsumption;

            if (consumedUnits > 0) {
                market.isDirty = true;
                ledger.recordResourceSink(
                        entityName(entity),
                        itemId,
                        consumedUnits,
                        "market-consumption");
            }
        }
    }

    private float getConsumptionMultiplier(int itemId, TransformComponent transform) {
        float multiplier = 1.0f;
        for (EconomyEvent event : eventManager.getActiveEvents()) {
            if (event.affects(itemId, transform.position)) {
                multiplier *= event.getConsumptionMultiplier();
            }
        }
        return multiplier;
    }

    private String entityName(Entity entity) {
        IdentityComponent identity = identityMapper.get(entity);
        return identity == null || identity.name == null || identity.name.isBlank()
                ? "UNIDENTIFIED"
                : identity.name;
    }
}
