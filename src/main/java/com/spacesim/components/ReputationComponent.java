package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.spacesim.constants.Constants;

/**
 * Репутация одной ECS-сущности у каждой известной фракции.
 *
 * <p>Значения хранятся в пунктах репутации и ограничены диапазоном
 * [{@link Constants#MIN_REPUTATION}, {@link Constants#MAX_REPUTATION}].
 * {@link com.spacesim.controllers.TradeController} читает репутацию для
 * поправки торговой цены и увеличивает её после успешных операций; интерфейс
 * использует компонент только для отображения. Массив закрыт, поэтому менять
 * значения следует через {@link #addReputation(int, float)}.</p>
 */
public class ReputationComponent implements Component {
    private final float[] reputation = new float[Constants.MAX_FACTIONS];

    /**
     * Создаёт компонент с нейтральной нулевой репутацией у всех фракций.
     */
    public ReputationComponent() {
    }

    /**
     * Возвращает репутацию у выбранной фракции.
     *
     * @param factionId идентификатор фракции
     * @return значение в установленном диапазоне; {@code 0}, если
     *         идентификатор не входит в {@code [0, Constants.MAX_FACTIONS)}
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
     * @param factionId идентификатор изменяемой фракции
     * @param amount конечное положительное или отрицательное изменение в
     *               пунктах репутации
     * @throws IllegalArgumentException если идентификатор фракции допустим, а
     *                                  {@code amount} равен {@code NaN} или бесконечности;
     *                                  при недопустимом идентификаторе значение не проверяется
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
        return factionId >= 0 && factionId < Constants.MAX_FACTIONS;
    }
}
