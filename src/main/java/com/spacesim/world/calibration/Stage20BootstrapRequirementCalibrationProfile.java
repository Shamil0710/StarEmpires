package com.spacesim.world.calibration;

import com.spacesim.content.Stage18FacilityCatalog;
import com.spacesim.content.Stage18FacilityCatalog.FacilityDefinition;
import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.content.Stage18RefiningCatalog;
import com.spacesim.content.Stage18RefiningCatalog.RecipeInputDefinition;
import com.spacesim.content.Stage18RefiningCatalog.RefiningRecipeDefinition;
import com.spacesim.content.Stage18RefiningCatalogLoader;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyCatalog.CommodityDefinition;
import com.spacesim.content.Stage18ResourceOntologyCatalog.QuantityUnit;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage18StationInfrastructureCatalog;
import com.spacesim.content.Stage18StationInfrastructureCatalog.StationArchetypeDefinition;
import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.world.Stage20EconomicBootstrapValidator.BootstrapRequirementProfile;
import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
import com.spacesim.world.Stage20FactionStartDependencyDiagnostics.Requirement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Versioned Stage-20E derivation of the ordinary-start bootstrap requirement profile from an
 * explicit reference industrial cell and authoritative Stage-18 physical process definitions.
 *
 * <p>The class deliberately separates policy from physics. Stage 18 does not mark any commodity as
 * civilization-essential, so the selected reference station and reference process streams are
 * explicit Stage-20 policy. Once selected, their kg/s requirements and supplier-route horizons are
 * derived from facility power/work/maintenance limits, station transfer/storage limits and recipe
 * mass fractions. No convenient demand rate or route duration is typed into the resulting profile.</p>
 *
 * <p>The current v1 policy represents a minimal frontier sustainment cell: one full water-purification
 * stream and one full structural-alloy stream on the existing frontier multipurpose station. It is
 * a generation-acceptance baseline, not population consumption and not a claim that an ordinary
 * start is technologically self-sufficient. Advanced industry may remain externally dependent.</p>
 */
public final class Stage20BootstrapRequirementCalibrationProfile {
    /** Current Stage-20E bootstrap requirement calibration version. */
    public static final String CURRENT_VERSION = "stage20e.bootstrap-requirements.v1";
    /** Explicit current reference station policy. */
    public static final String CURRENT_REFERENCE_STATION_ID = "station.infrastructure.frontier_multipurpose";

    private static final List<EssentialProcessPolicy> CURRENT_PROCESS_POLICY = List.of(
            new EssentialProcessPolicy("refining.water_purification", "family.water_cycle"),
            new EssentialProcessPolicy("refining.structural_alloy", "family.structural_industry"));

    private Stage20BootstrapRequirementCalibrationProfile() {
        throw new AssertionError("No instances");
    }

    /** Physical limiter responsible for one selected reference process rate. */
    public enum ProcessLimiter {
        /** Facility rated process power is the tightest physical limit. */
        PROCESS_POWER,
        /** Facility engineering work rate is the tightest physical limit. */
        ENGINEERING_WORK,
        /** Facility maintenance work rate is the tightest physical limit. */
        MAINTENANCE_WORK,
        /** Station cargo transfer is the tightest physical limit. */
        STATION_TRANSFER
    }

    /**
     * Explicit Stage-20 policy selecting one essential reference refining stream.
     *
     * @param recipeId authoritative Stage-18 refining recipe identity
     * @param dependencyFamilyId diagnostics grouping label for its extracted inputs
     */
    public record EssentialProcessPolicy(String recipeId, String dependencyFamilyId) {
        /**
         * Validates one policy row.
         *
         * @param recipeId Stage-18 recipe identity
         * @param dependencyFamilyId diagnostics family label
         */
        public EssentialProcessPolicy {
            recipeId = requireText(recipeId, "recipeId");
            dependencyFamilyId = requireText(dependencyFamilyId, "dependencyFamilyId");
        }
    }

