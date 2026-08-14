package com.spacesim.player;

import com.spacesim.world.FleetId;
import com.spacesim.world.StarSystemId;

import java.util.List;
import java.util.Objects;

/**
 * Immutable first strategic-map projection of player-known galactic state.
 *
 * <p>The snapshot intentionally contains no arbitrary remote ECS entities. Systems/links come only
 * from discovered topology; fleet markers are player-owned FleetIds; danger comes only from stored
 * player threat intelligence.</p>
 *
 * @param systems discovered system markers
 * @param links known topology links between discovered systems
 * @param fleets player-owned fleet markers
 */
public record GlobalFleetMapSnapshot(
        List<SystemMarker> systems,
        List<LinkMarker> links,
        List<FleetMarker> fleets) {

    /**
     * Canonicalizes one map snapshot.
     *
     * @param systems discovered system markers
     * @param links known topology links
     * @param fleets owned fleet markers
     */
    public GlobalFleetMapSnapshot {
        systems = List.copyOf(Objects.requireNonNull(systems, "Global map systems not set"));
        links = List.copyOf(Objects.requireNonNull(links, "Global map links not set"));
        fleets = List.copyOf(Objects.requireNonNull(fleets, "Global map fleets not set"));
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
            float intelConfidence) {
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
            float intelConfidence) {
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
            FleetOrderType orderType) {
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
    }
}
