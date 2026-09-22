package com.spacesim.ui;

import com.spacesim.campaign.GeneratedCampaignSession;
import com.spacesim.ui.CarrierOperationsUiProjection.CarrierView;
import com.spacesim.world.FleetId;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GeneratedWorldCarrierUiIntegrationTest {

    @Test
    void optionalCarrierSourceFlowsIntoExistingMilitaryInspectorProjectionWithoutMutatingRuntime() {
        GeneratedCampaignSession campaign = GeneratedCampaignSession.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        var runtime = campaign.runtime();
        GeneratedWorldUiModel compatibility = new GeneratedWorldUiModel(
                campaign.rootSeed(), runtime, campaign.content());
        var beforeState = campaign.captureState();
        var ordinary = compatibility.capture().military().stream()
                .findFirst().orElseThrow();

        assertFalse(ordinary.carrier());
        assertNull(ordinary.carrierOperations());

        CarrierView carrier = new CarrierView(
                new FleetId(ordinary.fleetId()),
                "fleet:" + ordinary.fleetId(),
                ordinary.factionId(),
                List.of(),
                List.of(),
                List.of());
        GeneratedWorldUiModel projected = new GeneratedWorldUiModel(
                campaign.rootSeed(),
                runtime,
                campaign.content(),
                fleetId -> fleetId.value() == ordinary.fleetId()
                        ? Optional.of(carrier) : Optional.empty());

        var bound = projected.capture().military().stream()
                .filter(value -> value.fleetId() == ordinary.fleetId())
                .findFirst().orElseThrow();

        assertTrue(bound.carrier());
        assertSame(carrier, bound.carrierOperations());
        assertEquals(ordinary.fleetId(), bound.carrierOperations().carrierFleetId().value());
        assertTrue(bound.sections().stream()
                .anyMatch(section -> section.title().equals("Авиагруппа / малые аппараты")));
        assertEquals(beforeState, campaign.captureState(),
                "read-only carrier projection must not mutate the generated campaign");
    }
}
