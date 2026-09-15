package com.spacesim.persistence;

import java.util.List;

/**
 * Version authority for the Stage-20.5 generated-world runtime checkpoint envelope.
 *
 * <p>The codec's binary header is regression-checked against this contract. Versions 1-3 remain
 * readable legacy formats; because they never stored custom multi-system scheduler configuration,
 * their migration reconstructs the historical {@code WorldSimulation} scheduler defaults. Version
 * 4/schema 3 persists scheduler configuration explicitly and is the first exact-continuation format
 * for generated worlds whose remote-update budget differs from those defaults.</p>
 */
public final class Stage20GeneratedWorldRuntimePersistenceContract {
    /** Current binary checkpoint file format. */
    public static final int CURRENT_FILE_FORMAT_VERSION = 4;
    /** Current value-state schema stored inside the current file format. */
    public static final int CURRENT_CHECKPOINT_SCHEMA_VERSION =
            Stage20GeneratedWorldRuntimePersistentState.CURRENT_VERSION;
    /** Current runtime composition contract restored by this persistence envelope. */
    public static final String CURRENT_BRIDGE_VERSION = Stage20GeneratedWorldRuntimeBridge.CURRENT_VERSION;
    /** Stable migration-table identity pinned by the Stage-22 core-pair freeze. */
    public static final String MIGRATION_VERSION = "stage20_5.generated-world-runtime-migration.v1";
    /** Binary file versions intentionally supported by the current decoder. */
    public static final List<Integer> SUPPORTED_FILE_FORMAT_VERSIONS = List.of(1, 2, 3, 4);

    private Stage20GeneratedWorldRuntimePersistenceContract() {
        throw new AssertionError("No instances");
    }
}
