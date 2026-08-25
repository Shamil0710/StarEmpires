package com.spacesim.world.generation;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipProtectionCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.content.ship.Stage175ICombatTestProtectionPack;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.ship.ShipDamageRuntime;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipInstanceRuntimeState;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.FleetCommandState;
import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.FleetOrderState;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.FleetCommandState.OrderStatus;
import com.spacesim.world.FleetCommandState.OrderType;
import com.spacesim.world.FleetForceRegistry;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetOperationalAvailability;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.FleetReadinessEvaluator;
import com.spacesim.world.FleetReadinessState;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.Stage21EPhysicalConsequenceService;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StrategicOperationState;
import com.spacesim.world.StrategicOperationState.ContactState;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Non-vacuous Stage-21E operation acceptance proving catastrophic loss and physical supply
 * consumption created by Stage 19 itself.
 *
 * <p>The scenario uses only ordinary generated fleets carrying exact Stage-21 strategic variants of
 * the provisional Stage-17.5I/19 doctrine fits. The hostile target begins in a valid, persisted,
 * heavily damaged and shield-depleted physical state but is explicitly not destroyed. Only the
 * production exact Stage-19 resolver is allowed to apply the final combat effects. Stage 21E then
 * has to commit that result by removing the same ordinary target {@link FleetId}; no fixture combat
 * score, statistical kill grant or replacement fleet is allowed. The surviving attacker must also
 * return with less physical ammunition and/or reaction mass than it carried before the exchange,
 * proving that operation supply is consumed from ordinary stores rather than hidden replenishment.</p>
 */
class Stage21EGeneratedWorldStage19LossAcceptanceTest {
    private static final double CRITICAL_INTEGRITY = 1e-6d;
    private static final String LAST_LIVE_MOUNT = "utility_storage";
    private static final double ENGAGEMENT_SEPARATION_M = 600d;
    private static final double MASS_EPSILON_KG = 1e-9d;

    @Test
    void exactStrategicFitOperationProducesRealStage19FleetLossWithoutReplacement() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        MilitaryFleet attacker = doctrineFleet(runtime, DoctrineId.E_BALANCED_CONTROL, -1);
        MilitaryFleet target = doctrineFleet(runtime, DoctrineId.D_DEFENSIVE_EW, attacker.factionId());

        moveFleetByOrdinaryRoute(runtime, target.fleetId(), attacker.systemId());
        FleetPlacementState attackerPlacement = runtime.world().findFleet(attacker.fleetId()).orElseThrow();
        FleetPlacementState targetPlacement = runtime.world().findFleet(target.fleetId()).orElseThrow();
        assertEquals(attacker.systemId(), targetPlacement.systemId());

        EngineeringComponent attackerEngineering = engineering(runtime, attacker.fleetId());
        double ammunitionBeforeKg = attackerEngineering.runtimeState.consumables().ammunitionMassKg();
        double reactionMassBeforeKg = attackerEngineering.runtimeState.consumables().reactionMassKg();
        assertTrue(ammunitionBeforeKg > 0d || reactionMassBeforeKg > 0d,
                "acceptance attacker must begin with physical operation supply to consume");

        Entity targetEntity = runtime.world().findSession(attacker.systemId()).orElseThrow()
                .getEntityRegistry().require(targetPlacement.localEntityId());
        EngineeringComponent targetEngineering = targetEntity.getComponent(EngineeringComponent.class);
        assertNotNull(targetEngineering);
        applyCriticalButSurvivingPhysicalState(targetEngineering);

        LocalPhysicalKinematics attackerPhysical = runtime.arrival().materialization(attacker.systemId())
                .physicalState(attackerPlacement.localEntityId()).orElseThrow();
        runtime.arrival().materialization(attacker.systemId()).updatePhysicalState(
                targetPlacement.localEntityId(),
                LocalPhysicalKinematics.stationary(
                        attackerPhysical.position().translated(0d, ENGAGEMENT_SEPARATION_M)));

        Map<FleetId, FleetOperationalAvailability> availability = Map.of(
                attacker.fleetId(), fullAvailability(),
                target.fleetId(), fullAvailability());
        FleetReadinessEvaluator evaluator = new FleetReadinessEvaluator(
                Stage175ICombatTestContentPack.loadDoctrines());
        FleetForceRegistry before = FleetForceRegistry.reconstruct(
                runtime.world().snapshot(), evaluator, availability);
        assertTrue(before.find(target.fleetId()).isPresent(),
                "target must still be an ordinary physical FleetId before Stage-19 execution");
        long now = runtime.world().getAuthoritativeWorldTick();

