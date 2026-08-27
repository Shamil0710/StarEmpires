package com.spacesim.persistence;

import com.spacesim.world.DiplomaticLifecycleState;
import com.spacesim.world.FactionLivingActorState;
import com.spacesim.world.FactionStrategicIntentState;
import com.spacesim.world.FleetCommandState;
import com.spacesim.world.SettlementRecoveryState;
import com.spacesim.world.Stage21HNpcMissionState;
import com.spacesim.world.StrategicOperationState;
import com.spacesim.world.TerritorialTransitionState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Explicit fail-closed backward migration into the final Stage-21 checkpoint.
 *
 * <p>Migration never reconstructs or rewrites an authority that already exists in the source. Each
 * lift preserves the complete source envelope and adds only the empty, current-schema sidecars owned
 * by later stages. Stage-20.5 migration creates initial actor lifecycle/intent rows for exactly the
 * factions already present in the saved world; all physical world, economy, freight, ownership,
 * diplomacy and industrial state remains inside the original Stage-20 checkpoint.</p>
 */
public final class Stage21IGeneratedWorldRuntimeMigration {
    private Stage21IGeneratedWorldRuntimeMigration() {
        throw new AssertionError("No instances");
    }

    /**
     * Decodes any supported generated-world checkpoint from Stage 20.5 through Stage 21H and lifts it.
     *
     * @param bytes source checkpoint bytes
     * @return final Stage-21I checkpoint preserving every authority present in the source
     */
    public static Stage21IGeneratedWorldRuntimePersistentState migrateSupported(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Cannot migrate an empty generated-world checkpoint");
        }

