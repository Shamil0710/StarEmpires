package com.spacesim.ship;

import com.spacesim.ship.WeaponDefinition.GuidedWeapon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic Stage-17.5E layered-defense assignment scheduler.
 *
 * <p>The scheduler has no PD probability and no arbitrary maximum range. A threat must first have a
 * ballistic intersection with the defended zone. Candidate stations are then constrained by their
 * real position, safe-intercept geometry, launcher readiness, support channels, physical ammunition,
 * thermal availability and interceptor propulsion/time. Assignments are plans only; body propagation
 * and eventual collision remain physical.</p>
 */
public final class LayeredDefenseScheduler {
    private static final double EPSILON = 1e-9d;
    private static final int INTERCEPT_SEARCH_STEPS = 64;

    /**
     * Circular defended geometry in the tactical plane.
     *
     * @param centerXM protected center x coordinate in meters
     * @param centerYM protected center y coordinate in meters
     * @param radiusM protected physical radius in meters
     */
    public record DefendedZone(double centerXM, double centerYM, double radiusM) {
        /**
         * Validates defended-zone geometry.
         *
         * @param centerXM protected center x coordinate in meters
         * @param centerYM protected center y coordinate in meters
         * @param radiusM protected physical radius in meters
         */
        public DefendedZone {
            requireFinite(centerXM, "centerXM");
            requireFinite(centerYM, "centerYM");
            requirePositiveFinite(radiusM, "radiusM");
        }
    }

    /**
     * One incoming physical-body threat hypothesis.
     *
     * @param threatId stable deterministic physical/body identity
     * @param xM current x position in meters
     * @param yM current y position in meters
     * @param velocityXMps current x velocity in meters per second
     * @param velocityYMps current y velocity in meters per second
     * @param physicalMassKg current physical body mass
     * @param guidanceAvailable whether the body can still actively guide; false does not delete it
     */
    public record Threat(
            long threatId,
            double xM,
            double yM,
            double velocityXMps,
            double velocityYMps,
            double physicalMassKg,
            boolean guidanceAvailable) {
        /**
         * Validates one physical threat body.
         *
         * @param threatId stable deterministic physical/body identity
         * @param xM current x position in meters
         * @param yM current y position in meters
         * @param velocityXMps current x velocity in meters per second
         * @param velocityYMps current y velocity in meters per second
         * @param physicalMassKg current physical body mass
         * @param guidanceAvailable whether active guidance remains available
         */
        public Threat {
            if (threatId <= 0L) {
                throw new IllegalArgumentException("threatId must be positive");
            }
            requireFinite(xM, "xM");
            requireFinite(yM, "yM");
            requireFinite(velocityXMps, "velocityXMps");
            requireFinite(velocityYMps, "velocityYMps");
            requirePositiveFinite(physicalMassKg, "physicalMassKg");
        }
    }

    /**
     * One guided-interceptor station on a defending formation.
     *
     * @param stationId stable deterministic station identity
     * @param xM station x position in meters
     * @param yM station y position in meters
     * @param launchVelocityMps inherited/local launch speed available before interceptor burn
     * @param interceptor physical guided interceptor definition
     * @param launcherReady whether the launch cell has completed its cycle
     * @param supportChannelsAvailable currently free guidance/fire-control channels
     * @param ammunitionRounds physical interceptor rounds currently available
     * @param thermalAvailable whether required launcher/emitter thermal duty is currently allowed
     * @param safeMinimumInterceptDistanceM minimum acceptable intercept distance from defended center
     */
    public record DefenseStation(
            long stationId,
            double xM,
            double yM,
            double launchVelocityMps,
            GuidedWeapon interceptor,
            boolean launcherReady,
            int supportChannelsAvailable,
            long ammunitionRounds,
            boolean thermalAvailable,
            double safeMinimumInterceptDistanceM) {
        /**
         * Validates one physical defense station state.
         *
         * @param stationId stable deterministic station identity
         * @param xM station x position in meters
         * @param yM station y position in meters
         * @param launchVelocityMps inherited/local launch speed before interceptor burn
         * @param interceptor physical guided interceptor definition
         * @param launcherReady whether the launcher is cycle-ready
         * @param supportChannelsAvailable currently free support channels
         * @param ammunitionRounds physical interceptor rounds available
         * @param thermalAvailable whether thermal duty permits a launch
         * @param safeMinimumInterceptDistanceM minimum acceptable intercept distance from defended center
         */
        public DefenseStation {
            if (stationId <= 0L) {
                throw new IllegalArgumentException("stationId must be positive");
            }
            requireFinite(xM, "xM");
            requireFinite(yM, "yM");
            requireNonNegativeFinite(launchVelocityMps, "launchVelocityMps");
            Objects.requireNonNull(interceptor, "interceptor");
            if (supportChannelsAvailable < 0) {
                throw new IllegalArgumentException("supportChannelsAvailable must be non-negative");
            }
            if (ammunitionRounds < 0L) {
                throw new IllegalArgumentException("ammunitionRounds must be non-negative");
            }
            requireNonNegativeFinite(safeMinimumInterceptDistanceM, "safeMinimumInterceptDistanceM");
        }
    }

