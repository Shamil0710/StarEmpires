package com.spacesim.world;

import java.util.List;
import java.util.Objects;

/**
 * Optional physical logistics authority for finite reaction-mass route planning and local servicing.
 *
 * <p>The interface does not own topology, jump movement or station inventory. Implementations inspect
 * those existing authorities and may consume station stock only from
 * {@link #prepareDeparture(FleetId, List)}. Pure planning must never mutate ship or station state.</p>
 */
public interface FleetPropellantLogisticsAuthority {
    /**
     * Plans one ordered neighbor route with projected intermediate refueling.
     *
     * @param fleetId stable physical fleet identity
     * @param orderedSystems route beginning at the fleet's current system
     * @return immutable physical journey plan
     */
    JourneyPlan planJourney(FleetId fleetId, List<StarSystemId> orderedSystems);

    /**
     * Physically performs only refueling required in the fleet's current system, then revalidates
     * the same remaining route against the now-authoritative ship/station state.
     *
     * @param fleetId stable physical fleet identity
     * @param orderedSystems remaining route beginning at the current system
     * @return immutable departure preparation result
     */
    DeparturePreparation prepareDeparture(FleetId fleetId, List<StarSystemId> orderedSystems);

    /**
     * One projected physical refueling action.
     *
     * @param systemId system containing the servicing endpoint
     * @param stationId exact station endpoint identity
     * @param bindingId authored commodity-to-interface binding
     * @param mountId fitted receiving module mount
     * @param commodityId finite station commodity consumed by servicing
     * @param massKg projected physical mass loaded
     */
    record RefuelStop(
            StarSystemId systemId,
            String stationId,
            String bindingId,
            String mountId,
            String commodityId,
            double massKg) {
        /** Validates one projected refueling action. */
        public RefuelStop {
            Objects.requireNonNull(systemId, "systemId");
            stationId = requireText(stationId, "stationId");
            bindingId = requireText(bindingId, "bindingId");
            mountId = requireText(mountId, "mountId");
            commodityId = requireText(commodityId, "commodityId");
            if (!Double.isFinite(massKg) || massKg <= 0d) {
                throw new IllegalArgumentException("massKg must be finite and positive");
            }
        }
    }

    /**
     * Immutable projected journey diagnostics.
     *
     * @param supported whether a physical refueling authority is bound
     * @param feasible whether every route segment can be completed under projected finite stock
     * @param orderedSystems exact route that was evaluated
     * @param refuelStops deterministic projected intermediate/current-system service actions
     * @param requiredDeltaVMps total local maneuver delta-v of the route
     * @param projectedRemainingReactionMassKg reaction mass projected after the final local maneuver
     * @param reason stable failure/compatibility reason
     */
    record JourneyPlan(
            boolean supported,
            boolean feasible,
            List<StarSystemId> orderedSystems,
            List<RefuelStop> refuelStops,
            double requiredDeltaVMps,
            double projectedRemainingReactionMassKg,
            String reason) {
        /** Validates and freezes one journey plan. */
        public JourneyPlan {
            orderedSystems = List.copyOf(Objects.requireNonNull(orderedSystems, "orderedSystems"));
            refuelStops = List.copyOf(Objects.requireNonNull(refuelStops, "refuelStops"));
            if (!Double.isFinite(requiredDeltaVMps) || requiredDeltaVMps < 0d
                    || !Double.isFinite(projectedRemainingReactionMassKg)
                    || projectedRemainingReactionMassKg < 0d) {
                throw new IllegalArgumentException("journey physical scalars must be finite and non-negative");
            }
            reason = reason == null ? "" : reason;
        }

        /**
         * Creates an explicit compatibility result for worlds without generated finite-station
         * refueling authority.
         *
         * @param orderedSystems evaluated route
         * @param feasible direct finite-fuel feasibility reported by the existing jump authority
         * @param requiredDeltaVMps direct-route maneuver budget
         * @param remainingReactionMassKg projected direct-route remaining reaction mass
         * @param reason compatibility/direct-route reason
         * @return immutable unsupported journey result preserving historical direct-route behavior
         */
        public static JourneyPlan compatibility(
                List<StarSystemId> orderedSystems,
                boolean feasible,
                double requiredDeltaVMps,
                double remainingReactionMassKg,
                String reason) {
            return new JourneyPlan(
                    false,
                    feasible,
                    orderedSystems,
                    List.of(),
                    requiredDeltaVMps,
                    Math.max(0d, remainingReactionMassKg),
                    reason);
        }
    }

    /**
     * Result of physically servicing only the current departure system.
     *
     * @param supported whether physical station servicing authority is bound
     * @param ready whether the remaining route is safe after committed local servicing
     * @param loadedMassKg mass physically removed from current-system station storage
     * @param stationIds deterministic stations actually used by this preparation
     * @param journey revalidated post-service journey
     * @param reason stable failure/compatibility reason
     */
    record DeparturePreparation(
            boolean supported,
            boolean ready,
            double loadedMassKg,
            List<String> stationIds,
            JourneyPlan journey,
            String reason) {
        /** Validates and freezes one departure preparation. */
        public DeparturePreparation {
            if (!Double.isFinite(loadedMassKg) || loadedMassKg < 0d) {
                throw new IllegalArgumentException("loadedMassKg must be finite and non-negative");
            }
            stationIds = List.copyOf(Objects.requireNonNull(stationIds, "stationIds"));
            Objects.requireNonNull(journey, "journey");
            reason = reason == null ? "" : reason;
        }
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
        return value.strip();
    }
}
