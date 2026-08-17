package com.spacesim.persistence;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.SensorKnowledgeComponent;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.ship.SensorMeasurement;
import com.spacesim.ship.ShieldFieldRuntime;
import com.spacesim.ship.ShipDamageRuntime;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipInstanceRuntimeState;
import com.spacesim.ship.ShipyardEngineeringService.MaintenanceState;
import com.spacesim.ship.SignatureState.Channel;
import com.spacesim.ship.TrackCovariance;
import com.spacesim.ship.TrackState;
import com.spacesim.ship.WeaponLoadoutState;
import com.spacesim.ship.WeaponMountRuntime;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class Stage175HPersistenceTest {
    @Test
    void ecsRoundTripPreservesDamageShieldMaintenanceWeaponAndSensorKnowledge() {
        Entity source = entity(new EntityId(9101L));
        EntityState captured = EntityStateMapper.capture(source);

        Entity restored = EntityStateMapper.restore(captured);

        assertEquals(captured, EntityStateMapper.capture(restored));
        EngineeringComponent engineering = restored.getComponent(EngineeringComponent.class);
        assertNotNull(engineering);
        assertEquals(0.5d,
                engineering.instanceState.damage().moduleDamage().moduleIntegrityByMount().get("core_drive"), 0d);
        assertEquals(1_250d, engineering.instanceState.shieldStatesByMount().get("core_shield").reserveJ(), 0d);
        assertEquals(900d,
                engineering.instanceState.maintenance().secondsSinceServiceByMount().get("core_drive"), 0d);
        assertEquals("ammo.test",
                engineering.instanceState.weaponLoadout().feeds().get(0).ammunitionContentId());
        assertEquals(2.5d,
                engineering.instanceState.weaponMountRuntime().cooldownSecondsByMount().get("weapon_1"), 0d);
        SensorKnowledgeComponent knowledge = restored.getComponent(SensorKnowledgeComponent.class);
        assertNotNull(knowledge);
        assertEquals(1, knowledge.tracks().size());
        assertEquals(1, knowledge.receivedMeasurements().size());
        assertEquals(1, knowledge.pendingMeasurements().size());
    }

    @Test
    void contentBoundEnvelopeV2RoundTripAndReencodePreserveHStateExactly() {
        GameState baseline = SimulationSession.createDemo(0x175A11L).snapshot();
        List<EntityState> entities = new ArrayList<>(baseline.entities());
        EntityId id = entities.get(0).id();
        entities.set(0, EntityStateMapper.capture(entity(id)));
        GameState state = new GameState(
                baseline.schemaVersion(), baseline.rootSeed(), baseline.clock(), baseline.nextEntityIdValue(),
                baseline.eventRandomState(), baseline.asteroidRandomState(), baseline.events(),
                baseline.asteroidSpawner(), baseline.priceRecorder(), baseline.ledger(), List.copyOf(entities));
        String fingerprint = ContentCatalogLoader.loadDefault().getFingerprint();

        byte[] bytes = ContentBoundSaveCodec.encode(state, fingerprint);
        ContentBoundSaveCodec.DecodedSave decoded = ContentBoundSaveCodec.decode(bytes);

        assertEquals(state, decoded.state());
        assertEquals(fingerprint, decoded.contentFingerprint());
        assertArrayEquals(bytes, ContentBoundSaveCodec.encode(decoded.state(), fingerprint));
        assertNotNull(decoded.state().entities().get(0).engineering().instanceState());
        assertNotNull(decoded.state().entities().get(0).sensorKnowledge());
    }

    @Test
    void rawCorePayloadCannotInventMissingHState() {
        GameState baseline = SimulationSession.createDemo(0x175A12L).snapshot();
        List<EntityState> entities = new ArrayList<>(baseline.entities());
        EntityId id = entities.get(0).id();
        entities.set(0, EntityStateMapper.capture(entity(id)));
        GameState state = new GameState(
                baseline.schemaVersion(), baseline.rootSeed(), baseline.clock(), baseline.nextEntityIdValue(),
                baseline.eventRandomState(), baseline.asteroidRandomState(), baseline.events(),
                baseline.asteroidSpawner(), baseline.priceRecorder(), baseline.ledger(), List.copyOf(entities));

        GameState decodedCore = GameStateCodec.decode(GameStateCodec.encode(state));

        assertNull(decodedCore.entities().get(0).engineering().instanceState(),
                "legacy core payload has no H extension and must not synthesize damage/shield/service state");
        assertNull(decodedCore.entities().get(0).sensorKnowledge(),
                "legacy core payload must not synthesize sensor knowledge");
        EngineeringComponent restored = EntityStateMapper.restore(decodedCore.entities().get(0))
                .getComponent(EngineeringComponent.class);
        assertEquals(ShipInstanceRuntimeState.legacyNeutral(), restored.instanceState,
                "materialization of missing-H payload must use neutral non-granting state");
    }

    private static Entity entity(EntityId id) {
        InstalledFit fit = new InstalledFit(
                "hull.h_state_test",
                List.of(
                        new InstalledModuleDefinition("core_drive", "module.drive_test"),
                        new InstalledModuleDefinition("core_shield", "module.shield_test"),
                        new InstalledModuleDefinition("weapon_1", "module.weapon_test")));
        RuntimeState runtime = new RuntimeState(
                ConsumableState.empty(),
                7_500d,
                450d,
                Map.of("core_drive", 150d, "core_shield", 25d, "weapon_1", 40d),
                Map.of("core_drive", 12_000d),
                2_000d,
                Map.of());
        ShipInstanceRuntimeState instance = new ShipInstanceRuntimeState(
                new ShipDamageRuntime.Snapshot(
                        Map.of("compartment_mid", 0.6d),
                        new DamageState(Map.of("core_drive", 0.5d, "core_shield", 0.75d))),
                Map.of("core_shield", new ShieldFieldRuntime.State(1_250d, 90d, false, 0d, 0.75d)),
                new MaintenanceState(Map.of("core_drive", 900d, "weapon_1", 120d)),
                new WeaponLoadoutState(List.of(
                        new WeaponLoadoutState.FeedBinding("weapon_1", "feed", "ammo.test"))),
                new WeaponMountRuntime.RuntimeState(Map.of("weapon_1", 2.5d)));
        SensorKnowledgeComponent knowledge = new SensorKnowledgeComponent();
        TrackState track = new TrackState(
                42L,
                TrackState.InformationState.TRACKED,
                true,
                1_000d,
                -2_000d,
                new TrackCovariance(100d, 0.001d, 400d),
                0.8d,
                10d,
                2,
                3);
        SensorMeasurement measurement = new SensorMeasurement(
                id.value(), 42L, Channel.THERMAL, 10d,
                0d, 0d, 0.5d, null, 0.001d, null,
                10d, 1d, 9d, TrackState.InformationState.DETECTED);
        knowledge.putTrack(track);
        knowledge.receiveMeasurement(measurement);
        knowledge.queueMeasurement(measurement, 12d);
        return new Entity()
                .add(new EntityIdComponent(id))
                .add(new EngineeringComponent(fit, runtime, instance))
                .add(knowledge);
    }
}
