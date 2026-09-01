package com.spacesim.content;

import com.spacesim.content.Stage18ManufacturingProductRegistry.Provenance;
import com.spacesim.content.ship.Stage22EmpireEngineeringCatalogLoader;
import com.spacesim.world.Stage21HNpcMissionState;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22EmpirePackageAcceptanceTest {
    @Test
    void fullPackageCrossAuthorityValidationCoversNineRequiredRoles() {
        var report = Stage22EmpirePackageValidator.validateDefault();
        var common = Stage22CoreContentSeamLoader.loadDefault();
        var empire = Stage22EmpirePackageLoader.loadDefault();

        Set<String> commonRoles = common.roles().stream()
                .map(Stage22CoreContentSeamCatalog.RoleDefinition::id)
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(commonRoles, report.familyMetrics().keySet());
        assertEquals(9, report.familyMetrics().size());
        assertEquals(empire.fingerprint(), report.packageFingerprint());
        assertEquals(64, report.productionFingerprint().length());
        assertEquals(64, report.engineeringFingerprint().length());
        assertEquals(64, report.manufacturingFingerprint().length());
        assertEquals(64, report.shipyardFingerprint().length());
        assertEquals(64, report.stationFingerprint().length());
        report.familyMetrics().forEach((role, metrics) -> {
            assertTrue(metrics.remainingOperationalMassKg() >= 0d, role);
            assertTrue(metrics.continuousPowerMarginW() >= 0d, role);
            assertTrue(metrics.continuousThermalMarginW() >= 0d, role);
            assertTrue(metrics.staffedCrewBurden() > 0, role);
            assertTrue(metrics.authoredLifeSupportCapacity() > 0, role);
        });
    }

    @Test
    void everyEmpireModuleIsStage22AuthoredAndManufacturableByOrdinaryStage18Grammar() {
        var engineering = Stage22EmpireEngineeringCatalogLoader.loadDefault();
        var registry = Stage18ManufacturingProductRegistry.loadDefault()
                .withEngineeringCatalog(engineering, Provenance.STAGE22_AUTHORED);
        var manufacturing = Stage22EmpireManufacturingCatalogLoader.loadDefault();

        assertEquals(10, engineering.getModules().size());
        engineering.getModules().forEach(module -> {
            var product = registry.findProduct(module.id());
            assertNotNull(product, module.id());
            assertEquals(Provenance.STAGE22_AUTHORED, product.provenance(), module.id());
            var binding = manufacturing.findProductBinding(module.id());
            assertNotNull(binding, module.id());
            assertNotNull(manufacturing.findProductProfile(binding.profileId()), binding.profileId());
        });
    }

    @Test
    void primaryAndRefitFitsRemainDistinctExactVisualFingerprints() {
        var empire = Stage22EmpirePackageLoader.loadDefault();
        var engineering = Stage22EmpireEngineeringCatalogLoader.loadDefault();
        var bindings = Stage22EmpireProductionCatalogs.loadVisualBindings();
        assertEquals(18, bindings.size());

        for (var family : empire.shipFamilies()) {
            String primary = Stage22FitFingerprint.compute(engineering, family.primaryFitId());
            String refit = Stage22FitFingerprint.compute(engineering, family.refitFitId());
            assertNotEquals(primary, refit, family.familyId());
            assertTrue(bindings.stream().anyMatch(value -> value.fitId().equals(family.primaryFitId())
                    && primary.equals(value.expectedFitFingerprint())));
            assertTrue(bindings.stream().anyMatch(value -> value.fitId().equals(family.refitFitId())
                    && refit.equals(value.expectedFitFingerprint())));
        }
    }

    @Test
    void empireAuthoringDoesNotUseProvisionalTestIdentityOrHiddenBonusVocabulary() {
        var empire = Stage22EmpirePackageLoader.loadDefault();
        var engineering = Stage22EmpireEngineeringCatalogLoader.loadDefault();
        String canonical = empire.shipFamilies() + "|" + empire.stations() + "|" + empire.missions()
                + "|" + engineering.getHulls() + "|" + engineering.getModules()
                + "|" + engineering.getDemonstratorFits();
        String lower = canonical.toLowerCase(java.util.Locale.ROOT);
        assertFalse(lower.contains("test_"));
        assertFalse(lower.contains("hidden_bonus"));
        assertFalse(lower.contains("faction_multiplier"));
        assertFalse(lower.contains("empire_only_multiplier"));
    }

    @Test
    void productionManifestsUseExactPrimaryFitsAndCommonYard() {
        var empire = Stage22EmpirePackageLoader.loadDefault();
        var engineering = Stage22EmpireEngineeringCatalogLoader.loadDefault();
        var manifests = Stage22EmpireProductionCatalogs.loadManifests();
        assertEquals(9, manifests.productionManifests().size());
        for (var family : empire.shipFamilies()) {
            var fit = engineering.findDemonstratorFit(family.primaryFitId());
            var manifest = manifests.findManifest(family.productionManifestId());
            assertNotNull(fit);
            assertNotNull(manifest);
            assertEquals(fit.hullId(), manifest.hullId());
            assertEquals(fit.id(), manifest.fitId());
            assertEquals(Stage22EmpireProductionCatalogs.YARD_ID, manifest.shipyardId());
            assertEquals(
                    fit.installedModules().stream().map(value -> value.moduleId()).collect(Collectors.toSet()),
                    Set.copyOf(manifest.componentIds()));
        }
    }

    @Test
    void authoredMissionVariantsRemainInstantiableByStage21HLifecycleAuthority() {
        var empire = Stage22EmpirePackageLoader.loadDefault();
        assertEquals(10, empire.missions().size());
        assertEquals(9, empire.missions().stream().map(value -> value.runtimeTemplate()).distinct().count());
        empire.missions().forEach(mission -> {
            var issuer = empire.findNpc(mission.issuerNpcId());
            assertNotNull(issuer, mission.id());
            assertTrue(Stage21HNpcMissionState.canIssue(issuer.role(), mission.runtimeTemplate()), mission.id());
        });
    }
}
