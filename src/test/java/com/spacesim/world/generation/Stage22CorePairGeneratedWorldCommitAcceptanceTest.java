package com.spacesim.world.generation;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.content.Stage22CorePairExperimentProtocol;
import com.spacesim.persistence.EntityState;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistenceCodec;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M22.6 B01 generated-world commit evidence for the exact Empire/Industrial Union core fits. */
class Stage22CorePairGeneratedWorldCommitAcceptanceTest {
    private static final long OPERATION_ID = 22_601L;
    private static final long TACTICAL_TICKS = 240L;

    @Test
    void exactCoreFitsCommitThroughStage21eAuthorityAndRoundTripDeterministically() {
        ScenarioResult first = runScenario();
        ScenarioResult second = runScenario();

        assertArrayEquals(first.checkpoint(), second.checkpoint(),
                "same generated world, exact core fits and bounded Stage-19 horizon must replay byte-identically");
        assertEquals(first.remainingCoreFleetIds(), second.remainingCoreFleetIds());
        assertTrue(first.anyPhysicalEffect(), "core encounter must commit spent stores, damage or destruction");
        assertEquals(first.nextFleetIdBefore(), first.nextFleetIdAfter(),
                "tactical commit must never allocate replacement FleetIds");
        assertTrue(first.remainingCoreFleetIds().stream().allMatch(first.coreFleetIds()::contains),
                "post-encounter core FleetIds must be a subset of the original ordinary FleetIds");
    }

    private static ScenarioResult runScenario() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        List<MilitaryFleet> military = militaryFleets(runtime);
        MilitaryFleet attacker = military.get(0);
        MilitaryFleet target = military.stream()
                .filter(value -> value.factionId() != attacker.factionId())
                .findFirst().orElseThrow();
        moveFleetByOrdinaryRoute(runtime, target.fleetId(), attacker.systemId());

        FleetPlacementState attackerPlacement = runtime.world().findFleet(attacker.fleetId()).orElseThrow();
        FleetPlacementState targetPlacement = runtime.world().findFleet(target.fleetId()).orElseThrow();
        assertEquals(attacker.systemId(), targetPlacement.systemId());

        var core = Stage22CorePairTacticalFactory.createDestroyerDuel(
                Stage22CorePairExperimentProtocol.Permutation.DEFAULT);
        EngineeringComponent empire = core.weapons().battleState().combatants().stream()
                .filter(value -> value.spec().entityId() == Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID)
                .findFirst().orElseThrow().engineering();
        EngineeringComponent union = core.weapons().battleState().combatants().stream()
                .filter(value -> value.spec().entityId() == Stage22CorePairTacticalFactory.UNION_ENTITY_ID)
                .findFirst().orElseThrow().engineering();

        Entity attackerEntity = runtime.world().findSession(attacker.systemId()).orElseThrow()
                .getEntityRegistry().require(attackerPlacement.localEntityId());
        Entity targetEntity = runtime.world().findSession(attacker.systemId()).orElseThrow()
                .getEntityRegistry().require(targetPlacement.localEntityId());
        attackerEntity.add(copy(empire));
        targetEntity.add(copy(union));

        LocalPhysicalKinematics attackerPhysical = runtime.arrival().materialization(attacker.systemId())
                .physicalState(attackerPlacement.localEntityId()).orElseThrow();
        runtime.arrival().materialization(attacker.systemId()).updatePhysicalState(
                targetPlacement.localEntityId(),
                new LocalPhysicalKinematics(attackerPhysical.position().translated(1_430d, 0d), 0d, 0d));

        EntityState attackerBefore = EntityStateMapper.capture(attackerEntity);
        EntityState targetBefore = EntityStateMapper.capture(targetEntity);
        List<PhysicalCombatant> combatants = new ArrayList<>(List.of(
                new PhysicalCombatant(attacker.fleetId(), CombatSide.OPERATION,
                        attackerEntity.getComponent(FactionComponent.class).factionId, attackerBefore),
                new PhysicalCombatant(target.fleetId(), CombatSide.CONTACT,
                        targetEntity.getComponent(FactionComponent.class).factionId, targetBefore)));
        combatants.sort(Comparator.comparing(PhysicalCombatant::fleetId));

