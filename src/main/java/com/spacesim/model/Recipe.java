package com.spacesim.model;

import com.spacesim.constants.Constants;

/**
 * Описание одного повторяемого производственного цикла.
 *
 * <p>Для каждого товара рецепт хранит количество входных и выходных единиц.
 * Неуказанное значение равно нулю, а повторный вызов {@link #input(int, int)}
 * или {@link #output(int, int)} заменяет предыдущее значение для того же
 * товара. После добавления рецепта в
 * {@link com.spacesim.components.ProductionComponent} его конфигурацию не
 * следует менять: {@link com.spacesim.systems.ProductionSystem} читает её без
 * дополнительной синхронизации.</p>
 *
 * <p>Количество товара измеряется в целых складских единицах, длительность —
 * в секундах игрового времени.</p>
 */
public class Recipe {
    /** Непустое отображаемое имя рецепта. */
    public final String name;

    /** Конечная положительная длительность одного цикла в секундах. */
    public final float durationSeconds;
    private final int[] inputItems = new int[Constants.MAX_ITEMS];
    private final int[] outputItems = new int[Constants.MAX_ITEMS];

    /**
     * Создаёт рецепт без заданных входов и выходов.
     *
     * @param name отображаемое имя; не может быть {@code null}, пустым или
     *             состоять только из пробельных символов
     * @param durationSeconds длительность цикла в секундах; должна быть
     *                        конечным числом строго больше нуля
     * @throws IllegalArgumentException если имя или длительность нарушают
     *                                  указанные инварианты
     */
    public Recipe(String name, float durationSeconds) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Recipe name must not be blank");
        }
        if (!Float.isFinite(durationSeconds) || durationSeconds <= 0f) {
            throw new IllegalArgumentException("Recipe duration must be finite and positive");
        }

        this.name = name;
        this.durationSeconds = durationSeconds;
    }

    /**
     * Задаёт расход товара за один цикл.
     *
     * @param itemId идентификатор товара в диапазоне
     *               {@code [0, Constants.MAX_ITEMS)}
     * @param amount положительное количество целых единиц
     * @return этот рецепт для цепочки конфигурационных вызовов
     * @throws IllegalArgumentException если идентификатор или количество
     *                                  недопустимы
     */
    public Recipe input(int itemId, int amount) {
        validateItemId(itemId);
        validateAmount(amount);
        inputItems[itemId] = amount;
        return this;
    }

    /**
     * Задаёт выпуск товара за один цикл.
     *
     * @param itemId идентификатор товара в диапазоне
     *               {@code [0, Constants.MAX_ITEMS)}
     * @param amount положительное количество целых единиц
     * @return этот рецепт для цепочки конфигурационных вызовов
     * @throws IllegalArgumentException если идентификатор или количество
     *                                  недопустимы
     */
    public Recipe output(int itemId, int amount) {
        validateItemId(itemId);
        validateAmount(amount);
        outputItems[itemId] = amount;
        return this;
    }

    /**
     * Возвращает расход выбранного товара за цикл.
     *
     * @param itemId идентификатор товара в диапазоне
     *               {@code [0, Constants.MAX_ITEMS)}
     * @return неотрицательное количество входных единиц; {@code 0}, если
     *         товар не был настроен как вход
     * @throws IllegalArgumentException если идентификатор недопустим
     */
    public int getInputAmount(int itemId) {
        validateItemId(itemId);
        return inputItems[itemId];
    }

    /**
     * Возвращает выпуск выбранного товара за цикл.
     *
     * @param itemId идентификатор товара в диапазоне
     *               {@code [0, Constants.MAX_ITEMS)}
     * @return неотрицательное количество выходных единиц; {@code 0}, если
     *         товар не был настроен как выход
     * @throws IllegalArgumentException если идентификатор недопустим
     */
    public int getOutputAmount(int itemId) {
        validateItemId(itemId);
        return outputItems[itemId];
    }

    private void validateItemId(int itemId) {
        if (itemId < 0 || itemId >= Constants.MAX_ITEMS) {
            throw new IllegalArgumentException("Invalid item id: " + itemId);
        }
    }

    private void validateAmount(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Item amount must be positive");
        }
    }
}
