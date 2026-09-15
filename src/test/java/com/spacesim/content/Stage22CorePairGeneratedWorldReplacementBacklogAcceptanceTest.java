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
import com.spacesim.world.SettlementRecoveryState.ReplacementStatus;
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
import com.spacesim.world.generation.GeneratedFactionMilitaryBootstrap;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import com.spacesim.world.generation.Stage21GGeneratedWorldPhysicalRecoveryAuthority;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 B13 production-versus-preservation evidence from finite replacement capacity.
 *
 * <p>The generated military bootstrap owns exactly three ordinary physical patrol ships per generated
 * faction. This test keeps that real owner identity and installs either exact Stage-22 core package on
 * the three assets. Three ordinary destructions become three Stage-21E/21G physical loss rows. One
 * finite Stage-18 replacement bill can commission only one fresh FleetId, leaving two causal persisted
 * DEMANDED rows rather than an abstract backlog counter.</p>
 */
class Stage22CorePairGeneratedWorldReplacementBacklogAcceptanceTest {
    private static final long OPERATION_BASE = 22_613_700L;
    private static final int CREW_AVAILABLE = 100_000;

    @Test
    void b13ThreePhysicalLossesAgainstOneReplacementBillLeaveTwoDemandBacklogForBothCorePackages() {
        ScenarioResult empire = run(true);
        ScenarioResult union = run(false);

        assertEquals(Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID, empire.corePackageId());
        assertEquals(Stage22CorePairBalanceEvidence.UNION_FACTION_ID, union.corePackageId());
        for (ScenarioResult result : List.of(empire, union)) {
            assertEquals(GeneratedFactionMilitaryBootstrap.SHIPS_PER_FACTION, result.physicalLosses());
            assertEquals(3, result.replacementDemands());
            assertEquals(1, result.commissioned());
            assertEquals(2, result.backlog());
            assertEquals(1L, result.fleetIdsAllocated());
            assertTrue(result.failedBuildsAllocatedNothing());
            assertTrue(result.fullRuntimeByteStable());
            assertTrue(result.recoveryByteStable());
            assertFalse(result.physicalOwnerId().isBlank());
        }

        LinkedHashMap<String, Object> archive = new LinkedHashMap<>();
        archive.put("empireCorePackage", empire);
        archive.put("unionCorePackage", union);
        Stage22CorePairEvidenceArchive.write(
                "B13-generated-world-finite-replacement-backlog",
                archive,
                "The accepted generated bootstrap provides three ordinary FleetIds per generated faction. For each paired coordinate those real assets retain their actual stable owner while carrying either exact core package. Three distinct losses become three Stage-21G demands; one finite Stage-18 replacement bill commissions one fresh FleetId, two exhausted-stock attempts allocate nothing and remain DEMANDED, and both recovery/full-runtime checkpoints remain deterministic. This closes the accumulated finite-capacity backlog seam, not the remaining full long-war campaign trajectory.");
    }

