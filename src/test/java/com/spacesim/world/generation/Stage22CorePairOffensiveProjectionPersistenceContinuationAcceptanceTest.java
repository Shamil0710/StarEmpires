package com.spacesim.world.generation;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.content.Stage22CorePairEvidenceArchive;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 B01/B10 mid-operation continuation for exact strategic projection packages.
 *
 * <p>Two generated factions each contribute ordinary persistent destroyer/tanker FleetIds. Their
 * engineering is replaced by the exact Stage-22 strategic mobility fits before both ships travel one
 * real topology edge through the production fitted FTL resolver and ordinary FleetJumpService. Each
 * destroyer then spends real reaction mass through {@link ShipEngineeringRuntime}. The atomic
 * Stage-20.5 checkpoint is captured at that depleted pre-replenishment boundary.</p>
 *
 * <p>Direct and restored runtimes must then perform the same finite tanker transfer through
 * {@link Stage19FleetReplenishmentService}, consume the same source mass, restore exactly the spent
 * target mass and end in byte-identical generated-world checkpoints. No free refill, projection token
 * or faction-specific logistics modifier participates in continuation.</p>
 */
class Stage22CorePairOffensiveProjectionPersistenceContinuationAcceptanceTest {
    private static final String PROPELLANT_FEED = "propellant_feed";
    private static final String REPLENISHMENT_TRANSFER = "replenishment_transfer";

    private static final CorePair EMPIRE = new CorePair(
            Stage22CorePairStrategicMobilityProjection.EMPIRE_DESTROYER_STRATEGIC_FIT,
            Stage22CorePairStrategicMobilityProjection.EMPIRE_TANKER_STRATEGIC_FIT);
    private static final CorePair UNION = new CorePair(
            Stage22CorePairStrategicMobilityProjection.UNION_DESTROYER_STRATEGIC_FIT,
            Stage22CorePairStrategicMobilityProjection.UNION_TANKER_STRATEGIC_FIT);

    @Test
    void b10DepletedProjectionResumesWithSameFiniteTankerTransferAfterGeneratedWorldRestore() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        ShipEngineeringCatalog catalog = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        List<ProjectionLane> lanes = projectionLanes(runtime);
        prepareAndProject(runtime, lanes.get(0), EMPIRE, catalog);
        prepareAndProject(runtime, lanes.get(1), UNION, catalog);

        BurnEvidence empireBurn = burnDestroyer(runtime, lanes.get(0), catalog);
        BurnEvidence unionBurn = burnDestroyer(runtime, lanes.get(1), catalog);
        assertTrue(empireBurn.consumedKg() > 0d);
        assertTrue(unionBurn.consumedKg() > 0d);

        byte[] preTransfer = Stage20GeneratedWorldRuntimePersistenceCodec.encode(runtime.captureState());
        var decoded = Stage20GeneratedWorldRuntimePersistenceCodec.decode(preTransfer);
        assertArrayEquals(preTransfer, Stage20GeneratedWorldRuntimePersistenceCodec.encode(decoded),
                "depleted offensive-projection checkpoint must be byte-stable");
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime restored =
                Stage20GeneratedWorldRuntimeBridge.restore(decoded);

        ContinuationResult direct = continueTransfers(
                runtime, lanes, List.of(empireBurn, unionBurn), catalog);
        ContinuationResult reloaded = continueTransfers(
                restored, lanes, List.of(empireBurn, unionBurn), catalog);
        assertEquals(direct, reloaded,
                "finite B10 replenishment result must not change across mid-operation save/load");

        byte[] directFinal = Stage20GeneratedWorldRuntimePersistenceCodec.encode(runtime.captureState());
        byte[] restoredFinal = Stage20GeneratedWorldRuntimePersistenceCodec.encode(restored.captureState());
        assertArrayEquals(directFinal, restoredFinal,
                "direct and restored offensive projection must converge to the same authoritative checkpoint");

