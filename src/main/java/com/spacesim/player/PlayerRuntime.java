package com.spacesim.player;

import com.spacesim.content.ContentCatalog;
import com.spacesim.world.FleetId;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.WorldSimulation;

import java.util.Objects;

/**
 * Runtime owner of the Stage-12 playable layer above an otherwise independent WorldSimulation.
 *
 * <p>The wrapper deliberately does not move player data into the simulation core. It validates
 * persistent references against the current world, forwards time advancement unchanged and
 * snapshots world + player atomically through {@link PlayableWorldState}.</p>
 */
public final class PlayerRuntime {
    private final WorldSimulation world;
    private final ContentCatalog content;
    private PlayerState player;

    private PlayerRuntime(WorldSimulation world, ContentCatalog content, PlayerState player) {
        this.world = Objects.requireNonNull(world, "WorldSimulation not set");
        this.content = Objects.requireNonNull(content, "ContentCatalog not set");
        this.player = Objects.requireNonNull(player, "PlayerState not set");
        validateReferences(this.world, this.content, this.player);
    }

    /**
     * Initializes a playable layer around an existing world runtime.
     *
     * @param world current authoritative world
     * @param content semantic content catalog used by that world
     * @param player initial persistent player state
     * @return playable runtime using the same world instance
     */
    public static PlayerRuntime create(
            WorldSimulation world,
            ContentCatalog content,
            PlayerState player) {
        return new PlayerRuntime(world, content, player);
    }

    /**
     * Restores a playable runtime from one atomic save snapshot.
     *
     * @param state playable save state with an initialized player
     * @param content semantic content catalog
     * @param activeSystemId StarSystem to run at full local rate after restore
     * @return restored playable runtime
     * @throws IllegalStateException if the save is a migrated pre-player world
     */
    public static PlayerRuntime restore(
            PlayableWorldState state,
            ContentCatalog content,
            StarSystemId activeSystemId) {
        PlayableWorldState checked = Objects.requireNonNull(state, "PlayableWorldState not set");
        if (checked.playerState() == null) {
            throw new IllegalStateException("Legacy world has no initialized PlayerState");
        }
        WorldSimulation restoredWorld = WorldSimulation.restore(
                checked.worldState(),
                Objects.requireNonNull(content, "ContentCatalog not set"),
                Objects.requireNonNull(activeSystemId, "Active StarSystemId not set"),
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        return new PlayerRuntime(restoredWorld, content, checked.playerState());
    }

    /** @return current authoritative world runtime */
    public WorldSimulation world() {
        return world;
    }

    /** @return current immutable player state */
    public PlayerState player() {
        return player;
    }

    /**
     * Advances the unchanged fixed-tick world pipeline.
     *
     * @param realDeltaSeconds render/frame delta
     * @return ordinary WorldSimulation advance report
     */
    public WorldSimulation.AdvanceReport advanceFrame(float realDeltaSeconds) {
        return world.advanceFrame(realDeltaSeconds);
    }

    /** @return atomic playable snapshot of current world and player state */
    public PlayableWorldState snapshot() {
        return new PlayableWorldState(
                PlayableWorldState.CURRENT_VERSION,
                world.snapshot(),
                player);
    }

    void replacePlayerState(PlayerState replacement) {
        PlayerState checked = Objects.requireNonNull(replacement, "Replacement PlayerState not set");
        validateReferences(world, content, checked);
        player = checked;
    }

    private static void validateReferences(
            WorldSimulation world,
            ContentCatalog content,
            PlayerState player) {
        if (player.factionContentId() != null && content.findFaction(player.factionContentId()) == null) {
            throw new IllegalArgumentException("Player affiliation references unknown faction: "
                    + player.factionContentId());
        }
        for (PlayerReputationState reputation : player.reputations()) {
            if (content.findFaction(reputation.factionContentId()) == null) {
                throw new IllegalArgumentException("Player reputation references unknown faction: "
                        + reputation.factionContentId());
            }
        }
        for (FleetId fleetId : player.ownedFleetIds()) {
            if (world.findFleet(fleetId).isEmpty()) {
                throw new IllegalArgumentException("Player owns unknown FleetId: " + fleetId);
            }
        }
        for (StarSystemId systemId : player.discoveredSystemIds()) {
            if (world.getTopology().findSystem(systemId).isEmpty()) {
                throw new IllegalArgumentException("Player discovered unknown StarSystem: " + systemId);
            }
        }
        for (DiscoveredObjectRef reference : player.discoveredObjects()) {
            if (world.getTopology().findSystem(reference.systemId()).isEmpty()) {
                throw new IllegalArgumentException("Player discovery references unknown StarSystem: "
                        + reference.systemId());
            }
        }
        if (player.homeSystemId() != null
                && world.getTopology().findSystem(player.homeSystemId()).isEmpty()) {
            throw new IllegalArgumentException("Player home references unknown StarSystem: "
                    + player.homeSystemId());
        }
    }
}
