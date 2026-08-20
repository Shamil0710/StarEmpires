package com.spacesim.world.generation;

import com.spacesim.ship.ShipEngineeringRuntime.JumpFailure;
import com.spacesim.ship.ShipEngineeringRuntime.JumpPlan;
import com.spacesim.world.Stage20PhysicalFreightRouteEvaluator.FreightFleetProfile;
import com.spacesim.world.calibration.Stage20BootstrapRequirementCalibrationProfile;
import com.spacesim.world.calibration.Stage20FactionStartAcceptanceProfile;
import com.spacesim.world.calibration.Stage20FtlCalibrationProfile;
import com.spacesim.world.calibration.Stage20FtlCalibrationProfile.JumpEdgeCalibrationSample;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalogLoader;
import com.spacesim.world.calibration.Stage20TopologyQualityCalibrationProfile;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.AcceptanceAuthority;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.InitialInfrastructureProfile;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.PhysicalTransportAuthority;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.ProbeInputs;

import java.util.List;
import java.util.Objects;

/**
 * Versioned representative input policy for Stage-20E whole-world production-probe evidence.
 *
 * <p>This profile makes the remaining representative-world assumptions explicit instead of hiding
 * them inside regression fixtures. World size, initial infrastructure mix, representative faction
 * identities and allocated freighter count are provisional generation/evidence policy and require
 * Stage-22 review. Physical jump timing, payload, topology quality and bootstrap demand are derived
 * from their existing Stage-20A/18 authorities and retain those source versions.</p>
 *
 * <p>The profile is selected before any root seed is evaluated. It must never inspect a generated
 * seed, failure reason, resource occurrence or faction-start result in order to change facilities,
 * freighter count, demand, topology quality or any other input.</p>
 */
public final class Stage20RepresentativeGeneratedWorldProbeProfile {
    /** Current representative production-probe profile version. */
    public static final String CURRENT_VERSION = "stage20e.representative-production-probe-profile.v1";
    /** Current explicit infrastructure-policy version. */
    public static final String INFRASTRUCTURE_POLICY_VERSION =
            "stage20e.representative-probe-infrastructure.v1";
    /** Representative Stage-20A freight reference used by the current evidence profile. */
    public static final String FREIGHT_REFERENCE_CLASS = "EARLY_CIVILIAN_FREIGHTER";
    /** Explicit representative fleet allocation; a Stage-22 reviewable evidence-policy choice. */
    public static final int ACTIVE_FREIGHTER_COUNT = 8;
    /** Explicit resource-anchor count generated in every representative system. */
    public static final int RESOURCE_ANCHOR_COUNT_PER_SYSTEM = 4;
    /** Policy authority remains provisional until Stage 22 content/balance review. */
    public static final boolean STAGE_22_REVIEW_REQUIRED = true;

    private static final String MAJOR_HUB_ARCHETYPE = "station.infrastructure.trade_logistics_hub";
    private static final List<String> INDUSTRIAL_ARCHETYPES = List.of(
            "station.infrastructure.frontier_multipurpose",
            "station.infrastructure.high_tech_hub",
            "station.infrastructure.industrial_station",
            "station.infrastructure.refinery_complex");
    private static final List<String> REPRESENTATIVE_FACTIONS = List.of(
            "faction.alpha",
            "faction.beta");

    private Stage20RepresentativeGeneratedWorldProbeProfile() {
        throw new AssertionError("No instances");
    }

    /**
     * Fully derived representative probe profile with exact upstream provenance.
     *
     * @param version stable representative-profile version
     * @param stage22ReviewRequired whether policy choices require Stage-22 review
     * @param inputs complete production-probe inputs
     * @param bootstrapRequirementVersion exact Stage-20E bootstrap-requirement authority
     * @param factionStartProfileVersion exact Stage-20E faction-start quality profile
     * @param topologyQualityVersion exact Stage-20A topology-quality profile
     * @param ftlCalibrationVersion exact Stage-20A fitted-jump calibration version
     * @param propulsionReferenceVersion exact representative-propulsion catalog version
     * @param freightReferenceClass representative freight class used for physical transport
     * @param activeFreighterCount explicitly allocated representative freighter count
     * @param policyEvidenceIds deterministic evidence/policy identifiers
     */
    public record DerivedProfile(
            String version,
            boolean stage22ReviewRequired,
            ProbeInputs inputs,
            String bootstrapRequirementVersion,
            String factionStartProfileVersion,
            String topologyQualityVersion,
            String ftlCalibrationVersion,
            String propulsionReferenceVersion,
            String freightReferenceClass,
            int activeFreighterCount,
            List<String> policyEvidenceIds) {
        /**
         * Validates one immutable representative evidence profile.
         *
         * @param version stable profile version
         * @param stage22ReviewRequired Stage-22 review boundary
         * @param inputs complete production-probe inputs
         * @param bootstrapRequirementVersion bootstrap demand authority version
         * @param factionStartProfileVersion faction-start policy version
         * @param topologyQualityVersion topology-quality authority version
         * @param ftlCalibrationVersion FTL calibration version
         * @param propulsionReferenceVersion propulsion-reference version
         * @param freightReferenceClass representative freight class
         * @param activeFreighterCount explicit representative fleet allocation
         * @param policyEvidenceIds policy/evidence identifiers
         */
        public DerivedProfile {
            version = requireText(version, "version");
            Objects.requireNonNull(inputs, "inputs");
            bootstrapRequirementVersion = requireText(bootstrapRequirementVersion, "bootstrapRequirementVersion");
            factionStartProfileVersion = requireText(factionStartProfileVersion, "factionStartProfileVersion");
            topologyQualityVersion = requireText(topologyQualityVersion, "topologyQualityVersion");
            ftlCalibrationVersion = requireText(ftlCalibrationVersion, "ftlCalibrationVersion");
            propulsionReferenceVersion = requireText(propulsionReferenceVersion, "propulsionReferenceVersion");
            freightReferenceClass = requireText(freightReferenceClass, "freightReferenceClass");
            if (activeFreighterCount <= 0) {
                throw new IllegalArgumentException("activeFreighterCount must be positive");
            }
            Objects.requireNonNull(policyEvidenceIds, "policyEvidenceIds");
            if (policyEvidenceIds.isEmpty() || policyEvidenceIds.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("policyEvidenceIds must be non-empty and contain no blanks");
            }
            policyEvidenceIds = policyEvidenceIds.stream().sorted().toList();
            if (!stage22ReviewRequired) {
                throw new IllegalArgumentException("representative v1 policy must retain Stage-22 review boundary");
            }
        }
    }