    private static ScenarioResult run(boolean empireCorePackage) {
        String corePackageId = empireCorePackage
                ? Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID
                : Stage22CorePairBalanceEvidence.UNION_FACTION_ID;
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        FactionIdentityResolver identities = FactionIdentityResolver.createDefault(
                ContentCatalogLoader.loadDefault(), runtime.world().snapshot().factionIdentities());
        List<MilitaryFleet> military = militaryFleets(runtime, identities);
        if (military.isEmpty()) throw new AssertionError("B13 generated military bootstrap is empty");

        String physicalOwner = military.get(0).stableFactionId();
        List<MilitaryFleet> targets = military.stream()
                .filter(row -> row.stableFactionId().equals(physicalOwner))
                .toList();
        if (targets.size() != GeneratedFactionMilitaryBootstrap.SHIPS_PER_FACTION) {
            throw new AssertionError("B13 generated military bootstrap violated ships-per-faction contract");
        }
        MilitaryFleet survivor = military.stream()
                .filter(row -> !row.stableFactionId().equals(physicalOwner))
                .findFirst()
                .orElseThrow(() -> new AssertionError("B13 requires a second generated faction"));

        var duel = Stage22CorePairTacticalFactory.createDestroyerDuel(Permutation.DEFAULT);
        EngineeringComponent targetCore = engineering(
                duel,
                empireCorePackage ? Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID
                        : Stage22CorePairTacticalFactory.UNION_ENTITY_ID);
        EngineeringComponent survivorCore = engineering(
                duel,
                empireCorePackage ? Stage22CorePairTacticalFactory.UNION_ENTITY_ID
                        : Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID);
        targets.forEach(target -> entity(runtime, target.placement()).add(copy(targetCore)));
        entity(runtime, survivor.placement()).add(copy(survivorCore));
        InstalledFit replacementFit = targetCore.fit;

        long tick = runtime.world().getAuthoritativeWorldTick();
        var settlement = new SettlementRecoveryState.Settlement(
                1L,
                "proposal.m22_6.backlog." + corePackageId,
                "war.m22_6.backlog",
                survivor.stableFactionId(),
                physicalOwner,
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
        FleetReadinessEvaluator evaluator = new FleetReadinessEvaluator(duel.content().engineering());
        ArrayList<FleetId> liveObserved = new ArrayList<>();
        liveObserved.add(survivor.fleetId());
        targets.forEach(target -> liveObserved.add(target.fleetId()));

        for (int index = 0; index < targets.size(); index++) {
            MilitaryFleet target = targets.get(index);
            FleetForceRegistry before = registry(runtime, evaluator, liveObserved);
            long operationId = OPERATION_BASE + index;
            OperationState operation = contactedOperation(operationId, survivor, target, tick);
            var localId = target.placement().localEntityId();
            runtime.world().destroyEntity(target.placement().systemId(), localId, DestructionPolicy.destroyAll());
            runtime.arrival().materialization(target.placement().systemId())
                    .releasePhysicalStateForWorldTransfer(localId);
            assertFalse(runtime.world().findFleet(target.fleetId()).isPresent());
            liveObserved.remove(target.fleetId());
            FleetForceRegistry after = registry(runtime, evaluator, liveObserved);
            var consequence = new Stage21EPhysicalConsequenceService().reconcile(operation, before, after);
            assertEquals(List.of(target.fleetId()), consequence.losses());
            recovery.recordPhysicalLosses(1L, operationId, consequence, before, identities, tick);
            recovery.requestReplacement(1L, target.fleetId(), replacementFit, tick);
        }
        assertEquals(3, recovery.snapshot().losses().size());
        assertEquals(3, recovery.snapshot().replacementDemands().size());
        recovery.finalizeRecoveryPlan(1L, tick);

        Stage22CorePairRecoveryProbe.PreparedYard setup = Stage22CorePairRecoveryProbe.prepareYard(empireCorePackage);
        Stage18StationStorage stock = replacementStock(setup, replacementFit);
        ShipyardEngineeringService planner = new ShipyardEngineeringService(setup.engineering(), setup.industrial());
        var plan = planner.planBuild(replacementFit, setup.yard().plannerCapability());
        double buildSeconds = plan.requirements().totalWorkSeconds() / setup.yard().plannerCapability().workRate();
        Stage21GGeneratedWorldPhysicalRecoveryAuthority generatedRecovery =
                new Stage21GGeneratedWorldPhysicalRecoveryAuthority(runtime, physicalRecovery(setup));
        StarSystemId buildSystem = targets.get(0).placement().systemId();
        LocalPhysicalKinematics berth = LocalPhysicalKinematics.stationary(
                runtime.infrastructure().endpoints().stream()
                        .filter(endpoint -> endpoint.systemId().equals(buildSystem))
                        .map(Stage20GeneratedWorldRuntimeBridge.RuntimeEndpoint::position)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("B13 build system lacks generated berth")));

        long allocatorBefore = runtime.world().snapshot().nextFleetIdValue();
        var firstDemand = recovery.snapshot().replacementDemands().get(0);
        var first = generatedRecovery.buildReplacement(
                recovery,
                firstDemand.id(),
                identities,
                buildSystem,
                "M22.6 backlog replacement",
                berth,
                replacementFit,
                stock,
                setup.yard(),
                setup.yard().openInterval(buildSeconds + 1d),
                tick);
        assertTrue(first.settlement().settled());
        assertNotNull(first.commissionedFleetId());
        assertTrue(stock.snapshotCommodityMassByIdKg().isEmpty());
        assertTrue(stock.snapshotProductCountById().isEmpty());

        long allocatorAfterFirst = runtime.world().snapshot().nextFleetIdValue();
        boolean failedBuildsAllocatedNothing = true;
        for (int index = 1; index < 3; index++) {
            var demand = recovery.snapshot().replacementDemands().get(index);
            long beforeFailed = runtime.world().snapshot().nextFleetIdValue();
            var failed = generatedRecovery.buildReplacement(
                    recovery,
                    demand.id(),
                    identities,
                    buildSystem,
                    "M22.6 queued backlog replacement " + index,
                    berth,
                    replacementFit,
                    stock,
                    setup.yard(),
                    setup.yard().openInterval(buildSeconds + 1d),
                    tick);
            failedBuildsAllocatedNothing &= !failed.settlement().settled()
                    && failed.commissionedFleetId() == null
                    && runtime.world().snapshot().nextFleetIdValue() == beforeFailed
                    && recovery.snapshot().requireReplacementDemand(demand.id()).status() == ReplacementStatus.DEMANDED;
        }
        assertTrue(failedBuildsAllocatedNothing);

        int commissioned = (int) recovery.snapshot().replacementDemands().stream()
                .filter(row -> row.status() == ReplacementStatus.COMMISSIONED)
                .count();
        int backlog = (int) recovery.snapshot().replacementDemands().stream()
                .filter(row -> row.status() == ReplacementStatus.DEMANDED)
                .count();
        assertEquals(1, commissioned);
        assertEquals(2, backlog);
        assertEquals(allocatorBefore + 1L, allocatorAfterFirst);
        assertEquals(allocatorAfterFirst, runtime.world().snapshot().nextFleetIdValue());

        byte[] recoveryBytes = SettlementRecoveryStateCodec.encode(recovery.snapshot());
        SettlementRecoveryState restoredRecovery = SettlementRecoveryStateCodec.decode(recoveryBytes);
        boolean recoveryByteStable = java.util.Arrays.equals(
                recoveryBytes, SettlementRecoveryStateCodec.encode(restoredRecovery))
                && restoredRecovery.replacementDemands().stream()
                        .filter(row -> row.status() == ReplacementStatus.DEMANDED).count() == 2L;

        byte[] runtimeBytes = Stage20GeneratedWorldRuntimePersistenceCodec.encode(runtime.captureState());
        var decodedRuntime = Stage20GeneratedWorldRuntimePersistenceCodec.decode(runtimeBytes);
        boolean fullRuntimeByteStable = java.util.Arrays.equals(
                runtimeBytes, Stage20GeneratedWorldRuntimePersistenceCodec.encode(decodedRuntime));
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime restoredRuntime =
                Stage20GeneratedWorldRuntimeBridge.restore(decodedRuntime);
        assertTrue(restoredRuntime.world().findFleet(first.commissionedFleetId()).isPresent());
        targets.forEach(target -> assertFalse(restoredRuntime.world().findFleet(target.fleetId()).isPresent()));

        return new ScenarioResult(
                corePackageId,
                physicalOwner,
                recovery.snapshot().losses().size(),
                recovery.snapshot().replacementDemands().size(),
                commissioned,
                backlog,
                allocatorAfterFirst - allocatorBefore,
                failedBuildsAllocatedNothing,
                fullRuntimeByteStable,
                recoveryByteStable);
    }

