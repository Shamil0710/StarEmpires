package com.spacesim.world;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Exact local physical positions of already deployed individual craft, separate from mission intent. */
public record SmallCraftLocalFlightState(long lastProcessedTick, List<Flight> flights) {
    /** Maximum bounded local-flight roster size. */
    public static final int MAX_FLIGHTS = 100_000;

    /**
     * @param lastProcessedTick last completed world interval processed by this domain
     * @param flights existing individual craft and their exact local physical states
     */
    public SmallCraftLocalFlightState {
        if (lastProcessedTick < 0) throw new IllegalArgumentException("Negative local-flight watermark");
        Objects.requireNonNull(flights);
        if (flights.size() > MAX_FLIGHTS) throw new IllegalArgumentException("Too many local craft");
        flights = flights.stream().sorted(Comparator.comparing(Flight::craftId)).toList();
        for (int i = 0; i < flights.size(); i++) {
            if (flights.get(i).deployedTick() > lastProcessedTick)
                throw new IllegalArgumentException("Craft deployment exceeds local-flight watermark");
            if (i > 0 && flights.get(i - 1).craftId().equals(flights.get(i).craftId()))
                throw new IllegalArgumentException("Duplicate physical small-craft identity");
        }
    }

    /**
     * @param craftId already issued physical craft identity
     * @param systemId exact local physical system
     * @param hostStableId actual departure host identity
     * @param missionId mission that crossed the physical launch boundary
     * @param physical exact local position and velocity
     * @param deployedTick completed world tick of the physical handoff
     */
    public record Flight(SmallCraftId craftId, StarSystemId systemId, String hostStableId,
            long missionId, LocalPhysicalKinematics physical, long deployedTick) {
        /**
         * @param craftId existing craft
         * @param systemId actual system
         * @param hostStableId actual physical host
         * @param missionId positive launch mission
         * @param physical exact kinematics
         * @param deployedTick actual handoff tick
         */
        public Flight {
            Objects.requireNonNull(craftId); Objects.requireNonNull(systemId); Objects.requireNonNull(physical);
            Objects.requireNonNull(hostStableId);
            if (hostStableId.isBlank() || hostStableId.length() > 512 || missionId <= 0 || deployedTick < 0)
                throw new IllegalArgumentException("Invalid physical launch identity or time");
        }
    }

    /** @return no inferred positions or deployments for a historical campaign */
    public static SmallCraftLocalFlightState empty() { return new SmallCraftLocalFlightState(0, List.of()); }
}
