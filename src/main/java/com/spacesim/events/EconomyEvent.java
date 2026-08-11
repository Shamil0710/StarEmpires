package com.spacesim.events;

import com.badlogic.gdx.math.Vector2;
import com.spacesim.constants.Constants;

import java.util.Objects;

/**
 * Описывает ограниченное по времени событие, влияющее на экономику в заданной области мира.
 *
 * <p>Параметры события неизменяемы для внешнего кода. Оставшееся время жизни изменяет только
 * {@link GlobalEventManager}, поэтому любые изменения набора экономических эффектов проходят
 * через единый менеджер и корректно отражаются в его ревизии.</p>
 *
 * <p>Экземпляр не является потокобезопасным: менеджер изменяет оставшуюся продолжительность без
 * синхронизации. Событие следует создавать и обслуживать в одном потоке игрового цикла. Координаты
 * защищены от внешней мутации: конструктор сохраняет копию вектора, а {@link #getLocation()}
 * возвращает новую копию.</p>
 *
 * <p>Геометрические вычисления выполняются в {@code float}. Конечные, но экстремально большие
 * координаты или радиус могут переполнить квадрат расстояния; для предсказуемой проверки области
 * следует использовать величины игрового масштаба.</p>
 */
public final class EconomyEvent {
    private final String name;
    private final int targetItemId;
    private final float priceMultiplier;
    private final float consumptionMultiplier;
    private float remainingDurationSeconds;
    private final Vector2 location;
    private final float radius;
    private final float radiusSquared;

    /**
     * Создаёт экономическое событие.
     *
     * @param name название события, отображаемое в новостях
     * @param targetItemId идентификатор товара, на который действует событие
     * @param priceMultiplier положительный множитель рыночной цены
     * @param consumptionMultiplier неотрицательный множитель потребления
     * @param durationSeconds продолжительность события в секундах
     * @param location центр области действия в мировых координатах; значение копируется
     * @param radius положительный радиус области действия в мировых единицах
     * @throws NullPointerException если {@code name} или {@code location} равен {@code null}
     * @throws IllegalArgumentException если идентификатор товара находится вне диапазона
     *         {@code [0, Constants.MAX_ITEMS)}, строка пуста либо числовой аргумент не удовлетворяет
     *         описанным ограничениям
     */
    public EconomyEvent(String name, int targetItemId, float priceMultiplier,
                        float consumptionMultiplier, float durationSeconds,
                        Vector2 location, float radius) {
        this.name = requireName(name);
        if (targetItemId < 0 || targetItemId >= Constants.MAX_ITEMS) {
            throw new IllegalArgumentException("Идентификатор товара находится вне допустимого диапазона");
        }
        this.targetItemId = targetItemId;
        this.priceMultiplier = requirePositiveFinite(priceMultiplier, "Множитель цены");
        this.consumptionMultiplier = requireNonNegativeFinite(
                consumptionMultiplier, "Множитель потребления");
        this.remainingDurationSeconds = requirePositiveFinite(
                durationSeconds, "Продолжительность события");

        Vector2 sourceLocation = Objects.requireNonNull(location, "Координаты события не заданы");
        if (!Float.isFinite(sourceLocation.x) || !Float.isFinite(sourceLocation.y)) {
            throw new IllegalArgumentException("Координаты события должны быть конечными числами");
        }
        this.location = new Vector2(sourceLocation);
        this.radius = requirePositiveFinite(radius, "Радиус события");
        this.radiusSquared = this.radius * this.radius;
    }

    /**
     * Возвращает название события.
     *
     * @return непустое название события
     */
    public String getName() {
        return name;
    }

    /**
     * Возвращает идентификатор товара, на который действует событие.
     *
     * @return идентификатор из диапазона {@code [0, Constants.MAX_ITEMS)}
     */
    public int getTargetItemId() {
        return targetItemId;
    }

    /**
     * Возвращает множитель рыночной цены.
     *
     * @return конечное положительное число
     */
    public float getPriceMultiplier() {
        return priceMultiplier;
    }

    /**
     * Возвращает множитель потребления товара.
     *
     * @return конечное неотрицательное число
     */
    public float getConsumptionMultiplier() {
        return consumptionMultiplier;
    }

    /**
     * Возвращает оставшееся время действия события.
     *
     * @return число секунд в диапазоне от нуля до исходной продолжительности
     */
    public float getRemainingDurationSeconds() {
        return remainingDurationSeconds;
    }

    /**
     * Возвращает копию координат центра события.
     *
     * <p>Изменение полученного вектора не меняет область действия события.</p>
     *
     * @return новый вектор с координатами центра события
     */
    public Vector2 getLocation() {
        return new Vector2(location);
    }

    /**
     * Возвращает радиус области действия события.
     *
     * @return положительный радиус в мировых единицах
     */
    public float getRadius() {
        return radius;
    }

    /**
     * Проверяет, действует ли событие на указанный товар в заданной точке.
     *
     * <p>Граница окружности не входит в область действия: расстояние должно быть строго меньше
     * радиуса.</p>
     *
     * @param itemId идентификатор проверяемого товара
     * @param position позиция объекта в мировых координатах
     * @return {@code true}, если совпадает товар и позиция находится внутри области события
     * @throws NullPointerException если {@code position} равен {@code null}
     */
    public boolean affects(int itemId, Vector2 position) {
        Objects.requireNonNull(position, "Проверяемая позиция не задана");
        return targetItemId == itemId && position.dst2(location) < radiusSquared;
    }

    /**
     * Уменьшает оставшееся время жизни события.
     *
     * @param deltaSeconds прошедшее неотрицательное время в секундах
     * @return {@code true}, если событие завершилось
     * @throws IllegalArgumentException если время отрицательно, бесконечно или равно
     *         {@link Float#NaN}
     */
    boolean advance(float deltaSeconds) {
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0f) {
            throw new IllegalArgumentException("Прошедшее время должно быть конечным и неотрицательным");
        }
        remainingDurationSeconds = Math.max(0f, remainingDurationSeconds - deltaSeconds);
        return remainingDurationSeconds <= 0f;
    }

    /**
     * Проверяет, исчерпано ли время действия события.
     *
     * @return {@code true}, если событие завершилось
     */
    boolean isExpired() {
        return remainingDurationSeconds <= 0f;
    }

    private static String requireName(String value) {
        String checkedValue = Objects.requireNonNull(value, "Название события не задано");
        if (checkedValue.isBlank()) {
            throw new IllegalArgumentException("Название события не должно быть пустым");
        }
        return checkedValue;
    }

    private static float requirePositiveFinite(float value, String argumentName) {
        if (!Float.isFinite(value) || value <= 0f) {
            throw new IllegalArgumentException(argumentName + " должен быть конечным положительным числом");
        }
        return value;
    }

    private static float requireNonNegativeFinite(float value, String argumentName) {
        if (!Float.isFinite(value) || value < 0f) {
            throw new IllegalArgumentException(argumentName + " должен быть конечным неотрицательным числом");
        }
        return value;
    }
}
