package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.spacesim.constants.Constants;

/**
 * Репутация одной ECS-сущности у materialized runtime factions.
 *
 * <p>Значения хранятся в пунктах репутации и ограничены диапазоном
 * [{@link Constants#MIN_REPUTATION}, {@link Constants#MAX_REPUTATION}].
 * {@link com.spacesim.controllers.TradeController} читает репутацию для
 * поправки торговой цены и увеличивает её после успешных операций; интерфейс
 * использует компонент только для отображения. Массив закрыт, поэтому менять
 * значения следует через {@link #addReputation(int, float)}.</p>
 *
 * <p>Размер storage определяется {@link Constants#FACTION_RUNTIME_CAPACITY}; это не число
 * фактически существующих factions. Stable identity и display metadata разрешаются отдельно через
 * world/content faction directory.</p>
 */
public class ReputationComponent implements Component {
    private final float[] reputation = new float[Constants.FACTION_RUNTIME_CAPACITY];

    /** Создаёт компонент с нейтральной нулевой репутацией во всех runtime slots. */
    public ReputationComponent() {
    }

    /**
     * Возвращает репутацию у выбранного runtime faction slot.
     *
     * @param factionId dense runtime faction ID
     * @return значение в установленном диапазоне; {@code 0}, если
     *         идентификатор не входит в runtime capacity
     */
    public float getReputation(int factionId) {
        if (!isValidFaction(factionId)) {
            return 0f;
        }
        return reputation[factionId];
    }

    /**
     * Прибавляет указанное число пунктов и ограничивает результат глобальными
     * минимальной и максимальной границами.
     *
     * <p>Недопустимый идентификатор фракции обрабатывается как отсутствие цели:
     * состояние не меняется. Это позволяет безопасно работать с сущностью,
     * фракция которой ещё не зарегистрирована.</p>
     *
     * @param factionId dense runtime faction ID
     * @param amount конечное положительное или отрицательное изменение в пунктах репутации
     * @throws IllegalArgumentException если идентификатор фракции допустим, а {@code amount}
     *         равен {@code NaN} или бесконечности
     */
    public void addReputation(int factionId, float amount) {
        if (!isValidFaction(factionId)) {
            return;
        }
        if (!Float.isFinite(amount)) {
            throw new IllegalArgumentException("Изменение репутации должно быть конечным числом");
        }
        reputation[factionId] = Math.max(Constants.MIN_REPUTATION,
                Math.min(Constants.MAX_REPUTATION, reputation[factionId] + amount));
    }

    private boolean isValidFaction(int factionId) {
        return factionId >= 0 && factionId < Constants.FACTION_RUNTIME_CAPACITY;
    }
}
