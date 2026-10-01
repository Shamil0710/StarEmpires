package com.spacesim.campaign;

import com.spacesim.components.WalletComponent;
import com.spacesim.persistence.Stage20FreightPersistentState;
import com.spacesim.persistence.Stage20FreightPersistentState.TransportOrderState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistentState;
import com.spacesim.player.PlayerState;
import com.spacesim.ui.GeneratedWorldUiModel;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab;
import com.spacesim.ui.ProductionUiProjector;
import com.spacesim.world.Stage21HNpcMissionService;
import com.spacesim.world.Stage21HNpcMissionService.PlayerCommand;
import com.spacesim.world.Stage21HNpcMissionState;
import com.spacesim.world.Stage21HNpcMissionState.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeneratedCampaignPlayerMissionIntegrationTest {
    private static final long REWARD = 1000L;

    @Test
    void inclusiveDeadlineExpiresWithoutPlayerAndReturnsExactEscrowOnce() {
        var campaign = fixture(false, false, false, 1, 2L);
        var mission = mission(campaign, 0);
        long before = treasury(campaign, mission.issuerFactionId());
        advanceTo(campaign, 2L);
        assertEquals(MissionStatus.OFFERED, mission(campaign, 0).status());
        advanceTo(campaign, 3L);
        assertEquals(MissionStatus.EXPIRED, mission(campaign, 0).status());
        assertEquals(3L, mission(campaign, 0).statusUpdatedTick());
        assertEquals(0L, mission(campaign, 0).escrowMilliCredits());
        assertEquals(before + REWARD, treasury(campaign, mission.issuerFactionId()));
        assertTrue(campaign.playerState().isEmpty());
        assertTrue(campaign.coordinator().npcMissions().reputations().isEmpty());
        advanceTo(campaign, 5L);
        assertEquals(before + REWARD, treasury(campaign, mission.issuerFactionId()));
        assertNoMoneySourceOrSink(campaign);
    }

    @Test
    void previewIsPureSubmissionUsesSameValidatorAndRepeatedOrForeignTokenFails() {
        var campaign = fixture(true, true, false, 1, 100L);
        var before = campaign.captureState();
        var preview = campaign.previewMissionCommand(PlayerCommand.ACCEPT, mission(campaign, 0).missionId());
        assertTrue(preview.allowed());
        assertEquals(before, campaign.captureState());
        var foreign = Stage228CampaignAuthority.restore(before);
        assertThrows(IllegalStateException.class, () -> foreign.submitMissionCommand(preview));
        assertEquals(before, foreign.captureState());
        assertEquals(MissionStatus.ACCEPTED, campaign.submitMissionCommand(preview).status());
        var accepted = campaign.captureState();
        assertThrows(IllegalStateException.class, () -> campaign.submitMissionCommand(preview));
        assertEquals(accepted, campaign.captureState());
        assertFalse(campaign.previewMissionCommand(PlayerCommand.ACCEPT, mission(campaign, 0).missionId()).allowed());
    }

    @Test
    void knowledgeViewerCannotGrantPlayerOrUndiscoveredMissionPermission() {
        for (var campaign : List.of(fixture(false, false, false, 1, 100L),
                fixture(true, false, false, 1, 100L))) {
            var before = campaign.captureState();
            var preview = campaign.previewMissionCommand(PlayerCommand.ACCEPT, mission(campaign, 0).missionId());
            assertFalse(preview.allowed());
            assertThrows(IllegalStateException.class, () -> campaign.submitMissionCommand(preview));
            assertEquals(before, campaign.captureState());
            var world = new GeneratedWorldUiModel(1L, campaign.coordinator().runtime(), campaign.coordinator().content()).capture();
            var projection = new ProductionUiProjector("faction.alpha").capture(campaign, world);
            assertFalse(projection.rows(Tab.CONTACTS).stream()
                    .anyMatch(row -> row.selection().stableId().startsWith("player-mission:")));
        }
    }

    @Test
    void staleTickOrPauseControlChangeRejectsPreviewWithoutMutation() {
        var campaign = fixture(true, true, false, 1, 100L);
        var preview = campaign.previewMissionCommand(PlayerCommand.REJECT, mission(campaign, 0).missionId());
        advanceTo(campaign, 1L);
        var afterTick = campaign.captureState();
        assertThrows(IllegalStateException.class, () -> campaign.submitMissionCommand(preview));
        assertEquals(afterTick, campaign.captureState());
        var current = campaign.previewMissionCommand(PlayerCommand.ACCEPT, mission(campaign, 0).missionId());
        campaign.coordinator().setPaused(true);
        var paused = campaign.captureState();
        assertThrows(IllegalStateException.class, () -> campaign.submitMissionCommand(current));
        assertEquals(paused, campaign.captureState());
    }

    @Test
    void rejectAndCancelUseRealTreasuryAndDoNotPayPlayer() {
        for (PlayerCommand command : List.of(PlayerCommand.REJECT, PlayerCommand.CANCEL)) {
            var campaign = fixture(true, true, false, 1, 100L);
            String id = mission(campaign, 0).missionId();
            String faction = mission(campaign, 0).issuerFactionId();
            long before = treasury(campaign, faction);
            if (command == PlayerCommand.CANCEL) campaign.submitMissionCommand(campaign.previewMissionCommand(PlayerCommand.ACCEPT, id));
            var result = campaign.submitMissionCommand(campaign.previewMissionCommand(command, id));
            assertEquals(command == PlayerCommand.REJECT ? MissionStatus.REJECTED : MissionStatus.CANCELLED, result.status());
            assertEquals(before + REWARD, treasury(campaign, faction));
            assertEquals(17L, campaign.playerState().orElseThrow().walletMilliCredits());
            assertNoMoneySourceOrSink(campaign);
        }
    }

    @Test
    void acceptedExpiryCreatesSingleFailureMemoryAndContinuesAcrossNativeSaveAtEightTimesSpeed() {
        var campaign = fixture(true, true, false, 1, 4L);
        campaign.submitMissionCommand(campaign.previewMissionCommand(PlayerCommand.ACCEPT, mission(campaign, 0).missionId()));
        advanceTo(campaign, 3L);
        var restored = roundtrip(campaign);
        campaign.coordinator().setTimeScale(8d);
        restored.coordinator().setTimeScale(8d);
        campaign.advanceFrame(0.04f);
        restored.advanceFrame(0.04f);
        assertEquals(campaign.captureState(), restored.captureState());
        assertEquals(MissionStatus.EXPIRED, mission(restored, 0).status());
        assertEquals(5L, mission(restored, 0).statusUpdatedTick());
        assertEquals(-8, restored.coordinator().npcMissions().reputations().get(0).derivedValue());
        assertEquals(1, restored.coordinator().npcMissions().reputations().get(0).events().size());
        assertEquals(17L, restored.playerState().orElseThrow().walletMilliCredits());
        assertEquals(restored.captureState(), roundtrip(restored).captureState());
    }

    @Test
    void pauseFractionalFrameAndCaptureNeverExpireOrReconcileContracts() {
        var campaign = fixture(true, true, false, 1, 2L);
        var before = campaign.captureState();
        assertEquals(before, roundtrip(campaign).captureState());
        campaign.advanceFrame(0f);
        assertEquals(before, campaign.captureState());
        campaign.advanceFrame(0.001f);
        assertEquals(before.stage21Runtime().stage21HRuntime().npcMissionState(), campaign.coordinator().npcMissions());
        campaign.coordinator().setPaused(true);
        var paused = campaign.captureState();
        campaign.advanceFrame(100f);
        assertEquals(paused, campaign.captureState());
    }

    @Test
    void expiryBudgetIsStableAndPendingWakeupsCannotStarveOverdueContracts() {
        var campaign = fixture(false, false, false, 10, 2L);
        for (var mission : campaign.coordinator().npcMissions().missions()) {
            campaign.coordinator().npcMissionService().enqueueWakeup(mission.missionId(), new MissionWakeup("future." + mission.missionId(), 0L, 100L));
        }
        advanceTo(campaign, 3L);
        assertEquals(8L, campaign.coordinator().npcMissions().missions().stream().filter(m -> m.status() == MissionStatus.EXPIRED).count());
        var restored = roundtrip(campaign);
        advanceTo(campaign, 4L);
        advanceTo(restored, 4L);
        assertEquals(campaign.captureState(), restored.captureState());
        assertTrue(campaign.coordinator().npcMissions().missions().stream().allMatch(m -> m.status() == MissionStatus.EXPIRED));
    }

    @Test
    void dueWakeupConsumesOrdinaryPendingObservationWithoutChangingWallet() {
        var campaign = fixture(true, true, true, 1, 100L);
        String id = mission(campaign, 0).missionId();
        campaign.submitMissionCommand(campaign.previewMissionCommand(PlayerCommand.ACCEPT, id));
        campaign.coordinator().npcMissionService().enqueueWakeup(id, new MissionWakeup("event.freight.pending", 0L, 1L));
        var restored = roundtrip(campaign);
        advanceTo(campaign, 1L);
        advanceTo(restored, 1L);
        assertEquals(campaign.captureState(), restored.captureState());
        assertEquals(MissionStatus.ACCEPTED, mission(campaign, 0).status());
        assertTrue(mission(campaign, 0).pendingWakeups().isEmpty());
        assertEquals("freight.delivery-pending", mission(campaign, 0).outcomeCode());
        assertEquals(17L, campaign.playerState().orElseThrow().walletMilliCredits());
    }

    @Test
    void restoredAuthoritativeDeliveryPaysOnlyParticipatingPlayerAndPreservesEveryOtherPlayerField() {
        // A previously delivered freight checkpoint is a fixture, not evidence that the UI can
        // physically deliver cargo. This test isolates the ordinary checkpoint settlement seam.
        for (boolean owns : List.of(true, false)) {
            var campaign = fixture(true, true, owns, 1, 100L);
            String id = mission(campaign, 0).missionId();
            campaign.submitMissionCommand(campaign.previewMissionCommand(PlayerCommand.ACCEPT, id));
            campaign = withDeliveredCheckpoint(campaign);
            var beforePlayer = campaign.playerState().orElseThrow();
            var restored = roundtrip(campaign);
            advanceTo(campaign, 1L);
            advanceTo(restored, 1L);
            assertEquals(campaign.captureState(), restored.captureState());
            assertEquals(owns ? MissionStatus.COMPLETED : MissionStatus.FAILED, mission(campaign, 0).status());
            var afterPlayer = campaign.playerState().orElseThrow();
            assertEquals(17L + (owns ? REWARD : 0L), afterPlayer.walletMilliCredits());
            assertEquals(beforePlayer.ownedFleetIds(), afterPlayer.ownedFleetIds());
            assertEquals(beforePlayer.discoveredSystemIds(), afterPlayer.discoveredSystemIds());
            assertEquals(beforePlayer.fleetOrders(), afterPlayer.fleetOrders());
            assertEquals(beforePlayer.threatIntel(), afterPlayer.threatIntel());
            assertEquals(0L, mission(campaign, 0).escrowMilliCredits());
            assertNoMoneySourceOrSink(campaign);
            assertEquals(campaign.captureState(), roundtrip(campaign).captureState());
        }
    }

    @Test
    void periodicAuthoritySweepSettlesChangedObjectiveWithoutMissionSpecificWakeup() {
        var campaign = fixture(true, true, true, 1, 100L);
        String id = mission(campaign, 0).missionId();
        campaign.submitMissionCommand(campaign.previewMissionCommand(PlayerCommand.ACCEPT, id));
        campaign = withDeliveredCheckpoint(campaign, false);
        advanceTo(campaign, 59L);
        assertEquals(MissionStatus.ACCEPTED, mission(campaign, 0).status());
        var restored = roundtrip(campaign);
        advanceTo(campaign, 60L);
        advanceTo(restored, 60L);
        assertEquals(campaign.captureState(), restored.captureState());
        assertEquals(MissionStatus.COMPLETED, mission(campaign, 0).status());
        assertEquals(1017L, campaign.playerState().orElseThrow().walletMilliCredits());
    }

    @Test
    void refundCapacityFailureAndUnknownIdentityAreRejectedByPurePreview() {
        var campaign = fixture(true, true, false, 1, 100L);
        var world = campaign.coordinator().runtime().world();
        String faction = mission(campaign, 0).issuerFactionId();
        long amount = Long.MAX_VALUE - treasury(campaign, faction);
        assertTrue(world.transferToFactionTreasury(faction, new WalletComponent(amount),
                "test-treasury-capacity", amount, "test-treasury-capacity"));
        var before = campaign.captureState();
        var rejected = campaign.previewMissionCommand(PlayerCommand.REJECT, mission(campaign, 0).missionId());
        assertFalse(rejected.allowed());
        assertThrows(IllegalStateException.class, () -> campaign.submitMissionCommand(rejected));
        assertFalse(campaign.previewMissionCommand(PlayerCommand.ACCEPT, "missing.mission").allowed());
        assertEquals(before, campaign.captureState());
    }

    @Test
    void periodicSweepProcessesMoreThanOneBudgetWithoutTransientSchedulerState() {
        var campaign = fixture(true, true, true, 10, 200L);
        for (var mission : campaign.coordinator().npcMissions().missions()) {
            campaign.submitMissionCommand(campaign.previewMissionCommand(PlayerCommand.ACCEPT, mission.missionId()));
        }
        campaign = withDeliveredCheckpoint(campaign, false);
        advanceTo(campaign, 60L);
        assertEquals(8L, campaign.coordinator().npcMissions().missions().stream()
                .filter(m -> m.status() == MissionStatus.COMPLETED).count());
        var restored = roundtrip(campaign);
        advanceTo(campaign, 120L);
        advanceTo(restored, 120L);
        assertEquals(campaign.captureState(), restored.captureState());
        assertTrue(campaign.coordinator().npcMissions().missions().stream()
                .allMatch(m -> m.status() == MissionStatus.COMPLETED));
        assertEquals(10017L, campaign.playerState().orElseThrow().walletMilliCredits());
    }

    @Test
    void physicalLossRemovesOwnedFleetAndAllowsExactCheckpointWithoutGrantingReplacement() {
        var campaign = fixture(true, true, true, 1, 100L);
        var player = campaign.playerState().orElseThrow();
        String id = mission(campaign, 0).missionId();
        campaign.submitMissionCommand(campaign.previewMissionCommand(PlayerCommand.ACCEPT, id));
        campaign.coordinator().npcMissionService().enqueueWakeup(id, new MissionWakeup("event.freight.destroyed", 0L, 1L));
        campaign.coordinator().runtime().destroyLocalFreighter(player.activeFleetId(),
                com.spacesim.world.DestructionPolicy.destroyAll());
        advanceTo(campaign, 1L);
        var after = campaign.playerState().orElseThrow();
        assertTrue(after.ownedFleetIds().isEmpty());
        assertNull(after.activeFleetId());
        assertEquals(player.walletMilliCredits(), after.walletMilliCredits());
        assertEquals(player.discoveredSystemIds(), after.discoveredSystemIds());
        assertEquals(MissionStatus.FAILED, mission(campaign, 0).status());
        assertEquals(0L, mission(campaign, 0).escrowMilliCredits());
        assertEquals(campaign.captureState(), roundtrip(campaign).captureState());
    }

    @Test
    void personalProjectionIsIndependentOfFactionViewerAndExplainsRewardAndDeadline() {
        var campaign = fixture(true, true, false, 1, 100L);
        var before = campaign.captureState();
        var world = new GeneratedWorldUiModel(1L, campaign.coordinator().runtime(), campaign.coordinator().content()).capture();
        for (String viewer : List.of("faction.alpha", "faction.beta")) {
            var row = new ProductionUiProjector(viewer).capture(campaign, world).rows(Tab.CONTACTS).stream()
                    .filter(value -> value.selection().stableId().startsWith("player-mission:")).findFirst().orElseThrow();
            assertEquals("Личные контракты", row.category());
            assertEquals("Срочная доставка снабжения", row.name());
            assertEquals("Предложен", row.summary());
            assertTrue(row.sections().stream().flatMap(section -> section.lines().stream())
                    .anyMatch(line -> line.label().equals("Цель") && line.value().contains("кг")));
            assertTrue(row.sections().stream().flatMap(section -> section.lines().stream()).anyMatch(value -> value.label().equals("Срок включительно")));
            assertFalse(row.provenance().isBlank());
        }
        assertEquals(before, campaign.captureState());
    }

    private static Stage228CampaignAuthority fixture(boolean initialized, boolean knows, boolean owns, int count, long deadline) {
        var authority = Stage228CampaignAuthority.create(1L);
        var c = authority.coordinator();
        var runtime = c.runtime().captureState();
        var order = runtime.freight().orders().get(0);
        var fact = new NpcKnowledgeFact("fact.freight.test", order.orderId(), KnowledgeKind.ACTOR_OBSERVATION,
                "ECONOMIC.RESOURCE_DEFICIT", 8000, "report.freight.test", 0L, -1L);
        var npc = new NpcState("npc.test.logistics", "Диспетчер", NpcRole.TRADE_LOGISTICS,
                order.stableFactionId(), order.orderedSystems().get(0), NpcAvailability.AVAILABLE, List.of(fact));
        var service = new Stage21HNpcMissionService(Stage21HNpcMissionState.empty(0L));
        service.installNpcRoster(List.of(npc));
        var economy = c.runtime().world().findFactionEconomicState(npc.factionContentId()).orElseThrow();
        long need = REWARD * count;
        long spendable = Math.max(0, economy.treasuryMilliCredits() - economy.treasuryReserveFloorMilliCredits());
        if (spendable < need) assertTrue(c.runtime().world().transferToFactionTreasury(npc.factionContentId(),
                new WalletComponent(need - spendable), "test-fixture-funding", need - spendable, "test-fixture-funding"));
        for (int i = 0; i < count; i++) service.offerMission(c.runtime().world(), runtime.freight(),
                runtime.campaign().industrialState(), runtime.campaign().discoveryState().knowledgeFor(npc.factionContentId()),
                c.operations(), npc.npcId(), MissionTemplate.EMERGENCY_SUPPLY_DELIVERY,
                new MissionObjective(ObjectiveAuthority.FREIGHT, ObjectiveKind.FREIGHT_ORDER_DELIVERED_KG_AT_LEAST,
                        order.orderId(), 0L, (long) order.deliveredMassKg() + 1L, ""), List.of(fact.factId()), deadline, REWARD);
        var stage21 = GeneratedCampaignAuthorityCheckpoint.capture(c.session(), c.actors(), c.strategicIntents(),
                c.diplomacy(), c.warfare(), c.commands(), c.operations(), c.transitions(), c.recovery(), service.snapshot());
        var base = authority.captureState();
        PlayerState player = initialized ? new PlayerState(17L, null, List.of(),
                owns ? List.of(order.fleetId()) : List.of(), owns ? order.fleetId() : null,
                knows ? List.of(npc.locationSystemId()) : List.of(), List.of(), null) : null;
        return Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistentState.compose(stage21,
                base.smallCraft(), base.hangars(), base.flightDeck(), base.operations(), player));
    }

    private static Stage228CampaignAuthority withDeliveredCheckpoint(Stage228CampaignAuthority source) {
        return withDeliveredCheckpoint(source, true);
    }
    private static Stage228CampaignAuthority withDeliveredCheckpoint(Stage228CampaignAuthority source, boolean wakeup) {
        var c = source.coordinator();
        var mission = mission(source, 0);
        if (wakeup) c.npcMissionService().enqueueWakeup(mission.missionId(), new MissionWakeup("event.freight.delivered", 0L, 1L));
        var saved = c.runtime().captureState();
        var freight = saved.freight();
        var orders = new ArrayList<TransportOrderState>();
        for (var o : freight.orders()) orders.add(o.orderId().equals(mission.objective().subjectId())
                ? new TransportOrderState(o.orderId(), o.fleetId(), o.stableFactionId(), o.assignmentKind(), o.commodityId(),
                o.sourceEndpointId(), o.destinationEndpointId(), o.sourceProvenanceId(), o.orderedSystems(),
                o.oneWayDeliverySeconds(), o.roundTripCycleSeconds(), o.deliveryDeadlineSeconds(), mission.objective().threshold(), o.delayedDeliveryCount()) : o);
        var nextFreight = new Stage20FreightPersistentState(freight.schemaVersion(), freight.rootSeed(), freight.generatorVersion(),
                freight.worldFingerprint(), freight.materializationVersion(), freight.compatibilityAuthorityVersion(),
                freight.nextFleetIdValue(), freight.nextCargoLotOrdinal(), freight.freighters(), freight.cargoLots(), orders);
        var session = GeneratedCampaignSession.restore(new Stage20GeneratedWorldRuntimePersistentState(saved.schemaVersion(),
                saved.bridgeVersion(), saved.campaign(), saved.worldState(), saved.activeSystemId(), saved.strategicStepTicks(),
                saved.remoteUpdateBudgetPerFrame(), nextFreight, saved.localFleetPhysicalStates()));
        var stage21 = GeneratedCampaignAuthorityCheckpoint.capture(session, c.actors(), c.strategicIntents(), c.diplomacy(),
                c.warfare(), c.commands(), c.operations(), c.transitions(), c.recovery(), c.npcMissions());
        var base = source.captureState();
        return Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistentState.compose(stage21,
                base.smallCraft(), base.hangars(), base.flightDeck(), base.operations(), base.playerState()));
    }

    private static MissionContract mission(Stage228CampaignAuthority campaign, int index) {
        return campaign.coordinator().npcMissions().missions().get(index);
    }
    private static long treasury(Stage228CampaignAuthority campaign, String faction) {
        return campaign.coordinator().runtime().world().findFactionEconomicState(faction).orElseThrow().treasuryMilliCredits();
    }
    private static void advanceTo(Stage228CampaignAuthority campaign, long tick) {
        while (campaign.coordinator().runtime().world().getAuthoritativeWorldTick() < tick) campaign.advanceFrame(1f / 60f);
    }
    private static Stage228CampaignAuthority roundtrip(Stage228CampaignAuthority campaign) {
        return Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(campaign.captureState())));
    }
    private static void assertNoMoneySourceOrSink(Stage228CampaignAuthority campaign) {
        assertFalse(campaign.coordinator().runtime().world().snapshot().systems().stream()
                .flatMap(system -> system.simulationState().ledger().entries().stream())
                .anyMatch(entry -> entry.reason().startsWith("stage21h-mission")
                        && (entry.type() == com.spacesim.economy.EconomicTransaction.Type.MONEY_SOURCE
                        || entry.type() == com.spacesim.economy.EconomicTransaction.Type.MONEY_SINK)));
    }
}
