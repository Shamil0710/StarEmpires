package com.spacesim.player;

import com.spacesim.content.ContentCatalog;
import com.spacesim.world.FactionDiplomacyState;
import com.spacesim.world.FactionEconomicState;
import com.spacesim.world.FactionIdentityResolver;
import com.spacesim.world.FactionStrategicState;
import com.spacesim.world.WorldFactionIdentityState;
import com.spacesim.world.WorldState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure Stage-17 transition that explicitly founds a player faction in persistent state.
 *
 * <p>Founding creates ordinary {@link FactionEconomicState}, {@link FactionStrategicState},
 * {@link FactionDiplomacyState} and {@link WorldFactionIdentityState} records, reserves the lowest
 * free bounded runtime faction slot, then changes only the player's explicit faction affiliation.
 * Treasury, territory and fiscal policy start at zero. Existing fleets, stations, construction
 * projects, personal money, diplomatic history and all physical world entities remain value-for-value
 * unchanged.</p>
 *
 * <p>The new faction ID and display metadata are world state rather than immutable content. Runtime
 * materialization uses {@link FactionIdentityResolver}; this class never mutates the
 * {@link ContentCatalog} or its semantic fingerprint.</p>
 */
public final class PlayerFactionFoundationService {
    private PlayerFactionFoundationService() {
        throw new AssertionError("PlayerFactionFoundationService does not create instances");
    }

    /**
     * Source-compatible founding path using the stable ID as the initial public display name.
     *
     * <p>Gameplay/UI code that has an explicit faction name should use
     * {@link #foundFaction(PlayableWorldState, ContentCatalog, String, String)}.</p>
     *
     * @param source current playable snapshot with an initialized independent player
     * @param content immutable content catalog used by the world
     * @param factionId requested stable world faction ID, for example {@code faction.star_empire}
     * @return a new current-schema playable snapshot containing the founded faction
     */
    public static PlayableWorldState foundFaction(
            PlayableWorldState source,
            ContentCatalog content,
            String factionId) {
        String id = WorldFactionIdentityState.normalizeStableId(factionId);
        return foundFaction(source, content, id, id);
    }

    /**
     * Founds one explicit player-controlled world faction without granting money, assets or land.
     *
     * @param source current playable snapshot with an initialized independent player
     * @param content immutable content catalog used by the world
     * @param factionId requested stable world faction ID
     * @param displayName public non-empty faction name stored in world identity metadata
     * @return a new current-schema playable snapshot containing the founded faction
     * @throws NullPointerException if an argument is missing
     * @throws IllegalArgumentException if the ID/name is invalid, collides or player is affiliated
     * @throws IllegalStateException if the playable snapshot has no initialized player
     */
    public static PlayableWorldState foundFaction(
            PlayableWorldState source,
            ContentCatalog content,
            String factionId,
            String displayName) {
        PlayableWorldState checked = Objects.requireNonNull(source, "PlayableWorldState not set");
        ContentCatalog checkedContent = Objects.requireNonNull(content, "ContentCatalog not set");
        PlayerState player = checked.playerState();
        if (player == null) {
            throw new IllegalStateException("Cannot found a faction before PlayerState initialization");
        }
        if (player.affiliated()) {
            throw new IllegalArgumentException("Player is already affiliated with a faction: "
                    + player.factionContentId());
        }

        String id = WorldFactionIdentityState.normalizeStableId(factionId);
        WorldState world = checked.worldState();
        if (containsEconomicFaction(world, id) || containsStrategicFaction(world, id)) {
            throw new IllegalArgumentException("Faction ID already exists: " + id);
        }

        FactionIdentityResolver resolver = FactionIdentityResolver.createDefault(
                checkedContent,
                world.factionIdentities());
        WorldFactionIdentityState identity = resolver.allocatePlayerCreated(id, displayName);

        List<FactionEconomicState> economics = new ArrayList<>(world.factions());
        economics.add(new FactionEconomicState(id, 0L, 0L, 0L, 0L, 0L));

        List<FactionStrategicState> strategies = new ArrayList<>(world.factionStrategies());
        strategies.add(new FactionStrategicState(id, 0, List.of(), List.of()));

        List<WorldFactionIdentityState> identities = new ArrayList<>(world.factionIdentities());
        identities.add(identity);

        List<FactionDiplomacyState> diplomacy = new ArrayList<>(world.factionDiplomacyStates());
        diplomacy.add(FactionDiplomacyState.neutral(id));

        WorldState updatedWorld = new WorldState(
                WorldState.CURRENT_VERSION,
                world.topology(),
                world.systems(),
                economics,
                strategies,
                world.nextConstructionProjectIdValue(),
                world.constructionProjects(),
                world.factionEconomicPressures(),
                world.nextFleetIdValue(),
                world.fleets(),
                world.fleetJumps(),
                identities,
                diplomacy);

        PlayerState updatedPlayer = new PlayerState(
                player.walletMilliCredits(),
                id,
                player.reputations(),
                player.ownedFleetIds(),
                player.activeFleetId(),
                player.discoveredSystemIds(),
                player.discoveredObjects(),
                player.homeSystemId(),
                player.dockedAt(),
                player.fleetOrders(),
                player.threatIntel(),
                player.ownedConstructionProjectIds(),
                player.ownedStations());

        return new PlayableWorldState(
                PlayableWorldState.CURRENT_VERSION,
                updatedWorld,
                updatedPlayer);
    }

    private static boolean containsEconomicFaction(WorldState world, String factionId) {
        return world.factions().stream()
                .anyMatch(state -> state.factionContentId().equals(factionId));
    }

    private static boolean containsStrategicFaction(WorldState world, String factionId) {
        return world.factionStrategies().stream()
                .anyMatch(state -> state.factionContentId().equals(factionId));
    }
}