    private static FleetForceRegistry registry(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetReadinessEvaluator evaluator,
            List<FleetId> observed) {
        Map<FleetId, FleetOperationalAvailability> availability = new TreeMap<>();
        for (FleetId fleetId : observed) {
            availability.put(fleetId, new FleetOperationalAvailability(CREW_AVAILABLE, FleetReadinessState.FULL));
        }
        return FleetForceRegistry.reconstruct(runtime.world().snapshot(), evaluator, availability);
    }

    private static OperationState contactedOperation(
            long operationId,
            MilitaryFleet survivor,
            MilitaryFleet target,
            long tick) {
        ContactState contact = new ContactState(
                target.fleetId(),
                target.placement().systemId(),
                ObservationChannel.LOCAL_SENSOR_REPORT,
                "b13:backlog:" + target.fleetId().value(),
                tick,
                tick + 100L);
        return new OperationState(
                operationId,
                OperationType.INTERCEPTION,
                operationId,
                operationId,
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
            Entity fleetEntity = entity(runtime, placement);
            EngineeringComponent engineering = fleetEntity.getComponent(EngineeringComponent.class);
            FactionComponent faction = fleetEntity.getComponent(FactionComponent.class);
            if (engineering == null || faction == null) continue;
            result.add(new MilitaryFleet(
                    placement.id(),
                    faction.factionId,
                    identities.stableId(faction.factionId).orElseThrow(),
                    placement));
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
            String corePackageId,
            String physicalOwnerId,
            int physicalLosses,
            int replacementDemands,
            int commissioned,
            int backlog,
            long fleetIdsAllocated,
            boolean failedBuildsAllocatedNothing,
            boolean fullRuntimeByteStable,
            boolean recoveryByteStable) { }
}
