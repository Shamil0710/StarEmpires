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
 * @param engineering fitted Stage-17.5 physical engineering state либо {@code null}
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
        ArchetypeState archetype,
        EngineeringState engineering) {

    /**
     * Compatibility constructor for pre-Stage-17.5C value code without engineering state.
     *
     * @param id persistent entity ID
     * @param identity identity state
     * @param transform transform state
     * @param inventory inventory state
     * @param wallet wallet state
     * @param market market state
     * @param production production state
     * @param priceHistory price-history state
     * @param faction faction state
     * @param reputation reputation state
     * @param ship legacy ship state
     * @param tradeAi trade-AI state
     * @param mining mining state
     * @param combat legacy combat state
     * @param asteroid asteroid state
     * @param archetype stable content archetype
     */
    public EntityState(
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
        this(
                id, identity, transform, inventory, wallet, market, production, priceHistory,
                faction, reputation, ship, tradeAi, mining, combat, asteroid, archetype, null);
    }

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
     * @param targetStock текущие effective целевые остатки
     * @param configuredTargetStock исходные station-configured baseline targets
     * @param baseConsumption базовое потребление
     * @param sellPrices цены продажи
     * @param buyPrices цены покупки
     * @param consumptionRemainder дробные остатки потребления
     * @param tradableItems маска торгуемых товаров
     * @param dirty требуется ли пересчёт рынка
     */
    public record MarketState(
            List<Integer> targetStock,
            List<Integer> configuredTargetStock,
            List<Float> baseConsumption,
            List<Float> sellPrices,
            List<Float> buyPrices,
            List<Double> consumptionRemainder,
            List<Boolean> tradableItems,
            boolean dirty) {

        /**
         * Compatibility constructor for schema v1/v2 values without explicit target provenance.
         *
         * <p>The old effective target becomes the configured baseline. This is deliberately
         * conservative: loading an older save cannot silently reduce an existing market target.</p>
         *
         * @param targetStock legacy effective targets
         * @param baseConsumption base consumption
         * @param sellPrices sell prices
         * @param buyPrices buy prices
         * @param consumptionRemainder consumption remainder
         * @param tradableItems tradable mask
         * @param dirty dirty flag
         */
        public MarketState(
                List<Integer> targetStock,
                List<Float> baseConsumption,
                List<Float> sellPrices,
                List<Float> buyPrices,
                List<Double> consumptionRemainder,
                List<Boolean> tradableItems,
                boolean dirty) {
            this(
                    targetStock,
                    targetStock,
                    baseConsumption,
                    sellPrices,
                    buyPrices,
                    consumptionRemainder,
                    tradableItems,
                    dirty);
        }
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
     * @param hull текущее состояние корпуса
     * @param maxHull максимальная прочность корпуса
     * @param shields текущие щиты
     * @param maxShields максимальные щиты
     * @param damagePerSecond урон
     * @param weaponRange дальность
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
     * @param spawnPointId stable ID точки появления
     * @param resourceItem runtime ID ресурса
     * @param initialResource исходное количество ресурса
     * @param remainingResource оставшееся количество ресурса
     */
    public record AsteroidState(
            String spawnPointId,
            int resourceItem,
            long initialResource,
            long remainingResource) {
    }

    /** @param contentId stable archetype content ID */
    public record ArchetypeState(String contentId) {
    }

    /**
     * Persistent Stage-17.5 fitted engineering state. Derived capabilities are intentionally absent.
     *
     * @param hullId stable fitted hull content ID
     * @param installedModules deterministic module-to-mount assignments
     * @param consumables physical carried/interface loads
     * @param sharedBusEnergyJ energy available on the shared ENERGY_STORAGE bus
     * @param shipHeatStoredJ heat stored on the ship heat bus
     * @param localHeatJByMount module-local heat values
     * @param thrustLimitNByMount current physical thrust ceilings
     * @param coolantBusCapacityW current physical coolant-transfer capacity
     * @param ftlCooldownSecondsByMount FTL cooldown values
     */
    public record EngineeringState(
            String hullId,
            List<InstalledModuleState> installedModules,
            EngineeringConsumableState consumables,
            double sharedBusEnergyJ,
            double shipHeatStoredJ,
            List<MountDoubleState> localHeatJByMount,
            List<MountDoubleState> thrustLimitNByMount,
            double coolantBusCapacityW,
            List<MountDoubleState> ftlCooldownSecondsByMount) {
    }

    /**
     * @param mountId hull-local mount ID
     * @param moduleId installed module content ID
     */
    public record InstalledModuleState(String mountId, String moduleId) {
    }

    /**
     * @param cargoMassKg cargo mass
     * @param storesMassKg stores mass
     * @param missionPayloadMassKg mission payload mass
     * @param missionIntegrationVolumeM3 mission integration volume
     * @param interfaceLoads physical interface loads
     */
    public record EngineeringConsumableState(
            double cargoMassKg,
            double storesMassKg,
            double missionPayloadMassKg,
            double missionIntegrationVolumeM3,
            List<EngineeringConsumableLoadState> interfaceLoads) {
    }

    /**
     * @param mountId installed module mount ID
     * @param interfaceId module-local interface ID
     * @param kindName physical interface kind enum name
     * @param amount authored physical amount
     * @param massKg physical mass
     * @param itemCount physical item count where meaningful
     */
    public record EngineeringConsumableLoadState(
            String mountId,
            String interfaceId,
            String kindName,
            double amount,
            double massKg,
            long itemCount) {
    }

    /**
     * Deterministically ordered mount-to-double row used instead of serializing JVM maps.
     *
     * @param mountId hull-local mount ID
     * @param value finite non-negative physical value
     */
    public record MountDoubleState(String mountId, double value) {
    }
}
