package com.spacesim.player;

import com.spacesim.world.FactionStrategicState;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.WorldSimulation;

import java.util.Objects;

/**
 * Shared Stage-16 strategic access rule for authoring a player construction project.
 *
 * <p>The policy deliberately reuses existing Stage-8 territorial/diplomatic access data instead of
 * inventing a hidden reputation threshold. Unclaimed systems are open. A controlling faction
 * always permits its own affiliate. Foreign or independent actors are accepted only when the
 * controller's existing directed market-access threshold admits their relation. A later explicit
 * construction-license system can replace this adapter without changing placement/UI callers.</p>
 */
public final class ConstructionAccessPolicy {
    private ConstructionAccessPolicy() {
        throw new AssertionError("ConstructionAccessPolicy does not create instances");
    }

    /**
     * Evaluates strategic construction access for one player/system pair.
     *
     * @param world authoritative world
     * @param player current persistent player state
     * @param systemId target system
     * @return true when current territorial policy permits project authoring
     */
    public static boolean allows(
            WorldSimulation world,
            PlayerState player,
            StarSystemId systemId) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "WorldSimulation not set");
        PlayerState checkedPlayer = Objects.requireNonNull(player, "PlayerState not set");
        StarSystemId target = Objects.requireNonNull(systemId, "Construction system not set");
        String controller = checkedWorld.controllingFaction(target).orElse(null);
        if (controller == null || controller.equals(checkedPlayer.factionContentId())) {
            return true;
        }
        FactionStrategicState controllerState = checkedWorld.findFactionStrategicState(controller).orElse(null);
        if (controllerState == null) {
            return false;
        }
        String playerFaction = checkedPlayer.factionContentId();
        int relation = playerFaction == null ? 0 : controllerState.relationTo(playerFaction);
        return relation >= controllerState.minimumMarketAccessRelation();
    }
}
