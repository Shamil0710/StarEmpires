package com.spacesim.world;

import com.spacesim.components.WalletComponent;
import com.spacesim.persistence.Stage20FreightPersistentState.TransportOrderState;
import com.spacesim.world.Stage21HNpcMissionState.KnowledgeKind;
import com.spacesim.world.Stage21HNpcMissionState.MissionContract;
import com.spacesim.world.Stage21HNpcMissionState.MissionObjective;
import com.spacesim.world.Stage21HNpcMissionState.MissionStatus;
import com.spacesim.world.Stage21HNpcMissionState.MissionTemplate;
import com.spacesim.world.Stage21HNpcMissionState.NpcAvailability;
import com.spacesim.world.Stage21HNpcMissionState.NpcKnowledgeFact;
import com.spacesim.world.Stage21HNpcMissionState.NpcRole;
import com.spacesim.world.Stage21HNpcMissionState.NpcState;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveAuthority;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveKind;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage21HMissionOpportunityCausalityTest {

    @Test
    void unrelatedCurrentFactCannotGroundRealSupplyMissionAndEscrowRollsBack() {
        var runtime = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        var saved = runtime.captureState();
        TransportOrderState order = saved.freight().orders().get(0);
        String faction = order.stableFactionId();
        long tick = runtime.world().getAuthoritativeWorldTick();
        ensureSpendable(runtime.world(), faction, 1_000L);
        long treasuryBefore = runtime.world().findFactionEconomicState(faction).orElseThrow()
                .treasuryMilliCredits();

        NpcKnowledgeFact unrelated = new NpcKnowledgeFact(
                "fact.security.unrelated",
                "border.test",
                KnowledgeKind.ACTOR_OBSERVATION,
                "SECURITY.BORDER_SECURITY",
                8_000,
                "report.security.unrelated",
                tick,
                -1L);
        NpcState npc = new NpcState(
                "npc.test.causality",
                "npc.test.causality.name",
                NpcRole.TRADE_LOGISTICS,
                faction,
                order.orderedSystems().get(0),
                NpcAvailability.AVAILABLE,
                List.of(unrelated));
        Stage21HNpcMissionService service = new Stage21HNpcMissionService(new Stage21HNpcMissionState(
                Stage21HNpcMissionState.CURRENT_VERSION,
                tick,
                1L,
                List.of(npc),
                List.of(),
                List.of(),
                List.of()));
        MissionObjective objective = new MissionObjective(
                ObjectiveAuthority.FREIGHT,
                ObjectiveKind.FREIGHT_ORDER_DELIVERED_KG_AT_LEAST,
                order.orderId(),
                0L,
                (long) Math.floor(order.deliveredMassKg()) + 1L,
                "");

        assertThrows(IllegalArgumentException.class, () -> service.offerMission(
                runtime.world(),
                saved.freight(),
                saved.campaign().industrialState(),
                saved.campaign().discoveryState().knowledgeFor(faction),
                StrategicOperationState.empty(),
                npc.npcId(),
                MissionTemplate.EMERGENCY_SUPPLY_DELIVERY,
                objective,
                List.of(unrelated.factId()),
                tick + 100L,
                1_000L));

        assertEquals(treasuryBefore,
                runtime.world().findFactionEconomicState(faction).orElseThrow().treasuryMilliCredits());
        assertTrue(service.snapshot().missions().isEmpty());
        assertEquals(1L, service.snapshot().nextMissionSequence());
    }

    @Test
    void persistedMissionRequiresCausallyCompatibleIssuerKnownFact() {
        NpcKnowledgeFact unrelated = new NpcKnowledgeFact(
                "fact.unrelated",
                "border.test",
                KnowledgeKind.ACTOR_OBSERVATION,
                "SECURITY.BORDER_SECURITY",
                5_000,
                "report.unrelated",
                1L,
                -1L);
        NpcState npc = new NpcState(
                "npc.test.persisted-causality",
                "npc.test.persisted-causality.name",
                NpcRole.TRADE_LOGISTICS,
                "faction.test",
                new StarSystemId(1L),
                NpcAvailability.AVAILABLE,
                List.of(unrelated));
        MissionContract mission = new MissionContract(
                "mission.stage21h.1",
                MissionTemplate.EMERGENCY_SUPPLY_DELIVERY,
                1,
                npc.npcId(),
                npc.factionContentId(),
                List.of(unrelated.factId()),
                new MissionObjective(
                        ObjectiveAuthority.FREIGHT,
                        ObjectiveKind.FREIGHT_ORDER_DELIVERED_KG_AT_LEAST,
                        "order.test",
                        0L,
                        10L,
                        ""),
                2L,
                20L,
                MissionStatus.OFFERED,
                2L,
                100L,
                100L,
                "",
                List.of());

        assertThrows(IllegalArgumentException.class, () -> new Stage21HNpcMissionState(
                Stage21HNpcMissionState.CURRENT_VERSION,
                10L,
                2L,
                List.of(npc),
                List.of(mission),
                List.of(),
                List.of()));
    }

    @Test
    void selfEscortIsRejectedByPersistentObjectiveShapeBeforeRuntimeEvaluation() {
        assertThrows(IllegalArgumentException.class, () -> new MissionObjective(
                ObjectiveAuthority.FLEET,
                ObjectiveKind.ESCORT_FLEETS_PRESENT_IN_SYSTEM,
                "7",
                2L,
                0L,
                "7"));
    }

    private static void ensureSpendable(WorldSimulation world, String faction, long required) {
        FactionEconomicState economy = world.findFactionEconomicState(faction).orElseThrow();
        long spendable = Math.max(0L,
                economy.treasuryMilliCredits() - economy.treasuryReserveFloorMilliCredits());
        if (spendable >= required) {
            return;
        }
        long amount = required - spendable;
        WalletComponent source = new WalletComponent(amount);
        assertTrue(world.transferToFactionTreasury(
                faction,
                source,
                "stage21h-causality-fixture",
                amount,
                "stage21h-causality-fixture"));
    }
}
