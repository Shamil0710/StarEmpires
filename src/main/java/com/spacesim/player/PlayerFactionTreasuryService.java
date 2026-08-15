package com.spacesim.player;

import com.spacesim.world.FactionEconomicState;
import com.spacesim.world.WorldState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure Stage-17C persistent transition between the player's personal/company wallet and the
 * ordinary Stage-8 faction treasury.
 *
 * <p>This first slice deliberately proves the money boundary before live runtime wiring. A
 * capitalization subtracts exactly the same amount from {@link PlayerState#walletMilliCredits()}
 * that it adds to the existing {@link FactionEconomicState} treasury. It does not touch station
 * wallets, physical entities, ownership, territory or policies and never creates starting capital.
 * The later live bridge must execute the same transition as a ledgered {@code MONEY_TRANSFER}.</p>
 */
public final class PlayerFactionTreasuryService {
    private PlayerFactionTreasuryService() {
        throw new AssertionError("PlayerFactionTreasuryService does not create instances");
    }

    /**
     * Returns the personal/treasury balances represented by one persistent playable snapshot.
     *
     * @param source playable state
     * @return finance view, or empty while the player is independent or has no economic faction state
     */
    public static Optional<PlayerFactionTreasuryView> view(PlayableWorldState source) {
        PlayableWorldState checked = Objects.requireNonNull(source, "PlayableWorldState not set");
        PlayerState player = checked.playerState();
        if (player == null || !player.affiliated()) {
            return Optional.empty();
        }
        FactionEconomicState economy = findFactionEconomy(checked.worldState(), player.factionContentId());
        if (economy == null) {
            return Optional.empty();
        }
        return Optional.of(new PlayerFactionTreasuryView(
                player.factionContentId(),
                player.walletMilliCredits(),
                economy.treasuryMilliCredits()));
    }

    /**
     * Capitalizes the affiliated faction by moving existing personal/company money into its treasury.
     *
     * @param source current playable state
     * @param amountMilliCredits strictly positive amount
     * @return new current-schema state with the exact conserved transfer applied
     * @throws NullPointerException if source is missing
     * @throws IllegalArgumentException if amount is invalid, player is independent, faction economic
     *         state is missing, personal funds are insufficient or the treasury would overflow
     * @throws IllegalStateException if PlayerState is not initialized
     */
    public static PlayableWorldState capitalize(PlayableWorldState source, long amountMilliCredits) {
        PlayableWorldState checked = Objects.requireNonNull(source, "PlayableWorldState not set");
        if (amountMilliCredits <= 0L) {
            throw new IllegalArgumentException("Faction capitalization amount must be positive");
        }
        PlayerState player = checked.playerState();
        if (player == null) {
            throw new IllegalStateException("Cannot capitalize faction before PlayerState initialization");
        }
        if (!player.affiliated()) {
            throw new IllegalArgumentException("Independent player has no faction treasury");
        }
        if (player.walletMilliCredits() < amountMilliCredits) {
            throw new IllegalArgumentException("Personal wallet cannot fund requested faction capitalization");
        }

        WorldState world = checked.worldState();
        List<FactionEconomicState> economics = new ArrayList<>(world.factions());
        int economyIndex = -1;
        FactionEconomicState previousEconomy = null;
        for (int i = 0; i < economics.size(); i++) {
            FactionEconomicState candidate = economics.get(i);
            if (candidate.factionContentId().equals(player.factionContentId())) {
                economyIndex = i;
                previousEconomy = candidate;
                break;
            }
        }
        if (previousEconomy == null) {
            throw new IllegalArgumentException(
                    "Player faction has no economic state: " + player.factionContentId());
        }

        final long resultingTreasury;
        try {
            resultingTreasury = Math.addExact(previousEconomy.treasuryMilliCredits(), amountMilliCredits);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Faction treasury capitalization would overflow", exception);
        }
        long resultingPersonalWallet = Math.subtractExact(player.walletMilliCredits(), amountMilliCredits);
        economics.set(economyIndex, new FactionEconomicState(
                previousEconomy.factionContentId(),
                resultingTreasury,
                previousEconomy.stationLiquidityReserveMilliCredits(),
                previousEconomy.maxLiquiditySupportPerDecisionMilliCredits()));

        WorldState updatedWorld = new WorldState(
                WorldState.CURRENT_VERSION,
                world.topology(),
                world.systems(),
                economics,
                world.factionStrategies(),
                world.nextConstructionProjectIdValue(),
                world.constructionProjects(),
                world.factionEconomicPressures(),
                world.nextFleetIdValue(),
                world.fleets(),
                world.fleetJumps(),
                world.factionIdentities());
        PlayerState updatedPlayer = new PlayerState(
                resultingPersonalWallet,
                player.factionContentId(),
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

    private static FactionEconomicState findFactionEconomy(WorldState world, String factionId) {
        for (FactionEconomicState economy : world.factions()) {
            if (economy.factionContentId().equals(factionId)) {
                return economy;
            }
        }
        return null;
    }
}
