package com.spacesim.systems;

import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.core.*;
import com.spacesim.components.*;
import com.spacesim.constants.Constants;
import com.spacesim.model.Recipe;

/**
 * Выполняет производство товаров по активным рецептам сущностей.
 *
 * <p>Система накапливает время в {@link ProductionComponent#progressSeconds}. Число полностью
 * завершённых циклов определяется делением суммы сохранённого прогресса и времени кадра на
 * длительность рецепта. Благодаря этому большой кадр обрабатывается пакетно, без цикла по каждой
 * произведённой единице.</p>
 *
 * <p>Размер пакета дополнительно ограничивается доступными входными товарами, вместимостью
 * инвентаря и диапазоном {@code int} для каждого запаса. При вычислении используется чистое
 * изменение товара за цикл ({@code output - input}), поэтому корректно поддерживаются рецепты,
 * одновременно потребляющие и производящие один товар. После фактического изменения инвентаря
 * связанный {@link MarketComponent} помечается как {@link MarketComponent#isDirty dirty}.</p>
 *
 * <p>Если активного рецепта нет либо даже один следующий цикл невозможен, накопленный прогресс
 * сбрасывается. Некорректное или неположительное время кадра игнорируется. Когда пакет исчерпал
 * ресурсы, неиспользованное время также не переносится; в остальных случаях сохраняется остаток от
 * деления на длительность рецепта.</p>
 */
public class ProductionSystem extends IteratingSystem {
    /** Маркер отсутствия ресурсного ограничения для рецепта с нулевыми чистыми изменениями. */
    private static final long UNLIMITED_CYCLES = Long.MAX_VALUE;

    /** Быстрый доступ к запасам производственной сущности. */
    private final ComponentMapper<InventoryComponent> im = ComponentMapper.getFor(InventoryComponent.class);
    /** Быстрый доступ к рецептам и накопленному производственному прогрессу. */
    private final ComponentMapper<ProductionComponent> pm = ComponentMapper.getFor(ProductionComponent.class);
    /** Доступ к необязательному рынку, который требуется пометить после изменения запасов. */
    private final ComponentMapper<MarketComponent> mm = ComponentMapper.getFor(MarketComponent.class);

    /**
     * Создаёт систему для сущностей с инвентарём и производственным компонентом.
     */
    public ProductionSystem() {
        super(Family.all(InventoryComponent.class, ProductionComponent.class).get());
    }

    /**
     * Продвигает активный рецепт одной производственной сущности.
     *
     * <p>Метод сначала проверяет возможность одного цикла, затем вычисляет число завершённых по
     * времени циклов и применяет максимально допустимую их часть одной пакетной операцией.</p>
     *
     * @param entity сущность с {@link InventoryComponent} и {@link ProductionComponent}
     * @param deltaTime прошедшее с предыдущего обновления время в секундах
     */
    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        if (!Float.isFinite(deltaTime) || deltaTime <= 0f) {
            return;
        }

        InventoryComponent inventory = im.get(entity);
        ProductionComponent production = pm.get(entity);
        Recipe recipe = production.getActiveRecipe();

        if (recipe == null) {
            production.progressSeconds = 0f;
            return;
        }

        if (!canProduce(inventory, recipe)) {
            production.progressSeconds = 0f;
            return;
        }

        double elapsedSeconds = normalizedProgress(production.progressSeconds) + deltaTime;
        double completedCycles = Math.floor(elapsedSeconds / recipe.durationSeconds);
        if (completedCycles < 1d) {
            production.progressSeconds = (float) elapsedSeconds;
            return;
        }

        long maximumCycles = getMaximumProducibleCycles(inventory, recipe);
        boolean resourcesExhausted = false;
        boolean inventoryChanged = maximumCycles != UNLIMITED_CYCLES;

        if (inventoryChanged) {
            long cyclesToApply = completedCycles >= maximumCycles
                    ? maximumCycles
                    : (long) completedCycles;
            applyRecipe(inventory, recipe, cyclesToApply);
            resourcesExhausted = !canProduce(inventory, recipe);
        }

        production.progressSeconds = resourcesExhausted
                ? 0f
                : (float) (elapsedSeconds % recipe.durationSeconds);

