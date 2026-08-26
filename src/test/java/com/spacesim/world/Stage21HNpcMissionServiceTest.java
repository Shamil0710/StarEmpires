package com.spacesim.world;

import com.spacesim.components.WalletComponent;
import com.spacesim.economy.EconomicTransaction.Type;
import com.spacesim.persistence.Stage18IndustrialState;
import com.spacesim.persistence.Stage20FreightPersistentState;
import com.spacesim.persistence.Stage20FreightPersistentState.TransportOrderState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistentState;
import com.spacesim.world.Stage21HNpcMissionState.KnowledgeKind;
import com.spacesim.world.Stage21HNpcMissionState.MissionContract;
import com.spacesim.world.Stage21HNpcMissionState.MissionObjective;
import com.spacesim.world.Stage21HNpcMissionState.MissionStatus;
import com.spacesim.world.Stage21HNpcMissionState.MissionTemplate;
import com.spacesim.world.Stage21HNpcMissionState.MissionWakeup;
import com.spacesim.world.Stage21HNpcMissionState.NpcAvailability;
import com.spacesim.world.Stage21HNpcMissionState.NpcKnowledgeFact;
import com.spacesim.world.Stage21HNpcMissionState.NpcRole;
import com.spacesim.world.Stage21HNpcMissionState.NpcState;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveAuthority;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveKind;
import com.spacesim.world.Stage21HNpcMissionState.ReputationEvent;
import com.spacesim.world.Stage21HNpcMissionState.ReputationEventKind;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage21HNpcMissionServiceTest {
    private static final long REWARD = 1_000L;

    @Test
    void offerFundsExactEscrowAndRejectRestoresTreasury() {
        Fixture fixture = fixture();
        ensureSpendable(fixture.world(), fixture.order().stableFactionId(), REWARD);
        long before = treasury(fixture.world(), fixture.order().stableFactionId());
        Stage21HNpcMissionService service = service(fixture);

        MissionContract offered = offer(service, fixture);
        assertEquals(before - REWARD, treasury(fixture.world(), fixture.order().stableFactionId()));
        assertEquals(REWARD, offered.escrowMilliCredits());

        MissionContract rejected = service.rejectMission(fixture.world(), offered.missionId());
        assertEquals(MissionStatus.REJECTED, rejected.status());
        assertEquals(0L, rejected.escrowMilliCredits());
        assertEquals(before, treasury(fixture.world(), fixture.order().stableFactionId()));
        assertNoStage21HMoneySourceOrSink(fixture.world());
    }

    @Test
    void acceptedPhysicalDeliveryPaysExactEscrowAndCreatesObservedContractReputation() {
        Fixture fixture = fixture();
        ensureSpendable(fixture.world(), fixture.order().stableFactionId(), REWARD);
        Stage21HNpcMissionService service = service(fixture);
        MissionContract offered = offer(service, fixture);
        service.acceptMission(offered.missionId(), fixture.world().getAuthoritativeWorldTick());
        long threshold = offered.objective().threshold();
        Stage20FreightPersistentState delivered = withDeliveredMass(
                fixture.freight(), fixture.order().orderId(), threshold);
        WalletComponent playerWallet = new WalletComponent();

        MissionContract completed = service.reconcileMission(
                fixture.world(), delivered, fixture.industry(), fixture.discovery(),
                StrategicOperationState.empty(), offered.missionId(), playerWallet, "actor.player");

        assertEquals(MissionStatus.COMPLETED, completed.status());
        assertEquals(REWARD, playerWallet.getBalanceMilliCredits());
        assertEquals(0L, completed.escrowMilliCredits());
        assertEquals(10, service.snapshot().reputations().get(0).derivedValue());
        assertNoStage21HMoneySourceOrSink(fixture.world());
    }

    @Test
    void unresolvedOfferDoesNotOwnWorldOutcomeAndWorldSolvedOpportunityRefundsIt() {
        Fixture fixture = fixture();
        ensureSpendable(fixture.world(), fixture.order().stableFactionId(), REWARD);
        long before = treasury(fixture.world(), fixture.order().stableFactionId());
        Stage21HNpcMissionService service = service(fixture);
        MissionContract offered = offer(service, fixture);
        Stage20FreightPersistentState delivered = withDeliveredMass(
                fixture.freight(), fixture.order().orderId(), offered.objective().threshold());

        MissionContract resolvedWithoutPlayer = service.reconcileMission(
                fixture.world(), delivered, fixture.industry(), fixture.discovery(),
                StrategicOperationState.empty(), offered.missionId(), new WalletComponent(), "actor.player");

        assertEquals(MissionStatus.FAILED, resolvedWithoutPlayer.status());
        assertEquals("opportunity.resolved-without-player", resolvedWithoutPlayer.outcomeCode());
        assertEquals(before, treasury(fixture.world(), fixture.order().stableFactionId()));
        assertTrue(service.snapshot().reputations().isEmpty());
    }

    @Test
    void schedulerUsesOnlyRelevantWakeupsAndObservedReputationRequiresNpcKnowledge() {
        Fixture fixture = fixture();
        ensureSpendable(fixture.world(), fixture.order().stableFactionId(), REWARD);
        Stage21HNpcMissionService service = service(fixture);
        MissionContract offered = offer(service, fixture);
        long now = fixture.world().getAuthoritativeWorldTick();
        service.enqueueWakeup(offered.missionId(), new MissionWakeup("event.freight.changed", now, now + 5L));

        assertTrue(service.dueMissionIds(now, 1).isEmpty());
        assertEquals(List.of(offered.missionId()), service.dueMissionIds(now + 5L, 1));
        assertThrows(IllegalArgumentException.class, () -> service.enqueueWakeup(
                offered.missionId(), new MissionWakeup("event.freight.changed", now, now + 5L)));

        ReputationEvent betrayal = new ReputationEvent(
                "reputation.betrayal.test", ReputationEventKind.BETRAYAL, -12,
                now, "action.betrayal.test");
        assertThrows(IllegalArgumentException.class, () -> service.recordObservedReputation(
                fixture.npc().npcId(), "actor.player", "fact.absent", betrayal));
        assertEquals(-12, service.recordObservedReputation(
                fixture.npc().npcId(), "actor.player", fixture.fact().factId(), betrayal).derivedValue());
    }

    @Test
    void issuerCannotOfferForeignPhysicalFreightOrSpendProtectedReserve() {
        Fixture fixture = fixture();
        String foreign = fixture.world().snapshot().factions().stream()
                .map(FactionEconomicState::factionContentId)
                .filter(value -> !value.equals(fixture.order().stableFactionId()))
                .findFirst().orElseThrow();
        NpcKnowledgeFact foreignFact = fact(fixture.order().orderId(), fixture.world().getAuthoritativeWorldTick());
        NpcState foreignNpc = npc(foreign, fixture.npc().locationSystemId(), foreignFact);
        Stage21HNpcMissionService foreignService = new Stage21HNpcMissionService(new Stage21HNpcMissionState(
                Stage21HNpcMissionState.CURRENT_VERSION,
                fixture.world().getAuthoritativeWorldTick(), 1L,
                List.of(foreignNpc), List.of(), List.of(), List.of()));
        assertThrows(IllegalStateException.class, () -> foreignService.offerMission(
                fixture.world(), fixture.freight(), fixture.industry(),
                fixture.saved().campaign().discoveryState().knowledgeFor(foreign), StrategicOperationState.empty(),
                foreignNpc.npcId(), MissionTemplate.EMERGENCY_SUPPLY_DELIVERY,
                objective(fixture.order()), List.of(foreignFact.factId()),
                fixture.world().getAuthoritativeWorldTick() + 100L, 1L));

        FactionEconomicState economy = fixture.world().findFactionEconomicState(fixture.order().stableFactionId()).orElseThrow();
        long spendable = Math.max(0L, economy.treasuryMilliCredits() - economy.treasuryReserveFloorMilliCredits());
        Stage21HNpcMissionService ownerService = service(fixture);
        if (spendable < Long.MAX_VALUE) {
            assertThrows(IllegalStateException.class, () -> ownerService.offerMission(
                    fixture.world(), fixture.freight(), fixture.industry(), fixture.discovery(),
                    StrategicOperationState.empty(), fixture.npc().npcId(), MissionTemplate.EMERGENCY_SUPPLY_DELIVERY,
                    objective(fixture.order()), List.of(fixture.fact().factId()),
                    fixture.world().getAuthoritativeWorldTick() + 100L, spendable + 1L));
        }
    }

    private static Fixture fixture() {
        var generated = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        var runtime = generated.runtime();
        Stage20GeneratedWorldRuntimePersistentState saved = runtime.captureState();
        Stage20FreightPersistentState freight = saved.freight();
        TransportOrderState order = freight.orders().get(0);
        long tick = runtime.world().getAuthoritativeWorldTick();
        NpcKnowledgeFact fact = fact(order.orderId(), tick);
        StarSystemId posting = order.orderedSystems().get(0);
        NpcState npc = npc(order.stableFactionId(), posting, fact);
        return new Fixture(runtime.world(), saved, freight, saved.campaign().industrialState(),
                saved.campaign().discoveryState().knowledgeFor(order.stableFactionId()), order, npc, fact);
    }

    private static Stage21HNpcMissionService service(Fixture fixture) {
        return new Stage21HNpcMissionService(new Stage21HNpcMissionState(
                Stage21HNpcMissionState.CURRENT_VERSION,
                fixture.world().getAuthoritativeWorldTick(),
                1L,
                List.of(fixture.npc()), List.of(), List.of(), List.of()));
    }

    private static MissionContract offer(Stage21HNpcMissionService service, Fixture fixture) {
        return service.offerMission(
                fixture.world(), fixture.freight(), fixture.industry(), fixture.discovery(),
                StrategicOperationState.empty(), fixture.npc().npcId(),
                MissionTemplate.EMERGENCY_SUPPLY_DELIVERY,
                objective(fixture.order()),
                List.of(fixture.fact().factId()),
                fixture.world().getAuthoritativeWorldTick() + 100L,
                REWARD);
    }

    private static MissionObjective objective(TransportOrderState order) {
        long threshold = (long) Math.floor(order.deliveredMassKg()) + 1L;
        return new MissionObjective(
                ObjectiveAuthority.FREIGHT,
                ObjectiveKind.FREIGHT_ORDER_DELIVERED_KG_AT_LEAST,
                order.orderId(), 0L, threshold, "");
    }

    private static NpcKnowledgeFact fact(String orderId, long tick) {
        return new NpcKnowledgeFact(
                "fact.freight." + orderId,
                orderId,
                KnowledgeKind.ACTOR_OBSERVATION,
                "ECONOMIC.RESOURCE_DEFICIT",
                8000,
                "report.freight." + orderId,
                tick,
                -1L);
    }

    private static NpcState npc(String faction, StarSystemId posting, NpcKnowledgeFact fact) {
        return new NpcState(
                "npc.test.logistics." + faction,
                "npc.test.logistics.name",
                NpcRole.TRADE_LOGISTICS,
                faction,
                posting,
                NpcAvailability.AVAILABLE,
                List.of(fact));
    }

    private static Stage20FreightPersistentState withDeliveredMass(
            Stage20FreightPersistentState source,
            String orderId,
            long deliveredMassKg) {
        ArrayList<TransportOrderState> orders = new ArrayList<>();
        for (TransportOrderState order : source.orders()) {
            if (!order.orderId().equals(orderId)) {
                orders.add(order);
                continue;
            }
            orders.add(new TransportOrderState(
                    order.orderId(), order.fleetId(), order.stableFactionId(), order.assignmentKind(),
                    order.commodityId(), order.sourceEndpointId(), order.destinationEndpointId(),
                    order.sourceProvenanceId(), order.orderedSystems(), order.oneWayDeliverySeconds(),
                    order.roundTripCycleSeconds(), order.deliveryDeadlineSeconds(), deliveredMassKg,
                    order.delayedDeliveryCount()));
        }
        return new Stage20FreightPersistentState(
                source.schemaVersion(), source.rootSeed(), source.generatorVersion(), source.worldFingerprint(),
                source.materializationVersion(), source.compatibilityAuthorityVersion(),
                source.nextFleetIdValue(), source.nextCargoLotOrdinal(), source.freighters(),
                source.cargoLots(), orders);
    }

    private static void ensureSpendable(WorldSimulation world, String faction, long required) {
        FactionEconomicState economy = world.findFactionEconomicState(faction).orElseThrow();
        long spendable = Math.max(0L, economy.treasuryMilliCredits() - economy.treasuryReserveFloorMilliCredits());
        if (spendable >= required) {
            return;
        }
        long amount = required - spendable;
        WalletComponent source = new WalletComponent(amount);
        assertTrue(world.transferToFactionTreasury(
                faction, source, "stage21h-test-funding", amount, "stage21h-test-funding"));
        assertEquals(0L, source.getBalanceMilliCredits());
    }

    private static long treasury(WorldSimulation world, String faction) {
        return world.findFactionEconomicState(faction).orElseThrow().treasuryMilliCredits();
    }

    private static void assertNoStage21HMoneySourceOrSink(WorldSimulation world) {
        boolean forbidden = world.snapshot().systems().stream()
                .flatMap(system -> system.simulationState().ledger().entries().stream())
                .anyMatch(entry -> entry.reason().startsWith("stage21h-mission")
                        && (entry.type() == Type.MONEY_SOURCE || entry.type() == Type.MONEY_SINK));
        assertFalse(forbidden);
    }

    private record Fixture(
            WorldSimulation world,
            Stage20GeneratedWorldRuntimePersistentState saved,
            Stage20FreightPersistentState freight,
            Stage18IndustrialState industry,
            Stage20DiscoveryKnowledgeState discovery,
            TransportOrderState order,
            NpcState npc,
            NpcKnowledgeFact fact) { }
}
