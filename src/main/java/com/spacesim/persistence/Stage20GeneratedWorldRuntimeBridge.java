package com.spacesim.persistence;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TransformComponent;
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

    private Stage20GeneratedWorldRuntimeBridge() {
        throw new AssertionError("No instances");
    }

    /**
     * Performs the one-time bootstrap materialization into an existing ordinary generated topology.
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
        Stage20GeneratedCampaignPersistentState saved = Objects.requireNonNull(campaign, "campaign");
        WorldSimulation runtime = Objects.requireNonNull(world, "world");
        MaterializedGeneratedIndustrialRuntime industry =
                Stage20GeneratedIndustrialRuntimeBridge.materializeBootstrap(
                        saved, Objects.requireNonNull(specialization, "specialization"));
        InfrastructureRegistry infrastructure = InfrastructureRegistry.materialize(saved, industry);
        Stage20FreightPersistentState freightState = Stage20FreightRuntimeMaterializer.materializeBootstrap(
                saved,
                specialization,
                runtime.snapshot().nextFleetIdValue());
        validateOrderEndpoints(freightState, infrastructure);

        Stage20LiveArrivalAuthorityIntegration arrival =
                Stage20LiveArrivalAuthorityIntegration.restoreAndBind(saved, runtime);
        materializeFreightEntities(runtime, freightState, arrival);
        Stage20FreightRuntime freight = Stage20FreightRuntime.restore(freightState);
        return new LiveRuntime(saved, runtime, industry, infrastructure, freight, arrival);
    }

    /**
     * Restores a composed runtime without invoking any Stage-20 generator or planner.
     *
     * @param checkpoint exact decoded atomic runtime checkpoint
     * @return independent restored live runtime
     */
    public static LiveRuntime restore(Stage20GeneratedWorldRuntimePersistentState checkpoint) {
        Stage20GeneratedWorldRuntimePersistentState saved = Objects.requireNonNull(
                checkpoint, "checkpoint");
        WorldSimulation world = WorldSimulation.restore(saved.worldState(), saved.activeSystemId());
        MaterializedGeneratedIndustrialRuntime industry =
                Stage20GeneratedIndustrialRuntimeBridge.restore(saved.campaign());
        InfrastructureRegistry infrastructure = InfrastructureRegistry.materialize(
                saved.campaign(), industry);
        Stage20FreightRuntime freight = Stage20FreightRuntime.restore(
                saved.campaign(),
                saved.freight(),
                Stage20FreightRuntimeMaterializer.FreighterCompatibilityAuthority.currentProvisional(),
                Stage175ICombatTestContentPack.load());
        validateOrderEndpoints(saved.freight(), infrastructure);
        Stage20LiveArrivalAuthorityIntegration arrival =
                Stage20LiveArrivalAuthorityIntegration.restoreAndBind(saved.campaign(), world);
        validateAndRegisterRestoredFreight(world, saved.freight(), arrival);
        return new LiveRuntime(saved.campaign(), world, industry, infrastructure, freight, arrival);
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
                .add(new FactionComponent(runtimeFactionId));
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
            if (placement.locationKind() == FleetLocationKind.IN_TRANSIT) {
                continue;
            }
            Entity entity = world.findSession(placement.systemId()).orElseThrow()
                    .getEntityRegistry().require(placement.localEntityId());
            ArchetypeComponent archetype = entity.getComponent(ArchetypeComponent.class);
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            Integer expectedFaction = world.findFactionRuntimeId(fleet.stableFactionId()).orElseThrow();
            if (archetype == null || !archetype.contentId.equals(fleet.hullId())
                    || faction == null || faction.factionId != expectedFaction) {
                throw new IllegalArgumentException(
                        "restored ordinary freight entity differs from persisted hull/owner identity");
            }
            arrival.materialization(placement.systemId())
                    .registerPhysicalState(placement.localEntityId(), fleet.physicalState());
        }
    }

    private static void validateOrderEndpoints(
            Stage20FreightPersistentState freight,
            InfrastructureRegistry infrastructure) {
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
        private final WorldSimulation world;
        private final MaterializedGeneratedIndustrialRuntime industry;
        private final InfrastructureRegistry infrastructure;
        private final Stage20FreightRuntime freight;
        private final Stage20LiveArrivalAuthorityIntegration arrival;
        private final Stage18LogisticsRuntime logistics;

        private LiveRuntime(
                Stage20GeneratedCampaignPersistentState campaignAuthority,
                WorldSimulation world,
                MaterializedGeneratedIndustrialRuntime industry,
                InfrastructureRegistry infrastructure,
                Stage20FreightRuntime freight,
                Stage20LiveArrivalAuthorityIntegration arrival) {
            this.campaignAuthority = Objects.requireNonNull(campaignAuthority, "campaignAuthority");
            this.world = Objects.requireNonNull(world, "world");
            this.industry = Objects.requireNonNull(industry, "industry");
            this.infrastructure = Objects.requireNonNull(infrastructure, "infrastructure");
            this.freight = Objects.requireNonNull(freight, "freight");
            this.arrival = Objects.requireNonNull(arrival, "arrival");
            this.logistics = new Stage18LogisticsRuntime(
                    Stage18ResourceOntologyLoader.loadDefault(),
                    Stage18ManufacturingProductRegistry.loadDefault());
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
            return freight.loadCommodity(
                    fleetId,
                    endpoint.storage(),
                    massKg,
                    order.sourceProvenanceId(),
                    simulationSeconds,
                    handling,
                    handling.openInterval(durationSeconds));
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
            return freight.unloadCommodity(
                    fleetId,
                    endpoint.storage(),
                    massKg,
                    handling,
                    handling.openInterval(durationSeconds));
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
            int nextIndex = switch (fleetState.phase()) {
                case OUTBOUND -> fleetState.routeIndex() + 1;
                case RETURNING -> fleetState.routeIndex() - 1;
                default -> throw new IllegalStateException(
                        "next route hop requires OUTBOUND or RETURNING freight phase");
            };
            if (nextIndex < 0 || nextIndex >= order.orderedSystems().size()) {
                throw new IllegalStateException("freight route has no next hop");
            }
            FleetPlacementState placement = world.findFleet(fleetId).orElseThrow();
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !placement.systemId().equals(fleetState.currentSystemId())) {
                throw new IllegalStateException("next freight hop requires matching local world placement");
            }
            return world.requestFleetJump(fleetId, order.orderedSystems().get(nextIndex));
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
         * envelope fingerprint after finite source reserve changes.
         *
         * @return complete atomic current runtime checkpoint
         */
        public Stage20GeneratedWorldRuntimePersistentState captureState() {
            synchronizeCompletedHops();
            Stage20GeneratedCampaignPersistentState withInfrastructure =
                    infrastructure.captureInto(campaignAuthority);
            Stage20GeneratedCampaignPersistentState campaign =
                    industry.captureCampaignState(withInfrastructure);
            Stage20FreightPersistentState freightState = rebindFreightFingerprint(
                    freight.capture(), campaign.materializedWorld().worldFingerprint());
            return new Stage20GeneratedWorldRuntimePersistentState(
                    Stage20GeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                    CURRENT_VERSION,
                    campaign,
                    world.snapshot(),
                    world.getActiveSystemId(),
                    freightState);
        }

        private void synchronizeCompletedHops() {
            for (FreighterState fleetState : freight.capture().freighters()) {
                if (fleetState.phase() != FreightPhase.OUTBOUND
                        && fleetState.phase() != FreightPhase.RETURNING) {
                    continue;
                }
                FleetPlacementState placement = world.findFleet(fleetState.fleetId()).orElseThrow();
                if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                        || placement.systemId().equals(fleetState.currentSystemId())) {
                    continue;
                }
                LocalPhysicalKinematics exact = arrival.materialization(placement.systemId())
                        .physicalState(placement.localEntityId()).orElseThrow(
                                () -> new IllegalStateException(
                                        "ordinary freight arrival lacks exact Stage-20 physical state"));
                if (fleetState.phase() == FreightPhase.OUTBOUND) {
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
     * @param generatedIndustrial whether Stage-20.5C owns this endpoint runtime
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
         * @param stationId exact generated station identity
         * @param stationArchetypeId exact Stage-18 station archetype
         * @param position exact generated local physical position
         * @param storage ordinary mutable Stage-18 storage
         * @param handlingCapability ordinary physical handling interface
         * @param generatedIndustrial whether Stage-20.5C owns this endpoint runtime
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
                MaterializedGeneratedIndustrialRuntime industry) {
            Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
            Stage18ManufacturingProductRegistry products = Stage18ManufacturingProductRegistry.loadDefault();
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
                        stationId, ORBITAL_LOCATION_TAG, archetype, ontology, products);
                Stage18StationStorage storage = node.storage();
                StationStorageSnapshot persisted = savedStorage.get(stationId);
                if (persisted != null) {
                    if (!persisted.capacityByStorageClassKg().equals(
                            archetype.storageCapacityByClassKg())) {
                        throw new IllegalArgumentException(
                                "canonical infrastructure storage differs from its archetype");
                    }
                    storage = Stage18StationStorage.restore(ontology, products, persisted);
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
                state.orders());
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