    /**
     * Calculation evidence for one explicitly selected process stream.
     *
     * @param recipeId Stage-18 refining recipe
     * @param facilityDefinitionId unique installed compatible facility
     * @param grossInputKgPerSecond full-rate gross recipe input requirement
     * @param usefulOutputKgPerSecond useful process output at that input rate
     * @param powerLimitedInputKgPerSecond facility power ceiling expressed as gross input rate
     * @param engineeringLimitedInputKgPerSecond engineering-work ceiling expressed as gross input rate
     * @param maintenanceLimitedInputKgPerSecond maintenance-work ceiling expressed as gross input rate
     * @param stationTransferLimitedInputKgPerSecond station cargo-transfer ceiling
     * @param limitingAuthority physical limiter selected by the minimum rate
     */
    public record ProcessEvidence(
            String recipeId,
            String facilityDefinitionId,
            double grossInputKgPerSecond,
            double usefulOutputKgPerSecond,
            double powerLimitedInputKgPerSecond,
            double engineeringLimitedInputKgPerSecond,
            double maintenanceLimitedInputKgPerSecond,
            double stationTransferLimitedInputKgPerSecond,
            ProcessLimiter limitingAuthority) {
        /** Validates immutable process evidence. */
        public ProcessEvidence {
            recipeId = requireText(recipeId, "recipeId");
            facilityDefinitionId = requireText(facilityDefinitionId, "facilityDefinitionId");
            requirePositive(grossInputKgPerSecond, "grossInputKgPerSecond");
            requirePositive(usefulOutputKgPerSecond, "usefulOutputKgPerSecond");
            requirePositive(powerLimitedInputKgPerSecond, "powerLimitedInputKgPerSecond");
            requirePositive(engineeringLimitedInputKgPerSecond, "engineeringLimitedInputKgPerSecond");
            requirePositive(maintenanceLimitedInputKgPerSecond, "maintenanceLimitedInputKgPerSecond");
            requirePositive(stationTransferLimitedInputKgPerSecond, "stationTransferLimitedInputKgPerSecond");
            Objects.requireNonNull(limitingAuthority, "limitingAuthority");
        }
    }

    /**
     * Complete current derived acceptance authority and provenance.
     *
     * @param version stable Stage-20E calibration version
     * @param referenceStationArchetypeId explicit reference station policy
     * @param processPolicy explicit essential process selection
     * @param processEvidence physical rate calculations for selected processes
     * @param bootstrapRequirements existing Stage-20E economic acceptance input
     * @param dependencyRequirements exact matching start-dependency projection
     * @param resourceOntologyFingerprint Stage-18A semantic fingerprint
     * @param refiningFingerprint Stage-18C semantic fingerprint
     * @param facilityFingerprint Stage-18E semantic fingerprint
     * @param stationInfrastructureFingerprint Stage-18F semantic fingerprint
     * @param stage22ReviewRequired whether the policy selection still requires later balance review
     */
    public record DerivedProfile(
            String version,
            String referenceStationArchetypeId,
            List<EssentialProcessPolicy> processPolicy,
            List<ProcessEvidence> processEvidence,
            BootstrapRequirementProfile bootstrapRequirements,
            List<Requirement> dependencyRequirements,
            String resourceOntologyFingerprint,
            String refiningFingerprint,
            String facilityFingerprint,
            String stationInfrastructureFingerprint,
            boolean stage22ReviewRequired) {
        /** Validates and freezes a derived profile. */
        public DerivedProfile {
            version = requireText(version, "version");
            referenceStationArchetypeId = requireText(referenceStationArchetypeId, "referenceStationArchetypeId");
            processPolicy = List.copyOf(Objects.requireNonNull(processPolicy, "processPolicy"));
            processEvidence = List.copyOf(Objects.requireNonNull(processEvidence, "processEvidence"));
            Objects.requireNonNull(bootstrapRequirements, "bootstrapRequirements");
            dependencyRequirements = List.copyOf(Objects.requireNonNull(dependencyRequirements, "dependencyRequirements"));
            resourceOntologyFingerprint = requireText(resourceOntologyFingerprint, "resourceOntologyFingerprint");
            refiningFingerprint = requireText(refiningFingerprint, "refiningFingerprint");
            facilityFingerprint = requireText(facilityFingerprint, "facilityFingerprint");
            stationInfrastructureFingerprint = requireText(
                    stationInfrastructureFingerprint, "stationInfrastructureFingerprint");
            if (processPolicy.isEmpty() || processEvidence.isEmpty() || dependencyRequirements.isEmpty()) {
                throw new IllegalArgumentException("derived bootstrap profile must contain policy/evidence/requirements");
            }
        }
    }

    /**
     * Derives the current minimal frontier bootstrap profile from production Stage-18 catalogs.
     *
     * @return deterministic current derived profile
     */
    public static DerivedProfile deriveCurrent() {
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        Stage18RefiningCatalog refining = Stage18RefiningCatalogLoader.loadDefault();
        Stage18FacilityCatalog facilities = Stage18FacilityCatalogLoader.loadDefault();
        Stage18StationInfrastructureCatalog stations = Stage18StationInfrastructureCatalogLoader.loadDefault();
        return derive(
                CURRENT_VERSION,
                CURRENT_REFERENCE_STATION_ID,
                CURRENT_PROCESS_POLICY,
                ontology,
                refining,
                facilities,
                stations,
                true);
    }