        CommandGroupState group = new CommandGroupState(
                1L,
                attacker.factionId(),
                "Stage21E real Stage19 casualty acceptance",
                List.of(attacker.fleetId()),
                attacker.systemId(),
                false,
                false,
                FleetReadinessState.FULL);
        FleetOrderState order = new FleetOrderState(
                1L,
                group.id(),
                OrderType.INTERCEPT,
                OrderSource.AI,
                attacker.systemId(),
                List.of(attacker.systemId()),
                0,
                now,
                now + 100L,
                OrderStatus.ACTIVE);
        FleetCommandState command = new FleetCommandState(2L, 2L, List.of(group), List.of(order));
        ContactState contact = new ContactState(
                target.fleetId(),
                attacker.systemId(),
                ObservationChannel.LOCAL_SENSOR_REPORT,
                "acceptance:real-stage19-loss:" + target.fleetId().value(),
                now,
                now + 100L);
        OperationState operation = new OperationState(
                1L,
                OperationType.INTERCEPTION,
                group.id(),
                order.id(),
                attacker.factionId(),
                List.of(attacker.fleetId()),
                attacker.systemId(),
                attacker.systemId(),
                "system:" + attacker.systemId().value(),
                RulesOfEngagement.IDENTIFIED_HOSTILES,
                new SupplyPolicy(0, 0, 100L),
                new WithdrawalPolicy(attacker.systemId(), 0, true, true),
                OperationStatus.CONTACT_CONFIRMED,
                now,
                now,
                -1L,
                contact,
                null);
        StrategicOperationState operations = new StrategicOperationState(2L, List.of(operation));

        int beforeFleetCount = runtime.world().getFleetPlacements().size();
        Stage21EGeneratedWorldTacticalExecutionService.ExecutionResult executed =
                new Stage21EGeneratedWorldTacticalExecutionService(
                        new Stage21EGeneratedWorldStage19Authority(runtime))
                        .execute(command, operations, operation.id(), before, now);

        FleetForceRegistry after = FleetForceRegistry.reconstruct(
                runtime.world().snapshot(), evaluator, availability);
        Stage21EPhysicalConsequenceService.ConsequenceReport consequences =
                new Stage21EPhysicalConsequenceService().reconcile(operation, before, after);

        assertEquals(List.of(target.fleetId()), consequences.losses(),
                "the targeted exact Stage-19 exchange must create one real ordinary FleetId loss");
        assertTrue(runtime.world().findFleet(target.fleetId()).isEmpty(),
                "Stage-19 catastrophic destruction must remove the exact ordinary target FleetId");
        assertTrue(runtime.world().findFleet(attacker.fleetId()).isPresent(),
                "the operation fleet must remain an ordinary surviving FleetId");
        assertEquals(beforeFleetCount - 1, runtime.world().getFleetPlacements().size(),
                "Stage 21E may remove the Stage-19 casualty but must not allocate any replacement");

        EngineeringComponent survivingAttacker = engineering(runtime, attacker.fleetId());
        double ammunitionAfterKg = survivingAttacker.runtimeState.consumables().ammunitionMassKg();
        double reactionMassAfterKg = survivingAttacker.runtimeState.consumables().reactionMassKg();
        assertTrue(
                ammunitionAfterKg + MASS_EPSILON_KG < ammunitionBeforeKg
                        || reactionMassAfterKg + MASS_EPSILON_KG < reactionMassBeforeKg,
                "exact Stage-19 operation must consume physical ammunition and/or reaction mass from the surviving FleetId");

        assertFalse(executed.owningGroupDestroyed());
        assertEquals(List.of(attacker.fleetId()),
                executed.commandState().requireGroup(group.id()).memberFleetIds());

