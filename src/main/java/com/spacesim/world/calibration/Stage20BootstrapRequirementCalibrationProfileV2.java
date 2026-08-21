package com.spacesim.world.calibration;

import com.spacesim.content.Stage18ResourceOntologyCatalog.CommodityDefinition;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage18StationInfrastructureCatalog.StationArchetypeDefinition;
import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.world.Stage20EconomicBootstrapValidator.BootstrapRequirementProfile;
import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
import com.spacesim.world.Stage20FactionStartDependencyDiagnostics.Requirement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Candidate Stage-20E bootstrap requirement authority that preserves v1 physical demand but corrects
 * the supplier-time semantic boundary.
 *
 * <p>V1 correctly derives the essential 50 kg/s water-ice and 25 kg/s metallic-ore rates from the
 * selected Stage-18 process cell, but also uses {@code storage capacity / demand rate} as a hard
 * supplier route-time cutoff. Canonical Stage-20 contracts instead define the causal order as route
 * time → ship throughput → inventory buffer need, and Stage-20J lists round-trip travel time and
 * buffer depletion time as separate measurements. V2 therefore retains the exact v1 demand/process
 * provenance while moving storage coverage to explicit resilience evidence and taking route-time
 * acceptance from {@link Stage20BootstrapServiceCadenceCalibrationProfile}.</p>
 *
 * <p>This class is introduced as a measured candidate authority first. It does not mutate or replace
 * the frozen v1 rejection baseline.</p>
 */
public final class Stage20BootstrapRequirementCalibrationProfileV2 {
    /** Candidate corrected bootstrap requirement authority version. */
    public static final String CURRENT_VERSION = "stage20e.bootstrap-requirements.v2";

    private Stage20BootstrapRequirementCalibrationProfileV2() {
        throw new AssertionError("No instances");
    }

    /**
     * Complete corrected candidate authority and provenance.
     *
     * @param version corrected bootstrap authority version
     * @param demandAuthorityVersion exact v1 process/rate authority reused without modification
     * @param serviceCadence corrected physical supplier-service cadence authority
     * @param bootstrapRequirements corrected economic/bootstrap requirements
     * @param dependencyRequirements exact matching faction-start dependency projection
     * @param referenceBufferCoverageSecondsByCommodity reference station storage coverage evidence
     * @param resourceOntologyFingerprint Stage-18 resource ontology fingerprint
     * @param stationInfrastructureFingerprint Stage-18 station-infrastructure fingerprint
     * @param stage22ReviewRequired whether policy/calibration remains provisional for Stage 22
     */
    public record DerivedProfile(
            String version,
            String demandAuthorityVersion,
            Stage20BootstrapServiceCadenceCalibrationProfile serviceCadence,
            BootstrapRequirementProfile bootstrapRequirements,
            List<Requirement> dependencyRequirements,
            Map<String, Double> referenceBufferCoverageSecondsByCommodity,
            String resourceOntologyFingerprint,
            String stationInfrastructureFingerprint,
            boolean stage22ReviewRequired) {
        /**
         * Validates and freezes one corrected candidate profile.
         *
         * @param version corrected bootstrap authority version
         * @param demandAuthorityVersion reused process/rate authority version
         * @param serviceCadence supplier service cadence authority
         * @param bootstrapRequirements corrected bootstrap requirements
         * @param dependencyRequirements matching dependency projection
         * @param referenceBufferCoverageSecondsByCommodity reference buffer coverage evidence
         * @param resourceOntologyFingerprint resource ontology fingerprint
         * @param stationInfrastructureFingerprint station-infrastructure fingerprint
         * @param stage22ReviewRequired Stage-22 review boundary
         */
        public DerivedProfile {
            version = requireText(version, "version");
            demandAuthorityVersion = requireText(demandAuthorityVersion, "demandAuthorityVersion");
            Objects.requireNonNull(serviceCadence, "serviceCadence");
            Objects.requireNonNull(bootstrapRequirements, "bootstrapRequirements");
            dependencyRequirements = List.copyOf(Objects.requireNonNull(
                    dependencyRequirements, "dependencyRequirements"));
            Objects.requireNonNull(referenceBufferCoverageSecondsByCommodity,
                    "referenceBufferCoverageSecondsByCommodity");
            TreeMap<String, Double> bufferCopy = new TreeMap<>();
            for (Map.Entry<String, Double> entry : referenceBufferCoverageSecondsByCommodity.entrySet()) {
                String commodityId = requireText(entry.getKey(), "buffer commodityId");
                double seconds = Objects.requireNonNull(entry.getValue(), "buffer coverage seconds");
                requirePositive(seconds, "buffer coverage seconds");
                bufferCopy.put(commodityId, seconds);
            }
            referenceBufferCoverageSecondsByCommodity = Collections.unmodifiableMap(bufferCopy);
            resourceOntologyFingerprint = requireText(resourceOntologyFingerprint, "resourceOntologyFingerprint");
            stationInfrastructureFingerprint = requireText(
                    stationInfrastructureFingerprint, "stationInfrastructureFingerprint");
            if (dependencyRequirements.isEmpty() || referenceBufferCoverageSecondsByCommodity.isEmpty()) {
                throw new IllegalArgumentException("corrected bootstrap profile requires dependencies and buffer evidence");
            }
        }
    }

