package com.spacesim.world.calibration;

import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
import com.spacesim.world.calibration.Stage20IntersystemCadenceCalibrationProfile.HopCadenceSample;

import java.util.List;
import java.util.Objects;

/**
 * Stage-20E minimum ordinary-start freight-service capacity derived from existing physical and
 * economic authorities rather than from representative seed outcomes.
 *
 * <p>The profile answers one narrow question: how many identical representative early civilian
 * freighters are required for one ordinary faction start to sustain the complete essential
 * bootstrap demand rate across the accepted regional five-hop reference envelope when the ships are
 * a finite shared pool. The derivation intentionally does not inspect generated worlds, candidate
 * placement results or corpus pass rates.</p>
 *
 * <p>The reference repeated cycle mirrors {@code Stage20PhysicalFreightRouteEvaluator}: load,
 * outbound local access, forward FTL ready-again time, unload, return local access and reverse FTL
 * ready-again time. The current calibration is symmetric because the representative production
 * probe uses the same fitted early-civilian-freighter jump plan in both directions. Final cooldown
 * is therefore paid on both legs, so the result is sustainable service capacity rather than a
 * one-shot delivery estimate.</p>
 *
 * <p>This profile is a generation-quality requirement, not a ship grant or ownership claim. It does
 * not materialize freighters, assign them to factions, transfer title to supplier facilities or
 * fabricate inventory. Those authorities remain separate Stage-20E integration concerns.</p>
 *
 * @param version stable freight-capacity requirement version
 * @param bootstrapRequirementVersion exact essential-demand authority version
 * @param serviceCadenceVersion exact supplier-service cadence authority version
 * @param intersystemCadenceVersion exact inter-system cadence authority version
 * @param freightReferenceClass representative physical freight role
 * @param regionalHopCount accepted ordinary regional hop envelope
 * @param totalEssentialDemandKgPerSecond sum of all essential bootstrap service rates for one start
 * @param payloadMassKg representative delivered payload per freighter trip
 * @param oneEndpointHandlingSeconds physical load or unload duration for one payload
 * @param maximumSourceLocalAccessSeconds worst accepted civilian routine non-jump local access
 * @param maximumJumpAccessSeconds worst accepted civilian routine hub-to-jump access per endpoint
 * @param regionalFtlReadyAgainSeconds five-hop FTL arrival plus final cooldown
 * @param referenceRoundTripCycleSeconds complete repeatable symmetric regional freight cycle
 * @param hubTransferMassRateKgPerSecond physical representative hub transfer-rate ceiling
 * @param oneFreighterSustainableThroughputKgPerSecond sustainable reference throughput of one ship
 * @param requiredFreighterCountPerFactionStart minimum integer ship count for one ordinary start
 * @param evidenceIds deterministic upstream provenance identifiers
 * @param stage22ReviewRequired whether this provisional sizing authority requires Stage-22 review
 */