        if (inventoryChanged && mm.has(entity)) {
            mm.get(entity).isDirty = true;
        }
    }

    /**
     * Вычисляет максимальное число циклов, которое можно безопасно применить одним пакетом.
     *
     * <p>Для товара с отрицательным чистым изменением границей служит момент исчерпания входного
     * запаса, для положительного — переполнение {@code int}. Если суммарный объём увеличивается,
     * добавляется ограничение общей вместимости. Значение {@link #UNLIMITED_CYCLES} означает, что
     * рецепт не меняет ни один запас и потому не имеет ресурсной границы.</p>
     *
     * @param inventory исходный инвентарь
     * @param recipe применяемый рецепт
     * @return максимальное неотрицательное число безопасных циклов либо
     *         {@link #UNLIMITED_CYCLES}
     */
    private long getMaximumProducibleCycles(InventoryComponent inventory, Recipe recipe) {
        long maximumCycles = UNLIMITED_CYCLES;
        long totalStock = 0L;
        long totalDeltaPerCycle = 0L;

        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            long stock = inventory.stock[itemId];
            long inputAmount = recipe.getInputAmount(itemId);
            long outputAmount = recipe.getOutputAmount(itemId);
            long itemDeltaPerCycle = outputAmount - inputAmount;

            totalStock += stock;
            totalDeltaPerCycle += itemDeltaPerCycle;

            if (itemDeltaPerCycle < 0L) {
                long cyclesUntilExhaustion = (stock - inputAmount) / -itemDeltaPerCycle + 1L;
                maximumCycles = Math.min(maximumCycles, cyclesUntilExhaustion);
            } else if (itemDeltaPerCycle > 0L) {
                long cyclesUntilOverflow = (Integer.MAX_VALUE - stock) / itemDeltaPerCycle;
                maximumCycles = Math.min(maximumCycles, cyclesUntilOverflow);
            }
        }

        if (totalDeltaPerCycle > 0L) {
            long cyclesUntilCapacity = ((long) inventory.capacity - totalStock) / totalDeltaPerCycle;
            maximumCycles = Math.min(maximumCycles, cyclesUntilCapacity);
        }

        return maximumCycles;
    }

    /**
     * Проверяет возможность выполнить ровно один цикл рецепта.
     *
     * <p>Проверка отклоняет отрицательные или недостаточные запасы, переполнение отдельного товара
     * и превышение общей вместимости после одновременного применения всех входов и выходов.</p>
     *
     * @param inventory проверяемый инвентарь
     * @param recipe проверяемый рецепт
     * @return {@code true}, если один цикл можно применить без нарушения ограничений
     */
    private boolean canProduce(InventoryComponent inventory, Recipe recipe) {
        long resultingTotalStock = 0L;

        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            int stock = inventory.stock[itemId];
            int inputAmount = recipe.getInputAmount(itemId);
            int outputAmount = recipe.getOutputAmount(itemId);

            if (stock < 0 || stock < inputAmount) {
                return false;
            }

            long resultingItemStock = (long) stock - inputAmount + outputAmount;
            if (resultingItemStock > Integer.MAX_VALUE) {
                return false;
            }
            resultingTotalStock += resultingItemStock;
        }

        return resultingTotalStock <= inventory.capacity;
    }

    /**
     * Применяет чистые изменения рецепта сразу для заданного числа циклов.
     *
     * <p>Метод полагается на предварительный расчёт
     * {@link #getMaximumProducibleCycles(InventoryComponent, Recipe)} и сам повторно не проверяет
     * вместимость или переполнение.</p>
     *
     * @param inventory изменяемый инвентарь
     * @param recipe применяемый рецепт
     * @param cycles число циклов, гарантированно допустимое предварительной проверкой
     */
    private void applyRecipe(InventoryComponent inventory, Recipe recipe, long cycles) {
        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            long itemDeltaPerCycle = (long) recipe.getOutputAmount(itemId)
                    - recipe.getInputAmount(itemId);
            long resultingStock = inventory.stock[itemId] + itemDeltaPerCycle * cycles;
            inventory.stock[itemId] = (int) resultingStock;
        }
    }

    /**
     * Нормализует ранее сохранённый прогресс перед накоплением времени.
     *
     * @param progressSeconds сохранённый прогресс в секундах
     * @return исходное неотрицательное конечное значение либо {@code 0}, если значение некорректно
     */
    private double normalizedProgress(float progressSeconds) {
        if (!Float.isFinite(progressSeconds) || progressSeconds < 0f) {
            return 0d;
        }
        return progressSeconds;
    }
}