    static DerivedProfile derive(
            String version,
            String referenceStationId,
            List<EssentialProcessPolicy> policy,
            Stage18ResourceOntologyCatalog ontology,
            Stage18RefiningCatalog refining,
            Stage18FacilityCatalog facilities,
            Stage18StationInfrastructureCatalog stations,
            boolean stage22ReviewRequired) {
        String checkedVersion = requireText(version, "version");
        String checkedStationId = requireText(referenceStationId, "referenceStationId");
        Objects.requireNonNull(policy, "policy");
        Stage18ResourceOntologyCatalog checkedOntology = Objects.requireNonNull(ontology, "ontology");
        Stage18RefiningCatalog checkedRefining = Objects.requireNonNull(refining, "refining");
        Stage18FacilityCatalog checkedFacilities = Objects.requireNonNull(facilities, "facilities");
        Stage18StationInfrastructureCatalog checkedStations = Objects.requireNonNull(stations, "stations");
        if (policy.isEmpty()) {
            throw new IllegalArgumentException("at least one essential process policy is required");
        }
        List<EssentialProcessPolicy> orderedPolicy = policy.stream()
                .sorted(Comparator.comparing(EssentialProcessPolicy::recipeId))
                .toList();
        if (orderedPolicy.stream().map(EssentialProcessPolicy::recipeId).distinct().count() != orderedPolicy.size()) {
            throw new IllegalArgumentException("essential process policy contains duplicate recipe IDs");
        }

        StationArchetypeDefinition station = checkedStations.findArchetype(checkedStationId);
        if (station == null) {
            throw new IllegalArgumentException("unknown reference station archetype: " + checkedStationId);
        }

        ArrayList<ProcessEvidence> evidence = new ArrayList<>();
        TreeMap<String, Double> requirementRateByCommodity = new TreeMap<>();
        TreeMap<String, String> familyByCommodity = new TreeMap<>();
        for (EssentialProcessPolicy processPolicy : orderedPolicy) {
            RefiningRecipeDefinition recipe = checkedRefining.findRecipe(processPolicy.recipeId());
            if (recipe == null) {
                throw new IllegalArgumentException("unknown essential refining recipe: " + processPolicy.recipeId());
            }
            FacilityDefinition facility = uniqueInstalledFacilityForRecipe(station, recipe, checkedFacilities);
            ProcessEvidence row = deriveProcessEvidence(station, facility, recipe);
            evidence.add(row);
            for (RecipeInputDefinition input : recipe.inputs()) {
                CommodityDefinition commodity = checkedOntology.findCommodity(input.commodityId());
                if (commodity == null || commodity.quantityUnit() != QuantityUnit.KILOGRAM) {
                    throw new IllegalArgumentException(
                            "essential refining input must be a kilogram-based Stage-18 commodity: "
                                    + input.commodityId());
                }
                double rate = row.grossInputKgPerSecond() * input.fractionOfInputMass();
                requirementRateByCommodity.merge(input.commodityId(), rate, Double::sum);
                String previousFamily = familyByCommodity.putIfAbsent(
                        input.commodityId(), processPolicy.dependencyFamilyId());
                if (previousFamily != null && !previousFamily.equals(processPolicy.dependencyFamilyId())) {
                    throw new IllegalArgumentException(
                            "one essential commodity cannot silently belong to multiple dependency families: "
                                    + input.commodityId());
                }
            }
        }
        evidence.sort(Comparator.comparing(ProcessEvidence::recipeId));

        double aggregateInboundRate = requirementRateByCommodity.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();
        if (aggregateInboundRate > station.transferMassRateKgPerSecond() + 1e-9d) {
            throw new IllegalArgumentException(
                    "reference essential process set exceeds station aggregate transfer authority");
        }

        Map<String, Double> totalRateByStorageClass = new HashMap<>();
        for (Map.Entry<String, Double> entry : requirementRateByCommodity.entrySet()) {
            CommodityDefinition commodity = checkedOntology.findCommodity(entry.getKey());
            if (!station.transferStorageClassIds().contains(commodity.storageClassId())) {
                throw new IllegalArgumentException(
                        "reference station cannot transfer essential commodity storage class: " + entry.getKey());
            }
            totalRateByStorageClass.merge(commodity.storageClassId(), entry.getValue(), Double::sum);
        }

        ArrayList<CommodityRequirement> commodityRequirements = new ArrayList<>();
        ArrayList<Requirement> dependencyRequirements = new ArrayList<>();
        double minimumRouteHorizon = Double.POSITIVE_INFINITY;
        double minimumRequirementRate = Double.POSITIVE_INFINITY;
        for (Map.Entry<String, Double> entry : requirementRateByCommodity.entrySet()) {
            CommodityDefinition commodity = checkedOntology.findCommodity(entry.getKey());
            Double storageCapacity = station.storageCapacityByClassKg().get(commodity.storageClassId());
            if (storageCapacity == null || !(storageCapacity > 0d)) {
                throw new IllegalArgumentException(
                        "reference station lacks physical storage for essential commodity: " + entry.getKey());
            }
            double sharedStorageDemandRate = totalRateByStorageClass.get(commodity.storageClassId());
            double routeHorizonSeconds = storageCapacity / sharedStorageDemandRate;
            requirePositive(routeHorizonSeconds, "routeHorizonSeconds");
            double requiredRate = entry.getValue();
            commodityRequirements.add(new CommodityRequirement(entry.getKey(), routeHorizonSeconds, requiredRate));
            dependencyRequirements.add(new Requirement(
                    entry.getKey(), familyByCommodity.get(entry.getKey()), requiredRate, routeHorizonSeconds));
            minimumRouteHorizon = Math.min(minimumRouteHorizon, routeHorizonSeconds);
            minimumRequirementRate = Math.min(minimumRequirementRate, requiredRate);
        }
        commodityRequirements.sort(Comparator.comparing(CommodityRequirement::commodityId));
        dependencyRequirements.sort(Comparator.comparing(Requirement::commodityId));

        BootstrapRequirementProfile bootstrap = new BootstrapRequirementProfile(
                checkedVersion,
                minimumRouteHorizon,
                minimumRequirementRate,
                commodityRequirements);
        return new DerivedProfile(
                checkedVersion,
                checkedStationId,
                orderedPolicy,
                evidence,
                bootstrap,
                dependencyRequirements,
                checkedOntology.getFingerprint(),
                checkedRefining.getFingerprint(),
                checkedFacilities.getFingerprint(),
                checkedStations.getFingerprint(),
                stage22ReviewRequired);
    }