public record Stage20BootstrapFreightCapacityRequirementProfile(
        String version,
        String bootstrapRequirementVersion,
        String serviceCadenceVersion,
        String intersystemCadenceVersion,
        String freightReferenceClass,
        int regionalHopCount,
        double totalEssentialDemandKgPerSecond,
        double payloadMassKg,
        double oneEndpointHandlingSeconds,
        double maximumSourceLocalAccessSeconds,
        double maximumJumpAccessSeconds,
        double regionalFtlReadyAgainSeconds,
        double referenceRoundTripCycleSeconds,
        double hubTransferMassRateKgPerSecond,
        double oneFreighterSustainableThroughputKgPerSecond,
        int requiredFreighterCountPerFactionStart,
        List<String> evidenceIds,
        boolean stage22ReviewRequired) {

    /** Current deterministic Stage-20E freight-capacity requirement authority. */
    public static final String CURRENT_VERSION = "stage20e.bootstrap-freight-capacity-requirement.v1";

    /**
     * Validates one immutable freight-capacity requirement profile.
     *
     * @param version stable requirement version
     * @param bootstrapRequirementVersion essential-demand authority version
     * @param serviceCadenceVersion supplier-service cadence authority version
     * @param intersystemCadenceVersion inter-system cadence authority version
     * @param freightReferenceClass representative freight role
     * @param regionalHopCount regional hop envelope
     * @param totalEssentialDemandKgPerSecond aggregate essential service rate
     * @param payloadMassKg representative payload
     * @param oneEndpointHandlingSeconds one load/unload duration
     * @param maximumSourceLocalAccessSeconds non-jump local access duration
     * @param maximumJumpAccessSeconds jump access duration per endpoint
     * @param regionalFtlReadyAgainSeconds regional FTL ready-again duration
     * @param referenceRoundTripCycleSeconds repeatable symmetric cycle duration
     * @param hubTransferMassRateKgPerSecond representative hub transfer-rate ceiling
     * @param oneFreighterSustainableThroughputKgPerSecond sustainable one-ship throughput
     * @param requiredFreighterCountPerFactionStart minimum integer ship count per ordinary start
     * @param evidenceIds upstream provenance identifiers
     * @param stage22ReviewRequired Stage-22 review boundary
     */
    public Stage20BootstrapFreightCapacityRequirementProfile {
        version = requireText(version, "version");
        bootstrapRequirementVersion = requireText(bootstrapRequirementVersion, "bootstrapRequirementVersion");
        serviceCadenceVersion = requireText(serviceCadenceVersion, "serviceCadenceVersion");
        intersystemCadenceVersion = requireText(intersystemCadenceVersion, "intersystemCadenceVersion");
        freightReferenceClass = requireText(freightReferenceClass, "freightReferenceClass");
        if (regionalHopCount <= 0) {
            throw new IllegalArgumentException("regionalHopCount must be positive");
        }
        requirePositive(totalEssentialDemandKgPerSecond, "totalEssentialDemandKgPerSecond");
        requirePositive(payloadMassKg, "payloadMassKg");
        requirePositive(oneEndpointHandlingSeconds, "oneEndpointHandlingSeconds");
        requirePositive(maximumSourceLocalAccessSeconds, "maximumSourceLocalAccessSeconds");
        requirePositive(maximumJumpAccessSeconds, "maximumJumpAccessSeconds");
        requirePositive(regionalFtlReadyAgainSeconds, "regionalFtlReadyAgainSeconds");
        requirePositive(referenceRoundTripCycleSeconds, "referenceRoundTripCycleSeconds");
        requirePositive(hubTransferMassRateKgPerSecond, "hubTransferMassRateKgPerSecond");
        requirePositive(oneFreighterSustainableThroughputKgPerSecond,
                "oneFreighterSustainableThroughputKgPerSecond");
        if (requiredFreighterCountPerFactionStart <= 0) {
            throw new IllegalArgumentException("requiredFreighterCountPerFactionStart must be positive");
        }
        Objects.requireNonNull(evidenceIds, "evidenceIds");
        if (evidenceIds.isEmpty() || evidenceIds.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("evidenceIds must be non-empty and contain no blanks");
        }
        evidenceIds = evidenceIds.stream().sorted().toList();

        double composedRoundTrip = 2d * oneEndpointHandlingSeconds
                + 2d * maximumSourceLocalAccessSeconds
                + 4d * maximumJumpAccessSeconds
                + 2d * regionalFtlReadyAgainSeconds;
        requireNearlyEqual(composedRoundTrip, referenceRoundTripCycleSeconds,
                "round-trip cycle must equal its physical components");
        double expectedOneShip = Math.min(
                payloadMassKg / referenceRoundTripCycleSeconds,
                hubTransferMassRateKgPerSecond);
        requireNearlyEqual(expectedOneShip, oneFreighterSustainableThroughputKgPerSecond,
                "one-freighter throughput must follow the physical cycle/handling ceiling");
        int expectedCount = checkedCeil(totalEssentialDemandKgPerSecond / oneFreighterSustainableThroughputKgPerSecond);
        if (requiredFreighterCountPerFactionStart != expectedCount) {
            throw new IllegalArgumentException("required freighter count must be the ceiling of demand / one-ship throughput");
        }
        if (!stage22ReviewRequired) {
            throw new IllegalArgumentException("freight-capacity sizing must retain the Stage-22 review boundary");
        }
    }

    /**
     * Derives the current minimum ordinary-start freight-service capacity from accepted authorities.
     *
     * @return deterministic current freight-capacity requirement
     */
    public static Stage20BootstrapFreightCapacityRequirementProfile deriveCurrent() {
        Stage20BootstrapRequirementCalibrationProfileV2.DerivedProfile bootstrap =
                Stage20BootstrapRequirementCalibrationProfileV2.deriveCurrent();
        Stage20BootstrapServiceCadenceCalibrationProfile service = bootstrap.serviceCadence();
        Stage20IntersystemCadenceCalibrationProfile intersystem =
                Stage20IntersystemCadenceCalibrationProfile.deriveCurrent();
        HopCadenceSample regional = intersystem.samples().stream()
                .filter(value -> value.representativeId().equals(service.freightReferenceClass()))
                .filter(value -> value.hopCount() == service.regionalHopCount())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "missing accepted regional ready-again cadence for " + service.freightReferenceClass()));

        double totalDemand = bootstrap.bootstrapRequirements().essentialCommodities().stream()
                .mapToDouble(CommodityRequirement::minSupplierThroughputKgPerSecond)
                .sum();
        requirePositive(totalDemand, "totalEssentialDemandKgPerSecond");
        double roundTrip = 2d * service.oneEndpointHandlingSeconds()
                + 2d * service.maximumSourceLocalAccessSeconds()
                + 4d * service.maximumJumpAccessSeconds()
                + 2d * regional.readyAgainTimeS();
        requirePositive(roundTrip, "referenceRoundTripCycleSeconds");
        double oneFreighterThroughput = Math.min(
                service.payloadMassKg() / roundTrip,
                service.hubTransferMassRateKgPerSecond());
        requirePositive(oneFreighterThroughput, "oneFreighterSustainableThroughputKgPerSecond");
        int requiredFreighters = checkedCeil(totalDemand / oneFreighterThroughput);

        return new Stage20BootstrapFreightCapacityRequirementProfile(
                CURRENT_VERSION,
                bootstrap.version(),
                service.version(),
                intersystem.version(),
                service.freightReferenceClass(),
                service.regionalHopCount(),
                totalDemand,
                service.payloadMassKg(),
                service.oneEndpointHandlingSeconds(),
                service.maximumSourceLocalAccessSeconds(),
                service.maximumJumpAccessSeconds(),
                regional.readyAgainTimeS(),
                roundTrip,
                service.hubTransferMassRateKgPerSecond(),
                oneFreighterThroughput,
                requiredFreighters,
                List.of(
                        "bootstrap-demand:" + bootstrap.version(),
                        "service-cadence:" + service.version(),
                        "intersystem-cadence:" + intersystem.version(),
                        "ship:" + regional.shipProvenanceId(),
                        "ftl:" + regional.ftlProvenanceId(),
                        "station-infrastructure:" + service.stationInfrastructureFingerprint()),
                true);
    }

    private static int checkedCeil(double value) {
        requirePositive(value, "freighter count quotient");
        double ceiling = Math.ceil(value);
        if (ceiling > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("required freighter count exceeds integer range");
        }
        return (int) ceiling;
    }

    private static void requireNearlyEqual(double expected, double actual, String message) {
        double tolerance = 1.0e-9d * Math.max(1d, Math.max(Math.abs(expected), Math.abs(actual)));
        if (Math.abs(expected - actual) > tolerance) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }

    private static void requirePositive(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }
}
