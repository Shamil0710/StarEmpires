package com.spacesim.content.ship;

import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ManufacturingProductRegistry.ProductKind;
import com.spacesim.content.Stage18ManufacturingProductRegistry.Provenance;
import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.content.ship.ShipyardIndustrialCatalog.ModuleIndustrialProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22CorePairStrategicMobilityIndustrialAcceptanceTest {
    @Test
    void strategicFtlIsARealStage18ProductWithFiniteAuthoredConstructionBurden() {
        ShipEngineeringCatalog engineering = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        ModuleDefinition ftl = engineering.findModule(Stage22CorePairStrategicMobilityProjection.FTL_MODULE_ID);

        assertNotNull(ftl);
        assertEquals(ModuleFamily.FTL_JUMP, ftl.family());
        assertTrue(ftl.massKg() > 0d && Double.isFinite(ftl.massKg()));
        assertTrue(ftl.constructionInputs().size() >= 3);
        assertTrue(ftl.constructionInputs().stream()
                .allMatch(input -> input.amount() > 0d && Double.isFinite(input.amount())));

        Stage18ManufacturingProductRegistry manufacturing = Stage18ManufacturingProductRegistry.loadDefault()
                .withEngineeringCatalog(engineering, Provenance.STAGE22_AUTHORED);
        Stage18ManufacturingProductRegistry.ProductDefinition product = manufacturing.findProduct(ftl.id());

        assertNotNull(product);
        assertEquals(ProductKind.MODULE, product.kind());
        assertEquals(ftl.massKg(), product.unitMassKg());
        assertEquals(Provenance.STAGE22_AUTHORED, product.provenance());
    }

    @Test
    void eachFactionPaysOrdinaryShipyardWorkForTheCommonFtlModule() {
        ShipEngineeringCatalog engineering = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        ShipyardIndustrialCatalog empireBase = Stage22EmpireShipyardIndustrialCatalogLoader.loadDefault();
        ShipyardIndustrialCatalog unionBase = Stage22IndustrialUnionShipyardIndustrialCatalogLoader.loadDefault();
        ShipyardIndustrialCatalog empire = Stage22CorePairShipyardIndustrialCatalogLoader.loadEmpireDefault();
        ShipyardIndustrialCatalog union = Stage22CorePairShipyardIndustrialCatalogLoader.loadIndustrialUnionDefault();
        String ftlId = Stage22CorePairStrategicMobilityProjection.FTL_MODULE_ID;

        assertNull(empireBase.findModuleProfile(ftlId));
        assertNull(unionBase.findModuleProfile(ftlId));
        assertTrue(empire.getModuleProfiles().size() > empireBase.getModuleProfiles().size(),
                "M22.6 composition may contain multiple paid common module projections");
        assertTrue(union.getModuleProfiles().size() > unionBase.getModuleProfiles().size(),
                "M22.6 composition may contain multiple paid common module projections");

        ModuleIndustrialProfile empireProfile = empire.findModuleProfile(ftlId);
        ModuleIndustrialProfile unionProfile = union.findModuleProfile(ftlId);
        assertPaidProfile(empireProfile);
        assertPaidProfile(unionProfile);
        assertNotEquals(empireProfile.fabricationCapabilities(), unionProfile.fabricationCapabilities());
        assertNotEquals(empireProfile.toolingTags(), unionProfile.toolingTags());
        assertNotEquals(empireProfile.manufacturingWorkSeconds(), unionProfile.manufacturingWorkSeconds());
        assertNotEquals(empireProfile.installationWorkSeconds(), unionProfile.installationWorkSeconds());

        assertThrows(IllegalArgumentException.class,
                () -> Stage22CorePairStrategicMobilityIndustrialProjection.applyEmpire(empire, engineering));
        assertThrows(IllegalArgumentException.class,
                () -> Stage22CorePairStrategicMobilityIndustrialProjection.applyIndustrialUnion(union, engineering));
    }

    @Test
    void allStrategicVariantsCarryThePaidFtlWhileBaseCombatFitsRemainSeparate() {
        ShipEngineeringCatalog engineering = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        String ftlId = Stage22CorePairStrategicMobilityProjection.FTL_MODULE_ID;
        List<String> strategicFits = List.of(
                Stage22CorePairStrategicMobilityProjection.EMPIRE_DESTROYER_STRATEGIC_FIT,
                Stage22CorePairStrategicMobilityProjection.EMPIRE_TANKER_STRATEGIC_FIT,
                Stage22CorePairStrategicMobilityProjection.EMPIRE_SUPPORT_STRATEGIC_FIT,
                Stage22CorePairStrategicMobilityProjection.UNION_DESTROYER_STRATEGIC_FIT,
                Stage22CorePairStrategicMobilityProjection.UNION_TANKER_STRATEGIC_FIT,
                Stage22CorePairStrategicMobilityProjection.UNION_SUPPORT_STRATEGIC_FIT);

        for (String fitId : strategicFits) {
            DemonstratorFitDefinition fit = engineering.findDemonstratorFit(fitId);
            assertNotNull(fit, fitId);
            assertEquals(1L, fit.installedModules().stream()
                    .filter(module -> ftlId.equals(module.moduleId()))
                    .count(), fitId);
        }

        for (String baseFitId : List.of(
                "fit.empire.destroyer.screen_v1",
                "fit.industrial_union.destroyer.line_v1")) {
            DemonstratorFitDefinition fit = engineering.findDemonstratorFit(baseFitId);
            assertNotNull(fit, baseFitId);
            assertTrue(fit.installedModules().stream().noneMatch(module -> ftlId.equals(module.moduleId())), baseFitId);
        }
    }

    private static void assertPaidProfile(ModuleIndustrialProfile profile) {
        assertNotNull(profile);
        assertTrue(!profile.fabricationCapabilities().isEmpty());
        assertTrue(!profile.toolingTags().isEmpty());
        assertTrue(profile.precisionRequirement() > 0d && Double.isFinite(profile.precisionRequirement()));
        assertTrue(profile.industrialPowerW() > 0d && Double.isFinite(profile.industrialPowerW()));
        assertTrue(profile.laborRequirement() > 0);
        assertTrue(profile.automationRequirement() > 0);
        assertTrue(profile.manufacturingWorkSeconds() > 0d && Double.isFinite(profile.manufacturingWorkSeconds()));
        assertTrue(profile.installationWorkSeconds() > 0d && Double.isFinite(profile.installationWorkSeconds()));
        assertTrue(profile.removalWorkSeconds() > 0d && Double.isFinite(profile.removalWorkSeconds()));
    }
}
