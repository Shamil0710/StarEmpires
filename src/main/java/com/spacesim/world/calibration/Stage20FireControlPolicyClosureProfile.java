package com.spacesim.world.calibration;

import com.spacesim.ship.TrackState.InformationState;

import java.util.List;
import java.util.Objects;

/**
 * Accepted Stage-20 closure of the provisional A.4 fused-track/fire-control policy question.
 *
 * <p>The closure deliberately rejects one universal sensor-side FIRE_CONTROL sigma/age wall as the
 * admission rule for every weapon. Production kinetic and guided planning already consume TRACKED
 * Cartesian state and propagate physical uncertainty/motion. Beam planning now follows the same
 * contract: TRACKED is the shared solved-Cartesian floor and covariance continuously expands the
 * exposure spot. The sensor-side FIRE_CONTROL label remains a high-quality evidence state, but it is
 * not a universal weapon-permission bit.</p>
 *
 * @param version stable closure-profile version
 * @param minimumSharedWeaponTrackState minimum shared Cartesian information state
 * @param universalSensorFireControlThresholdRequired whether one global sensor sigma/age threshold gates all weapons
 * @param kineticConsumesContinuousTrackUncertainty whether kinetic planning propagates covariance/motion
 * @param beamConsumesContinuousTrackUncertainty whether beam planning propagates covariance into spot/irradiance
 * @param guidedConsumesContinuousTrackState whether guided planning accepts tracked kinematics and physical guidance state
 * @param sources exact production runtime seams establishing the closure
 */
public record Stage20FireControlPolicyClosureProfile(
        String version,
        InformationState minimumSharedWeaponTrackState,
        boolean universalSensorFireControlThresholdRequired,
        boolean kineticConsumesContinuousTrackUncertainty,
        boolean beamConsumesContinuousTrackUncertainty,
        boolean guidedConsumesContinuousTrackState,
        List<String> sources) {
    /** Current Stage-20 fire-control policy closure version. */
    public static final String CURRENT_VERSION = "stage20a.fire-control-policy-closure.v1";

    /**
     * Validates one immutable closure profile.
     *
     * @param version stable closure-profile version
     * @param minimumSharedWeaponTrackState shared solved-Cartesian admission floor
     * @param universalSensorFireControlThresholdRequired whether one global threshold gates every weapon
     * @param kineticConsumesContinuousTrackUncertainty whether kinetic planning uses physical uncertainty
     * @param beamConsumesContinuousTrackUncertainty whether beam planning uses physical uncertainty
     * @param guidedConsumesContinuousTrackState whether guided planning consumes tracked state
     * @param sources production runtime provenance
     */
    public Stage20FireControlPolicyClosureProfile {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        Objects.requireNonNull(minimumSharedWeaponTrackState, "minimumSharedWeaponTrackState");
        Objects.requireNonNull(sources, "sources");
        if (sources.isEmpty() || sources.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("sources must be non-empty and contain no blank values");
        }
        sources = sources.stream().sorted().toList();
    }

    /**
     * Returns the accepted current Stage-20 closure derived from production runtime semantics.
     *
     * @return deterministic weapon-dependent fire-control policy profile
     */
    public static Stage20FireControlPolicyClosureProfile deriveCurrent() {
        return new Stage20FireControlPolicyClosureProfile(
                CURRENT_VERSION,
                InformationState.TRACKED,
                false,
                true,
                true,
                true,
                List.of(
                        "BeamWeaponRuntime.plan:TRACKED+covariance_spot",
                        "GuidanceRuntime.planLeadPursuit:TRACKED",
                        "WeaponFireControl.planKinetic:TRACKED+covariance_motion"));
    }

    /**
     * Reports whether the provisional A.4 global policy question is physically closed for Stage 20.
     *
     * @return true only when TRACKED is the shared floor and all current weapon families consume physical track state
     */
    public boolean closesStage20FireControlPolicy() {
        return minimumSharedWeaponTrackState == InformationState.TRACKED
                && !universalSensorFireControlThresholdRequired
                && kineticConsumesContinuousTrackUncertainty
                && beamConsumesContinuousTrackUncertainty
                && guidedConsumesContinuousTrackState;
    }
}
