package com.spacesim.content.ship;

import com.spacesim.content.Stage228SmallCraftShipyardCatalogLoader;
import com.spacesim.content.Stage18ShipyardCatalog;
import com.spacesim.content.weapon.Stage228SmallCraftWeaponRuntimeProjection;
import com.spacesim.content.weapon.Stage22CorePairWeaponRuntimeCatalogLoader;
import com.spacesim.presentation.asset.Stage228SmallCraftVisualResolver;
import com.spacesim.presentation.asset.Stage22ProductionShipVisualResolver;
import com.spacesim.presentation.asset.Stage22ProductionShipVisualResolver.RuntimeVisualState;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipFittingValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Stage228SmallCraftProductionContentTest {

    @Test
    void sixProductionFitsUseOrdinaryEngineeringAuthorityAndPhysicalRoleDifferences() {
        ShipEngineeringCatalog engineering = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        ShipFittingValidator validator = new ShipFittingValidator(engineering);

        List<Stage228SmallCraftProductionProjection.DesignBinding> designs =
                Stage228SmallCraftProductionProjection.designBindings();
        assertEquals(6, designs.size());
        assertEquals(6, designs.stream().map(value -> value.fitId()).distinct().count());
        assertEquals(
                Set.of(
                        Stage228SmallCraftProductionProjection.ROLE_INTERCEPTION,
                        Stage228SmallCraftProductionProjection.ROLE_DEFENCE,
                        Stage228SmallCraftProductionProjection.ROLE_STRIKE),
                designs.stream().map(value -> value.roleId()).collect(Collectors.toSet()));

        for (var design : designs) {
            var authored = engineering.findDemonstratorFit(design.fitId());
            assertNotNull(authored);
            var hull = engineering.findHull(authored.hullId());
            assertNotNull(hull);
            var result = validator.validate(
                    hull,
                    InstalledFit.fromDemonstrator(authored),
                    ConsumableState.empty(),
                    DamageState.pristine());
            assertTrue(result.isValid(), design.fitId() + " -> " + result.issues());
            assertTrue(hull.boundingDimensionsM().lengthM() <= 35d);
            assertTrue(hull.maxOperationalMassKg() <= 560_000d);
        }

        var interceptor = engineering.findDemonstratorFit(
                Stage228SmallCraftProductionProjection.EMPIRE_INTERCEPTOR_FIT_ID);
        var defence = engineering.findDemonstratorFit(
                Stage228SmallCraftProductionProjection.EMPIRE_DEFENCE_FIT_ID);
        var strike = engineering.findDemonstratorFit(
                Stage228SmallCraftProductionProjection.EMPIRE_STRIKE_FIT_ID);

        assertTrue(hasModule(interceptor, Stage228SmallCraftProductionProjection.BEAM_ID));
        assertFalse(hasModule(interceptor, Stage228SmallCraftProductionProjection.SHIELD_ID));
        assertTrue(hasModule(defence, Stage228SmallCraftProductionProjection.BEAM_ID));
        assertTrue(hasModule(defence, Stage228SmallCraftProductionProjection.SHIELD_ID));
        assertTrue(hasModule(strike, Stage228SmallCraftProductionProjection.KINETIC_ID));
        assertFalse(hasModule(strike, Stage228SmallCraftProductionProjection.BEAM_ID));
    }

    @Test
    void strikeCraftUsesOrdinaryFiniteLauncherAndFactionMaterialAmmunition() {
        var runtime = Stage22CorePairWeaponRuntimeCatalogLoader.loadCombined();
        var profile = runtime.launchers().findByModuleId(
                Stage228SmallCraftProductionProjection.KINETIC_ID);
        assertNotNull(profile);
        assertEquals("kinetic_feed", profile.ammunitionInterfaceId());
        assertEquals(1d, profile.ammunitionAmountPerShot());
        assertNotNull(runtime.ammunition().findKinetic(
                Stage228SmallCraftWeaponRuntimeProjection.EMPIRE_AMMO_ID));
        assertNotNull(runtime.ammunition().findKinetic(
                Stage228SmallCraftWeaponRuntimeProjection.UNION_AMMO_ID));
        assertEquals(
                12d,
                runtime.ammunition().findKinetic(
                        Stage228SmallCraftWeaponRuntimeProjection.EMPIRE_AMMO_ID).massKg());
        assertEquals(
                12d,
                runtime.ammunition().findKinetic(
                        Stage228SmallCraftWeaponRuntimeProjection.UNION_AMMO_ID).massKg());
    }

    @Test
    void smallCraftHasCompleteIndustrialAndStage18PhysicalProductionCoverage() {
        ShipyardIndustrialCatalog empireIndustrial =
                Stage22CorePairShipyardIndustrialCatalogLoader.loadEmpireDefault();
        ShipyardIndustrialCatalog unionIndustrial =
                Stage22CorePairShipyardIndustrialCatalogLoader.loadIndustrialUnionDefault();
        Stage18ShipyardCatalog empirePhysical =
                Stage228SmallCraftShipyardCatalogLoader.loadEmpireDefault();
        Stage18ShipyardCatalog unionPhysical =
                Stage228SmallCraftShipyardCatalogLoader.loadIndustrialUnionDefault();

        assertNotNull(empireIndustrial.findHullProfile(
                Stage228SmallCraftProductionProjection.EMPIRE_HULL_ID));
        assertNotNull(unionIndustrial.findHullProfile(
                Stage228SmallCraftProductionProjection.UNION_HULL_ID));
        assertNotNull(empirePhysical.findHullProfile(
                Stage228SmallCraftProductionProjection.EMPIRE_HULL_ID));
        assertNotNull(unionPhysical.findHullProfile(
                Stage228SmallCraftProductionProjection.UNION_HULL_ID));

        for (String moduleId : List.of(
                Stage228SmallCraftProductionProjection.REACTOR_ID,
                Stage228SmallCraftProductionProjection.DRIVE_ID,
                Stage228SmallCraftProductionProjection.SENSOR_ID,
                Stage228SmallCraftProductionProjection.RADIATOR_ID,
                Stage228SmallCraftProductionProjection.SHIELD_ID,
                Stage228SmallCraftProductionProjection.BEAM_ID,
                Stage228SmallCraftProductionProjection.KINETIC_ID)) {
            assertNotNull(empireIndustrial.findModuleProfile(moduleId));
            assertNotNull(unionIndustrial.findModuleProfile(moduleId));
            assertNotNull(empirePhysical.findModuleProfile(moduleId));
            assertNotNull(unionPhysical.findModuleProfile(moduleId));
        }

        var engineering = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        assertEquals(
                engineering.findHull(Stage228SmallCraftProductionProjection.EMPIRE_HULL_ID)
                        .bareHullMassKg(),
                empirePhysical.findHullProfile(Stage228SmallCraftProductionProjection.EMPIRE_HULL_ID)
                        .buildInputsKg().stream().mapToDouble(
                                com.spacesim.content.Stage18ShipyardCatalog.PhysicalInputDefinition::massKg).sum(),
                1e-9);
        assertEquals(
                engineering.findHull(Stage228SmallCraftProductionProjection.UNION_HULL_ID)
                        .bareHullMassKg(),
                unionPhysical.findHullProfile(Stage228SmallCraftProductionProjection.UNION_HULL_ID)
                        .buildInputsKg().stream().mapToDouble(
                                com.spacesim.content.Stage18ShipyardCatalog.PhysicalInputDefinition::massKg).sum(),
                1e-9);
    }

    @Test
    void visualBindingUsesStableFitIdentityExactPhysicalScaleAndFailClosedFallback() {
        var empireCarrier = Stage22ProductionShipVisualResolver.resolveRole(
                "fleet.carrier.empire",
                "faction.imperial_directorate",
                "role.military.carrier",
                RuntimeVisualState.IDLE);
        var unionCarrier = Stage22ProductionShipVisualResolver.resolveRole(
                "fleet.carrier.union",
                "faction.industrial_union",
                "role.military.carrier",
                RuntimeVisualState.IDLE);

        var empireCraft = Stage228SmallCraftVisualResolver.resolve(
                "craft:1",
                Stage228SmallCraftProductionProjection.EMPIRE_INTERCEPTOR_FIT_ID);
        var unionCraft = Stage228SmallCraftVisualResolver.resolve(
                "craft:2",
                Stage228SmallCraftProductionProjection.UNION_STRIKE_FIT_ID);

        assertEquals(
                Stage228SmallCraftVisualResolver.ScaleAuthority.EXACT_PHYSICAL_CONTENT,
                empireCraft.scaleAuthority());
        assertEquals(32d, empireCraft.worldLengthM());
        assertEquals(35d, unionCraft.worldLengthM());
        assertTrue(empireCraft.worldLengthM() < empireCarrier.worldLengthM() * 0.10d);
        assertTrue(unionCraft.worldLengthM() < unionCarrier.worldLengthM() * 0.10d);
        assertEquals(3d, empireCraft.minimumMarkerPixels());

        assertThrows(
                IllegalArgumentException.class,
                () -> Stage228SmallCraftVisualResolver.resolve(
                        "craft:missing", "fit.missing.small_craft_v1"));
    }

    private static boolean hasModule(
            ShipEngineeringCatalog.DemonstratorFitDefinition fit,
            String moduleId) {
        return fit.installedModules().stream()
                .anyMatch(value -> value.moduleId().equals(moduleId));
    }
}