    /**
     * Derives the corrected candidate profile while preserving v1 essential process/rate authority.
     *
     * @return deterministic corrected Stage-20E candidate profile
     */
    public static DerivedProfile deriveCurrent() {
        Stage20BootstrapRequirementCalibrationProfile.DerivedProfile demandAuthority =
                Stage20BootstrapRequirementCalibrationProfile.deriveCurrent();
        Stage20BootstrapServiceCadenceCalibrationProfile serviceCadence =
                Stage20BootstrapServiceCadenceCalibrationProfile.deriveCurrent();
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var stations = Stage18StationInfrastructureCatalogLoader.loadDefault();
        StationArchetypeDefinition referenceStation = stations.findArchetype(
                Stage20BootstrapRequirementCalibrationProfile.CURRENT_REFERENCE_STATION_ID);
        if (referenceStation == null) {
            throw new IllegalStateException("missing v1 bootstrap reference station");
        }

        TreeMap<String, String> familyByCommodity = new TreeMap<>();
        for (Requirement dependency : demandAuthority.dependencyRequirements()) {
            familyByCommodity.put(dependency.commodityId(), dependency.familyId());
        }

        TreeMap<String, Double> rateByCommodity = new TreeMap<>();
        Map<String, Double> totalRateByStorageClass = new HashMap<>();
        for (CommodityRequirement requirement : demandAuthority.bootstrapRequirements().essentialCommodities()) {
            rateByCommodity.put(requirement.commodityId(), requirement.minSupplierThroughputKgPerSecond());
            CommodityDefinition commodity = ontology.findCommodity(requirement.commodityId());
            if (commodity == null) {
                throw new IllegalStateException("v1 demand references missing Stage-18 commodity: "
                        + requirement.commodityId());
            }
            totalRateByStorageClass.merge(
                    commodity.storageClassId(), requirement.minSupplierThroughputKgPerSecond(), Double::sum);
        }

        ArrayList<CommodityRequirement> economicRequirements = new ArrayList<>();
        ArrayList<Requirement> dependencyRequirements = new ArrayList<>();
        TreeMap<String, Double> bufferCoverage = new TreeMap<>();
        for (Map.Entry<String, Double> entry : rateByCommodity.entrySet()) {
            String commodityId = entry.getKey();
            double requiredRate = entry.getValue();
            CommodityDefinition commodity = ontology.findCommodity(commodityId);
            Double storageCapacity = referenceStation.storageCapacityByClassKg().get(commodity.storageClassId());
            if (storageCapacity == null || !(storageCapacity > 0d)) {
                throw new IllegalStateException("reference station lacks storage for essential commodity: " + commodityId);
            }
            double sharedClassDemand = totalRateByStorageClass.get(commodity.storageClassId());
            double coverageSeconds = storageCapacity / sharedClassDemand;
            requirePositive(coverageSeconds, "reference buffer coverage");
            bufferCoverage.put(commodityId, coverageSeconds);

            double deliveryBudget = serviceCadence.maximumSupplierDeliveryTimeSeconds();
            economicRequirements.add(new CommodityRequirement(commodityId, deliveryBudget, requiredRate));
            String familyId = familyByCommodity.get(commodityId);
            if (familyId == null) {
                throw new IllegalStateException("v1 dependency projection missing commodity family: " + commodityId);
            }
            dependencyRequirements.add(new Requirement(
                    commodityId, familyId, requiredRate, deliveryBudget));
        }
        economicRequirements.sort(java.util.Comparator.comparing(CommodityRequirement::commodityId));
        dependencyRequirements.sort(java.util.Comparator.comparing(Requirement::commodityId));

        BootstrapRequirementProfile bootstrap = new BootstrapRequirementProfile(
                CURRENT_VERSION,
                serviceCadence.maximumSupplierDeliveryTimeSeconds(),
                demandAuthority.bootstrapRequirements().minIntermediateInputThroughputKgPerSecond(),
                economicRequirements);
        return new DerivedProfile(
                CURRENT_VERSION,
                demandAuthority.version(),
                serviceCadence,
                bootstrap,
                dependencyRequirements,
                bufferCoverage,
                ontology.getFingerprint(),
                stations.getFingerprint(),
                true);
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
