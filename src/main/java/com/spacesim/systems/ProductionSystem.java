package com.spacesim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.constants.Constants;
import com.spacesim.economy.EconomicLedger;
import com.spacesim.model.Recipe;

import java.util.Objects;

/**
 * Выполняет производство товаров по активным рецептам сущностей.
 *
 * <p>Система накапливает время в {@link ProductionComponent#progressSeconds}. Число полностью
 * завершённых циклов определяется делением суммы сохранённого прогресса и времени tick на
 * длительность рецепта. Благодаря этому большой интервал обрабатывается пакетно, без цикла по
 * каждой произведённой единице.</p>
 *
 * <p>Размер пакета ограничивается доступными входными товарами, вместимостью инвентаря и диапазоном
 * {@code int}. Фактически применённый пакет является явным {@code RESOURCE_TRANSFORM} и фиксируется
 * в {@link EconomicLedger}; производство не маскируется под независимые resource source/sink.</p>
 */
public class ProductionSystem extends IteratingSystem {
    private static final long UNLIMITED_CYCLES = Long.MAX_VALUE;

    private final ComponentMapper<InventoryComponent> im = ComponentMapper.getFor(InventoryComponent.class);
    private final ComponentMapper<ProductionComponent> pm = ComponentMapper.getFor(ProductionComponent.class);
    private final ComponentMapper<MarketComponent> mm = ComponentMapper.getFor(MarketComponent.class);
    private final ComponentMapper<IdentityComponent> identityMapper = ComponentMapper.getFor(IdentityComponent.class);
    private final EconomicLedger ledger;

    /** Создаёт систему с собственным диагностическим ledger. */
    public ProductionSystem() {
        this(new EconomicLedger());
    }

    /**
     * Создаёт систему, записывающую фактические производственные преобразования в общий журнал.
     *
     * @param ledger общий экономический журнал игровой сессии
     * @throws NullPointerException если журнал не задан
     */
    public ProductionSystem(EconomicLedger ledger) {
        super(Family.all(InventoryComponent.class, ProductionComponent.class).get());
        this.ledger = Objects.requireNonNull(ledger, "EconomicLedger не задан");
    }

    /** @return ledger, в который записываются производственные transform-операции */
    public EconomicLedger getLedger() {
        return ledger;
    }

    /**
     * Продвигает активный рецепт одной производственной сущности.
     *
     * @param entity сущность с инвентарём и производственным компонентом
     * @param deltaTime прошедшее игровое время в секундах
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
        long cyclesApplied = 0L;

        if (inventoryChanged) {
            cyclesApplied = completedCycles >= maximumCycles
                    ? maximumCycles
                    : (long) completedCycles;
            if (cyclesApplied > 0L) {
                applyRecipe(inventory, recipe, cyclesApplied);
            }
            resourcesExhausted = !canProduce(inventory, recipe);
        }

        production.progressSeconds = resourcesExhausted
                ? 0f
                : (float) (elapsedSeconds % recipe.durationSeconds);

        if (cyclesApplied > 0L) {
            if (mm.has(entity)) {
                mm.get(entity).isDirty = true;
            }
            ledger.recordResourceTransform(
                    entityName(entity),
                    recipe.name + " x" + cyclesApplied);
        }
    }

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

    private void applyRecipe(InventoryComponent inventory, Recipe recipe, long cycles) {
        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            long itemDeltaPerCycle = (long) recipe.getOutputAmount(itemId)
                    - recipe.getInputAmount(itemId);
            long resultingStock = inventory.stock[itemId] + itemDeltaPerCycle * cycles;
            inventory.stock[itemId] = (int) resultingStock;
        }
    }

    private double normalizedProgress(float progressSeconds) {
        if (!Float.isFinite(progressSeconds) || progressSeconds < 0f) {
            return 0d;
        }
        return progressSeconds;
    }

    private String entityName(Entity entity) {
        IdentityComponent identity = identityMapper.get(entity);
        return identity == null || identity.name == null || identity.name.isBlank()
                ? "UNIDENTIFIED"
                : identity.name;
    }
}
