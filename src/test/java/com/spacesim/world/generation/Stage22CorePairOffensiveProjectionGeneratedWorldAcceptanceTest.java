package com.spacesim.world.generation;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.content.Stage22CorePairExperimentProtocol.Permutation;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage22CorePairStrategicMobilityProjection;
import com.spacesim.economy.Stage19FleetReplenishmentService;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistenceCodec;
import com.spacesim.ship.ShipEngineeringRuntime;
import com.spacesim.ship.ShipEngineeringRuntime.OperatingCommand;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipInstanceRuntimeState;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.GeneratedWorldFtlTestSupport;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 B10 generated-world integration for exact strategic destroyer+tanker+support packages.
 *
 * <p>All three fitted ships enter the ordinary generated-world FleetJumpService before movement;
 * Stage-22 content is therefore interpreted by the production fitted-jump resolver rather than
 * installed only after arrival. After arrival the destroyer spends ordinary drive reaction mass and
 * the co-located tanker restores exactly that finite mass through Stage19FleetReplenishmentService.
 * No abstract projection token, free docking refill or faction-specific travel authority is used.</p>
 */
class Stage22CorePairOffensiveProjectionGeneratedWorldAcceptanceTest {
    private static final String PROPELLANT_FEED = "propellant_feed";
    private static final String REPLENISHMENT_TRANSFER = "replenishment_transfer";

    private static final CorePackage EMPIRE = new CorePackage(
            Stage22CorePairStrategicMobilityProjection.EMPIRE_DESTROYER_STRATEGIC_FIT,
            Stage22CorePairStrategicMobilityProjection.EMPIRE_TANKER_STRATEGIC_FIT,
            Stage22CorePairStrategicMobilityProjection.EMPIRE_SUPPORT_STRATEGIC_FIT);
    private static final CorePackage UNION = new CorePackage(
            Stage22CorePairStrategicMobilityProjection.UNION_DESTROYER_STRATEGIC_FIT,
            Stage22CorePairStrategicMobilityProjection.UNION_TANKER_STRATEGIC_FIT,
            Stage22CorePairStrategicMobilityProjection.UNION_SUPPORT_STRATEGIC_FIT);

    @Test
    void b10ExactStrategicPackagesMoveThroughOrdinaryGeneratedWorldFtlAndReplenishAfterArrival() {
        ScenarioResult first = run(Permutation.DEFAULT);
        ScenarioResult repeated = run(Permutation.DEFAULT);
        ScenarioResult mirrored = run(Permutation.MIRRORED);

        assertArrayEquals(first.checkpoint(), repeated.checkpoint(),
                "same B10 generated-world package assignment must persist byte-identically");
        assertEquals(first.summary(), repeated.summary());
        for (ScenarioResult result : List.of(first, mirrored)) {
            assertEquals(2, result.summary().packageCount());
            assertEquals(6, result.summary().strategicShipsArrived());
            assertEquals(2, result.summary().destroyersBurnedReactionMass());
            assertEquals(2, result.summary().successfulTankerTransfers());
            assertEquals(2, result.summary().runtimeFactionIdentitiesPreserved());
            assertTrue(result.summary().transferredMassKg() > 0d);
        }
    }

