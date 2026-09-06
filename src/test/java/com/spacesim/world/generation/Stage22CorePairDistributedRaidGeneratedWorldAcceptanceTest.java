package com.spacesim.world.generation;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.content.Stage22CorePairBalanceEvidence;
import com.spacesim.content.Stage22CorePairExperimentProtocol.Permutation;
import com.spacesim.persistence.EntityState;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistenceCodec;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver;
import com.spacesim.ship.Stage22CorePairTacticalFactory;
import com.spacesim.world.FleetForceRegistry;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetOperationalAvailability;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.FleetReadinessEvaluator;
import com.spacesim.world.FleetReadinessState;
import com.spacesim.world.GeneratedWorldFtlTestSupport;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.PhysicalWarfareOperation;
import com.spacesim.world.PhysicalWarfareOperationService;
import com.spacesim.world.Stage21ETacticalMaterializationService.CombatSide;
import com.spacesim.world.Stage21ETacticalMaterializationService.PhysicalCombatant;
import com.spacesim.world.Stage21ETacticalMaterializationService.TacticalMaterializationRequest;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StrategicOperationService;
import com.spacesim.world.StrategicOperationService.SupplyDecision;
import com.spacesim.world.StrategicOperationState;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 B06 generated-world integration for distributed low-intensity raids.
 *
 * <p>The generated campaign already commissions three ordinary physical military FleetIds per
 * faction. This acceptance uses those exact persistent fleets rather than inventing a raid-force
 * registry: each attacker/defender pair moves through the ordinary generated-world FTL path into a
 * distinct objective system, receives the accepted exact Stage-22 engineering fit at the same
 * Stage-21E tactical-admission seam used by B01/B08, and remains owned by ordinary world/persistence
 * authorities.</p>
 *
 * <p>Three simultaneous raid lanes are reviewed by the ordinary Stage-21E supply/readiness service.
 * Two lanes retain observed physical supply access and are allowed to commit an exact Stage-19
 * encounter against a real opposing patrol FleetId. The third lane loses supply access, submits the
 * ordinary withdrawal decision and is proven physically unchanged while the other lanes fight. No
 * raid damage scalar, abstract patrol score, hidden income penalty or faction-specific combat bonus is
 * introduced.</p>
 *
 * <p>Initial inter-system staging still uses the generated campaign's provisional Stage-21 military
 * engineering because the production world FTL catalog has not yet been promoted to the Stage-22
 * core-fit universe; B10 owns exact-core projection travel. Exact core fits are installed only after
 * ordinary physical arrival, matching the existing B01/B08 tactical materialization boundary.</p>
 */
class Stage22CorePairDistributedRaidGeneratedWorldAcceptanceTest {
    private static final long OPERATION_BASE = 22_606_000L;
    private static final long TACTICAL_TICKS = 600L;
    private static final int CREW_AVAILABLE = 100_000;
    private static final int REQUIRED_SUPPLY_ACCESS_BPS = 5_000;
    private static final double CONTACT_SEPARATION_M = 1_250d;
    private static final float SIMULATION_WAIT_FRAME_SECONDS = 10f;
    private static final int MAX_COOLDOWN_WAIT_FRAMES = 40;

    @Test
    void b06DistributesThreePhysicalRaidLanesAcrossGeneratedWorldAndCommitsOnlySupportedContacts() {
        ScenarioResult defaultFirst = run(Permutation.DEFAULT);
        ScenarioResult defaultSecond = run(Permutation.DEFAULT);
        ScenarioResult mirrored = run(Permutation.MIRRORED);

        assertArrayEquals(defaultFirst.checkpoint(), defaultSecond.checkpoint(),
                "same B06 generated-world inputs must produce a byte-identical authoritative checkpoint");
        assertEquals(defaultFirst.summary(), defaultSecond.summary());

        for (ScenarioResult result : List.of(defaultFirst, mirrored)) {
            assertEquals(3, result.summary().distinctObjectiveSystems());
            assertEquals(2, result.summary().continuingRaids());
            assertEquals(1, result.summary().withdrawingRaids());
            assertEquals(2, result.summary().committedEncounters());
            assertTrue(result.summary().physicallyActiveRaidAnchors() >= 3,
                    "all three raid intents must have real local FleetId/target anchors before supply review");
            assertTrue(result.summary().physicalEffectCount() > 0,
                    "supported B06 lanes must commit at least one real ammunition/damage/destruction consequence");
            assertTrue(result.summary().withheldLaneUnchanged(),
                    "supply-denied lane must not receive hidden tactical or economic consequences");
            assertTrue(result.summary().survivingCommittedFleetCount() >= 0);
        }
    }