    /**
     * Derives the current representative production-probe profile from existing authorities.
     *
     * @return deterministic current representative profile
     */
    public static DerivedProfile deriveCurrent() {
        var bootstrap = Stage20BootstrapRequirementCalibrationProfile.deriveCurrent();
        Stage20FactionStartAcceptanceProfile startProfile = Stage20FactionStartAcceptanceProfile.current();
        Stage20TopologyQualityCalibrationProfile topology = Stage20TopologyQualityCalibrationProfile.deriveCurrent();
        Stage20FtlCalibrationProfile ftl = Stage20FtlCalibrationProfile.deriveCurrent();
        Stage20RepresentativePropulsionCatalog propulsion = Stage20RepresentativePropulsionCatalogLoader.loadDefault();
        JumpEdgeCalibrationSample freightSample = compatibleSample(ftl, FREIGHT_REFERENCE_CLASS);

        double requiredEnergyJ = freightSample.requiredTranslationEnergyJ().orElseThrow();
        double spoolSeconds = freightSample.spoolTimeS().orElseThrow();
        JumpPlan jumpPlan = new JumpPlan(
                true,
                JumpFailure.NONE,
                "ftl.calibration." + FREIGHT_REFERENCE_CLASS,
                freightSample.translatedMassKg(),
                requiredEnergyJ,
                requiredEnergyJ,
                0d,
                requiredEnergyJ / spoolSeconds,
                spoolSeconds,
                freightSample.referenceEdgeTransitTimeS(),
                freightSample.cooldownS(),
                0d);
        FreightFleetProfile fleet = FreightFleetProfile.fromMissionCargoStoresReference(
                propulsion,
                FREIGHT_REFERENCE_CLASS,
                ACTIVE_FREIGHTER_COUNT);

        InitialInfrastructureProfile infrastructure = new InitialInfrastructureProfile(
                INFRASTRUCTURE_POLICY_VERSION,
                MAJOR_HUB_ARCHETYPE,
                INDUSTRIAL_ARCHETYPES,
                RESOURCE_ANCHOR_COUNT_PER_SYSTEM);
        AcceptanceAuthority acceptance = new AcceptanceAuthority(
                bootstrap.bootstrapRequirements(),
                bootstrap.dependencyRequirements(),
                startProfile,
                REPRESENTATIVE_FACTIONS);
        ProbeInputs inputs = new ProbeInputs(
                Stage20MacroGalaxyGeometryGenerator.GenerationRequest.representative(),
                topology,
                infrastructure,
                acceptance,
                new PhysicalTransportAuthority(jumpPlan, jumpPlan, fleet));

        return new DerivedProfile(
                CURRENT_VERSION,
                STAGE_22_REVIEW_REQUIRED,
                inputs,
                bootstrap.version(),
                startProfile.version(),
                topology.version(),
                ftl.version(),
                propulsion.version(),
                FREIGHT_REFERENCE_CLASS,
                ACTIVE_FREIGHTER_COUNT,
                List.of(
                        "policy:four-regions-eight-to-ten-systems",
                        "policy:four-resource-anchors-per-system",
                        "policy:representative-factions-alpha-beta",
                        "policy:eight-early-civilian-freighters",
                        "policy:initial-industrial-archetype-set-v1",
                        freightSample.shipProvenanceId(),
                        freightSample.ftlProvenanceId(),
                        fleet.sourceEvidenceId()));
    }

    private static JumpEdgeCalibrationSample compatibleSample(
            Stage20FtlCalibrationProfile profile,
            String representativeId) {
        return profile.samples().stream()
                .filter(value -> value.representativeId().equals(representativeId))
                .filter(value -> value.requiredTranslationEnergyJ().isPresent())
                .filter(value -> value.spoolTimeS().isPresent())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Missing compatible accepted Stage-20A FTL calibration for " + representativeId));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }
}
