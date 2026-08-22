package com.spacesim.presentation.asset;

import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.model.ShipType;
import com.spacesim.ship.Stage175ICombatTestContentPack;
import com.spacesim.ui.ShipVisualRole;
import com.spacesim.world.Stage20SpecialLocationWorld.LocationKind;
import com.spacesim.world.calibration.Stage20StationPhysicalGeometryProfile;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20MinimumPlayableSpriteCatalogTest {
    @Test
    void everyMinimumRoleIsBoundAndEveryPngHasRealTransparentAlpha() throws IOException {
        List<Stage20MinimumPlayableSpriteCatalog.SpriteBinding> bindings =
                Stage20MinimumPlayableSpriteCatalog.allBindings();
        assertEquals(
                Stage20MinimumPlayableSpriteCatalog.VisualRole.values().length,
                bindings.size());
        assertEquals(10, Stage20MinimumPlayableSpriteCatalog.allTexturePaths().size());
        assertEquals(bindings.size(), bindings.stream().map(value -> value.role().name()).distinct().count());

        for (String path : Stage20MinimumPlayableSpriteCatalog.allTexturePaths()) {
            BufferedImage image = image(path);
            assertTrue(image.getColorModel().hasAlpha(), path);
            assertEquals(0, alpha(image.getRGB(0, 0)), path);
            assertEquals(0, alpha(image.getRGB(image.getWidth() - 1, image.getHeight() - 1)), path);
            assertTrue(hasOpaquePixel(image), path);
            assertTrue(hasTransparentPixel(image), path);
        }
    }

    @Test
    void resourceAtlasKeepsFourDistinctNonEmptyRegions() throws IOException {
        BufferedImage atlas = image("assets/stage20_5/resources/resource_body_atlas_v1.png");
        Set<Long> regionFingerprints = new HashSet<>();
        for (var role : List.of(
                Stage20MinimumPlayableSpriteCatalog.VisualRole.RESOURCE_CARBONACEOUS,
                Stage20MinimumPlayableSpriteCatalog.VisualRole.RESOURCE_WATER_ICE,
                Stage20MinimumPlayableSpriteCatalog.VisualRole.RESOURCE_METALLIC,
                Stage20MinimumPlayableSpriteCatalog.VisualRole.RESOURCE_MINERAL)) {
            var region = Stage20MinimumPlayableSpriteCatalog.binding(role).region();
            long fingerprint = 1L;
            int opaque = 0;
            for (int y = region.pixelY(); y < region.pixelY() + region.pixelHeight(); y += 8) {
                for (int x = region.pixelX(); x < region.pixelX() + region.pixelWidth(); x += 8) {
                    int rgba = atlas.getRGB(x, y);
                    if (alpha(rgba) > 32) {
                        opaque++;
                        fingerprint = fingerprint * 31L + rgba;
                    }
                }
            }
            assertTrue(opaque > 100, role.name());
            assertTrue(regionFingerprints.add(fingerprint), role.name());
        }
    }

    @Test
    void exactProductionAndFreightHullMappingsUsePhysicalDimensionsAndHardpoints() {
        var production = ShipEngineeringCatalogLoader.loadDefault();
        var escort = Stage20MinimumPlayableSpriteCatalog.resolveShip(
                "hull.escort_destroyer_v1",
                Stage20MinimumPlayableSpriteCatalog.ShipRole.UTILITY,
                production);
        assertEquals(Stage20MinimumPlayableSpriteCatalog.ScaleAuthority.EXACT_PHYSICAL_CONTENT,
                escort.scaleAuthority());
        assertEquals(220d, escort.worldLengthM(), 0d);
        assertEquals(72d, escort.worldWidthM(), 0d);
        assertEquals(Stage20MinimumPlayableSpriteCatalog.VisualRole.LIGHT_COMBAT_ESCORT_SHIP,
                escort.binding().role());
        assertEquals("weapon_spinal", escort.binding().hardpoints().get(0).id());
        assertEquals(0.8272727f, escort.binding().hardpoints().get(0).normalizedX(), 1.0e-7f);

        var freight = Stage20MinimumPlayableSpriteCatalog.resolveShip(
                "hull.test_bulk_freighter_v1",
                Stage20MinimumPlayableSpriteCatalog.ShipRole.LIGHT_COMBAT_ESCORT,
                Stage175ICombatTestContentPack.load());
        assertEquals(Stage20MinimumPlayableSpriteCatalog.ScaleAuthority.EXACT_PHYSICAL_CONTENT,
                freight.scaleAuthority());
        assertEquals(Stage20MinimumPlayableSpriteCatalog.VisualRole.CARGO_TRANSPORT_SHIP,
                freight.binding().role());
        assertEquals(280d, freight.worldLengthM(), 0d);
        assertEquals(88d, freight.worldWidthM(), 0d);
    }

    @Test
    void unknownContentUsesExplicitRoleFallbackWithoutInventingPhysicalAuthority() {
        var fallback = Stage20MinimumPlayableSpriteCatalog.resolveShip(
                "hull.future.unknown",
                Stage20MinimumPlayableSpriteCatalog.ShipRole.MINING_INDUSTRIAL,
                ShipEngineeringCatalogLoader.loadDefault());

        assertEquals(Stage20MinimumPlayableSpriteCatalog.ScaleAuthority.PRESENTATION_FALLBACK,
                fallback.scaleAuthority());
        assertTrue(fallback.authorityId().startsWith(
                Stage20MinimumPlayableSpriteCatalog.FALLBACK_VERSION));
        assertEquals(Stage20MinimumPlayableSpriteCatalog.VisualRole.MINING_INDUSTRIAL_SHIP,
                fallback.binding().role());
    }

    @Test
    void stationResourceSpecialAndExistingViewerPathsResolveWithoutSimulationMutation() {
        Stage20StationPhysicalGeometryProfile geometry =
                Stage20StationPhysicalGeometryProfile.deriveCurrent();
        var trade = Stage20MinimumPlayableSpriteCatalog.resolveStation(
                "station.infrastructure.trade_logistics_hub", false, geometry);
        var yard = Stage20MinimumPlayableSpriteCatalog.resolveStation(
                "station.infrastructure.trade_logistics_hub", true, geometry);
        assertEquals(1_600d, trade.worldLengthM(), 0d);
        assertEquals(1_000d, trade.worldWidthM(), 0d);
        assertEquals(Stage20MinimumPlayableSpriteCatalog.VisualRole.TRADE_DOCK_STATION,
                trade.binding().role());
        assertEquals(Stage20MinimumPlayableSpriteCatalog.VisualRole.SHIPYARD_STATION,
                yard.binding().role());

        var resource = Stage20MinimumPlayableSpriteCatalog.resolveResource(
                "occurrence.water_ice", 312d, 144d);
        assertEquals(312d, resource.worldLengthM(), 0d);
        assertEquals(144d, resource.worldWidthM(), 0d);
        assertEquals(Stage20MinimumPlayableSpriteCatalog.VisualRole.RESOURCE_WATER_ICE,
                resource.binding().role());

        var derelict = Stage20MinimumPlayableSpriteCatalog.resolveSpecialLocation(LocationKind.DERELICT);
        assertEquals(Stage20MinimumPlayableSpriteCatalog.VisualRole.DERELICT,
                derelict.binding().role());
        assertThrows(IllegalArgumentException.class,
                () -> Stage20MinimumPlayableSpriteCatalog.resolveSpecialLocation(LocationKind.ANOMALY));

        ShipType ordinaryType = ShipType.MINING_SHIP;
        var playable = Stage20MinimumPlayableSpriteCatalog.resolvePlayable(ordinaryType);
        assertEquals(ShipType.MINING_SHIP, ordinaryType);
        assertEquals(Stage20MinimumPlayableSpriteCatalog.VisualRole.MINING_INDUSTRIAL_SHIP,
                playable.binding().role());
        assertNotEquals(
                Stage20MinimumPlayableSpriteCatalog.resolveCombatRole(ShipVisualRole.KINETIC)
                        .binding().assetId(),
                Stage20MinimumPlayableSpriteCatalog.resolveCombatRole(ShipVisualRole.BALANCED)
                        .binding().assetId());
    }

    private static BufferedImage image(String path) throws IOException {
        try (InputStream input = Stage20MinimumPlayableSpriteCatalogTest.class
                .getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path);
            BufferedImage image = ImageIO.read(input);
            assertNotNull(image, path);
            return image;
        }
    }

    private static boolean hasOpaquePixel(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y += 4) {
            for (int x = 0; x < image.getWidth(); x += 4) {
                if (alpha(image.getRGB(x, y)) > 224) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasTransparentPixel(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y += 4) {
            for (int x = 0; x < image.getWidth(); x += 4) {
                if (alpha(image.getRGB(x, y)) == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int alpha(int rgba) {
        return rgba >>> 24;
    }
}
