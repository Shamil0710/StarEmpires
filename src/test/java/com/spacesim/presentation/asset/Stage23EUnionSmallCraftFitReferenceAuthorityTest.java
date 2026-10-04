package com.spacesim.presentation.asset;

import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.Stage228SmallCraftEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage228SmallCraftProductionProjection;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage23EUnionSmallCraftFitReferenceAuthorityTest {
    @Test
    void unionFitReferencesOnlyExposeAuthoredProductionDifferences() {
        var catalog = Stage228SmallCraftEngineeringCatalogLoader.loadDefault();
        var hull = catalog.findHull(Stage228SmallCraftProductionProjection.UNION_HULL_ID);
        assertNotNull(hull);
        assertEquals(29d, hull.boundingDimensionsM().lengthM(), 0d);
        assertEquals(14d, hull.boundingDimensionsM().widthM(), 0d);
        assertEquals(1, hull.hardpoints().size());

        var weapon = hull.hardpoints().get(0);
        assertEquals("weapon_primary", weapon.id());
        assertEquals(0d, weapon.positionM().xM(), 0d);
        assertEquals(29d * 0.30d, weapon.positionM().yM(), 1.0e-9d);

        assertFit(
                catalog.findDemonstratorFit(Stage228SmallCraftProductionProjection.UNION_INTERCEPTOR_FIT_ID),
                Stage228SmallCraftProductionProjection.BEAM_ID,
                false,
                Stage228SmallCraftVisualResolver.MarkerKind.INTERCEPTOR_WEDGE);

        assertFit(
                catalog.findDemonstratorFit(Stage228SmallCraftProductionProjection.UNION_DEFENCE_FIT_ID),
                Stage228SmallCraftProductionProjection.BEAM_ID,
                true,
                Stage228SmallCraftVisualResolver.MarkerKind.DEFENCE_DIAMOND);

        assertFit(
                catalog.findDemonstratorFit(Stage228SmallCraftProductionProjection.UNION_STRIKE_FIT_ID),
                Stage228SmallCraftProductionProjection.KINETIC_ID,
                false,
                Stage228SmallCraftVisualResolver.MarkerKind.STRIKE_SPEAR);
    }

    private static void assertFit(
            DemonstratorFitDefinition fit,
            String expectedWeapon,
            boolean expectedShield,
            Stage228SmallCraftVisualResolver.MarkerKind expectedMarker) {
        assertNotNull(fit);
        assertEquals(Stage228SmallCraftProductionProjection.UNION_HULL_ID, fit.hullId());

        Map<String, String> mounts = fit.installedModules().stream()
                .collect(Collectors.toMap(
                        InstalledModuleDefinition::mountId,
                        InstalledModuleDefinition::moduleId));

        assertEquals(Stage228SmallCraftProductionProjection.REACTOR_ID, mounts.get("core_reactor"));
        assertEquals(Stage228SmallCraftProductionProjection.DRIVE_ID, mounts.get("core_drive"));
        assertEquals(Stage228SmallCraftProductionProjection.SENSOR_ID, mounts.get("utility_sensor"));
        assertEquals(Stage228SmallCraftProductionProjection.RADIATOR_ID, mounts.get("utility_thermal"));
        assertEquals(expectedWeapon, mounts.get("weapon_primary"));

        if (expectedShield) {
            assertEquals(Stage228SmallCraftProductionProjection.SHIELD_ID, mounts.get("utility_defense"));
        } else {
            assertFalse(mounts.containsKey("utility_defense"));
        }

        Set<String> allowed = expectedShield
                ? Set.of(
                        Stage228SmallCraftProductionProjection.REACTOR_ID,
                        Stage228SmallCraftProductionProjection.DRIVE_ID,
                        Stage228SmallCraftProductionProjection.SENSOR_ID,
                        Stage228SmallCraftProductionProjection.RADIATOR_ID,
                        Stage228SmallCraftProductionProjection.SHIELD_ID,
                        expectedWeapon)
                : Set.of(
                        Stage228SmallCraftProductionProjection.REACTOR_ID,
                        Stage228SmallCraftProductionProjection.DRIVE_ID,
                        Stage228SmallCraftProductionProjection.SENSOR_ID,
                        Stage228SmallCraftProductionProjection.RADIATOR_ID,
                        expectedWeapon);
        assertTrue(allowed.containsAll(mounts.values()));
        assertEquals(allowed.size(), Set.copyOf(mounts.values()).size());

        var visual = Stage228SmallCraftVisualResolver.resolve("stage23e-reference-audit", fit.id());
        assertEquals(expectedMarker, visual.binding().markerKind());
        assertEquals(29d, visual.worldLengthM(), 0d);
        assertEquals(14d, visual.worldWidthM(), 0d);
    }
}
