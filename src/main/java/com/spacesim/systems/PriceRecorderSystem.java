package com.spacesim.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.systems.IteratingSystem;
import com.spacesim.components.*;
import com.spacesim.constants.Constants;

/**
 * Периодически сохраняет текущие цены продажи в ограниченную историю рынка.
 *
 * <p>Общий для системы таймер накапливает положительное конечное время кадров. При достижении
 * секундного интервала выставляется кратковременный флаг записи, после чего Ashley одним проходом
 * добавляет по одной точке истории каждой подходящей сущности. Остаток времени сохраняется операцией
 * по модулю интервала, поэтому небольшая погрешность кадров не накапливается.</p>
 *
 * <p>Даже если один кадр перекрывает несколько интервалов, за этот вызов записывается только один
 * снимок: система хранит историю наблюдаемых состояний, а не дублирует одну и ту же цену для
 * пропущенных моментов. После прохода флаг записи обязательно сбрасывается. Неконечное или
 * неположительное время игнорируется.</p>
 */
public class PriceRecorderSystem extends IteratingSystem {
    /** Период между соседними снимками цен в секундах симуляции. */
    private static final float RECORD_INTERVAL_SECONDS = 1.0f;

    /** Накопленное время внутри текущего интервала записи. */
    private float timer = 0;
    /** Признак единственного прохода записи, действующий только в текущем вызове обновления. */
    private boolean shouldRecordThisFrame = false;

    /** Быстрый доступ к текущим рыночным ценам. */
    private final ComponentMapper<MarketComponent> mm = ComponentMapper.getFor(MarketComponent.class);
    /** Быстрый доступ к ограниченным массивам истории цен. */
    private final ComponentMapper<PriceHistoryComponent> hm = ComponentMapper.getFor(PriceHistoryComponent.class);

    /**
     * Создаёт регистратор для сущностей с рынком и компонентом истории цен.
     */
    public PriceRecorderSystem() {
        super(Family.all(MarketComponent.class, PriceHistoryComponent.class).get());
    }

    /**
     * Продвигает таймер записи и при необходимости запускает один проход сохранения цен.
     *
     * @param deltaTime прошедшее с предыдущего обновления время в секундах; неположительные,
     *                  бесконечные значения и {@code NaN} игнорируются
     */
    @Override
    public void update(float deltaTime) {
        if (!Float.isFinite(deltaTime) || deltaTime <= 0f) {
            shouldRecordThisFrame = false;
            return;
        }

        timer += deltaTime;
        shouldRecordThisFrame = timer >= RECORD_INTERVAL_SECONDS;

        if (shouldRecordThisFrame) {
            timer %= RECORD_INTERVAL_SECONDS;
        }

        super.update(deltaTime);
        shouldRecordThisFrame = false;
    }

    /**
     * Добавляет один снимок всех цен продажи в историю сущности.
     *
     * <p>После добавления самой старой точки она удаляется, если число элементов превысило
     * {@link PriceHistoryComponent#maxPoints}. В кадрах без активного флага записи метод ничего не
     * изменяет.</p>
     *
     * @param entity сущность с компонентами {@link MarketComponent} и
     *               {@link PriceHistoryComponent}
     * @param deltaTime время кадра в секундах; при непосредственной записи не используется
     */
    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        if (!shouldRecordThisFrame) {
            return;
        }

        MarketComponent m = mm.get(entity);
        PriceHistoryComponent h = hm.get(entity);

        for(int i=0; i<Constants.MAX_ITEMS; i++) {
            h.history[i].add(m.sellPrices[i]);
            if(h.history[i].size > h.maxPoints) h.history[i].removeIndex(0);
        }
    }
}
