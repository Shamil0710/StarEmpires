package com.spacesim.content;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.content.Stage22CorePairExperimentProtocol.Permutation;
import com.spacesim.economy.Stage18ShipConsumableService;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.economy.Stage19WarfareSupplyService;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistenceCodec;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipyardEngineeringService;
import com.spacesim.ship.Stage22CorePairTacticalFactory;
import com.spacesim.world.DestructionPolicy;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.FactionIdentityResolver;
import com.spacesim.world.FleetForceRegistry;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetOperationalAvailability;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.FleetReadinessEvaluator;
import com.spacesim.world.FleetReadinessState;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.SettlementRecoveryService;
import com.spacesim.world.SettlementRecoveryState;
import com.spacesim.world.SettlementRecoveryStateCodec;
import com.spacesim.world.Stage21EPhysicalConsequenceService;
import com.spacesim.world.Stage21GPhysicalRecoveryService;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StrategicOperationState.ContactState;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import com.spacesim.world.generation.Stage21GGeneratedWorldPhysicalRecoveryAuthority;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 B01/B13/B14 generated-world persistence for a paid Stage-21G replacement.
 *
 * <p>The test begins with an ordinary generated military FleetId, installs the exact matching core
 * package, records only an actual ordinary destruction through Stage-21E consequence reconciliation,
 * and creates a Stage-21G replacement demand. The existing Stage-18/17.5 yard consumes finite stock
 * and work. {@link Stage21GGeneratedWorldPhysicalRecoveryAuthority} then adds only the missing exact
 * Stage-20 berth sidecar to the fresh ordinary replacement FleetId.</p>
 *
 * <p>The acceptance boundary is deliberately stronger than WorldState persistence: the complete
 * Stage-20.5 generated runtime must capture, encode, decode and restore after commissioning. The
 * restored replacement must retain the same fresh FleetId, exact core fit and hierarchical/double
 * berth. Stage-21G recovery metadata is independently byte-stable and still references that exact
 * commissioned FleetId. Both core factions traverse the same seam.</p>
 */
class Stage22CorePairGeneratedWorldReplacementPersistenceAcceptanceTest {
    private static final long OPERATION_ID = 22_613_500L;
    private static final int CREW_AVAILABLE = 100_000;

    @Test
    void b13PaidReplacementRemainsCapturableByFullGeneratedWorldRuntimeForBothCoreFactions() {
        ScenarioResult empire = run(true);
        ScenarioResult union = run(false);

        assertEquals(Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID, empire.factionId());
        assertEquals(Stage22CorePairBalanceEvidence.UNION_FACTION_ID, union.factionId());
        for (ScenarioResult result : List.of(empire, union)) {
            assertTrue(result.fullRuntimeByteStable());
            assertTrue(result.recoveryByteStable());
            assertTrue(result.exactBerthRestored());
            assertTrue(result.exactFitRestored());
            assertTrue(result.finiteStockConsumed());
            assertNotEquals(result.lostFleetId(), result.replacementFleetId());
        }

        LinkedHashMap<String, Object> archive = new LinkedHashMap<>();
        archive.put("empire", empire);
        archive.put("union", union);
        Stage22CorePairEvidenceArchive.write(
                "B13-B14-generated-world-paid-replacement-full-checkpoint",
                archive,
                "A real ordinary FleetId loss becomes a Stage-21E consequence and Stage-21G replacement demand. Existing finite Stage-18 yard stock/work commissions a fresh FleetId, and the generated-world recovery adapter registers only its exact Stage-20 stationary berth. Full Stage-20.5 capture/restore and Stage-21G recovery-state bytes are deterministic for both exact core packages. This closes the missing commissioned-replacement physical-sidecar/persistence seam; multi-loss sustained backlog cadence and the continuous same-shock B14 repair/replacement curve remain open.");
    }