    private static ScenarioResult run(Permutation permutation) {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        ShipEngineeringCatalog catalog = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        List<FactionFleetGroup> groups = generatedMilitaryGroups(runtime);
        if (groups.size() != 2 || groups.stream().anyMatch(group -> group.fleets().size() != 3)) {
            throw new AssertionError("B10 requires the current two-slot, three-military-fleet generated bootstrap");
        }
        List<StarSystemId> targets = unoccupiedTargets(runtime, 2);
        List<CorePackage> packages = permutation == Permutation.DEFAULT
                ? List.of(EMPIRE, UNION)
                : List.of(UNION, EMPIRE);

        int arrived = 0;
        int burned = 0;
        int transfers = 0;
        int identities = 0;
        double transferredMassKg = 0d;
        Stage19FleetReplenishmentService replenishment = new Stage19FleetReplenishmentService(catalog);

        for (int index = 0; index < groups.size(); index++) {
            FactionFleetGroup group = groups.get(index);
            CorePackage corePackage = packages.get(index);
            StarSystemId target = targets.get(index);
            List<String> fitIds = List.of(
                    corePackage.destroyerFitId(),
                    corePackage.tankerFitId(),
                    corePackage.supportFitId());

            for (int shipIndex = 0; shipIndex < 3; shipIndex++) {
                FleetId fleetId = group.fleets().get(shipIndex);
                FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
                Entity entity = entity(runtime, placement);
                int factionBefore = entity.getComponent(FactionComponent.class).factionId;
                entity.add(strategicComponent(catalog, fitIds.get(shipIndex)));
                assertEquals(factionBefore, entity.getComponent(FactionComponent.class).factionId,
                        "installing exact B10 engineering must not rewrite generated faction identity");

                moveFleetByOrdinaryRoute(runtime, fleetId, target);
                FleetPlacementState arrivedPlacement = runtime.world().findFleet(fleetId).orElseThrow();
                assertEquals(target, arrivedPlacement.systemId());
                EngineeringComponent arrivedEngineering = entity(runtime, arrivedPlacement)
                        .getComponent(EngineeringComponent.class);
                assertEquals(fitIds.get(shipIndex()), exactFitId(catalog, arrivedEngineering.fit),
                        "ordinary FTL movement must preserve the exact strategic fit assignment");
                arrived++;
            }

            FleetPlacementState destroyerPlacement = runtime.world().findFleet(group.fleets().get(0)).orElseThrow();
            FleetPlacementState tankerPlacement = runtime.world().findFleet(group.fleets().get(1)).orElseThrow();
            Entity destroyerEntity = entity(runtime, destroyerPlacement);
            Entity tankerEntity = entity(runtime, tankerPlacement);
            EngineeringComponent destroyer = destroyerEntity.getComponent(EngineeringComponent.class);
            EngineeringComponent tanker = tankerEntity.getComponent(EngineeringComponent.class);
            double destroyerBeforeBurn = amount(destroyer.runtimeState.consumables(), PROPELLANT_FEED);
            double tankerBeforeTransfer = amount(tanker.runtimeState.consumables(), REPLENISHMENT_TRANSFER);

            ShipEngineeringRuntime engineeringRuntime = new ShipEngineeringRuntime(catalog);
            var burn = engineeringRuntime.advance(
                    destroyer.fit,
                    destroyer.runtimeState,
                    destroyer.instanceState.damage().moduleDamage(),
                    new OperatingCommand(Map.of("core_drive", 1d), Map.of(), Set.of()),
                    1d);
            assertTrue(burn.actualThrustN() > 0d && burn.massFlowKgPerS() > 0d,
                    "arrived strategic destroyer must burn physical reaction mass through ordinary engineering");
            double destroyerAfterBurn = amount(burn.state().consumables(), PROPELLANT_FEED);
            double consumedKg = destroyerBeforeBurn - destroyerAfterBurn;
            assertTrue(consumedKg > 0d);
            destroyer = new EngineeringComponent(destroyer.fit, burn.state(), destroyer.instanceState);
            destroyerEntity.add(destroyer);
            burned++;

            var transfer = replenishment.transferReactionMass(
                    tanker.fit,
                    tanker.runtimeState.consumables(),
                    destroyer.fit,
                    destroyer.runtimeState.consumables(),
                    consumedKg);
            assertEquals(Stage19FleetReplenishmentService.Status.TRANSFERRED, transfer.status());
            assertEquals(destroyerBeforeBurn, amount(transfer.targetConsumables(), PROPELLANT_FEED), 1e-6d);
            assertEquals(tankerBeforeTransfer - consumedKg,
                    amount(transfer.sourceConsumables(), REPLENISHMENT_TRANSFER), 1e-6d);

            tankerEntity.add(new EngineeringComponent(
                    tanker.fit,
                    withConsumables(tanker.runtimeState, transfer.sourceConsumables()),
                    tanker.instanceState));
            destroyerEntity.add(new EngineeringComponent(
                    destroyer.fit,
                    withConsumables(destroyer.runtimeState, transfer.targetConsumables()),
                    destroyer.instanceState));
            transferredMassKg += transfer.transferredMassKg();
            transfers++;

            int factionAfter = destroyerEntity.getComponent(FactionComponent.class).factionId;
            assertEquals(group.runtimeFactionId(), factionAfter);
            identities++;
        }

        byte[] checkpoint = Stage20GeneratedWorldRuntimePersistenceCodec.encode(runtime.captureState());
        assertArrayEquals(checkpoint,
                Stage20GeneratedWorldRuntimePersistenceCodec.encode(
                        Stage20GeneratedWorldRuntimePersistenceCodec.decode(checkpoint)),
                "B10 strategic movement/replenishment checkpoint must round-trip byte-identically");
        return new ScenarioResult(
                checkpoint,
                new Summary(groups.size(), arrived, burned, transfers, identities, transferredMassKg));
    }

    private static EngineeringComponent strategicComponent(ShipEngineeringCatalog catalog, String fitId) {
        DemonstratorFitDefinition definition = catalog.findDemonstratorFit(fitId);
        if (definition == null) {
            throw new AssertionError("Missing exact B10 strategic fit: " + fitId);
        }
        InstalledFit fit = InstalledFit.fromDemonstrator(definition);
        ConsumableState consumables = fullReactionMass(catalog, definition);
        ShipEngineeringRuntime engineeringRuntime = new ShipEngineeringRuntime(catalog);
        var operating = engineeringRuntime.initialize(fit, consumables, DamageState.pristine());
        return new EngineeringComponent(fit, operating, ShipInstanceRuntimeState.legacyNeutral());
    }