    /**
     * One deterministic physical defense assignment.
     *
     * @param threatId assigned threat identity
     * @param stationId assigned station identity
     * @param predictedImpactSeconds predicted ballistic impact time without successful defense
     * @param plannedInterceptSeconds planned launch-to-intercept time from now
     * @param interceptXM predicted intercept x coordinate
     * @param interceptYM predicted intercept y coordinate
     */
    public record Assignment(
            long threatId,
            long stationId,
            double predictedImpactSeconds,
            double plannedInterceptSeconds,
            double interceptXM,
            double interceptYM) {
        /**
         * Validates one immutable assignment.
         *
         * @param threatId assigned threat identity
         * @param stationId assigned station identity
         * @param predictedImpactSeconds predicted ballistic impact time
         * @param plannedInterceptSeconds planned intercept time
         * @param interceptXM predicted intercept x coordinate
         * @param interceptYM predicted intercept y coordinate
         */
        public Assignment {
            if (threatId <= 0L || stationId <= 0L) {
                throw new IllegalArgumentException("assignment identities must be positive");
            }
            requirePositiveFinite(predictedImpactSeconds, "predictedImpactSeconds");
            requirePositiveFinite(plannedInterceptSeconds, "plannedInterceptSeconds");
            requireFinite(interceptXM, "interceptXM");
            requireFinite(interceptYM, "interceptYM");
            if (plannedInterceptSeconds > predictedImpactSeconds + EPSILON) {
                throw new IllegalArgumentException("intercept cannot be planned after predicted impact");
            }
        }
    }

    /**
     * Assigns physically feasible defenses in deterministic impact-time order.
     *
     * @param zone protected geometry
     * @param threats incoming physical bodies
     * @param stations available defense stations
     * @return deterministic immutable assignments
     */
    public List<Assignment> schedule(
            DefendedZone zone,
            List<Threat> threats,
            List<DefenseStation> stations) {
        DefendedZone checkedZone = Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(threats, "threats");
        Objects.requireNonNull(stations, "stations");

        List<ThreatWithImpact> inbound = new ArrayList<>();
        for (Threat threat : threats) {
            Threat checked = Objects.requireNonNull(threat, "threat");
            double impactSeconds = predictedImpactSeconds(checkedZone, checked);
            if (Double.isFinite(impactSeconds)) {
                inbound.add(new ThreatWithImpact(checked, impactSeconds));
            }
        }
        inbound.sort(Comparator.comparingDouble(ThreatWithImpact::impactSeconds)
                .thenComparingLong(value -> value.threat().threatId()));

        List<DefenseStation> orderedStations = new ArrayList<>(stations);
        if (orderedStations.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("stations must not contain null");
        }
        orderedStations.sort(Comparator.comparingLong(DefenseStation::stationId));
        Map<Long, Integer> channelsRemaining = new HashMap<>();
        Map<Long, Long> ammunitionRemaining = new HashMap<>();
        for (DefenseStation station : orderedStations) {
            channelsRemaining.put(station.stationId(), station.supportChannelsAvailable());
            ammunitionRemaining.put(station.stationId(), station.ammunitionRounds());
        }

        List<Assignment> assignments = new ArrayList<>();
        for (ThreatWithImpact inboundThreat : inbound) {
            for (DefenseStation station : orderedStations) {
                if (!station.launcherReady()
                        || !station.thermalAvailable()
                        || channelsRemaining.get(station.stationId()) <= 0
                        || ammunitionRemaining.get(station.stationId()) <= 0L) {
                    continue;
                }
                InterceptSolution solution = findIntercept(
                        checkedZone,
                        inboundThreat.threat(),
                        inboundThreat.impactSeconds(),
                        station);
                if (solution == null) {
                    continue;
                }
                assignments.add(new Assignment(
                        inboundThreat.threat().threatId(),
                        station.stationId(),
                        inboundThreat.impactSeconds(),
                        solution.timeSeconds(),
                        solution.xM(),
                        solution.yM()));
                channelsRemaining.put(station.stationId(), channelsRemaining.get(station.stationId()) - 1);
                ammunitionRemaining.put(station.stationId(), ammunitionRemaining.get(station.stationId()) - 1L);
                break;
            }
        }
        return List.copyOf(assignments);
    }