        IllegalArgumentException last = null;
        try {
            return migrate(Stage21HGeneratedWorldRuntimePersistenceCodec.decode(bytes));
        } catch (IllegalArgumentException exception) {
            last = exception;
        }
        try {
            return migrate(Stage21GGeneratedWorldRuntimePersistenceCodec.decode(bytes));
        } catch (IllegalArgumentException exception) {
            last = exception;
        }
        try {
            return migrate(Stage21FGeneratedWorldRuntimePersistenceCodec.decode(bytes));
        } catch (IllegalArgumentException exception) {
            last = exception;
        }
        try {
            return migrate(Stage21EGeneratedWorldRuntimePersistenceCodec.decode(bytes));
        } catch (IllegalArgumentException exception) {
            last = exception;
        }
        try {
            return migrate(Stage21DGeneratedWorldRuntimePersistenceCodec.decode(bytes));
        } catch (IllegalArgumentException exception) {
            last = exception;
        }
        try {
            return migrate(Stage21CGeneratedWorldRuntimePersistenceCodec.decode(bytes));
        } catch (IllegalArgumentException exception) {
            last = exception;
        }
        try {
            return migrate(Stage21BGeneratedWorldRuntimePersistenceCodec.decode(bytes));
        } catch (IllegalArgumentException exception) {
            last = exception;
        }
        try {
            return migrate(Stage21AGeneratedWorldRuntimePersistenceCodec.decode(bytes));
        } catch (IllegalArgumentException exception) {
            last = exception;
        }
        try {
            return migrate(Stage20GeneratedWorldRuntimePersistenceCodec.decode(bytes));
        } catch (IllegalArgumentException exception) {
            last = exception;
        }
        throw new IllegalArgumentException(
                "Unsupported or corrupt generated-world checkpoint; supported sources are Stage 20.5 and Stage 21A-H",
                last);
    }

    /**
     * Lifts an accepted Stage-20.5 checkpoint into current Stage-21 authorities.
     *
     * @param stage20 accepted Stage-20.5 checkpoint
     * @return final Stage-21I checkpoint with deterministic initial later-stage sidecars
     */
    public static Stage21IGeneratedWorldRuntimePersistentState migrate(
            Stage20GeneratedWorldRuntimePersistentState stage20) {
        Objects.requireNonNull(stage20, "stage20");
        long tick = authoritativeWorldTick(stage20);
        long firstReviewTick = Math.addExact(tick, 1L);

        ArrayList<FactionLivingActorState> actors = new ArrayList<>();
        ArrayList<FactionStrategicIntentState> intents = new ArrayList<>();
        stage20.worldState().factions().stream()
                .map(faction -> faction.factionContentId())
                .distinct()
                .sorted()
                .forEach(factionId -> {
                    actors.add(FactionLivingActorState.initial(factionId, firstReviewTick));
                    intents.add(FactionStrategicIntentState.initial(factionId));
                });
        if (actors.isEmpty()) {
            throw new IllegalArgumentException("Stage-20.5 migration requires at least one saved faction actor");
        }

        Stage21AGeneratedWorldRuntimePersistentState stage21A = new Stage21AGeneratedWorldRuntimePersistentState(
                Stage21AGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                Stage21AGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                stage20,
                List.copyOf(actors));
        Stage21BGeneratedWorldRuntimePersistentState stage21B = new Stage21BGeneratedWorldRuntimePersistentState(
                Stage21BGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                Stage21BGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                stage21A,
                List.copyOf(intents));
        return finishFromStage21B(stage21B, tick, "stage20.5.v2");
    }

    /**
     * Preserves Stage-21A state and adds only later-stage empty sidecars.
     *
     * @param stage21A accepted Stage-21A checkpoint
     * @return final Stage-21I checkpoint preserving Stage-21A authority
     */
    public static Stage21IGeneratedWorldRuntimePersistentState migrate(
            Stage21AGeneratedWorldRuntimePersistentState stage21A) {
        Objects.requireNonNull(stage21A, "stage21A");
        long tick = authoritativeWorldTick(stage21A.stage20Runtime());
        List<FactionStrategicIntentState> intents = stage21A.livingActors().stream()
                .map(actor -> FactionStrategicIntentState.initial(actor.factionContentId()))
                .toList();
        Stage21BGeneratedWorldRuntimePersistentState stage21B = new Stage21BGeneratedWorldRuntimePersistentState(
                Stage21BGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                Stage21BGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                stage21A,
                intents);
        return finishFromStage21B(stage21B, tick, "stage21a.v1");
    }

    /**
     * Preserves Stage-21B state and adds only later-stage empty sidecars.
     *
     * @param stage21B accepted Stage-21B checkpoint
     * @return final Stage-21I checkpoint preserving Stage-21B authority
     */
    public static Stage21IGeneratedWorldRuntimePersistentState migrate(
            Stage21BGeneratedWorldRuntimePersistentState stage21B) {
        Objects.requireNonNull(stage21B, "stage21B");
        long tick = authoritativeWorldTick(stage21B.stage21ARuntime().stage20Runtime());
        return finishFromStage21B(stage21B, tick, "stage21b.v5");
    }

    /**
     * Preserves Stage-21C state and adds only later-stage empty sidecars.
     *
     * @param stage21C accepted Stage-21C checkpoint
     * @return final Stage-21I checkpoint preserving Stage-21C authority
     */
    public static Stage21IGeneratedWorldRuntimePersistentState migrate(
            Stage21CGeneratedWorldRuntimePersistentState stage21C) {
        Objects.requireNonNull(stage21C, "stage21C");
        long tick = authoritativeWorldTick(stage21C.stage21BRuntime().stage21ARuntime().stage20Runtime());
        return finishFromStage21C(stage21C, tick, "stage21c.v6");
    }

    /**
     * Preserves Stage-21D state and adds only later-stage empty sidecars.
     *
     * @param stage21D accepted Stage-21D checkpoint
     * @return final Stage-21I checkpoint preserving Stage-21D authority
     */
    public static Stage21IGeneratedWorldRuntimePersistentState migrate(
            Stage21DGeneratedWorldRuntimePersistentState stage21D) {
        Objects.requireNonNull(stage21D, "stage21D");
        long tick = authoritativeWorldTick(stage21D.stage21CRuntime().stage21BRuntime()
                .stage21ARuntime().stage20Runtime());
        return finishFromStage21D(stage21D, tick, "stage21d.v7");
    }

    /**
     * Preserves Stage-21E state and adds only later-stage empty sidecars.
     *
     * @param stage21E accepted Stage-21E checkpoint
     * @return final Stage-21I checkpoint preserving Stage-21E authority
     */
    public static Stage21IGeneratedWorldRuntimePersistentState migrate(
            Stage21EGeneratedWorldRuntimePersistentState stage21E) {
        Objects.requireNonNull(stage21E, "stage21E");
        long tick = authoritativeWorldTick(stage21E.stage21DRuntime().stage21CRuntime().stage21BRuntime()
                .stage21ARuntime().stage20Runtime());
        return finishFromStage21E(stage21E, tick, "stage21e.v8");
    }

    /**
     * Preserves Stage-21F state and adds only later-stage empty sidecars.
     *
     * @param stage21F accepted Stage-21F checkpoint
     * @return final Stage-21I checkpoint preserving Stage-21F authority
     */
    public static Stage21IGeneratedWorldRuntimePersistentState migrate(
            Stage21FGeneratedWorldRuntimePersistentState stage21F) {
        Objects.requireNonNull(stage21F, "stage21F");
        long tick = authoritativeWorldTick(stage21F.stage21ERuntime().stage21DRuntime().stage21CRuntime()
                .stage21BRuntime().stage21ARuntime().stage20Runtime());
        return finishFromStage21F(stage21F, tick, "stage21f.v9");
    }

    /**
     * Preserves Stage-21G state and adds only the Stage-21H RPG sidecar.
     *
     * @param stage21G accepted Stage-21G checkpoint
     * @return final Stage-21I checkpoint preserving Stage-21G authority
     */
    public static Stage21IGeneratedWorldRuntimePersistentState migrate(
            Stage21GGeneratedWorldRuntimePersistentState stage21G) {
        Objects.requireNonNull(stage21G, "stage21G");
        long tick = authoritativeWorldTick(stage21G.stage21FRuntime().stage21ERuntime().stage21DRuntime()
                .stage21CRuntime().stage21BRuntime().stage21ARuntime().stage20Runtime());
        Stage21HGeneratedWorldRuntimePersistentState stage21H = Stage21HGeneratedWorldRuntimePersistentState.compose(
                stage21G, Stage21HNpcMissionState.empty(tick));
        return Stage21IGeneratedWorldRuntimePersistentState.migrated(stage21H, "stage21g.v10", tick);
    }

    /**
     * Preserves the complete Stage-21H authority chain and only adopts the final envelope.
     *
     * @param stage21H accepted Stage-21H checkpoint
     * @return final Stage-21I checkpoint preserving the complete Stage-21H authority chain
     */
    public static Stage21IGeneratedWorldRuntimePersistentState migrate(
            Stage21HGeneratedWorldRuntimePersistentState stage21H) {
        Objects.requireNonNull(stage21H, "stage21H");
        long tick = Stage21IGeneratedWorldRuntimePersistentState.authoritativeWorldTick(stage21H);
        return Stage21IGeneratedWorldRuntimePersistentState.migrated(stage21H, "stage21h.v11", tick);
    }

    private static Stage21IGeneratedWorldRuntimePersistentState finishFromStage21B(
            Stage21BGeneratedWorldRuntimePersistentState stage21B,
            long tick,
            String source) {
        Stage21CGeneratedWorldRuntimePersistentState stage21C = new Stage21CGeneratedWorldRuntimePersistentState(
                Stage21CGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                Stage21CGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                stage21B,
                DiplomaticLifecycleState.empty(tick),
                Stage19ConflictState.empty(tick));
        return finishFromStage21C(stage21C, tick, source);
    }

    private static Stage21IGeneratedWorldRuntimePersistentState finishFromStage21C(
            Stage21CGeneratedWorldRuntimePersistentState stage21C,
            long tick,
            String source) {
        Stage21DGeneratedWorldRuntimePersistentState stage21D = Stage21DGeneratedWorldRuntimePersistentState.compose(
                stage21C, FleetCommandState.empty());
        return finishFromStage21D(stage21D, tick, source);
    }

    private static Stage21IGeneratedWorldRuntimePersistentState finishFromStage21D(
            Stage21DGeneratedWorldRuntimePersistentState stage21D,
            long tick,
            String source) {
        Stage21EGeneratedWorldRuntimePersistentState stage21E = Stage21EGeneratedWorldRuntimePersistentState.compose(
                stage21D, StrategicOperationState.empty());
        return finishFromStage21E(stage21E, tick, source);
    }

    private static Stage21IGeneratedWorldRuntimePersistentState finishFromStage21E(
            Stage21EGeneratedWorldRuntimePersistentState stage21E,
            long tick,
            String source) {
        Stage21FGeneratedWorldRuntimePersistentState stage21F = Stage21FGeneratedWorldRuntimePersistentState.compose(
                stage21E, TerritorialTransitionState.empty());
        return finishFromStage21F(stage21F, tick, source);
    }

    private static Stage21IGeneratedWorldRuntimePersistentState finishFromStage21F(
            Stage21FGeneratedWorldRuntimePersistentState stage21F,
            long tick,
            String source) {
        Stage21GGeneratedWorldRuntimePersistentState stage21G = Stage21GGeneratedWorldRuntimePersistentState.compose(
                stage21F, SettlementRecoveryState.empty(tick));
        Stage21HGeneratedWorldRuntimePersistentState stage21H = Stage21HGeneratedWorldRuntimePersistentState.compose(
                stage21G, Stage21HNpcMissionState.empty(tick));
        return Stage21IGeneratedWorldRuntimePersistentState.migrated(stage21H, source, tick);
    }

    private static long authoritativeWorldTick(Stage20GeneratedWorldRuntimePersistentState stage20) {
        return stage20.worldState().systems().stream()
                .filter(system -> system.systemId().equals(stage20.activeSystemId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Migration source active system is absent from saved world state"))
                .simulationState().clock().tick();
    }
}
