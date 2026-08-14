package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionMarketAccessComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic Stage-16 supplier planner for player-owned construction logistics.
 *
 * <p>Only markets already present in {@link PlayerState#discoveredObjects()} are candidates. The
 * planner may read their current public market quote and physical stock but never discovers a new
 * remote entity. Candidate systems use the same cumulative {@link PlayerFleetRoutePlanner} as
 * Stage-15 movement, so danger in any traversed system/link can change supplier choice.</p>
 *
 * <p>Construction supply is a fulfillment mission rather than speculative trade. Candidate ordering
 * therefore minimizes travel+risk cost first, then raw purchase price, then the stable qualified
 * market reference. Stock is never reserved; execution must re-plan from fresh state if another
 * actor consumes it first.</p>
 */
public final class PlayerSupplyProjectPlanner {
    private static final double COST_EPSILON = 1e-9d;

    private final PlayerRuntime runtime;
    private final ContentCatalog content;
    private final PlayerFleetRoutePlanner routePlanner;

    /**
     * Creates a supplier planner for one playable runtime.
     *
     * @param runtime current authoritative player/world runtime
     */
    public PlayerSupplyProjectPlanner(PlayerRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "PlayerRuntime not set");
        this.content = runtime.content();
        this.routePlanner = new PlayerFleetRoutePlanner(runtime);
    }

    /**
     * Selects the best currently executable known supplier for one required material.
     *
     * @param fleetId player-owned physical cargo fleet
     * @param constructionSite persistent owned construction-site reference
     * @param itemContentId stable required material content ID
     * @return deterministic fresh supplier plan or empty when no known executable source exists
     */
    public Optional<PlayerSupplyProjectPlan> plan(
            FleetId fleetId,
            DiscoveredObjectRef constructionSite,
            String itemContentId) {
        FleetId actor = Objects.requireNonNull(fleetId, "Supply FleetId not set");
        DiscoveredObjectRef site = Objects.requireNonNull(constructionSite, "Construction site not set");
        ContentCatalog.ItemDefinition item = content.findItem(normalizeItem(itemContentId));
        PlayerState player = runtime.player();
        if (item == null || !player.ownedFleetIds().contains(actor)) {
            return Optional.empty();
        }
        FleetPlacementState placement = runtime.world().findFleet(actor).orElse(null);
        if (placement == null || placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
            return Optional.empty();
        }
        Entity ship = runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().find(placement.localEntityId());
        ShipComponent shipRole = ship == null ? null : ship.getComponent(ShipComponent.class);
        if (shipRole == null || !shipRole.canPurchaseItem(item.runtimeId())) {
            return Optional.empty();
        }
        int participantFaction = playerFactionRuntimeId(player);
        List<DiscoveredObjectRef> known = new ArrayList<>(player.discoveredObjects());
        known.sort(Comparator.naturalOrder());

        PlayerSupplyProjectPlan best = null;
        for (DiscoveredObjectRef candidateRef : known) {
            if (candidateRef.equals(site)
                    || !player.discoveredSystemIds().contains(candidateRef.systemId())) {
                continue;
            }
            Entity marketEntity = runtime.world().findSession(candidateRef.systemId()).orElseThrow()
                    .getEntityRegistry().find(candidateRef.entityId());
            MarketComponent market = marketEntity == null ? null : marketEntity.getComponent(MarketComponent.class);
            InventoryComponent inventory = marketEntity == null
                    ? null : marketEntity.getComponent(InventoryComponent.class);
            WalletComponent wallet = marketEntity == null ? null : marketEntity.getComponent(WalletComponent.class);
            if (market == null || inventory == null || wallet == null
                    || !market.isTradable(item.runtimeId())
                    || inventory.stock[item.runtimeId()] <= 0
                    || !positiveFinite(market.sellPrices[item.runtimeId()])
                    || !hasAccess(marketEntity, participantFaction)) {
                continue;
            }
            PlayerRouteRiskView route = routePlanner.plan(actor, placement.systemId(), candidateRef.systemId())
                    .orElse(null);
            if (route == null) {
                continue;
            }
            PlayerSupplyProjectPlan candidate = new PlayerSupplyProjectPlan(
                    candidateRef,
                    route,
                    market.sellPrices[item.runtimeId()],
                    inventory.stock[item.runtimeId()]);
            if (better(candidate, best)) {
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    private int playerFactionRuntimeId(PlayerState player) {
        if (player.factionContentId() == null) {
            return -1;
        }
        ContentCatalog.FactionDefinition faction = content.findFaction(player.factionContentId());
        return faction == null ? Integer.MIN_VALUE : faction.runtimeId();
    }

    private static boolean hasAccess(Entity marketEntity, int participantFaction) {
        if (participantFaction == Integer.MIN_VALUE) {
            return false;
        }
        FactionMarketAccessComponent access = marketEntity.getComponent(FactionMarketAccessComponent.class);
        return access == null || access.canTrade(participantFaction);
    }

    private static boolean better(PlayerSupplyProjectPlan candidate, PlayerSupplyProjectPlan current) {
        if (current == null) {
            return true;
        }
        double costDifference = candidate.route().totalCost() - current.route().totalCost();
        if (Math.abs(costDifference) > COST_EPSILON) {
            return costDifference < 0d;
        }
        int price = Float.compare(candidate.rawSellPriceCredits(), current.rawSellPriceCredits());
        if (price != 0) {
            return price < 0;
        }
        return candidate.supplier().compareTo(current.supplier()) < 0;
    }

    private static String normalizeItem(String value) {
        return value == null ? "" : value.strip();
    }

    private static boolean positiveFinite(float value) {
        return Float.isFinite(value) && value > 0f;
    }
}