    private static FacilityDefinition uniqueInstalledFacilityForRecipe(
            StationArchetypeDefinition station,
            RefiningRecipeDefinition recipe,
            Stage18FacilityCatalog facilities) {
        ArrayList<FacilityDefinition> compatible = new ArrayList<>();
        for (String facilityId : station.installedFacilityDefinitionIds()) {
            FacilityDefinition facility = facilities.findFacility(facilityId);
            if (facility == null) {
                throw new IllegalArgumentException("reference station contains unknown facility: " + facilityId);
            }
            if (facility.capabilityTags().containsAll(recipe.requiredCapabilityTags())) {
                compatible.add(facility);
            }
        }
        compatible.sort(Comparator.comparing(FacilityDefinition::id));
        if (compatible.size() != 1) {
            throw new IllegalArgumentException(
                    "essential process requires exactly one explicitly resolvable installed facility: "
                            + recipe.id() + ", candidates=" + compatible.stream().map(FacilityDefinition::id).toList());
        }
        return compatible.get(0);
    }

    private static ProcessEvidence deriveProcessEvidence(
            StationArchetypeDefinition station,
            FacilityDefinition facility,
            RefiningRecipeDefinition recipe) {
        double power = facility.ratedProcessPowerW() / recipe.energyJPerInputKg();
        double engineering = facility.engineeringWorkRate() / recipe.workSecondsPerInputKg();
        double maintenance = facility.maintenanceWorkRate() / recipe.maintenanceWorkSecondsPerInputKg();
        double transfer = station.transferMassRateKgPerSecond();
        double gross = Math.min(Math.min(power, engineering), Math.min(maintenance, transfer));
        ProcessLimiter limiter;
        if (gross == power) {
            limiter = ProcessLimiter.PROCESS_POWER;
        } else if (gross == engineering) {
            limiter = ProcessLimiter.ENGINEERING_WORK;
        } else if (gross == maintenance) {
            limiter = ProcessLimiter.MAINTENANCE_WORK;
        } else {
            limiter = ProcessLimiter.STATION_TRANSFER;
        }
        return new ProcessEvidence(
                recipe.id(),
                facility.id(),
                gross,
                gross * recipe.outputMassFraction(),
                power,
                engineering,
                maintenance,
                transfer,
                limiter);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static void requirePositive(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }
}
