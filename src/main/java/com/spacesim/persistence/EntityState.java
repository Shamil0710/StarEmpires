package com.spacesim.persistence;

import java.util.List;

/**
 * Value-based serializable snapshot of one ECS entity and all supported persistent components.
 *
 * @param id stable required entity ID
 * @param identity identity state or {@code null}
 * @param transform transform state or {@code null}
 * @param inventory inventory state or {@code null}
 * @param wallet wallet state or {@code null}
 * @param market market state or {@code null}
 * @param production production state or {@code null}
 * @param priceHistory price history or {@code null}
 * @param faction faction state or {@code null}
 * @param reputation reputation state or {@code null}
 * @param ship legacy ship classification or {@code null}
 * @param tradeAi trade AI state or {@code null}
 * @param mining mining state or {@code null}
 * @param combat legacy combat state or {@code null}
 * @param asteroid asteroid state or {@code null}
 * @param archetype stable content archetype or {@code null}
 * @param engineering fitted Stage-17.5 engineering state or {@code null}
 * @param sensorKnowledge system-local Stage-17.5D/H information state or {@code null}
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
     * Compatibility constructor for Stage-17.5C-G values without sensor knowledge persistence.
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
     * Compatibility constructor for pre-Stage-17.5C values without engineering state.
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

    /**
     * Persistent identity fields.
     *
     * @param name display name
     * @param kindName identity-kind enum name
     */
    public record IdentityState(String name, String kindName) { }

    /**
     * Persistent transform fields.
     *
     * @param x x coordinate
     * @param y y coordinate
     * @param velocityX x velocity
     * @param velocityY y velocity
     */
    public record TransformState(float x, float y, float velocityX, float velocityY) { }

    /**
     * Persistent inventory fields.
     *
     * @param capacity inventory capacity
     * @param stock item-indexed stock
     */
    public record InventoryState(int capacity, List<Integer> stock) { }

    /** @param balanceMilliCredits authoritative balance in milli-credits */
    public record WalletState(long balanceMilliCredits) { }

    /**
     * Persistent market fields.
     *
     * @param targetStock effective target stock
     * @param configuredTargetStock authored baseline target stock
     * @param baseConsumption base consumption
     * @param sellPrices sell prices
     * @param buyPrices buy prices
     * @param consumptionRemainder fractional consumption remainder
     * @param tradableItems tradable mask
     * @param dirty recalculation flag
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
         * Compatibility constructor for schema v1/v2 values without configured target provenance.
         *
         * @param targetStock legacy effective target stock
         * @param baseConsumption base consumption
         * @param sellPrices sell prices
         * @param buyPrices buy prices
         * @param consumptionRemainder fractional consumption remainder
         * @param tradableItems tradable mask
         * @param dirty recalculation flag
         */
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

    /**
     * Persistent production fields.
     *
     * @param recipes recipes
     * @param activeRecipeIndex active recipe index
     * @param progressSeconds current recipe progress
     */
    public record ProductionState(List<RecipeState> recipes, int activeRecipeIndex, float progressSeconds) { }

    /**
     * Persistent recipe fields.
     *
     * @param name recipe name
     * @param durationSeconds recipe duration
     * @param inputs item-indexed inputs
     * @param outputs item-indexed outputs
     */
    public record RecipeState(String name, float durationSeconds, List<Integer> inputs, List<Integer> outputs) { }

    /**
     * Persistent price-history fields.
     *
     * @param maxPoints history limit per item
     * @param history item-indexed price series
     */
    public record PriceHistoryState(int maxPoints, List<List<Float>> history) { }

    /** @param factionId runtime faction ID */
    public record FactionState(int factionId) { }

    /** @param values item-indexed reputation values */
    public record ReputationState(List<Float> values) { }

    /** @param typeName legacy ship-type enum name or {@code null} */
    public record ShipState(String typeName) { }

    /**
     * Persistent trade-AI state.
     *
     * @param stateName FSM state name
     * @param buyStationId buy station
     * @param sellStationId sell station
     * @param targetStationId current target station
     * @param targetItem selected item
     * @param specializedItem specialized item
     * @param targetAmount target amount
     * @param cargoSpace cargo constraint
     * @param movementSpeed movement speed
     * @param expectedProfitMilliCredits expected profit
     * @param routeSearchCooldown route-search cooldown
     */
    public record TradeAiState(
            String stateName, EntityId buyStationId, EntityId sellStationId, EntityId targetStationId,
            int targetItem, int specializedItem, int targetAmount, int cargoSpace, float movementSpeed,
            long expectedProfitMilliCredits, float routeSearchCooldown) { }

    /**
     * Persistent mining state.
     *
     * @param resourceItem extracted item
     * @param extractionPerSecond extraction rate
     * @param movementSpeed movement speed
     * @param extractionRange extraction range
     * @param dockingRange docking range
     * @param extractionRemainder fractional extraction remainder
     * @param totalMined total mined
     * @param totalDelivered total delivered
     * @param active active flag
     * @param stateName FSM state name
     * @param targetAsteroidId target asteroid
     * @param homeBaseId home base
     */
    public record MiningState(
            int resourceItem, float extractionPerSecond, float movementSpeed, float extractionRange,
            float dockingRange, double extractionRemainder, long totalMined, long totalDelivered,
            boolean active, String stateName, EntityId targetAsteroidId, EntityId homeBaseId) { }

    /**
     * Legacy combat state retained for compatibility.
     *
     * @param hull current legacy hull value
     * @param maxHull maximum legacy hull value
     * @param shields current legacy shield value
     * @param maxShields maximum legacy shield value
     * @param damagePerSecond legacy damage value
     * @param weaponRange legacy range value
     */
    public record CombatState(
            float hull, float maxHull, float shields, float maxShields,
            float damagePerSecond, float weaponRange) { }

    /**
     * Persistent asteroid state.
     *
     * @param spawnPointId stable spawn-point ID
     * @param resourceItem resource item
     * @param initialResource initial amount
     * @param remainingResource remaining amount
     */
    public record AsteroidState(String spawnPointId, int resourceItem, long initialResource, long remainingResource) { }

    /** @param contentId stable archetype content ID */
    public record ArchetypeState(String contentId) { }

    /**
     * Persistent Stage-17.5 fitted engineering state; derived capabilities are intentionally absent.
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
        /**
         * Compatibility constructor for core GameState v4 payloads without Stage-17.5H extension.
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
         */
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

    /**
     * Persistent installed module assignment.
     *
     * @param mountId hull-local mount ID
     * @param moduleId installed module content ID
     */
    public record InstalledModuleState(String mountId, String moduleId) { }

    /**
     * Persistent physical carried/interface loads.
     *
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
     * Persistent one-interface physical load.
     *
     * @param mountId installed module mount ID
     * @param interfaceId module-local interface ID
     * @param kindName physical interface-kind enum name
     * @param amount authored physical amount
     * @param massKg physical mass
     * @param itemCount physical item count
     */
    public record EngineeringConsumableLoadState(
            String mountId, String interfaceId, String kindName,
            double amount, double massKg, long itemCount) { }

    /**
     * Deterministically keyed double value.
     *
     * @param mountId stable local key
     * @param value finite physical value
     */
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
     * Persistent shield runtime state.
     *
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

    /**
     * Persistent weapon-feed identity binding.
     *
     * @param mountId weapon mount
     * @param interfaceId feed ID
     * @param ammunitionContentId ammunition content ID
     */
    public record WeaponFeedState(String mountId, String interfaceId, String ammunitionContentId) { }

    /**
     * System-local sensor knowledge persisted while entity identity remains valid.
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
     * Persistent fused sensor track.
     *
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
     * Persistent sensor measurement.
     *
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

    /**
     * Persistent delayed datalink delivery.
     *
     * @param measurement transmitted measurement
     * @param deliverAtSeconds delivery time
     */
    public record PendingSensorMeasurementState(SensorMeasurementState measurement, double deliverAtSeconds) { }
}