    private static ScenarioResult run(Permutation permutation) {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        int empireFactionId = runtime.world().findFactionRuntimeId(
                Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID).orElseThrow();
        int unionFactionId = runtime.world().findFactionRuntimeId(
                Stage22CorePairBalanceEvidence.UNION_FACTION_ID).orElseThrow();

        List<MilitaryFleet> empire = militaryFleets(runtime, empireFactionId);
        List<MilitaryFleet> union = militaryFleets(runtime, unionFactionId);
        assertEquals(GeneratedFactionMilitaryBootstrap.SHIPS_PER_FACTION, empire.size());
        assertEquals(GeneratedFactionMilitaryBootstrap.SHIPS_PER_FACTION, union.size());

        boolean empireAttacks = permutation == Permutation.DEFAULT;
        List<MilitaryFleet> attackers = empireAttacks ? empire : union;
        List<MilitaryFleet> defenders = empireAttacks ? union : empire;
        int attackerFactionId = empireAttacks ? empireFactionId : unionFactionId;

        var core = Stage22CorePairTacticalFactory.createDestroyerDuel(permutation);
        EngineeringComponent empireEngineering = engineeringFor(
                core, Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID);
        EngineeringComponent unionEngineering = engineeringFor(
                core, Stage22CorePairTacticalFactory.UNION_ENTITY_ID);
        EngineeringComponent attackerEngineering = empireAttacks ? empireEngineering : unionEngineering;
        EngineeringComponent defenderEngineering = empireAttacks ? unionEngineering : empireEngineering;

        List<StarSystemId> objectives = runtime.world().getTopology().systems().stream()
                .map(value -> value.id())
                .limit(3)
                .toList();
        if (objectives.size() != 3) {
            throw new AssertionError("B06 generated world requires at least three objective systems");
        }

        ArrayList<Lane> lanes = new ArrayList<>();
        PhysicalWarfareOperationService physicalOperations = new PhysicalWarfareOperationService(runtime.world());
        for (int laneIndex = 0; laneIndex < 3; laneIndex++) {
            MilitaryFleet attacker = attackers.get(laneIndex);
            MilitaryFleet defender = defenders.get(laneIndex);
            StarSystemId objective = objectives.get(laneIndex);
            moveFleetByOrdinaryRoute(runtime, attacker.fleetId(), objective);
            moveFleetByOrdinaryRoute(runtime, defender.fleetId(), objective);

            FleetPlacementState attackerPlacement = runtime.world().findFleet(attacker.fleetId()).orElseThrow();
            FleetPlacementState defenderPlacement = runtime.world().findFleet(defender.fleetId()).orElseThrow();
            assertEquals(objective, attackerPlacement.systemId());
            assertEquals(objective, defenderPlacement.systemId());

            Entity attackerEntity = entity(runtime, attackerPlacement);
            Entity defenderEntity = entity(runtime, defenderPlacement);
            attackerEntity.add(copy(attackerEngineering));
            defenderEntity.add(copy(defenderEngineering));
            assertEquals(attackerFactionId, attackerEntity.getComponent(FactionComponent.class).factionId);

            LocalPhysicalKinematics attackerPhysical = runtime.arrival().materialization(objective)
                    .physicalState(attackerPlacement.localEntityId()).orElseThrow();
            runtime.arrival().materialization(objective).updatePhysicalState(
                    defenderPlacement.localEntityId(),
                    new LocalPhysicalKinematics(
                            attackerPhysical.position().translated(CONTACT_SEPARATION_M, laneIndex * 35d),
                            0d,
                            0d));

            PhysicalWarfareOperation physicalRaid = PhysicalWarfareOperation.raid(
                    attacker.fleetId(), objective, defenderPlacement.localEntityId());
            if (!physicalOperations.isPhysicallyActive(physicalRaid)) {
                throw new AssertionError("B06 raid lacks a real local physical anchor: lane=" + laneIndex);
            }
            lanes.add(new Lane(
                    laneIndex,
                    attacker.fleetId(),
                    defender.fleetId(),
                    objective,
                    physicalRaid,
                    EntityStateMapper.capture(attackerEntity),
                    EntityStateMapper.capture(defenderEntity)));
        }

        FleetReadinessEvaluator evaluator = new FleetReadinessEvaluator(core.content().engineering());
        HashMap<FleetId, FleetOperationalAvailability> availability = new HashMap<>();
        for (Lane lane : lanes) {
            availability.put(
                    lane.attackerFleetId(),
                    new FleetOperationalAvailability(
                            CREW_AVAILABLE,
                            lane.index() == 2 ? 0 : FleetReadinessState.FULL));
        }
        FleetForceRegistry forces = FleetForceRegistry.reconstruct(
                runtime.world().snapshot(), evaluator, availability);

        ArrayList<OperationState> operationRows = new ArrayList<>();
        long now = runtime.world().getAuthoritativeWorldTick();
        for (Lane lane : lanes) {
            operationRows.add(operation(lane, attackerFactionId, now));
        }
        StrategicOperationState operations = new StrategicOperationState(4L, operationRows);
        StrategicOperationService strategic = new StrategicOperationService();

        LinkedHashMap<Integer, SupplyDecision> decisions = new LinkedHashMap<>();
        int physicallyActiveRaidAnchors = 0;
        int committedEncounters = 0;
        int physicalEffectCount = 0;
        for (Lane lane : lanes) {
            if (physicalOperations.isPhysicallyActive(lane.physicalRaid())) {
                physicallyActiveRaidAnchors++;
            }
            SupplyDecision decision = strategic.reviewSupplyAndReadiness(
                    operations, lane.index() + 1L, forces, now).decision();
            decisions.put(lane.index(), decision);
            if (decision == SupplyDecision.CONTINUE) {
                physicalEffectCount += commitEncounter(runtime, core, lane) ? 1 : 0;
                committedEncounters++;
            }
        }

        long continuingRaids = decisions.values().stream().filter(value -> value == SupplyDecision.CONTINUE).count();
        long withdrawingRaids = decisions.values().stream()
                .filter(value -> value == SupplyDecision.SUBMIT_ORDINARY_WITHDRAW_ORDER)
                .count();
        assertEquals(2L, continuingRaids);
        assertEquals(1L, withdrawingRaids);
        assertEquals(SupplyDecision.SUBMIT_ORDINARY_WITHDRAW_ORDER, decisions.get(2));

        Lane withheld = lanes.get(2);
        FleetPlacementState withheldAttackerPlacement = runtime.world().findFleet(
                withheld.attackerFleetId()).orElseThrow();
        FleetPlacementState withheldDefenderPlacement = runtime.world().findFleet(
                withheld.defenderFleetId()).orElseThrow();
        boolean withheldLaneUnchanged = withheld.attackerBefore().equals(EntityStateMapper.capture(
                        entity(runtime, withheldAttackerPlacement)))
                && withheld.defenderBefore().equals(EntityStateMapper.capture(
                        entity(runtime, withheldDefenderPlacement)));
        assertTrue(withheldLaneUnchanged);

        int survivingCommittedFleetCount = 0;
        for (Lane lane : lanes.subList(0, 2)) {
            if (runtime.world().findFleet(lane.attackerFleetId()).isPresent()) survivingCommittedFleetCount++;
            if (runtime.world().findFleet(lane.defenderFleetId()).isPresent()) survivingCommittedFleetCount++;
        }

        byte[] checkpoint = Stage20GeneratedWorldRuntimePersistenceCodec.encode(runtime.captureState());
        assertArrayEquals(
                checkpoint,
                Stage20GeneratedWorldRuntimePersistenceCodec.encode(
                        Stage20GeneratedWorldRuntimePersistenceCodec.decode(checkpoint)),
                "B06 post-raid generated-world state must retain byte-stable persistence");

        return new ScenarioResult(
                checkpoint,
                new Summary(
                        objectives.stream().distinct().count() == 3 ? 3 : (int) objectives.stream().distinct().count(),
                        (int) continuingRaids,
                        (int) withdrawingRaids,
                        committedEncounters,
                        physicallyActiveRaidAnchors,
                        physicalEffectCount,
                        withheldLaneUnchanged,
                        survivingCommittedFleetCount));
    }

