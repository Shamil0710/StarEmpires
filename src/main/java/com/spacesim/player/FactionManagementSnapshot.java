package com.spacesim.player;

import com.spacesim.world.DiplomaticMarketAccessResolver;
import com.spacesim.world.FactionDiplomacyState;
import com.spacesim.world.FactionDoctrineState;
import com.spacesim.world.FactionEconomicState;
import com.spacesim.world.FactionFiscalPolicyState;
import com.spacesim.world.FactionStockPolicyState;
import com.spacesim.world.FactionStockProductionPolicyState;
import com.spacesim.world.FactionTerritoryView;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StrategicGrowthState;

import java.util.List;
import java.util.Objects;

/**
 * Immutable Stage-17G player-facing projection of authoritative faction management state.
 *
 * <p>The snapshot is deliberately presentation-only. It carries persistent economy, policy,
 * diplomacy and expansion values plus derived legal territory/access views, but owns no mutable
 * simulation state and exposes no command callbacks. An independent player receives an explicit
 * unaffiliated snapshot instead of hidden faction authority.</p>
 *
 * @param affiliated whether the player currently has a legal faction affiliation
 * @param worldTick authoritative world tick used for legal/expiry decisions
 * @param personalWalletMilliCredits player's ordinary personal wallet balance
 * @param factionContentId stable faction ID, or {@code null} while independent
 * @param factionDisplayName public faction name, or empty while independent
 * @param runtimeFactionId dense ECS faction slot, or {@code -1} while independent
 * @param economy persistent faction economy, or {@code null} while independent
 * @param doctrine institutional doctrine, or {@code null} while independent
 * @param fiscalPolicy persistent fiscal policy, or {@code null} while independent
 * @param stockProductionPolicy persistent base stock/production policy, or {@code null}
 * @param resilienceDemandFloors automatic resilience demand overlay
 * @param ownedFleets player-owned physical fleet placements in stable ID order
 * @param constructionProjects player-owned physical construction project views
 * @param ownedStations player-owned completed physical station views
 * @param territories known-system territorial views in system ID order
 * @param diplomacy player's faction diplomacy aggregate, or {@code null} while independent
 * @param counterparties visible legal/diplomatic counterpart summaries
 * @param expansionPlans persistent faction growth plans in stable plan order
 */
