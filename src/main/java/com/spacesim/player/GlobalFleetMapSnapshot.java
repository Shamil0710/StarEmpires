package com.spacesim.player;

import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.ConstructionProjectStatus;
import com.spacesim.world.FleetId;
import com.spacesim.world.StarSystemId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable strategic-map projection of player-known galactic state and player-owned assets.
 *
 * <p>The snapshot intentionally contains no arbitrary remote ECS entities. Systems/links come only
 * from discovered topology; fleet markers are player-owned FleetIds; danger comes only from stored
 * player threat intelligence. Stage-16 construction/station markers are derived solely from
 * {@link PlayerConstructionManagementModel}, so ownership never becomes a back door for leaking
 * unrelated remote NPC markets or stations.</p>
 *
 * @param systems discovered system markers
 * @param links known topology links between discovered systems
 * @param fleets player-owned fleet markers
 * @param projects player-owned live construction-project markers
 * @param stations player-owned completed-station markers
 */
public record GlobalFleetMapSnapshot(
        List<SystemMarker> systems,
        List<LinkMarker> links,
        List<FleetMarker> fleets,
        List<ConstructionProjectMarker> projects,
        List<OwnedStationMarker> stations) {

    /**
     * Canonicalizes one map snapshot.
     *
     * @param systems discovered system markers
     * @param links known topology links
     * @param fleets owned fleet markers
     * @param projects owned construction-project markers
     * @param stations owned completed-station markers
     */
    public GlobalFleetMapSnapshot {
        systems = sortedCopy(systems, SystemMarker::compareTo, "Global map systems not set");
        links = sortedCopy(links, LinkMarker::compareTo, "Global map links not set");
        fleets = sortedCopy(fleets, FleetMarker::compareTo, "Global map fleets not set");
        projects = sortedCopy(projects, ConstructionProjectMarker::compareTo, "Global map projects not set");
        stations = sortedCopy(stations, OwnedStationMarker::compareTo, "Global map stations not set");
    }

    /**
     * Source-compatible Stage-15 constructor for snapshots without construction assets.
     *
     * @param systems discovered system markers
     * @param links known topology links
     * @param fleets owned fleet markers
     */
    public GlobalFleetMapSnapshot(
            List<SystemMarker> systems,
            List<LinkMarker> links,
            List<FleetMarker> fleets) {
        this(systems, links, fleets, List.of(), List.of());
    }

    /**
     * One discovered StarSystem on the strategic map.
     *
     * @param systemId stable system ID
     * @param name display name
     * @param galaxyX topology X coordinate
     * @param galaxyY topology Y coordinate
     * @param observedDanger effective stored system danger, not probability
     * @param intelConfidence stored observation confidence, zero when unknown
     */
    public record SystemMarker(
            StarSystemId systemId,
            String name,
            double galaxyX,
            double galaxyY,
            double observedDanger,
            float intelConfidence) implements Comparable<SystemMarker> {
        /**
         * Validates one discovered system marker.
         *
         * @param systemId stable system ID
         * @param name display name
         * @param galaxyX finite topology X
         * @param galaxyY finite topology Y
         * @param observedDanger non-negative observed score
         * @param intelConfidence confidence in [0,1]
         */
        public SystemMarker {
            systemId = Objects.requireNonNull(systemId, "Global map system ID not set");
            name = Objects.requireNonNull(name, "Global map system name not set").strip();
            if (name.isEmpty() || !Double.isFinite(galaxyX) || !Double.isFinite(galaxyY)
                    || !Double.isFinite(observedDanger) || observedDanger < 0d
                    || !Float.isFinite(intelConfidence) || intelConfidence < 0f || intelConfidence > 1f) {
                throw new IllegalArgumentException("Invalid global map system marker");
            }
        }

        @Override
        public int compareTo(SystemMarker other) {
            return systemId.compareTo(Objects.requireNonNull(other, "Other system marker not set").systemId);
        }
    }

    /**
     * One known topology corridor.
     *
     * @param first canonical first endpoint
     * @param second canonical second endpoint
     * @param observedDanger stored link danger score, not probability
     * @param intelConfidence stored observation confidence, zero when unknown
     */
    public record LinkMarker(
            StarSystemId first,
            StarSystemId second,
            double observedDanger,
            float intelConfidence) implements Comparable<LinkMarker> {
        /**
         * Validates and canonicalizes one known link.
         *
         * @param first first endpoint
         * @param second second endpoint
         * @param observedDanger non-negative observed score
         * @param intelConfidence confidence in [0,1]
         */
        public LinkMarker {
            first = Objects.requireNonNull(first, "Global map link endpoint not set");
            second = Objects.requireNonNull(second, "Global map link endpoint not set");
            if (first.equals(second)) {
                throw new IllegalArgumentException("Global map link requires two systems");
            }
            if (first.compareTo(second) > 0) {
                StarSystemId swap = first;
                first = second;
                second = swap;
            }
            if (!Double.isFinite(observedDanger) || observedDanger < 0d
                    || !Float.isFinite(intelConfidence) || intelConfidence < 0f || intelConfidence > 1f) {
                throw new IllegalArgumentException("Invalid global map link danger");
            }
        }

        @Override
        public int compareTo(LinkMarker other) {
            LinkMarker checked = Objects.requireNonNull(other, "Other link marker not set");
            int firstOrder = first.compareTo(checked.first);
            return firstOrder != 0 ? firstOrder : second.compareTo(checked.second);
        }
    }

    /**
     * One player-owned fleet on the strategic map.
     *
     * @param fleetId stable owned fleet ID
     * @param systemId current materialized system, or null during transit
     * @param transitDestination destination while jump transit is active, otherwise null
     * @param activeDirectControl whether this is the selected direct-control fleet
     * @param orderType current explicit delegated order type, or HOLD when none is assigned
     */
    public record FleetMarker(
            FleetId fleetId,
            StarSystemId systemId,
            StarSystemId transitDestination,
            boolean activeDirectControl,
            FleetOrderType orderType) implements Comparable<FleetMarker> {
        /**
         * Validates one owned fleet marker.
         *
         * @param fleetId stable owned fleet ID
         * @param systemId current system or null in transit
         * @param transitDestination active jump destination or null
         * @param activeDirectControl direct-control flag
         * @param orderType explicit/default order type
         */
        public FleetMarker {
            fleetId = Objects.requireNonNull(fleetId, "Global map FleetId not set");
            orderType = Objects.requireNonNull(orderType, "Global map fleet order not set");
            if (systemId == null && transitDestination == null) {
                throw new IllegalArgumentException("Global map fleet requires current or transit system");
            }
        }

        @Override
        public int compareTo(FleetMarker other) {
            return fleetId.compareTo(Objects.requireNonNull(other, "Other fleet marker not set").fleetId);
        }
    }

    /**
     * One player-owned live construction project on the global strategic map.
     *
     * @param projectId stable project ID
     * @param systemId physical project system
     * @param stationArchetypeContentId target station archetype content ID
     * @param stationDisplayName target station display name
     * @param status authoritative construction lifecycle status
     * @param buildProgress normalized BUILDING progress in [0,1]
     * @param missingMaterialUnits total real units still missing at the site
     * @param fundingShortfallMilliCredits minimum-funding shortfall in milli-credits
     * @param territorialAccessCurrentlyAllowed current construction-access result
     * @param supplyFleetIds owned fleets currently delegated to this project
     */
    public record ConstructionProjectMarker(
            ConstructionProjectId projectId,
            StarSystemId systemId,
            String stationArchetypeContentId,
            String stationDisplayName,
            ConstructionProjectStatus status,
            double buildProgress,
            long missingMaterialUnits,
            long fundingShortfallMilliCredits,
            boolean territorialAccessCurrentlyAllowed,
            List<FleetId> supplyFleetIds) implements Comparable<ConstructionProjectMarker> {
        /**
         * Validates and canonicalizes one construction-project map marker.
         *
         * @param projectId stable project ID
         * @param systemId physical system
         * @param stationArchetypeContentId target station archetype ID
         * @param stationDisplayName target station display name
         * @param status current lifecycle status
         * @param buildProgress normalized build progress
         * @param missingMaterialUnits non-negative missing material total
         * @param fundingShortfallMilliCredits non-negative minimum-funding shortfall
         * @param territorialAccessCurrentlyAllowed current access result
         * @param supplyFleetIds owned fleets supplying this site
         */
        public ConstructionProjectMarker {
            projectId = Objects.requireNonNull(projectId, "Global map construction project ID not set");
            systemId = Objects.requireNonNull(systemId, "Global map construction system not set");
            stationArchetypeContentId = requireText(
                    stationArchetypeContentId, "Global map construction archetype not set");
            stationDisplayName = requireText(stationDisplayName, "Global map construction name not set");
            status = Objects.requireNonNull(status, "Global map construction status not set");
            if (!Double.isFinite(buildProgress) || buildProgress < 0d || buildProgress > 1d
                    || missingMaterialUnits < 0L || fundingShortfallMilliCredits < 0L) {
                throw new IllegalArgumentException("Invalid global map construction marker values");
            }
            List<FleetId> fleets = new ArrayList<>(Objects.requireNonNull(
                    supplyFleetIds, "Global map construction supply fleets not set"));
            fleets.sort(FleetId::compareTo);
            supplyFleetIds = List.copyOf(fleets);
        }

        @Override
        public int compareTo(ConstructionProjectMarker other) {
            return projectId.compareTo(Objects.requireNonNull(other, "Other project marker not set").projectId);
        }
    }

    /**
     * One completed ordinary station physically owned by the player.
     *
     * @param reference persistent player ownership reference
     * @param stationArchetypeContentId physical station archetype content ID
     * @param stationDisplayName station display name
     * @param walletMilliCredits current real station operating-wallet balance
     * @param legalFactionContentId optional legal/faction affiliation
     */
    public record OwnedStationMarker(
            OwnedStationRef reference,
            String stationArchetypeContentId,
            String stationDisplayName,
            long walletMilliCredits,
            String legalFactionContentId) implements Comparable<OwnedStationMarker> {
        /**
         * Validates one completed-station map marker.
         *
         * @param reference persistent ownership reference
         * @param stationArchetypeContentId physical station archetype ID
         * @param stationDisplayName station display name
         * @param walletMilliCredits non-negative real operating balance
         * @param legalFactionContentId optional legal/faction content ID
         */
        public OwnedStationMarker {
            reference = Objects.requireNonNull(reference, "Global map owned station reference not set");
            stationArchetypeContentId = requireText(
                    stationArchetypeContentId, "Global map owned station archetype not set");
            stationDisplayName = requireText(stationDisplayName, "Global map owned station name not set");
            if (walletMilliCredits < 0L) {
                throw new IllegalArgumentException("Global map station wallet cannot be negative");
            }
            if (legalFactionContentId != null) {
                legalFactionContentId = requireText(
                        legalFactionContentId, "Global map station legal faction cannot be blank");
            }
        }

        /** @return physical station system carried by the ownership reference */
        public StarSystemId systemId() {
            return reference.systemId();
        }

        @Override
        public int compareTo(OwnedStationMarker other) {
            return reference.compareTo(Objects.requireNonNull(other, "Other station marker not set").reference);
        }
    }

    private static <T> List<T> sortedCopy(
            List<T> source,
            java.util.Comparator<? super T> comparator,
            String message) {
        List<T> copy = new ArrayList<>(Objects.requireNonNull(source, message));
        copy.sort(comparator);
        return List.copyOf(copy);
    }

    private static String requireText(String value, String message) {
        String checked = Objects.requireNonNull(value, message).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return checked;
    }
}
