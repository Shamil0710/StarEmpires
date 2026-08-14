package com.spacesim.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable Stage-16 management snapshot shared by project UI and strategic-map projections.
 *
 * @param projects canonical list of live player-owned construction projects
 * @param stations canonical list of live physical player-owned completed stations
 */
public record PlayerConstructionManagementSnapshot(
        List<PlayerConstructionProjectView> projects,
        List<PlayerOwnedStationView> stations) {

    /**
     * Canonicalizes one construction-management snapshot.
     *
     * @param projects live owned projects
     * @param stations live owned completed stations
     */
    public PlayerConstructionManagementSnapshot {
        List<PlayerConstructionProjectView> projectCopy = new ArrayList<>(
                Objects.requireNonNull(projects, "Construction management projects not set"));
        projectCopy.sort(PlayerConstructionProjectView::compareTo);
        projects = List.copyOf(projectCopy);
        List<PlayerOwnedStationView> stationCopy = new ArrayList<>(
                Objects.requireNonNull(stations, "Construction management stations not set"));
        stationCopy.sort(PlayerOwnedStationView::compareTo);
        stations = List.copyOf(stationCopy);
    }
}
