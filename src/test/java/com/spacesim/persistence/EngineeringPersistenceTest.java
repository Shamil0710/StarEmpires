package com.spacesim.persistence;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class EngineeringPersistenceTest {
    @Test
    void entityMapperRoundTripPreservesOnlyFitAndPhysicalRuntimeState() {
        Entity entity = new Entity()
                .add(new EntityIdComponent(new EntityId(9001L)))
                .add(engineering());

        EntityState captured = EntityStateMapper.capture(entity);
        assertNotNull(captured.engineering());
        Entity restored = EntityStateMapper.restore(captured);
        EngineeringComponent component = restored.getComponent(EngineeringComponent.class);

        assertNotNull(component);
        assertEquals(engineering().fit, component.fit);
        assertEquals(engineering().runtimeState, component.runtimeState);
        assertEquals(captured, EntityStateMapper.capture(restored));
    }

    @Test
    void binaryV4RoundTripAndReencodePreserveEngineeringExactly() {
        GameState baseline = SimulationSession.createDemo(0x175C01L).snapshot();
        List<EntityState> entities = new ArrayList<>(baseline.entities());
        EntityState source = entities.get(0);
        EntityState.EngineeringState engineering = EntityStateMapper.capture(
                new Entity().add(new EntityIdComponent(new EntityId(991L))).add(engineering())).engineering();
        entities.set(0, new EntityState(
                source.id(), source.identity(), source.transform(), source.inventory(), source.wallet(),
                source.market(), source.production(), source.priceHistory(), source.faction(), source.reputation(),
                source.ship(), source.tradeAi(), source.mining(), source.combat(), source.asteroid(),
                source.archetype(), engineering));
        GameState state = new GameState(
                GameState.CURRENT_VERSION,
                baseline.rootSeed(), baseline.clock(), baseline.nextEntityIdValue(),
                baseline.eventRandomState(), baseline.asteroidRandomState(), baseline.events(),
                baseline.asteroidSpawner(), baseline.priceRecorder(), baseline.ledger(), List.copyOf(entities));

        byte[] bytes = GameStateCodec.encode(state);
        GameState decoded = GameStateCodec.decode(bytes);

        assertEquals(state, decoded);
        assertArrayEquals(bytes, GameStateCodec.encode(decoded));
        assertEquals(engineering, decoded.entities().get(0).engineering());
    }

    @Test
    void valueMigrationV3AddsNoSyntheticEngineeringState() {
        GameState baseline = SimulationSession.createDemo(0x175C02L).snapshot();
        EntityState original = baseline.entities().get(0);
        GameState v3 = new GameState(
                GameState.CONFIGURED_MARKET_TARGET_VERSION,
                baseline.rootSeed(), baseline.clock(), baseline.nextEntityIdValue(),
                baseline.eventRandomState(), baseline.asteroidRandomState(), baseline.events(),
                baseline.asteroidSpawner(), baseline.priceRecorder(), baseline.ledger(), baseline.entities());

        GameState migrated = GameStateMigration.toCurrent(v3);

        assertEquals(GameState.CURRENT_VERSION, migrated.schemaVersion());
        assertNull(migrated.entities().get(0).engineering());
        if (original.market() != null) {
            assertEquals(original.market().configuredTargetStock(),
                    migrated.entities().get(0).market().configuredTargetStock());
        }
    }

    private static EngineeringComponent engineering() {
        InstalledFit fit = new InstalledFit(
                "hull.persistence_test",
                List.of(
                        new InstalledModuleDefinition("core_drive", "module.drive_test"),
                        new InstalledModuleDefinition("core_ftl", "module.ftl_test")));
        ConsumableState loads = new ConsumableState(
                1250d,
                85d,
                40d,
                3d,
                List.of(new ConsumableLoad(
                        "core_drive", "propellant_feed", InterfaceKind.REACTION_MASS,
                        725d, 725d, 0L)));
        RuntimeState runtime = new RuntimeState(
                loads,
                42_000_000d,
                7_500_000d,
                Map.of("core_drive", 2_000_000d, "core_ftl", 3_000_000d),
                Map.of("core_drive", 125_000d),
                9_000_000d,
                Map.of("core_ftl", 17.5d));
        return new EngineeringComponent(fit, runtime);
    }
}
