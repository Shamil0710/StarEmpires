package com.spacesim.presentation.asset;

import com.spacesim.content.Stage22EmpireCharacterLineup;
import com.spacesim.content.Stage22EmpirePackageCatalog;
import com.spacesim.content.Stage22EmpirePackageLoader;
import com.spacesim.content.Stage22IndustrialUnionCharacterLineup;
import com.spacesim.content.Stage22IndustrialUnionPackageCatalog;
import com.spacesim.content.Stage22IndustrialUnionPackageLoader;
import com.spacesim.content.Stage22IndustrialUnionProductionCatalogs;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Automated M22.6 preflight for B19 grayscale silhouette and B20 shared character-style gates. */
class Stage22CorePairVisualCharacterAcceptanceTest {
    @Test
    void b19CoreFactionPrimarySilhouettesRemainDistinctBeforeColorAndHeraldry() throws Exception {
        Map<String, String> empireByRole = Stage22EmpireShipVisualCatalog.loadDefault().families().stream()
                .collect(Collectors.toMap(
                        Stage22EmpireShipVisualCatalog.FamilyVisual::roleId,
                        value -> value.assets().baseTexturePath()));

        Stage22IndustrialUnionPackageCatalog union = Stage22IndustrialUnionPackageLoader.loadDefault();
        Map<String, String> unionAssetByFit = Stage22IndustrialUnionProductionCatalogs.loadVisualBindings().stream()
                .collect(Collectors.toMap(value -> value.fitId(), value -> value.assetRef(), (left, right) -> left));
        Map<String, String> unionByRole = union.shipFamilies().stream().collect(Collectors.toMap(
                Stage22IndustrialUnionPackageCatalog.ShipFamilyDefinition::roleId,
                value -> unionAssetByFit.get(value.primaryFitId())));

        Set<String> expectedRoles = Stage22EmpirePackageLoader.loadDefault().shipFamilies().stream()
                .map(Stage22EmpirePackageCatalog.ShipFamilyDefinition::roleId)
                .collect(Collectors.toSet());
        assertEquals(expectedRoles, empireByRole.keySet());
        assertEquals(expectedRoles, unionByRole.keySet());

        Map<String, String> empireDigestByRole = new HashMap<>();
        Map<String, String> unionDigestByRole = new HashMap<>();
        for (String role : expectedRoles) {
            empireDigestByRole.put(role, alphaMaskDigest(empireByRole.get(role)));
            unionDigestByRole.put(role, alphaMaskDigest(unionByRole.get(role)));
            assertNotEquals(empireDigestByRole.get(role), unionDigestByRole.get(role), role);
        }
        assertEquals(9, new HashSet<>(empireDigestByRole.values()).size());
        assertEquals(9, new HashSet<>(unionDigestByRole.values()).size());
        Set<String> crossFactionOverlap = new HashSet<>(empireDigestByRole.values());
        crossFactionOverlap.retainAll(unionDigestByRole.values());
        assertTrue(crossFactionOverlap.isEmpty(),
                "core factions must not share an identical primary alpha silhouette before color/heraldry");
    }

    @Test
    void b20CharacterLineupsShareMasterStyleAuthorityButKeepDistinctFactionOverlays() {
        Stage22EmpireCharacterLineup.Catalog empire = Stage22EmpireCharacterLineup.loadDefault();
        Stage22IndustrialUnionCharacterLineup.Catalog union = Stage22IndustrialUnionCharacterLineup.loadDefault();

        assertEquals("docs/characters/character_master_prompt.md", empire.masterPromptRef());
        assertEquals(empire.masterPromptRef(), union.masterPromptRef());
        assertEquals("docs/factions/empire_visual_bible.md", empire.factionVisualRef());
        assertEquals("docs/factions/industrial_union_visual_bible.md", union.factionVisualRef());
        assertNotEquals(empire.factionVisualRef(), union.factionVisualRef());
        assertNotEquals(empire.fingerprint(), union.fingerprint());
        assertTrue(empire.overlays().size() >= 7);
        assertTrue(union.overlays().size() >= 7);
        assertTrue(empire.overlays().stream().allMatch(value ->
                !value.roleBrief().isBlank() && !value.statusReadability().isBlank() && !value.practicalGear().isBlank()));
        assertTrue(union.overlays().stream().allMatch(value ->
                !value.roleBrief().isBlank() && !value.statusReadability().isBlank() && !value.practicalGear().isBlank()));
        assertFalse(empire.overlays().stream().anyMatch(value -> value.id().startsWith("character_overlay.industrial_union.")));
        assertFalse(union.overlays().stream().anyMatch(value -> value.id().startsWith("character_overlay.empire.")));
    }

    private static String alphaMaskDigest(String path) throws Exception {
        assertNotNull(path, "visual binding path");
        BufferedImage image;
        try (InputStream stream = Stage22CorePairVisualCharacterAcceptanceTest.class
                .getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            image = ImageIO.read(stream);
        }
        assertNotNull(image, path);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(intBytes(image.getWidth()));
        digest.update(intBytes(image.getHeight()));
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                digest.update((byte) ((image.getRGB(x, y) >>> 24) & 0xff));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static byte[] intBytes(int value) {
        return new byte[] {
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value
        };
    }
}
