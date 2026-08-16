package com.spacesim.player;

import java.util.Objects;

/**
 * Immutable Stage-17G composition used by the strategic/global-map UI.
 *
 * <p>The map retains Stage-15 non-omniscient topology/fleet visibility while the management panel
 * receives the separate authoritative faction projection. Neither side contains mutation callbacks;
 * commands remain in {@link PlayerStrategicCommandService} and {@link PlayerFactionManagementService}.</p>
 *
 * @param map existing player-known global fleet/construction map
 * @param management current faction-management projection
 */
public record FactionGlobalMapSnapshot(
        GlobalFleetMapSnapshot map,
        FactionManagementSnapshot management) {

    /**
     * Validates the composed strategic UI snapshot.
     *
     * @param map player-known global map
     * @param management faction-management projection
     */
    public FactionGlobalMapSnapshot {
        Objects.requireNonNull(map, "Global map snapshot not set");
        Objects.requireNonNull(management, "Faction management snapshot not set");
    }
}
