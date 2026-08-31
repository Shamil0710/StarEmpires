package com.spacesim.content;

import com.spacesim.content.Stage22CoreContentSeamCatalog.AuthoringTemplateDefinition;
import com.spacesim.content.Stage22CoreContentSeamCatalog.RoleDefinition;
import com.spacesim.content.Stage22CoreContentSeamCatalog.RoleDomain;
import com.spacesim.content.Stage22CoreContentSeamCatalog.VisualBindingDefinition;
import com.spacesim.content.Stage22CoreProductionManifestCatalog.ProductionManifestDefinition;
import com.spacesim.content.Stage22CoreProductionManifestCatalog.SupportEnduranceRequirement;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.world.calibration.Stage20RepresentativeEnduranceProfile;
import com.spacesim.world.calibration.Stage20RepresentativeEnduranceProfile.EnduranceSample;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Cross-authority M22.2 gate joining common authoring metadata to accepted physical authorities.
 *
 * <p>This class is a validator/projection only. It does not persist or mutate engineering,
 * manufacturing, facility, shipyard, endurance, faction or mission state.</p>
 */
public final class Stage22CoreContentSeamValidator {
    private static final Set<String> REQUIRED_SUPPORT_ROLES = Set.of(
            "role.support.freight",
            "role.support.tanker_replenishment",
            "role.support.fleet_logistics_repair_salvage");

    private Stage22CoreContentSeamValidator() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads every accepted authority and validates the built-in M22.2 common seam.
     *
     * @return deterministic diagnostic validation evidence
     */
    public static ValidationReport validateDefault() {
        return validate(
                Stage22CoreContentSeamLoader.loadDefault(),
                Stage22CoreProductionManifestLoader.loadDefault(),
                ShipEngineeringCatalogLoader.loadDefault(),
                Stage18ManufacturingProductRegistry.loadDefault(),
                Stage18FacilityCatalogLoader.loadDefault(),
                Stage18ShipyardCatalogLoader.loadDefault(),
                Stage20RepresentativeEnduranceProfile.deriveCurrent());
    }

    /**
     * Validates one M22.2 seam against explicit authority instances.
     *
     * @param seam common role/mission/visual authoring metadata
     * @param production physical-reference manifests and support endurance floors
     * @param engineering existing engineering authority
     * @param manufacturing existing finished-product authority
     * @param facilities existing Stage-18 facility authority
     * @param shipyards existing Stage-18 shipyard authority
     * @param endurance existing Stage-20 endurance calibration
     * @return deterministic validation evidence
     */
    public static ValidationReport validate(
            Stage22CoreContentSeamCatalog seam,
            Stage22CoreProductionManifestCatalog production,
            ShipEngineeringCatalog engineering,
            Stage18ManufacturingProductRegistry manufacturing,
            Stage18FacilityCatalog facilities,
            Stage18ShipyardCatalog shipyards,
            Stage20RepresentativeEnduranceProfile endurance) {
        Objects.requireNonNull(seam, "seam");
        Objects.requireNonNull(production, "production");
        Objects.requireNonNull(engineering, "engineering");
        Objects.requireNonNull(manufacturing, "manufacturing");
        Objects.requireNonNull(facilities, "facilities");
        Objects.requireNonNull(shipyards, "shipyards");
        Objects.requireNonNull(endurance, "endurance");

        Map<String, String> visualFingerprints = new LinkedHashMap<>();
        for (VisualBindingDefinition visual : seam.visualBindings()) {
            String fingerprint = Stage22FitFingerprint.compute(engineering, visual.fitId());
            if (visual.expectedFitFingerprint() != null && !visual.expectedFitFingerprint().equals(fingerprint)) {
                throw new IllegalArgumentException("Stale exact fit fingerprint for visual binding: " + visual.id());
            }
            visualFingerprints.put(visual.id(), fingerprint);
        }

        for (ProductionManifestDefinition manifest : production.productionManifests()) {
            validateProductionManifest(manifest, engineering, manufacturing, facilities, shipyards);
        }

        for (AuthoringTemplateDefinition template : seam.authoringTemplates()) {
            ProductionManifestDefinition manifest = production.findManifestForFit(template.fitId());
            if (manifest == null
                    || !manifest.hullId().equals(template.productionHullId())) {
                throw new IllegalArgumentException(
                        "Authoring template has no exact component/hull/facility production manifest: " + template.id());
            }
            VisualBindingDefinition visual = seam.findVisualBinding(template.visualBindingId());
            if (visual == null || !visual.fitId().equals(manifest.fitId())) {
                throw new IllegalArgumentException("Authoring template breaks fit-to-visual chain: " + template.id());
            }
            if (!visualFingerprints.containsKey(visual.id())) {
                throw new IllegalStateException("Visual fingerprint was not resolved: " + visual.id());
            }
        }

        Map<String, Double> enduranceMargins = validateSupportEndurance(seam, production, endurance);
        return new ValidationReport(
                seam.fingerprint(),
                production.fingerprint(),
                Collections.unmodifiableMap(new LinkedHashMap<>(visualFingerprints)),
                Collections.unmodifiableMap(new LinkedHashMap<>(enduranceMargins)));
    }