    private static double predictedImpactSeconds(DefendedZone zone, Threat threat) {
        double rx = threat.xM() - zone.centerXM();
        double ry = threat.yM() - zone.centerYM();
        double vx = threat.velocityXMps();
        double vy = threat.velocityYMps();
        double a = vx * vx + vy * vy;
        double b = 2d * (rx * vx + ry * vy);
        double c = rx * rx + ry * ry - zone.radiusM() * zone.radiusM();
        if (c <= 0d) {
            return EPSILON;
        }
        if (a <= EPSILON) {
            return Double.NaN;
        }
        double discriminant = b * b - 4d * a * c;
        if (discriminant < 0d) {
            return Double.NaN;
        }
        double root = Math.sqrt(discriminant);
        double first = (-b - root) / (2d * a);
        double second = (-b + root) / (2d * a);
        double best = Double.POSITIVE_INFINITY;
        if (first > EPSILON) {
            best = first;
        }
        if (second > EPSILON && second < best) {
            best = second;
        }
        return Double.isFinite(best) ? best : Double.NaN;
    }

    private static InterceptSolution findIntercept(
            DefendedZone zone,
            Threat threat,
            double impactSeconds,
            DefenseStation station) {
        for (int step = 1; step < INTERCEPT_SEARCH_STEPS; step++) {
            double time = impactSeconds * step / INTERCEPT_SEARCH_STEPS;
            double threatX = threat.xM() + threat.velocityXMps() * time;
            double threatY = threat.yM() + threat.velocityYMps() * time;
            double distanceFromProtectedCenter = Math.hypot(threatX - zone.centerXM(), threatY - zone.centerYM());
            if (distanceFromProtectedCenter + EPSILON < station.safeMinimumInterceptDistanceM()) {
                continue;
            }
            double requiredDistance = Math.hypot(threatX - station.xM(), threatY - station.yM());
            if (reachableDistance(station, time) + EPSILON >= requiredDistance) {
                return new InterceptSolution(time, threatX, threatY);
            }
        }
        return null;
    }

    private static double reachableDistance(DefenseStation station, double timeSeconds) {
        GuidedWeapon interceptor = station.interceptor();
        double massFlow = interceptor.massFlowKgPerS();
        double fuelBurnLimit = interceptor.propellantMassKg() / massFlow;
        double burnTime = Math.min(timeSeconds, Math.min(interceptor.burnTimeSeconds(), fuelBurnLimit));
        double consumed = Math.min(interceptor.propellantMassKg(), massFlow * burnTime);
        double initialMass = interceptor.wetMassKg();
        double finalMass = initialMass - consumed;
        double deltaV = consumed <= EPSILON
                ? 0d
                : interceptor.exhaustVelocityMps() * Math.log(initialMass / finalMass);
        double initialAcceleration = interceptor.thrustN() / initialMass;
        double poweredDistance = station.launchVelocityMps() * burnTime
                + 0.5d * initialAcceleration * burnTime * burnTime;
        double coastTime = Math.max(0d, timeSeconds - burnTime);
        double coastSpeed = station.launchVelocityMps() + deltaV;
        return poweredDistance + coastSpeed * coastTime;
    }

    private record ThreatWithImpact(Threat threat, double impactSeconds) {
    }

    private record InterceptSolution(double timeSeconds, double xM, double yM) {
    }

    private static void requirePositiveFinite(double value, String label) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
    }

    private static void requireNonNegativeFinite(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
    }
}
