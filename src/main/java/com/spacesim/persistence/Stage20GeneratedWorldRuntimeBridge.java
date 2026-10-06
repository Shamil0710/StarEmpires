package com.spacesim.persistence;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage18StationInfrastructureCatalog;
import com.spacesim.content.Stage18StationInfrastructureCatalog.StationArchetypeDefinition;
import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.economy.Stage18ExtractionRuntime.ExtractionResult;
import com.spacesim.economy.Stage18LogisticsRuntime;
import com.spacesim.economy.Stage18LogisticsRuntime.HandlingCapability;
import com.spacesim.economy.Stage18LogisticsRuntime.TransferResult;
import com.spacesim.economy.Stage18StationIndustrialNode;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.economy.Stage18StationStorage.StationStorageSnapshot;
import com.spacesim.model.ShipType;
import com.spacesim.persistence.Stage18IndustrialState.FacilityInstallationSnapshot;
import com.spacesim.persistence.Stage18IndustrialState.YardInstallationSnapshot;
import com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase;
import com.spacesim.persistence.Stage20FreightPersistentState.FreighterState;
import com.spacesim.persistence.Stage20FreightPersistentState.TransportOrderState;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.CanonicalRow;
import com.spacesim.persistence.Stage20GeneratedIndustrialRuntimeBridge.MaterializedGeneratedIndustrialRuntime;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistentState.LocalFleetPhysicalState;
import com.spacesim.persistence.Stage20IndustrialEntityMaterializer.MaterializedIndustrialStation;
import com.spacesim.persistence.Stage20SourceOutpostMaterializer.MaterializedExtractionOutpost;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.ResolvedSprite;
import com.spacesim.simulation.Stage20MaterializationService;
import com.spacesim.world.DestructionPolicy;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetJumpState;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.Stage20LocalInfrastructureLayout.PlacementKind;
import com.spacesim.world.Stage20OperationalIndustrialSpecializationPlan.OperationalSpecializationReport;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.WorldSimulation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Final Stage-20.5 composition boundary for accepted generated authority and the existing ordinary
 * multi-system runtime.
 *
 * <p>The bridge materializes the finite source/industrial registries, every accepted freight slot
 * and each canonical infrastructure endpoint exactly once. Freight entities receive consecutive
 * IDs from the existing {@link WorldSimulation} allocator, execute only the existing neighbor jump
 * FSM and synchronize their route state exclusively from the exact Stage-20 arrival sidecar. Cargo
 * still moves through {@link Stage18LogisticsRuntime}; sprites remain a read-only projection.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20GeneratedWorldRuntimeBridge {
    /** Stable final Stage-20.5 runtime composition contract. */
    public static final String CURRENT_VERSION = "stage20_5.generated-world-runtime-bridge.v1";
    private static final String INFRASTRUCTURE_DOMAIN = "INFRASTRUCTURE_PLACEMENT";
    private static final String ORBITAL_LOCATION_TAG = "location.orbital_station";
    private static final String PROPELLANT_COMMODITY_ID = "commodity.material.purified_water";
    private static final String LIQUID_STORAGE_CLASS_ID = "storage.liquid_tank";
    private static final double MAX_INITIAL_PROPELLANT_RESERVE_KG = 24_000_000d;

    private Stage20GeneratedWorldRuntimeBridge() {
        throw new AssertionError("No instances");
    }

    /**
     * Performs the one-time bootstrap materialization into an existing ordinary generated topology
     * using the baseline Stage-18 manufactured-product vocabulary.
     *
     * @param campaign exact accepted Stage-20K campaign
     * @param specialization exact accepted Stage-20F operating authority
     * @param world ordinary live world with the exact generated topology
     * @return composed live generated-world runtime
     */
    public static LiveRuntime materializeBootstrap(
            Stage20GeneratedCampaignPersistentState campaign,
            OperationalSpecializationReport specialization,
            WorldSimulation world) {
        return materializeBootstrap(
                campaign,
                specialization,
                world,
                Stage18ManufacturingProductRegistry.loadDefault());
    }

    /**
     * Performs one-time bootstrap materialization with an explicitly composed ordinary Stage-18
     * manufactured-product registry.
     *
     * <p>This overload is a content-composition seam only. It does not add faction-specific behavior
     * to Stage 20; later content layers may extend the ordinary Stage-18 product vocabulary and pass
     * the resulting registry down to the existing industrial and logistics authorities.</p>
     *
     * @param campaign exact accepted Stage-20K campaign
     * @param specialization exact accepted Stage-20F operating authority
     * @param world ordinary live world with the exact generated topology
     * @param products explicit ordinary Stage-18 manufactured-product vocabulary
     * @return composed live generated-world runtime
     */
    public static LiveRuntime materializeBootstrap(
            Stage20GeneratedCampaignPersistentState campaign,
            OperationalSpecializationReport specialization,
            WorldSimulation world,
            Stage18ManufacturingProductRegistry products) {
        return materializeBootstrap(campaign, specialization, world, products,
                Stage20FreightRuntimeMaterializer.ReserveLoadoutPolicy.BASELINE);
    }

    /** Materializes explicit initial NPC loadouts; restoration never invokes this policy. */
    public static LiveRuntime materializeBootstrap(
            Stage20GeneratedCampaignPersistentState campaign, OperationalSpecializationReport specialization,
            WorldSimulation world, Stage18ManufacturingProductRegistry products,
            Stage20FreightRuntimeMaterializer.ReserveLoadoutPolicy reservePolicy) {
        Stage20GeneratedCampaignPersistentState saved = Objects.requireNonNull(campaign, "campaign");
        WorldSimulation runtime = Objects.requireNonNull(world, "world");
        Stage18ManufacturingProductRegistry productRegistry = Objects.requireNonNull(products, "products");
        MaterializedGeneratedIndustrialRuntime industry =
                Stage20GeneratedIndustrialRuntimeBridge.materializeBootstrap(
                        saved,
                        Objects.requireNonNull(specialization, "specialization"),
                        productRegistry);
        InfrastructureRegistry infrastructure = InfrastructureRegistry.materialize(
                saved, industry, productRegistry, true);
        Stage20FreightPersistentState freightState = Stage20FreightRuntimeMaterializer.materializeBootstrap(
                saved,
                specialization,
                runtime.snapshot().nextFleetIdValue(),
                Stage20FreightRuntimeMaterializer.FreighterCompatibilityAuthority.currentProvisional(),
                com.spacesim.content.ship.Stage175ICombatTestContentPack.load(), reservePolicy);
        validateOrderEndpoints(freightState, infrastructure, industry);

        Stage20LiveArrivalAuthorityIntegration arrival =
                Stage20LiveArrivalAuthorityIntegration.restoreAndBind(saved, runtime);
        materializeFreightEntities(runtime, freightState, arrival);
        Stage20FreightRuntime freight = Stage20FreightRuntime.restore(freightState);
        return new LiveRuntime(
                saved, runtime, industry, infrastructure, freight, arrival, productRegistry);
    }

    /**
     * Restores a composed runtime without invoking any Stage-20 generator or planner using the
     * baseline Stage-18 manufactured-product vocabulary.
     *
     * @param checkpoint exact decoded atomic runtime checkpoint
     * @return independent restored live runtime
     */
    public static LiveRuntime restore(Stage20GeneratedWorldRuntimePersistentState checkpoint) {
        return restore(checkpoint, Stage18ManufacturingProductRegistry.loadDefault());
    }

    /**
     * Restores a composed runtime with an explicitly composed ordinary Stage-18 product registry.
     *
     * @param checkpoint exact decoded atomic runtime checkpoint
     * @param products explicit ordinary Stage-18 manufactured-product vocabulary
     * @return independent restored live runtime
     */
    public static LiveRuntime restore(
            Stage20GeneratedWorldRuntimePersistentState checkpoint,
            Stage18ManufacturingProductRegistry products) {
        Stage20GeneratedWorldRuntimePersistentState saved = Objects.requireNonNull(
                checkpoint, "checkpoint");
        Stage18ManufacturingProductRegistry productRegistry = Objects.requireNonNull(products, "products");
        WorldSimulation world = WorldSimulation.restore(
                saved.worldState(),
                ContentCatalogLoader.loadDefault(),
                saved.activeSystemId(),
                saved.strategicStepTicks(),
                saved.remoteUpdateBudgetPerFrame());
        MaterializedGeneratedIndustrialRuntime industry =
                Stage20GeneratedIndustrialRuntimeBridge.restore(saved.campaign(), productRegistry);
        InfrastructureRegistry infrastructure = InfrastructureRegistry.materialize(
                saved.campaign(), industry, productRegistry, false);
        Stage20FreightRuntime freight = Stage20FreightRuntime.restore(
                saved.campaign(),
                saved.freight(),
                Stage20FreightRuntimeMaterializer.FreighterCompatibilityAuthority.currentProvisional(),
                Stage175ICombatTestContentPack.load());
        validateOrderEndpoints(saved.freight(), infrastructure, industry);
        Stage20LiveArrivalAuthorityIntegration arrival =
                Stage20LiveArrivalAuthorityIntegration.restoreAndBind(saved.campaign(), world);
        registerRestoredLocalFleetPhysicalStates(
                world, saved.localFleetPhysicalStates(), arrival);
        validateAndRegisterRestoredFreight(world, saved.freight(), arrival);
        return new LiveRuntime(
                saved.campaign(), world, industry, infrastructure, freight, arrival, productRegistry);
    }

    private static void materializeFreightEntities(
            WorldSimulation world,
            Stage20FreightPersistentState freight,
            Stage20LiveArrivalAuthorityIntegration arrival) {
        long expected = world.snapshot().nextFleetIdValue();
        ArrayList<CreatedFleet> created = new ArrayList<>();
        try {
            for (FreighterState fleet : freight.freighters()) {
                if (fleet.fleetId().value() != expected++) {
                    throw new IllegalArgumentException(
                            "freight FleetIds must consume the ordinary world allocator consecutively");
                }
                int factionId = world.findFactionRuntimeId(fleet.stableFactionId()).orElseThrow(
                        () -> new IllegalArgumentException(
                                "freight owner is absent from ordinary world faction directory: "
                                        + fleet.stableFactionId()));
                Entity entity = freightEntity(fleet, factionId);
                EntityId localId = world.createEntity(fleet.currentSystemId(), entity);
                FleetId assigned = world.findFleetByLocal(fleet.currentSystemId(), localId).orElseThrow();
                if (!assigned.equals(fleet.fleetId())) {
                    throw new IllegalStateException("ordinary world allocated a different freight FleetId");
                }
                arrival.materialization(fleet.currentSystemId())
                        .registerPhysicalState(localId, fleet.physicalState());
                created.add(new CreatedFleet(fleet.currentSystemId(), localId));
            }
            if (world.snapshot().nextFleetIdValue() != freight.nextFleetIdValue()) {
                throw new IllegalStateException("ordinary world and freight allocator watermarks diverged");
            }
        } catch (RuntimeException | Error exception) {
            rollbackCreatedFreight(world, arrival, created, exception);
            throw exception;
        }
    }

    private static Entity freightEntity(FreighterState fleet, int runtimeFactionId) {
        TransformComponent transform = new TransformComponent();
        transform.position.set(
                exactFloat(fleet.physicalState().position().offsetXM(), "freight position X"),
                exactFloat(fleet.physicalState().position().offsetYM(), "freight position Y"));
        transform.velocity.set(
                exactFloat(fleet.physicalState().velocityXMps(), "freight velocity X"),
                exactFloat(fleet.physicalState().velocityYMps(), "freight velocity Y"));
        return new Entity()
                .add(new IdentityComponent(
                        "Freight " + fleet.stableFactionId() + " #" + fleet.ownershipOrdinal(),
                        IdentityComponent.Kind.FLEET))
                .add(new ArchetypeComponent(fleet.hullId()))
                .add(transform)
                .add(new ShipComponent(ShipType.MATERIAL_CARRIER))
                .add(new FactionComponent(runtimeFactionId))
                .add(freightEngineering(fleet));
    }

    /**
     * Projects the legacy Stage-20 freight ownership row onto its reviewed Stage-22 physical asset.
     *
     * <p>The freight sidecar keeps its historical compatibility hull/fit IDs for save compatibility,
     * while the live ECS engineering authority uses the licensed core freight fit selected by the
     * stable generated-faction identity. Initial reaction mass is explicit finite starting stock,
     * not an infinite-flight fallback.</p>
     */
    private static com.spacesim.components.EngineeringComponent freightEngineering(FreighterState fleet) {
        String fitId = fleet.fitId().equals(com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader.UNION_MINING_FREIGHT_STRATEGIC_FIT)
                && fleet.stableFactionId().equals("faction.beta") ? fleet.fitId() : switch (fleet.stableFactionId()) {
            case "faction.alpha" ->
                    com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader
                            .EMPIRE_FREIGHT_STRATEGIC_FIT;
            case "faction.beta" ->
                    com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader
                            .UNION_FREIGHT_STRATEGIC_FIT;
            default -> throw new IllegalStateException(
                    "generated freight has no reviewed physical engineering asset: "
                            + fleet.stableFactionId());
        };
        var catalog = com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader.loadDefault();
        var definition = catalog.findDemonstratorFit(fitId);
        if (definition == null) {
            throw new IllegalStateException("missing reviewed freight fit: " + fitId);
        }
        var fit = com.spacesim.ship.ShipEngineeringState.InstalledFit.fromDemonstrator(definition);
        java.util.ArrayList<com.spacesim.ship.ShipEngineeringState.ConsumableLoad> loads =
                new java.util.ArrayList<>();
        for (var installed : fit.installedModules()) {
            var module = catalog.findModule(installed.moduleId());
            if (module == null) {
                throw new IllegalStateException("reviewed freight fit references missing module");
            }
            for (var iface : module.interfaces()) {
                if (iface.kind() == com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind.REACTION_MASS) {
                    loads.add(new com.spacesim.ship.ShipEngineeringState.ConsumableLoad(
                            installed.mountId(),
                            iface.id(),
                            iface.kind(),
                            iface.capacity(),
                            iface.capacity(),
                            0L));
                }
            }
        }
        if (loads.isEmpty()) {
            throw new IllegalStateException("reviewed freight fit has no reaction-mass interface: " + fitId);
        }
        var consumables = new com.spacesim.ship.ShipEngineeringState.ConsumableState(
                fleet.cargoMassKg(), 0d, 0d, 0d, loads);
        var operating = new com.spacesim.ship.ShipEngineeringRuntime(catalog).initialize(
                fit,
                consumables,
                com.spacesim.ship.ShipEngineeringState.DamageState.pristine());
        return new com.spacesim.components.EngineeringComponent(
                fit,
                operating,
                com.spacesim.ship.ShipInstanceRuntimeState.legacyNeutral());
    }

    private static void rollbackCreatedFreight(
            WorldSimulation world,
            Stage20LiveArrivalAuthorityIntegration arrival,
            List<CreatedFleet> created,
            Throwable failure) {
        for (int index = created.size() - 1; index >= 0; index--) {
            CreatedFleet fleet = created.get(index);
            try {
                if (world.removeEntity(fleet.systemId(), fleet.entityId())) {
                    arrival.materialization(fleet.systemId())
                            .releasePhysicalStateForWorldTransfer(fleet.entityId());
                }
            } catch (RuntimeException | Error rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    private static void validateFreightLegalFaction(WorldSimulation world, FreighterState fleet) {
        var placement = world.findFleet(fleet.fleetId()).orElse(null);
        if (fleet.phase() == FreightPhase.DESTROYED) {
            if (placement != null) throw new IllegalArgumentException("Destroyed freight still has world placement");
            return;
        }
        if (placement == null) throw new IllegalArgumentException("Freight legal mirror has no world fleet");
        int expected = world.findFactionRuntimeId(fleet.legalFactionId()).orElseThrow(
                () -> new IllegalArgumentException("Unknown freight legal faction"));
        int actual;
        if (placement.locationKind() == FleetLocationKind.IN_TRANSIT) {
            var faction = placement.transitState().entityState().faction();
            if (faction == null) throw new IllegalArgumentException("Transit freight lacks legal faction");
            actual = faction.factionId();
        } else {
            var faction = world.findSession(placement.systemId()).orElseThrow().getEntityRegistry()
                    .require(placement.localEntityId()).getComponent(FactionComponent.class);
            if (faction == null) throw new IllegalArgumentException("Local freight lacks legal faction");
            actual = faction.factionId;
        }
        if (actual != expected) throw new IllegalArgumentException("Freight legal mirror differs from canonical world affiliation");
    }

    private static void validateAndRegisterRestoredFreight(
            WorldSimulation world,
            Stage20FreightPersistentState freight,
            Stage20LiveArrivalAuthorityIntegration arrival) {
        for (FreighterState fleet : freight.freighters()) {
            FleetPlacementState placement = world.findFleet(fleet.fleetId()).orElse(null);
            if (fleet.phase() == FreightPhase.DESTROYED) {
                if (placement != null) {
                    throw new IllegalArgumentException("destroyed freighter restored into ordinary world");
                }
                continue;
            }
            if (placement == null) {
                throw new IllegalArgumentException("operational freighter is absent from restored world");
            }
            validateFreightLegalFaction(world, fleet);
            if (placement.locationKind() == FleetLocationKind.IN_TRANSIT) {
                continue;
            }
            Entity entity = world.findSession(placement.systemId()).orElseThrow()
                    .getEntityRegistry().require(placement.localEntityId());
            ArchetypeComponent archetype = entity.getComponent(ArchetypeComponent.class);
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            if (entity.getComponent(com.spacesim.components.EngineeringComponent.class) == null) {
                // Explicit migration for historical generated-freight saves that predate finite propulsion.
                entity.add(freightEngineering(fleet));
            }
            Integer expectedFaction = world.findFactionRuntimeId(fleet.legalFactionId()).orElseThrow();
            if (archetype == null || !archetype.contentId.equals(fleet.hullId())
                    || faction == null || faction.factionId != expectedFaction) {
                throw new IllegalArgumentException(
                        "restored ordinary freight entity differs from persisted hull/owner identity");
            }
            LocalPhysicalKinematics restored = arrival.materialization(placement.systemId())
                    .physicalState(placement.localEntityId()).orElseThrow();
            if (!restored.equals(fleet.physicalState())) {
                throw new IllegalArgumentException(
                        "restored ordinary freight physical state differs from freight sidecar");
            }
        }
    }

    private static void registerRestoredLocalFleetPhysicalStates(
            WorldSimulation world,
            List<LocalFleetPhysicalState> states,
            Stage20LiveArrivalAuthorityIntegration arrival) {
        for (LocalFleetPhysicalState state : states) {
            FleetPlacementState placement = world.findFleet(state.fleetId()).orElseThrow();
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !placement.systemId().equals(state.systemId())) {
                throw new IllegalArgumentException(
                        "restored fleet physical sidecar differs from ordinary placement");
            }
            arrival.materialization(state.systemId()).registerPhysicalState(
                    placement.localEntityId(), state.physicalState());
        }
    }

    private static void validateOrderEndpoints(
            Stage20FreightPersistentState freight,
            InfrastructureRegistry infrastructure, MaterializedGeneratedIndustrialRuntime industry) {
        var extractedBySource = new java.util.TreeMap<String, Double>();
        var extractionCatalog = com.spacesim.content.Stage18ExtractionCatalogLoader.loadDefault();
        for (var lot : freight.productLots()) infrastructure.endpoint(lot.sourceEndpointId());
        for (var order : freight.personalMiningOrders()) {
            var source = industry.sourceOutposts().sources().source(order.sourceId()).sourceState();
            var method = extractionCatalog.findMethod(order.methodId());
            if (source.sourceKind() != com.spacesim.content.Stage18ExtractionCatalog.SourceKind.NATURAL_OCCURRENCE
                    || method == null || method.sourceKind() != source.sourceKind())
                throw new IllegalArgumentException("Mining references an unknown or incompatible physical source/method");
        }
        for (var lot : freight.cargoLots()) {
            if (lot.orderId().equals(Stage20FreightPersistentState.manualCargoOrderId(lot.fleetId())))
                infrastructure.endpoint(lot.sourceEndpointId());
            if (lot.orderId().equals(Stage20FreightPersistentState.personalExtractionOrderId(lot.fleetId()))) {
                var source = industry.sourceOutposts().sources().source(lot.sourceEndpointId()).sourceState();
                if (!source.outputCommodityId().equals(lot.commodityId()))
                    throw new IllegalArgumentException("Extracted cargo commodity differs from physical source");
                double aboard = extractedBySource.merge(source.sourceId(), lot.massKg(), Double::sum);
                double maximumRecovered = (source.initialAccessibleMassKg() - source.remainingAccessibleMassKg())
                        * source.gradeFraction() * source.sourceRecoveryFraction();
                if (aboard > maximumRecovered + Math.max(1e-9, maximumRecovered * 1e-12))
                    throw new IllegalArgumentException("Extracted cargo exceeds depleted physical source mass");
            }
        }
        for (TransportOrderState order : freight.orders()) {
            RuntimeEndpoint source = infrastructure.endpoint(order.sourceEndpointId());
            RuntimeEndpoint destination = infrastructure.endpoint(order.destinationEndpointId());
            if (!source.systemId().equals(order.orderedSystems().get(0))
                    || !destination.systemId().equals(
                            order.orderedSystems().get(order.orderedSystems().size() - 1))) {
                throw new IllegalArgumentException(
                        "freight order endpoint system differs from its exact neighbor route");
            }
        }
    }

    private static float exactFloat(double value, String label) {
        float result = (float) value;
        if (!Float.isFinite(result)) {
            throw new IllegalArgumentException(label + " is outside the legacy ECS projection range");
        }
        return result;
    }

    private record CreatedFleet(StarSystemId systemId, EntityId entityId) { }

    /**
     * One live, fully composed Stage-20.5 generated-world session.
     *
     * <p>Callers may inspect the lower-level registries, but route progress can advance only through
     * {@link #requestNextRouteHop(FleetId)} plus {@link #advanceFrame(float)}; caller-supplied
     * arrival coordinates are intentionally absent from this boundary.</p>
     */
    public static final class LiveRuntime {
        private final Stage20GeneratedCampaignPersistentState campaignAuthority;
        private Stage20DiscoveryPersistentState discovery;
        private final WorldSimulation world;
        private MaterializedGeneratedIndustrialRuntime industry;
        private final InfrastructureRegistry infrastructure;
        private final Stage20FreightRuntime freight;
        private final Stage20LiveArrivalAuthorityIntegration arrival;
        private final Stage18LogisticsRuntime logistics;
        private final com.spacesim.economy.Stage18ManufacturingWorkQueue manufacturingQueue;
        private final com.spacesim.economy.Stage18FacilityConstructionWorkQueue constructionQueue;

        private LiveRuntime(
                Stage20GeneratedCampaignPersistentState campaignAuthority,
                WorldSimulation world,
                MaterializedGeneratedIndustrialRuntime industry,
                InfrastructureRegistry infrastructure,
                Stage20FreightRuntime freight,
                Stage20LiveArrivalAuthorityIntegration arrival,
                Stage18ManufacturingProductRegistry products) {
            this.campaignAuthority = Objects.requireNonNull(campaignAuthority, "campaignAuthority");
            this.discovery = campaignAuthority.discoveryState();
            this.world = Objects.requireNonNull(world, "world");
            this.industry = Objects.requireNonNull(industry, "industry");
            this.infrastructure = Objects.requireNonNull(infrastructure, "infrastructure");
            this.freight = Objects.requireNonNull(freight, "freight");
            this.arrival = Objects.requireNonNull(arrival, "arrival");
            this.logistics = new Stage18LogisticsRuntime(
                    Stage18ResourceOntologyLoader.loadDefault(),
                    Objects.requireNonNull(products, "products"));
            this.manufacturingQueue = new com.spacesim.economy.Stage18ManufacturingWorkQueue(
                    Stage18ResourceOntologyLoader.loadDefault(),
                    com.spacesim.content.Stage22CivilianMiningProductionPath.loadManufacturing(), products,
                    campaignAuthority.industrialState().processOrders(), campaignAuthority.industrialState().simulationTick());
            manufacturingQueue.restoreReservations(id -> infrastructure.endpoint(id).storage());
            this.constructionQueue = new com.spacesim.economy.Stage18FacilityConstructionWorkQueue(
                    new com.spacesim.economy.Stage18FacilityConstructionRuntime(
                            com.spacesim.content.Stage18FacilityConstructionCatalogLoader.loadDefault(),
                            com.spacesim.content.Stage18FacilityCatalogLoader.loadDefault(),
                            Stage18ResourceOntologyLoader.loadDefault()), Stage18ResourceOntologyLoader.loadDefault(),
                    campaignAuthority.industrialState().constructionOrders(), campaignAuthority.industrialState().simulationTick());
            constructionQueue.bindReservations(id -> infrastructure.endpoint(id).storage());
            if (constructionQueue.capture().stream().anyMatch(com.spacesim.economy.Stage18FacilityConstructionWorkQueue::managed)
                    && constructionQueue.lastProcessedTick() > world.getAuthoritativeWorldTick())
                throw new IllegalArgumentException("Construction watermark exceeds world time");
            if (manufacturingQueue.capture().stream().anyMatch(com.spacesim.economy.Stage18ManufacturingWorkQueue::managed)
                    && manufacturingQueue.lastProcessedTick() > world.getAuthoritativeWorldTick())
                throw new IllegalArgumentException("Manufacturing watermark exceeds world time");
            this.world.bindFleetPropellantLogisticsAuthority(
                    new Stage22GeneratedWorldPropellantLogisticsAuthority(
                            this.world, this.infrastructure, this.industry));
        }

        /** @return current immutable observer-local discovery registry */
        public Stage20DiscoveryPersistentState discoveryState() { return discovery; }

        /** @return native reserved-material production queue; commands require campaign ownership validation */
        public com.spacesim.economy.Stage18ManufacturingWorkQueue manufacturingQueue() { return manufacturingQueue; }
        /** @return existing physical facility-construction queue with shared industrial persistence */
        public com.spacesim.economy.Stage18FacilityConstructionWorkQueue constructionQueue() { return constructionQueue; }

        /**
         * Installs only completed orders retained by the queue at an already completed world tick.
         * This supplies no electrical power, workforce, maintenance capacity or stock.
         * @param completed newly completed physical construction orders
         */
        public void adoptCompletedFacilityConstruction(
                List<com.spacesim.economy.Stage18FacilityConstructionRuntime.ConstructionOrderSnapshot> completed) {
            completed = List.copyOf(completed);
            if (completed.isEmpty()) return;
            if (constructionQueue.lastProcessedTick() > world.getAuthoritativeWorldTick())
                throw new IllegalStateException("Construction cannot finish ahead of the world clock");
            var retained = constructionQueue.capture();
            if (!retained.containsAll(completed))
                throw new IllegalArgumentException("Installation must retain its paid construction evidence");
            var construction = new com.spacesim.economy.Stage18FacilityConstructionRuntime(
                    com.spacesim.content.Stage18FacilityConstructionCatalogLoader.loadDefault(),
                    com.spacesim.content.Stage18FacilityCatalogLoader.loadDefault(), Stage18ResourceOntologyLoader.loadDefault());
            var updated = industry.industrial().adoptCompletedConstruction(completed, construction);
            industry = new MaterializedGeneratedIndustrialRuntime(updated, industry.sourceOutposts());
        }

        /**
         * Installs only exact retained yard completions at an already completed world tick.
         * @param completed newly completed physical structures
         * @param queue actual construction authority retaining material/work evidence
         */
        public void adoptCompletedYardConstruction(
                List<com.spacesim.economy.Stage23YardConstructionWorkQueue.Order> completed,
                com.spacesim.economy.Stage23YardConstructionWorkQueue queue) {
            if (completed.isEmpty()) return;
            if (queue.capture().lastProcessedTick() > world.getAuthoritativeWorldTick())
                throw new IllegalStateException("Yard construction cannot finish ahead of world time");
            industry = new MaterializedGeneratedIndustrialRuntime(
                    industry.industrial().adoptCompletedYardConstruction(completed, queue), industry.sourceOutposts());
        }

        /**
         * Redistributes already existing station resources without any bootstrap or inventory grant.
         * @param stationId actual station; player command checks ownership and physical berth
         * @param targetId actual installed facility
         */
        public void allocateFacilityResources(String stationId, String targetId) {
            industry = new MaterializedGeneratedIndustrialRuntime(
                    industry.industrial().allocateFacilityResources(stationId, targetId), industry.sourceOutposts());
        }

        /**
         * Transfers existing yard resources within a station without issuing operating capacity.
         * @param stationId actual station; player command checks ownership and berth
         * @param targetId actual installed yard
         */
        public void allocateYardResources(String stationId, String targetId) {
            industry = new MaterializedGeneratedIndustrialRuntime(
                    industry.industrial().allocateYardResources(stationId, targetId), industry.sourceOutposts());
        }

        /**
         * Shares an existing personal observation with an existing faction knowledge owner.
         * The campaign validates the recipient and contract; no generated truth is consulted.
         * @param recipient faction receiving the volunteered observation
         * @param object exact personally observed static object
         * @param reportId durable provenance identity
         * @return recipient knowledge after the ordinary merge
         */
        public com.spacesim.world.Stage20DiscoveryKnowledgeState sharePersonalDiscovery(
                String recipient, com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectRef object, String reportId) {
            if (world.findFactionEconomicState(recipient).isEmpty())
                throw new IllegalArgumentException("Unknown discovery recipient");
            var personal = discovery.knowledgeFor(com.spacesim.world.Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID)
                    .knowledge(object).orElseThrow(() -> new IllegalStateException("No personal observation"));
            var prior = discovery.knowledgeFor(recipient);
            if (prior.knowledge(object).stream().flatMap(k -> k.evidence().stream())
                    .anyMatch(e -> e.provenanceId().equals(reportId)))
                throw new IllegalStateException("Discovery report already delivered");
            var next = new com.spacesim.world.Stage20DiscoveryKnowledgeRuntime().observe(prior,
                    new com.spacesim.world.Stage20DiscoveryKnowledgeRuntime.StaticObservation(object, personal.state(),
                            personal.classificationId(), personal.knownLocation(), personal.resourceKnowledge(),
                            new com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryEvidence(
                                    com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoverySource.PURCHASED_OR_SHARED_MAP_DATA,
                                    reportId, personal.lastUpdatedSeconds(), java.util.OptionalDouble.empty())));
            var states = new ArrayList<>(discovery.knowledgeStates());
            states.removeIf(state -> state.ownerId().equals(recipient)); states.add(next);
            discovery = new Stage20DiscoveryPersistentState(discovery.envelopeVersion(), discovery.rootSeed(),
                    discovery.worldGenerationVersion(), discovery.worldFingerprint(), states);
            return next;
        }

        /**
         * Records the human actor's actual physical visit to an existing local station.
         * The caller has completed ordinary docking; this seam revalidates ownership and geometry.
         * Previously known permanent station locations are not rewritten on repeated visits.
         * @param player current durable docked player
         * @param stationId physically visited generated endpoint
         */
        public void recordPersonalStationVisit(com.spacesim.player.PlayerState player, String stationId) {
            Objects.requireNonNull(player, "player");
            if (!player.docked() || player.activeFleetId() == null
                    || !player.ownedFleetIds().contains(player.activeFleetId()))
                throw new IllegalStateException("Personal station discovery requires owned docked fleet");
            var fleet = world.findFleet(player.activeFleetId()).orElseThrow();
            var endpoint = infrastructure.endpoint(stationId);
            if (fleet.locationKind() != FleetLocationKind.IN_SYSTEM || world.findFleetJump(fleet.fleetId()).isPresent()
                    || !fleet.systemId().equals(endpoint.systemId()) || !player.dockedAt().systemId().equals(fleet.systemId()))
                throw new IllegalStateException("Personal station discovery requires local physical docking");
            var physical = arrival.materialization(fleet.systemId()).physicalState(fleet.localEntityId()).orElseThrow();
            if (physical.position().distanceTo(endpoint.position()) > 1000d
                    || Math.hypot(physical.velocityXMps(), physical.velocityYMps()) > 1d)
                throw new IllegalStateException("Station visit is outside ordinary berth geometry");
            String owner = com.spacesim.world.Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID;
            var prior = discovery.knowledgeFor(owner);
            var object = new com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectRef(endpoint.systemId(),
                    com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectKind.INFRASTRUCTURE, stationId);
            if (prior.entries().stream().filter(entry -> entry.object().equals(object))
                    .flatMap(entry -> entry.evidence().stream()).anyMatch(evidence ->
                        evidence.source() == com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoverySource.PHYSICAL_VISIT_OR_SURVEY
                        && evidence.provenanceId().equals("personal-station-visit:" + fleet.fleetId().value() + ":" + stationId))) return;
            double seconds = world.getAuthoritativeWorldTick() * (double) world.findSession(fleet.systemId()).orElseThrow().getClock().getFixedStepSeconds();
            var observed = new com.spacesim.world.Stage20DiscoveryKnowledgeRuntime().observe(prior,
                    new com.spacesim.world.Stage20DiscoveryKnowledgeRuntime.StaticObservation(object,
                            com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryState.KNOWN_STATIC_LOCATION,
                            Optional.of(endpoint.stationArchetypeId()), Optional.of(endpoint.position()),
                            com.spacesim.world.Stage20DiscoveryKnowledgeState.ResourceKnowledge.none(),
                            new com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryEvidence(
                                    com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoverySource.PHYSICAL_VISIT_OR_SURVEY,
                                    "personal-station-visit:" + fleet.fleetId().value() + ":" + stationId, seconds, java.util.OptionalDouble.empty())));
            var states = new ArrayList<>(discovery.knowledgeStates());
            states.removeIf(state -> state.ownerId().equals(owner)); states.add(observed);
            discovery = new Stage20DiscoveryPersistentState(discovery.envelopeVersion(), discovery.rootSeed(),
                    discovery.worldGenerationVersion(), discovery.worldFingerprint(), states);
        }

        /**
         * Retains the physical location and resource indication from an actual recovered cargo
         * sample. It provides no deposit grade, reserve estimate or foreign observer knowledge.
         * @param player current personal actor
         * @param sourceId contacted finite occurrence
         */
        public void recordPersonalExtractionSample(com.spacesim.player.PlayerState player, String sourceId) {
            if (player == null || player.activeFleetId() == null || !player.ownedFleetIds().contains(player.activeFleetId()))
                throw new IllegalStateException("Extraction observation requires its personal owner");
            var fleet = world.findFleet(player.activeFleetId()).orElseThrow();
            var source = industry.sourceOutposts().sources().source(sourceId);
            if (fleet.locationKind() != FleetLocationKind.IN_SYSTEM || !fleet.systemId().equals(source.systemId()))
                throw new IllegalStateException("Extraction sample requires the actual local occurrence");
            var sample = freight.capture().cargoLots().stream()
                    .filter(l -> l.fleetId().equals(player.activeFleetId()) && l.sourceEndpointId().equals(sourceId)
                            && l.orderId().equals(Stage20FreightPersistentState.personalExtractionOrderId(player.activeFleetId())))
                    .min(java.util.Comparator.comparingDouble(Stage20FreightPersistentState.CargoLotState::loadedAtSimulationSeconds))
                    .orElseThrow(() -> new IllegalStateException("No recovered physical sample"));
            String owner = com.spacesim.world.Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID;
            var prior = discovery.knowledgeFor(owner);
            var object = new com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectRef(source.systemId(),
                    com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectKind.RESOURCE_OCCURRENCE, sourceId);
            if (prior.knowledge(object).stream().anyMatch(k -> k.resourceKnowledge().level().ordinal()
                    >= com.spacesim.world.Stage20DiscoveryKnowledgeState.ResourceKnowledgeLevel.RESOURCE_INDICATION.ordinal())) return;
            String classification = prior.knowledge(object).flatMap(k -> k.classificationId()).orElse(sample.commodityId());
            var observed = new com.spacesim.world.Stage20DiscoveryKnowledgeRuntime().observe(prior,
                    new com.spacesim.world.Stage20DiscoveryKnowledgeRuntime.StaticObservation(object,
                            com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryState.KNOWN_STATIC_LOCATION,
                            Optional.of(classification), Optional.of(source.position()),
                            new com.spacesim.world.Stage20DiscoveryKnowledgeState.ResourceKnowledge(
                                    com.spacesim.world.Stage20DiscoveryKnowledgeState.ResourceKnowledgeLevel.RESOURCE_INDICATION,
                                    Optional.empty(), Optional.empty()),
                            new com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryEvidence(
                                    com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoverySource.PHYSICAL_VISIT_OR_SURVEY,
                                    "personal-extraction-sample:" + player.activeFleetId().value() + ':' + sourceId,
                                    sample.loadedAtSimulationSeconds(), java.util.OptionalDouble.empty())));
            var states = new ArrayList<>(discovery.knowledgeStates());
            states.removeIf(state -> state.ownerId().equals(owner)); states.add(observed);
            discovery = new Stage20DiscoveryPersistentState(discovery.envelopeVersion(), discovery.rootSeed(),
                    discovery.worldGenerationVersion(), discovery.worldFingerprint(), states);
        }

        /**
         * Receives permitted static station rows from the actual seller's existing archive.
         * The campaign calls this only after its conserved new-game ship sale; no source position
         * or missing archive record is synthesized from generated truth.
         * @param player actual buyer owning the purchased local fleet
         * @param purchasedFleet purchased existing fleet
         * @param seller actual faction seller
         * @param permitted seller-owned civilian station references disclosed by the sale policy
         * @return exact received references, without stock, estimates or ownership grants
         */
        public List<com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectRef> receiveSellerStationBriefing(
                com.spacesim.player.PlayerState player, FleetId purchasedFleet, String seller,
                List<com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectRef> permitted) {
            Objects.requireNonNull(player); Objects.requireNonNull(purchasedFleet); Objects.requireNonNull(seller);
            if (!player.ownedFleetIds().contains(purchasedFleet) || !purchasedFleet.equals(player.activeFleetId())
                    || world.findFactionEconomicState(seller).isEmpty()
                    || !freight.findFreighter(purchasedFleet).orElseThrow().stableFactionId().equals(seller))
                throw new IllegalArgumentException("Station briefing requires its actual purchased ship and faction seller");
            var archive = discovery.knowledgeFor(seller);
            String owner = com.spacesim.world.Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID;
            var personal = discovery.knowledgeFor(owner);
            var received = new ArrayList<com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectRef>();
            double seconds = world.getAuthoritativeWorldTick() * (double) world.findSession(world.getActiveSystemId()).orElseThrow().getClock().getFixedStepSeconds();
            for (var ref : List.copyOf(permitted).stream().distinct().sorted().toList()) {
                if (ref.kind() != com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectKind.INFRASTRUCTURE)
                    throw new IllegalArgumentException("Sale briefing is limited to permitted stations");
                var station = industry.industrial().stations().stream().filter(s -> s.stationId().equals(ref.objectId())
                        && s.systemId().equals(ref.systemId()) && s.stableFactionId().equals(seller)).findFirst();
                var known = archive.entries().stream().filter(e -> e.object().equals(ref) && e.knownLocation().isPresent()).findFirst();
                if (station.isEmpty() || known.isEmpty()
                        || !known.orElseThrow().classificationId().equals(java.util.Optional.of(station.orElseThrow().stationArchetypeId()))) continue;
                personal = new com.spacesim.world.Stage20DiscoveryKnowledgeRuntime().observe(personal,
                        new com.spacesim.world.Stage20DiscoveryKnowledgeRuntime.StaticObservation(ref,
                                com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryState.KNOWN_STATIC_LOCATION,
                                known.orElseThrow().classificationId(), known.orElseThrow().knownLocation(),
                                com.spacesim.world.Stage20DiscoveryKnowledgeState.ResourceKnowledge.none(),
                                new com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryEvidence(
                                        com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoverySource.FACTION_INTELLIGENCE,
                                        "paid-ship-briefing:" + seller + ':' + purchasedFleet.value() + ':' + ref.objectId(), seconds, java.util.OptionalDouble.empty())));
                received.add(ref);
            }
            if (!received.isEmpty()) {
                var states = new ArrayList<>(discovery.knowledgeStates()); states.removeIf(state -> state.ownerId().equals(owner)); states.add(personal);
                discovery = new Stage20DiscoveryPersistentState(discovery.envelopeVersion(), discovery.rootSeed(),
                        discovery.worldGenerationVersion(), discovery.worldFingerprint(), states);
            }
            return List.copyOf(received);
        }

        /** @return ordinary multi-system simulation authority */
        public WorldSimulation world() {
            return world;
        }

        /** @return composed finite source and generated industrial registries */
        public MaterializedGeneratedIndustrialRuntime industry() {
            return industry;
        }

        /** @return canonical generated infrastructure endpoint registry */
        public InfrastructureRegistry infrastructure() {
            return infrastructure;
        }

        /** @return physical persistent freight runtime */
        public Stage20FreightRuntime freight() {
            return freight;
        }

        /** @return exact saved arrival authority bound to the ordinary jump FSM */
        public Stage20LiveArrivalAuthorityIntegration arrival() {
            return arrival;
        }

        /**
         * Executes finite extraction into an exact generated source outpost.
         *
         * @param siteId canonical extraction-site identity
         * @param requestedSourceMassKg gross finite source mass request
         * @param durationSeconds physical extraction interval
         * @return ordinary Stage-18 extraction result
         */
        public ExtractionResult extract(
                String siteId,
                double requestedSourceMassKg,
                double durationSeconds) {
            return industry.sourceOutposts().extract(
                    siteId, requestedSourceMassKg, durationSeconds);
        }

        /**
         * Moves already extracted cargo from its outpost to the order's exact local major hub.
         *
         * @param fleetId order-owning freight fleet
         * @param siteId generated source-outpost site
         * @param massKg recovered commodity mass
         * @param durationSeconds local handling interval
         * @return ordinary Stage-18 physical transfer result
         */
        public TransferResult transferOutpostToOrderSource(
                FleetId fleetId,
                String siteId,
                double massKg,
                double durationSeconds) {
            FreighterState fleetState = freight.findFreighter(fleetId).orElseThrow();
            TransportOrderState order = freight.findOrder(fleetState.activeOrderId()).orElseThrow();
            MaterializedExtractionOutpost outpost = industry.sourceOutposts().outpost(siteId);
            RuntimeEndpoint sourceEndpoint = infrastructure.endpoint(order.sourceEndpointId());
            if (!outpost.site().systemId().equals(order.orderedSystems().get(0))
                    || !outpost.source().sourceState().outputCommodityId().equals(order.commodityId())) {
                throw new IllegalArgumentException(
                        "source outpost does not provide this freight order's commodity/system");
            }
            HandlingCapability handling = intersectHandling(
                    "stage20_5.outpost-to-hub:" + siteId,
                    outpost.stationNode().handlingCapability(),
                    sourceEndpoint.handlingCapability());
            return logistics.transferCommodity(
                    outpost.storage(),
                    sourceEndpoint.storage(),
                    order.commodityId(),
                    massKg,
                    handling,
                    handling.openInterval(durationSeconds));
        }

        /**
         * Transfers a countable manufactured product between two canonical generated-world station
         * endpoints through the ordinary Stage-18 logistics authority.
         *
         * @param sourceStationId exact source station identity
         * @param destinationStationId exact destination station identity
         * @param productContentId ordinary Stage-18 manufactured-product content ID
         * @param count positive product-unit count
         * @param durationSeconds finite physical handling interval
         * @return ordinary Stage-18 physical transfer result
         */
        public TransferResult transferProductBetweenEndpoints(
                String sourceStationId,
                String destinationStationId,
                String productContentId,
                int count,
                double durationSeconds) {
            RuntimeEndpoint source = infrastructure.endpoint(sourceStationId);
            RuntimeEndpoint destination = infrastructure.endpoint(destinationStationId);
            HandlingCapability handling = intersectHandling(
                    "stage20_5.product-transfer:" + source.stationId() + ':' + destination.stationId(),
                    source.handlingCapability(),
                    destination.handlingCapability());
            return logistics.transferProduct(
                    source.storage(),
                    destination.storage(),
                    productContentId,
                    count,
                    handling,
                    handling.openInterval(durationSeconds));
        }

        /**
         * Loads real hub inventory into the exact assigned fleet hold and creates provenance only
         * after the ordinary transfer commits.
         *
         * @param fleetId exact assigned fleet
         * @param massKg physical mass to load
         * @param simulationSeconds authoritative loading time
         * @param durationSeconds finite handling interval
         * @return physical cargo operation result
         */
        public Stage20FreightRuntime.CargoOperationResult loadAtOrderSource(
                FleetId fleetId,
                double massKg,
                double simulationSeconds,
                double durationSeconds) {
            FreighterState fleetState = freight.findFreighter(fleetId).orElseThrow();
            TransportOrderState order = freight.findOrder(fleetState.activeOrderId()).orElseThrow();
            RuntimeEndpoint endpoint = infrastructure.endpoint(order.sourceEndpointId());
            HandlingCapability handling = holdHandling(fleetId, endpoint.handlingCapability());
            Stage20FreightRuntime.CargoOperationResult result = freight.loadCommodity(
                    fleetId,
                    endpoint.storage(),
                    massKg,
                    order.sourceProvenanceId(),
                    simulationSeconds,
                    handling,
                    handling.openInterval(durationSeconds));
            if (result.transferred()) {
                synchronizeFreightEngineeringCargo(fleetId);
            }
            return result;
        }

        /**
         * Unloads the exact physical hold into the order's ordinary destination station storage.
         *
         * @param fleetId arrived assigned fleet
         * @param massKg physical mass to unload
         * @param durationSeconds finite handling interval
         * @return physical cargo operation result
         */
        public Stage20FreightRuntime.CargoOperationResult unloadAtOrderDestination(
                FleetId fleetId,
                double massKg,
                double durationSeconds) {
            FreighterState fleetState = freight.findFreighter(fleetId).orElseThrow();
            TransportOrderState order = freight.findOrder(fleetState.activeOrderId()).orElseThrow();
            RuntimeEndpoint endpoint = infrastructure.endpoint(order.destinationEndpointId());
            HandlingCapability handling = holdHandling(fleetId, endpoint.handlingCapability());
            Stage20FreightRuntime.CargoOperationResult result = freight.unloadCommodity(
                    fleetId,
                    endpoint.storage(),
                    massKg,
                    handling,
                    handling.openInterval(durationSeconds));
            if (result.transferred()) {
                synchronizeFreightEngineeringCargo(fleetId);
            }
            return result;
        }

        /** @param fleetId existing local physical hold whose cargo mass must update the shared fitted-mass authority */
        public void synchronizeFreightEngineeringCargo(FleetId fleetId) {
            FreighterState fleetState = freight.findFreighter(fleetId).orElseThrow();
            FleetPlacementState placement = world.findFleet(fleetId).orElseThrow();
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
                throw new IllegalStateException("freight cargo synchronization requires local placement");
            }
            Entity entity = world.findSession(placement.systemId()).orElseThrow()
                    .getEntityRegistry().require(placement.localEntityId());
            var engineering = entity.getComponent(com.spacesim.components.EngineeringComponent.class);
            if (engineering == null) {
                throw new IllegalStateException("physical freight entity lost EngineeringComponent");
            }
            var state = engineering.runtimeState;
            var previous = state.consumables();
            var nextConsumables = new com.spacesim.ship.ShipEngineeringState.ConsumableState(
                    fleetState.cargoMassKg(),
                    previous.storesMassKg(),
                    previous.missionPayloadMassKg(),
                    previous.missionIntegrationVolumeM3(),
                    previous.interfaceLoads());
            engineering.setRuntimeState(new com.spacesim.ship.ShipEngineeringRuntime.RuntimeState(
                    nextConsumables,
                    state.sharedBusEnergyJ(),
                    state.shipHeatStoredJ(),
                    state.localHeatJByMount(),
                    state.thrustLimitNByMount(),
                    state.coolantBusCapacityW(),
                    state.ftlCooldownSecondsByMount()));
        }

        /**
         * Requests the next exact route edge through the existing ordinary jump FSM.
         *
         * @param fleetId outbound or returning freight fleet
         * @return persistent ordinary moving-to-jump state
         */
        public FleetJumpState requestNextRouteHop(FleetId fleetId) {
            synchronizeCompletedHops();
            FreighterState fleetState = freight.findFreighter(fleetId).orElseThrow();
            TransportOrderState order = freight.findOrder(fleetState.activeOrderId()).orElseThrow();
            if (fleetState.phase() != FreightPhase.OUTBOUND
                    && fleetState.phase() != FreightPhase.RETURNING) {
                throw new IllegalStateException(
                        "next route hop requires OUTBOUND or RETURNING freight phase");
            }
            FleetPlacementState placement = world.findFleet(fleetId).orElseThrow();
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !placement.systemId().equals(fleetState.currentSystemId())) {
                throw new IllegalStateException("next freight hop requires matching local world placement");
            }

            List<StarSystemId> remainingRoute = remainingFreightRoute(fleetState, order);
            if (remainingRoute.size() < 2) {
                throw new IllegalStateException("freight route has no next hop");
            }
            var preparation = world.prepareFleetPropellantDeparture(fleetId, remainingRoute);
            if (!preparation.ready()) {
                StarSystemId directionalDestination = fleetState.phase() == FreightPhase.OUTBOUND
                        ? order.orderedSystems().get(order.orderedSystems().size() - 1)
                        : order.orderedSystems().get(0);
                String rerouteFactionId = fleetState.stableFactionId();
                var alternate = new com.spacesim.world.FleetStrategicRoutePlanner(world.getTopology())
                        .planConstrained(
                                0,
                                fleetState.currentSystemId(),
                                directionalDestination,
                                world.getAuthoritativeWorldTick(),
                                (factionId, from, to, tick, destination) -> {
                                    String controller = world.controllingFaction(to).orElse(null);
                                    return controller == null
                                            || world.evaluateFactionMarketAccess(
                                                    controller, rerouteFactionId).allowed();
                                },
                                candidate -> world.planFleetPropellantJourney(
                                        fleetId, candidate.systems()).feasible())
                        .orElse(null);
                if (alternate != null && alternate.systems().size() >= 2
                        && !alternate.systems().equals(remainingRoute)) {
                    order = freight.rerouteRemaining(fleetId, alternate.systems());
                    fleetState = freight.findFreighter(fleetId).orElseThrow();
                    remainingRoute = remainingFreightRoute(fleetState, order);
                    preparation = world.prepareFleetPropellantDeparture(fleetId, remainingRoute);
                }
            }
            if (!preparation.ready()) {
                throw new IllegalStateException(
                        "freighter cannot start remaining route without a safe finite-propellant plan: "
                                + fleetId + " reason=" + preparation.reason()
                                + " requiredDeltaVMps=" + preparation.journey().requiredDeltaVMps()
                                + " projectedRemainingReactionMassKg="
                                + preparation.journey().projectedRemainingReactionMassKg());
            }
            return world.requestFleetJump(fleetId, remainingRoute.get(1));
        }

        private static List<StarSystemId> remainingFreightRoute(
                FreighterState fleetState,
                TransportOrderState order) {
            ArrayList<StarSystemId> remaining = new ArrayList<>();
            if (fleetState.phase() == FreightPhase.OUTBOUND) {
                for (int index = fleetState.routeIndex(); index < order.orderedSystems().size(); index++) {
                    remaining.add(order.orderedSystems().get(index));
                }
            } else if (fleetState.phase() == FreightPhase.RETURNING) {
                for (int index = fleetState.routeIndex(); index >= 0; index--) {
                    remaining.add(order.orderedSystems().get(index));
                }
            } else {
                throw new IllegalStateException(
                        "remaining freight route requires OUTBOUND or RETURNING phase");
            }
            return List.copyOf(remaining);
        }

        /**
         * Advances the ordinary world and then commits only completed exact route arrivals.
         *
         * @param realDeltaSeconds non-negative render-frame duration
         * @return ordinary world advance report
         */
        public WorldSimulation.AdvanceReport advanceFrame(float realDeltaSeconds) {
            WorldSimulation.AdvanceReport report = world.advanceFrame(realDeltaSeconds);
            synchronizeCompletedHops();
            return report;
        }

        /**
         * Permanently removes a local freight entity and its aboard cargo without replacement.
         *
         * @param fleetId exact local freight fleet
         * @param policy ordinary world economic destruction policy
         * @return paired world/freight destruction evidence
         */
        public FreightDestructionResult destroyLocalFreighter(
                FleetId fleetId,
                DestructionPolicy policy) {
            FreighterState fleetState = freight.findFreighter(fleetId).orElseThrow();
            FleetPlacementState placement = world.findFleet(fleetId).orElseThrow();
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
                throw new IllegalStateException("local freight destruction cannot target transit placement");
            }
            var worldResult = world.destroyEntity(
                    placement.systemId(), placement.localEntityId(), Objects.requireNonNull(policy, "policy"));
            arrival.materialization(placement.systemId())
                    .releasePhysicalStateForWorldTransfer(placement.localEntityId());
            var freightResult = freight.destroy(fleetId);
            if (!fleetState.currentSystemId().equals(placement.systemId())) {
                throw new IllegalStateException("destroyed freight system identity changed during operation");
            }
            return new FreightDestructionResult(worldResult, freightResult);
        }

        /**
         * Resolves the exact production-bound cargo sprite without mutating the freight state.
         *
         * @param fleetId physical freight identity
         * @return exact-scale presentation projection
         */
        public ResolvedSprite freightSprite(FleetId fleetId) {
            FreighterState state = freight.findFreighter(fleetId).orElseThrow();
            return Stage20MinimumPlayableSpriteCatalog.resolveShip(
                    state.hullId(),
                    Stage20MinimumPlayableSpriteCatalog.ShipRole.CARGO_TRANSPORT,
                    Stage175ICombatTestContentPack.load());
        }

        /**
         * Captures a self-consistent campaign/world/freight checkpoint, rebinding only the freight
         * envelope fingerprint after finite source reserve changes and refreshing the redundant
         * local freight physical mirror from the exact Stage-20 physical sidecar.
         *
         * @return complete atomic current runtime checkpoint
         */
        public Stage20GeneratedWorldRuntimePersistentState captureState() {
            synchronizeCompletedHops();
            Stage20GeneratedCampaignPersistentState withInfrastructure =
                    infrastructure.captureInto(campaignAuthority);
            Stage20GeneratedCampaignPersistentState campaign =
                    industry.captureCampaignState(withInfrastructure);
            var previousIndustry = campaign.industrialState();
            campaign = replaceIndustry(campaign, new Stage18IndustrialState(previousIndustry.schemaVersion(),
                    previousIndustry.contentFingerprint(), Math.max(manufacturingQueue.lastProcessedTick(), constructionQueue.lastProcessedTick()), previousIndustry.sources(),
                    previousIndustry.stationStorages(), previousIndustry.facilities(), previousIndustry.yards(),
                    constructionQueue.capture(), manufacturingQueue.capture()));
            campaign = new Stage20GeneratedCampaignPersistentState(campaign.schemaVersion(), campaign.generationIdentity(),
                    campaign.materializedWorld(), campaign.materializationState(), campaign.industrialState(),
                    new Stage20DiscoveryPersistentState(discovery.envelopeVersion(), discovery.rootSeed(),
                            discovery.worldGenerationVersion(), campaign.materializedWorld().worldFingerprint(), discovery.knowledgeStates()),
                    campaign.openRuntimeBoundaries());
            List<LocalFleetPhysicalState> localPhysical = captureLocalFleetPhysicalStates();
            Stage20FreightPersistentState freightState = rebindFreightFingerprint(
                    freight.capture(), campaign.materializedWorld().worldFingerprint());
            freightState = synchronizeFreightPhysicalMirrors(freightState, localPhysical);
            for (var fleet : freightState.freighters()) validateFreightLegalFaction(world, fleet);
            return new Stage20GeneratedWorldRuntimePersistentState(
                    Stage20GeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                    CURRENT_VERSION,
                    campaign,
                    world.snapshot(),
                    world.getActiveSystemId(),
                    world.getStrategicStepTicks(),
                    world.getRemoteUpdateBudgetPerFrame(),
                    freightState,
                    localPhysical);
        }

        private List<LocalFleetPhysicalState> captureLocalFleetPhysicalStates() {
            ArrayList<LocalFleetPhysicalState> result = new ArrayList<>();
            for (FleetPlacementState placement : world.getFleetPlacements()) {
                if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
                    continue;
                }
                LocalPhysicalKinematics physical = arrival.materialization(placement.systemId())
                        .physicalState(placement.localEntityId()).orElseThrow(
                                () -> new IllegalStateException(
                                        "ordinary local fleet lacks exact Stage-20 physical state: "
                                                + placement.id()));
                result.add(new LocalFleetPhysicalState(
                        placement.id(), placement.systemId(), physical));
            }
            result.sort(java.util.Comparator.comparing(LocalFleetPhysicalState::fleetId));
            return List.copyOf(result);
        }

        private static Stage20FreightPersistentState synchronizeFreightPhysicalMirrors(
                Stage20FreightPersistentState source,
                List<LocalFleetPhysicalState> localPhysical) {
            Map<FleetId, LocalFleetPhysicalState> exactByFleet = new HashMap<>();
            for (LocalFleetPhysicalState state : localPhysical) {
                if (exactByFleet.putIfAbsent(state.fleetId(), state) != null) {
                    throw new IllegalStateException(
                            "duplicate exact local fleet state while capturing freight mirror: "
                                    + state.fleetId());
                }
            }
            ArrayList<FreighterState> fleets = new ArrayList<>(source.freighters().size());
            boolean changed = false;
            for (FreighterState fleetState : source.freighters()) {
                LocalFleetPhysicalState exact = exactByFleet.get(fleetState.fleetId());
                if (exact == null) {
                    fleets.add(fleetState);
                    continue;
                }
                if (!fleetState.currentSystemId().equals(exact.systemId())) {
                    throw new IllegalStateException(
                            "local freight system differs from exact physical capture: "
                                    + fleetState.fleetId());
                }
                if (fleetState.physicalState().equals(exact.physicalState())) {
                    fleets.add(fleetState);
                    continue;
                }
                fleets.add(new FreighterState(
                        fleetState.fleetId(),
                        fleetState.stableFactionId(),
                        fleetState.ownershipOrdinal(),
                        fleetState.hullId(),
                        fleetState.fitId(),
                        fleetState.cargoCapacityKg(),
                        fleetState.currentSystemId(),
                        exact.physicalState(),
                        fleetState.phase(),
                        fleetState.activeOrderId(),
                        fleetState.routeIndex(),
                        fleetState.cargoStorage(),
                        fleetState.legalFactionId(), fleetState.carriedEquipmentMassKg()));
                changed = true;
            }
            if (!changed) {
                return source;
            }
            return new Stage20FreightPersistentState(
                    source.schemaVersion(),
                    source.rootSeed(),
                    source.generatorVersion(),
                    source.worldFingerprint(),
                    source.materializationVersion(),
                    source.compatibilityAuthorityVersion(),
                    source.nextFleetIdValue(),
                    source.nextCargoLotOrdinal(),
                    fleets,
                    source.cargoLots(),
                    source.orders(), source.personalMiningOrders(), source.productLots());
        }

        private void synchronizeCompletedHops() {
            for (FreighterState fleetState : freight.capture().freighters()) {
                if (fleetState.phase() != FreightPhase.IDLE
                        && fleetState.phase() != FreightPhase.OUTBOUND
                        && fleetState.phase() != FreightPhase.RETURNING) {
                    continue;
                }
                FleetPlacementState placement = world.findFleet(fleetState.fleetId()).orElseThrow();
                if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                        || placement.systemId().equals(fleetState.currentSystemId())) {
                    continue;
                }
                Entity arrivedEntity = world.findSession(placement.systemId()).orElseThrow()
                        .getEntityRegistry().require(placement.localEntityId());
                if (arrivedEntity.getComponent(com.spacesim.components.EngineeringComponent.class) == null) {
                    // One-time migration for historical saves captured while generated freight was
                    // already detached in transit before finite propulsion entered the world schema.
                    arrivedEntity.add(freightEngineering(fleetState));
                }
                LocalPhysicalKinematics exact = arrival.materialization(placement.systemId())
                        .physicalState(placement.localEntityId()).orElseThrow(
                                () -> new IllegalStateException(
                                        "ordinary freight arrival lacks exact Stage-20 physical state"));
                if (fleetState.phase() == FreightPhase.IDLE) {
                    freight.synchronizeIdleArrival(fleetState.fleetId(), placement.systemId(), exact);
                } else if (fleetState.phase() == FreightPhase.OUTBOUND) {
                    freight.completeNextOutboundHop(
                            fleetState.fleetId(), placement.systemId(), exact);
                } else {
                    freight.completeNextReturnHop(
                            fleetState.fleetId(),
                            placement.systemId(),
                            exact,
                            currentSimulationSeconds());
                }
            }
        }

        private double currentSimulationSeconds() {
            var session = world.findSession(world.getActiveSystemId()).orElseThrow();
            return world.getAuthoritativeWorldTick() * (double) session.getClock().getFixedStepSeconds();
        }

        private static HandlingCapability holdHandling(
                FleetId fleetId,
                HandlingCapability endpoint) {
            return new HandlingCapability(
                    "stage20_5.freight-hold:" + fleetId.value() + ':' + endpoint.handlingId(),
                    endpoint.supportedStorageClassIds(),
                    endpoint.massRateKgPerSecond(),
                    endpoint.maxUnitMassKg());
        }
    }

    /** Paired evidence that one ordinary world entity and the same freight identity were destroyed. */
    public record FreightDestructionResult(
            com.spacesim.world.DestructionResult worldResult,
            Stage20FreightRuntime.DestructionResult freightResult) {
        /**
         * Validates one identity-preserving destruction pair.
         *
         * @param worldResult ordinary world destruction result
         * @param freightResult matching physical-freight destruction result
         */
        public FreightDestructionResult {
            Objects.requireNonNull(worldResult, "worldResult");
            Objects.requireNonNull(freightResult, "freightResult");
        }
    }

    /**
     * One canonical ordinary station endpoint used by local staging, loading or unloading.
     *
     * @param systemId exact generated system
     * @param stationId exact generated station identity
     * @param stationArchetypeId exact Stage-18 station archetype
     * @param position exact generated local physical position
     * @param storage ordinary mutable Stage-18 storage
     * @param handlingCapability ordinary physical handling interface
     * @param generatedIndustrial whether Stage-20.5 owns this endpoint runtime
     */
    public record RuntimeEndpoint(
            StarSystemId systemId,
            String stationId,
            String stationArchetypeId,
            LocalPhysicalPosition position,
            Stage18StationStorage storage,
            HandlingCapability handlingCapability,
            boolean generatedIndustrial) {
        /**
         * Validates one exact endpoint binding.
         *
         * @param systemId exact generated system
         * @param stationId exact generated station placement ID
         * @param stationArchetypeId exact Stage-18 station archetype
         * @param position exact generated local physical position
         * @param storage ordinary mutable Stage-18 storage
         * @param handlingCapability ordinary physical handling interface
         * @param generatedIndustrial whether Stage-20.5 owns this endpoint runtime
         */
        public RuntimeEndpoint {
            Objects.requireNonNull(systemId, "systemId");
            stationId = requireText(stationId, "stationId");
            stationArchetypeId = requireText(stationArchetypeId, "stationArchetypeId");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(storage, "storage");
            Objects.requireNonNull(handlingCapability, "handlingCapability");
            if (!storage.stationId().equals(stationId)) {
                throw new IllegalArgumentException("endpoint storage identity differs from generated station");
            }
        }
    }

    /** Deterministic exact-ID registry for all freight loading and unloading endpoints. */
    public static final class InfrastructureRegistry {
        private final Map<String, RuntimeEndpoint> endpoints;

        private InfrastructureRegistry(Map<String, RuntimeEndpoint> endpoints) {
            this.endpoints = Map.copyOf(endpoints);
        }

        private static InfrastructureRegistry materialize(
                Stage20GeneratedCampaignPersistentState campaign,
                MaterializedGeneratedIndustrialRuntime industry,
                Stage18ManufacturingProductRegistry products,
                boolean seedBootstrapPropellant) {
            Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
            Stage18ManufacturingProductRegistry productRegistry = Objects.requireNonNull(products, "products");
            Stage18StationInfrastructureCatalog infrastructure =
                    Stage18StationInfrastructureCatalogLoader.loadDefault();
            Map<String, StationStorageSnapshot> savedStorage = new HashMap<>();
            campaign.industrialState().stationStorages().forEach(value ->
                    savedStorage.put(value.stationId(), value));
            TreeMap<String, RuntimeEndpoint> result = new TreeMap<>();
            for (MaterializedIndustrialStation station : industry.industrial().stations()) {
                result.put(station.stationId(), new RuntimeEndpoint(
                        station.systemId(),
                        station.stationId(),
                        station.stationArchetypeId(),
                        station.position(),
                        station.storage(),
                        station.stationNode().handlingCapability(),
                        true));
            }
            for (CanonicalRow row : campaign.materializedWorld().worldRows()) {
                if (!INFRASTRUCTURE_DOMAIN.equals(row.domain())) {
                    continue;
                }
                requireValueCount(row, 9);
                PlacementKind kind = parsePlacementKind(row.values().get(1), row);
                if (kind != PlacementKind.MAJOR_HUB_STATION
                        && kind != PlacementKind.INDEPENDENT_STATION) {
                    continue;
                }
                StarSystemId systemId = new StarSystemId(parsePositiveLong(
                        row.values().get(0), row, "systemId"));
                String stationId = infrastructureId(row.stableId());
                String archetypeId = requireText(row.values().get(2), "stationArchetypeId");
                LocalPhysicalPosition position = new LocalPhysicalPosition(
                        parseLong(row.values().get(3), row, "cellX"),
                        parseLong(row.values().get(4), row, "cellY"),
                        parseDouble(row.values().get(5), row, "offsetXM"),
                        parseDouble(row.values().get(6), row, "offsetYM"));
                RuntimeEndpoint existing = result.get(stationId);
                if (existing != null) {
                    if (!existing.systemId().equals(systemId)
                            || !existing.stationArchetypeId().equals(archetypeId)
                            || !existing.position().equals(position)) {
                        throw new IllegalArgumentException(
                                "generated industrial endpoint differs from infrastructure placement");
                    }
                    continue;
                }
                StationArchetypeDefinition archetype = infrastructure.findArchetype(archetypeId);
                if (archetype == null) {
                    throw new IllegalArgumentException(
                            "canonical infrastructure references unknown Stage-18 archetype: " + archetypeId);
                }
                Stage18StationIndustrialNode node = Stage18StationIndustrialNode.instantiate(
                        stationId, ORBITAL_LOCATION_TAG, archetype, ontology, productRegistry);
                Stage18StationStorage storage = node.storage();
                StationStorageSnapshot persisted = savedStorage.get(stationId);
                if (persisted != null) {
                    if (!persisted.capacityByStorageClassKg().equals(
                            archetype.storageCapacityByClassKg())) {
                        throw new IllegalArgumentException(
                                "canonical infrastructure storage differs from its archetype");
                    }
                    storage = Stage18StationStorage.restore(ontology, productRegistry, persisted);
                } else if (seedBootstrapPropellant
                        && archetype.storageCapacityByClassKg().containsKey(LIQUID_STORAGE_CLASS_ID)
                        && archetype.transferStorageClassIds().contains(LIQUID_STORAGE_CLASS_ID)) {
                    double reserveKg = Math.min(
                            MAX_INITIAL_PROPELLANT_RESERVE_KG,
                            archetype.storageCapacityByClassKg().get(LIQUID_STORAGE_CLASS_ID) * 0.5d);
                    if (reserveKg > 0d) {
                        storage = new Stage18StationStorage(
                                ontology,
                                productRegistry,
                                stationId,
                                archetype.storageCapacityByClassKg(),
                                Map.of(PROPELLANT_COMMODITY_ID, reserveKg),
                                Map.of());
                    }
                }
                if (result.putIfAbsent(stationId, new RuntimeEndpoint(
                        systemId,
                        stationId,
                        archetypeId,
                        position,
                        storage,
                        node.handlingCapability(),
                        false)) != null) {
                    throw new IllegalArgumentException("duplicate canonical infrastructure station ID");
                }
            }
            if (result.isEmpty()) {
                throw new IllegalArgumentException("generated campaign has no infrastructure endpoints");
            }
            return new InfrastructureRegistry(result);
        }

        /** @return exact station-ID ordered endpoint list */
        public List<RuntimeEndpoint> endpoints() {
            return endpoints.values().stream()
                    .sorted(java.util.Comparator.comparing(RuntimeEndpoint::stationId))
                    .toList();
        }

        /**
         * Finds one exact runtime station endpoint.
         *
         * @param stationId generated station identity
         * @return matching ordinary endpoint
         */
        public RuntimeEndpoint endpoint(String stationId) {
            RuntimeEndpoint result = endpoints.get(requireText(stationId, "stationId"));
            if (result == null) {
                throw new IllegalArgumentException("unknown generated runtime endpoint: " + stationId);
            }
            return result;
        }

        private Stage20GeneratedCampaignPersistentState captureInto(
                Stage20GeneratedCampaignPersistentState campaign) {
            Stage18IndustrialState previous = campaign.industrialState();
            Set<String> owned = endpoints.values().stream()
                    .filter(value -> !value.generatedIndustrial())
                    .map(RuntimeEndpoint::stationId)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            ArrayList<StationStorageSnapshot> storage = new ArrayList<>();
            previous.stationStorages().stream()
                    .filter(value -> !owned.contains(value.stationId()))
                    .forEach(storage::add);
            endpoints.values().stream()
                    .filter(value -> !value.generatedIndustrial())
                    .map(value -> value.storage().snapshot())
                    .forEach(storage::add);
            Stage18IndustrialState industry = new Stage18IndustrialState(
                    Stage18IndustrialState.CURRENT_VERSION,
                    previous.contentFingerprint(),
                    previous.simulationTick(),
                    previous.sources(),
                    storage,
                    previous.facilities(),
                    previous.yards(),
                    previous.constructionOrders(),
                    previous.processOrders());
            return replaceIndustry(campaign, industry);
        }
    }

    private static HandlingCapability intersectHandling(
            String handlingId,
            HandlingCapability first,
            HandlingCapability second) {
        TreeSet<String> classes = new TreeSet<>(first.supportedStorageClassIds());
        classes.retainAll(second.supportedStorageClassIds());
        return new HandlingCapability(
                handlingId,
                classes,
                Math.min(first.massRateKgPerSecond(), second.massRateKgPerSecond()),
                Math.min(first.maxUnitMassKg(), second.maxUnitMassKg()));
    }

    private static Stage20FreightPersistentState rebindFreightFingerprint(
            Stage20FreightPersistentState state,
            String fingerprint) {
        return new Stage20FreightPersistentState(
                state.schemaVersion(),
                state.rootSeed(),
                state.generatorVersion(),
                fingerprint,
                state.materializationVersion(),
                state.compatibilityAuthorityVersion(),
                state.nextFleetIdValue(),
                state.nextCargoLotOrdinal(),
                state.freighters(),
                state.cargoLots(),
                state.orders(), state.personalMiningOrders(), state.productLots());
    }

    private static Stage20GeneratedCampaignPersistentState replaceIndustry(
            Stage20GeneratedCampaignPersistentState campaign,
            Stage18IndustrialState industry) {
        return new Stage20GeneratedCampaignPersistentState(
                campaign.schemaVersion(),
                campaign.generationIdentity(),
                campaign.materializedWorld(),
                campaign.materializationState(),
                industry,
                campaign.discoveryState(),
                campaign.openRuntimeBoundaries());
    }

    private static String infrastructureId(String stableId) {
        int separator = stableId.indexOf(':');
        if (separator < 0 || separator == stableId.length() - 1) {
            throw new IllegalArgumentException("malformed infrastructure stable ID: " + stableId);
        }
        return stableId.substring(separator + 1);
    }

    private static void requireValueCount(CanonicalRow row, int count) {
        if (row.values().size() < count) {
            throw new IllegalArgumentException(
                    "malformed " + row.domain() + " row: " + row.stableId());
        }
    }

    private static PlacementKind parsePlacementKind(String value, CanonicalRow row) {
        try {
            return PlacementKind.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "infrastructure kind is invalid in " + row.stableId(), exception);
        }
    }

    private static long parsePositiveLong(String value, CanonicalRow row, String field) {
        long result = parseLong(value, row, field);
        if (result <= 0L) {
            throw new IllegalArgumentException(field + " must be positive in " + row.stableId());
        }
        return result;
    }

    private static long parseLong(String value, CanonicalRow row, String field) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " is invalid in " + row.stableId(), exception);
        }
    }

    private static double parseDouble(String value, CanonicalRow row, String field) {
        try {
            double result = Double.parseDouble(value);
            if (!Double.isFinite(result)) {
                throw new NumberFormatException("non-finite");
            }
            return result;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " is invalid in " + row.stableId(), exception);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.strip();
    }
}
