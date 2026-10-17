package com.spacesim.campaign;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.SensorKnowledgeComponent;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ship.*;
import com.spacesim.ui.GeneratedCampaignSensorContactUi;
import com.spacesim.world.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignPersonalSensorsTest {
    @Test void pulseWhileCoastingPreservesFiniteMotionAndCannotIntroduceASecondMovementTick() {
        var c = fixture(100, false); var r = c.coordinator().runtime(); var w = r.world();
        var fleet = w.findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        var local = r.arrival().materialization(fleet.systemId());
        var origin = local.physicalState(fleet.localEntityId()).orElseThrow().position();
        local.updatePhysicalState(fleet.localEntityId(), new LocalPhysicalKinematics(origin, 20, -10));
        long tick = w.getAuthoritativeWorldTick();
        var before = c.captureState();
        var pulse = c.previewPilotAction("SCAN", "", "", 1);
        assertTrue(pulse.allowed()); assertEquals(before, c.captureState());
        c.submitPilotAction(pulse);
        assertEquals(tick + 1, w.getAuthoritativeWorldTick());
        var after = local.physicalState(fleet.localEntityId()).orElseThrow();
        double step = c.coordinator().session().fixedStepSeconds();
        assertEquals(origin.translated(20 * step, -10 * step), after.position());
        assertEquals(20, after.velocityXMps()); assertEquals(-10, after.velocityYMps());
        var saved = c.captureState();
        assertEquals(saved, Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(saved))).captureState());
    }
    @Test void radarDetectsAStationBeyondDockRangeWithoutGrantingItsArchiveOrIdentity() {
        var c = fixture(100, false); var r = c.coordinator().runtime(); var w = r.world();
        var fleet = w.findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        var station = r.infrastructure().endpoints().stream().filter(e -> e.systemId().equals(fleet.systemId())).findFirst().orElseThrow();
        var ref = c.pilotMarketReference(station.stationId()).orElseThrow();
        // Explicit geometry isolates radar reception beyond the existing 1 km docking boundary.
        r.arrival().materialization(fleet.systemId()).updatePhysicalState(fleet.localEntityId(),
                LocalPhysicalKinematics.stationary(station.position().translated(5000, 0)));
        var archive = r.discoveryState();
        var stock = station.storage().snapshot();
        assertFalse(c.previewPilotAction("DOCK", station.stationId(), "", 0).allowed());
        var pulse = c.previewPilotAction("SCAN", "", "", 1);
        assertTrue(pulse.allowed()); c.submitPilotAction(pulse);
        var knowledge = w.findSession(fleet.systemId()).orElseThrow().getEntityRegistry().require(fleet.localEntityId())
                .getComponent(SensorKnowledgeComponent.class);
        assertTrue(knowledge.receivedMeasurements().stream().anyMatch(m -> m.targetId() == ref.entityId().value()
                && m.rangeM() != null && m.rangeM() > 1000));
        assertEquals(archive, r.discoveryState(), "Radar contact must not become advertised station coordinates or archetype");
        assertEquals(stock, station.storage().snapshot());
        assertTrue(GeneratedCampaignSensorContactUi.rows(c).stream().allMatch(row -> row.focusFleet() == 0
                && (row.focusSystem() == null || row.focusSystem().equals(fleet.systemId()))));
        w.activateSystem(fleet.systemId());
        var beforeProjection = c.captureState();
        var markers = GeneratedCampaignSensorContactUi.localObjects(r, c.playerState().orElseThrow(), fleet.systemId());
        var marker = markers.stream().filter(m -> m.stableId().equals("personal-sensor-contact:"
                + fleet.systemId().value() + ':' + ref.entityId().value())).findFirst().orElseThrow();
        var track = knowledge.tracks().stream().filter(t -> t.targetId() == ref.entityId().value()).findFirst().orElseThrow();
        assertEquals(LocalPhysicalPosition.origin().translated(track.estimatedXM(), track.estimatedYM()), marker.position());
        assertEquals("", marker.factionId());
        assertNull(marker.sprite(), "An anonymous measurement must not reveal the target hull or station artwork");
        assertNotEquals(station.position(), marker.position(), "Focus must use the noisy personal estimate, not endpoint truth");
        var model = new com.spacesim.ui.GeneratedWorldUiModel(c.coordinator().rootSeed(), r, c.coordinator().content(),
                com.spacesim.ui.GeneratedWorldUiModel.CarrierUiSource.none(), () -> c.playerState().orElse(null));
        assertTrue(model.capture().localObjects().contains(marker), "The client map must expose the measured marker");
        assertTrue(GeneratedCampaignSensorContactUi.rows(c).stream().anyMatch(row -> row.selection().stableId().equals(marker.stableId())
                && fleet.systemId().equals(row.focusSystem())), "The contact row must focus the same anonymous map marker");
        assertEquals(beforeProjection, c.captureState(), "Map projection must not grant knowledge or perform work");
        var loaded = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(beforeProjection)));
        assertEquals(markers, GeneratedCampaignSensorContactUi.localObjects(loaded.coordinator().runtime(),
                loaded.playerState().orElseThrow(), fleet.systemId()));
        var otherSystem = w.snapshot().systems().stream().map(StarSystemSimulationState::systemId)
                .filter(id -> !id.equals(fleet.systemId())).findFirst().orElseThrow();
        assertTrue(GeneratedCampaignSensorContactUi.localObjects(r, c.playerState().orElseThrow(), otherSystem).isEmpty(),
                "A system-local target identity cannot be projected into a different system");
        // Explicit history expiry isolates the presentation boundary: a retained track alone
        // must not create navigation once the delivered range evidence is no longer present.
        double horizon = DatalinkState.local().maxMeasurementAgeSeconds();
        knowledge.pruneMeasurements(track.lastMeasurementSeconds() + horizon + 1, horizon);
        assertTrue(GeneratedCampaignSensorContactUi.localObjects(r, c.playerState().orElseThrow(), fleet.systemId()).isEmpty());
        assertTrue(GeneratedCampaignSensorContactUi.rows(c).stream().allMatch(row -> row.focusSystem() == null));
        var saved = c.captureState();
        assertEquals(saved, Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(saved))).captureState());
    }
    @Test void confirmedRadarPulseUsesTheExistingPilotFitAndOneWorldTick() {
        var c = fixture(100, false);
        long tick = c.coordinator().runtime().world().getAuthoritativeWorldTick();
        var baseline = c.captureState();
        assertEquals(1, GeneratedCampaignSensorContactUi.actions(c).size());
        assertEquals(baseline, c.captureState(), "Presenting the pulse action must be pure");
        var preview = c.previewPilotAction("SCAN", "", "", 1);
        assertTrue(preview.allowed());
        assertEquals(baseline, c.captureState(), "Preview must not emit, spend energy or run time");
        c.submitPilotAction(preview);
        assertEquals(tick + 1, c.coordinator().runtime().world().getAuthoritativeWorldTick());
        assertTrue(c.coordinator().isPaused());
        var world = c.coordinator().runtime().world();
        var fleet = world.findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        var knowledge = world.findSession(fleet.systemId()).orElseThrow().getEntityRegistry().require(fleet.localEntityId())
                .getComponent(SensorKnowledgeComponent.class);
        assertNotNull(knowledge);
        assertTrue(knowledge.receivedMeasurements().stream().anyMatch(m -> m.rangeM() != null));
        assertFalse(GeneratedCampaignSensorContactUi.rows(c).isEmpty());
        assertTrue(GeneratedCampaignSensorContactUi.rows(c).stream().allMatch(r -> r.focusFleet() == 0
                && (r.focusSystem() == null || r.focusSystem().equals(fleet.systemId()))));
        assertTrue(c.playerJournal().entries().stream().anyMatch(e -> e.action().equals("SCAN")));
        var saved = c.captureState();
        var loaded = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(saved)));
        assertNull(firstDifference(saved, loaded.captureState(), "campaign"));
        assertThrows(IllegalStateException.class, () -> c.submitPilotAction(preview));
    }

    @Test void radarDenialPreservesHeatKnowledgeTimeAndJournal() {
        var c = fixture(100, false);
        c.setPilotThrust(0, 0, false);
        var ship = ownShip(c); var state = ship.runtimeState;
        ship.setRuntimeState(new ShipEngineeringRuntime.RuntimeState(state.consumables(), state.sharedBusEnergyJ(),
                state.shipHeatStoredJ(), Map.of("utility_sensor", 1e20), state.thrustLimitNByMount(),
                state.coolantBusCapacityW(), state.ftlCooldownSecondsByMount()));
        var denied = c.captureState();
        var preview = c.previewPilotAction("SCAN", "", "", 1);
        assertFalse(preview.allowed());
        assertThrows(IllegalStateException.class, () -> c.submitPilotAction(preview));
        assertEquals(denied, c.captureState());
        assertTrue(GeneratedCampaignSensorContactUi.rows(c).isEmpty());
    }

    @Test void pulseWithThrustUsesTheSamePhysicalMovementAndBindsThePreviewToControlInput() {
        var c = fixture(100, false);
        assertTrue(c.setPilotThrust(.5f, .25f, false));
        var before = c.captureState();
        var reference = Stage228CampaignAuthority.restore(before);
        reference.setPilotThrust(.5f, .25f, false);
        reference.coordinator().setPaused(false);
        reference.advanceFrame(reference.coordinator().session().fixedStepSeconds());
        reference.coordinator().setPaused(true);
        var fleet = c.coordinator().runtime().world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        var expected = reference.coordinator().runtime().arrival().materialization(fleet.systemId()).physicalState(fleet.localEntityId()).orElseThrow();
        var pulse = c.previewPilotAction("SCAN", "", "", 1);
        assertTrue(pulse.allowed()); assertEquals(before, c.captureState());
        c.setPilotThrust(.75f, .25f, false);
        assertThrows(IllegalStateException.class, () -> c.submitPilotAction(pulse));
        assertEquals(before, c.captureState(), "Changed control must reject before a physical tick or measurement");
        c.setPilotThrust(.5f, .25f, false);
        c.submitPilotAction(pulse);
        assertEquals(expected, c.coordinator().runtime().arrival().materialization(fleet.systemId()).physicalState(fleet.localEntityId()).orElseThrow());
        assertEquals(ownShip(reference).runtimeState.consumables(), ownShip(c).runtimeState.consumables());
        assertTrue(ownShip(c).runtimeState.sharedBusEnergyJ() <= ownShip(reference).runtimeState.sharedBusEnergyJ());
        assertEquals(before, Stage228CampaignAuthority.restore(before).captureState());
    }

    @Test void pulseCommitsActualMountHeatThroughTheCommonEngineeringService() {
        var c = fixture(100, false); var ship = ownShip(c);
        var sensor = new ShipSensorEngineeringAdapter().derive(new ProductionEngineeringRuntimeResolver().derive(ship))
                .sensors().stream().filter(s -> s.definition().mode() == SensorDefinition.Mode.ACTIVE_RADAR).findFirst().orElseThrow();
        double before = ship.runtimeState.localHeatJByMount().getOrDefault(sensor.mountId(), 0d);
        GeneratedCampaignPersonalSensors.pulse(c);
        assertEquals(before + sensor.definition().activeModeWasteHeatW() * c.coordinator().session().fixedStepSeconds(),
                ship.runtimeState.localHeatJByMount().get(sensor.mountId()), 1e-6);
    }

    @Test void radarCannotReuseReactorPowerReservedForIdleStorageRecharge() {
        var c = fixture(100, false); var ship = ownShip(c);
        var derived = new ProductionEngineeringRuntimeResolver().derive(ship);
        // This existing fit has no available shared storage to supply a pulse after the margin is reserved.
        var before = c.captureState();
        assertThrows(IllegalStateException.class,
                () -> GeneratedCampaignPersonalSensors.pulse(c, derived.continuousPowerMarginW()));
        assertEquals(before, c.captureState(), "A denied pulse cannot partially spend heat or manufacture a contact");
    }
    @Test void passiveContactRequiresRealTicksAndPreservesUnknownRangeAndNativeKnowledge() {
        var c = fixture(100);
        var before = c.captureState();
        var archive = c.coordinator().runtime().discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID);
        assertTrue(GeneratedCampaignSensorContactUi.rows(c).isEmpty());
        c.advanceFrame(1); assertEquals(before, c.captureState(), "Pause cannot manufacture a measurement");
        c.coordinator().setPaused(false); c.advanceFrame(1.1f);
        var rows = GeneratedCampaignSensorContactUi.rows(c);
        assertFalse(rows.isEmpty(), () -> "Actual fitted passive sensor must see the nearby fitted physical target; tick="
                + c.coordinator().runtime().world().getAuthoritativeWorldTick() + " power="
                + new ProductionEngineeringRuntimeResolver().derive(ownShip(c)).continuousPowerMarginW() + " sensors="
                + new ShipSensorEngineeringAdapter().derive(new ProductionEngineeringRuntimeResolver().derive(ownShip(c))));
        assertTrue(rows.stream().allMatch(r -> r.focusFleet() == 0 && r.focusSystem() == null));
        assertTrue(rows.stream().anyMatch(r -> r.toString().contains("Дальность и координаты не определены")));
        assertTrue(GeneratedCampaignSensorContactUi.localObjects(c.coordinator().runtime(), c.playerState().orElseThrow(),
                c.coordinator().runtime().world().getActiveSystemId()).isEmpty(), "Bearing alone cannot create a focus position");
        var saved = c.captureState();
        assertEquals(rows, GeneratedCampaignSensorContactUi.rows(c));
        assertEquals(saved, c.captureState(), "Reading contacts cannot refresh observations");
        var loaded = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(saved)));
        assertNull(firstDifference(saved, loaded.captureState(), "campaign")); assertEquals(rows, GeneratedCampaignSensorContactUi.rows(loaded));
        long notifications = c.playerJournal().entries().stream().filter(e -> e.action().equals("SENSOR_CONTACT_DETECTED")).count();
        c.advanceFrame(1.1f);
        assertEquals(notifications, c.playerJournal().entries().stream().filter(e -> e.action().equals("SENSOR_CONTACT_DETECTED")).count());
        assertEquals(before.playerState().walletMilliCredits(), c.playerState().orElseThrow().walletMilliCredits());
        assertEquals(before.playerState().ownedFleetIds(), c.playerState().orElseThrow().ownedFleetIds());
        assertEquals(archive, c.coordinator().runtime().discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID));
    }

    @Test void destroyedSensorAndUndetectablyDistantTargetDoNotGrantContacts() {
        var c = fixture(100); var ship = ownShip(c); var old = ship.instanceState;
        var sensors = new ShipSensorEngineeringAdapter().derive(new ProductionEngineeringRuntimeResolver().derive(ship)).sensors();
        var damage = new java.util.TreeMap<String, Double>();
        sensors.forEach(s -> damage.put(s.mountId(), 0d));
        ship.setInstanceState(new ShipInstanceRuntimeState(new ShipDamageRuntime.Snapshot(Map.of(),
                new ShipEngineeringState.DamageState(damage)), old.shieldStatesByMount(), old.maintenance(), old.weaponLoadout(), old.weaponMountRuntime()));
        c.coordinator().setPaused(false); c.advanceFrame(1.1f);
        assertTrue(GeneratedCampaignSensorContactUi.rows(c).isEmpty());
        var distant = fixture(1e18); distant.coordinator().setPaused(false); distant.advanceFrame(1.1f);
        assertTrue(GeneratedCampaignSensorContactUi.rows(distant).isEmpty());
    }

    @Test void localContactsDoNotFollowTheObserverIntoAnotherSystem() {
        var c = fixture(100);
        c.coordinator().setPaused(false); c.advanceFrame(1.1f);
        assertFalse(GeneratedCampaignSensorContactUi.rows(c).isEmpty());
        var world = c.coordinator().runtime().world();
        var own = world.findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        var destination = world.snapshot().systems().stream()
                .map(StarSystemSimulationState::systemId).filter(id -> !id.equals(own.systemId()))
                .findFirst().orElseThrow();
        // Explicit topology fixture tests the system-local identity boundary, not ordinary flight.
        world.beginFleetTransfer(own.id(), destination);
        assertTrue(GeneratedCampaignSensorContactUi.rows(c).isEmpty());
        world.completeFleetTransfer(own.id(), 0, 0);
        assertTrue(GeneratedCampaignSensorContactUi.rows(c).isEmpty());
    }

    private static String firstDifference(Object a, Object b, String path) {
        if (java.util.Objects.equals(a, b)) return null;
        if (a == null || b == null || a.getClass() != b.getClass()) return path;
        if (a instanceof java.util.List<?> left && b instanceof java.util.List<?> right) {
            if (left.size() != right.size()) return path + ".size";
            for (int i = 0; i < left.size(); i++) {
                String difference = firstDifference(left.get(i), right.get(i), path + "[" + i + "]");
                if (difference != null) return difference;
            }
        }
        if (a.getClass().isRecord()) for (var field : a.getClass().getRecordComponents()) {
            try {
                String difference = firstDifference(field.getAccessor().invoke(a), field.getAccessor().invoke(b), path + "." + field.getName());
                if (difference != null) return difference;
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }
        return path + ": " + a + " / " + b;
    }

    private static EngineeringComponent ownShip(Stage228CampaignAuthority c) {
        var world = c.coordinator().runtime().world(); var f = world.findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        return world.findSession(f.systemId()).orElseThrow().getEntityRegistry().require(f.localEntityId()).getComponent(EngineeringComponent.class);
    }

    private static Stage228CampaignAuthority fixture(double distance) {
        return fixture(distance, true);
    }

    private static Stage228CampaignAuthority fixture(double distance, boolean passiveFit) {
        var c = FoundedCampaignFixture.restore(); var r = c.coordinator().runtime(); var w = r.world();
        var p = c.playerState().orElseThrow(); var own = w.findFleet(p.activeFleetId()).orElseThrow();
        var target = w.getFleetPlacements().stream().filter(f -> !p.ownedFleetIds().contains(f.id()) && f.locationKind() == FleetLocationKind.IN_SYSTEM)
                .filter(f -> w.findSession(f.systemId()).orElseThrow().getEntityRegistry().require(f.localEntityId()).getComponent(EngineeringComponent.class) != null)
                .findFirst().orElseThrow();
        // Explicit geometry fixture isolates sensor SNR; it does not prove ordinary acquisition.
        var origin = r.arrival().materialization(target.systemId()).physicalState(target.localEntityId()).orElseThrow().position();
        if (!target.systemId().equals(own.systemId())) { w.beginFleetTransfer(own.id(), target.systemId()); w.completeFleetTransfer(own.id(), 0, 0); }
        own = w.findFleet(own.id()).orElseThrow();
        var materialization = r.arrival().materialization(own.systemId());
        var positioned = LocalPhysicalKinematics.stationary(origin.translated(distance, 0));
        if (materialization.physicalState(own.localEntityId()).isEmpty()) materialization.registerPhysicalState(own.localEntityId(), positioned);
        else materialization.updatePhysicalState(own.localEntityId(), positioned);
        if (!passiveFit) { c.coordinator().setPaused(true); return c; }
        // The historical pilot has active radar only: use an admitted passive-capable fit as an equipment fixture.
        var catalog = com.spacesim.content.ship.Stage175ICombatTestContentPack.loadStage21StrategicDoctrines();
        var fit = ShipEngineeringState.InstalledFit.fromDemonstrator(catalog.findDemonstratorFit(
                com.spacesim.content.ship.Stage175ICombatTestContentPack.stage21StrategicFitId("fit.test_doctrine_a_kinetic_v1")));
        var operating = new ShipEngineeringRuntime(catalog).initialize(fit, ShipEngineeringState.ConsumableState.empty());
        w.findSession(own.systemId()).orElseThrow().getEntityRegistry().require(own.localEntityId())
                .add(new EngineeringComponent(fit, operating, ShipInstanceRuntimeState.legacyNeutral()));
        c.coordinator().setPaused(true); return c;
    }
}
