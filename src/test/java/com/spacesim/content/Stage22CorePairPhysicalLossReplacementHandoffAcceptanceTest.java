package com.spacesim.content;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.content.Stage18ManufacturingProductRegistry.Provenance;
import com.spacesim.content.Stage22CorePairExperimentProtocol.Permutation;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.economy.Stage18ShipConsumableCatalogLoader;
import com.spacesim.economy.Stage18ShipConsumableService;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.economy.Stage19WarfareSupplyService;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.persistence.SettlementRecoveryStateCodec;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.persistence.WorldStateCodec;
import com.spacesim.ship.ShieldFieldRuntime.State;
import com.spacesim.ship.ShipDamageRuntime;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipInstanceRuntimeState;
import com.spacesim.ship.ShipyardEngineeringService;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver;
import com.spacesim.ship.Stage22CorePairTacticalFactory;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.FactionIdentityResolver;
import com.spacesim.world.FleetForceRegistry;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetOperationalAvailability;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.FleetReadinessEvaluator;
import com.spacesim.world.FleetReadinessState;
import com.spacesim.world.GeneratedWorldFtlTestSupport;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.SettlementRecoveryService;
import com.spacesim.world.SettlementRecoveryState;
import com.spacesim.world.Stage21EPhysicalConsequenceService;
import com.spacesim.world.Stage21ETacticalMaterializationService.CombatSide;
import com.spacesim.world.Stage21ETacticalMaterializationService.PhysicalCombatant;
import com.spacesim.world.Stage21ETacticalMaterializationService.TacticalMaterializationRequest;
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
import com.spacesim.world.generation.Stage21EGeneratedWorldStage19Authority;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
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
 * M22.6 B13/B14 production handoff from an actual generated-world combat loss to paid replacement.
 *
 * <p>The target begins as a declared heavily attrited survivor: exact core fit and finite stores are
 * retained, structural integrity is low and its already-collapsed shield remains unavailable for the
 * contact. Stage-19 still has to inflict the catastrophic loss. Only disappearance of the ordinary
 * FleetId from the generated world may then become a Stage-21E consequence, Stage-21G loss record and
 * replacement demand. The replacement is built from finite Stage-18 hull/module stock and work.</p>
 */
class Stage22CorePairPhysicalLossReplacementHandoffAcceptanceTest {
    private static final long OPERATION_ID = 22_613_001L;
    private static final long TACTICAL_TICKS = 1_200L;
    private static final int CREW_AVAILABLE = 100_000;
    private static final double CONTACT_SEPARATION_M = 850d;
    private static final double ATTRITED_STRUCTURE = 0.03d;
    private static final double SHIELD_RESTART_SECONDS = 10_000d;

    @Test
    void b13PhysicalLossBecomesPersistedPaidReplacementForEitherCorePackage() {
        ScenarioResult unionLoss = run(Permutation.DEFAULT);
        ScenarioResult empireLoss = run(Permutation.MIRRORED);

        assertEquals(Stage22CorePairBalanceEvidence.UNION_FACTION_ID, unionLoss.corePackageLost());
        assertEquals(Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID, empireLoss.corePackageLost());
        for (ScenarioResult result : List.of(unionLoss, empireLoss)) {
            assertTrue(result.physicalLossRecorded());
            assertTrue(result.replacementCommissioned());
            assertTrue(result.buildSeconds() > 0d);
            assertTrue(result.consumedHullMassKg() > 0d);
            assertTrue(result.consumedModuleMassKg() > 0d);
            assertNotEquals(result.lostFleetId(), result.replacementFleetId(),
                    "replacement must retain the ordinary fresh-identity rule");
        }

        LinkedHashMap<String, Object> archive = new LinkedHashMap<>();
        archive.put("default", unionLoss);
        archive.put("mirrored", empireLoss);
        Stage22CorePairEvidenceArchive.write(
                "B13-B14-physical-loss-paid-replacement-handoff",
                archive,
                "Generated-world exact Stage-19 combat causes the FleetId loss; Stage-21E consequence reconciliation derives that loss from before/after physical registries; Stage-21G persists it and accepts a replacement demand; existing Stage-18/17.5 yard authority consumes finite hull/module stock and work to commission a fresh FleetId. Mirroring makes both exact core destroyer packages traverse the same loss-to-replacement seam. The declared low-integrity/collapsed-shield target represents prior rolling attrition and is not synthetic combat damage.");
    }

