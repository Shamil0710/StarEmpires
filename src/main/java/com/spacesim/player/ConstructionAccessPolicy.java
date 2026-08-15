package com.spacesim.player;

import com.spacesim.world.StarSystemId;
import com.spacesim.world.TerritorialConstructionAuthorization;
import com.spacesim.world.WorldSimulation;

import java.util.Objects;

/**
 * Player-facing adapter for the shared Stage-17D territorial construction authorization.
 *
 * <p>Market access, reputation and friendly relations do not imply a right to build. The player
 * uses exactly the same territorial authorization as faction AI and direct world construction:
 * unclaimed space is open, domestic controlled territory is open, and foreign controlled territory
 * requires an explicit unexpired concession from the current controller. An unaffiliated player
 * remains politically neutral and may author private construction without creating sovereignty.</p>
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
     * @return true when current territorial law permits ordinary project authoring
     */
    public static boolean allows(
            WorldSimulation world,
            PlayerState player,
            StarSystemId systemId) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "WorldSimulation not set");
        PlayerState checkedPlayer = Objects.requireNonNull(player, "PlayerState not set");
        StarSystemId target = Objects.requireNonNull(systemId, "Construction system not set");
        return TerritorialConstructionAuthorization.evaluate(
                checkedWorld,
                checkedPlayer.factionContentId(),
                target).allowed();
    }
}