    private static OperationState operation(Lane lane, int factionId, long tick) {
        long operationId = lane.index() + 1L;
        return new OperationState(
                operationId,
                OperationType.RAID,
                operationId,
                operationId,
                factionId,
                List.of(lane.attackerFleetId()),
                lane.objectiveSystemId(),
                lane.objectiveSystemId(),
                "system:" + lane.objectiveSystemId().value(),
                RulesOfEngagement.IDENTIFIED_HOSTILES,
                new SupplyPolicy(0, REQUIRED_SUPPLY_ACCESS_BPS, 0L),
                new WithdrawalPolicy(lane.objectiveSystemId(), 0, true, true),
                OperationStatus.ACTIVE,
                tick,
                tick,
                -1L,
                null,
                null);
    }

    private static boolean commitEncounter(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            Stage22CorePairTacticalFactory.Duel core,
            Lane lane) {
        FleetPlacementState attackerPlacement = runtime.world().findFleet(lane.attackerFleetId()).orElseThrow();
        FleetPlacementState defenderPlacement = runtime.world().findFleet(lane.defenderFleetId()).orElseThrow();
        Entity attacker = entity(runtime, attackerPlacement);
        Entity defender = entity(runtime, defenderPlacement);
        EntityState attackerBefore = EntityStateMapper.capture(attacker);
        EntityState defenderBefore = EntityStateMapper.capture(defender);
        long attackerRoundsBefore = attacker.getComponent(EngineeringComponent.class)
                .runtimeState.consumables().ammunitionCount();
        long defenderRoundsBefore = defender.getComponent(EngineeringComponent.class)
                .runtimeState.consumables().ammunitionCount();

        ArrayList<PhysicalCombatant> combatants = new ArrayList<>(List.of(
                new PhysicalCombatant(
                        lane.attackerFleetId(),
                        CombatSide.OPERATION,
                        attacker.getComponent(FactionComponent.class).factionId,
                        attackerBefore),
                new PhysicalCombatant(
                        lane.defenderFleetId(),
                        CombatSide.CONTACT,
                        defender.getComponent(FactionComponent.class).factionId,
                        defenderBefore)));
        combatants.sort(Comparator.comparing(PhysicalCombatant::fleetId));

        var resolver = new Stage19ExactTacticalEncounterResolver(
                core.content().engineering(),
                core.protection(),
                core.content().ammunition(),
                core.content().launchers());
        new Stage21EGeneratedWorldStage19Authority(runtime, resolver, TACTICAL_TICKS).materializeExact(
                new TacticalMaterializationRequest(
                        OPERATION_BASE + lane.index(),
                        lane.objectiveSystemId(),
                        runtime.world().getAuthoritativeWorldTick(),
                        List.copyOf(combatants)));

        boolean attackerAlive = runtime.world().findFleet(lane.attackerFleetId()).isPresent();
        boolean defenderAlive = runtime.world().findFleet(lane.defenderFleetId()).isPresent();
        boolean physicalEffect = !attackerAlive || !defenderAlive;
        if (attackerAlive) {
            FleetPlacementState placement = runtime.world().findFleet(lane.attackerFleetId()).orElseThrow();
            Entity current = entity(runtime, placement);
            long after = current.getComponent(EngineeringComponent.class)
                    .runtimeState.consumables().ammunitionCount();
            assertTrue(after <= attackerRoundsBefore,
                    "Stage-19 raid contact cannot refill attacker ammunition");
            physicalEffect |= !attackerBefore.equals(EntityStateMapper.capture(current));
        }
        if (defenderAlive) {
            FleetPlacementState placement = runtime.world().findFleet(lane.defenderFleetId()).orElseThrow();
            Entity current = entity(runtime, placement);
            long after = current.getComponent(EngineeringComponent.class)
                    .runtimeState.consumables().ammunitionCount();
            assertTrue(after <= defenderRoundsBefore,
                    "Stage-19 raid contact cannot refill patrol ammunition");
            physicalEffect |= !defenderBefore.equals(EntityStateMapper.capture(current));
        }
        return physicalEffect;
    }

