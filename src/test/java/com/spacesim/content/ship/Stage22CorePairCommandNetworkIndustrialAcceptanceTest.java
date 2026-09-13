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

class Stage22CorePairCommandNetworkIndustrialAcceptanceTest {
    @Test
    void coreDatalinkIsARealStage18ProductWithFiniteCommonPhysics() {
        ShipEngineeringCatalog engineering = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        ModuleDefinition datalink = engineering.findModule(Stage22CorePairCommandNetworkProjection.DATALINK_MODULE_ID);

        assertNotNull(datalink);
        assertEquals(ModuleFamily.COMMUNICATION_DATALINK, datalink.family());
        assertTrue(datalink.massKg() > 0d && Double.isFinite(datalink.massKg()));
        assertTrue(datalink.continuousPowerDemandW() > 0d);
        assertEquals(64d, datalink.capabilityParameters().get("support_channels"));
        assertTrue(datalink.constructionInputs().size() >= 3);
        assertTrue(datalink.constructionInputs().stream()
                .allMatch(input -> input.amount() > 0d && Double.isFinite(input.amount())));

        Stage18ManufacturingProductRegistry manufacturing = Stage18ManufacturingProductRegistry.loadDefault()
                .withEngineeringCatalog(engineering, Provenance.STAGE22_AUTHORED);
        Stage18ManufacturingProductRegistry.ProductDefinition product = manufacturing.findProduct(datalink.id());

        assertNotNull(product);
        assertEquals(ProductKind.MODULE, product.kind());
        assertEquals(datalink.massKg(), product.unitMassKg());
        assertEquals(Provenance.STAGE22_AUTHORED, product.provenance());
    }

    @Test
    void eachFactionPaysOrdinaryShipyardWorkForTheSamePhysicalDatalink() {
        ShipEngineeringCatalog engineering = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        ShipyardIndustrialCatalog empireBase = Stage22EmpireShipyardIndustrialCatalogLoader.loadDefault();
        ShipyardIndustrialCatalog unionBase = Stage22IndustrialUnionShipyardIndustrialCatalogLoader.loadDefault();
        ShipyardIndustrialCatalog empire = Stage22CorePairShipyardIndustrialCatalogLoader.loadEmpireDefault();
        ShipyardIndustrialCatalog union = Stage22CorePairShipyardIndustrialCatalogLoader.loadIndustrialUnionDefault();
        String datalinkId = Stage22CorePairCommandNetworkProjection.DATALINK_MODULE_ID;

        assertNull(empireBase.findModuleProfile(datalinkId));
        assertNull(unionBase.findModuleProfile(datalinkId));
        assertEquals(empireBase.getModuleProfiles().size() + 2, empire.getModuleProfiles().size(),
                "M22.6 must expose exactly the paid FTL and command-network common profiles");
        assertEquals(unionBase.getModuleProfiles().size() + 2, union.getModuleProfiles().size(),
                "M22.6 must expose exactly the paid FTL and command-network common profiles");

        ModuleIndustrialProfile empireProfile = empire.findModuleProfile(datalinkId);
        ModuleIndustrialProfile unionProfile = union.findModuleProfile(datalinkId);
        assertPaidProfile(empireProfile);
        assertPaidProfile(unionProfile);
        assertNotEquals(empireProfile.fabricationCapabilities(), unionProfile.fabricationCapabilities());
        assertNotEquals(empireProfile.toolingTags(), unionProfile.toolingTags());
        assertNotEquals(empireProfile.manufacturingWorkSeconds(), unionProfile.manufacturingWorkSeconds());
        assertNotEquals(empireProfile.installationWorkSeconds(), unionProfile.installationWorkSeconds());

        assertThrows(IllegalArgumentException.class,
                () -> Stage22CorePairCommandNetworkIndustrialProjection.applyEmpire(empire, engineering));
        assertThrows(IllegalArgumentException.class,
                () -> Stage22CorePairCommandNetworkIndustrialProjection.applyIndustrialUnion(union, engineering));
    }

    @Test
    void commandVariantsPayWithDefenseSlotWhileAcceptedBaseFitsRemainShielded() {
        ShipEngineeringCatalog engineering = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        String datalinkId = Stage22CorePairCommandNetworkProjection.DATALINK_MODULE_ID;
        List<String> commandFits = List.of(
                Stage22CorePairCommandNetworkProjection.EMPIRE_DESTROYER_COMMAND_FIT,
                Stage22CorePairCommandNetworkProjection.UNION_DESTROYER_COMMAND_FIT);

        for (String fitId : commandFits) {
            DemonstratorFitDefinition fit = engineering.findDemonstratorFit(fitId);
            assertNotNull(fit, fitId);
            assertEquals(1L, fit.installedModules().stream()
                    .filter(module -> "utility_defense".equals(module.mountId()))
                    .filter(module -> datalinkId.equals(module.moduleId()))
                    .count(), fitId);
            assertTrue(fit.installedModules().stream()
                    .filter(module -> "utility_defense".equals(module.mountId()))
                    .map(module -> engineering.findModule(module.moduleId()))
                    .noneMatch(module -> module != null && module.family() == ModuleFamily.SHIELD_FIELD),
                    fitId + " must physically trade the defensive slot for network capability");
        }

        for (String baseFitId : List.of(
                "fit.empire.destroyer.screen_v1",
                "fit.industrial_union.destroyer.line_v1")) {
            DemonstratorFitDefinition fit = engineering.findDemonstratorFit(baseFitId);
            assertNotNull(fit, baseFitId);
            assertTrue(fit.installedModules().stream().noneMatch(module -> datalinkId.equals(module.moduleId())), baseFitId);
            assertTrue(fit.installedModules().stream()
                    .filter(module -> "utility_defense".equals(module.mountId()))
                    .map(module -> engineering.findModule(module.moduleId()))
                    .anyMatch(module -> module != null && module.family() == ModuleFamily.SHIELD_FIELD),
                    baseFitId + " accepted combat fit must retain its authored defense module");
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
