package com.spacesim.campaign;

import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import com.spacesim.world.generation.Stage20RepresentativeGeneratedWorldProbeProfileV3;
import com.spacesim.world.generation.Stage20ResolvedGeneratedWorldProductionProbe;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GeneratedCampaignProductionBootstrapRegressionTest {
    @Test
    void ordinaryCampaignKeepsAcceptedStage20ProfileAndUsesCanonicalPublicFactionNames() {
        long seed = Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED;

        var resolved = Stage20ResolvedGeneratedWorldProductionProbe.runCurrent(seed);
        assertEquals(
                Stage20RepresentativeGeneratedWorldProbeProfileV3.CURRENT_VERSION,
                resolved.representativeProfileVersion(),
                "ordinary campaign generation must retain the exact accepted Stage-20 V3 profile");

        GeneratedCampaignCoordinator campaign = assertDoesNotThrow(
                () -> GeneratedCampaignCoordinator.create(seed),
                "ordinary desktop launch path must survive freight planning and Stage-21 adoption");

        Map<String, String> names = campaign.runtime().world().getWorldFactionIdentities().stream()
                .collect(Collectors.toMap(
                        identity -> identity.stableFactionId(),
                        identity -> identity.displayName()));
        assertEquals("Империя", names.get("faction.alpha"));
        assertEquals("Индустриальный Союз", names.get("faction.beta"));
        assertFalse(names.containsValue("Alpha"));
        assertFalse(names.containsValue("Beta"));
    }
}
