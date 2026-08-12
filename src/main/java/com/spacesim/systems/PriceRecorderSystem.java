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
 * по модулю интервала. Этот остаток входит в {@link State}, поэтому загрузка не сдвигает момент
 * следующего снимка цен.</p>
 */
public class PriceRecorderSystem extends IteratingSystem {
    /** Период между соседними снимками цен в секундах симуляции. */
    public static final float RECORD_INTERVAL_SECONDS = 1.0f;

    /**
     * Сериализуемое состояние системного таймера.
     *
     * @param timerSeconds накопленное время внутри текущего интервала
     */
    public record State(float timerSeconds) {
        /**
         * Проверяет диапазон таймера.
         *
         * @param timerSeconds накопленное время внутри текущего интервала
         */
        public State {
            if (!Float.isFinite(timerSeconds)
                    || timerSeconds < 0f
                    || timerSeconds >= RECORD_INTERVAL_SECONDS) {
                throw new IllegalArgumentException("Таймер PriceRecorder должен принадлежать [0, interval)");
            }
        }
    }

    private float timer;
    private boolean shouldRecordThisFrame;

    private final ComponentMapper<MarketComponent> mm = ComponentMapper.getFor(MarketComponent.class);
    private final ComponentMapper<PriceHistoryComponent> hm = ComponentMapper.getFor(PriceHistoryComponent.class);

    /** Создаёт регистратор с пустым внутренним таймером. */
    public PriceRecorderSystem() {
        this(new State(0f));
    }

    /**
     * Восстанавливает регистратор из сохранённого системного таймера.
     *
     * @param state состояние таймера
     * @throws NullPointerException если состояние не задано
     */
    public PriceRecorderSystem(State state) {
        super(Family.all(MarketComponent.class, PriceHistoryComponent.class).get());
        State checked = java.util.Objects.requireNonNull(state, "Состояние PriceRecorder не задано");
        timer = checked.timerSeconds();
    }

    /** @return immutable снимок внутреннего таймера */
    public State snapshotState() {
        return new State(timer);
    }

    /**
     * Продвигает таймер записи и при необходимости запускает один проход сохранения цен.
     *
     * @param deltaTime прошедшее с предыдущего обновления время в секундах
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

    /** Добавляет один снимок всех цен продажи в историю сущности при активном флаге записи. */
    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        if (!shouldRecordThisFrame) {
            return;
        }

        MarketComponent market = mm.get(entity);
        PriceHistoryComponent history = hm.get(entity);
        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            history.history[itemId].add(market.sellPrices[itemId]);
            if (history.history[itemId].size > history.maxPoints) {
                history.history[itemId].removeIndex(0);
            }
        }
    }
}
