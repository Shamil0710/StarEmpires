package com.spacesim.world.generation;

import com.spacesim.components.WalletComponent;
import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.LiveRuntime;
import com.spacesim.persistence.Stage21AGeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage21BGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21CGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21DGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21EGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21FGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21GGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21HGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21HGeneratedWorldRuntimePersistentState;
import com.spacesim.warfare.Stage19ConflictRuntime;
import com.spacesim.world.DiplomaticLifecycleState;
import com.spacesim.world.FactionEconomicState;
import com.spacesim.world.FactionStrategicIntentState;
import com.spacesim.world.FleetCommandState;
import com.spacesim.world.SettlementRecoveryState;
import com.spacesim.world.Stage21HNpcMissionService;
import com.spacesim.world.Stage21HNpcMissionState;
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
import com.spacesim.world.StrategicOperationState;
import com.spacesim.world.TerritorialTransitionState;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage21HGeneratedWorldRuntimePersistenceAcceptanceTest {
    private static final long REWARD = 2_000L;

    @Test
    void activeFundedMissionRoundTripsDeterministicallyOverCompleteStage21GCheckpoint() {
        Fixture fixture = fixture();
        Stage21HGeneratedWorldRuntimePersistentState expected = Stage21HGeneratedWorldRuntimePersistentState.compose(
                fixture.stage21G(), fixture.service().snapshot());

        byte[] first = Stage21HGeneratedWorldRuntimePersistenceCodec.encode(expected);
        Stage21HGeneratedWorldRuntimePersistentState decoded =
                Stage21HGeneratedWorldRuntimePersistenceCodec.decode(first);
        byte[] second = Stage21HGeneratedWorldRuntimePersistenceCodec.encode(decoded);

        assertArrayEquals(first, second);
        assertEquals(fixture.stage21G(), decoded.stage21GRuntime());
        assertEquals(fixture.service().snapshot(), decoded.npcMissionState());
        assertEquals(MissionStatus.OFFERED, decoded.npcMissionState().missions().get(0).status());
        assertEquals(REWARD, decoded.npcMissionState().missions().get(0).escrowMilliCredits());
    }

    @Test
    void activeEscrowRequiresExactLowerWorldTreasuryFundingProvenance() {
        Fixture fixture = fixture();
        Stage21HNpcMissionState original = fixture.service().snapshot();
        MissionContract mission = original.missions().get(0);
        MissionContract forged = new MissionContract(
                mission.missionId(), mission.template(), mission.templateVersion(),
                mission.issuerNpcId(), mission.issuerFactionId(), mission.sourceKnowledgeFactIds(),
                mission.objective(), mission.createdTick(), mission.deadlineTick(), mission.status(),
                mission.statusUpdatedTick(), mission.rewardMilliCredits() + 1L,
                mission.escrowMilliCredits() + 1L, mission.outcomeCode(), mission.pendingWakeups());
        Stage21HNpcMissionState forgedState = new Stage21HNpcMissionState(
                Stage21HNpcMissionState.CURRENT_VERSION,
                original.simulationTick(), original.nextMissionSequence(), original.npcs(),
                List.of(forged), original.reputations(), original.storyChains());

        assertThrows(IllegalArgumentException.class, () ->
                Stage21HGeneratedWorldRuntimePersistentState.compose(fixture.stage21G(), forgedState));
    }

    @Test
    void unknownNpcFactionFutureRpgTimeAndCorruptTopLevelPayloadFailClosed() {
        Fixture fixture = fixture();
        Stage21HNpcMissionState original = fixture.service().snapshot();
        NpcState npc = original.npcs().get(0);
        NpcState unknown = new NpcState(
                npc.npcId(), npc.nameKey(), npc.role(), "faction.missing.stage21h",
                npc.locationSystemId(), npc.availability(), npc.knowledge());
        Stage21HNpcMissionState unknownFaction = new Stage21HNpcMissionState(
                Stage21HNpcMissionState.CURRENT_VERSION,
                original.simulationTick(), original.nextMissionSequence(),
                List.of(unknown), List.of(), List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () ->
                Stage21HGeneratedWorldRuntimePersistentState.compose(fixture.stage21G(), unknownFaction));

        Stage21HNpcMissionState future = Stage21HNpcMissionState.empty(fixture.now() + 1L);
        assertThrows(IllegalArgumentException.class, () ->
                Stage21HGeneratedWorldRuntimePersistentState.compose(fixture.stage21G(), future));

        byte[] valid = Stage21HGeneratedWorldRuntimePersistenceCodec.encode(
                Stage21HGeneratedWorldRuntimePersistentState.compose(
                        fixture.stage21G(), fixture.service().snapshot()));
        byte[] futureFile = valid.clone();
        ByteBuffer.wrap(futureFile).putInt(4, 99);
        assertThrows(IllegalArgumentException.class, () ->
                Stage21HGeneratedWorldRuntimePersistenceCodec.decode(futureFile));
        byte[] futureSchema = valid.clone();
        ByteBuffer.wrap(futureSchema).putInt(8, Stage21HGeneratedWorldRuntimePersistentState.CURRENT_VERSION + 1);
        assertThrows(IllegalArgumentException.class, () ->
                Stage21HGeneratedWorldRuntimePersistenceCodec.decode(futureSchema));
        byte[] corruptMagic = valid.clone();
        corruptMagic[0] ^= 0x6a;
        assertThrows(IllegalArgumentException.class, () ->
                Stage21HGeneratedWorldRuntimePersistenceCodec.decode(corruptMagic));
        assertThrows(IllegalArgumentException.class, () ->
                Stage21HGeneratedWorldRuntimePersistenceCodec.decode(Arrays.copyOf(valid, valid.length - 1)));
        assertThrows(IllegalArgumentException.class, () ->
                Stage21HGeneratedWorldRuntimePersistenceCodec.decode(Arrays.copyOf(valid, valid.length + 1)));
    }

    private static Fixture fixture() {
        LiveRuntime stage20 = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        var initial = stage20.captureState();
        var order = initial.freight().orders().get(0);
        String issuer = order.stableFactionId();
        String second = initial.worldState().factions().stream()
                .map(FactionEconomicState::factionContentId)
                .filter(value -> !value.equals(issuer))
                .findFirst().orElseThrow();
        long now = stage20.world().getAuthoritativeWorldTick();
        ensureSpendable(stage20, issuer, REWARD);

        NpcKnowledgeFact fact = new NpcKnowledgeFact(
                "fact.stage21h.persistence",
                order.orderId(),
                KnowledgeKind.ACTOR_OBSERVATION,
                "ECONOMIC.RESOURCE_DEFICIT",
                8000,
                "report.stage21h.persistence",
                now,
                -1L);
        NpcState npc = new NpcState(
                "npc.stage21h.persistence",
                "npc.stage21h.persistence.name",
                NpcRole.TRADE_LOGISTICS,
                issuer,
                order.orderedSystems().get(0),
                NpcAvailability.AVAILABLE,
                List.of(fact));
        Stage21HNpcMissionService service = new Stage21HNpcMissionService(new Stage21HNpcMissionState(
                Stage21HNpcMissionState.CURRENT_VERSION,
                now, 1L, List.of(npc), List.of(), List.of(), List.of()));
        long threshold = (long) Math.floor(order.deliveredMassKg()) + 1L;
        MissionObjective objective = new MissionObjective(
                ObjectiveAuthority.FREIGHT,
                ObjectiveKind.FREIGHT_ORDER_DELIVERED_KG_AT_LEAST,
                order.orderId(), 0L, threshold, "");
        service.offerMission(
                stage20.world(), initial.freight(), initial.campaign().industrialState(),
                initial.campaign().discoveryState().knowledgeFor(issuer), StrategicOperationState.empty(),
                npc.npcId(), MissionTemplate.EMERGENCY_SUPPLY_DELIVERY, objective,
                List.of(fact.factId()), now + 100L, REWARD);

        Stage21BGeneratedWorldRuntimePersistentState stage21B = stage21B(stage20, issuer, second);
        Stage19ConflictRuntime warfare = new Stage19ConflictRuntime(Stage19ConflictState.empty(now));
        Stage21CGeneratedWorldRuntimePersistentState stage21C = new Stage21CGeneratedWorldRuntimePersistentState(
                Stage21CGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                Stage21CGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                stage21B,
                DiplomaticLifecycleState.empty(now),
                warfare.snapshot());
        Stage21DGeneratedWorldRuntimePersistentState stage21D = Stage21DGeneratedWorldRuntimePersistentState.compose(
                stage21C, FleetCommandState.empty());
        Stage21EGeneratedWorldRuntimePersistentState stage21E = Stage21EGeneratedWorldRuntimePersistentState.compose(
                stage21D, StrategicOperationState.empty());
        Stage21FGeneratedWorldRuntimePersistentState stage21F = Stage21FGeneratedWorldRuntimePersistentState.compose(
                stage21E, TerritorialTransitionState.empty());
        Stage21GGeneratedWorldRuntimePersistentState stage21G = Stage21GGeneratedWorldRuntimePersistentState.compose(
                stage21F, SettlementRecoveryState.empty(now));
        return new Fixture(stage21G, service, now);
    }

    private static Stage21BGeneratedWorldRuntimePersistentState stage21B(
            LiveRuntime stage20,
            String first,
            String second) {
        var stage21A = Stage21AGeneratedWorldRuntimeBridge.materializeBootstrap(
                stage20, List.of(first, second), 30L);
        return new Stage21BGeneratedWorldRuntimePersistentState(
                Stage21BGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                Stage21BGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                stage21A.captureState(),
                List.of(
                        FactionStrategicIntentState.initial(first),
                        FactionStrategicIntentState.initial(second)));
    }

    private static void ensureSpendable(LiveRuntime stage20, String faction, long required) {
        FactionEconomicState economy = stage20.world().findFactionEconomicState(faction).orElseThrow();
        long spendable = Math.max(0L,
                economy.treasuryMilliCredits() - economy.treasuryReserveFloorMilliCredits());
        if (spendable >= required) {
            return;
        }
        long amount = required - spendable;
        WalletComponent source = new WalletComponent(amount);
        assertTrue(stage20.world().transferToFactionTreasury(
                faction, source, "stage21h-persistence-fixture", amount, "stage21h-persistence-fixture"));
    }

    private record Fixture(
            Stage21GGeneratedWorldRuntimePersistentState stage21G,
            Stage21HNpcMissionService service,
            long now) { }
}
