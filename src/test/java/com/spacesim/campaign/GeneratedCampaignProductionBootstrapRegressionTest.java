package com.spacesim.campaign;

import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GeneratedCampaignProductionBootstrapRegressionTest {
    @Test
    void ordinaryCampaignSurvivesFreightPlanningAndUsesCanonicalPublicFactionNames() {
        long seed = Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED;

        GeneratedCampaignCoordinator campaign = assertDoesNotThrow(
                () -> GeneratedCampaignCoordinator.create(seed),
                "ordinary desktop launch path must retain the accepted Stage-20 profile, survive freight planning and adopt Stage-21 authorities");

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
