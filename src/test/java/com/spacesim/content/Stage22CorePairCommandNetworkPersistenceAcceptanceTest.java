package com.spacesim.content;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.Stage22CorePairExperimentProtocol.Permutation;
import com.spacesim.persistence.ContentBoundSaveCodec;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityState;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.persistence.GameState;
import com.spacesim.ship.LiveTacticalBattleControlRuntime;
import com.spacesim.ship.LiveTacticalBattleRuntimeState;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.ImportedCombatantState;
import com.spacesim.ship.LiveTacticalInitialReadinessService;
import com.spacesim.ship.Stage22CorePairTacticalFactory;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 B01/B11 production save-continuation evidence for degraded command and sensors.
 *
 * <p>The existing paired B11 batch proves deterministic fresh-run relay behaviour. This probe closes
 * the missing protocol step from {@code faction_balance_validation_framework.md} section 6.1 without
 * inventing a tactical save authority. The authoritative persistent facts are ordinary entity
 * engineering/transform state: failed local radar and, for negative lanes, failed fitted datalink
 * endpoints. They are captured through {@link EntityStateMapper}, round-tripped through the ordinary
 * {@link ContentBoundSaveCodec}, restored, and imported back into the existing Stage-19 exact tactical
 * runtime. Actor-visible contacts and control intent are recomputed by the normal sensor/datalink/
 * control chain after load; no transient contact list or planner decision is serialized by the test.</p>
 */
class Stage22CorePairCommandNetworkPersistenceAcceptanceTest {
    private static final String SENSOR_MOUNT = "utility_sensor";
    private static final String NETWORK_MOUNT = "utility_defense";
    private static final int CONTROL_WINDOW_TICKS = 20;
    private static final long SAVE_SEED = 22_611L;

    @Test
    void b11PhysicalDegradationSaveLoadPreservesRelayAndFailClosedContinuation() {
        for (Permutation permutation : Permutation.values()) {
            assertContinuation(permutation, BreakMode.NONE);
            assertContinuation(permutation, BreakMode.RECEIVER);
            assertContinuation(permutation, BreakMode.SENDER);
        }
    }

    private static void assertContinuation(Permutation permutation, BreakMode breakMode) {
        Stage22CorePairTacticalFactory.CommandNetworkSkirmish direct =
                Stage22CorePairTacticalFactory.createCommandNetworkSkirmish(permutation, SAVE_SEED);
        LiveTacticalBattleRuntimeState directBattle = direct.control().battleState();
        applyDegradation(directBattle, breakMode);

        PersistedRoster persisted = persist(directBattle);
        LiveTacticalBattleRuntimeState restoredBattle = LiveTacticalBattleRuntimeState.importExact(
                restoredCombatants(directBattle, persisted.entitiesById()),
                direct.content().engineering(),
                direct.protection());
        LiveTacticalBattleControlRuntime restoredControl = new LiveTacticalBattleControlRuntime(restoredBattle);

        for (LiveTacticalBattleRuntimeState.CombatantRuntime combatant : directBattle.combatants()) {
            EngineeringComponent restoredEngineering = restoredBattle
                    .requireCombatant(combatant.spec().entityId()).engineering();
            assertEquals(combatant.engineering().fit, restoredEngineering.fit);
            assertEquals(combatant.engineering().runtimeState, restoredEngineering.runtimeState);
            assertEquals(combatant.engineering().instanceState, restoredEngineering.instanceState);
        }

        for (int tick = 0; tick < CONTROL_WINDOW_TICKS; tick++) {
            direct.control().advanceOneTick();
            restoredControl.advanceOneTick();
        }

        assertEquals(direct.control().fingerprint(), restoredControl.fingerprint(),
                "B11 control continuation changed after ordinary entity save/load for "
                        + permutation + "/" + breakMode);
        assertPrimaryOutcomeEquals(direct.control(), restoredControl,
                Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID);
        assertPrimaryOutcomeEquals(direct.control(), restoredControl,
                Stage22CorePairTacticalFactory.UNION_ENTITY_ID);

        int empireContacts = restoredBattle.visibleContacts(Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID).size();
        int unionContacts = restoredBattle.visibleContacts(Stage22CorePairTacticalFactory.UNION_ENTITY_ID).size();
        if (breakMode == BreakMode.NONE) {
            assertTrue(empireContacts > 0,
                    "intact Empire relay must reconstruct hostile awareness after load");
            assertTrue(unionContacts > 0,
                    "intact Union relay must reconstruct hostile awareness after load");
        } else {
            assertEquals(0, empireContacts, "broken physical datalink must remain fail-closed after load");
            assertEquals(0, unionContacts, "broken physical datalink must remain fail-closed after load");
        }
    }

    private static void assertPrimaryOutcomeEquals(
            LiveTacticalBattleControlRuntime direct,
            LiveTacticalBattleControlRuntime restored,
            long entityId) {
        assertEquals(
                direct.battleState().visibleContacts(entityId),
                restored.battleState().visibleContacts(entityId));
        assertEquals(direct.controlState(entityId), restored.controlState(entityId));
    }