        long now = runtime.world().getAuthoritativeWorldTick();
        long nextFleetIdBefore = runtime.world().snapshot().nextFleetIdValue();
        Set<FleetId> coreFleetIds = Set.of(attacker.fleetId(), target.fleetId());
        var resolver = new Stage19ExactTacticalEncounterResolver(
                core.content().engineering(), core.protection(), core.content().ammunition(), core.content().launchers());
        var authority = new Stage21EGeneratedWorldStage19Authority(runtime, resolver, TACTICAL_TICKS);
        long encounterId = authority.materializeExact(
                new TacticalMaterializationRequest(OPERATION_ID, attacker.systemId(), now, List.copyOf(combatants)));
        assertEquals(now + 1L, encounterId);

        Set<FleetId> remaining = new HashSet<>();
        for (FleetId fleetId : coreFleetIds) {
            runtime.world().findFleet(fleetId).ifPresent(ignored -> remaining.add(fleetId));
        }
        boolean anyPhysicalEffect = remaining.size() < coreFleetIds.size();
        for (FleetId fleetId : remaining) {
            FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
            Entity entity = runtime.world().findSession(placement.systemId()).orElseThrow()
                    .getEntityRegistry().require(placement.localEntityId());
            EntityState before = fleetId.equals(attacker.fleetId()) ? attackerBefore : targetBefore;
            EntityState after = EntityStateMapper.capture(entity);
            anyPhysicalEffect |= !after.equals(before);
            String expectedFit = fleetId.equals(attacker.fleetId())
                    ? Stage22CorePairTacticalFactory.EMPIRE_DESTROYER_FIT
                    : Stage22CorePairTacticalFactory.UNION_DESTROYER_FIT;
            assertEquals(expectedFit, entity.getComponent(EngineeringComponent.class).fit.fitId(),
                    "generated-world tactical commit must preserve the exact installed core fit");
        }

        byte[] checkpoint = Stage20GeneratedWorldRuntimePersistenceCodec.encode(runtime.captureState());
        var decoded = Stage20GeneratedWorldRuntimePersistenceCodec.decode(checkpoint);
        assertArrayEquals(checkpoint, Stage20GeneratedWorldRuntimePersistenceCodec.encode(decoded),
                "post-core-encounter generated-world checkpoint must be byte-stable");
        return new ScenarioResult(
                checkpoint,
                Set.copyOf(coreFleetIds),
                Set.copyOf(remaining),
                anyPhysicalEffect,
                nextFleetIdBefore,
                runtime.world().snapshot().nextFleetIdValue());
    }

    private static EngineeringComponent copy(EngineeringComponent source) {
        return new EngineeringComponent(source.fit, source.runtimeState, source.instanceState);
    }

    private static List<MilitaryFleet> militaryFleets(Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime) {
        ArrayList<MilitaryFleet> result = new ArrayList<>();
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) continue;
            Entity entity = runtime.world().findSession(placement.systemId()).orElseThrow()
                    .getEntityRegistry().require(placement.localEntityId());
            EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            if (engineering != null && faction != null) {
                result.add(new MilitaryFleet(placement.id(), faction.factionId, placement.systemId()));
            }
        }
        result.sort(Comparator.comparing(MilitaryFleet::fleetId));
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
            for (int attempt = 0; attempt < 400 && runtime.world().findFleetJump(fleetId).isPresent(); attempt++) {
                runtime.advanceFrame(0.25f);
            }
            assertTrue(runtime.world().findFleetJump(fleetId).isEmpty(),
                    "ordinary target movement must finish every topology hop");
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
        throw new AssertionError("generated topology has no route between military fleets");
    }

    private record MilitaryFleet(FleetId fleetId, int factionId, StarSystemId systemId) { }

    private record ScenarioResult(
            byte[] checkpoint,
            Set<FleetId> coreFleetIds,
            Set<FleetId> remainingCoreFleetIds,
            boolean anyPhysicalEffect,
            long nextFleetIdBefore,
            long nextFleetIdAfter) {
        private ScenarioResult {
            checkpoint = Arrays.copyOf(checkpoint, checkpoint.length);
            coreFleetIds = Set.copyOf(coreFleetIds);
            remainingCoreFleetIds = Set.copyOf(remainingCoreFleetIds);
        }
    }
}
