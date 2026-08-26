package com.spacesim.persistence;

import com.spacesim.world.StarSystemSimulationState;

import java.util.Objects;

/**
 * Final Stage-21 generated-world checkpoint envelope.
 *
 * <p>Stage 21I introduces no competing simulation authority. The complete accepted Stage-21H
 * checkpoint is embedded unchanged; this envelope owns only final-format identity and deterministic
 * migration provenance so old supported generated-world saves can be upgraded once and then continue
 * through the same Stage-21A..H authorities.</p>
 *
 * @param schemaVersion exact Stage-21I checkpoint schema
 * @param runtimeVersion exact Stage-21I runtime contract identifier
 * @param stage21HRuntime complete accepted Stage-21H checkpoint
 * @param migrationProvenance deterministic source-format provenance for this checkpoint lineage
 */
public record Stage21IGeneratedWorldRuntimePersistentState(
        int schemaVersion,
        String runtimeVersion,
        Stage21HGeneratedWorldRuntimePersistentState stage21HRuntime,
        MigrationProvenance migrationProvenance) {

    /** Current final Stage-21 checkpoint schema. */
    public static final int CURRENT_VERSION = 12;
    /** Current final Stage-21 runtime contract. */
    public static final String CURRENT_RUNTIME_VERSION = "stage21i.generated-world-final-gate.v12";

    /**
     * Deterministic migration lineage. This is checkpoint metadata, never gameplay authority.
     *
     * @param sourceFormat stable source-format token
     * @param migrated whether this lineage entered Stage-21I through backward migration
     * @param migrationTick authoritative world tick at which the migration/adoption occurred
     */
    public record MigrationProvenance(String sourceFormat, boolean migrated, long migrationTick) {
        /** Validates immutable migration metadata. */
        public MigrationProvenance {
            sourceFormat = requireText(sourceFormat, "sourceFormat");
            if (migrationTick < 0L) {
                throw new IllegalArgumentException("migrationTick cannot be negative");
            }
        }
    }

    /** Validates final-envelope identity and authoritative clock bounds. */
    public Stage21IGeneratedWorldRuntimePersistentState {
        if (schemaVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-21I checkpoint schema: " + schemaVersion);
        }
        runtimeVersion = requireText(runtimeVersion, "runtimeVersion");
        if (!CURRENT_RUNTIME_VERSION.equals(runtimeVersion)) {
            throw new IllegalArgumentException("Unsupported Stage-21I runtime version: " + runtimeVersion);
        }
        Objects.requireNonNull(stage21HRuntime, "stage21HRuntime");
        Objects.requireNonNull(migrationProvenance, "migrationProvenance");
        long worldTick = authoritativeWorldTick(stage21HRuntime);
        if (migrationProvenance.migrationTick() > worldTick) {
            throw new IllegalArgumentException("Stage-21I migration provenance is ahead of authoritative world time");
        }
    }

    /**
     * Adopts an already-current Stage-21H checkpoint into the final Stage-21 format without migration.
     *
     * @param stage21H complete accepted Stage-21H checkpoint
     * @return deterministic final Stage-21 checkpoint
     */
    public static Stage21IGeneratedWorldRuntimePersistentState compose(
            Stage21HGeneratedWorldRuntimePersistentState stage21H) {
        long tick = authoritativeWorldTick(Objects.requireNonNull(stage21H, "stage21H"));
        return new Stage21IGeneratedWorldRuntimePersistentState(
                CURRENT_VERSION,
                CURRENT_RUNTIME_VERSION,
                stage21H,
                new MigrationProvenance("stage21h.native", false, tick));
    }

    /**
     * Creates a final checkpoint from an explicitly migrated supported source.
     *
     * @param stage21H complete lifted Stage-21H authority chain
     * @param sourceFormat stable source format token
     * @param migrationTick authoritative migration tick
     * @return deterministic final Stage-21 checkpoint retaining migration lineage
     */
    public static Stage21IGeneratedWorldRuntimePersistentState migrated(
            Stage21HGeneratedWorldRuntimePersistentState stage21H,
            String sourceFormat,
            long migrationTick) {
        return new Stage21IGeneratedWorldRuntimePersistentState(
                CURRENT_VERSION,
                CURRENT_RUNTIME_VERSION,
                Objects.requireNonNull(stage21H, "stage21H"),
                new MigrationProvenance(sourceFormat, true, migrationTick));
    }

    /** Returns the authoritative active-system clock embedded in the ordinary Stage-20 world. */
    public static long authoritativeWorldTick(Stage21HGeneratedWorldRuntimePersistentState stage21H) {
        var stage20 = stage21H.stage21GRuntime().stage21FRuntime().stage21ERuntime()
                .stage21DRuntime().stage21CRuntime().stage21BRuntime().stage21ARuntime().stage20Runtime();
        return stage20.worldState().systems().stream()
                .filter(system -> system.systemId().equals(stage20.activeSystemId()))
                .map(StarSystemSimulationState::simulationState)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Stage-21I checkpoint active system is absent from saved world state"))
                .clock().tick();
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
