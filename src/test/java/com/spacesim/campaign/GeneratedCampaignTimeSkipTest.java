package com.spacesim.campaign;

import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.simulation.CampaignTimeSkipState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignTimeSkipTest {
    @Test void minutesAdvanceTheSameWorldAndRespectPauseAndFrameQuota() {
        var c = FoundedCampaignFixture.restore();
        long tick = c.coordinator().runtime().world().getAuthoritativeWorldTick();
        long wallet = c.playerState().orElseThrow().walletMilliCredits();
        var assets = c.playerState().orElseThrow().ownedFleetIds();
        c.coordinator().setTimeScale(4);
        var ordinary = Stage228CampaignAuthority.restore(c.captureState());
        assertThrows(IllegalArgumentException.class, () -> c.startTimeSkipMinutes(0));
        assertThrows(IllegalArgumentException.class, () -> c.startTimeSkipMinutes(1000001));
        c.startTimeSkipMinutes(1);
        assertEquals(CampaignTimeSkipState.Mode.MINUTES, c.timeSkip().mode());
        long target = c.timeSkip().targetTick();
        assertThrows(IllegalStateException.class, c::startTimeSkipUntilEvent);
        assertEquals(3, c.advanceTimeSkipBudget(1_000_000_000, 3));
        c.coordinator().setPaused(true);
        assertEquals(0, c.advanceTimeSkipBudget(1_000_000_000, 3));
        assertEquals(tick + 3, c.coordinator().runtime().world().getAuthoritativeWorldTick());
        c.coordinator().setPaused(false);
        while (c.timeSkip().active()) c.advanceTimeSkipBudget(1_000_000_000, 100);
        assertEquals(target, c.coordinator().runtime().world().getAuthoritativeWorldTick());
        assertTrue(c.coordinator().isPaused()); assertEquals(4, c.coordinator().timeScale());
        assertEquals(wallet, c.playerState().orElseThrow().walletMilliCredits());
        assertEquals(assets, c.playerState().orElseThrow().ownedFleetIds());
        ordinary.coordinator().setTimeScale(1); ordinary.coordinator().setPaused(false);
        for (long i = tick; i < target; i++) ordinary.advanceFrame(ordinary.coordinator().session().fixedStepSeconds());
        ordinary.coordinator().setPaused(true); ordinary.coordinator().setTimeScale(4);
        assertEquals(ordinary.captureState().stage21Runtime(), c.captureState().stage21Runtime(),
                "Skipping must advance NPCs, industry, fuel and world clocks exactly like ordinary ticks");
        assertEquals(ordinary.playerState(), c.playerState());
    }

    @Test void unfinishedRequestSurvivesExactNativeSaveAndContinuesWithBoundedWork() throws Exception {
        var c = FoundedCampaignFixture.restore(); c.startTimeSkipMinutes(2);
        c.advanceTimeSkipBudget(1_000_000_000, 7); c.coordinator().setPaused(true);
        var saved = c.captureState();
        var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(saved)));
        assertEquals(saved, restored.captureState());
        assertEquals(0, restored.advanceTimeSkipBudget(1_000_000_000, 5));
        restored.coordinator().setPaused(false);
        assertEquals(5, restored.advanceTimeSkipBudget(1_000_000_000, 5));
        long tick = restored.coordinator().runtime().world().getAuthoritativeWorldTick();
        restored.cancelTimeSkip();
        assertFalse(restored.timeSkip().active()); assertTrue(restored.coordinator().isPaused());
        assertEquals(tick, restored.coordinator().runtime().world().getAuthoritativeWorldTick());
        assertEquals(restored.captureState(), Stage228CampaignAuthority.restore(
                Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(restored.captureState()))).captureState());
    }

    @Test void eventSkipStopsOnActualRepairCompletion() {
        // Explicit berth, yard materials and tiny damage fixture; completion still requires real work.
        var c = GeneratedCampaignPersonalRepairTest.fixture("utility_sensor", .999999, FoundedCampaignFixture.restore());
        String station = c.coordinator().runtime().industry().industrial().stations().stream()
                .filter(s -> c.ownsProductionStation(s.stationId())).findFirst().orElseThrow().stationId();
        c.submitPilotAction(c.previewPilotAction("START_REPAIR", station, "", 0));
        c.startTimeSkipUntilEvent();
        assertEquals(1, c.advanceTimeSkipBudget(1_000_000_000, 50));
        assertTrue(c.repairQueue().orders().isEmpty());
        assertFalse(c.timeSkip().active()); assertTrue(c.coordinator().isPaused());
        assertEquals(1, c.playerJournal().entries().stream()
                .filter(e -> e.kind() == com.spacesim.player.PlayerJournalState.Kind.REPAIR_COMPLETED).count());
    }

    @Test void newPersonalDamageInterruptsDurationBeforeAnotherTick() {
        var c = GeneratedCampaignPersonalRepairTest.fixture("utility_sensor", .9, FoundedCampaignFixture.restore());
        c.startTimeSkipMinutes(60);
        var fleet = c.coordinator().runtime().world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        var ship = c.coordinator().runtime().world().findSession(fleet.systemId()).orElseThrow()
                .getEntityRegistry().require(fleet.localEntityId()).getComponent(com.spacesim.components.EngineeringComponent.class);
        var state = ship.instanceState;
        ship.setInstanceState(new com.spacesim.ship.ShipInstanceRuntimeState(
                new com.spacesim.ship.ShipDamageRuntime.Snapshot(java.util.Map.of(),
                    new com.spacesim.ship.ShipEngineeringState.DamageState(java.util.Map.of("utility_sensor", .8))),
                state.shieldStatesByMount(), state.maintenance(), state.weaponLoadout(), state.weaponMountRuntime()));
        var saved = c.captureState();
        assertFalse(saved.timeSkip().active()); assertTrue(c.coordinator().isPaused());
        assertEquals("Корабль получил повреждение.", c.lastTimeSkipMessage());
    }

    @Test void nativeV17AdoptsIdleControlWithoutAdvancingWorld() {
        var c = FoundedCampaignFixture.restore(); var saved = c.captureState();
        byte[] historical = HistoricalCampaignTimeControlFixture.withoutIdleTimeControl(
                Stage228GeneratedCampaignPersistenceCodec.encode(saved));
        java.nio.ByteBuffer.wrap(historical).putInt(4, 17).putInt(8, 17);
        byte[] runtime = "m22.8.generated-campaign.v17".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        System.arraycopy(runtime, 0, historical, 14, runtime.length);
        assertEquals(saved, Stage228GeneratedCampaignPersistenceCodec.decode(historical));
    }

    @Test void flightEventsStopAtActualPhaseBoundariesAndResumeAfterNativeReload() {
        var c = FoundedCampaignFixture.restore(); var r = c.coordinator().runtime();
        var fleet = r.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        var destination = r.world().getTopology().neighbors(fleet.systemId()).get(0);
        // Explicit departure-node geometry fixture isolates phase stopping, not ordinary navigation.
        var departure = r.arrival().resolve(destination, fleet.systemId());
        r.arrival().materialization(fleet.systemId()).updatePhysicalState(fleet.localEntityId(),
                com.spacesim.world.LocalPhysicalKinematics.stationary(departure.physicalState().position()));
        c.submitPilotAction(c.previewPilotAction("JUMP", Long.toString(destination.value()), "", 0));
        boolean transit = false;
        int stops = 0;
        while (c.coordinator().runtime().world().findFleetJump(fleet.id()).isPresent()) {
            String initialPhase = c.coordinator().runtime().world().findFleetJump(fleet.id()).orElseThrow().phase().name();
            c.startTimeSkipUntilEvent();
            var saved = c.captureState();
            c = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                    Stage228GeneratedCampaignPersistenceCodec.encode(saved)));
            assertEquals(saved, c.captureState());
            for (int frames = 0; c.timeSkip().active(); frames++) {
                assertTrue(frames < 2000, "Flight phase event must finish");
                c.advanceTimeSkipBudget(1_000_000_000, 100);
            }
            assertTrue(c.coordinator().isPaused());
            var phase = c.coordinator().runtime().world().findFleetJump(fleet.id());
            if (phase.isPresent()) assertNotEquals(initialPhase, phase.orElseThrow().phase().name());
            transit |= phase.isPresent() && phase.orElseThrow().phase() == com.spacesim.world.FleetJumpPhase.IN_TRANSIT;
            assertTrue(++stops <= 5);
        }
        assertTrue(transit);
        assertEquals(destination, c.coordinator().runtime().world().findFleet(fleet.id()).orElseThrow().systemId());
    }
}
