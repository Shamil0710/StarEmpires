package com.spacesim.persistence;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.ship.EntityId;
import com.spacesim.ship.ShipEngineeringRuntime;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipInstanceRuntimeState;
import com.spacesim.ship.ShipyardEngineeringService.MaintenanceState;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import com.spacesim.ship.Stage175IPhysicalDestructionScenario;
import com.spacesim.ship.WeaponMountRuntime;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.ui.Stage175ITacticalVisualProjection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage175IPostCombatPersistenceAcceptanceTest {
    @Test
    void destroyedPhysicalShipRemainsDestroyedAfterProductionSaveLoad() {
        var destruction = Stage175IPhysicalDestructionScenario.run();
        var catalog = Stage175ICombatTestContentPack.loadDoctrines();
        var doctrine = Stage175IFleetDoctrineCatalog.get(DoctrineId.E_BALANCED_CONTROL);
        InstalledFit fit = InstalledFit.fromDemonstrator(catalog.findDemonstratorFit(doctrine.fitId()));
        ShipEngineeringRuntime runtime = new ShipEngineeringRuntime(catalog);
        var runtimeState = runtime.initialize(
                fit,
                doctrine.initialConsumables(),
                destruction.finalDamage().moduleDamage());
        ShipInstanceRuntimeState instanceState = new ShipInstanceRuntimeState(
                destruction.finalDamage(),
                Map.of("utility_shield", destruction.finalShield()),
                new MaintenanceState(Map.of()),
                doctrine.weaponLoadout(),
                WeaponMountRuntime.RuntimeState.empty());
        EntityId shipId = new EntityId(17_500_999L);
        Entity source = new Entity()
                .add(new EntityIdComponent(shipId))
                .add(new EngineeringComponent(fit, runtimeState, instanceState));
        EntityState captured = EntityStateMapper.capture(source);

        GameState baseline = SimulationSession.createDemo(0x1751F0L).snapshot();
        List<EntityState> entities = new ArrayList<>(baseline.entities());
        entities.set(0, captured);
        GameState state = new GameState(
                baseline.schemaVersion(), baseline.rootSeed(), baseline.clock(), baseline.nextEntityIdValue(),
                baseline.eventRandomState(), baseline.asteroidRandomState(), baseline.events(),
                baseline.asteroidSpawner(), baseline.priceRecorder(), baseline.ledger(), List.copyOf(entities));
        String fingerprint = ContentCatalogLoader.loadDefault().getFingerprint();

        byte[] encoded = ContentBoundSaveCodec.encode(state, fingerprint);
        ContentBoundSaveCodec.DecodedSave decoded = ContentBoundSaveCodec.decode(encoded);
        assertEquals(state, decoded.state());
        assertEquals(fingerprint, decoded.contentFingerprint());
        assertFalse(decoded.legacyRawFormat());
        assertArrayEquals(encoded, ContentBoundSaveCodec.encode(decoded.state(), fingerprint));

        Entity restoredEntity = EntityStateMapper.restore(decoded.state().entities().get(0));
        EngineeringComponent restored = restoredEntity.getComponent(EngineeringComponent.class);
        assertNotNull(restored);
        assertEquals(destruction.finalDamage(), restored.instanceState.damage);
        assertEquals(destruction.finalShield(), restored.instanceState.shieldStatesByMount().get("utility_shield"));
        assertTrue(restored.instanceState.damage.compartmentIntegrityById().values().stream()
                .allMatch(value -> value <= 0d));
        assertTrue(restored.instanceState.damage.moduleDamage().moduleIntegrityByMount().values().stream()
                .allMatch(value -> value <= 0d));

        var visual = new Stage175ITacticalVisualProjection()
                .addShip(
                        shipId.value(),
                        destruction.hull(),
                        restored.instanceState.damage,
                        1_000d,
                        700d,
                        0d,
                        0d,
                        destruction.fittedShield().definition(),
                        restored.instanceState.shieldStatesByMount().get("utility_shield"))
                .snapshot();
        assertTrue(visual.ships().get(0).wreck(),
                "post-combat load must remain a wreck instead of silently respawning a healthy ship");
    }
}