public record FactionManagementSnapshot(
        boolean affiliated,
        long worldTick,
        long personalWalletMilliCredits,
        String factionContentId,
        String factionDisplayName,
        int runtimeFactionId,
        FactionEconomicState economy,
        FactionDoctrineState doctrine,
        FactionFiscalPolicyState fiscalPolicy,
        FactionStockProductionPolicyState stockProductionPolicy,
        List<FactionStockPolicyState> resilienceDemandFloors,
        List<FleetPlacementState> ownedFleets,
        List<PlayerConstructionProjectView> constructionProjects,
        List<PlayerOwnedStationView> ownedStations,
        List<FactionTerritoryView> territories,
        FactionDiplomacyState diplomacy,
        List<CounterpartyView> counterparties,
        List<StrategicGrowthState.Plan> expansionPlans) {

    /**
     * Canonicalizes immutable collections and validates independent/faction-bound shapes.
     *
     * @param affiliated whether player has faction authority
     * @param worldTick authoritative world tick
     * @param personalWalletMilliCredits non-negative personal balance
     * @param factionContentId faction ID or null
     * @param factionDisplayName display name or empty
     * @param runtimeFactionId runtime slot or -1
     * @param economy faction economy or null
     * @param doctrine doctrine or null
     * @param fiscalPolicy fiscal policy or null
     * @param stockProductionPolicy stock/production policy or null
     * @param resilienceDemandFloors resilience overlay
     * @param ownedFleets owned fleet placements
     * @param constructionProjects owned construction projects
     * @param ownedStations owned completed stations
     * @param territories known territory views
     * @param diplomacy faction diplomacy or null
     * @param counterparties counterpart summaries
     * @param expansionPlans persistent expansion plans
     */
    public FactionManagementSnapshot {
        if (worldTick < 0L || personalWalletMilliCredits < 0L) {
            throw new IllegalArgumentException("Management tick/wallet cannot be negative");
        }
        factionDisplayName = Objects.requireNonNull(factionDisplayName, "Faction display name not set");
        resilienceDemandFloors = List.copyOf(Objects.requireNonNull(
                resilienceDemandFloors, "Resilience demand floors not set"));
        ownedFleets = List.copyOf(Objects.requireNonNull(ownedFleets, "Owned fleets not set"));
        constructionProjects = List.copyOf(Objects.requireNonNull(
                constructionProjects, "Construction projects not set"));
        ownedStations = List.copyOf(Objects.requireNonNull(ownedStations, "Owned stations not set"));
        territories = List.copyOf(Objects.requireNonNull(territories, "Territory views not set"));
        counterparties = List.copyOf(Objects.requireNonNull(counterparties, "Counterparties not set"));
        expansionPlans = List.copyOf(Objects.requireNonNull(expansionPlans, "Expansion plans not set"));

        if (!affiliated) {
            if (factionContentId != null || runtimeFactionId != -1 || !factionDisplayName.isEmpty()
                    || economy != null || doctrine != null || fiscalPolicy != null
                    || stockProductionPolicy != null || diplomacy != null
                    || !resilienceDemandFloors.isEmpty() || !territories.isEmpty()
                    || !counterparties.isEmpty() || !expansionPlans.isEmpty()) {
                throw new IllegalArgumentException("Independent management snapshot cannot expose faction authority");
            }
        } else {
            factionContentId = Objects.requireNonNull(factionContentId, "Faction content ID not set").strip();
            if (factionContentId.isEmpty() || factionDisplayName.isBlank() || runtimeFactionId < 0) {
                throw new IllegalArgumentException("Affiliated management snapshot requires resolved faction identity");
            }
            Objects.requireNonNull(economy, "Faction economy not set");
            Objects.requireNonNull(doctrine, "Faction doctrine not set");
            Objects.requireNonNull(fiscalPolicy, "Faction fiscal policy not set");
            Objects.requireNonNull(stockProductionPolicy, "Faction stock/production policy not set");
            Objects.requireNonNull(diplomacy, "Faction diplomacy not set");
        }
    }

    /**
     * Read-only legal/diplomatic summary for one known faction counterparty.
     *
     * @param factionContentId stable counterparty ID
     * @param displayName public counterparty name
     * @param trust player's faction directed trust value
     * @param credibility player's faction assessment of counterparty credibility
     * @param outboundMarketEmbargo whether player's faction currently embargoes this counterparty
     * @param inboundMarketEmbargo whether counterparty currently embargoes player's faction
     * @param counterpartyCustomsTariffBasisPoints counterparty ordinary customs tariff
     * @param accessToCounterpartyMarkets effective legal access for player faction to counterparty markets
     * @param counterpartyAccessToOurMarkets effective legal access for counterparty to player-faction markets
     */
    public record CounterpartyView(
            String factionContentId,
            String displayName,
            int trust,
            int credibility,
            boolean outboundMarketEmbargo,
            boolean inboundMarketEmbargo,
            int counterpartyCustomsTariffBasisPoints,
            DiplomaticMarketAccessResolver.Decision accessToCounterpartyMarkets,
            DiplomaticMarketAccessResolver.Decision counterpartyAccessToOurMarkets)
            implements Comparable<CounterpartyView> {
        /**
         * Validates one deterministic counterparty row.
         *
         * @param factionContentId stable counterparty ID
         * @param displayName public counterparty name
         * @param trust directed trust
         * @param credibility directed credibility assessment
         * @param outboundMarketEmbargo current outbound embargo flag
         * @param inboundMarketEmbargo current inbound embargo flag
         * @param counterpartyCustomsTariffBasisPoints ordinary counterparty tariff
         * @param accessToCounterpartyMarkets effective player-to-counterparty access decision
         * @param counterpartyAccessToOurMarkets effective counterparty-to-player access decision
         */
        public CounterpartyView {
            factionContentId = Objects.requireNonNull(factionContentId, "Counterparty ID not set").strip();
            displayName = Objects.requireNonNull(displayName, "Counterparty display name not set").strip();
            if (factionContentId.isEmpty() || displayName.isEmpty()) {
                throw new IllegalArgumentException("Counterparty identity cannot be blank");
            }
            if (trust < -100 || trust > 100 || credibility < 0 || credibility > 100) {
                throw new IllegalArgumentException("Counterparty standing is outside supported bounds");
            }
            if (counterpartyCustomsTariffBasisPoints < 0 || counterpartyCustomsTariffBasisPoints > 10_000) {
                throw new IllegalArgumentException("Counterparty tariff must be in 0..10000 bps");
            }
            Objects.requireNonNull(accessToCounterpartyMarkets, "Outbound access decision not set");
            Objects.requireNonNull(counterpartyAccessToOurMarkets, "Inbound access decision not set");
        }

        @Override
        public int compareTo(CounterpartyView other) {
            return factionContentId.compareTo(Objects.requireNonNull(other, "Counterparty not set").factionContentId);
        }
    }
}
