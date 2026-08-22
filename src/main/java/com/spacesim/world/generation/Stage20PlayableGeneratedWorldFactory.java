package com.spacesim.world.generation;

import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.content.Stage18FacilityCatalog.FacilityDefinition;
import com.spacesim.content.Stage18ShipyardCatalog.YardDefinition;
import com.spacesim.content.Stage18ShipyardCatalogLoader;
import com.spacesim.economy.Stage18FacilityRuntime.InstalledFacilityState;
import com.spacesim.economy.Stage18ShipyardRuntime.InstalledYardState;
import com.spacesim.economy.Stage18StationStorage.StationStorageSnapshot;
import com.spacesim.persistence.Stage18IndustrialState;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistence;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.LiveRuntime;
import com.spacesim.persistence.Stage20MaterializationPersistence;
import com.spacesim.persistence.Stage20MaterializationPersistentState;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.simulation.Stage20MaterializationService;
import com.spacesim.world.FactionEconomicState;
import com.spacesim.world.FactionIdentityResolver;
import com.spacesim.world.FactionStrategicState;
import com.spacesim.world.Stage20BootstrapFreightOwnershipPlan;
import com.spacesim.world.Stage20BootstrapProductionCapacityCalculator.ProcessKind;
import com.spacesim.world.Stage20DiscoveryKnowledgeState;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.FacilitySlotKey;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.FacilityStateAssignment;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.OperatingReport;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.OperatingStateAuthority;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.StationKey;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.StationServiceAllocation;
import com.spacesim.world.Stage20IndustrialInitialInventoryPlan;
import com.spacesim.world.Stage20IndustrialInitialInventoryPlan.InitialInventoryAuthority;
import com.spacesim.world.Stage20IndustrialInitialInventoryPlan.InventoryReport;
import com.spacesim.world.Stage20IndustrialInitialInventoryPlan.StationInventoryAssignment;
import com.spacesim.world.Stage20IndustrialInputFreightOwnershipPlan;
import com.spacesim.world.Stage20IndustrialInputFreightOwnershipPlan.IndustrialFreightReport;
import com.spacesim.world.Stage20IndustrialInputFreightOwnershipPlan.ProcessOwnerAssignment;
import com.spacesim.world.Stage20IndustrialInputFreightOwnershipPlan.ProcessOwnershipAuthority;
import com.spacesim.world.Stage20IndustrialInputReservationPlan;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.ProcessOutputRequest;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.ProcessSelectionKey;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.SelectionAuthority;
import com.spacesim.world.Stage20IndustrialInputRouteEvidencePlan;
import com.spacesim.world.Stage20IndustrialShipyardInstallationPlan;
import com.spacesim.world.Stage20IndustrialShipyardInstallationPlan.InstalledYardAssignment;
import com.spacesim.world.Stage20IndustrialShipyardInstallationPlan.ShipyardInstallationAuthority;
import com.spacesim.world.Stage20IndustrialShipyardInstallationPlan.StationYardAuthority;
import com.spacesim.world.Stage20IndustrialShipyardInstallationPlan.YardSlotKey;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.CandidateReport;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.StationCandidate;
import com.spacesim.world.Stage20OperationalIndustrialSpecializationPlan;
import com.spacesim.world.Stage20OperationalIndustrialSpecializationPlan.OperationalSpecializationReport;
import com.spacesim.world.Stage20SpecialLocationGenerator;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.RouteAdmissionStatus;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StarSystemSimulationState;
import com.spacesim.world.WorldFactionIdentityState;
import com.spacesim.world.WorldSimulation;
import com.spacesim.world.WorldState;
import com.spacesim.world.generation.Stage20ResolvedGeneratedWorldProductionProbe.ResolvedProbeResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Production bootstrap for the accepted Stage-20 generated world and its Stage-20.5 runtime.
 *
 * <p>This is the executable counterpart of the final acceptance fixture. It invokes the accepted
 * generator/planner chain only for a new campaign, captures the canonical generated campaign and
 * then materializes the ordinary source, station, freight and jump runtimes. Save-game resume must
 * use {@link Stage20GeneratedWorldRuntimeBridge#restore} instead of calling this factory again.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20PlayableGeneratedWorldFactory {
    /** Seed used by the first public generated-world viewer. */
    public static final long DEFAULT_WORLD_SEED = 1L;

    private static final ContentCatalog CONTENT = ContentCatalogLoader.loadDefault();

    private Stage20PlayableGeneratedWorldFactory() {
        throw new AssertionError("No instances");
    }

    /**
     * Generates and materializes one new playable world through the accepted production chain.
     *
     * @param rootSeed deterministic new-campaign seed
     * @return live generated-world runtime plus immutable bootstrap authority
     */
    public static GeneratedWorld create(long rootSeed) {
        BootstrapFixture fixture = operationalFixture(rootSeed);
        Stage20GeneratedCampaignPersistentState campaign = campaign(fixture);
        WorldSimulation world = ordinaryWorld(fixture.resolved());
        LiveRuntime runtime = Stage20GeneratedWorldRuntimeBridge.materializeBootstrap(
                campaign, fixture.specialization(), world);
        return new GeneratedWorld(rootSeed, CONTENT, fixture.specialization(), runtime);
    }

    private static BootstrapFixture operationalFixture(long rootSeed) {
        ResolvedProbeResult resolved = Stage20ResolvedGeneratedWorldProductionProbe.runCurrent(rootSeed);
        CandidateReport candidates = Stage20IndustrialSpecializationCandidatePlan.reconstruct(resolved);
        var routes = Stage20IndustrialInputRouteEvidencePlan.reconstruct(resolved);
        var selected = routes.processes().stream()
                .filter(value -> value.candidate().capacity().processKind() == ProcessKind.REFINING)
                .filter(value -> value.candidate().throughput().inputLimitedOutputKgPerSecond() > 0d)
                .filter(value -> value.inputs().size() == 1)
                .filter(value -> stationFor(candidates, ProcessSelectionKey.from(value.candidate()))
                        .facilitySlots().stream().map(slot -> slot.definition().id()).toList()
                        .containsAll(List.of(
                                "facility.fabrication.heavy",
                                "facility.fabrication.assembly")))
                .filter(value -> value.inputs().get(0).supplyRoutes().stream()
                        .filter(route -> route.status() == RouteAdmissionStatus.ADMITTED)
                        .noneMatch(route -> route.supplyKey().systemId().equals(
                                value.candidate().capacity().systemId())))
                .filter(value -> value.inputs().get(0).supplyRoutes().stream()
                        .anyMatch(route -> route.status() == RouteAdmissionStatus.ADMITTED))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Generated seed has no accepted remote refining bootstrap: " + rootSeed));
        ProcessSelectionKey process = ProcessSelectionKey.from(selected.candidate());
        StationCandidate station = stationFor(candidates, process);
        double output = selected.candidate().throughput().inputLimitedOutputKgPerSecond() * 0.0001d;
        var reservation = Stage20IndustrialInputReservationPlan.reserve(
                resolved,
                new SelectionAuthority(
                        "selection.playable-generated-world.v1",
                        rootSeed,
                        List.of(new ProcessOutputRequest(process, output))));
        var bootstrapOwnership = Stage20BootstrapFreightOwnershipPlan.plan(resolved);
        var owner = bootstrapOwnership.factions().stream()
                .filter(value -> value.reserveFreighterCount() > 0)
                .max(Comparator.comparingInt(value -> value.reserveFreighterCount()))
                .orElseThrow(() -> new IllegalStateException(
                        "Generated seed has no reserve freight ownership: " + rootSeed));
        IndustrialFreightReport freight = Stage20IndustrialInputFreightOwnershipPlan.planCurrent(
                resolved,
                reservation,
                new ProcessOwnershipAuthority(
                        "process-owners.playable-generated-world.v1",
                        rootSeed,
                        List.of(new ProcessOwnerAssignment(process, owner.stableFactionId()))));

        var selectedSlot = station.facilitySlots().stream()
                .filter(value -> value.definition().id().equals(process.facilityDefinitionId()))
                .findFirst().orElseThrow();
        var heavySlot = station.facilitySlots().stream()
                .filter(value -> value.definition().id().equals("facility.fabrication.heavy"))
                .findFirst().orElseThrow();
        var assemblySlot = station.facilitySlots().stream()
                .filter(value -> value.definition().id().equals("facility.fabrication.assembly"))
                .findFirst().orElseThrow();
        StationKey stationKey = new StationKey(process.systemId(), process.stationPlacementId());
        InstalledFacilityState selectedState = fullState(
                station, selectedSlot.facilityOrdinal(), selectedSlot.definition());
        InstalledFacilityState heavyState = fullState(
                station, heavySlot.facilityOrdinal(), heavySlot.definition());
        InstalledFacilityState assemblyState = fullState(
                station, assemblySlot.facilityOrdinal(), assemblySlot.definition());
        YardDefinition yardDefinition = Stage18ShipyardCatalogLoader.loadDefault()
                .findYard("yard.orbital_escort_v1");
        double servicePower = selectedState.allocatedProcessPowerW()
                + heavyState.allocatedProcessPowerW()
                + assemblyState.allocatedProcessPowerW()
                + yardDefinition.ratedIntegrationPowerW();
        double serviceHeat = selectedState.availableHeatRejectionW()
                + heavyState.availableHeatRejectionW()
                + assemblyState.availableHeatRejectionW();
        double serviceLabor = selectedState.availableLaborUnits()
                + heavyState.availableLaborUnits()
                + assemblyState.availableLaborUnits()
                + yardDefinition.laborCapacity();
        double serviceMaintenance = selectedState.availableMaintenanceWorkRate()
                + heavyState.availableMaintenanceWorkRate()
                + assemblyState.availableMaintenanceWorkRate();
        OperatingStateAuthority operatingAuthority = new OperatingStateAuthority(
                "operating.playable-generated-world.v1",
                rootSeed,
                List.of(new StationServiceAllocation(
                        stationKey,
                        servicePower,
                        serviceHeat,
                        serviceLabor,
                        serviceMaintenance)),
                List.of(new FacilityStateAssignment(
                        new FacilitySlotKey(stationKey, selectedSlot.definition().id()),
                        owner.stableFactionId(),
                        selectedState)));
        OperatingReport operating = Stage20IndustrialFacilityOperatingPlan.plan(
                resolved, freight, operatingAuthority);
        InventoryReport inventory = Stage20IndustrialInitialInventoryPlan.plan(
                resolved, operating, inventoryAuthority(resolved, candidates, freight, operating));

        InstalledYardState yardState = new InstalledYardState(
                Stage20IndustrialShipyardInstallationPlan.canonicalYardInstanceId(
                        station.placement().id(), 0),
                yardDefinition.id(),
                1d,
                yardDefinition.ratedIntegrationPowerW(),
                yardDefinition.ratedEngineeringWorkRate(),
                yardDefinition.laborCapacity(),
                yardDefinition.automationCapacity(),
                true);
        ShipyardInstallationAuthority yardAuthority = new ShipyardInstallationAuthority(
                "yards.playable-generated-world.v1",
                rootSeed,
                List.of(new StationYardAuthority(
                        stationKey,
                        List.of(
                                new FacilityStateAssignment(
                                        new FacilitySlotKey(stationKey, heavySlot.definition().id()),
                                        owner.stableFactionId(),
                                        heavyState),
                                new FacilityStateAssignment(
                                        new FacilitySlotKey(stationKey, assemblySlot.definition().id()),
                                        owner.stableFactionId(),
                                        assemblyState)),
                        List.of(new InstalledYardAssignment(
                                new YardSlotKey(stationKey, 0),
                                owner.stableFactionId(),
                                yardState)))));
        var yards = Stage20IndustrialShipyardInstallationPlan.plan(resolved, inventory, yardAuthority);
        OperationalSpecializationReport specialization =
                Stage20OperationalIndustrialSpecializationPlan.derive(resolved, yards);
        if (!specialization.readyForRuntimeBridge()) {
            throw new IllegalStateException("Generated industrial specialization is not runtime-ready");
        }
        return new BootstrapFixture(resolved, specialization);
    }

    private static Stage20GeneratedCampaignPersistentState campaign(BootstrapFixture fixture) {
        SimulationSession session = SimulationSession.createDemo(fixture.resolved().rootSeed());
        Stage20MaterializationPersistentState physical = Stage20MaterializationPersistence.capture(
                session, Stage20MaterializationService.forSession(session));
        return Stage20GeneratedCampaignPersistence.capture(
                fixture.resolved(),
                Stage20SpecialLocationGenerator.generateCurrent(fixture.resolved()),
                fixture.specialization(),
                physical,
                Stage18IndustrialState.empty(0L),
                List.of(new Stage20DiscoveryKnowledgeState(
                        "faction.playable-generated-world.observer",
                        List.of())));
    }

    private static WorldSimulation ordinaryWorld(ResolvedProbeResult resolved) {
        var topology = resolved.generation().topology().requireAcceptedTopology();
        ArrayList<StarSystemSimulationState> systems = new ArrayList<>();
        for (var system : topology.systems()) {
            systems.add(new StarSystemSimulationState(
                    system.id(),
                    SimulationSession.createDemo(resolved.rootSeed() ^ system.id().value(), CONTENT).snapshot()));
        }
        StarSystemId active = topology.systems().get(0).id();
        ArrayList<FactionEconomicState> economies = new ArrayList<>();
        ArrayList<FactionStrategicState> strategies = new ArrayList<>();
        ArrayList<WorldFactionIdentityState> identities = new ArrayList<>();
        resolved.generation().placement().orElseThrow().assignments().stream()
                .map(value -> value.stableFactionId())
                .distinct()
                .sorted()
                .forEach(factionId -> {
                    FactionIdentityResolver resolver = FactionIdentityResolver.createDefault(CONTENT, identities);
                    WorldFactionIdentityState allocated = resolver.allocatePlayerCreated(
                            factionId, generatedFactionName(factionId));
                    identities.add(new WorldFactionIdentityState(
                            allocated.stableFactionId(),
                            allocated.runtimeFactionId(),
                            allocated.displayName(),
                            WorldFactionIdentityState.Origin.WORLD_BOOTSTRAP));
                    economies.add(new FactionEconomicState(factionId, 0L, 0L, 0L, 0L, 0L));
                    strategies.add(new FactionStrategicState(factionId, 0, List.of(), List.of()));
                });
        WorldState bootstrap = new WorldState(
                WorldState.CURRENT_VERSION, topology, systems, economies, strategies);
        WorldState generated = new WorldState(
                WorldState.CURRENT_VERSION,
                topology,
                systems,
                economies,
                strategies,
                bootstrap.nextConstructionProjectIdValue(),
                bootstrap.constructionProjects(),
                bootstrap.factionEconomicPressures(),
                bootstrap.nextFleetIdValue(),
                bootstrap.fleets(),
                bootstrap.fleetJumps(),
                identities);
        return WorldSimulation.restore(generated, CONTENT, active, 10, Math.max(1, systems.size()));
    }

    private static InitialInventoryAuthority inventoryAuthority(
            ResolvedProbeResult resolved,
            CandidateReport candidates,
            IndustrialFreightReport freight,
            OperatingReport operating) {
        TreeMap<StationKey, TreeMap<String, Double>> requiredByStation = new TreeMap<>();
        for (var reservation : freight.reservation().reservations()) {
            StationKey station = new StationKey(
                    reservation.process().systemId(),
                    reservation.process().stationPlacementId());
            double mass = reservation.reservedInputKgPerSecond() * reservation.route().travelTimeS();
            requiredByStation.computeIfAbsent(station, ignored -> new TreeMap<>())
                    .merge(reservation.inputCommodityId(), mass, Double::sum);
        }
        ArrayList<StationInventoryAssignment> assignments = new ArrayList<>();
        for (var station : operating.stations()) {
            StationCandidate candidate = stationFor(candidates, new ProcessSelectionKey(
                    station.station().systemId(),
                    station.station().stationPlacementId(),
                    operating.processes().get(0).process().facilityDefinitionId(),
                    operating.processes().get(0).process().processId(),
                    operating.processes().get(0).process().outputCommodityId()));
            assignments.add(new StationInventoryAssignment(
                    station.station(),
                    new StationStorageSnapshot(
                            station.station().stationPlacementId(),
                            candidate.archetype().storageCapacityByClassKg(),
                            requiredByStation.get(station.station()),
                            Map.of())));
        }
        return new InitialInventoryAuthority(
                "initial-inventory.playable-generated-world.v1",
                resolved.rootSeed(),
                assignments);
    }

    private static InstalledFacilityState fullState(
            StationCandidate station,
            int ordinal,
            FacilityDefinition definition) {
        return new InstalledFacilityState(
                Stage20IndustrialFacilityOperatingPlan.canonicalFacilityInstanceId(
                        station.placement().id(), ordinal),
                definition.id(),
                1d,
                definition.ratedProcessPowerW(),
                definition.ratedProcessPowerW() * definition.heatRejectionWPerProcessW(),
                definition.requiredLaborUnitsAtFullRate(),
                definition.maintenanceWorkRate(),
                Stage20IndustrialFacilityOperatingPlan.GENERATED_STATION_LOCATION_TAG,
                true);
    }

    private static StationCandidate stationFor(
            CandidateReport candidates,
            ProcessSelectionKey process) {
        return candidates.systems().stream()
                .filter(value -> value.systemId().equals(process.systemId()))
                .flatMap(value -> value.stations().stream())
                .filter(value -> value.placement().id().equals(process.stationPlacementId()))
                .findFirst()
                .orElseThrow();
    }

    private static String generatedFactionName(String factionId) {
        String suffix = factionId.substring(factionId.lastIndexOf('.') + 1).replace('_', ' ');
        return suffix.isBlank() ? factionId : Character.toUpperCase(suffix.charAt(0)) + suffix.substring(1);
    }

    private record BootstrapFixture(
            ResolvedProbeResult resolved,
            OperationalSpecializationReport specialization) {
    }

    /**
     * Complete result of one new generated-world bootstrap.
     *
     * @param rootSeed exact new-world seed
     * @param content installed content catalogue
     * @param specialization accepted operational industrial authority
     * @param runtime live Stage-20.5 generated-world runtime
     */
    public record GeneratedWorld(
            long rootSeed,
            ContentCatalog content,
            OperationalSpecializationReport specialization,
            LiveRuntime runtime) {
        /** Validates a complete bootstrap result. */
        public GeneratedWorld {
            Objects.requireNonNull(content, "content");
            Objects.requireNonNull(specialization, "specialization");
            Objects.requireNonNull(runtime, "runtime");
        }
    }
}