    private static ConsumableState fullReactionMass(
            ShipEngineeringCatalog catalog,
            DemonstratorFitDefinition fit) {
        ArrayList<ConsumableLoad> loads = new ArrayList<>();
        for (InstalledModuleDefinition assignment : fit.installedModules()) {
            ModuleDefinition module = catalog.findModule(assignment.moduleId());
            for (InterfaceDefinition definition : module.interfaces()) {
                if (definition.kind() != InterfaceKind.REACTION_MASS) continue;
                loads.add(new ConsumableLoad(
                        assignment.mountId(),
                        definition.id(),
                        InterfaceKind.REACTION_MASS,
                        definition.capacity(),
                        definition.capacity(),
                        0L));
            }
        }
        return new ConsumableState(0d, 0d, 0d, 0d, loads);
    }

    private static ShipEngineeringRuntime.RuntimeState withConsumables(
            ShipEngineeringRuntime.RuntimeState current,
            ConsumableState consumables) {
        return new ShipEngineeringRuntime.RuntimeState(
                consumables,
                current.sharedBusEnergyJ(),
                current.shipHeatStoredJ(),
                current.localHeatJByMount(),
                current.thrustLimitNByMount(),
                current.coolantBusCapacityW(),
                current.ftlCooldownSecondsByMount());
    }

    private static String exactFitId(ShipEngineeringCatalog catalog, InstalledFit fit) {
        return catalog.getDemonstratorFits().stream()
                .filter(definition -> definition.hullId().equals(fit.hullId()))
                .filter(definition -> definition.installedModules().equals(fit.installedModules()))
                .map(DemonstratorFitDefinition::id)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Generated B10 fleet lost exact strategic fit identity"));
    }

    private static double amount(ConsumableState state, String interfaceId) {
        return state.interfaceLoads().stream()
                .filter(load -> load.kind() == InterfaceKind.REACTION_MASS)
                .filter(load -> load.interfaceId().equals(interfaceId))
                .mapToDouble(ConsumableLoad::amount)
                .sum();
    }

    private static List<FactionFleetGroup> generatedMilitaryGroups(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime) {
        TreeMap<Integer, ArrayList<FleetId>> byFaction = new TreeMap<>();
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) continue;
            Entity entity = entity(runtime, placement);
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
            if (faction == null || engineering == null) continue;
            byFaction.computeIfAbsent(faction.factionId, ignored -> new ArrayList<>()).add(placement.id());
        }
        ArrayList<FactionFleetGroup> result = new ArrayList<>();
        for (Map.Entry<Integer, ArrayList<FleetId>> entry : byFaction.entrySet()) {
            entry.getValue().sort(Comparator.naturalOrder());
            result.add(new FactionFleetGroup(entry.getKey(), List.copyOf(entry.getValue())));
        }
        return List.copyOf(result);
    }

    private static List<StarSystemId> unoccupiedTargets(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            int count) {
        HashSet<StarSystemId> occupied = new HashSet<>();
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() == FleetLocationKind.IN_SYSTEM) {
                occupied.add(placement.systemId());
            }
        }
        List<StarSystemId> targets = runtime.world().getTopology().systems().stream()
                .map(value -> value.id())
                .filter(systemId -> !occupied.contains(systemId))
                .limit(count)
                .toList();
        if (targets.size() != count) {
            throw new AssertionError("B10 generated world lacks enough unoccupied projection objectives");
        }
        return targets;
    }

    private static Entity entity(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetPlacementState placement) {
        return runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().require(placement.localEntityId());
    }

    private static void moveFleetByOrdinaryRoute(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId,
            StarSystemId destination) {
        FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
        List<StarSystemId> route = route(runtime, placement.systemId(), destination);
        for (int index = 1; index < route.size(); index++) {
            GeneratedWorldFtlTestSupport.placeAtOutgoingEndpoint(runtime, fleetId, route.get(index));
            runtime.world().requestFleetJump(fleetId, route.get(index));
            GeneratedWorldFtlTestSupport.advanceOrdinaryJumpToCompletion(runtime, fleetId);
        }
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
                    ArrayList<StarSystemId> reverse = new ArrayList<>();
                    StarSystemId cursor = destination;
                    while (cursor != null) {
                        reverse.add(cursor);
                        cursor = previous.get(cursor);
                    }
                    java.util.Collections.reverse(reverse);
                    return List.copyOf(reverse);
                }
                queue.addLast(neighbor);
            }
        }
        throw new AssertionError("generated topology has no B10 strategic route");
    }

    private record CorePackage(String destroyerFitId, String tankerFitId, String supportFitId) { }

    private record FactionFleetGroup(int runtimeFactionId, List<FleetId> fleets) { }

    private record Summary(
            int packageCount,
            int strategicShipsArrived,
            int destroyersBurnedReactionMass,
            int successfulTankerTransfers,
            int runtimeFactionIdentitiesPreserved,
            double transferredMassKg) { }

    private record ScenarioResult(byte[] checkpoint, Summary summary) {
        private ScenarioResult {
            checkpoint = Arrays.copyOf(checkpoint, checkpoint.length);
        }
    }
}
