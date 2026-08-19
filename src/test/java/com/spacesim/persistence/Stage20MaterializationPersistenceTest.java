package com.spacesim.persistence;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.persistence.Stage20MaterializationPersistentState.PhysicalEntityState;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.simulation.Stage20MaterializationService;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.LocalPhysicalPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20MaterializationPersistenceTest {
    @TempDir
    Path tempDirectory;

    @Test
    void dematerializedEntityAndFarPhysicalStateSurviveBinarySaveLoadExactly() throws Exception {
        SimulationSession session = SimulationSession.createDemo(20_021L);
        Stage20MaterializationService materialization = Stage20MaterializationService.forSession(session);
        Entity original = firstPersistentEntity(session);
        EntityId id = original.getComponent(EntityIdComponent.class).id;
        EntityState before = EntityStateMapper.capture(original);
        int entityCountBefore = session.snapshot().entities().size();
        LocalPhysicalKinematics physical = new LocalPhysicalKinematics(
                new LocalPhysicalPosition(9_000_000_000L, -8_000_000_000L, 45_678.125d, -12_345.75d),
                12_345.625d,
                -6_789.375d);

        materialization.registerPhysicalState(id, physical);
        materialization.dematerialize(id);
        assertFalse(session.getEntityRegistry().contains(id));

        Stage20MaterializationPersistentState captured =
                Stage20MaterializationPersistence.capture(session, materialization);

        assertEquals(GameState.CURRENT_VERSION, captured.gameState().schemaVersion());
        assertEquals(entityCountBefore, captured.gameState().entities().size());
        assertEquals(before, state(captured.gameState().entities(), id));
        assertEquals(physical, physical(captured, id).physicalState());

        byte[] firstEncoding = Stage20MaterializationPersistenceCodec.encode(captured);
        byte[] secondEncoding = Stage20MaterializationPersistenceCodec.encode(captured);
        assertArrayEquals(firstEncoding, secondEncoding);
        Stage20MaterializationPersistentState decoded =
                Stage20MaterializationPersistenceCodec.decode(firstEncoding);
        assertEquals(captured, decoded);

        Path save = tempDirectory.resolve("stage20-materialization.bin");
        Stage20MaterializationPersistenceCodec.write(save, captured);
        assertEquals(captured, Stage20MaterializationPersistenceCodec.read(save));

        Stage20MaterializationPersistence.RestoredRuntime restored =
                Stage20MaterializationPersistence.restore(decoded);
        Entity restoredEntity = restored.session().getEntityRegistry().require(id);

        assertEquals(before, EntityStateMapper.capture(restoredEntity));
        assertEquals(physical, restored.materialization().physicalState(id).orElseThrow());
        assertFalse(restored.materialization().isDematerialized(id));
        assertEquals(entityCountBefore, restored.session().snapshot().entities().size());
    }

    @Test
    void representationLevelIsNotPersistedButCausalPhysicalStateIs() {
        SimulationSession session = SimulationSession.createDemo(20_022L);
        Stage20MaterializationService materialization = Stage20MaterializationService.forSession(session);
        Entity entity = firstPersistentEntity(session);
        EntityId id = entity.getComponent(EntityIdComponent.class).id;
        LocalPhysicalKinematics physical = new LocalPhysicalKinematics(
                new LocalPhysicalPosition(4_500_000_000L, 2_500_000_000L, 800d, -900d),
                700d,
                -300d);

        materialization.registerPhysicalState(id, physical);
        materialization.dematerialize(id);
        Stage20MaterializationPersistentState state =
                Stage20MaterializationPersistence.capture(session, materialization);
        Stage20MaterializationPersistence.RestoredRuntime restored =
                Stage20MaterializationPersistence.restore(
                        Stage20MaterializationPersistenceCodec.decode(
                                Stage20MaterializationPersistenceCodec.encode(state)));

        assertTrue(restored.session().getEntityRegistry().contains(id));
        assertFalse(restored.materialization().isDematerialized(id));
        assertEquals(physical, restored.materialization().physicalState(id).orElseThrow());
    }

    @Test
    void envelopeRejectsPhysicalStateForEntityAbsentFromGameState() {
        SimulationSession session = SimulationSession.createDemo(20_023L);
        GameState base = session.snapshot();
        long maximumId = base.entities().stream().mapToLong(value -> value.id().value()).max().orElseThrow();
        PhysicalEntityState orphan = new PhysicalEntityState(
                new EntityId(maximumId + 1_000L),
                LocalPhysicalKinematics.stationary(LocalPhysicalPosition.origin()));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new Stage20MaterializationPersistentState(
                        Stage20MaterializationPersistentState.CURRENT_VERSION,
                        base,
                        List.of(orphan)));

        assertTrue(error.getMessage().contains("absent from GameState"));
    }

    @Test
    void coreGameStateV4CodecRemainsIndependentAndUnchanged() {
        SimulationSession session = SimulationSession.createDemo(20_024L);
        GameState core = session.snapshot();

        assertEquals(4, GameState.CURRENT_VERSION);
        byte[] encoded = GameStateCodec.encode(core);
        assertEquals(core, GameStateCodec.decode(encoded));
    }

    private static Entity firstPersistentEntity(SimulationSession session) {
        for (Entity entity : session.getEngine().getEntities()) {
            if (entity.getComponent(EntityIdComponent.class) != null) {
                return entity;
            }
        }
        throw new AssertionError("Demo session has no persistent entity");
    }

    private static EntityState state(List<EntityState> states, EntityId id) {
        return states.stream()
                .filter(value -> value.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static PhysicalEntityState physical(Stage20MaterializationPersistentState state, EntityId id) {
        return state.physicalEntities().stream()
                .filter(value -> value.id().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
