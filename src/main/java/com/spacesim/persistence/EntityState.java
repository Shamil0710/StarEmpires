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
 * @param sensorKnowledge system-local Stage-17.5D/H information state либо {@code null}
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
        EngineeringState engineering,
        SensorKnowledgeState sensorKnowledge) {

    /**
     * Compatibility constructor for Stage-17.5C–G value code without sensor knowledge persistence.
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
     * @param engineering fitted engineering state
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
            ArchetypeState archetype,
            EngineeringState engineering) {
        this(id, identity, transform, inventory, wallet, market, production, priceHistory,
                faction, reputation, ship, tradeAi, mining, combat, asteroid, archetype,
                engineering, null);
    }

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
        this(id, identity, transform, inventory, wallet, market, production, priceHistory,
                faction, reputation, ship, tradeAi, mining, combat, asteroid, archetype,
                null, null);
    }

    /** @param name отображаемое имя @param kindName имя {@code IdentityComponent.Kind} */
    public record IdentityState(String name, String kindName) { }

    /** @param x координата X @param y координата Y @param velocityX скорость X @param velocityY скорость Y */
    public record TransformState(float x, float y, float velocityX, float velocityY) { }

    /** @param capacity общая вместимость @param stock остатки всех runtime-товаров по item ID */
    public record InventoryState(int capacity, List<Integer> stock) { }

    /** @param balanceMilliCredits authoritative баланс в milli-credits */
    public record WalletState(long balanceMilliCredits) { }

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
        /** Compatibility constructor for schema v1/v2 values without explicit target provenance. */
        public MarketState(
                List<Integer> targetStock,
                List<Float> baseConsumption,
                List<Float> sellPrices,
                List<Float> buyPrices,
                List<Double> consumptionRemainder,
                List<Boolean> tradableItems,
                boolean dirty) {
            this(targetStock, targetStock, baseConsumption, sellPrices, buyPrices,
                    consumptionRemainder, tradableItems, dirty);
        }
    }

    /** @param recipes recipes @param activeRecipeIndex active recipe @param progressSeconds progress */
    public record ProductionState(List<RecipeState> recipes, int activeRecipeIndex, float progressSeconds) { }

    /** @param name name @param durationSeconds duration @param inputs inputs @param outputs outputs */
    public record RecipeState(String name, float durationSeconds, List<Integer> inputs, List<Integer> outputs) { }

    /** @param maxPoints history limit @param history price history */
    public record PriceHistoryState(int maxPoints, List<List<Float>> history) { }

    /** @param factionId runtime ID фракции */
    public record FactionState(int factionId) { }

    /** @param values значения отношений по faction ID */
    public record ReputationState(List<Float> values) { }

    /** @param typeName имя {@code ShipType} либо {@code null} */
    public record ShipState(String typeName) { }

    /**
     * @param stateName state name
     * @param buyStationId buy station
     * @param sellStationId sell station
     * @param targetStationId target station
     * @param targetItem item
     * @param specializedItem specialization
     * @param targetAmount amount
     * @param cargoSpace cargo
     * @param movementSpeed movement speed
     * @param expectedProfitMilliCredits expected profit
     * @param routeSearchCooldown cooldown
     */
    public record TradeAiState(
            String stateName, EntityId buyStationId, EntityId sellStationId, EntityId targetStationId,
            int targetItem, int specializedItem, int targetAmount, int cargoSpace, float movementSpeed,
            long expectedProfitMilliCredits, float routeSearchCooldown) { }

    /**
     * @param resourceItem resource
     * @param extractionPerSecond extraction
     * @param movementSpeed speed
     * @param extractionRange range
     * @param dockingRange docking range
     * @param extractionRemainder remainder
     * @param totalMined total mined
     * @param totalDelivered total delivered
     * @param active active
     * @param stateName state
     * @param targetAsteroidId target
     * @param homeBaseId home
     */
    public record MiningState(
            int resourceItem, float extractionPerSecond, float movementSpeed, float extractionRange,
            float dockingRange, double extractionRemainder, long totalMined, long totalDelivered,
            boolean active, String stateName, EntityId targetAsteroidId, EntityId homeBaseId) { }

    /** @param hull hull @param maxHull max hull @param shields shields @param maxShields max shields
     * @param damagePerSecond damage @param weaponRange range */
    public record CombatState(
            float hull, float maxHull, float shields, float maxShields,
            float damagePerSecond, float weaponRange) { }

    /** @param spawnPointId point @param resourceItem item @param initialResource initial @param remainingResource remaining */
    public record AsteroidState(String spawnPointId, int resourceItem, long initialResource, long remainingResource) { }

    /** @param contentId stable archetype content ID */
    public record ArchetypeState(String contentId) { }

    /**
     * Persistent Stage-17.5 fitted engineering state. Derived capabilities are intentionally absent.
     *
     * @param hullId stable fitted hull content ID
     * @param installedModules deterministic module-to-mount assignments
     * @param consumables physical carried/interface loads
     * @param sharedBusEnergyJ shared ENERGY_STORAGE energy
     * @param shipHeatStoredJ ship-bus heat
     * @param localHeatJByMount module-local heat
     * @param thrustLimitNByMount physical thrust ceilings
     * @param coolantBusCapacityW coolant-transfer capacity
     * @param ftlCooldownSecondsByMount FTL cooldowns
     * @param instanceState Stage-17.5H local damage/shield/maintenance/weapon continuity
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
            List<MountDoubleState> ftlCooldownSecondsByMount,
            ShipInstanceState instanceState) {
        /** Compatibility constructor for core GameState v4 payloads without Stage-17.5H extension. */
        public EngineeringState(
                String hullId,
                List<InstalledModuleState> installedModules,
                EngineeringConsumableState consumables,
                double sharedBusEnergyJ,
                double shipHeatStoredJ,
                List<MountDoubleState> localHeatJByMount,
                List<MountDoubleState> thrustLimitNByMount,
                double coolantBusCapacityW,
                List<MountDoubleState> ftlCooldownSecondsByMount) {
            this(hullId, installedModules, consumables, sharedBusEnergyJ, shipHeatStoredJ,
                    localHeatJByMount, thrustLimitNByMount, coolantBusCapacityW,
                    ftlCooldownSecondsByMount, null);
        }
    }

    /** @param mountId hull-local mount ID @param moduleId installed module content ID */
    public record InstalledModuleState(String mountId, String moduleId) { }

    /**
     * @param cargoMassKg cargo mass
     * @param storesMassKg stores mass
     * @param missionPayloadMassKg mission payload mass
     * @param missionIntegrationVolumeM3 mission integration volume
     * @param interfaceLoads physical interface loads
     */
    public record EngineeringConsumableState(
            double cargoMassKg, double storesMassKg, double missionPayloadMassKg,
            double missionIntegrationVolumeM3, List<EngineeringConsumableLoadState> interfaceLoads) { }

    /**
     * @param mountId installed module mount ID
     * @param interfaceId module-local interface ID
     * @param kindName physical interface kind enum name
     * @param amount authored physical amount
     * @param massKg physical mass
     * @param itemCount physical item count
     */
    public record EngineeringConsumableLoadState(
            String mountId, String interfaceId, String kindName,
            double amount, double massKg, long itemCount) { }

    /** @param mountId deterministic key @param value finite physical value */
    public record MountDoubleState(String mountId, double value) { }

    /**
     * Stage-17.5H persistent physical state outside Stage-17.5C operating buses.
     *
     * @param compartmentIntegrityById local compartment integrity
     * @param moduleIntegrityByMount local installed module integrity
     * @param shieldsByMount shield reserve/collapse state
     * @param serviceAgeByMount scheduled-service age
     * @param weaponFeeds ammunition identity bindings
     * @param weaponCooldownByMount launcher cooldowns
     */
    public record ShipInstanceState(
            List<MountDoubleState> compartmentIntegrityById,
            List<MountDoubleState> moduleIntegrityByMount,
            List<ShieldRuntimeState> shieldsByMount,
            List<MountDoubleState> serviceAgeByMount,
            List<WeaponFeedState> weaponFeeds,
            List<MountDoubleState> weaponCooldownByMount) { }

    /**
     * @param mountId shield emitter mount
     * @param reserveJ current field reserve
     * @param accumulatedHeatJ shield-local accumulated heat
     * @param collapsed collapse flag
     * @param restartRemainingSeconds restart lockout
     * @param emitterIntegrity persisted emitter integrity
     */
    public record ShieldRuntimeState(
            String mountId, double reserveJ, double accumulatedHeatJ, boolean collapsed,
            double restartRemainingSeconds, double emitterIntegrity) { }

    /** @param mountId weapon mount @param interfaceId feed ID @param ammunitionContentId ammunition content ID */
    public record WeaponFeedState(String mountId, String interfaceId, String ammunitionContentId) { }

    /**
     * System-local sensor knowledge persisted only while the entity remains in the same identity domain.
     *
     * @param tracks fused tracks
     * @param receivedMeasurements delivered measurement history
     * @param pendingMeasurements in-flight datalink deliveries
     */
    public record SensorKnowledgeState(
            List<SensorTrackState> tracks,
            List<SensorMeasurementState> receivedMeasurements,
            List<PendingSensorMeasurementState> pendingMeasurements) { }

    /**
     * @param targetId target ID
     * @param informationStateName information-state enum name
     * @param positionKnown whether position is solved
     * @param estimatedXM estimated x
     * @param estimatedYM estimated y
     * @param positionVarianceM2 optional position variance
     * @param bearingVarianceRad2 bearing variance
     * @param rangeVarianceM2 optional range variance
     * @param classificationConfidence classification evidence
     * @param lastMeasurementSeconds freshest measurement time
     * @param contributingObservers distinct observers
     * @param fusedMeasurementCount fused measurement count
     */
    public record SensorTrackState(
            long targetId, String informationStateName, boolean positionKnown,
            double estimatedXM, double estimatedYM, Double positionVarianceM2,
            double bearingVarianceRad2, Double rangeVarianceM2,
            double classificationConfidence, double lastMeasurementSeconds,
            int contributingObservers, int fusedMeasurementCount) { }

    /**
     * @param observerId observer ID
     * @param targetId target ID
     * @param channelName signature channel name
     * @param timestampSeconds measurement time
     * @param observerXM observer x
     * @param observerYM observer y
     * @param bearingRad bearing
     * @param rangeM optional range
     * @param bearingVarianceRad2 bearing variance
     * @param rangeVarianceM2 optional range variance
     * @param receivedSignalPowerW received signal
     * @param effectiveInterferencePowerW effective interference
     * @param snr signal-to-noise-plus-interference ratio
     * @param evidenceStateName evidence information-state name
     */
    public record SensorMeasurementState(
            long observerId, long targetId, String channelName, double timestampSeconds,
            double observerXM, double observerYM, double bearingRad, Double rangeM,
            double bearingVarianceRad2, Double rangeVarianceM2, double receivedSignalPowerW,
            double effectiveInterferencePowerW, double snr, String evidenceStateName) { }

    /** @param measurement transmitted measurement @param deliverAtSeconds delivery time */
    public record PendingSensorMeasurementState(SensorMeasurementState measurement, double deliverAtSeconds) { }
}
