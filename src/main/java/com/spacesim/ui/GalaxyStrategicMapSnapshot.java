package com.spacesim.ui;

import com.spacesim.world.StarSystemId;

import java.util.List;
import java.util.Objects;

/**
 * Read-only presentation snapshot for the global strategic galaxy map.
 *
 * @param galaxyName galaxy display name
 * @param systems strategic system markers
 * @param edges explicit jump-edge markers
 * @param factions faction summaries
 * @param activeSystemId currently active system, or {@code null}
 * @param selectedNeighborId selected direct jump neighbor, or {@code null}
 */
public record GalaxyStrategicMapSnapshot(
        String galaxyName,
        List<SystemView> systems,
        List<EdgeView> edges,
        List<FactionView> factions,
        StarSystemId activeSystemId,
        StarSystemId selectedNeighborId) {

    /**
     * Validates and defensively copies all top-level strategic presentation collections.
     *
     * @param galaxyName galaxy display name
     * @param systems strategic system markers
     * @param edges explicit jump-edge markers
     * @param factions faction summaries
     * @param activeSystemId currently active system, or {@code null}
     * @param selectedNeighborId selected direct jump neighbor, or {@code null}
     */
    public GalaxyStrategicMapSnapshot {
        galaxyName = Objects.requireNonNull(galaxyName, "Galaxy map name not set");
        systems = List.copyOf(Objects.requireNonNull(systems, "Galaxy map systems not set"));
        edges = List.copyOf(Objects.requireNonNull(edges, "Galaxy map edges not set"));
        factions = List.copyOf(Objects.requireNonNull(factions, "Galaxy map factions not set"));
    }

    /**
     * One strategic system marker derived from topology and current territorial control.
     *
     * @param id stable system ID
     * @param name system display name
     * @param sectorName containing sector display name
     * @param galaxyX authoritative strategic X coordinate
     * @param galaxyY authoritative strategic Y coordinate
     * @param controllerFactionId controlling faction ID, or {@code null}
     * @param controllerDisplayName resolved controller display name
     * @param neighborCount number of direct jump neighbors
     * @param active whether this is the currently active system
     * @param selectedNeighbor whether this is the selected direct jump neighbor
     */
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
        /**
         * Validates one immutable strategic system marker.
         *
         * @param id stable system ID
         * @param name system display name
         * @param sectorName containing sector display name
         * @param galaxyX authoritative strategic X coordinate
         * @param galaxyY authoritative strategic Y coordinate
         * @param controllerFactionId controlling faction ID, or {@code null}
         * @param controllerDisplayName resolved controller display name
         * @param neighborCount number of direct jump neighbors
         * @param active whether this is the currently active system
         * @param selectedNeighbor whether this is the selected direct jump neighbor
         */
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

    /**
     * One explicit authoritative jump connection rendered on the global map.
     *
     * @param first first endpoint
     * @param second second endpoint
     * @param touchesActiveSystem whether the edge touches the active system
     */
    public record EdgeView(StarSystemId first, StarSystemId second, boolean touchesActiveSystem) {
        /**
         * Validates an immutable non-self jump connection marker.
         *
         * @param first first endpoint
         * @param second second endpoint
         * @param touchesActiveSystem whether the edge touches the active system
         */
        public EdgeView {
            Objects.requireNonNull(first, "Galaxy map edge first system not set");
            Objects.requireNonNull(second, "Galaxy map edge second system not set");
            if (first.equals(second)) {
                throw new IllegalArgumentException("Galaxy map edge cannot be self-connected");
            }
        }
    }

    /**
     * Read-only Stage-17 faction summary displayed beside the strategic map.
     *
     * @param factionId stable faction ID
     * @param displayName faction display name
     * @param controlledSystems number of currently controlled systems
     * @param treasuryMilliCredits authoritative treasury balance in milli-credits
     * @param stationTaxBasisPoints own-station tax in basis points
     * @param territorialTariffBasisPoints foreign-territory/transit levy in basis points
     * @param customsTariffBasisPoints customs tariff in basis points
     * @param activeClaims number of current territorial claim records
     * @param strategicGoals number of current strategic goal records
     * @param treatyRecords number of treaty records
     * @param embargoRecords number of embargo records
     */
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
        /**
         * Validates non-negative diagnostic counters and required faction identity fields.
         *
         * @param factionId stable faction ID
         * @param displayName faction display name
         * @param controlledSystems number of currently controlled systems
         * @param treasuryMilliCredits authoritative treasury balance in milli-credits
         * @param stationTaxBasisPoints own-station tax in basis points
         * @param territorialTariffBasisPoints foreign-territory/transit levy in basis points
         * @param customsTariffBasisPoints customs tariff in basis points
         * @param activeClaims number of current territorial claim records
         * @param strategicGoals number of current strategic goal records
         * @param treatyRecords number of treaty records
         * @param embargoRecords number of embargo records
         */
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