        LinkedHashMap<String, Object> archive = new LinkedHashMap<>();
        archive.put("preTransferCheckpointBytes", preTransfer.length);
        archive.put("finalCheckpointBytes", directFinal.length);
        archive.put("empireBurn", empireBurn);
        archive.put("unionBurn", unionBurn);
        archive.put("direct", direct);
        archive.put("restored", reloaded);
        archive.put("finalCheckpointEqual", true);
        Stage22CorePairEvidenceArchive.write(
                "B10-generated-world-mid-projection-save-continuation",
                archive,
                "Exact strategic destroyer/tanker fits travel through ordinary generated-world FTL, destroyers spend physical reaction mass, and the save boundary occurs before replenishment. The restored runtime consumes the same finite tanker mass through Stage-19 replenishment and converges to the direct byte-identical Stage-20.5 checkpoint. This closes the mid-operation persistence seam; broader long-horizon offensive tempo/overextension remains part of B10/B13 campaign evidence.");
    }

    private static void prepareAndProject(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            ProjectionLane lane,
            CorePair pair,
            ShipEngineeringCatalog catalog) {
        Entity destroyer = entity(runtime, lane.destroyerFleetId());
        Entity tanker = entity(runtime, lane.tankerFleetId());
        int destroyerFaction = destroyer.getComponent(FactionComponent.class).factionId;
        int tankerFaction = tanker.getComponent(FactionComponent.class).factionId;
        assertEquals(lane.factionId(), destroyerFaction);
        assertEquals(lane.factionId(), tankerFaction);

        destroyer.add(strategicComponent(catalog, pair.destroyerFitId()));
        tanker.add(strategicComponent(catalog, pair.tankerFitId()));
        moveOrdinaryHop(runtime, lane.destroyerFleetId(), lane.targetSystemId());
        moveOrdinaryHop(runtime, lane.tankerFleetId(), lane.targetSystemId());
        assertEquals(lane.targetSystemId(), runtime.world().findFleet(lane.destroyerFleetId()).orElseThrow().systemId());
        assertEquals(lane.targetSystemId(), runtime.world().findFleet(lane.tankerFleetId()).orElseThrow().systemId());
    }

    private static BurnEvidence burnDestroyer(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            ProjectionLane lane,
            ShipEngineeringCatalog catalog) {
        Entity entity = entity(runtime, lane.destroyerFleetId());
        EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
        double before = amount(engineering.runtimeState.consumables(), PROPELLANT_FEED);
        ShipEngineeringRuntime engineeringRuntime = new ShipEngineeringRuntime(catalog);
        var burn = engineeringRuntime.advance(
                engineering.fit,
                engineering.runtimeState,
                engineering.instanceState.damage().moduleDamage(),
                new OperatingCommand(Map.of("core_drive", 1d), Map.of(), Set.of()),
                1d);
        assertTrue(burn.actualThrustN() > 0d && burn.massFlowKgPerS() > 0d);
        double after = amount(burn.state().consumables(), PROPELLANT_FEED);
        entity.add(new EngineeringComponent(engineering.fit, burn.state(), engineering.instanceState));
        return new BurnEvidence(lane.destroyerFleetId().value(), before, after, before - after);
    }

    private static ContinuationResult continueTransfers(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            List<ProjectionLane> lanes,
            List<BurnEvidence> burns,
            ShipEngineeringCatalog catalog) {
        Stage19FleetReplenishmentService service = new Stage19FleetReplenishmentService(catalog);
        ArrayList<TransferEvidence> transfers = new ArrayList<>();
        double total = 0d;
        for (int index = 0; index < lanes.size(); index++) {
            ProjectionLane lane = lanes.get(index);
            BurnEvidence burn = burns.get(index);
            Entity destroyerEntity = entity(runtime, lane.destroyerFleetId());
            Entity tankerEntity = entity(runtime, lane.tankerFleetId());
            EngineeringComponent destroyer = destroyerEntity.getComponent(EngineeringComponent.class);
            EngineeringComponent tanker = tankerEntity.getComponent(EngineeringComponent.class);
            double tankerBefore = amount(tanker.runtimeState.consumables(), REPLENISHMENT_TRANSFER);

            var transfer = service.transferReactionMass(
                    tanker.fit,
                    tanker.runtimeState.consumables(),
                    destroyer.fit,
                    destroyer.runtimeState.consumables(),
                    burn.consumedKg());
            assertEquals(Stage19FleetReplenishmentService.Status.TRANSFERRED, transfer.status());
            assertEquals(burn.beforeKg(), amount(transfer.targetConsumables(), PROPELLANT_FEED), 1e-6d);
            assertEquals(tankerBefore - burn.consumedKg(),
                    amount(transfer.sourceConsumables(), REPLENISHMENT_TRANSFER), 1e-6d);

            tankerEntity.add(new EngineeringComponent(
                    tanker.fit,
                    withConsumables(tanker.runtimeState, transfer.sourceConsumables()),
                    tanker.instanceState));
            destroyerEntity.add(new EngineeringComponent(
                    destroyer.fit,
                    withConsumables(destroyer.runtimeState, transfer.targetConsumables()),
                    destroyer.instanceState));
            total += transfer.transferredMassKg();
            transfers.add(new TransferEvidence(
                    lane.factionId(),
                    lane.destroyerFleetId().value(),
                    lane.tankerFleetId().value(),
                    tankerBefore,
                    amount(transfer.sourceConsumables(), REPLENISHMENT_TRANSFER),
                    amount(transfer.targetConsumables(), PROPELLANT_FEED),
                    transfer.transferredMassKg()));
        }
        return new ContinuationResult(List.copyOf(transfers), total);
    }

    private static EngineeringComponent strategicComponent(
            ShipEngineeringCatalog catalog,
            String fitId) {
        DemonstratorFitDefinition definition = catalog.findDemonstratorFit(fitId);
        if (definition == null) {
            throw new AssertionError("missing exact strategic projection fit: " + fitId);
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
            if (module == null) {
                throw new AssertionError("strategic fit references missing module: " + assignment.moduleId());
            }
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

    private static double amount(ConsumableState state, String interfaceId) {
        return state.interfaceLoads().stream()
                .filter(load -> load.kind() == InterfaceKind.REACTION_MASS)
                .filter(load -> load.interfaceId().equals(interfaceId))
                .mapToDouble(ConsumableLoad::amount)
                .sum();
    }

    private static List<ProjectionLane> projectionLanes(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime) {
        TreeMap<Integer, ArrayList<FleetId>> byFaction = new TreeMap<>();
        TreeMap<Integer, StarSystemId> homeByFaction = new TreeMap<>();
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) continue;
            Entity entity = entity(runtime, placement.id());
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
            if (faction == null || engineering == null) continue;
            byFaction.computeIfAbsent(faction.factionId, ignored -> new ArrayList<>()).add(placement.id());
            homeByFaction.putIfAbsent(faction.factionId, placement.systemId());
        }

        ArrayList<ProjectionLane> result = new ArrayList<>();
        for (var entry : byFaction.entrySet()) {
            List<FleetId> fleets = entry.getValue().stream().sorted().toList();
            if (fleets.size() < 2) continue;
            StarSystemId home = homeByFaction.get(entry.getKey());
            StarSystemId target = runtime.world().getTopology().neighbors(home).stream().sorted().findFirst().orElse(null);
            if (target == null) continue;
            result.add(new ProjectionLane(entry.getKey(), fleets.get(0), fleets.get(1), home, target));
            if (result.size() == 2) break;
        }
        if (result.size() < 2) {
            throw new AssertionError("generated world lacks two projection lanes with destroyer/tanker FleetIds");
        }
        return List.copyOf(result);
    }

    private static void moveOrdinaryHop(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId,
            StarSystemId destination) {
        GeneratedWorldFtlTestSupport.placeAtOutgoingEndpoint(runtime, fleetId, destination);
        runtime.world().requestFleetJump(fleetId, destination);
        GeneratedWorldFtlTestSupport.advanceOrdinaryJumpToCompletion(runtime, fleetId);
    }

    private static Entity entity(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId) {
        FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
        if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
            throw new IllegalStateException("projection continuation FleetId must be local");
        }
        return runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().require(placement.localEntityId());
    }

    private record CorePair(String destroyerFitId, String tankerFitId) { }

    private record ProjectionLane(
            int factionId,
            FleetId destroyerFleetId,
            FleetId tankerFleetId,
            StarSystemId homeSystemId,
            StarSystemId targetSystemId) { }

    private record BurnEvidence(long destroyerFleetId, double beforeKg, double afterKg, double consumedKg) { }

    private record TransferEvidence(
            int factionId,
            long destroyerFleetId,
            long tankerFleetId,
            double tankerBeforeKg,
            double tankerAfterKg,
            double destroyerAfterKg,
            double transferredKg) { }

    private record ContinuationResult(List<TransferEvidence> transfers, double totalTransferredKg) { }
}