        OperationState resolved = executed.operationState().requireOperation(operation.id());
        assertEquals(OperationStatus.ACTIVE, resolved.status());
        assertNotNull(resolved.encounter());
        assertFalse(resolved.encounter().active(),
                "the exact casualty must be committed before Stage-21E tactical execution returns");
    }

    private static void applyCriticalButSurvivingPhysicalState(EngineeringComponent engineering) {
        ShipEngineeringCatalog catalog = Stage175ICombatTestContentPack.loadStage21StrategicDoctrines();
        ShipProtectionCatalog protection = Stage175ICombatTestProtectionPack.load();
        var hull = catalog.findHull(engineering.fit.hullId());
        var layout = protection.findHullDamageLayout(hull.id());

        TreeMap<String, Double> moduleIntegrity = new TreeMap<>();
        engineering.fit.installedModules().forEach(installed -> moduleIntegrity.put(installed.mountId(), 0d));
        if (!moduleIntegrity.containsKey(LAST_LIVE_MOUNT)) {
            throw new AssertionError("generated defensive strategic fit lacks expected physical storage mount");
        }
        moduleIntegrity.put(LAST_LIVE_MOUNT, CRITICAL_INTEGRITY);
        ShipDamageRuntime.Snapshot damage = new ShipDamageRuntime.Snapshot(
                Map.of(
                        "engineering", CRITICAL_INTEGRITY,
                        "mission_core", 0d,
                        "weapons", 0d),
                new DamageState(moduleIntegrity));
        assertFalse(ShipDamageRuntime.isFullyDestroyed(hull, engineering.fit, layout, damage),
                "acceptance target must not already satisfy catastrophic destruction before Stage 19");

        ShipInstanceRuntimeState previous = engineering.instanceState;
        engineering.setInstanceState(new ShipInstanceRuntimeState(
                damage,
                Map.of(),
                previous.maintenance(),
                previous.weaponLoadout(),
                previous.weaponMountRuntime()));
    }

    private static EngineeringComponent engineering(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId) {
        FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
        if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
            throw new AssertionError("operation supply inspection requires an in-system ordinary FleetId");
        }
        Entity entity = runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().require(placement.localEntityId());
        EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
        if (engineering == null) {
            throw new AssertionError("ordinary military FleetId lacks physical engineering state");
        }
        return engineering;
    }

    private static MilitaryFleet doctrineFleet(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            DoctrineId doctrineId,
            int excludedFactionId) {
        InstalledFit expected = strategicFit(doctrineId);
        ArrayList<MilitaryFleet> matches = new ArrayList<>();
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) continue;
            Entity entity = runtime.world().findSession(placement.systemId()).orElseThrow()
                    .getEntityRegistry().require(placement.localEntityId());
            EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            if (engineering == null || faction == null || faction.factionId == excludedFactionId) continue;
            if (engineering.fit.equals(expected)) {
                matches.add(new MilitaryFleet(placement.id(), faction.factionId, placement.systemId()));
            }
        }
        matches.sort(java.util.Comparator.comparing(MilitaryFleet::fleetId));
        if (matches.isEmpty()) {
            throw new AssertionError("generated world lacks exact strategic doctrine fleet: " + doctrineId);
        }
        return matches.get(0);
    }

    private static InstalledFit strategicFit(DoctrineId doctrineId) {
        ShipEngineeringCatalog catalog = Stage175ICombatTestContentPack.loadStage21StrategicDoctrines();
        var doctrine = Stage175IFleetDoctrineCatalog.get(doctrineId);
        return InstalledFit.fromDemonstrator(catalog.findDemonstratorFit(
                Stage175ICombatTestContentPack.stage21StrategicFitId(doctrine.fitId())));
    }

    private static void moveFleetByOrdinaryRoute(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId,
            StarSystemId destination) {
        FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
        List<StarSystemId> route = route(runtime, placement.systemId(), destination);
        for (int index = 1; index < route.size(); index++) {
            runtime.world().requestFleetJump(fleetId, route.get(index));
            for (int attempt = 0; attempt < 400 && runtime.world().findFleetJump(fleetId).isPresent(); attempt++) {
                runtime.advanceFrame(0.25f);
            }
            assertTrue(runtime.world().findFleetJump(fleetId).isEmpty(),
                    "ordinary hostile movement must finish every topology hop");
            assertEquals(route.get(index), runtime.world().findFleet(fleetId).orElseThrow().systemId());
            if (index + 1 < route.size()) awaitFittedCooldown(runtime, fleetId);
        }
    }

    private static void awaitFittedCooldown(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId) {
        for (int attempt = 0; attempt < 400; attempt++) {
            FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
            Entity entity = runtime.world().findSession(placement.systemId()).orElseThrow()
                    .getEntityRegistry().require(placement.localEntityId());
            EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
            if (engineering == null || engineering.runtimeState.ftlCooldownSecondsByMount().values().stream()
                    .noneMatch(value -> value > 0d)) return;
            runtime.advanceFrame(0.25f);
        }
        throw new AssertionError("ordinary fitted FTL cooldown did not clear through simulation time");
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
        throw new AssertionError("generated topology has no physical route to Stage-19 loss objective");
    }

    private static FleetOperationalAvailability fullAvailability() {
        return new FleetOperationalAvailability(Integer.MAX_VALUE, FleetReadinessState.FULL);
    }

    private record MilitaryFleet(FleetId fleetId, int factionId, StarSystemId systemId) { }
}
