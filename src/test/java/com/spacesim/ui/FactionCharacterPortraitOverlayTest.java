package com.spacesim.ui;

import com.spacesim.content.Stage22FactionProfileCatalog;
import com.spacesim.content.Stage22FactionProfileLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class FactionCharacterPortraitOverlayTest {
    @Test
    void resolvesCoreRostersThroughGovernedStableFactionProfiles() {
        Stage22FactionProfileCatalog catalog = Stage22FactionProfileLoader.loadDefault();
        var empireProfile = catalog.systemicProfiles().stream()
                .filter(profile -> "core.empire".equals(profile.packageKey()))
                .findFirst().orElse(null);
        var industrialUnionProfile = catalog.systemicProfiles().stream()
                .filter(profile -> "core.industrial_union".equals(profile.packageKey()))
                .findFirst().orElse(null);

        assertNotNull(empireProfile);
        assertNotNull(industrialUnionProfile);
        assertEquals(FactionCharacterPortraitOverlay.RosterKind.EMPIRE,
                FactionCharacterPortraitOverlay.resolveRosterKind(
                        catalog, empireProfile.stableFactionId()));
        assertEquals(FactionCharacterPortraitOverlay.RosterKind.INDUSTRIAL_UNION,
                FactionCharacterPortraitOverlay.resolveRosterKind(
                        catalog, industrialUnionProfile.stableFactionId()));
    }

    @Test
    void ignoresMissingOrUnprofiledFactionIdentity() {
        Stage22FactionProfileCatalog catalog = Stage22FactionProfileLoader.loadDefault();

        assertNull(FactionCharacterPortraitOverlay.resolveRosterKind(catalog, null));
        assertNull(FactionCharacterPortraitOverlay.resolveRosterKind(catalog, ""));
        assertNull(FactionCharacterPortraitOverlay.resolveRosterKind(catalog, "unregistered-faction"));
        assertNull(FactionCharacterPortraitOverlay.resolveRosterKind(null, "unregistered-faction"));
    }
}
