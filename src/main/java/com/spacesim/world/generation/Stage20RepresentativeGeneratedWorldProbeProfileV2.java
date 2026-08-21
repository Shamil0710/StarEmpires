package com.spacesim.world.generation;

import com.spacesim.world.calibration.Stage20BootstrapRequirementCalibrationProfileV2;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.AcceptanceAuthority;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.ProbeInputs;

import java.util.List;
import java.util.Objects;

/**
 * Measured candidate representative profile for corrected Stage-20E bootstrap service cadence.
 *
 * <p>The existing representative v1 profile remains untouched so the frozen 0-of-16 baseline stays
 * reproducible. This candidate derives the exact same macro geometry, topology quality,
 * infrastructure, faction identities, FTL plan and eight-freighter transport authority from v1 and
 * replaces only the bootstrap/dependency route-time authority with
 * {@link Stage20BootstrapRequirementCalibrationProfileV2}. This makes before/after corpus evidence
 * causally attributable to the corrected supplier-time semantics.</p>
 */
public final class Stage20RepresentativeGeneratedWorldProbeProfileV2 {
    /** Candidate representative profile version. */
    public static final String CURRENT_VERSION = "stage20e.representative-production-probe-profile.v2-candidate";

    private Stage20RepresentativeGeneratedWorldProbeProfileV2() {
        throw new AssertionError("No instances");
    }

    /**
     * Complete candidate profile and exact v1 provenance boundary.
     *
     * @param version candidate representative profile version
     * @param sourceRepresentativeProfileVersion exact unchanged v1 representative profile version
     * @param bootstrapRequirementVersion corrected bootstrap authority version
     * @param serviceCadenceVersion corrected supplier-service cadence authority version
     * @param inputs complete production-probe inputs
     * @param policyEvidenceIds deterministic provenance statements
     * @param stage22ReviewRequired whether candidate policy/calibration remains provisional
     */
    public record DerivedProfile(
            String version,
            String sourceRepresentativeProfileVersion,
            String bootstrapRequirementVersion,
            String serviceCadenceVersion,
            ProbeInputs inputs,
            List<String> policyEvidenceIds,
            boolean stage22ReviewRequired) {
        /**
         * Validates one immutable candidate representative profile.
         *
         * @param version candidate profile version
         * @param sourceRepresentativeProfileVersion source v1 representative profile version
         * @param bootstrapRequirementVersion corrected bootstrap authority version
         * @param serviceCadenceVersion corrected service-cadence authority version
         * @param inputs complete production-probe inputs
         * @param policyEvidenceIds deterministic provenance statements
         * @param stage22ReviewRequired Stage-22 review boundary
         */
        public DerivedProfile {
            version = requireText(version, "version");
            sourceRepresentativeProfileVersion = requireText(
                    sourceRepresentativeProfileVersion, "sourceRepresentativeProfileVersion");
            bootstrapRequirementVersion = requireText(bootstrapRequirementVersion, "bootstrapRequirementVersion");
            serviceCadenceVersion = requireText(serviceCadenceVersion, "serviceCadenceVersion");
            Objects.requireNonNull(inputs, "inputs");
            Objects.requireNonNull(policyEvidenceIds, "policyEvidenceIds");
            if (policyEvidenceIds.isEmpty() || policyEvidenceIds.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("policyEvidenceIds must be non-empty and contain no blanks");
            }
            policyEvidenceIds = policyEvidenceIds.stream().sorted().toList();
            if (!stage22ReviewRequired) {
                throw new IllegalArgumentException("candidate profile must retain Stage-22 review boundary");
            }
        }
    }

    /**
     * Derives the candidate by replacing only v1 bootstrap/dependency time authority.
     *
     * @return deterministic candidate representative profile
     */
    public static DerivedProfile deriveCurrent() {
        Stage20RepresentativeGeneratedWorldProbeProfile.DerivedProfile v1 =
                Stage20RepresentativeGeneratedWorldProbeProfile.deriveCurrent();
        Stage20BootstrapRequirementCalibrationProfileV2.DerivedProfile bootstrap =
                Stage20BootstrapRequirementCalibrationProfileV2.deriveCurrent();

        AcceptanceAuthority v1Acceptance = v1.inputs().acceptance();
        AcceptanceAuthority correctedAcceptance = new AcceptanceAuthority(
                bootstrap.bootstrapRequirements(),
                bootstrap.dependencyRequirements(),
                v1Acceptance.factionStartProfile(),
                v1Acceptance.stableFactionIds());
        ProbeInputs inputs = new ProbeInputs(
                v1.inputs().macroRequest(),
                v1.inputs().topologyQuality(),
                v1.inputs().infrastructure(),
                correctedAcceptance,
                v1.inputs().transport());

        return new DerivedProfile(
                CURRENT_VERSION,
                v1.version(),
                bootstrap.version(),
                bootstrap.serviceCadence().version(),
                inputs,
                List.of(
                        "contract:docs/stage20_physical_world_generation_plan.md:haul-time-before-buffer-need",
                        "contract:docs/stage20_physical_world_generation_plan.md:Stage20J-round-trip-vs-buffer-depletion",
                        "contract:docs/physical_trade_route_scoring_contract.md:route-time-to-throughput-to-buffer",
                        "preserve:v1-essential-process-rates",
                        "preserve:v1-macro-topology-infrastructure-factions-transport",
                        "supplier-envelope:accepted-stage20a-regional-five-hop-plus-local-handling"),
                true);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }
}
