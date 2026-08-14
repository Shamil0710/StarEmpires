package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ReputationComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.controllers.TradeController;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Manual market interface reusing the same authoritative TradeController as AI.
 *
 * <p>The player wallet/reputation live in PlayerState while cargo lives physically on the active
 * ship. Each command creates a non-persistent participant proxy that shares the ship's real
 * InventoryComponent and mirrors player wallet, reputation and faction affiliation. A successful
 * TradeController operation therefore mutates the actual cargo/station economy and the resulting
 * wallet/reputation values are copied back into PlayerState without creating goods or money.</p>
 */
public final class PlayerMarketService {
    private final PlayerRuntime runtime;
    private final ContentCatalog content;

    /**
     * Creates a manual market service.
     *
     * @param runtime current playable runtime
     * @param content content catalog used by the runtime world
     */
    public PlayerMarketService(PlayerRuntime runtime, ContentCatalog content) {
        this.runtime = Objects.requireNonNull(runtime, "PlayerRuntime not set");
        this.content = Objects.requireNonNull(content, "ContentCatalog not set");
    }

    /**
     * Builds the current docked market/cargo/wallet snapshot for UI rendering.
     *
     * @return market view, or empty unless the active ship is docked at a live market
     */
    public Optional<PlayerMarketView> view() {
        TradeContext context = resolveContext();
        if (context == null) {
            return Optional.empty();
        }
        boolean access = context.controller().canTradeWithStation(context.proxy(), context.station());
        List<PlayerMarketItemView> rows = new ArrayList<>();
        for (ContentCatalog.ItemDefinition item : content.getItems()) {
            int id = item.runtimeId();
            rows.add(new PlayerMarketItemView(
                    item.id(), item.displayName(), id,
                    context.stationInventory().stock[id],
                    context.market().targetStock[id],
                    context.shipInventory().stock[id],
                    context.controller().getEffectiveSellPrice(context.station(), id, context.proxyReputation()),
                    context.controller().getEffectiveBuyPrice(context.station(), id, context.proxyReputation()),
                    context.market().isTradable(id)));
        }
        return Optional.of(new PlayerMarketView(
                context.player().dockedAt(),
                context.player().walletMilliCredits(),
                context.shipInventory().getTotalStock(),
                context.shipInventory().capacity,
                access,
                rows));
    }

    /**
     * Manually buys physical cargo from the currently docked station.
     *
     * @param itemContentId stable item content ID
     * @param amount strictly positive amount
     * @return true only when the shared TradeController completes the full transfer
     */
    public boolean buy(String itemContentId, int amount) {
        ContentCatalog.ItemDefinition item = content.findItem(normalizedItemId(itemContentId));
        TradeContext context = resolveContext();
        if (item == null || context == null || amount <= 0) {
            return false;
        }
        ShipComponent ship = context.ship().getComponent(ShipComponent.class);
        if (ship == null || !ship.canPurchaseItem(item.runtimeId())) {
            return false;
        }
        if (!context.controller().buyFromStation(
                context.station(), context.proxy(), item.runtimeId(), amount, context.proxyReputation())) {
            return false;
        }
        persistProxyFinancialState(context);
        return true;
    }

    /**
     * Manually sells physical cargo to the currently docked station.
     *
     * @param itemContentId stable item content ID
     * @param amount strictly positive amount
     * @return true only when the shared TradeController completes the full transfer
     */
    public boolean sell(String itemContentId, int amount) {
        ContentCatalog.ItemDefinition item = content.findItem(normalizedItemId(itemContentId));
        TradeContext context = resolveContext();
        if (item == null || context == null || amount <= 0) {
            return false;
        }
        if (!context.controller().sellToStation(
                context.station(), context.proxy(), item.runtimeId(), amount, context.proxyReputation())) {
            return false;
        }
        persistProxyFinancialState(context);
        return true;
    }

    private TradeContext resolveContext() {
        PlayerState player = runtime.player();
        if (player.dockedAt() == null || player.activeFleetId() == null) {
            return null;
        }
        FleetPlacementState placement = runtime.world().findFleet(player.activeFleetId()).orElse(null);
        if (placement == null
                || placement.locationKind() != FleetLocationKind.IN_SYSTEM
                || !placement.systemId().equals(player.dockedAt().systemId())) {
            return null;
        }
        SimulationSession session = runtime.world().findSession(placement.systemId()).orElse(null);
        if (session == null) {
            return null;
        }
        Entity ship = session.getEntityRegistry().find(placement.localEntityId());
        Entity station = session.getEntityRegistry().find(player.dockedAt().entityId());
        InventoryComponent shipInventory = ship == null ? null : ship.getComponent(InventoryComponent.class);
        InventoryComponent stationInventory = station == null ? null : station.getComponent(InventoryComponent.class);
        MarketComponent market = station == null ? null : station.getComponent(MarketComponent.class);
        WalletComponent stationWallet = station == null ? null : station.getComponent(WalletComponent.class);
        if (ship == null || station == null || shipInventory == null || stationInventory == null
                || market == null || stationWallet == null) {
            return null;
        }

        Entity proxy = new Entity();
        proxy.add(shipInventory);
        WalletComponent proxyWallet = new WalletComponent(player.walletMilliCredits());
        proxy.add(proxyWallet);
        proxy.add(new IdentityComponent("Player", IdentityComponent.Kind.FLEET));
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
        return new TradeContext(
                player, session, ship, station, shipInventory, stationInventory, market,
                new TradeController(session.getLedger()), proxy, proxyWallet, proxyReputation);
    }

    private void persistProxyFinancialState(TradeContext context) {
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
                previous.threatIntel(),
                previous.ownedConstructionProjectIds(),
                previous.ownedStations()));
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

    private static String normalizedItemId(String itemContentId) {
        if (itemContentId == null) {
            return "";
        }
        return itemContentId.strip();
    }

    private record TradeContext(
            PlayerState player,
            SimulationSession session,
            Entity ship,
            Entity station,
            InventoryComponent shipInventory,
            InventoryComponent stationInventory,
            MarketComponent market,
            TradeController controller,
            Entity proxy,
            WalletComponent proxyWallet,
            ReputationComponent proxyReputation) {
    }
}
