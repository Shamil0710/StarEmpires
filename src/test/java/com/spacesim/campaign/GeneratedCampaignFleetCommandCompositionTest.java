package com.spacesim.campaign;

import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.world.FleetForceRegistry;
import com.spacesim.world.FleetReadinessEvaluator;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedCampaignFleetCommandCompositionTest {
    @Test
    void commandGroupsUseRealGeneratedFleetsAndContinueAcrossNativeSave() {
        GeneratedCampaignCoordinator campaign = GeneratedCampaignCoordinator.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        FleetForceRegistry firstForces = reconstructForces(campaign);
        var available = firstForces.entries().stream()
                .filter(entry -> entry.factionId() >= 0)
                .toList();
        assertTrue(available.size() >= 2,
                "default generated campaign needs at least two affiliated fleets for live Stage-21D continuation proof");
        StarSystemId home = campaign.session().runtime().world().getTopology().systems().get(0).id();

        var firstForce = available.get(0);
        var firstGroup = campaign.formFleetCommandGroup(
                firstForces,
                firstForce.factionId(),
                "M22.7 live group A",
                List.of(firstForce.fleetId()),
                home,
                false,
                false,
                5_000);
        assertEquals(firstForce.fleetId(), firstGroup.memberFleetIds().get(0));
        var beforeSave = campaign.fleetCommands();

        GeneratedCampaignCoordinator restored = GeneratedCampaignCoordinator.decodeOrMigrate(campaign.encode());
        assertEquals(beforeSave, restored.fleetCommands());

        FleetForceRegistry restoredForces = reconstructForces(restored);
        HashSet<com.spacesim.world.FleetId> assigned = new HashSet<>();
        restored.fleetCommands().groups().forEach(group -> assigned.addAll(group.memberFleetIds()));
        var secondForce = restoredForces.entries().stream()
                .filter(entry -> entry.factionId() >= 0)
                .filter(entry -> !assigned.contains(entry.fleetId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("restored generated campaign needs an unassigned physical fleet"));

        restored.formFleetCommandGroup(
                restoredForces,
                secondForce.factionId(),
                "M22.7 live group B",
                List.of(secondForce.fleetId()),
                home,
                false,
                true,
                4_000);
        assertNotEquals(beforeSave, restored.fleetCommands(),
                "restored Stage-21D state must remain live rather than a passive checkpoint value");

        GeneratedCampaignCoordinator continued = GeneratedCampaignCoordinator.decodeOrMigrate(restored.encode());
        assertEquals(restored.fleetCommands(), continued.fleetCommands());
        assertEquals(restored.session().captureState().worldState(), continued.session().captureState().worldState(),
                "command metadata composition must not create or move a second physical fleet authority");
    }

    private static FleetForceRegistry reconstructForces(GeneratedCampaignCoordinator campaign) {
        return FleetForceRegistry.reconstruct(
                campaign.session().captureState().worldState(),
                new FleetReadinessEvaluator(ShipEngineeringCatalogLoader.loadDefault()),
                Map.of());
    }
}
