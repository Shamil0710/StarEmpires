package com.spacesim.campaign;

import com.spacesim.persistence.Stage20LiveArrivalAuthorityIntegration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ordinary blind jump from the new pilot's start to a neighbouring system and back: each jump,
 * departure approach included, takes about one game hour and arrives near the destination hub.
 */
@Tag("scenario")
class JumpScenarioTest {
    private static final long SEED = 2L;
    /** Two game hours at the 0.1 s fixed step: the upper bound of an ordinary jump. */
    private static final long MAX_JUMP_TICKS = 72_000L;

    @Test
    void ordinaryJumpThereAndBackTakesAboutAGameHourEachWay() {
        var campaign = Stage228CampaignAuthority.create(SEED);
        campaign.submitIndependentPilotStart(campaign.previewIndependentPilotStart());
        var pilot = new ScenarioPilot(campaign, 4 * MAX_JUMP_TICKS);
        var home = pilot.fleet().systemId();
        var neighbour = pilot.world().getTopology().neighbors(home).get(0);

        long outbound = jump(pilot, neighbour);
        pilot.saveAndReload();
        long inbound = jump(pilot, home);

        assertTrue(outbound <= MAX_JUMP_TICKS && inbound <= MAX_JUMP_TICKS,
                "jumps take about a game hour: outbound=" + outbound + " inbound=" + inbound + " ticks");
        var hub = pilot.campaign().coordinator().runtime().infrastructure().endpoint("hub." + home.value());
        assertTrue(pilot.physical().position().distanceTo(hub.position())
                        <= Stage20LiveArrivalAuthorityIntegration.JUMP_OPERATIONAL_DISTANCE_M * 1.5,
                "arrival is near the destination hub");
    }

    private static long jump(ScenarioPilot pilot, com.spacesim.world.StarSystemId destination) {
        long start = pilot.world().getAuthoritativeWorldTick();
        while (!pilot.world().previewFittedFleetJump(pilot.fleetId()).orElseThrow().allowed()) pilot.tick();
        pilot.action("JUMP", Long.toString(destination.value()), "", 0);
        while (pilot.world().findFleetJump(pilot.fleetId()).isPresent()) pilot.tick();
        assertEquals(destination, pilot.fleet().systemId());
        return pilot.world().getAuthoritativeWorldTick() - start;
    }
}
