package com.spacesim.ui;

import com.spacesim.world.StarSystemId;

import java.util.List;
import java.util.Objects;

/** Read-only presentation snapshot for the global strategic galaxy map. */
public record GalaxyStrategicMapSnapshot(
        String galaxyName,
        List<SystemView> systems,
        List<EdgeView> edges,
        List<FactionView> factions,
        StarSystemId activeSystemId,
        StarSystemId selectedNeighborId) {

    public GalaxyStrategicMapSnapshot {
        galaxyName = Objects.requireNonNull(galaxyName, "Galaxy map name not set");
        systems = List.copyOf(Objects.requireNonNull(systems, "Galaxy map systems not set"));
        edges = List.copyOf(Objects.requireNonNull(edges, "Galaxy map edges not set"));
        factions = List.copyOf(Objects.requireNonNull(factions, "Galaxy map factions not set"));
    }

    public record SystemView(
            StarSystemId id,
            String name,
            String sectorName,
            double galaxyX,
            double galaxyY,
            String controllerFactionId,
            String controllerDisplayName,
            int neighborCount,
            boolean active,
            boolean selectedNeighbor) {
        public SystemView {
            Objects.requireNonNull(id, "Galaxy map system ID not set");
            name = Objects.requireNonNull(name, "Galaxy map system name not set");
            sectorName = Objects.requireNonNull(sectorName, "Galaxy map sector name not set");
            controllerDisplayName = Objects.requireNonNull(
                    controllerDisplayName, "Galaxy map controller display name not set");
            if (!Double.isFinite(galaxyX) || !Double.isFinite(galaxyY) || neighborCount < 0) {
                throw new IllegalArgumentException("Invalid galaxy map system geometry/count");
            }
        }
    }

    public record EdgeView(StarSystemId first, StarSystemId second, boolean touchesActiveSystem) {
        public EdgeView {
            Objects.requireNonNull(first, "Galaxy map edge first system not set");
            Objects.requireNonNull(second, "Galaxy map edge second system not set");
            if (first.equals(second)) {
                throw new IllegalArgumentException("Galaxy map edge cannot be self-connected");
            }
        }
    }

    public record FactionView(
            String factionId,
            String displayName,
            int controlledSystems,
            long treasuryMilliCredits,
            int stationTaxBasisPoints,
            int territorialTariffBasisPoints,
            int customsTariffBasisPoints,
            int activeClaims,
            int strategicGoals,
            int treatyRecords,
            int embargoRecords) {
        public FactionView {
            factionId = Objects.requireNonNull(factionId, "Faction map ID not set");
            displayName = Objects.requireNonNull(displayName, "Faction map display name not set");
            if (controlledSystems < 0 || treasuryMilliCredits < 0L
                    || stationTaxBasisPoints < 0 || territorialTariffBasisPoints < 0
                    || customsTariffBasisPoints < 0 || activeClaims < 0 || strategicGoals < 0
                    || treatyRecords < 0 || embargoRecords < 0) {
                throw new IllegalArgumentException("Faction map counters cannot be negative");
            }
        }
    }
}
