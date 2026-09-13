package com.spacesim.world.generation;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.content.Stage18ExtractionCatalogLoader;
import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.content.Stage18ManufacturingCatalogLoader;
import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18RefiningCatalog;
import com.spacesim.content.Stage18RefiningCatalog.RefiningRecipeDefinition;
import com.spacesim.content.Stage18RefiningCatalogLoader;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage22CorePairExperimentProtocol.Permutation;
import com.spacesim.content.Stage22CorePairEvidenceArchive;
import com.spacesim.economy.Stage18ExtractionRuntime;
import com.spacesim.economy.Stage18FacilityRuntime;
import com.spacesim.economy.Stage18FacilityRuntime.FacilityCapabilitySnapshot;
import com.spacesim.economy.Stage18ManufacturingRuntime;
import com.spacesim.economy.Stage18RefiningRuntime;
import com.spacesim.economy.Stage18StationProductionBridge;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.persistence.Stage20FreightPersistentState.AssignmentKind;
import com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase;
import com.spacesim.persistence.Stage20FreightPersistentState.FreighterState;
import com.spacesim.persistence.Stage20FreightPersistentState.TransportOrderState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage20IndustrialEntityMaterializer.MaterializedIndustrialStation;
import com.spacesim.persistence.Stage20SourceOutpostMaterializer.MaterializedExtractionOutpost;
import com.spacesim.ship.ShipDamageRuntime;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipInstanceRuntimeState;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver;
import com.spacesim.ship.Stage22CorePairTacticalFactory;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.GeneratedWorldFtlTestSupport;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.Stage21ETacticalMaterializationService.CombatSide;
import com.spacesim.world.Stage21ETacticalMaterializationService.PhysicalCombatant;
import com.spacesim.world.Stage21ETacticalMaterializationService.TacticalMaterializationRequest;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StrategicOperationState;
import com.spacesim.world.StrategicOperationStateCodec;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M22.6 B08: physical interdiction outcome -> same freight order -> real Stage-18 production. */
class Stage22CorePairFreightProductionCausalAcceptanceTest {
    private static final long OPERATION_ID = 22_608_100L;
    private static final long TACTICAL_TICKS = 1_200L;
    private static final double HANDLING_SECONDS = 3_600d;
    private static final double CRITICAL_INTEGRITY = 1.0e-6d;
    private static final double PROCESS_WINDOW_SECONDS = 1.0e9d;
    private static final double EPSILON = 1.0e-7d;

    @Test
    void physicalInterdictorOutcomeControlsSameIndustrialInputAndSavedProductionContinuation() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime seedRuntime = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        byte[] baseline = Stage20GeneratedWorldRuntimePersistenceCodec.encode(seedRuntime.captureState());

