package com.spacesim.player;

import java.util.Objects;

/**
 * Stage-17G read-only composition boundary for the strategic map and faction-management panel.
 */
public final class FactionGlobalMapModel {
    private FactionGlobalMapModel() {
        throw new AssertionError("FactionGlobalMapModel does not create instances");
    }

    /**
     * Captures both player-known global-map information and authoritative faction management state.
     *
     * @param runtime current playable runtime
     * @return immutable composed strategic snapshot
     */
    public static FactionGlobalMapSnapshot capture(PlayerRuntime runtime) {
        PlayerRuntime checked = Objects.requireNonNull(runtime, "PlayerRuntime not set");
        return new FactionGlobalMapSnapshot(
                GlobalFleetMapModel.capture(checked),
                FactionManagementModel.capture(checked));
    }
}
