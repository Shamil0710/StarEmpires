package com.spacesim.world.generation;

import com.spacesim.content.Stage22EmpirePackageCatalog;
import com.spacesim.content.Stage22IndustrialUnionPackageCatalog;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class Stage227GeneratedCampaignProbeProfileTest {
    @Test
    void integratedCampaignUsesCanonicalStage22FactionIdentities() {
        var profile = Stage227GeneratedCampaignProbeProfile.deriveCurrent();
        Set<String> factions = Set.copyOf(profile.inputs().acceptance().stableFactionIds());

        assertEquals(Set.of(
                Stage22EmpirePackageCatalog.STABLE_FACTION_ID,
                Stage22IndustrialUnionPackageCatalog.STABLE_FACTION_ID), factions);
        assertFalse(factions.contains("faction.alpha"));
        assertFalse(factions.contains("faction.beta"));
        assertEquals(Stage227GeneratedCampaignProbeProfile.CURRENT_VERSION, profile.version());
    }

    @Test
    void historicalV3StillRetainsDiagnosticAlphaBetaFixture() {
        var historical = Stage20RepresentativeGeneratedWorldProbeProfileV3.deriveCurrent();
        assertEquals(Set.of("faction.alpha", "faction.beta"),
                Set.copyOf(historical.inputs().acceptance().stableFactionIds()));
    }
}