        var vectors = new ArrayList<Map<String, Object>>();
        for (Permutation permutation : Permutation.values()) {
            EconomicResult admitted = run(restored(baseline), permutation, true);
            EconomicResult blocked = run(restored(baseline), permutation, false);
            assertFalse(admitted.interdictorAlive());
            assertEquals(admitted.loadedMassKg(), admitted.deliveredMassKg(), EPSILON);
            assertTrue(admitted.productionAccepted());
            assertTrue(admitted.productionConsumedKg() > 0d);
            assertTrue(admitted.orderId().startsWith("freight-order:industrial:"));
            assertTrue(blocked.interdictorAlive());
            assertFalse(blocked.productionAccepted());
            assertEquals(0d, blocked.deliveredMassKg(), EPSILON);
            assertEquals(0d, blocked.productionConsumedKg(), EPSILON);
            assertEquals(admitted.loadedMassKg(), blocked.loadedMassKg(), EPSILON);
            assertEquals(admitted.orderId(), blocked.orderId(),
                    "physical counterfactuals must keep the same industrial order and starting cargo");
            vectors.add(vector(permutation, admitted));
            vectors.add(vector(permutation, blocked));
        }
        Stage22CorePairEvidenceArchive.write("B08-generated-freight-production-counterfactuals", vectors,
                "Deterministic authority controls on one generated seed, with legacy owners and exact core fits. "
                        + "Critical/pristine interdictor conditions are declared inputs, not balanced matchups. "
                        + "World and operation sidecar use their existing separate codecs. "
                        + "Deployment occurs before core fit installation; no B10 or core-profile campaign pass.");
    }

    private static Stage20GeneratedWorldRuntimeBridge.LiveRuntime restored(byte[] baseline) {
        return Stage20GeneratedWorldRuntimeBridge.restore(
                Stage20GeneratedWorldRuntimePersistenceCodec.decode(baseline));
    }

    private static EconomicResult run(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            Permutation permutation,
            boolean criticalInterdictor) {
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        Stage18RefiningCatalog refining = Stage18RefiningCatalogLoader.loadDefault();
        ProductionFreight candidate = productionFreight(runtime, ontology, refining);
        FreighterState freighter = candidate.freighter();
        TransportOrderState orderBefore = candidate.order();
        MaterializedIndustrialStation station = candidate.station();
        RefiningRecipeDefinition recipe = candidate.recipe();
        FacilityCapabilitySnapshot facility = candidate.facility();
        Stage18StationProductionBridge production = productionBridge(ontology);

        double initialInputKg = station.storage().commodityMassKg(orderBefore.commodityId());
        if (initialInputKg > EPSILON) {
            assertTrue(production.refineAtStation(
                    recipe.id(), initialInputKg, station.storage(), facility, PROCESS_WINDOW_SECONDS).accepted());
        }
        assertEquals(0d, station.storage().commodityMassKg(orderBefore.commodityId()), EPSILON);
        assertEquals(Stage18RefiningRuntime.Status.INSUFFICIENT_INPUT,
                production.refineAtStation(
                        recipe.id(), 1d, station.storage(), facility, PROCESS_WINDOW_SECONDS).status());

        MaterializedExtractionOutpost outpost = candidate.outpost();
        var extraction = runtime.extract(outpost.site().siteId(), 10_000d, HANDLING_SECONDS);
        assertTrue(extraction.committed());
        double loadMassKg = Math.min(Math.min(extraction.outputMassStoredKg(), 1_000d), freighter.cargoCapacityKg());
        assertTrue(loadMassKg > 1d);
        assertTrue(runtime.transferOutpostToOrderSource(
                freighter.fleetId(), outpost.site().siteId(), loadMassKg, HANDLING_SECONDS).transferred());
        assertTrue(runtime.loadAtOrderSource(
                freighter.fleetId(), loadMassKg, 0d, HANDLING_SECONDS).transferred());
        runtime.freight().dispatchOutbound(freighter.fleetId(), 0d);
        FreighterState outbound = runtime.freight().findFreighter(freighter.fleetId()).orElseThrow();
        assertEquals(FreightPhase.OUTBOUND, outbound.phase());
        assertEquals(loadMassKg, outbound.cargoMassKg(), EPSILON);

        StarSystemId from = outbound.currentSystemId();
        StarSystemId to = orderBefore.orderedSystems().get(outbound.routeIndex() + 1);
        int trafficFaction = runtime.world().findFactionRuntimeId(outbound.stableFactionId()).orElseThrow();
        MilitaryFleet escort = closestMilitaryFleet(runtime, from, trafficFaction, true);
        MilitaryFleet interdictor = closestMilitaryFleet(runtime, from, trafficFaction, false);
        moveFleet(runtime, escort.fleetId(), from);
        moveFleet(runtime, interdictor.fleetId(), from);

        StrategicOperationState operations = interception(
                interdictor, from, to, runtime.world().getAuthoritativeWorldTick());
        Stage21EGeneratedWorldTrafficRuntime traffic = new Stage21EGeneratedWorldTrafficRuntime(runtime);
        var denied = new com.spacesim.world.Stage21EOperationTrafficPolicy(
                new com.spacesim.world.PhysicalWarfareOperationService(runtime.world()))
                .edgeAvailability(operations, from, to, trafficFaction);
        assertFalse(denied.allowsTraffic());

        Stage22CorePairTacticalFactory.Duel core = Stage22CorePairTacticalFactory.createDestroyerDuel(permutation);
        EngineeringComponent empire = engineeringFor(core, Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID);
        EngineeringComponent union = engineeringFor(core, Stage22CorePairTacticalFactory.UNION_ENTITY_ID);
        FleetPlacementState escortPlacement = runtime.world().findFleet(escort.fleetId()).orElseThrow();
        FleetPlacementState interdictorPlacement = runtime.world().findFleet(interdictor.fleetId()).orElseThrow();
        Entity escortEntity = entity(runtime, escortPlacement);
        Entity interdictorEntity = entity(runtime, interdictorPlacement);
        escortEntity.add(copy(permutation == Permutation.DEFAULT ? empire : union));
        interdictorEntity.add(copy(permutation == Permutation.DEFAULT ? union : empire));
        if (criticalInterdictor) {
            applyCriticalState(core, interdictorEntity.getComponent(EngineeringComponent.class));
        }
        LocalPhysicalKinematics anchor = runtime.arrival().materialization(from)
                .physicalState(escortPlacement.localEntityId()).orElseThrow();
        runtime.arrival().materialization(from).updatePhysicalState(
                interdictorPlacement.localEntityId(),
                LocalPhysicalKinematics.stationary(criticalInterdictor
                        ? anchor.position().translated(0d, 600d)
                        : anchor.position().translated(1_430d, 0d)));

        List<PhysicalCombatant> combatants = new ArrayList<>(List.of(
                new PhysicalCombatant(escort.fleetId(), CombatSide.CONTACT, escort.factionId(),
                        EntityStateMapper.capture(escortEntity)),
                new PhysicalCombatant(interdictor.fleetId(), CombatSide.OPERATION, interdictor.factionId(),
                        EntityStateMapper.capture(interdictorEntity))));
        combatants.sort(Comparator.comparing(PhysicalCombatant::fleetId));
        var resolver = new Stage19ExactTacticalEncounterResolver(
                core.content().engineering(), core.protection(), core.content().ammunition(), core.content().launchers());
        new Stage21EGeneratedWorldStage19Authority(runtime, resolver, TACTICAL_TICKS).materializeExact(
                new TacticalMaterializationRequest(
                        OPERATION_ID, from, runtime.world().getAuthoritativeWorldTick(), List.copyOf(combatants)));
        boolean interdictorAlive = runtime.world().findFleet(interdictor.fleetId()).isPresent();
        assertEquals(!criticalInterdictor, interdictorAlive,
                "the physical control must exercise the declared live/removed blockade branch");
        if (interdictorAlive) {
            byte[] checkpoint = Stage20GeneratedWorldRuntimePersistenceCodec.encode(runtime.captureState());
            byte[] operationCheckpoint = StrategicOperationStateCodec.encode(operations);
            var resumed = restored(checkpoint);
            var resumedOperations = StrategicOperationStateCodec.decode(operationCheckpoint);
            for (var checked : List.of(runtime, resumed)) {
                var checkedTraffic = new Stage21EGeneratedWorldTrafficRuntime(checked);
                assertThrows(IllegalStateException.class,
                        () -> checkedTraffic.requestNextRouteHop(resumedOperations, freighter.fleetId()));
                assertTrue(checked.world().findFleetJump(freighter.fleetId()).isEmpty());
                var held = checked.freight().findFreighter(freighter.fleetId()).orElseThrow();
                assertEquals(loadMassKg, held.cargoMassKg(), EPSILON);
                assertEquals(orderBefore.orderId(), held.activeOrderId());
                var checkedStation = checked.industry().industrial().station(station.stationId());
                assertEquals(Stage18RefiningRuntime.Status.INSUFFICIENT_INPUT,
                        production.refineAtStation(recipe.id(), 1d, checkedStation.storage(),
                                facility, PROCESS_WINDOW_SECONDS).status());
                assertArrayEquals(checkpoint,
                        Stage20GeneratedWorldRuntimePersistenceCodec.encode(checked.captureState()),
                        "denied delivery and production must preserve the complete state before and after load");
            }
            return new EconomicResult(true, loadMassKg, 0d, false, 0d, orderBefore.orderId());
        }

        while (runtime.freight().findFreighter(freighter.fleetId()).orElseThrow().phase() == FreightPhase.OUTBOUND) {
            FreighterState beforeHop = runtime.freight().findFreighter(freighter.fleetId()).orElseThrow();
            traffic.requestNextRouteHop(operations, freighter.fleetId());
            assertTrue(runtime.world().findFleetJump(freighter.fleetId()).isPresent());
            awaitJump(runtime, freighter.fleetId());
            FreighterState afterHop = runtime.freight().findFreighter(freighter.fleetId()).orElseThrow();
            assertTrue(afterHop.routeIndex() > beforeHop.routeIndex()
                    || afterHop.phase() == FreightPhase.AT_DESTINATION);
        }
        FreighterState atDestination = runtime.freight().findFreighter(freighter.fleetId()).orElseThrow();
        assertEquals(FreightPhase.AT_DESTINATION, atDestination.phase());
        assertEquals(orderBefore.destinationEndpointId(), station.stationId());
        assertEquals(0d, station.storage().commodityMassKg(orderBefore.commodityId()), EPSILON);

        byte[] deliveredCheckpoint = Stage20GeneratedWorldRuntimePersistenceCodec.encode(runtime.captureState());
        var resumed = restored(deliveredCheckpoint);
        var resumedOperations = StrategicOperationStateCodec.decode(StrategicOperationStateCodec.encode(operations));
        EconomicResult uninterrupted = finishProduction(runtime, operations, candidate, loadMassKg, production);
        EconomicResult continued = finishProduction(resumed, resumedOperations, candidate, loadMassKg, production);
        assertEquals(uninterrupted, continued);
        assertArrayEquals(Stage20GeneratedWorldRuntimePersistenceCodec.encode(runtime.captureState()),
                Stage20GeneratedWorldRuntimePersistenceCodec.encode(resumed.captureState()),
                "actual unloading and refining after world reconstruction must match uninterrupted production");
        return uninterrupted;
    }

    private static EconomicResult finishProduction(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime, StrategicOperationState operations,
            ProductionFreight candidate, double loadMassKg, Stage18StationProductionBridge production) {
        var station = runtime.industry().industrial().station(candidate.station().stationId());
        assertTrue(station.facilityCapabilities().contains(candidate.facility()),
                "restored production must have the same physical facility capability");
        var traffic = new Stage21EGeneratedWorldTrafficRuntime(runtime);
        var unload = traffic.unloadAtOrderDestination(
                operations, candidate.freighter().fleetId(), loadMassKg, HANDLING_SECONDS);
        assertTrue(unload.transferred());
        assertEquals(loadMassKg, station.storage().commodityMassKg(candidate.order().commodityId()), EPSILON);
        var orderAfter = runtime.freight().findOrder(candidate.order().orderId()).orElseThrow();
        double deliveredKg = orderAfter.deliveredMassKg() - candidate.order().deliveredMassKg();
        assertEquals(loadMassKg, deliveredKg, EPSILON);
        double batchKg = Math.min(10d, loadMassKg);
        var produced = production.refineAtStation(candidate.recipe().id(), batchKg, station.storage(),
                candidate.facility(), PROCESS_WINDOW_SECONDS);
        assertTrue(produced.accepted());
        double consumedKg = produced.consumedInputMassByCommodityKg().values().stream()
                .mapToDouble(Double::doubleValue).sum();
        assertEquals(batchKg, consumedKg, EPSILON);
        return new EconomicResult(false, loadMassKg, deliveredKg, produced.accepted(), consumedKg, orderAfter.orderId());
    }

    private static Map<String, Object> vector(Permutation permutation, EconomicResult result) {
        return Map.of("seed", Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED,
                "permutation", permutation, "orderId", result.orderId(),
                "interdictorAlive", result.interdictorAlive(), "loadedMassKg", result.loadedMassKg(),
                "deliveredMassKg", result.deliveredMassKg(), "productionAccepted", result.productionAccepted(),
                "productionConsumedKg", result.productionConsumedKg());
    }

    private static ProductionFreight productionFreight(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            Stage18ResourceOntologyCatalog ontology,
            Stage18RefiningCatalog refining) {
        ArrayList<ProductionFreight> candidates = new ArrayList<>();
        for (FreighterState freighter : runtime.freight().capture().freighters()) {
            if (freighter.phase() != FreightPhase.AT_SOURCE || freighter.activeOrderId().isBlank()) continue;
            TransportOrderState order = runtime.freight().findOrder(freighter.activeOrderId()).orElse(null);
            if (order == null || order.assignmentKind() != AssignmentKind.INDUSTRIAL_INPUT
                    || order.orderedSystems().size() < 2) continue;
            MaterializedExtractionOutpost outpost = matchingOutpost(runtime, order);
            if (outpost == null) continue;
            MaterializedIndustrialStation station;
            try {
                station = runtime.industry().industrial().station(order.destinationEndpointId());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            for (RefiningRecipeDefinition recipe : refining.getRecipes()) {
                String processMarker = ":" + recipe.id() + ":" + order.commodityId() + ":";
                if (recipe.inputs().size() != 1
                        || !recipe.inputs().get(0).commodityId().equals(order.commodityId())
                        || !order.sourceProvenanceId().contains(processMarker)) continue;
                var input = ontology.findCommodity(order.commodityId());
                var output = ontology.findCommodity(recipe.outputCommodityId());
                if (input == null || output == null
                        || !input.storageClassId().equals(output.storageClassId())) continue;
                FacilityCapabilitySnapshot facility = station.facilityCapabilities().stream()
                        .filter(value -> value.status() == Stage18FacilityRuntime.Status.ACTIVE)
                        .filter(value -> value.capabilityTags().containsAll(recipe.requiredCapabilityTags()))
                        .filter(value -> value.effectiveProcessPowerW() > 0d
                                && value.effectiveEngineeringWorkRate() > 0d
                                && value.effectiveMaintenanceWorkRate() > 0d)
                        .findFirst().orElse(null);
                if (facility != null) {
                    candidates.add(new ProductionFreight(freighter, order, station, outpost, recipe, facility));
                }
            }
        }
        return candidates.stream()
                .min(Comparator.<ProductionFreight>comparingInt(value -> value.order().orderedSystems().size())
                        .thenComparingDouble(value -> routeDistance(runtime, value.order().orderedSystems()))
                        .thenComparing(value -> value.order().orderId()))
                .orElseThrow(() -> new AssertionError(
                        "generated world lacks runnable INDUSTRIAL_INPUT refining freight"));
    }

    private static MaterializedExtractionOutpost matchingOutpost(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            TransportOrderState order) {
        StarSystemId source = order.orderedSystems().get(0);
        return runtime.industry().sourceOutposts().outposts().stream()
                .filter(value -> value.site().systemId().equals(source))
                .filter(value -> value.source().sourceState().outputCommodityId().equals(order.commodityId()))
                .findFirst().orElse(null);
    }

    private static Stage18StationProductionBridge productionBridge(Stage18ResourceOntologyCatalog ontology) {
        Stage18ManufacturingProductRegistry products = Stage18ManufacturingProductRegistry.loadDefault();
        Stage18FacilityRuntime facilities = new Stage18FacilityRuntime(Stage18FacilityCatalogLoader.loadDefault());
        return new Stage18StationProductionBridge(
                ontology, products, facilities,
                new Stage18ExtractionRuntime(ontology, Stage18ExtractionCatalogLoader.loadDefault()),
                new Stage18RefiningRuntime(ontology, Stage18RefiningCatalogLoader.loadDefault()),
                new Stage18ManufacturingRuntime(
                        ontology, Stage18ManufacturingCatalogLoader.loadDefault(), products));
    }

    private static void applyCriticalState(
            Stage22CorePairTacticalFactory.Duel core,
            EngineeringComponent engineering) {
        var hull = core.content().engineering().findHull(engineering.fit.hullId());
        var layout = core.protection().findHullDamageLayout(hull.id());
        if (layout == null) throw new AssertionError("exact B08 hull lacks protection layout");
        TreeMap<String, Double> compartments = new TreeMap<>();
        hull.compartments().forEach(value -> compartments.put(value.id(), 0d));
        String live = hull.compartments().stream().filter(value -> value.id().equals("engineering"))
                .findFirst().orElseGet(() -> hull.compartments().stream().findFirst().orElseThrow()).id();
        compartments.put(live, CRITICAL_INTEGRITY);
        TreeMap<String, Double> modules = new TreeMap<>();
        engineering.fit.installedModules().forEach(value -> modules.put(value.mountId(), 0d));
        ShipDamageRuntime.Snapshot damage = new ShipDamageRuntime.Snapshot(compartments, new DamageState(modules));
        assertFalse(ShipDamageRuntime.isFullyDestroyed(hull, engineering.fit, layout, damage));
        ShipInstanceRuntimeState previous = engineering.instanceState;
        engineering.setInstanceState(new ShipInstanceRuntimeState(
                damage, Map.of(), previous.maintenance(), previous.weaponLoadout(), previous.weaponMountRuntime()));
    }

    private static EngineeringComponent engineeringFor(
            Stage22CorePairTacticalFactory.Duel duel,
            long entityId) {
        return duel.weapons().battleState().combatants().stream()
                .filter(value -> value.spec().entityId() == entityId)
                .findFirst().orElseThrow().engineering();
    }

    private static EngineeringComponent copy(EngineeringComponent source) {
        return new EngineeringComponent(source.fit, source.runtimeState, source.instanceState);
    }

    private static Entity entity(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetPlacementState placement) {
        return runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().require(placement.localEntityId());
    }

    private static StrategicOperationState interception(
            MilitaryFleet actor,
            StarSystemId from,
            StarSystemId to,
            long tick) {
        return new StrategicOperationState(OPERATION_ID + 1L, List.of(new OperationState(
                OPERATION_ID, OperationType.INTERCEPTION, 1L, 1L, actor.factionId(), List.of(actor.fleetId()),
                from, to, "system:" + to.value(), RulesOfEngagement.DECLARED_HOSTILES,
                new SupplyPolicy(0, 0, 100L), new WithdrawalPolicy(from, 0, true, true),
                OperationStatus.ACTIVE, tick, tick, -1L, null, null)));
    }

    private static MilitaryFleet closestMilitaryFleet(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            StarSystemId target,
            int trafficFaction,
            boolean sameFaction) {
        return militaryFleets(runtime).stream()
                .filter(value -> (value.factionId() == trafficFaction) == sameFaction)
                .min(Comparator.<MilitaryFleet>comparingInt(value -> route(runtime, value.systemId(), target).size())
                        .thenComparingDouble(value -> routeDistance(
                                runtime, route(runtime, value.systemId(), target)))
                        .thenComparing(MilitaryFleet::fleetId))
                .orElseThrow(() -> new AssertionError("generated world lacks required B08 military side"));
    }

    private static List<MilitaryFleet> militaryFleets(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime) {
        ArrayList<MilitaryFleet> result = new ArrayList<>();
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) continue;
            Entity entity = entity(runtime, placement);
            EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            if (engineering != null && faction != null) {
                result.add(new MilitaryFleet(placement.id(), faction.factionId, placement.systemId()));
            }
        }
        result.sort(Comparator.comparing(MilitaryFleet::fleetId));
        return List.copyOf(result);
    }

    private static void moveFleet(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId,
            StarSystemId destination) {
        List<StarSystemId> route = route(
                runtime, runtime.world().findFleet(fleetId).orElseThrow().systemId(), destination);
        for (int i = 1; i < route.size(); i++) {
            GeneratedWorldFtlTestSupport.placeAtOutgoingEndpoint(runtime, fleetId, route.get(i));
            runtime.world().requestFleetJump(fleetId, route.get(i));
            awaitJump(runtime, fleetId);
            // The next placeAtOutgoingEndpoint call owns fitted cooldown/thermal/charge recovery.
        }
    }

    private static void awaitJump(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId) {
        GeneratedWorldFtlTestSupport.advanceOrdinaryJumpToCompletion(runtime, fleetId);
    }

    private static List<StarSystemId> route(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            StarSystemId origin,
            StarSystemId destination) {
        if (origin.equals(destination)) return List.of(origin);
        ArrayDeque<StarSystemId> queue = new ArrayDeque<>();
        Map<StarSystemId, StarSystemId> previous = new HashMap<>();
        queue.add(origin);
        previous.put(origin, null);
        while (!queue.isEmpty()) {
            StarSystemId current = queue.removeFirst();
            for (StarSystemId neighbor : runtime.world().getTopology().neighbors(current)) {
                if (previous.containsKey(neighbor)) continue;
                previous.put(neighbor, current);
                if (neighbor.equals(destination)) {
                    ArrayList<StarSystemId> reversed = new ArrayList<>();
                    for (StarSystemId cursor = destination; cursor != null; cursor = previous.get(cursor)) {
                        reversed.add(cursor);
                    }
                    java.util.Collections.reverse(reversed);
                    return List.copyOf(reversed);
                }
                queue.addLast(neighbor);
            }
        }
        throw new AssertionError("generated topology has no B08 route");
    }

    private static double routeDistance(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            List<StarSystemId> route) {
        double distance = 0d;
        for (int i = 1; i < route.size(); i++) {
            var from = runtime.world().getTopology().findSystem(route.get(i - 1)).orElseThrow();
            var to = runtime.world().getTopology().findSystem(route.get(i)).orElseThrow();
            distance += StrictMath.hypot(to.x() - from.x(), to.y() - from.y());
        }
        return distance;
    }

    private record ProductionFreight(
            FreighterState freighter,
            TransportOrderState order,
            MaterializedIndustrialStation station,
            MaterializedExtractionOutpost outpost,
            RefiningRecipeDefinition recipe,
            FacilityCapabilitySnapshot facility) { }

    private record MilitaryFleet(FleetId fleetId, int factionId, StarSystemId systemId) { }

    private record EconomicResult(
            boolean interdictorAlive,
            double loadedMassKg,
            double deliveredMassKg,
            boolean productionAccepted,
            double productionConsumedKg,
            String orderId) { }
}
