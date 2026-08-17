package com.spacesim.persistence;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.SensorKnowledgeComponent;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.ship.SensorMeasurement;
import com.spacesim.ship.ShieldFieldRuntime;
import com.spacesim.ship.ShipDamageRuntime;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipInstanceRuntimeState;
import com.spacesim.ship.ShipyardEngineeringService.MaintenanceState;
import com.spacesim.ship.SignatureState.Channel;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import com.spacesim.ship.TrackCovariance;
import com.spacesim.ship.TrackState;
import com.spacesim.ship.WeaponMountRuntime;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage175ICombatPersistenceAcceptanceTest {
    @Test
    void productionEnvelopePreservesCombinedMidCombatContinuityExactly() {
        var catalog = Stage175ICombatTestContentPack.loadDoctrines();
        var doctrine = Stage175IFleetDoctrineCatalog.get(DoctrineId.A_KINETIC_LINE);
        InstalledFit fit = InstalledFit.fromDemonstrator(catalog.findDemonstratorFit(doctrine.fitId()));
        ConsumableState partialConsumables = consumeOnePhysicalRound(doctrine.initialConsumables());
        EntityId shipId = new EntityId(17_500_901L);

        RuntimeState runtime = new RuntimeState(
                partialConsumables,
                120_000_000_000d,
                7_500_000_000d,
                Map.of(
                        "core_drive", 2_500_000_000d,
                        "utility_thermal", 6_000_000_000d,
                        "utility_shield", 1_200_000_000d,
                        "weapon_primary", 900_000_000d),
                Map.of("core_drive", 12_000_000d),
                1_400_000_000d,
                Map.of("ftl_stage17_5i_persistence_checkpoint", 37.5d));
        ShipInstanceRuntimeState instance = new ShipInstanceRuntimeState(
                new ShipDamageRuntime.Snapshot(
                        Map.of("engineering", 0.72d, "weapons", 0.58d),
                        new DamageState(Map.of(
                                "core_drive", 0.74d,
                                "utility_thermal", 0.61d,
                                "utility_shield", 0.83d,
                                "weapon_primary", 0.66d))),
                Map.of("utility_shield", new ShieldFieldRuntime.State(
                        25_000_000_000d, 1_200_000_000d, false, 0d, 0.83d)),
                new MaintenanceState(Map.of(
                        "core_drive", 96_000d,
                        "utility_thermal", 51_000d,
                        "weapon_primary", 14_400d)),
                doctrine.weaponLoadout(),
                new WeaponMountRuntime.RuntimeState(Map.of(
                        "weapon_primary", 1.25d,
                        "weapon_secondary", 0.4d)));
        Entity source = new Entity()
                .add(new EntityIdComponent(shipId))
                .add(new EngineeringComponent(fit, runtime, instance))
                .add(sensorKnowledge(shipId));
        EntityState captured = EntityStateMapper.capture(source);

        GameState baseline = SimulationSession.createDemo(0x1751E5L).snapshot();
        List<EntityState> entities = new ArrayList<>(baseline.entities());
        entities.set(0, captured);
        GameState state = new GameState(
                baseline.schemaVersion(), baseline.rootSeed(), baseline.clock(), baseline.nextEntityIdValue(),
                baseline.eventRandomState(), baseline.asteroidRandomState(), baseline.events(),
                baseline.asteroidSpawner(), baseline.priceRecorder(), baseline.ledger(), List.copyOf(entities));
        String fingerprint = ContentCatalogLoader.loadDefault().getFingerprint();

        byte[] encoded = ContentBoundSaveCodec.encode(state, fingerprint);
        ContentBoundSaveCodec.DecodedSave decoded = ContentBoundSaveCodec.decode(encoded);
        EntityState restored = decoded.state().entities().get(0);

        assertEquals(state, decoded.state());
        assertEquals(fingerprint, decoded.contentFingerprint());
        assertFalse(decoded.legacyRaw());
        assertArrayEquals(encoded, ContentBoundSaveCodec.encode(decoded.state(), fingerprint),
                "production envelope must remain deterministic after a mid-combat round-trip");

        assertNotNull(restored.engineering());
        assertNotNull(restored.engineering().instanceState());
        assertTrue(restored.engineering().consumables().ammunitionCount()
                        < doctrine.initialConsumables().ammunitionCount(),
                "partial magazine must remain physically depleted");
        assertEquals(6_000_000_000d,
                mountValue(restored.engineering().localHeatJByMount(), "utility_thermal"), 0d);
        assertEquals(37.5d,
                mountValue(restored.engineering().ftlCooldownSecondsByMount(),
                        "ftl_stage17_5i_persistence_checkpoint"), 0d);
        assertEquals(0.61d,
                mountValue(restored.engineering().instanceState().moduleIntegrityByMount(), "utility_thermal"), 0d);
        assertEquals(25_000_000_000d,
                shield(restored, "utility_shield").reserveJ(), 0d);
        assertEquals(1_200_000_000d,
                shield(restored, "utility_shield").accumulatedHeatJ(), 0d);
        assertEquals("ammo.test_kinetic_dart_150kg_v1",
                restored.engineering().instanceState().weaponFeeds().stream()
                        .filter(feed -> feed.mountId().equals("weapon_primary"))
                        .findFirst()
                        .orElseThrow()
                        .ammunitionContentId());
        assertNotNull(restored.sensorKnowledge());
        assertEquals(1, restored.sensorKnowledge().tracks().size());
        assertEquals(1, restored.sensorKnowledge().receivedMeasurements().size());
        assertEquals(1, restored.sensorKnowledge().pendingMeasurements().size());
    }

    private static ConsumableState consumeOnePhysicalRound(ConsumableState initial) {
        List<ConsumableLoad> loads = new ArrayList<>();
        boolean consumed = false;
        for (ConsumableLoad load : initial.interfaceLoads()) {
            if (!consumed && load.kind() == InterfaceKind.AMMUNITION && load.itemCount() > 0L) {
                double unitMassKg = load.massKg() / load.itemCount();
                loads.add(new ConsumableLoad(
                        load.mountId(), load.interfaceId(), load.kind(),
                        load.amount() - 1d, load.massKg() - unitMassKg, load.itemCount() - 1L));
                consumed = true;
            } else {
                loads.add(load);
            }
        }
        if (!consumed) {
            throw new IllegalStateException("Stage 17.5I doctrine fixture has no ammunition to deplete");
        }
        return new ConsumableState(
                initial.cargoMassKg(), initial.storesMassKg(), initial.missionPayloadMassKg(),
                initial.missionIntegrationVolumeM3(), List.copyOf(loads));
    }

    private static SensorKnowledgeComponent sensorKnowledge(EntityId observerId) {
        SensorKnowledgeComponent knowledge = new SensorKnowledgeComponent();
        TrackState track = new TrackState(
                17_500_902L,
                TrackState.InformationState.FIRE_CONTROL,
                true,
                48_000d,
                -11_000d,
                new TrackCovariance(64d, 0.0001d, 144d),
                0.94d,
                120d,
                2,
                4);
        SensorMeasurement measurement = new SensorMeasurement(
                observerId.value(), 17_500_902L, Channel.ACTIVE_RADAR, 120d,
                0d, 0d, -0.225d, 49_244d, 0.0001d, 25d,
                120d, 250d, 900d, TrackState.InformationState.FIRE_CONTROL);
        knowledge.putTrack(track);
        knowledge.receiveMeasurement(measurement);
        knowledge.queueMeasurement(measurement, 120.25d);
        return knowledge;
    }

    private static double mountValue(List<EntityState.MountDoubleState> values, String mountId) {
        return values.stream()
                .filter(value -> value.mountId().equals(mountId))
                .findFirst()
                .orElseThrow()
                .value();
    }

    private static EntityState.ShieldRuntimeState shield(EntityState state, String mountId) {
        return state.engineering().instanceState().shieldsByMount().stream()
                .filter(value -> value.mountId().equals(mountId))
                .findFirst()
                .orElseThrow();
    }
}