    private static void applyDegradation(LiveTacticalBattleRuntimeState battle, BreakMode breakMode) {
        LiveTacticalInitialReadinessService initial = new LiveTacticalInitialReadinessService();
        initial.setModuleIntegrity(
                battle.requireCombatant(Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID), SENSOR_MOUNT, 0d);
        initial.setModuleIntegrity(
                battle.requireCombatant(Stage22CorePairTacticalFactory.UNION_ENTITY_ID), SENSOR_MOUNT, 0d);
        if (breakMode == BreakMode.RECEIVER) {
            initial.setModuleIntegrity(
                    battle.requireCombatant(Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID), NETWORK_MOUNT, 0d);
            initial.setModuleIntegrity(
                    battle.requireCombatant(Stage22CorePairTacticalFactory.UNION_ENTITY_ID), NETWORK_MOUNT, 0d);
        } else if (breakMode == BreakMode.SENDER) {
            initial.setModuleIntegrity(
                    battle.requireCombatant(Stage22CorePairTacticalFactory.EMPIRE_COMMAND_WINGMAN_ID), NETWORK_MOUNT, 0d);
            initial.setModuleIntegrity(
                    battle.requireCombatant(Stage22CorePairTacticalFactory.UNION_COMMAND_WINGMAN_ID), NETWORK_MOUNT, 0d);
        }
    }

    private static PersistedRoster persist(LiveTacticalBattleRuntimeState battle) {
        GameState baseline = SimulationSession.createDemo(SAVE_SEED).snapshot();
        ArrayList<EntityState> entities = new ArrayList<>(baseline.entities());
        for (LiveTacticalBattleRuntimeState.CombatantRuntime combatant : battle.combatants()) {
            Entity source = persistentEntity(combatant);
            entities.add(EntityStateMapper.capture(source));
        }
        long nextEntityId = Math.max(
                baseline.nextEntityIdValue(),
                Stage22CorePairTacticalFactory.UNION_COMMAND_WINGMAN_ID + 1L);
        GameState checkpoint = new GameState(
                baseline.schemaVersion(),
                baseline.rootSeed(),
                baseline.clock(),
                nextEntityId,
                baseline.eventRandomState(),
                baseline.asteroidRandomState(),
                baseline.events(),
                baseline.asteroidSpawner(),
                baseline.priceRecorder(),
                baseline.ledger(),
                List.copyOf(entities));
        String fingerprint = ContentCatalogLoader.loadDefault().getFingerprint();
        byte[] encoded = ContentBoundSaveCodec.encode(checkpoint, fingerprint);
        ContentBoundSaveCodec.DecodedSave decoded = ContentBoundSaveCodec.decode(encoded);
        assertArrayEquals(encoded, ContentBoundSaveCodec.encode(decoded.state(), fingerprint),
                "B11 ordinary save envelope must be byte-stable");
        assertEquals(fingerprint, decoded.contentFingerprint());

        HashMap<Long, Entity> restored = new HashMap<>();
        for (EntityState state : decoded.state().entities()) {
            long id = state.id().value();
            if (isCommandEntity(id)) {
                restored.put(id, EntityStateMapper.restore(state));
            }
        }
        assertEquals(4, restored.size(), "all four exact B11 command-network combatants must restore");
        return new PersistedRoster(Map.copyOf(restored));
    }

    private static Entity persistentEntity(LiveTacticalBattleRuntimeState.CombatantRuntime combatant) {
        TransformComponent transform = new TransformComponent();
        transform.position.set(combatant.transform().position);
        transform.velocity.set(combatant.transform().velocity);
        EngineeringComponent engineering = combatant.engineering();
        return new Entity()
                .add(new EntityIdComponent(new EntityId(combatant.spec().entityId())))
                .add(transform)
                .add(new EngineeringComponent(engineering.fit, engineering.runtimeState, engineering.instanceState));
    }

    private static List<ImportedCombatantState> restoredCombatants(
            LiveTacticalBattleRuntimeState source,
            Map<Long, Entity> restoredById) {
        ArrayList<ImportedCombatantState> restored = new ArrayList<>();
        for (LiveTacticalBattleRuntimeState.CombatantRuntime original : source.combatants()) {
            Entity entity = restoredById.get(original.spec().entityId());
            if (entity == null) {
                throw new AssertionError("missing restored B11 combatant " + original.spec().entityId());
            }
            EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            restored.add(new ImportedCombatantState(
                    original.spec().entityId(),
                    original.spec().side(),
                    engineering,
                    transform.position.x,
                    transform.position.y,
                    transform.velocity.x,
                    transform.velocity.y));
        }
        return List.copyOf(restored);
    }

    private static boolean isCommandEntity(long entityId) {
        return entityId == Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID
                || entityId == Stage22CorePairTacticalFactory.EMPIRE_COMMAND_WINGMAN_ID
                || entityId == Stage22CorePairTacticalFactory.UNION_ENTITY_ID
                || entityId == Stage22CorePairTacticalFactory.UNION_COMMAND_WINGMAN_ID;
    }

    private enum BreakMode {
        NONE,
        RECEIVER,
        SENDER
    }

    private record PersistedRoster(Map<Long, Entity> entitiesById) { }
}
