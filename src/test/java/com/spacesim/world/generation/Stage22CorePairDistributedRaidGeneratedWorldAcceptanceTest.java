package com.spacesim.world.generation;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.FactionComponent;
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
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 B06 generated-world integration for distributed low-intensity raids.
 *
 * <p>The generated campaign already commissions three ordinary physical military FleetIds per
 * generated faction. This acceptance keeps those generated faction identities authoritative rather
 * than pretending that the Stage-20 representative {@code faction.alpha}/{@code faction.beta}
 * placement contract has already migrated to Stage-22 core identities. The two deterministic
 * generated faction slots are used only as mirrored fixture carriers for the exact Empire and
 * Industrial Union engineering packages.</p>
 *
 * <p>Each attacker/defender pair moves through the ordinary generated-world FTL path into a distinct
 * objective system. Exact Stage-22 engineering fits are installed only after physical arrival at the
 * same Stage-21E tactical-admission seam already exercised by B01/B08. Three simultaneous RAID rows
 * are then reviewed by the ordinary Stage-21E supply/readiness service: two retain observed physical
 * supply access and may commit exact Stage-19 encounters; the third loses supply access, must submit
 * the ordinary withdrawal decision and is proven physically unchanged while the supported lanes
 * fight.</p>
 *
 * <p>No raid damage scalar, abstract patrol score, hidden income penalty, generated-faction identity
 * rewrite or faction-specific combat bonus is introduced. Exact-core inter-system projection remains
 * owned by B10; this test intentionally preserves the existing provisional generated-world FTL
 * engineering until arrival instead of manufacturing a second travel authority.</p>
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
            assertEquals(2, result.summary().generatedFactionSlots());
            assertEquals(3, result.summary().distinctObjectiveSystems());
            assertEquals(2, result.summary().continuingRaids());
            assertEquals(1, result.summary().withdrawingRaids());
            assertEquals(2, result.summary().committedEncounters());
            assertEquals(3, result.summary().physicallyActiveRaidAnchors());
            assertTrue(result.summary().physicalEffectCount() > 0,
                    "supported B06 lanes must commit real ammunition/damage/destruction consequences");
            assertTrue(result.summary().withheldLaneUnchanged(),
                    "supply-denied lane must not receive hidden tactical or economic consequences");
            assertTrue(result.summary().survivingCommittedFleetCount() >= 0
                            && result.summary().survivingCommittedFleetCount() <= 4,
                    "two committed lanes can retain at most their four original physical FleetIds");
        }
    }

    private static ScenarioResult run(Permutation permutation) {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        List<FactionFleetGroup> generatedGroups = generatedMilitaryGroups(runtime);
        assertEquals(2, generatedGroups.size(),
                "current representative generated-world contract must expose two deterministic faction slots");
        for (FactionFleetGroup group : generatedGroups) {
            assertEquals(GeneratedFactionMilitaryBootstrap.SHIPS_PER_FACTION, group.fleets().size(),
                    "each generated faction must retain the ordinary three-ship military bootstrap");
        }

        FactionFleetGroup empireSlot = generatedGroups.get(0);
        FactionFleetGroup unionSlot = generatedGroups.get(1);
        boolean empireAttacks = permutation == Permutation.DEFAULT;
        FactionFleetGroup attackerGroup = empireAttacks ? empireSlot : unionSlot;
        FactionFleetGroup defenderGroup = empireAttacks ? unionSlot : empireSlot;

        var core = Stage22CorePairTacticalFactory.createDestroyerDuel(permutation);
        EngineeringComponent empireEngineering = engineeringFor(
                core, Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID);
        EngineeringComponent unionEngineering = engineeringFor(
                core, Stage22CorePairTacticalFactory.UNION_ENTITY_ID);

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
            MilitaryFleet empireFleet = empireSlot.fleets().get(laneIndex);
            MilitaryFleet unionFleet = unionSlot.fleets().get(laneIndex);
            MilitaryFleet attacker = attackerGroup.fleets().get(laneIndex);
            MilitaryFleet defender = defenderGroup.fleets().get(laneIndex);
            StarSystemId objective = objectives.get(laneIndex);

            moveFleetByOrdinaryRoute(runtime, empireFleet.fleetId(), objective);
            moveFleetByOrdinaryRoute(runtime, unionFleet.fleetId(), objective);

            FleetPlacementState empirePlacement = runtime.world().findFleet(empireFleet.fleetId()).orElseThrow();
            FleetPlacementState unionPlacement = runtime.world().findFleet(unionFleet.fleetId()).orElseThrow();
            assertEquals(objective, empirePlacement.systemId());
            assertEquals(objective, unionPlacement.systemId());

            Entity empireEntity = entity(runtime, empirePlacement);
            Entity unionEntity = entity(runtime, unionPlacement);
            int empireGeneratedFaction = empireEntity.getComponent(FactionComponent.class).factionId;
            int unionGeneratedFaction = unionEntity.getComponent(FactionComponent.class).factionId;
            empireEntity.add(copy(empireEngineering));
            unionEntity.add(copy(unionEngineering));
            assertEquals(empireGeneratedFaction, empireEntity.getComponent(FactionComponent.class).factionId,
                    "installing an exact core fit must not rewrite generated-world faction identity");
            assertEquals(unionGeneratedFaction, unionEntity.getComponent(FactionComponent.class).factionId,
                    "installing an exact core fit must not rewrite generated-world faction identity");

            FleetPlacementState attackerPlacement = runtime.world().findFleet(attacker.fleetId()).orElseThrow();
            FleetPlacementState defenderPlacement = runtime.world().findFleet(defender.fleetId()).orElseThrow();
            Entity attackerEntity = entity(runtime, attackerPlacement);
            Entity defenderEntity = entity(runtime, defenderPlacement);
            assertEquals(attackerGroup.runtimeFactionId(),
                    attackerEntity.getComponent(FactionComponent.class).factionId);
            assertEquals(defenderGroup.runtimeFactionId(),
                    defenderEntity.getComponent(FactionComponent.class).factionId);

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
            assertTrue(physicalOperations.isPhysicallyActive(physicalRaid),
                    "B06 raid requires a real local combat FleetId and a real local target entity");
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
            operationRows.add(operation(lane, attackerGroup.runtimeFactionId(), now));
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
                if (commitEncounter(runtime, core, lane)) {
                    physicalEffectCount++;
                }
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
                        generatedGroups.size(),
                        (int) objectives.stream().distinct().count(),
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
            long roundsAfter = current.getComponent(EngineeringComponent.class)
                    .runtimeState.consumables().ammunitionCount();
            assertTrue(roundsAfter <= attackerRoundsBefore,
                    "Stage-19 raid contact cannot refill attacker ammunition");
            physicalEffect |= !attackerBefore.equals(EntityStateMapper.capture(current));
        }
        if (defenderAlive) {
            FleetPlacementState placement = runtime.world().findFleet(lane.defenderFleetId()).orElseThrow();
            Entity current = entity(runtime, placement);
            long roundsAfter = current.getComponent(EngineeringComponent.class)
                    .runtimeState.consumables().ammunitionCount();
            assertTrue(roundsAfter <= defenderRoundsBefore,
                    "Stage-19 raid contact cannot refill patrol ammunition");
            physicalEffect |= !defenderBefore.equals(EntityStateMapper.capture(current));
        }
        return physicalEffect;
    }

    private static List<FactionFleetGroup> generatedMilitaryGroups(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime) {
        TreeMap<Integer, ArrayList<MilitaryFleet>> byFaction = new TreeMap<>();
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) continue;
            Entity entity = entity(runtime, placement);
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
            if (faction == null || engineering == null) continue;
            byFaction.computeIfAbsent(faction.factionId, ignored -> new ArrayList<>())
                    .add(new MilitaryFleet(placement.id()));
        }
        ArrayList<FactionFleetGroup> result = new ArrayList<>();
        for (Map.Entry<Integer, ArrayList<MilitaryFleet>> entry : byFaction.entrySet()) {
            entry.getValue().sort(Comparator.comparing(MilitaryFleet::fleetId));
            result.add(new FactionFleetGroup(entry.getKey(), List.copyOf(entry.getValue())));
        }
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

    private record MilitaryFleet(FleetId fleetId) { }

    private record FactionFleetGroup(int runtimeFactionId, List<MilitaryFleet> fleets) {
        private FactionFleetGroup {
            fleets = List.copyOf(fleets);
        }
    }

    private record Lane(
            int index,
            FleetId attackerFleetId,
            FleetId defenderFleetId,
            StarSystemId objectiveSystemId,
            PhysicalWarfareOperation physicalRaid,
            EntityState attackerBefore,
            EntityState defenderBefore) { }

    private record Summary(
            int generatedFactionSlots,
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
