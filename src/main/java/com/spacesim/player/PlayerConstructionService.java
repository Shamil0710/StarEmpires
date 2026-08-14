package com.spacesim.player;

import com.spacesim.content.ContentCatalog;
import com.spacesim.economy.Money;
import com.spacesim.world.ConstructionDurationPolicy;
import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.ConstructionProjectState;
import com.spacesim.world.ConstructionSettlementKind;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StarSystemId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Authoritative Stage-16 player adapter for querying and creating physical construction projects.
 *
 * <p>This service does not spawn a completed station. It creates the same world-level construction
 * project/site used by the Stage-9 simulation core and records only human ownership in
 * {@link PlayerState}. The world project uses {@link ConstructionSettlementKind#EXTERNAL_OWNER},
 * so an independent player does not need a hidden faction treasury before Stage 17.</p>
 *
 * <p>The first authoring slice deliberately requires the active owned FleetId to be physically
 * materialized in its current discovered system. Detailed clearance and territory/access checks are
 * the next Stage-16 placement-policy slice and remain outside UI code.</p>
 */
public final class PlayerConstructionService {
    private final PlayerRuntime runtime;

    /**
     * Creates a player construction adapter for one playable runtime.
     *
     * @param runtime current player/world runtime
     */
    public PlayerConstructionService(PlayerRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "PlayerRuntime not set");
    }

    /**
     * Returns all currently constructible station archetypes with authoritative cost/work data.
     *
     * <p>Technology/unlock filtering is intentionally not fabricated before the future tech-tier
     * model exists. For this Stage-16 slice, every catalog station carrying a construction
     * definition is exposed.</p>
     *
     * @return immutable list sorted by stable archetype content ID
     */
    public List<PlayerConstructionArchetypeView> buildableArchetypes() {
        ContentCatalog catalog = runtime.content();
        List<PlayerConstructionArchetypeView> result = new ArrayList<>();
        for (ContentCatalog.StationArchetypeDefinition station : catalog.getStationArchetypes()) {
            if (station.construction() == null) {
                continue;
            }
            ConstructionDurationPolicy.Estimate estimate = ConstructionDurationPolicy.estimate(catalog, station);
            Map<String, Integer> materials = new TreeMap<>(station.construction().materials());
            result.add(new PlayerConstructionArchetypeView(
                    station.id(),
                    station.displayName(),
                    Money.fromCredits(station.construction().fundingCredits()),
                    materials,
                    estimate.materialWorkUnits(),
                    estimate.totalSeconds()));
        }
        result.sort(Comparator.comparing(PlayerConstructionArchetypeView::archetypeContentId));
        return List.copyOf(result);
    }

    /**
     * Creates one independent player-owned construction project in the active fleet's system.
     *
     * @param stationArchetypeContentId constructible station archetype content ID
     * @param x finite local-system X coordinate
     * @param y finite local-system Y coordinate
     * @return stable world-level construction project ID
     * @throws IllegalArgumentException for unknown/non-constructible archetype or invalid coordinates
     * @throws IllegalStateException when no owned active fleet is physically present in a discovered system
     */
    public ConstructionProjectId createProject(
            String stationArchetypeContentId,
            float x,
            float y) {
        String archetypeId = requireConstructible(stationArchetypeContentId).id();
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException("Construction coordinates must be finite");
        }

        PlayerState current = runtime.player();
        FleetPlacementState placement = current.activeFleetId() == null
                ? null : runtime.world().findFleet(current.activeFleetId()).orElse(null);
        if (placement == null || placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
            throw new IllegalStateException("Active player fleet must be physically present to author construction");
        }
        StarSystemId systemId = placement.systemId();
        if (!current.discoveredSystemIds().contains(systemId)) {
            throw new IllegalStateException("Construction system must be discovered by the player");
        }

        ConstructionProjectId projectId = runtime.world().createConstructionProject(
                null, archetypeId, systemId, x, y);
        try {
            ConstructionProjectState state = runtime.world().findConstructionProject(projectId).orElseThrow();
            if (state.settlementKind() != ConstructionSettlementKind.EXTERNAL_OWNER
                    || state.ownerFactionContentId() != null
                    || state.legalFactionContentId() != null) {
                throw new IllegalStateException("Independent player project has invalid world settlement contract");
            }
            List<ConstructionProjectId> ownedProjects = new ArrayList<>(current.ownedConstructionProjectIds());
            ownedProjects.add(projectId);
            runtime.replacePlayerState(PlayerRuntime.copyWithConstructionOwnership(
                    current, ownedProjects, current.ownedStations()));
            return projectId;
        } catch (RuntimeException exception) {
            rollbackEmptyProject(projectId, exception);
            throw exception;
        }
    }

    private ContentCatalog.StationArchetypeDefinition requireConstructible(String value) {
        String id = Objects.requireNonNull(value, "Station archetype ID not set").strip();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Station archetype ID cannot be blank");
        }
        ContentCatalog.StationArchetypeDefinition station = runtime.content().findStationArchetype(id);
        if (station == null || station.construction() == null) {
            throw new IllegalArgumentException("Station archetype is not constructible: " + id);
        }
        return station;
    }

    private void rollbackEmptyProject(ConstructionProjectId projectId, RuntimeException cause) {
        try {
            runtime.world().cancelConstructionProject(projectId);
        } catch (RuntimeException rollbackFailure) {
            cause.addSuppressed(rollbackFailure);
        }
    }
}
