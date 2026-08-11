package com.spacesim.systems;

import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.core.*;
import com.spacesim.components.*;
import com.spacesim.constants.Constants;
import com.spacesim.events.EconomyEvent;
import com.spacesim.events.GlobalEventManager;

/**
 * Уменьшает запасы товаров в соответствии с базовой скоростью потребления рынка.
 *
 * <p>Система обрабатывает сущности, у которых одновременно присутствуют инвентарь, рынок и
 * положение в мире. Для каждого товара величина потребления вычисляется как произведение базовой
 * скорости, прошедшего времени и множителей всех действующих в этой точке экономических событий.
 * Множители нескольких событий перемножаются.</p>
 *
 * <p>Поскольку запасы представлены целыми единицами, дробная часть результата сохраняется в
 * {@link MarketComponent#consumptionRemainder}. Как только накопленное значение достигает целой
 * единицы, из инвентаря списывается доступное количество, а неиспользованный временной остаток
 * переносится на следующий кадр. Фактическое изменение запаса устанавливает
 * {@link MarketComponent#isDirty}, чтобы {@link MarketSystem} пересчитал цены.</p>
 *
 * <p>Неконечное или неположительное время кадра игнорируется. Для отсутствующего запаса либо
 * некорректной базовой скорости накопленный остаток соответствующего товара сбрасывается.</p>
 */
public class ConsumptionSystem extends IteratingSystem {
    /** Менеджер событий, задающих пространственные множители потребления. */
    private final GlobalEventManager eventManager;

    /** Быстрый доступ к инвентарю обрабатываемой сущности. */
    private ComponentMapper<InventoryComponent> im = ComponentMapper.getFor(InventoryComponent.class);
    /** Быстрый доступ к параметрам рынка и накопленным дробным остаткам. */
    private ComponentMapper<MarketComponent> mm = ComponentMapper.getFor(MarketComponent.class);
    /** Быстрый доступ к мировой позиции, необходимой для проверки областей событий. */
    private ComponentMapper<TransformComponent> tm = ComponentMapper.getFor(TransformComponent.class);

    /**
     * Создаёт систему потребления.
     *
     * @param eventManager менеджер, предоставляющий актуальный список экономических событий;
     *                     во время работы системы не должен быть {@code null}
     */
    public ConsumptionSystem(GlobalEventManager eventManager) {
        super(Family.all(InventoryComponent.class, MarketComponent.class, TransformComponent.class).get());
        this.eventManager = eventManager;
    }

    /**
     * Применяет накопившееся потребление к одной подходящей сущности.
     *
     * <p>Метод вызывается Ashley только для сущностей семейства системы. Списание ограничивается
     * текущим запасом, поэтому значение в инвентаре не становится отрицательным.</p>
     *
     * @param entity сущность с компонентами {@link InventoryComponent}, {@link MarketComponent}
     *               и {@link TransformComponent}
     * @param deltaTime прошедшее с предыдущего обновления время в секундах
     */
    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        if (!Float.isFinite(deltaTime) || deltaTime <= 0f) {
            return;
        }

        InventoryComponent inv = im.get(entity);
        MarketComponent market = mm.get(entity);
        TransformComponent transform = tm.get(entity);

        for (int i = 0; i < Constants.MAX_ITEMS; i++) {
            if (inv.stock[i] <= 0) {
                market.consumptionRemainder[i] = 0d;
                continue;
            }

            float baseConsumption = market.baseConsumption[i];
            if (!Float.isFinite(baseConsumption) || baseConsumption <= 0f) {
                market.consumptionRemainder[i] = 0d;
                continue;
            }

            float multiplier = getConsumptionMultiplier(i, transform);
            if (!Float.isFinite(multiplier) || multiplier <= 0f) {
                continue;
            }

            double accumulatedConsumption = market.consumptionRemainder[i]
                    + (double) baseConsumption * multiplier * deltaTime;
            if (!Double.isFinite(accumulatedConsumption)) {
                market.consumptionRemainder[i] = 0d;
                continue;
            }

            double wholeConsumption = Math.floor(accumulatedConsumption);
            if (wholeConsumption < 1d) {
                market.consumptionRemainder[i] = accumulatedConsumption;
                continue;
            }

            int unitsToConsume = wholeConsumption >= Integer.MAX_VALUE
                    ? Integer.MAX_VALUE
                    : (int) wholeConsumption;
            int consumedUnits = Math.min(inv.stock[i], unitsToConsume);
            inv.stock[i] -= consumedUnits;
            market.consumptionRemainder[i] = accumulatedConsumption - wholeConsumption;

            if (consumedUnits > 0) {
                market.isDirty = true;
            }
        }
    }

    /**
     * Вычисляет совокупный множитель потребления для товара в позиции сущности.
     *
     * @param itemId идентификатор товара
     * @param transform положение потребляющей сущности
     * @return произведение множителей всех влияющих событий или {@code 1.0f}, если таких событий нет
     */
    private float getConsumptionMultiplier(int itemId, TransformComponent transform) {
        float multiplier = 1.0f;
        for (EconomyEvent event : eventManager.getActiveEvents()) {
            if (event.affects(itemId, transform.position)) {
                multiplier *= event.getConsumptionMultiplier();
            }
        }
        return multiplier;
    }
}
