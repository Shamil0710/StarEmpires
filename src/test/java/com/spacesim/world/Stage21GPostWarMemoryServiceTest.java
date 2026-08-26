package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.warfare.Stage19ConflictRuntime;
import com.spacesim.world.DiplomaticLifecycleState.ProposalKind;
import com.spacesim.world.DiplomaticLifecycleState.RelationFactor;
import com.spacesim.world.DiplomaticLifecycleState.WarGoal;
import com.spacesim.world.DiplomaticLifecycleState.WarGoalKind;
import com.spacesim.world.DiplomaticLifecycleState.WarStatus;
import com.spacesim.world.SettlementRecoveryState.FleetLossRecord;
import com.spacesim.world.SettlementRecoveryState.Settlement;
import com.spacesim.world.SettlementRecoveryState.SettlementStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage21GPostWarMemoryServiceTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";
    private static final String MINERS = "faction.miners";

    @Test
    void completedPeaceKeepsStage21CCooldownAndRecordsBoundedLossGrievanceExactlyOnce() {
        WorldSimulation world = DemoGalaxyFactory.create(21_795L);
        DiplomaticLifecycleService diplomacy = diplomacy(world);
        var war = diplomacy.declareWarFromObservedAttack(
                TRADE_LEAGUE, MINERS, "observed.attack.memory.21g",
                world.getAuthoritativeWorldTick(), goals());
        long now = world.getAuthoritativeWorldTick();
        var peace = diplomacy.propose(new DiplomaticLifecycleService.ProposalRequest(
                "goal.peace.memory.21g", TRADE_LEAGUE, MINERS, ProposalKind.PEACE, war.warId(),
                List.of(), List.of(), now + 100L));
        peace = diplomacy.accept(peace.proposalId());
        var acceptedWar = diplomacy.snapshot().wars().stream()
                .filter(candidate -> candidate.warId().equals(war.warId()))
                .findFirst().orElseThrow();
        assertEquals(WarStatus.PEACE, acceptedWar.status());
        assertTrue(acceptedWar.reEscalationCooldownUntilTick()
                >= acceptedWar.statusChangedTick() + DiplomaticLifecycleService.MINIMUM_REESCALATION_COOLDOWN_TICKS);

        Settlement settlement = new Settlement(
                1L, peace.proposalId(), war.warId(), TRADE_LEAGUE, MINERS,
                now, now, SettlementStatus.COMPLETE, false);
        SettlementRecoveryState state = new SettlementRecoveryState(
                SettlementRecoveryState.CURRENT_VERSION,
                now,
                2L,
                1L,
                List.of(settlement),
                List.of(),
                List.of(),
                List.of(
                        new FleetLossRecord(1L, 101L, new FleetId(21_795_001L), TRADE_LEAGUE, now),
                        new FleetLossRecord(1L, 101L, new FleetId(21_795_002L), TRADE_LEAGUE, now)),
                List.of());
        SettlementRecoveryService recovery = new SettlementRecoveryService(state);
        Stage21GPostWarMemoryService memory = new Stage21GPostWarMemoryService();

        memory.recordPostWarMemory(recovery, diplomacy, settlement.id(), now);

        assertTrue(recovery.snapshot().requireSettlement(settlement.id()).memoryRecorded());
        assertEquals(12 - 2 * Stage21GPostWarMemoryService.GRIEVANCE_PER_LOST_FLEET,
                diplomacy.derivedRelation(TRADE_LEAGUE, MINERS));
        assertEquals(12, diplomacy.derivedRelation(MINERS, TRADE_LEAGUE));
        assertEquals(1L, diplomacy.snapshot().relationMemories().stream()
                .filter(row -> row.ownerFactionId().equals(TRADE_LEAGUE)
                        && row.targetFactionId().equals(MINERS))
                .flatMap(row -> row.events().stream())
                .filter(event -> event.factor() == RelationFactor.REMEMBERED_ACTION
                        && event.subjectId().equals(war.warId()))
                .count());

        int relationAfterFirstPass = diplomacy.derivedRelation(TRADE_LEAGUE, MINERS);
        int eventCountAfterFirstPass = diplomacy.snapshot().relationMemories().stream()
                .mapToInt(row -> row.events().size()).sum();
        memory.recordPostWarMemory(recovery, diplomacy, settlement.id(), now);

        assertEquals(relationAfterFirstPass, diplomacy.derivedRelation(TRADE_LEAGUE, MINERS));
        assertEquals(eventCountAfterFirstPass, diplomacy.snapshot().relationMemories().stream()
                .mapToInt(row -> row.events().size()).sum());
        var afterMemoryWar = diplomacy.snapshot().wars().stream()
                .filter(candidate -> candidate.warId().equals(war.warId()))
                .findFirst().orElseThrow();
        assertEquals(acceptedWar.reEscalationCooldownUntilTick(), afterMemoryWar.reEscalationCooldownUntilTick());
    }

    @Test
    void manyPhysicalLossesAreCappedInsteadOfCreatingUnboundedRelationCurrency() {
        WorldSimulation world = DemoGalaxyFactory.create(21_796L);
        DiplomaticLifecycleService diplomacy = diplomacy(world);
        var war = diplomacy.declareWarFromObservedAttack(
                TRADE_LEAGUE, MINERS, "observed.attack.memory.cap.21g",
                world.getAuthoritativeWorldTick(), goals());
        long now = world.getAuthoritativeWorldTick();
        var peace = diplomacy.propose(new DiplomaticLifecycleService.ProposalRequest(
                "goal.peace.memory.cap.21g", TRADE_LEAGUE, MINERS, ProposalKind.PEACE, war.warId(),
                List.of(), List.of(), now + 100L));
        peace = diplomacy.accept(peace.proposalId());
        Settlement settlement = new Settlement(
                1L, peace.proposalId(), war.warId(), TRADE_LEAGUE, MINERS,
                now, now, SettlementStatus.COMPLETE, false);
        List<FleetLossRecord> losses = java.util.stream.LongStream.range(1L, 20L)
                .mapToObj(value -> new FleetLossRecord(
                        1L, 202L, new FleetId(21_796_000L + value), TRADE_LEAGUE, now))
                .toList();
        SettlementRecoveryService recovery = new SettlementRecoveryService(new SettlementRecoveryState(
                SettlementRecoveryState.CURRENT_VERSION,
                now,
                2L,
                1L,
                List.of(settlement),
                List.of(),
                List.of(),
                losses,
                List.of()));

        new Stage21GPostWarMemoryService().recordPostWarMemory(recovery, diplomacy, 1L, now);

        assertEquals(12 - Stage21GPostWarMemoryService.MAX_GRIEVANCE,
                diplomacy.derivedRelation(TRADE_LEAGUE, MINERS));
    }

    private static DiplomaticLifecycleService diplomacy(WorldSimulation world) {
        return new DiplomaticLifecycleService(
                world,
                new Stage19ConflictRuntime(Stage19ConflictState.empty(world.getAuthoritativeWorldTick())),
                DiplomaticLifecycleState.empty(world.getAuthoritativeWorldTick()));
    }

    private static List<WarGoal> goals() {
        return List.of(
                new WarGoal("goal.tl.memory.21g", TRADE_LEAGUE, WarGoalKind.SECURITY, "security.tl", true),
                new WarGoal("goal.miners.memory.21g", MINERS, WarGoalKind.SECURITY, "security.miners", true));
    }
}