    private static ScenarioResult run(Permutation permutation) {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        List<MilitaryFleet> military = militaryFleets(runtime);
        MilitaryFleet attacker = military.get(0);
        MilitaryFleet target = military.stream()
                .filter(value -> value.factionId() != attacker.factionId())
                .findFirst()
                .orElseThrow();
        moveFleetByOrdinaryRoute(runtime, target.fleetId(), attacker.systemId());

        FleetPlacementState attackerPlacement = runtime.world().findFleet(attacker.fleetId()).orElseThrow();
        FleetPlacementState targetPlacement = runtime.world().findFleet(target.fleetId()).orElseThrow();
        StarSystemId system = attackerPlacement.systemId();
        assertEquals(system, targetPlacement.systemId());

        var core = Stage22CorePairTacticalFactory.createDestroyerDuel(permutation);
        boolean targetIsEmpire = permutation == Permutation.MIRRORED;
        EngineeringComponent empire = engineeringFor(core, Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID);
        EngineeringComponent union = engineeringFor(core, Stage22CorePairTacticalFactory.UNION_ENTITY_ID);
        EngineeringComponent attackerCore = targetIsEmpire ? union : empire;
        EngineeringComponent targetCore = targetIsEmpire ? empire : union;
        String targetCoreFaction = targetIsEmpire
                ? Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID
                : Stage22CorePairBalanceEvidence.UNION_FACTION_ID;

        Entity attackerEntity = entity(runtime, attackerPlacement);
        Entity targetEntity = entity(runtime, targetPlacement);
        attackerEntity.add(copy(attackerCore));
        targetEntity.add(attrited(targetCore));
        InstalledFit lostFit = targetEntity.getComponent(EngineeringComponent.class).fit;

        LocalPhysicalKinematics attackerPhysical = runtime.arrival().materialization(system)
                .physicalState(attackerPlacement.localEntityId()).orElseThrow();
        runtime.arrival().materialization(system).updatePhysicalState(
                targetPlacement.localEntityId(),
                new LocalPhysicalKinematics(
                        attackerPhysical.position().translated(CONTACT_SEPARATION_M, 0d),
                        0d,
                        0d));

        var evaluator = new FleetReadinessEvaluator(Stage22CorePairEngineeringCatalogLoader.loadDefault());
        Map<FleetId, FleetOperationalAvailability> available = Map.of(
                attacker.fleetId(), new FleetOperationalAvailability(CREW_AVAILABLE, FleetReadinessState.FULL),
                target.fleetId(), new FleetOperationalAvailability(CREW_AVAILABLE, FleetReadinessState.FULL));
        FleetForceRegistry before = FleetForceRegistry.reconstruct(runtime.world().snapshot(), evaluator, available);
        long now = runtime.world().getAuthoritativeWorldTick();
        OperationState operation = contactedOperation(
                attacker.fleetId(), target.fleetId(), attacker.factionId(), system, now);

        List<PhysicalCombatant> combatants = new ArrayList<>(List.of(
                new PhysicalCombatant(
                        attacker.fleetId(), CombatSide.OPERATION, attacker.factionId(), EntityStateMapper.capture(attackerEntity)),
                new PhysicalCombatant(
                        target.fleetId(), CombatSide.CONTACT, target.factionId(), EntityStateMapper.capture(targetEntity))));
        combatants.sort(java.util.Comparator.comparing(PhysicalCombatant::fleetId));
        var resolver = new Stage19ExactTacticalEncounterResolver(
                core.content().engineering(), core.protection(), core.content().ammunition(), core.content().launchers());
        new Stage21EGeneratedWorldStage19Authority(runtime, resolver, TACTICAL_TICKS).materializeExact(
                new TacticalMaterializationRequest(OPERATION_ID, system, now, List.copyOf(combatants)));

        assertFalse(runtime.world().findFleet(target.fleetId()).isPresent(),
                "declared attrited target must still be destroyed by exact Stage-19 physical combat");
        assertTrue(runtime.world().findFleet(attacker.fleetId()).isPresent(),
                "B13 handoff fixture requires the opposing ordinary fleet to survive the loss contact");

        Map<FleetId, FleetOperationalAvailability> survivingAvailability = Map.of(
                attacker.fleetId(), new FleetOperationalAvailability(CREW_AVAILABLE, FleetReadinessState.FULL));
        FleetForceRegistry after = FleetForceRegistry.reconstruct(
                runtime.world().snapshot(), evaluator, survivingAvailability);
        var report = new Stage21EPhysicalConsequenceService().reconcile(operation, before, after);
        assertEquals(List.of(target.fleetId()), report.losses(),
                "only actual disappearance of the ordinary target FleetId may count as the B13 loss");

        FactionIdentityResolver identities = FactionIdentityResolver.createDefault(
                ContentCatalogLoader.loadDefault(), runtime.world().snapshot().factionIdentities());
        String attackerStable = identities.stableId(attacker.factionId()).orElseThrow();
        String targetStable = identities.stableId(target.factionId()).orElseThrow();
        var settlement = new SettlementRecoveryState.Settlement(
                1L,
                "proposal.m22_6.b13.attrition",
                "war.m22_6.b13.attrition",
                attackerStable,
                targetStable,
                now,
                now,
                SettlementRecoveryState.SettlementStatus.PENDING,
                false);
        var recovery = new SettlementRecoveryService(new SettlementRecoveryState(
                SettlementRecoveryState.CURRENT_VERSION,
                now,
                2L,
                1L,
                List.of(settlement),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
        recovery.recordPhysicalLosses(1L, OPERATION_ID, report, before, identities, now);
        assertEquals(target.fleetId(), recovery.snapshot().losses().get(0).lostFleetId());
        var demand = recovery.requestReplacement(1L, target.fleetId(), lostFit, now);
        recovery.finalizeRecoveryPlan(1L, now);

        Stage22CorePairRecoveryProbe.PreparedYard setup = Stage22CorePairRecoveryProbe.prepareYard(targetIsEmpire);
        Stage18StationStorage stock = replacementStock(setup, lostFit);
        Map<String, Double> initialMaterials = stock.snapshotCommodityMassByIdKg();
        Map<String, Integer> initialModules = stock.snapshotProductCountById();
        var planner = new ShipyardEngineeringService(setup.engineering(), setup.industrial());
        var buildPlan = planner.planBuild(lostFit, setup.yard().plannerCapability());
        double buildSeconds = buildPlan.requirements().totalWorkSeconds() / setup.yard().plannerCapability().workRate();
        Stage21GPhysicalRecoveryService recoveryService = physicalRecovery(setup);
        long fleetAllocatorBefore = runtime.world().snapshot().nextFleetIdValue();
        var built = recoveryService.buildReplacement(
                recovery,
                demand.id(),
                runtime.world(),
                identities,
                system,
                "M22.6 rolling attrition replacement",
                0f,
                0f,
                lostFit,
                stock,
                setup.yard(),
                setup.yard().openInterval(buildSeconds + 1d),
                now);

        assertTrue(built.settlement().settled());
        assertEquals(SettlementRecoveryState.ReplacementStatus.COMMISSIONED,
                recovery.snapshot().requireReplacementDemand(demand.id()).status());
        assertTrue(runtime.world().findFleet(built.commissionedFleetId()).isPresent());
        assertEquals(fleetAllocatorBefore + 1L, runtime.world().snapshot().nextFleetIdValue());
        assertTrue(stock.snapshotCommodityMassByIdKg().isEmpty());
        assertTrue(stock.snapshotProductCountById().isEmpty());
        assertEquals(initialMaterials, built.settlement().consumedCommodityMassKg());
        assertEquals(initialModules, built.settlement().consumedProductCount());

        byte[] worldBytes = WorldStateCodec.encode(runtime.world().snapshot());
        assertArrayEquals(worldBytes, WorldStateCodec.encode(WorldStateCodec.decode(worldBytes)));
        byte[] recoveryBytes = SettlementRecoveryStateCodec.encode(recovery.snapshot());
        assertArrayEquals(recoveryBytes,
                SettlementRecoveryStateCodec.encode(SettlementRecoveryStateCodec.decode(recoveryBytes)));

        double hullMassKg = built.settlement().consumedCommodityMassKg().values().stream()
                .mapToDouble(Double::doubleValue).sum();
        double moduleMassKg = built.settlement().consumedProductCount().entrySet().stream()
                .mapToDouble(row -> setup.products().findProduct(row.getKey()).unitMassKg() * row.getValue())
                .sum();
        return new ScenarioResult(
                permutation.name(),
                targetCoreFaction,
                target.fleetId().value(),
                built.commissionedFleetId().value(),
                true,
                true,
                buildSeconds,
                hullMassKg,
                moduleMassKg);
    }

    private static OperationState contactedOperation(
            FleetId attacker,
            FleetId target,
            int attackerFaction,
            StarSystemId system,
            long tick) {
        ContactState contact = new ContactState(
                target,
                system,
                ObservationChannel.LOCAL_SENSOR_REPORT,
                "b13:physical:" + target.value(),
                tick,
                tick + TACTICAL_TICKS + 10L);
        return new OperationState(
                OPERATION_ID,
                OperationType.INTERCEPTION,
                OPERATION_ID,
                OPERATION_ID,
                attackerFaction,
                List.of(attacker),
                system,
                system,
                "system:" + system.value(),
                RulesOfEngagement.IDENTIFIED_HOSTILES,
                new SupplyPolicy(0, 0, 0L),
                new WithdrawalPolicy(system, 0, true, true),
                OperationStatus.CONTACT_CONFIRMED,
                tick,
                tick,
                -1L,
                contact,
                null);
    }

    private static EngineeringComponent engineeringFor(Stage22CorePairTacticalFactory.Duel core, long entityId) {
        return core.weapons().battleState().combatants().stream()
                .filter(value -> value.spec().entityId() == entityId)
                .findFirst()
                .orElseThrow()
                .engineering();
    }

    private static EngineeringComponent copy(EngineeringComponent source) {
        return new EngineeringComponent(source.fit, source.runtimeState, source.instanceState);
    }

    private static EngineeringComponent attrited(EngineeringComponent source) {
        Map<String, Double> compartments = new TreeMap<>();
        source.instanceState.damage().compartmentIntegrityById().keySet()
                .forEach(id -> compartments.put(id, ATTRITED_STRUCTURE));
        ShipDamageRuntime.Snapshot damage = new ShipDamageRuntime.Snapshot(
                compartments,
                source.instanceState.damage().moduleDamage());
        Map<String, State> shields = new TreeMap<>();
        source.instanceState.shieldStatesByMount().forEach((mount, state) -> shields.put(
                mount,
                new State(0d, state.accumulatedHeatJ(), true, SHIELD_RESTART_SECONDS, state.emitterIntegrity())));
        ShipInstanceRuntimeState instance = new ShipInstanceRuntimeState(
                damage,
                shields,
                source.instanceState.maintenance(),
                source.instanceState.weaponLoadout(),
                source.instanceState.weaponMountRuntime());
        return new EngineeringComponent(source.fit, source.runtimeState, instance);
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

    private static Entity entity(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetPlacementState placement) {
        return runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().require(placement.localEntityId());
    }

    private static List<MilitaryFleet> militaryFleets(Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime) {
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
        result.sort(java.util.Comparator.comparing(MilitaryFleet::fleetId));
        if (result.size() < 2) throw new AssertionError("generated world lacks military fleets");
        return List.copyOf(result);
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
        throw new AssertionError("generated topology has no route between B13 combatants");
    }

    private record MilitaryFleet(FleetId fleetId, int factionId, StarSystemId systemId) { }

    private record ScenarioResult(
            String permutation,
            String corePackageLost,
            long lostFleetId,
            long replacementFleetId,
            boolean physicalLossRecorded,
            boolean replacementCommissioned,
            double buildSeconds,
            double consumedHullMassKg,
            double consumedModuleMassKg) { }
}
