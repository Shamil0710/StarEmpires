package com.spacesim.presentation.asset;

import com.spacesim.content.Stage22EmpirePackageCatalog;
import com.spacesim.content.Stage22EmpirePackageLoader;
import com.spacesim.content.Stage22FitFingerprint;
import com.spacesim.content.ship.Stage22EmpireEngineeringCatalogLoader;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22EmpireShipVisualCatalogTest {
    @Test
    void productionCatalogCoversTheExactNineRoleFloorAndEngineeringGeometry() {
        var packageCatalog = Stage22EmpirePackageLoader.loadDefault();
        var engineering = Stage22EmpireEngineeringCatalogLoader.loadDefault();
        var visuals = Stage22EmpireShipVisualCatalog.loadDefault();

        Set<String> expectedRoles = packageCatalog.shipFamilies().stream()
                .map(Stage22EmpirePackageCatalog.ShipFamilyDefinition::roleId)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> actualRoles = visuals.families().stream()
                .map(Stage22EmpireShipVisualCatalog.FamilyVisual::roleId)
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(expectedRoles, actualRoles);
        assertEquals(9, visuals.families().size());
        assertEquals(engineering.getFingerprint(), visuals.engineeringFingerprint());
        assertEquals(64, visuals.fingerprint().length());

        for (var visual : visuals.families()) {
            var fit = engineering.findDemonstratorFit(visual.primaryFitId());
            var hull = engineering.findHull(fit.hullId());
            assertEquals((float) hull.boundingDimensionsM().lengthM(), visual.sprite().worldWidth());
            assertEquals((float) hull.boundingDimensionsM().widthM(), visual.sprite().worldHeight());
            assertEquals(visual.sprite().worldWidth(), visual.sprite().collisionWidth());
            assertEquals(visual.sprite().worldHeight(), visual.sprite().collisionHeight());
            assertEquals(0.5f, visual.sprite().pivotX());
            assertEquals(0.5f, visual.sprite().pivotY());
            assertEquals(SourceFacing.RIGHT, visual.sprite().sourceFacing());
            assertTrue(visual.sprite().hardpoints().stream().anyMatch(value -> value.type() == VisualHardpointType.ENGINE));
            for (var engineeringHardpoint : hull.hardpoints()) {
                assertTrue(visual.sprite().hardpoints().stream()
                        .anyMatch(value -> value.id().equals("engineering_" + engineeringHardpoint.id())),
                        visual.familyId() + " -> " + engineeringHardpoint.id());
            }
            for (var compartment : hull.compartments()) {
                assertTrue(visual.sprite().hardpoints().stream()
                        .anyMatch(value -> value.id().equals("service_" + compartment.id())),
                        visual.familyId() + " -> " + compartment.id());
            }
        }
    }

    @Test
    void everyFamilyOwnsAlignedAlphaEmissiveDamageAndSharedEngineStates() throws IOException {
        var visuals = Stage22EmpireShipVisualCatalog.loadDefault();
        Set<String> familyBasePaths = new HashSet<>();
        for (var visual : visuals.families()) {
            ShipVisualAssetSet assets = visual.assets();
            assertEquals(5, assets.allTexturePaths().size());
            assertEquals(5L, assets.allTexturePaths().stream().distinct().count());
            assertTrue(familyBasePaths.add(assets.baseTexturePath()));
            assertEquals(Stage22EmpireShipVisualCatalog.ENGINE_IDLE, assets.engineIdleTexturePath());
            assertEquals(Stage22EmpireShipVisualCatalog.ENGINE_THRUST, assets.engineThrustTexturePath());
            assertTrue(assets.baseTexturePath().contains("/production/"));

            BufferedImage base = readPng(assets.baseTexturePath());
            BufferedImage emissive = readPng(assets.emissiveTexturePath());
            BufferedImage damage = readPng(assets.damageTexturePath());
            assertEquals(base.getWidth(), emissive.getWidth());
            assertEquals(base.getHeight(), emissive.getHeight());
            assertEquals(base.getWidth(), damage.getWidth());
            assertEquals(base.getHeight(), damage.getHeight());
            assertTrue(base.getWidth() >= 128 && base.getHeight() >= 96);

            LayerStats stats = layerStats(base, emissive, damage);
            int pixels = base.getWidth() * base.getHeight();
            assertTrue(stats.baseOpaque() > pixels * 0.10, visual.familyId());
            assertTrue(stats.baseOpaque() < pixels * 0.60, visual.familyId());
            assertTrue(stats.emissiveOpaque() > 0, visual.familyId());
            assertTrue(stats.damageOpaque() > 0, visual.familyId());
            assertTrue(stats.emissiveOutsideBase() <= 8, visual.familyId());
            assertTrue(stats.damageOutsideBase() <= 32, visual.familyId());

            assertResource(visual.markerSilhouettePath());
            assets.allTexturePaths().forEach(Stage22EmpireShipVisualCatalogTest::assertResource);
            assertEquals(Stage22EmpireShipVisualCatalog.PROVENANCE_REF, visual.provenanceRef());
        }
        assertEquals(9, familyBasePaths.size());
        assertTrue(readPng(Stage22EmpireShipVisualCatalog.ENGINE_IDLE).getWidth() > 0);
        assertTrue(readPng(Stage22EmpireShipVisualCatalog.ENGINE_THRUST).getWidth() > 0);
    }

    @Test
    void primaryFamilyMasksRemainDistinctBeforeColorAndHeraldry() throws IOException {
        var visuals = Stage22EmpireShipVisualCatalog.loadDefault();
        Set<String> maskSignatures = new HashSet<>();
        for (var visual : visuals.families()) {
            BufferedImage base = readPng(visual.assets().baseTexturePath());
            int occupied = 0;
            int minX = base.getWidth();
            int minY = base.getHeight();
            int maxX = -1;
            int maxY = -1;
            for (int y = 0; y < base.getHeight(); y++) {
                for (int x = 0; x < base.getWidth(); x++) {
                    if (((base.getRGB(x, y) >>> 24) & 0xff) != 0) {
                        occupied++;
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
            String signature = occupied + ":" + minX + ":" + minY + ":" + maxX + ":" + maxY;
            assertTrue(maskSignatures.add(signature), "duplicate alpha silhouette signature for " + visual.familyId());
        }
        assertEquals(9, maskSignatures.size());
    }

    @Test
    void emitsExactFitFingerprintDiagnosticsForIndependentPinning() {
        var packageCatalog = Stage22EmpirePackageLoader.loadDefault();
        var engineering = Stage22EmpireEngineeringCatalogLoader.loadDefault();
        for (var family : packageCatalog.shipFamilies()) {
            System.out.println("M22_3_EMPIRE_FIT_PIN|" + family.primaryFitId() + "|"
                    + Stage22FitFingerprint.compute(engineering, family.primaryFitId()));
            System.out.println("M22_3_EMPIRE_FIT_PIN|" + family.refitFitId() + "|"
                    + Stage22FitFingerprint.compute(engineering, family.refitFitId()));
        }
    }

    private static BufferedImage readPng(String path) throws IOException {
        try (InputStream stream = Stage22EmpireShipVisualCatalogTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image, path);
            return image;
        }
    }

    private static LayerStats layerStats(BufferedImage base, BufferedImage emissive, BufferedImage damage) {
        int baseOpaque = 0;
        int emissiveOpaque = 0;
        int damageOpaque = 0;
        int emissiveOutsideBase = 0;
        int damageOutsideBase = 0;
        for (int y = 0; y < base.getHeight(); y++) {
            for (int x = 0; x < base.getWidth(); x++) {
                boolean b = alpha(base, x, y) != 0;
                boolean e = alpha(emissive, x, y) != 0;
                boolean d = alpha(damage, x, y) != 0;
                if (b) baseOpaque++;
                if (e) emissiveOpaque++;
                if (d) damageOpaque++;
                if (e && !b) emissiveOutsideBase++;
                if (d && !b) damageOutsideBase++;
            }
        }
        return new LayerStats(baseOpaque, emissiveOpaque, damageOpaque, emissiveOutsideBase, damageOutsideBase);
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) >>> 24) & 0xff;
    }

    private static void assertResource(String path) {
        assertNotNull(Stage22EmpireShipVisualCatalogTest.class.getClassLoader().getResource(path), path);
    }

    private record LayerStats(
            int baseOpaque,
            int emissiveOpaque,
            int damageOpaque,
            int emissiveOutsideBase,
            int damageOutsideBase) { }
}