    private static void validateProductionManifest(
            ProductionManifestDefinition manifest,
            ShipEngineeringCatalog engineering,
            Stage18ManufacturingProductRegistry manufacturing,
            Stage18FacilityCatalog facilities,
            Stage18ShipyardCatalog shipyards) {
        DemonstratorFitDefinition fit = engineering.findDemonstratorFit(manifest.fitId());
        if (fit == null || !fit.hullId().equals(manifest.hullId())) {
            throw new IllegalArgumentException("Production manifest fit/hull mismatch: " + manifest.id());
        }
        var hull = engineering.findHull(manifest.hullId());
        if (hull == null || shipyards.findHullProfile(manifest.hullId()) == null) {
            throw new IllegalArgumentException("Production manifest lacks an accepted physical hull path: " + manifest.id());
        }

        TreeSet<String> installedModules = fit.installedModules().stream()
                .map(InstalledModuleDefinition::moduleId)
                .collect(Collectors.toCollection(TreeSet::new));
        if (!installedModules.equals(new TreeSet<>(manifest.componentIds()))) {
            throw new IllegalArgumentException("Production manifest components do not equal exact fit modules: " + manifest.id());
        }
        for (String componentId : manifest.componentIds()) {
            if (manufacturing.findProduct(componentId) == null) {
                throw new IllegalArgumentException("Production manifest component is not manufacturable: " + componentId);
            }
        }

        var yard = shipyards.findYard(manifest.shipyardId());
        if (yard == null) {
            throw new IllegalArgumentException("Unknown production shipyard: " + manifest.shipyardId());
        }
        Set<String> expectedFacilities = new TreeSet<>(yard.requiredSupportFacilityDefinitionIds());
        Set<String> declaredFacilities = new TreeSet<>(manifest.requiredFacilityIds());
        if (!expectedFacilities.equals(declaredFacilities)) {
            throw new IllegalArgumentException("Production manifest facility set does not match shipyard requirements: " + manifest.id());
        }
        for (String facilityId : manifest.requiredFacilityIds()) {
            if (facilities.findFacility(facilityId) == null) {
                throw new IllegalArgumentException("Unknown required production facility: " + facilityId);
            }
        }

        var dimensions = hull.boundingDimensionsM();
        var berth = yard.berthDimensionsM();
        if (dimensions.lengthM() > berth.lengthM()
                || dimensions.widthM() > berth.widthM()
                || dimensions.heightM() > berth.heightM()
                || hull.maxOperationalMassKg() > yard.maxServiceMassKg()) {
            throw new IllegalArgumentException("Shipyard cannot physically service production hull: " + manifest.id());
        }
    }

    private static Map<String, Double> validateSupportEndurance(
            Stage22CoreContentSeamCatalog seam,
            Stage22CoreProductionManifestCatalog production,
            Stage20RepresentativeEnduranceProfile endurance) {
        Map<String, EnduranceSample> samples = endurance.samples().stream()
                .collect(Collectors.toMap(EnduranceSample::representativeId, value -> value));
        TreeSet<String> actualSupportRoles = seam.roles().stream()
                .filter(role -> role.domain() == RoleDomain.SUPPORT)
                .map(RoleDefinition::id)
                .collect(Collectors.toCollection(TreeSet::new));
        if (!actualSupportRoles.equals(new TreeSet<>(REQUIRED_SUPPORT_ROLES))) {
            throw new IllegalArgumentException("Common role taxonomy does not expose the exact three support families");
        }
        TreeSet<String> declared = production.supportEnduranceRequirements().stream()
                .map(SupportEnduranceRequirement::roleId)
                .collect(Collectors.toCollection(TreeSet::new));
        if (!declared.equals(actualSupportRoles)) {
            throw new IllegalArgumentException("Every support role must have exactly one endurance requirement");
        }

        Map<String, Double> margins = new LinkedHashMap<>();
        for (String roleId : actualSupportRoles) {
            RoleDefinition role = seam.findRole(roleId);
            SupportEnduranceRequirement requirement = production.findEnduranceRequirement(roleId);
            if (role == null || requirement == null || !role.enduranceReferenceId().equals(requirement.referenceId())) {
                throw new IllegalArgumentException("Support role/endurance reference mismatch: " + roleId);
            }
            EnduranceSample sample = samples.get(requirement.referenceId());
            if (sample == null) {
                throw new IllegalArgumentException("Unknown Stage-20 endurance reference: " + requirement.referenceId());
            }
            double margin = sample.missionStoresEnduranceS() - requirement.minimumMissionEnduranceS();
            if (margin < 0d || sample.fullReactionMassBurnAtSustainedS() <= 0d) {
                throw new IllegalArgumentException("Support endurance floor exceeds accepted calibration: " + roleId);
            }
            margins.put(roleId, margin);
        }
        return margins;
    }

    /**
     * Diagnostic evidence emitted after the full common seam validates.
     *
     * @param seamFingerprint common role/mission/visual semantic fingerprint
     * @param productionFingerprint production/endurance semantic fingerprint
     * @param visualFitFingerprints exact fit fingerprint resolved for every visual binding
     * @param supportEnduranceMarginS positive/zero stores-endurance margin for every support role
     */
    public record ValidationReport(
            String seamFingerprint,
            String productionFingerprint,
            Map<String, String> visualFitFingerprints,
            Map<String, Double> supportEnduranceMarginS) {
        /** Freezes validation evidence. */
        public ValidationReport {
            seamFingerprint = requireFingerprint(seamFingerprint, "seamFingerprint");
            productionFingerprint = requireFingerprint(productionFingerprint, "productionFingerprint");
            visualFitFingerprints = Map.copyOf(Objects.requireNonNull(visualFitFingerprints, "visualFitFingerprints"));
            supportEnduranceMarginS = Map.copyOf(Objects.requireNonNull(supportEnduranceMarginS, "supportEnduranceMarginS"));
            for (String value : visualFitFingerprints.values()) {
                requireFingerprint(value, "visual fit fingerprint");
            }
            if (!supportEnduranceMarginS.keySet().equals(REQUIRED_SUPPORT_ROLES)) {
                throw new IllegalArgumentException("Validation report must cover exact support roles");
            }
        }
    }

    private static String requireFingerprint(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (!checked.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " must be lowercase SHA-256");
        }
        return checked;
    }
}
