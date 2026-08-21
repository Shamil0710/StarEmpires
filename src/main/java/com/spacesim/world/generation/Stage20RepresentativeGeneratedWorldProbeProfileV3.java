package com.spacesim.world.generation;

import com.spacesim.world.calibration.Stage20CoordinatedFreightAcceptanceProfile;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.ProbeInputs;

import java.util.List;
import java.util.Objects;

/**
 * Representative Stage-20E production profile that preserves the measured v2 generated world and
 * explicitly adds the coordinated finite-freight acceptance policy.
 *
 * <p>V1/v2 remain reproducible historical/candidate profiles. V3 changes no macro geometry, topology,
 * infrastructure, bootstrap demand, faction placement policy, fitted transport physics or payload.
 * It only supplies the already verified coordinated-freight production policy for the resolved
 * whole-seed acceptance path.</p>
 */
public final class Stage20RepresentativeGeneratedWorldProbeProfileV3 {
    /** Representative coordinated-freight production profile version. */
    public static final String CURRENT_VERSION = "stage20e.representative-production-probe-profile.v3";

    private Stage20RepresentativeGeneratedWorldProbeProfileV3() {
        throw new AssertionError("No instances");
    }

    /**
     * Complete v3 production profile.
     *
     * @param version v3 profile version
     * @param sourceRepresentativeProfileVersion exact preserved v2 profile version
     * @param inputs unchanged generated-world production inputs
     * @param coordinatedFreightAcceptance explicit finite-freight production acceptance policy
     * @param policyEvidenceIds deterministic provenance statements
     * @param stage22ReviewRequired inherited provisional review boundary
     */
    public record DerivedProfile(
            String version,
            String sourceRepresentativeProfileVersion,
            ProbeInputs inputs,
            Stage20CoordinatedFreightAcceptanceProfile coordinatedFreightAcceptance,
            List<String> policyEvidenceIds,
            boolean stage22ReviewRequired) {
        /** Validates and freezes one v3 profile. */
        public DerivedProfile {
            version = requireText(version, "version");
            sourceRepresentativeProfileVersion = requireText(
                    sourceRepresentativeProfileVersion, "sourceRepresentativeProfileVersion");
            Objects.requireNonNull(inputs, "inputs");
            Objects.requireNonNull(coordinatedFreightAcceptance, "coordinatedFreightAcceptance");
            policyEvidenceIds = List.copyOf(Objects.requireNonNull(policyEvidenceIds, "policyEvidenceIds"));
            if (policyEvidenceIds.isEmpty()
                    || policyEvidenceIds.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("policyEvidenceIds must be non-empty and contain no blanks");
            }
            policyEvidenceIds = policyEvidenceIds.stream().map(String::strip).sorted().toList();
            if (!inputs.acceptance().bootstrapRequirements().version().equals(
                    coordinatedFreightAcceptance.freightCapacityRequirement().bootstrapRequirementVersion())) {
                throw new IllegalArgumentException(
                        "coordinated freight capacity must derive from the same bootstrap requirement authority");
            }
            if (Double.compare(
                    inputs.transport().fleetProfile().payloadMassKgPerFreighter(),
                    coordinatedFreightAcceptance.freightCapacityRequirement().payloadMassKg()) != 0) {
                throw new IllegalArgumentException(
                        "coordinated freight capacity payload must match the preserved representative transport payload");
            }
            if (stage22ReviewRequired != coordinatedFreightAcceptance.stage22ReviewRequired()) {
                throw new IllegalArgumentException("v3 must preserve coordinated freight Stage-22 review boundary");
            }
        }
    }

    /**
     * Derives v3 by adding only explicit coordinated-freight acceptance to the unchanged v2 world.
     *
     * @return deterministic current v3 production profile
     */
    public static DerivedProfile deriveCurrent() {
        Stage20RepresentativeGeneratedWorldProbeProfileV2.DerivedProfile v2 =
                Stage20RepresentativeGeneratedWorldProbeProfileV2.deriveCurrent();
        Stage20CoordinatedFreightAcceptanceProfile freight =
                Stage20CoordinatedFreightAcceptanceProfile.deriveCurrent();
        return new DerivedProfile(
                CURRENT_VERSION,
                v2.version(),
                v2.inputs(),
                freight,
                List.of(
                        "preserve:" + v2.version() + ":macro-topology-infrastructure-bootstrap-factions-transport",
                        "freight-policy:" + freight.version(),
                        "whole-seed:stage20e.generated-world-seed-acceptance.v2"),
                freight.stage22ReviewRequired());
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.strip();
    }
}
