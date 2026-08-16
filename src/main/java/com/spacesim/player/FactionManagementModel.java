package com.spacesim.player;

import com.spacesim.world.FactionDiplomacyState;
import com.spacesim.world.FactionEconomicState;
import com.spacesim.world.FactionFiscalPolicyState;
import com.spacesim.world.FactionIdentityResolver;
import com.spacesim.world.FactionStockProductionPolicyState;
import com.spacesim.world.FactionStrategicState;
import com.spacesim.world.FactionTerritoryService;
import com.spacesim.world.FactionTerritoryView;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StrategicGrowthPlanService;
import com.spacesim.world.WorldSimulation;
import com.spacesim.world.WorldState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Stage-17G authoritative read model for the player-faction management/global-map layer.
 *
 * <p>The model projects existing persistent/runtime sources only. It never advances simulation
 * time, mutates policy, transfers money, affiliates assets, changes territory or edits diplomacy.
 * Territory visibility follows player discovery; faction-owned legal state therefore does not
 * become a new omniscient sensor channel.</p>
 */
public final class FactionManagementModel {
    private FactionManagementModel() {
        throw new AssertionError("FactionManagementModel does not create instances");
    }

    /**
     * Captures one deterministic immutable management snapshot.
     *
     * @param runtime authoritative playable runtime
     * @return management projection for an independent or faction-affiliated player
     */
    public static FactionManagementSnapshot capture(PlayerRuntime runtime) {
        PlayerRuntime checked = Objects.requireNonNull(runtime, "PlayerRuntime not set");
        PlayerState player = checked.player();
        WorldSimulation world = checked.world();
        long worldTick = world.getAuthoritativeWorldTick();

        List<FleetPlacementState> ownedFleets = ownedFleets(world, player);
        PlayerConstructionManagementSnapshot construction =
                new PlayerConstructionManagementModel(checked).capture();
        String factionId = player.factionContentId();
        if (factionId == null) {
            return new FactionManagementSnapshot(
                    false,
                    worldTick,
                    player.walletMilliCredits(),
                    null,
                    "",
                    -1,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    ownedFleets,
                    construction.projects(),
                    construction.stations(),
                    List.of(),
                    null,
                    List.of(),
                    List.of());
        }

        WorldState worldState = world.snapshot();
        FactionIdentityResolver identities = FactionIdentityResolver.createDefault(
                checked.content(), worldState.factionIdentities());
        int runtimeFactionId = identities.runtimeId(factionId).orElseThrow(
                () -> new IllegalStateException("Player faction identity is unresolved: " + factionId));
        String displayName = identities.displayName(factionId).orElseThrow(
                () -> new IllegalStateException("Player faction display name is unresolved: " + factionId));
        FactionEconomicState economy = world.findFactionEconomicState(factionId).orElseThrow(
                () -> new IllegalStateException("Player faction economy is missing: " + factionId));
        FactionStrategicState strategy = world.findFactionStrategicState(factionId).orElseThrow(
                () -> new IllegalStateException("Player faction strategy is missing: " + factionId));
        FactionFiscalPolicyState fiscal = world.findFactionFiscalPolicy(factionId).orElseThrow(
                () -> new IllegalStateException("Player faction fiscal policy is missing: " + factionId));
        FactionStockProductionPolicyState stockProduction = world.findFactionStockProductionPolicy(factionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Player faction stock/production policy is missing: " + factionId));
        FactionDiplomacyState diplomacy = world.findFactionDiplomacyState(factionId).orElseThrow(
                () -> new IllegalStateException("Player faction diplomacy is missing: " + factionId));

        return new FactionManagementSnapshot(
                true,
                worldTick,
                player.walletMilliCredits(),
                factionId,
                displayName,
                runtimeFactionId,
                economy,
                strategy.doctrine(),
                fiscal,
                stockProduction,
                world.findFactionResilienceDemandFloors(factionId),
                ownedFleets,
                construction.projects(),
                construction.stations(),
                territories(world, player, factionId),
                diplomacy,
                counterparties(world, identities, worldState, diplomacy, factionId, worldTick),
                StrategicGrowthPlanService.plans(strategy));
    }

    private static List<FleetPlacementState> ownedFleets(WorldSimulation world, PlayerState player) {
        List<FleetPlacementState> result = new ArrayList<>(player.ownedFleetIds().size());
        for (var fleetId : player.ownedFleetIds()) {
            result.add(world.findFleet(fleetId).orElseThrow(
                    () -> new IllegalStateException("Owned fleet is missing from world: " + fleetId)));
        }
        result.sort(Comparator.comparing(FleetPlacementState::fleetId));
        return List.copyOf(result);
    }

    private static List<FactionTerritoryView> territories(
            WorldSimulation world,
            PlayerState player,
            String factionId) {
        List<StarSystemId> systems = new ArrayList<>(player.discoveredSystemIds());
        systems.sort(StarSystemId::compareTo);
        List<FactionTerritoryView> result = new ArrayList<>(systems.size());
        for (StarSystemId systemId : systems) {
            result.add(FactionTerritoryService.assess(world, systemId, factionId));
        }
        return List.copyOf(result);
    }

    private static List<FactionManagementSnapshot.CounterpartyView> counterparties(
            WorldSimulation world,
            FactionIdentityResolver identities,
            WorldState worldState,
            FactionDiplomacyState ownDiplomacy,
            String factionId,
            long worldTick) {
        List<FactionManagementSnapshot.CounterpartyView> result = new ArrayList<>();
        for (FactionDiplomacyState other : worldState.factionDiplomacyStates()) {
            String otherId = other.factionContentId();
            if (factionId.equals(otherId)) {
                continue;
            }
            String displayName = identities.displayName(otherId).orElse(otherId);
            result.add(new FactionManagementSnapshot.CounterpartyView(
                    otherId,
                    displayName,
                    ownDiplomacy.trustTo(otherId),
                    ownDiplomacy.credibilityOf(otherId),
                    ownDiplomacy.hasActiveMarketEmbargoAgainst(otherId, worldTick),
                    other.hasActiveMarketEmbargoAgainst(factionId, worldTick),
                    other.customsTariffBasisPoints(),
                    world.evaluateFactionMarketAccess(otherId, factionId),
                    world.evaluateFactionMarketAccess(factionId, otherId)));
        }
        result.sort(Comparator.naturalOrder());
        return List.copyOf(result);
    }
}
