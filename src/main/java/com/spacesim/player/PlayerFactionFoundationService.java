package com.spacesim.player;

import com.spacesim.content.ContentCatalog;
import com.spacesim.world.FactionEconomicState;
import com.spacesim.world.FactionStrategicState;
import com.spacesim.world.WorldState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Pure Stage-17 foundation transition that explicitly founds a player faction in persistent state.
 *
 * <p>This first slice deliberately stops before local ECS affiliation. It creates ordinary
 * {@link FactionEconomicState} and {@link FactionStrategicState} records with zero treasury,
 * zero territory and zero fiscal policy, then changes only the player's explicit faction
 * affiliation. Existing fleets, stations, construction projects, personal money and all physical
 * world entities remain byte-for-byte/value-for-value unchanged.</p>
 *
 * <p>The new faction ID is world state rather than immutable content. Runtime materialization of a
 * world-defined faction into dense local {@code FactionComponent} IDs is the following Stage-17
 * identity-bridge slice; this class therefore does not mutate a {@link ContentCatalog} or its
 * semantic fingerprint.</p>
 */
public final class PlayerFactionFoundationService {
    private static final Pattern FACTION_ID = Pattern.compile(
            "faction\\.[a-z0-9]+(?:[._-][a-z0-9]+)*");

    private PlayerFactionFoundationService() {
        throw new AssertionError("PlayerFactionFoundationService does not create instances");
    }

    /**
     * Founds one explicit player-controlled world faction without granting money, assets or land.
     *
     * @param source current playable snapshot with an initialized independent player
     * @param content immutable content catalog used by the world
     * @param factionId requested stable world faction ID, for example {@code faction.star_empire}
     * @return a new current-schema playable snapshot containing the founded faction
     * @throws NullPointerException if an argument is missing
     * @throws IllegalArgumentException if the ID is invalid/collides or the player is affiliated
     * @throws IllegalStateException if the playable snapshot has no initialized player
     */
    public static PlayableWorldState foundFaction(
            PlayableWorldState source,
            ContentCatalog content,
            String factionId) {
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

        String id = normalizeFactionId(factionId);
        WorldState world = checked.worldState();
        if (checkedContent.findFaction(id) != null
                || containsEconomicFaction(world, id)
                || containsStrategicFaction(world, id)) {
            throw new IllegalArgumentException("Faction ID already exists: " + id);
        }

        List<FactionEconomicState> economics = new ArrayList<>(world.factions());
        economics.add(new FactionEconomicState(id, 0L, 0L, 0L));

        List<FactionStrategicState> strategies = new ArrayList<>(world.factionStrategies());
        strategies.add(new FactionStrategicState(id, 0, List.of(), List.of()));

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
                world.fleetJumps());

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

    private static String normalizeFactionId(String factionId) {
        String id = Objects.requireNonNull(factionId, "Faction ID not set").strip();
        if (!FACTION_ID.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "Player faction ID must use stable lower-case faction.* syntax");
        }
        return id;
    }
}
