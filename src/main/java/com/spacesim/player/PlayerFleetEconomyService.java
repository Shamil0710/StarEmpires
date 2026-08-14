package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.ReputationComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.controllers.TradeController;
import com.spacesim.economy.Money;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Physical Stage-15 trade boundary for a delegated player-owned FleetId.
 *
 * <p>The service mirrors {@link PlayerMarketService} without requiring the fleet to be the directly
 * controlled active ship. A transaction is legal only when the same persistent physical fleet is
 * materialized in the market system, has physically reached berth range and has almost stopped.
 * The ordinary {@link TradeController} remains the sole owner of cargo/market/wallet transfer
 * validation. PlayerState supplies the shared company wallet and reputation; the real ship
 * {@link InventoryComponent} is attached to a short-lived transaction proxy, so no virtual cargo
 * or passive income is introduced.</p>
 */
public final class PlayerFleetEconomyService {
    private static final float DEFAULT_BERTH_RANGE = 10f;
    private static final float MAX_BERTH_SPEED = 0.25f;

    private final PlayerRuntime runtime;
    private final ContentCatalog content;

    /**
     * Creates the delegated physical trade adapter.
     *
     * @param runtime current playable runtime
     */
    public PlayerFleetEconomyService(PlayerRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "PlayerRuntime not set");
        this.content = runtime.content();
    }

    /**
     * Buys the maximum currently legal quantity of one item for an owned fleet.
     *
     * @param fleetId physical player-owned fleet
     * @param stationRef discovered physical source market
     * @param itemContentId stable content item ID
     * @return whole units transferred, or zero when current physical/economic constraints reject it
     */
    public int buyMaximum(FleetId fleetId, DiscoveredObjectRef stationRef, String itemContentId) {
        Context context = context(fleetId, stationRef, itemContentId);
        if (context == null || !context.ship().canPurchaseItem(context.item().runtimeId())) {
            return 0;
        }
        int itemId = context.item().runtimeId();
        float price = context.controller().getEffectiveSellPrice(
                context.station(), itemId, context.proxyReputation());
        int amount = Math.min(context.stationInventory().stock[itemId], context.shipInventory().getFreeCapacity());
        amount = Math.min(amount, affordable(context.proxyWallet().getBalanceMilliCredits(), price, amount));
        if (amount <= 0 || !context.controller().buyFromStation(
                context.station(), context.proxy(), itemId, amount, context.proxyReputation())) {
            return 0;
        }
        persistFinancialState(context);
        return amount;
    }

    /**
     * Sells the maximum currently legal quantity of one physical cargo item.
     *
     * @param fleetId physical player-owned fleet
     * @param stationRef discovered physical destination market
     * @param itemContentId stable content item ID
     * @return whole units transferred, or zero when current physical/economic constraints reject it
     */
    public int sellMaximum(FleetId fleetId, DiscoveredObjectRef stationRef, String itemContentId) {
        Context context = context(fleetId, stationRef, itemContentId);
        if (context == null) {
            return 0;
        }
        int itemId = context.item().runtimeId();
        int amount = Math.min(context.shipInventory().stock[itemId], context.stationInventory().getFreeCapacity());
        float price = context.controller().getEffectiveBuyPrice(
                context.station(), itemId, context.proxyReputation());
        amount = Math.min(amount, affordable(context.stationWallet().getBalanceMilliCredits(), price, amount));
        amount = Math.min(amount, affordable(
                Long.MAX_VALUE - context.proxyWallet().getBalanceMilliCredits(), price, amount));
        if (amount <= 0 || !context.controller().sellToStation(
                context.station(), context.proxy(), itemId, amount, context.proxyReputation())) {
            return 0;
        }
        persistFinancialState(context);
        return amount;
    }

    /**
     * Checks the physical berth boundary without executing a transaction.
     *
     * @param fleetId physical player-owned fleet
     * @param stationRef target market
     * @return true when the fleet is in the same system, inside berth range and nearly stopped
     */
    public boolean isBerthed(FleetId fleetId, DiscoveredObjectRef stationRef) {
        PhysicalContext context = physicalContext(fleetId, stationRef);
        return context != null && insideBerth(context);
    }

    private Context context(FleetId fleetId, DiscoveredObjectRef stationRef, String itemContentId) {
        String normalizedItem = itemContentId == null ? "" : itemContentId.strip();
        ContentCatalog.ItemDefinition item = content.findItem(normalizedItem);
        PhysicalContext physical = physicalContext(fleetId, stationRef);
        if (item == null || physical == null || !insideBerth(physical)) {
            return null;
        }
        PlayerState player = runtime.player();
        Entity proxy = new Entity();
        proxy.add(physical.shipInventory());
        WalletComponent proxyWallet = new WalletComponent(player.walletMilliCredits());
        proxy.add(proxyWallet);
        proxy.add(new IdentityComponent("Player fleet " + fleetId.value(), IdentityComponent.Kind.FLEET));
        if (player.factionContentId() != null) {
            ContentCatalog.FactionDefinition faction = content.findFaction(player.factionContentId());
            if (faction == null) {
                return null;
            }
            proxy.add(new FactionComponent(faction.runtimeId()));
        }
        ReputationComponent proxyReputation = new ReputationComponent();
        for (PlayerReputationState reputation : player.reputations()) {
            ContentCatalog.FactionDefinition faction = content.findFaction(reputation.factionContentId());
            if (faction == null) {
                return null;
            }
            proxyReputation.addReputation(faction.runtimeId(), reputation.value());
        }
        proxy.add(proxyReputation);
        return new Context(
                player,
                physical.shipRole(),
                physical.station(),
                physical.shipInventory(),
                physical.stationInventory(),
                physical.stationWallet(),
                item,
                new TradeController(physical.session().getLedger()),
                proxy,
                proxyWallet,
                proxyReputation);
    }

    private PhysicalContext physicalContext(FleetId fleetId, DiscoveredObjectRef stationRef) {
        if (fleetId == null || stationRef == null) {
            return null;
        }
        PlayerState player = runtime.player();
        if (!player.ownedFleetIds().contains(fleetId) || !player.discoveredObjects().contains(stationRef)) {
            return null;
        }
        FleetPlacementState placement = runtime.world().findFleet(fleetId).orElse(null);
        if (placement == null
                || placement.locationKind() != FleetLocationKind.IN_SYSTEM
                || !placement.systemId().equals(stationRef.systemId())) {
            return null;
        }
        SimulationSession session = runtime.world().findSession(placement.systemId()).orElse(null);
        if (session == null) {
            return null;
        }
        Entity ship = session.getEntityRegistry().find(placement.localEntityId());
        Entity station = session.getEntityRegistry().find(stationRef.entityId());
        TransformComponent shipTransform = ship == null ? null : ship.getComponent(TransformComponent.class);
        TransformComponent stationTransform = station == null ? null : station.getComponent(TransformComponent.class);
        ShipComponent shipRole = ship == null ? null : ship.getComponent(ShipComponent.class);
        InventoryComponent shipInventory = ship == null ? null : ship.getComponent(InventoryComponent.class);
        InventoryComponent stationInventory = station == null ? null : station.getComponent(InventoryComponent.class);
        MarketComponent market = station == null ? null : station.getComponent(MarketComponent.class);
        WalletComponent stationWallet = station == null ? null : station.getComponent(WalletComponent.class);
        if (shipTransform == null || stationTransform == null || shipRole == null || shipInventory == null
                || stationInventory == null || market == null || stationWallet == null) {
            return null;
        }
        MiningComponent mining = ship.getComponent(MiningComponent.class);
        float berthRange = mining != null && Float.isFinite(mining.dockingRange) && mining.dockingRange > 0f
                ? mining.dockingRange : DEFAULT_BERTH_RANGE;
        return new PhysicalContext(
                session, ship, station, shipTransform, stationTransform, shipRole,
                shipInventory, stationInventory, stationWallet, berthRange);
    }

    private static boolean insideBerth(PhysicalContext context) {
        return context.shipTransform().velocity.len2() <= MAX_BERTH_SPEED * MAX_BERTH_SPEED
                && context.shipTransform().position.dst2(context.stationTransform().position)
                <= context.berthRange() * context.berthRange();
    }

    private void persistFinancialState(Context context) {
        PlayerState previous = context.player();
        runtime.replacePlayerState(new PlayerState(
                context.proxyWallet().getBalanceMilliCredits(),
                previous.factionContentId(),
                snapshotReputation(context.proxyReputation()),
                previous.ownedFleetIds(),
                previous.activeFleetId(),
                previous.discoveredSystemIds(),
                previous.discoveredObjects(),
                previous.homeSystemId(),
                previous.dockedAt(),
                previous.fleetOrders(),
                previous.threatIntel()));
    }

    private List<PlayerReputationState> snapshotReputation(ReputationComponent reputation) {
        List<PlayerReputationState> result = new ArrayList<>();
        for (ContentCatalog.FactionDefinition faction : content.getFactions()) {
            float value = reputation.getReputation(faction.runtimeId());
            if (value != 0f) {
                result.add(new PlayerReputationState(faction.id(), value));
            }
        }
        return result;
    }

    private static int affordable(long balanceMilliCredits, float priceCredits, int maximum) {
        if (maximum <= 0 || balanceMilliCredits < 0L || !Float.isFinite(priceCredits) || priceCredits <= 0f) {
            return 0;
        }
        try {
            return Money.maximumAffordable(balanceMilliCredits, priceCredits, maximum);
        } catch (IllegalArgumentException exception) {
            return 0;
        }
    }

    private record PhysicalContext(
            SimulationSession session,
            Entity ship,
            Entity station,
            TransformComponent shipTransform,
            TransformComponent stationTransform,
            ShipComponent shipRole,
            InventoryComponent shipInventory,
            InventoryComponent stationInventory,
            WalletComponent stationWallet,
            float berthRange) {
    }

    private record Context(
            PlayerState player,
            ShipComponent ship,
            Entity station,
            InventoryComponent shipInventory,
            InventoryComponent stationInventory,
            WalletComponent stationWallet,
            ContentCatalog.ItemDefinition item,
            TradeController controller,
            Entity proxy,
            WalletComponent proxyWallet,
            ReputationComponent proxyReputation) {
    }
}
