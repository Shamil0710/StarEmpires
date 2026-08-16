package com.spacesim.ship;

import com.spacesim.ship.TrackState.InformationState;
import com.spacesim.ship.WeaponDefinition.BeamWeapon;

import java.util.Objects;

/**
 * Deterministic Stage-17.5E beam geometry and duty solver.
 *
 * <p>A beam does not materialize a fake projectile body. Range enters continuously through
 * diffraction and pointing jitter; the result exposes spot geometry, energy, irradiance, electrical
 * demand and heat for later material/thermal integration. No authoritative hard range or hit chance
 * exists in this runtime.</p>
 */
public final class BeamWeaponRuntime {
    /** Stable reason why a requested beam dwell cannot be planned. */
    public enum Failure {
        /** Physical beam solution exists. */ NONE,
        /** Track lacks solved Cartesian range/position. */ TRACK_POSITION_UNKNOWN,
        /** Track has not reached fire-control quality. */ FIRE_CONTROL_INSUFFICIENT,
        /** Requested dwell exceeds the emitter's authored continuous-duty limit. */ DWELL_LIMIT_EXCEEDED
    }

    /**
     * Deterministic physical beam solution at one target range.
     *
     * @param allowed whether the requested dwell can be executed
     * @param failure stable rejection reason or NONE
     * @param targetId target track identity
     * @param rangeM current geometric range in meters
     * @param diffractionRadiusM diffraction-limited spot-radius contribution
     * @param pointingSigmaRadiusM one-sigma pointing-jitter displacement at range
     * @param oneSigmaTrackRadiusM one-sigma target-position uncertainty radius
     * @param effectiveSpotRadiusM root-sum-square beam/pointing/track radius used for exposure envelope
     * @param dwellSeconds requested dwell duration
     * @param deliveredBeamEnergyJ beam energy emitted during dwell
     * @param meanIrradianceWPerM2 mean power divided by effective circular spot area
     * @param electricalEnergyDemandJ electrical energy required during dwell
     * @param wasteHeatJ local waste heat generated during dwell
     */
    public record BeamSolution(
            boolean allowed,
            Failure failure,
            long targetId,
            double rangeM,
            double diffractionRadiusM,
            double pointingSigmaRadiusM,
            double oneSigmaTrackRadiusM,
            double effectiveSpotRadiusM,
            double dwellSeconds,
            double deliveredBeamEnergyJ,
            double meanIrradianceWPerM2,
            double electricalEnergyDemandJ,
            double wasteHeatJ) {
        /**
         * Validates one immutable beam result.
         *
         * @param allowed whether the requested dwell can be executed
         * @param failure stable rejection reason or NONE
         * @param targetId target track identity
         * @param rangeM current geometric range in meters
         * @param diffractionRadiusM diffraction-limited spot-radius contribution
         * @param pointingSigmaRadiusM one-sigma pointing-jitter displacement
         * @param oneSigmaTrackRadiusM one-sigma track-position radius
         * @param effectiveSpotRadiusM combined exposure radius
         * @param dwellSeconds requested dwell duration
         * @param deliveredBeamEnergyJ delivered beam energy
         * @param meanIrradianceWPerM2 mean irradiance
         * @param electricalEnergyDemandJ electrical energy demand
         * @param wasteHeatJ local waste heat generated
         */
        public BeamSolution {
            Objects.requireNonNull(failure, "failure");
            if (targetId <= 0L) {
                throw new IllegalArgumentException("targetId must be positive");
            }
            requireNonNegativeFinite(rangeM, "rangeM");
            requireNonNegativeFinite(diffractionRadiusM, "diffractionRadiusM");
            requireNonNegativeFinite(pointingSigmaRadiusM, "pointingSigmaRadiusM");
            requireNonNegativeFinite(oneSigmaTrackRadiusM, "oneSigmaTrackRadiusM");
            requireNonNegativeFinite(effectiveSpotRadiusM, "effectiveSpotRadiusM");
            requireNonNegativeFinite(dwellSeconds, "dwellSeconds");
            requireNonNegativeFinite(deliveredBeamEnergyJ, "deliveredBeamEnergyJ");
            requireNonNegativeFinite(meanIrradianceWPerM2, "meanIrradianceWPerM2");
            requireNonNegativeFinite(electricalEnergyDemandJ, "electricalEnergyDemandJ");
            requireNonNegativeFinite(wasteHeatJ, "wasteHeatJ");
            if (allowed != (failure == Failure.NONE)) {
                throw new IllegalArgumentException("allowed and failure must agree");
            }
        }
    }

    /**
     * Plans one continuous beam dwell against a current Stage-17.5D track.
     *
     * @param weapon physical beam definition
     * @param track target track
     * @param emitterXM emitter x position in meters
     * @param emitterYM emitter y position in meters
     * @param dwellSeconds requested dwell duration
     * @return deterministic beam geometry/energy solution
     */
    public BeamSolution plan(
            BeamWeapon weapon,
            TrackState track,
            double emitterXM,
            double emitterYM,
            double dwellSeconds) {
        BeamWeapon checkedWeapon = Objects.requireNonNull(weapon, "weapon");
        TrackState checkedTrack = Objects.requireNonNull(track, "track");
        requireFinite(emitterXM, "emitterXM");
        requireFinite(emitterYM, "emitterYM");
        requirePositiveFinite(dwellSeconds, "dwellSeconds");
        if (!checkedTrack.positionKnown()) {
            return rejected(checkedTrack.targetId(), Failure.TRACK_POSITION_UNKNOWN);
        }
        if (checkedTrack.informationState() != InformationState.FIRE_CONTROL) {
            return rejected(checkedTrack.targetId(), Failure.FIRE_CONTROL_INSUFFICIENT);
        }
        if (dwellSeconds > checkedWeapon.maxContinuousDwellSeconds()) {
            return rejected(checkedTrack.targetId(), Failure.DWELL_LIMIT_EXCEEDED);
        }
        double dx = checkedTrack.estimatedXM() - emitterXM;
        double dy = checkedTrack.estimatedYM() - emitterYM;
        double range = Math.hypot(dx, dy);
        double diffractionRadius = checkedWeapon.diffractionAngleRad() * range;
        double pointingRadius = checkedWeapon.pointingJitterRad() * range;
        double trackRadius = Math.sqrt(Math.max(0d, checkedTrack.covariance().positionVarianceM2()));
        double effectiveRadius = Math.sqrt(
                diffractionRadius * diffractionRadius
                        + pointingRadius * pointingRadius
                        + trackRadius * trackRadius);
        // Keep the zero-range mathematical limit finite without inventing a gameplay range wall.
        double area = Math.PI * Math.max(1e-12d, effectiveRadius * effectiveRadius);
        double beamEnergy = checkedWeapon.beamPowerW() * dwellSeconds;
        return new BeamSolution(
                true,
                Failure.NONE,
                checkedTrack.targetId(),
                range,
                diffractionRadius,
                pointingRadius,
                trackRadius,
                effectiveRadius,
                dwellSeconds,
                beamEnergy,
                checkedWeapon.beamPowerW() / area,
                checkedWeapon.electricalPowerDemandW() * dwellSeconds,
                checkedWeapon.wasteHeatW() * dwellSeconds);
    }

    private static BeamSolution rejected(long targetId, Failure failure) {
        return new BeamSolution(false, failure, targetId, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d);
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
