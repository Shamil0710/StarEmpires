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
 * <p>Both directions conserve money exactly. Capitalization moves existing personal money into
 * public faction funds; an explicit reverse transfer moves existing treasury money back to the
 * personal/company wallet. Neither direction touches station wallets, physical entities, ownership,
 * territory or policies. No automatic dividend or money source/sink is introduced. Live runtime
 * adapters must execute the same transitions as ledgered {@code MONEY_TRANSFER} operations.</p>
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
        return transfer(source, amountMilliCredits, true);
    }

    /**
     * Explicitly transfers existing faction-treasury money back to the personal/company wallet.
     *
     * <p>This is an accounting boundary only, not automatic faction income. Stage-17F governance or
     * budget policy may later restrict when gameplay exposes this command without changing the
     * conservation semantics defined here.</p>
     *
     * @param source current playable state
     * @param amountMilliCredits strictly positive amount
     * @return new current-schema state with the exact conserved transfer applied
     * @throws NullPointerException if source is missing
     * @throws IllegalArgumentException if amount is invalid, player is independent, faction economic
     *         state is missing, treasury funds are insufficient or the personal wallet would overflow
     * @throws IllegalStateException if PlayerState is not initialized
     */
    public static PlayableWorldState transferToPersonal(
            PlayableWorldState source,
            long amountMilliCredits) {
        return transfer(source, amountMilliCredits, false);
    }

    private static PlayableWorldState transfer(
            PlayableWorldState source,
            long amountMilliCredits,
            boolean toTreasury) {
        PlayableWorldState checked = Objects.requireNonNull(source, "PlayableWorldState not set");
        if (amountMilliCredits <= 0L) {
            throw new IllegalArgumentException("Faction treasury transfer amount must be positive");
        }
        PlayerState player = checked.playerState();
        if (player == null) {
            throw new IllegalStateException("Cannot transfer faction funds before PlayerState initialization");
        }
        if (!player.affiliated()) {
            throw new IllegalArgumentException("Independent player has no faction treasury");
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

        long resultingPersonalWallet;
        long resultingTreasury;
        if (toTreasury) {
            if (player.walletMilliCredits() < amountMilliCredits) {
                throw new IllegalArgumentException("Personal wallet cannot fund requested faction capitalization");
            }
            resultingPersonalWallet = Math.subtractExact(player.walletMilliCredits(), amountMilliCredits);
            try {
                resultingTreasury = Math.addExact(
                        previousEconomy.treasuryMilliCredits(), amountMilliCredits);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Faction treasury capitalization would overflow", exception);
            }
        } else {
            if (previousEconomy.treasuryMilliCredits() < amountMilliCredits) {
                throw new IllegalArgumentException("Faction treasury cannot fund requested personal transfer");
            }
            resultingTreasury = Math.subtractExact(
                    previousEconomy.treasuryMilliCredits(), amountMilliCredits);
            try {
                resultingPersonalWallet = Math.addExact(player.walletMilliCredits(), amountMilliCredits);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Personal wallet transfer would overflow", exception);
            }
        }

        economics.set(economyIndex, new FactionEconomicState(
                previousEconomy.factionContentId(),
                resultingTreasury,
                previousEconomy.stationLiquidityReserveMilliCredits(),
                previousEconomy.maxLiquiditySupportPerDecisionMilliCredits(),
                previousEconomy.treasuryReserveFloorMilliCredits(),
                previousEconomy.maxConstructionInvestmentPerDecisionMilliCredits()));
        return replaceBalances(checked, economics, resultingPersonalWallet);
    }

    private static PlayableWorldState replaceBalances(
            PlayableWorldState source,
            List<FactionEconomicState> economics,
            long personalWalletMilliCredits) {
        WorldState world = source.worldState();
        PlayerState player = source.playerState();
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
                world.factionIdentities(),
                world.factionDiplomacyStates());
        PlayerState updatedPlayer = new PlayerState(
                personalWalletMilliCredits,
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
