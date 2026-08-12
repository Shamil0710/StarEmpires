package com.spacesim.components;

import com.badlogic.ashley.core.Component;

/**
 * Изменяемые базовые характеристики боевого корабля.
 *
 * <p>Корпус и щиты выражаются в условных единицах прочности, урон — в единицах прочности в
 * секунду, дальность — в мировых единицах. Компонент пока является доменными данными и не выбирает
 * цель самостоятельно. Поскольку поля открыты для будущей боевой системы, метод
 * {@link #isOperational()} повторно проверяет их после любых внешних изменений.</p>
 */
public class CombatComponent implements Component {
    /** Текущая прочность корпуса. */
    public float hull = 100f;

    /** Максимальная прочность корпуса; штатно строго положительна. */
    public float maxHull = 100f;

    /** Текущий запас щитов. */
    public float shields = 50f;

    /** Максимальный запас щитов; может быть нулевым у корабля без щитов. */
    public float maxShields = 50f;

    /** Номинальный непрерывный урон в секунду. */
    public float damagePerSecond = 10f;

    /** Максимальная дальность применения оружия в мировых единицах. */
    public float weaponRange = 100f;

    /** Создаёт исправный боевой компонент со стандартными характеристиками. */
    public CombatComponent() {
    }

    /**
     * Создаёт боевой компонент с явно заданным текущим и максимальным состоянием.
     *
     * <p>Нулевой корпус, урон или дальность допустимы как небоеспособное состояние, но
     * {@link #isOperational()} для него вернёт {@code false}.</p>
     *
     * @param hull текущая неотрицательная прочность корпуса
     * @param maxHull конечная строго положительная максимальная прочность корпуса
     * @param shields текущий неотрицательный запас щитов
     * @param maxShields конечный неотрицательный максимальный запас щитов
     * @param damagePerSecond конечный неотрицательный урон в секунду
     * @param weaponRange конечная неотрицательная дальность оружия
     * @throws IllegalArgumentException если значение неконечно, отрицательно, превышает свой
     *                                  максимум либо максимальный корпус неположителен
     */
    public CombatComponent(float hull, float maxHull, float shields, float maxShields,
                           float damagePerSecond, float weaponRange) {
        validateState(hull, maxHull, shields, maxShields, damagePerSecond, weaponRange);
        this.hull = hull;
        this.maxHull = maxHull;
        this.shields = shields;
        this.maxShields = maxShields;
        this.damagePerSecond = damagePerSecond;
        this.weaponRange = weaponRange;
    }

    /**
     * Проверяет целостность характеристик и способность корабля вести бой.
     *
     * @return {@code true}, если все числа конечны, текущие запасы находятся в границах, корпус
     *         не уничтожен, а урон и дальность строго положительны
     */
    public boolean isOperational() {
        return isValidState(hull, maxHull, shields, maxShields, damagePerSecond, weaponRange)
                && hull > 0f
                && damagePerSecond > 0f
                && weaponRange > 0f;
    }

    private static void validateState(float hull, float maxHull, float shields, float maxShields,
                                      float damagePerSecond, float weaponRange) {
        if (!isValidState(hull, maxHull, shields, maxShields, damagePerSecond, weaponRange)) {
            throw new IllegalArgumentException("Некорректные боевые характеристики корабля");
        }
    }

    private static boolean isValidState(float hull, float maxHull, float shields, float maxShields,
                                        float damagePerSecond, float weaponRange) {
        return Float.isFinite(hull)
                && Float.isFinite(maxHull)
                && Float.isFinite(shields)
                && Float.isFinite(maxShields)
                && Float.isFinite(damagePerSecond)
                && Float.isFinite(weaponRange)
                && maxHull > 0f
                && hull >= 0f
                && hull <= maxHull
                && maxShields >= 0f
                && shields >= 0f
                && shields <= maxShields
                && damagePerSecond >= 0f
                && weaponRange >= 0f;
    }
}