    private static ScenarioResult run(boolean empire) {
        String targetFaction = empire
                ? Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID
                : Stage22CorePairBalanceEvidence.UNION_FACTION_ID;
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        FactionIdentityResolver identities = FactionIdentityResolver.createDefault(
                ContentCatalogLoader.loadDefault(), runtime.world().snapshot().factionIdentities());

        List<MilitaryFleet> military = militaryFleets(runtime, identities);
        MilitaryFleet target = military.stream()
                .filter(row -> row.stableFactionId().equals(targetFaction))
                .findFirst()
                .orElseThrow(() -> new AssertionError("generated world lacks target core faction military fleet"));
        MilitaryFleet survivor = military.stream()
                .filter(row -> !row.stableFactionId().equals(targetFaction))
                .findFirst()
                .orElseThrow(() -> new AssertionError("generated world lacks opposing military fleet"));

        var duel = Stage22CorePairTacticalFactory.createDestroyerDuel(Permutation.DEFAULT);
        EngineeringComponent empireCore = engineering(duel, Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID);
        EngineeringComponent unionCore = engineering(duel, Stage22CorePairTacticalFactory.UNION_ENTITY_ID);
        EngineeringComponent targetCore = empire ? empireCore : unionCore;
        EngineeringComponent survivorCore = empire ? unionCore : empireCore;
        entity(runtime, target.placement()).add(copy(targetCore));
        entity(runtime, survivor.placement()).add(copy(survivorCore));
        InstalledFit lostFit = targetCore.fit;

        FleetReadinessEvaluator evaluator = new FleetReadinessEvaluator(duel.content().engineering());
        Map<FleetId, FleetOperationalAvailability> beforeAvailability = Map.of(
                target.fleetId(), new FleetOperationalAvailability(CREW_AVAILABLE, FleetReadinessState.FULL),
                survivor.fleetId(), new FleetOperationalAvailability(CREW_AVAILABLE, FleetReadinessState.FULL));
        FleetForceRegistry before = FleetForceRegistry.reconstruct(
                runtime.world().snapshot(), evaluator, beforeAvailability);
        long tick = runtime.world().getAuthoritativeWorldTick();
        OperationState operation = contactedOperation(survivor, target, tick);

        var targetLocalId = target.placement().localEntityId();
        runtime.world().destroyEntity(
                target.placement().systemId(), targetLocalId, DestructionPolicy.destroyAll());
        runtime.arrival().materialization(target.placement().systemId())
                .releasePhysicalStateForWorldTransfer(targetLocalId);
        assertFalse(runtime.world().findFleet(target.fleetId()).isPresent(),
                "B13 physical loss must remove the ordinary FleetId before recovery planning");

        Map<FleetId, FleetOperationalAvailability> afterAvailability = Map.of(
                survivor.fleetId(), new FleetOperationalAvailability(CREW_AVAILABLE, FleetReadinessState.FULL));
        FleetForceRegistry after = FleetForceRegistry.reconstruct(
                runtime.world().snapshot(), evaluator, afterAvailability);
        var report = new Stage21EPhysicalConsequenceService().reconcile(operation, before, after);
        assertEquals(List.of(target.fleetId()), report.losses());

        var settlement = new SettlementRecoveryState.Settlement(
                1L,
                "proposal.m22_6.generated_replacement." + targetFaction,
                "war.m22_6.generated_replacement",
                survivor.stableFactionId(),
                targetFaction,
                tick,
                tick,
                SettlementRecoveryState.SettlementStatus.PENDING,
                false);
        SettlementRecoveryService recovery = new SettlementRecoveryService(new SettlementRecoveryState(
                SettlementRecoveryState.CURRENT_VERSION,
                tick,
                2L,
                1L,
                List.of(settlement),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
        recovery.recordPhysicalLosses(1L, OPERATION_ID, report, before, identities, tick);
        var demand = recovery.requestReplacement(1L, target.fleetId(), lostFit, tick);
        recovery.finalizeRecoveryPlan(1L, tick);

        Stage22CorePairRecoveryProbe.PreparedYard setup = Stage22CorePairRecoveryProbe.prepareYard(empire);
        Stage18StationStorage stock = replacementStock(setup, lostFit);
        Map<String, Double> startingMaterials = stock.snapshotCommodityMassByIdKg();
        Map<String, Integer> startingModules = stock.snapshotProductCountById();
        ShipyardEngineeringService planner = new ShipyardEngineeringService(setup.engineering(), setup.industrial());
        var buildPlan = planner.planBuild(lostFit, setup.yard().plannerCapability());
        double buildSeconds = buildPlan.requirements().totalWorkSeconds() / setup.yard().plannerCapability().workRate();

        LocalPhysicalKinematics berth = LocalPhysicalKinematics.stationary(
                runtime.infrastructure().endpoints().stream()
                        .filter(endpoint -> endpoint.systemId().equals(target.placement().systemId()))
                        .map(Stage20GeneratedWorldRuntimeBridge.RuntimeEndpoint::position)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("replacement system lacks generated infrastructure berth")));
        Stage21GGeneratedWorldPhysicalRecoveryAuthority generatedRecovery =
                new Stage21GGeneratedWorldPhysicalRecoveryAuthority(runtime, physicalRecovery(setup));
        long allocatorBefore = runtime.world().snapshot().nextFleetIdValue();
        var built = generatedRecovery.buildReplacement(
                recovery,
                demand.id(),
                identities,
                target.placement().systemId(),
                "M22.6 generated recovery replacement",
                berth,
                lostFit,
                stock,
                setup.yard(),
                setup.yard().openInterval(buildSeconds + 1d),
                tick);

        assertTrue(built.settlement().settled());
        assertEquals(SettlementRecoveryState.ReplacementStatus.COMMISSIONED,
                recovery.snapshot().requireReplacementDemand(demand.id()).status());
        assertEquals(allocatorBefore + 1L, runtime.world().snapshot().nextFleetIdValue());
        FleetPlacementState replacement = runtime.world().findFleet(built.commissionedFleetId()).orElseThrow();
        assertEquals(target.placement().systemId(), replacement.systemId());
        assertEquals(berth, runtime.arrival().materialization(replacement.systemId())
                .physicalState(replacement.localEntityId()).orElseThrow());

        boolean finiteStockConsumed = stock.snapshotCommodityMassByIdKg().isEmpty()
                && stock.snapshotProductCountById().isEmpty()
                && startingMaterials.equals(built.settlement().consumedCommodityMassKg())
                && startingModules.equals(built.settlement().consumedProductCount());

        byte[] recoveryBytes = SettlementRecoveryStateCodec.encode(recovery.snapshot());
        SettlementRecoveryState restoredRecovery = SettlementRecoveryStateCodec.decode(recoveryBytes);
        boolean recoveryByteStable = java.util.Arrays.equals(
                recoveryBytes, SettlementRecoveryStateCodec.encode(restoredRecovery))
                && built.commissionedFleetId().equals(
                        restoredRecovery.requireReplacementDemand(demand.id()).commissionedFleetId());

        byte[] runtimeBytes = Stage20GeneratedWorldRuntimePersistenceCodec.encode(runtime.captureState());
        var decoded = Stage20GeneratedWorldRuntimePersistenceCodec.decode(runtimeBytes);
        boolean fullRuntimeByteStable = java.util.Arrays.equals(
                runtimeBytes, Stage20GeneratedWorldRuntimePersistenceCodec.encode(decoded));
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime restored = Stage20GeneratedWorldRuntimeBridge.restore(decoded);
        FleetPlacementState restoredPlacement = restored.world().findFleet(built.commissionedFleetId()).orElseThrow();
        LocalPhysicalKinematics restoredBerth = restored.arrival().materialization(restoredPlacement.systemId())
                .physicalState(restoredPlacement.localEntityId()).orElseThrow();
        EngineeringComponent restoredEngineering = entity(restored, restoredPlacement)
                .getComponent(EngineeringComponent.class);
        boolean exactBerthRestored = berth.equals(restoredBerth);
        boolean exactFitRestored = restoredEngineering != null && lostFit.equals(restoredEngineering.fit);

        assertArrayEquals(runtimeBytes, Stage20GeneratedWorldRuntimePersistenceCodec.encode(decoded));
        assertArrayEquals(recoveryBytes, SettlementRecoveryStateCodec.encode(restoredRecovery));
        assertTrue(exactBerthRestored);
        assertTrue(exactFitRestored);
        assertTrue(finiteStockConsumed);

        return new ScenarioResult(
                targetFaction,
                target.fleetId().value(),
                built.commissionedFleetId().value(),
                fullRuntimeByteStable,
                recoveryByteStable,
                exactBerthRestored,
                exactFitRestored,
                finiteStockConsumed,
                buildSeconds);
    }

    private static OperationState contactedOperation(MilitaryFleet survivor, MilitaryFleet target, long tick) {
        ContactState contact = new ContactState(
                target.fleetId(),
                target.placement().systemId(),
                ObservationChannel.LOCAL_SENSOR_REPORT,
                "b13:generated-loss:" + target.fleetId().value(),
                tick,
                tick + 100L);
        return new OperationState(
                OPERATION_ID,
                OperationType.INTERCEPTION,
                OPERATION_ID,
                OPERATION_ID,
                survivor.runtimeFactionId(),
                List.of(survivor.fleetId()),
                survivor.placement().systemId(),
                target.placement().systemId(),
                "system:" + target.placement().systemId().value(),
                RulesOfEngagement.IDENTIFIED_HOSTILES,
                new SupplyPolicy(0, 0, 0L),
                new WithdrawalPolicy(survivor.placement().systemId(), 0, true, true),
                OperationStatus.CONTACT_CONFIRMED,
                tick,
                tick,
                -1L,
                contact,
                null);
    }

    private static EngineeringComponent engineering(Stage22CorePairTacticalFactory.Duel duel, long entityId) {
        return duel.weapons().battleState().combatants().stream()
                .filter(value -> value.spec().entityId() == entityId)
                .findFirst()
                .orElseThrow()
                .engineering();
    }

    private static EngineeringComponent copy(EngineeringComponent source) {
        return new EngineeringComponent(source.fit, source.runtimeState, source.instanceState);
    }

    private static Stage21GPhysicalRecoveryService physicalRecovery(Stage22CorePairRecoveryProbe.PreparedYard setup) {
        return new Stage21GPhysicalRecoveryService(
                Stage18ShipConsumableCatalogLoader.loadDefault(),
                new Stage18ShipConsumableService(
                        Stage18ShipConsumableCatalogLoader.loadDefault(), setup.engineering()),
                new Stage19WarfareSupplyService(setup.products()),
                new ShipyardEngineeringService(setup.engineering(), setup.industrial()),
                setup.yardRuntime(),
                setup.engineering());
    }

    private static Stage18StationStorage replacementStock(
            Stage22CorePairRecoveryProbe.PreparedYard setup,
            InstalledFit fit) {
        Map<String, Double> materials = new TreeMap<>();
        setup.yards().findHullProfile(fit.hullId()).buildInputsKg()
                .forEach(input -> materials.merge(input.commodityId(), input.massKg(), Double::sum));
        Map<String, Integer> modules = new TreeMap<>();
        fit.installedModules().forEach(row -> modules.merge(row.moduleId(), 1, Integer::sum));
        return new Stage18StationStorage(
                Stage18ResourceOntologyLoader.loadDefault(),
                setup.products(),
                setup.station().stationId(),
                setup.station().storage().snapshotCapacityByStorageClassKg(),
                materials,
                modules);
    }

    private static List<MilitaryFleet> militaryFleets(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FactionIdentityResolver identities) {
        ArrayList<MilitaryFleet> result = new ArrayList<>();
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) continue;
            Entity entity = entity(runtime, placement);
            EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            if (engineering == null || faction == null) continue;
            String stableFaction = identities.stableId(faction.factionId).orElseThrow();
            result.add(new MilitaryFleet(placement.id(), faction.factionId, stableFaction, placement));
        }
        result.sort(java.util.Comparator.comparing(MilitaryFleet::fleetId));
        return List.copyOf(result);
    }

    private static Entity entity(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetPlacementState placement) {
        return runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().require(placement.localEntityId());
    }

    private record MilitaryFleet(
            FleetId fleetId,
            int runtimeFactionId,
            String stableFactionId,
            FleetPlacementState placement) { }

    private record ScenarioResult(
            String factionId,
            long lostFleetId,
            long replacementFleetId,
            boolean fullRuntimeByteStable,
            boolean recoveryByteStable,
            boolean exactBerthRestored,
            boolean exactFitRestored,
            boolean finiteStockConsumed,
            double buildSeconds) { }
}