    private static List<MilitaryFleet> militaryFleets(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            int factionId) {
        ArrayList<MilitaryFleet> result = new ArrayList<>();
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) continue;
            Entity entity = entity(runtime, placement);
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
            if (faction != null && faction.factionId == factionId && engineering != null) {
                result.add(new MilitaryFleet(placement.id(), placement.systemId()));
            }
        }
        result.sort(Comparator.comparing(MilitaryFleet::fleetId));
        return List.copyOf(result);
    }

    private static EngineeringComponent engineeringFor(
            Stage22CorePairTacticalFactory.Duel core,
            long entityId) {
        return core.weapons().battleState().combatants().stream()
                .filter(value -> value.spec().entityId() == entityId)
                .findFirst()
                .orElseThrow()
                .engineering();
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

    private static void moveFleetByOrdinaryRoute(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId,
            StarSystemId destination) {
        FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
        List<StarSystemId> route = route(runtime, placement.systemId(), destination);
        for (int index = 1; index < route.size(); index++) {
            GeneratedWorldFtlTestSupport.placeAtOutgoingEndpoint(runtime, fleetId, route.get(index));
            runtime.world().requestFleetJump(fleetId, route.get(index));
            awaitJump(runtime, fleetId);
            if (index + 1 < route.size()) awaitFittedCooldown(runtime, fleetId);
        }
    }

    private static void awaitJump(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId) {
        int phaseTransitions = 0;
        while (true) {
            var initial = runtime.world().findFleetJump(fleetId);
            if (initial.isEmpty()) return;
            var phase = initial.orElseThrow();
            long phaseStartedTick = phase.phaseStartedTick();
            long phaseDeadlineTick = phase.phaseEndsTick() + 1L;
            while (true) {
                var current = runtime.world().findFleetJump(fleetId);
                if (current.isEmpty()) return;
                var state = current.orElseThrow();
                if (state.phase() != phase.phase() || state.phaseStartedTick() != phaseStartedTick) break;
                long worldTick = runtime.world().getAuthoritativeWorldTick();
                if (worldTick > phaseDeadlineTick) {
                    throw new AssertionError("ordinary B06 FTL phase exceeded phaseEndsTick: " + state);
                }
                runtime.advanceFrame(SIMULATION_WAIT_FRAME_SECONDS);
            }
            phaseTransitions++;
            if (phaseTransitions > 8) {
                throw new AssertionError("ordinary B06 FTL jump exceeded bounded canonical phase transitions");
            }
        }
    }

    private static void awaitFittedCooldown(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId) {
        for (int attempt = 0; attempt < MAX_COOLDOWN_WAIT_FRAMES; attempt++) {
            FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
            EngineeringComponent engineering = entity(runtime, placement).getComponent(EngineeringComponent.class);
            if (engineering == null || engineering.runtimeState.ftlCooldownSecondsByMount().values().stream()
                    .noneMatch(value -> value > 0d)) {
                return;
            }
            runtime.advanceFrame(SIMULATION_WAIT_FRAME_SECONDS);
        }
        throw new AssertionError("B06 generated military FTL cooldown did not clear");
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
        throw new AssertionError("generated topology has no B06 military route");
    }

    private record MilitaryFleet(FleetId fleetId, StarSystemId initialSystemId) { }

    private record Lane(
            int index,
            FleetId attackerFleetId,
            FleetId defenderFleetId,
            StarSystemId objectiveSystemId,
            PhysicalWarfareOperation physicalRaid,
            EntityState attackerBefore,
            EntityState defenderBefore) { }

    private record Summary(
            int distinctObjectiveSystems,
            int continuingRaids,
            int withdrawingRaids,
            int committedEncounters,
            int physicallyActiveRaidAnchors,
            int physicalEffectCount,
            boolean withheldLaneUnchanged,
            int survivingCommittedFleetCount) { }

    private record ScenarioResult(byte[] checkpoint, Summary summary) {
        private ScenarioResult {
            checkpoint = Arrays.copyOf(checkpoint, checkpoint.length);
        }
    }
}
