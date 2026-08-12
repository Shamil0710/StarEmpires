package com.spacesim.persistence;

import java.util.List;

/**
 * Value-based сериализуемый снимок одной ECS-сущности и всех поддерживаемых компонентов.
 *
 * <p>DTO не содержит Ashley {@code Entity}, libGDX-векторов или изменяемых коллекций компонентов.
 * Отсутствующий компонент представлен {@code null}. Списки создаются immutable mapper/codec-слоем,
 * поэтому record equality подходит для точного round-trip и continuation-тестов.</p>
 *
 * @param id устойчивый обязательный ID сущности
 * @param identity пользовательская идентичность либо {@code null}
 * @param transform положение/скорость либо {@code null}
 * @param inventory склад либо {@code null}
 * @param wallet баланс либо {@code null}
 * @param market рынок либо {@code null}
 * @param production производство либо {@code null}
 * @param priceHistory история цен либо {@code null}
 * @param faction фракция либо {@code null}
 * @param reputation репутация либо {@code null}
 * @param ship корабельный тип либо {@code null}
 * @param tradeAi состояние торгового AI либо {@code null}
 * @param mining состояние добычи либо {@code null}
 * @param combat боевое состояние либо {@code null}
 * @param asteroid природный ресурс либо {@code null}
 * @param archetype stable content archetype либо {@code null} для legacy/dynamic сущности
 */
public record EntityState(
        EntityId id,
        IdentityState identity,
        TransformState transform,
        InventoryState inventory,
        WalletState wallet,
        MarketState market,
        ProductionState production,
        PriceHistoryState priceHistory,
        FactionState faction,
        ReputationState reputation,
        ShipState ship,
        TradeAiState tradeAi,
        MiningState mining,
        CombatState combat,
        AsteroidState asteroid,
        ArchetypeState archetype) {

    /**
     * @param name отображаемое имя
     * @param kindName имя {@code IdentityComponent.Kind}
     */
    public record IdentityState(String name, String kindName) {
    }

    /**
     * @param x координата X
     * @param y координата Y
     * @param velocityX скорость X
     * @param velocityY скорость Y
     */
    public record TransformState(float x, float y, float velocityX, float velocityY) {
    }

    /**
     * @param capacity общая вместимость
     * @param stock остатки всех runtime-товаров по item ID
     */
    public record InventoryState(int capacity, List<Integer> stock) {
    }

    /** @param balanceMilliCredits authoritative баланс в milli-credits */
    public record WalletState(long balanceMilliCredits) {
    }

    /**
     * @param targetStock целевые остатки
     * @param baseConsumption базовое потребление
     * @param sellPrices цены продажи
     * @param buyPrices цены покупки
     * @param consumptionRemainder дробные остатки потребления
     * @param tradableItems маска торгуемых товаров
     * @param dirty требуется ли пересчёт рынка
     */
    public record MarketState(
            List<Integer> targetStock,
            List<Float> baseConsumption,
            List<Float> sellPrices,
            List<Float> buyPrices,
            List<Double> consumptionRemainder,
            List<Boolean> tradableItems,
            boolean dirty) {
    }

    /**
     * @param recipes упорядоченный каталог рецептов сущности
     * @param activeRecipeIndex индекс активного рецепта
     * @param progressSeconds прогресс текущего цикла
     */
    public record ProductionState(
            List<RecipeState> recipes,
            int activeRecipeIndex,
            float progressSeconds) {
    }

    /**
     * @param name имя рецепта
     * @param durationSeconds длительность цикла
     * @param inputs входы по item ID
     * @param outputs выходы по item ID
     */
    public record RecipeState(
            String name,
            float durationSeconds,
            List<Integer> inputs,
            List<Integer> outputs) {
    }

    /**
     * @param maxPoints лимит точек на товар
     * @param history список ценовых рядов по item ID
     */
    public record PriceHistoryState(int maxPoints, List<List<Float>> history) {
    }

    /** @param factionId runtime ID фракции */
    public record FactionState(int factionId) {
    }

    /** @param values значения отношений по faction ID */
    public record ReputationState(List<Float> values) {
    }

    /** @param typeName имя {@code ShipType} либо {@code null}, если тип повреждён/не настроен */
    public record ShipState(String typeName) {
    }

    /**
     * @param stateName имя состояния FSM либо {@code null}
     * @param buyStationId станция покупки либо {@code null}
     * @param sellStationId станция продажи либо {@code null}
     * @param targetStationId текущая цель либо {@code null}
     * @param targetItem выбранный товар
     * @param specializedItem специализация
     * @param targetAmount выбранное количество
     * @param cargoSpace AI-ограничение трюма
     * @param movementSpeed скорость
     * @param expectedProfitMilliCredits ожидаемая валовая прибыль
     * @param routeSearchCooldown cooldown поиска
     */
    public record TradeAiState(
            String stateName,
            EntityId buyStationId,
            EntityId sellStationId,
            EntityId targetStationId,
            int targetItem,
            int specializedItem,
            int targetAmount,
            int cargoSpace,
            float movementSpeed,
            long expectedProfitMilliCredits,
            float routeSearchCooldown) {
    }

    /**
     * @param resourceItem добываемый товар
     * @param extractionPerSecond производительность
     * @param movementSpeed скорость полёта
     * @param extractionRange радиус добычи
     * @param dockingRange радиус разгрузки
     * @param extractionRemainder дробный остаток добычи
     * @param totalMined всего добыто
     * @param totalDelivered всего доставлено
     * @param active включено ли оборудование
     * @param stateName имя состояния FSM либо {@code null}
     * @param targetAsteroidId цель добычи либо {@code null}
     * @param homeBaseId база разгрузки либо {@code null}
     */
    public record MiningState(
            int resourceItem,
            float extractionPerSecond,
            float movementSpeed,
            float extractionRange,
            float dockingRange,
            double extractionRemainder,
            long totalMined,
            long totalDelivered,
            boolean active,
            String stateName,
            EntityId targetAsteroidId,
            EntityId homeBaseId) {
    }

    /**
     * @param hull текущий корпус
     * @param maxHull максимальный корпус
     * @param shields текущий щит
     * @param maxShields максимальный щит
     * @param damagePerSecond урон в секунду
     * @param weaponRange дальность оружия
     */
    public record CombatState(
            float hull,
            float maxHull,
            float shields,
            float maxShields,
            float damagePerSecond,
            float weaponRange) {
    }

    /**
     * @param spawnPointId стабильный ID точки пояса
     * @param resourceItem товар ресурса
     * @param initialResource первоначальный запас
     * @param remainingResource текущий остаток
     */
    public record AsteroidState(
            String spawnPointId,
            int resourceItem,
            long initialResource,
            long remainingResource) {
    }

    /** @param contentId stable station/ship archetype content ID */
    public record ArchetypeState(String contentId) {
    }
}
