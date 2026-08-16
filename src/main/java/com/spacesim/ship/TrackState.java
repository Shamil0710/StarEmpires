package com.spacesim.ship;

import java.util.Objects;

/**
 * Authoritative information state for one target hypothesis.
 *
 * <p>Unknown position is represented explicitly by {@code positionKnown=false}; x/y are then
 * canonical zero placeholders and must not be treated as target coordinates.</p>
 *
 * @param targetId stable target identity value
 * @param informationState current information quality state
 * @param positionKnown whether Cartesian target position has been solved
 * @param estimatedXM estimated x position when known
 * @param estimatedYM estimated y position when known
 * @param covariance current uncertainty state
 * @param classificationConfidence deterministic classification evidence in [0,1]
 * @param lastMeasurementSeconds authoritative time of the freshest fused measurement
 * @param contributingObservers number of distinct observers in the current solution
 * @param fusedMeasurementCount number of measurements fused into the current solution
 */
public record TrackState(
        long targetId,
        InformationState informationState,
        boolean positionKnown,
        double estimatedXM,
        double estimatedYM,
        TrackCovariance covariance,
        double classificationConfidence,
        double lastMeasurementSeconds,
        int contributingObservers,
        int fusedMeasurementCount) {

    /** Required Stage-17.5D information-quality progression. */
    public enum InformationState {
        /** Signal/contact exists but identity/range solution may be poor. */ DETECTED,
        /** Target class/identity evidence is sufficient for classification. */ CLASSIFIED,
        /** Position solution and covariance support a tactical track. */ TRACKED,
        /** Fresh covariance is good enough for downstream weapon-specific fire-control work. */ FIRE_CONTROL
    }

    /** Validates stable identity and explicit known/unknown position semantics. */
    public TrackState {
        if (targetId <= 0L) {
            throw new IllegalArgumentException("targetId must be positive");
        }
        Objects.requireNonNull(informationState, "informationState");
        Objects.requireNonNull(covariance, "covariance");
        if (!Double.isFinite(estimatedXM) || !Double.isFinite(estimatedYM)) {
            throw new IllegalArgumentException("track coordinates must be finite");
        }
        if (!positionKnown && (estimatedXM != 0d || estimatedYM != 0d)) {
            throw new IllegalArgumentException("unknown position must use canonical zero placeholders");
        }
        if (positionKnown != covariance.hasPositionCovariance()) {
            throw new IllegalArgumentException("positionKnown must match position covariance availability");
        }
        if (!Double.isFinite(classificationConfidence)
                || classificationConfidence < 0d || classificationConfidence > 1d) {
            throw new IllegalArgumentException("classificationConfidence must be finite in [0,1]");
        }
        if (!Double.isFinite(lastMeasurementSeconds)) {
            throw new IllegalArgumentException("lastMeasurementSeconds must be finite");
        }
        if (contributingObservers <= 0 || fusedMeasurementCount <= 0) {
            throw new IllegalArgumentException("track must have positive observer/measurement counts");
        }
    }

    /** @return track age in seconds at the supplied authoritative time */
    public double ageSeconds(double nowSeconds) {
        if (!Double.isFinite(nowSeconds) || nowSeconds < lastMeasurementSeconds) {
            throw new IllegalArgumentException("nowSeconds must be finite and not precede the last measurement");
        }
        return nowSeconds - lastMeasurementSeconds;
    }
}
