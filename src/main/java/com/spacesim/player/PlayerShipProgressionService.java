package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.CombatCommandComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.MiningCommandComponent;
import com.spacesim.components.PlayerControlledComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;

import java.util.Objects;

/**
 * Stage-14B player-facing boundary for buying an existing ship and changing the directly controlled
 * owned fleet.
 *
 * <p>Purchase never spawns, clones or relocates a fleet. An explicit {@link PlayerShipSaleOffer}
 * is revalidated against the current physical world: the player must be docked at the seller, the
 * seller must be a live market with a real wallet, the offered FleetId must be a live in-system
 * ship, and seller/ship faction ownership must match. The actual atomic money and ownership
 * transfer remains {@link PlayerOwnershipService}.</p>
 *
 * <p>Changing the active fleet is a control-state operation only. It does not move either ship,
 * transfer cargo or cancel travel. A fleet already in jump transit cannot become directly
 * controlled until it materializes in a system. The current local ship must be physically stopped
 * before control is handed elsewhere, preventing a presentation/service layer from zeroing its
 * velocity outside the fixed-tick movement pipeline.</p>
 */
public final class PlayerShipProgressionService {
    private static final float STATIONARY_EPSILON_SQUARED = 0.0001f;

    private final PlayerRuntime runtime;
    private final PlayerOwnershipService ownership;

    /**
     * Creates the progression adapter over one playable runtime.
     *
     * @param runtime authoritative playable runtime
     */
    public PlayerShipProgressionService(PlayerRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "PlayerRuntime not set");
        this.ownership = new PlayerOwnershipService(runtime);
    }

    /**
     * Evaluates an offer against the live seller/fleet/player state without mutating anything.
     *
     * @param offer explicit current sale offer
     * @return read-only eligibility/status view
     */
    public PlayerShipPurchaseView inspect(PlayerShipSaleOffer offer) {
        PlayerShipSaleOffer checked = Objects.requireNonNull(offer, "Ship sale offer not set");
        PlayerState player = runtime.player();
        ResolvedOffer resolved = resolveLiveOffer(checked);
        PlayerShipPurchaseView.Status status;
        if (!isDockedAtSeller(player, checked)) {
            status = PlayerShipPurchaseView.Status.NOT_DOCKED_AT_SELLER;
        } else if (resolved == null || resolved.seller() == null) {
            status = PlayerShipPurchaseView.Status.INVALID_SELLER;
        } else if (player.ownedFleetIds().contains(checked.fleetId())) {
            status = PlayerShipPurchaseView.Status.ALREADY_OWNED;
        } else if (resolved.ship() == null) {
            status = PlayerShipPurchaseView.Status.FLEET_NOT_AVAILABLE;
        } else if (!resolved.sellerFactionMatchesShip()) {
            status = PlayerShipPurchaseView.Status.SELLER_MISMATCH;
        } else if (player.walletMilliCredits() < checked.priceMilliCredits()) {
            status = PlayerShipPurchaseView.Status.INSUFFICIENT_FUNDS;
        } else {
            status = PlayerShipPurchaseView.Status.AVAILABLE;
        }

        String shipName = resolved == null || resolved.shipIdentity() == null
                ? "" : resolved.shipIdentity().name;
        String archetypeId = resolved == null || resolved.shipArchetype() == null
                ? "" : resolved.shipArchetype().contentId;
        return new PlayerShipPurchaseView(
                status,
                checked.systemId(),
                checked.sellerStationId(),
                checked.fleetId(),
                shipName,
                archetypeId,
                checked.priceMilliCredits(),
                player.walletMilliCredits());
    }

    /**
     * Purchases a currently valid offer using the seller station's real wallet.
     *
     * @param offer explicit current sale offer
     * @return {@code true} when money and FleetId ownership transferred atomically
     */
    public boolean purchase(PlayerShipSaleOffer offer) {
        PlayerShipSaleOffer checked = Objects.requireNonNull(offer, "Ship sale offer not set");
        PlayerShipPurchaseView view = inspect(checked);
        if (!view.purchasable()) {
            return false;
        }
        ResolvedOffer resolved = resolveLiveOffer(checked);
        if (resolved == null || resolved.sellerWallet() == null || resolved.session() == null) {
            return false;
        }
        String sellerName = resolved.sellerIdentity() == null
                ? "SHIPYARD" : resolved.sellerIdentity().name;
        return ownership.purchaseFleet(
                checked.fleetId(),
                resolved.sellerWallet(),
                checked.priceMilliCredits(),
                resolved.session().getLedger(),
                sellerName);
    }

    /**
     * Changes direct control to another live owned FleetId without recreating or relocating it.
     *
     * <p>The player must be undocked. A target in jump transit is rejected until it is physically
     * materialized. If the current active ship is local, its direct-control intent and physical
     * velocity must already be stopped; callers should use {@link PlayerRuntime#stopMovement()} and
     * advance an ordinary fixed tick before switching when necessary.</p>
     *
     * @param fleetId owned FleetId that should become active
     * @return {@code true} when active control is already there or was safely rebound
     */
    public boolean switchActiveFleet(FleetId fleetId) {
        FleetId checked = Objects.requireNonNull(fleetId, "Active FleetId not set");
        PlayerState player = runtime.player();
        if (player.docked() || !player.ownedFleetIds().contains(checked)) {
            return false;
        }
        FleetPlacementState targetPlacement = runtime.world().findFleet(checked).orElse(null);
        if (targetPlacement == null || targetPlacement.locationKind() != FleetLocationKind.IN_SYSTEM
                || runtime.world().findFleetJump(checked).isPresent()) {
            return false;
        }
        if (checked.equals(player.activeFleetId())) {
            return true;
        }
        if (!canReleaseCurrentControl(player.activeFleetId())) {
            return false;
        }

        clearTransientActionIntent(player.activeFleetId());
        PlayerState replacement = PlayerRuntime.copyWithOwnershipAndWallet(
                player,
                player.walletMilliCredits(),
                player.ownedFleetIds(),
                checked);
        runtime.replacePlayerState(replacement);
        return checked.equals(runtime.player().activeFleetId());
    }

    private boolean canReleaseCurrentControl(FleetId currentFleetId) {
        if (currentFleetId == null) {
            return true;
        }
        FleetPlacementState placement = runtime.world().findFleet(currentFleetId).orElse(null);
        if (placement == null || placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
            return true;
        }
        SimulationSession session = runtime.world().findSession(placement.systemId()).orElse(null);
        Entity entity = session == null ? null : session.getEntityRegistry().find(placement.localEntityId());
        if (entity == null) {
            return true;
        }
        PlayerControlledComponent control = entity.getComponent(PlayerControlledComponent.class);
        TransformComponent transform = entity.getComponent(TransformComponent.class);
        if (control != null
                && (Math.abs(control.axisX) > 0.0001f || Math.abs(control.axisY) > 0.0001f)) {
            return false;
        }
        return transform == null || transform.velocity.len2() <= STATIONARY_EPSILON_SQUARED;
    }

    private void clearTransientActionIntent(FleetId currentFleetId) {
        if (currentFleetId == null) {
            return;
        }
        FleetPlacementState placement = runtime.world().findFleet(currentFleetId).orElse(null);
        if (placement == null || placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
            return;
        }
        SimulationSession session = runtime.world().findSession(placement.systemId()).orElse(null);
        Entity entity = session == null ? null : session.getEntityRegistry().find(placement.localEntityId());
        if (entity == null) {
            return;
        }
        CombatCommandComponent combat = entity.getComponent(CombatCommandComponent.class);
        if (combat != null) {
            combat.clear();
        }
        MiningCommandComponent mining = entity.getComponent(MiningCommandComponent.class);
        if (mining != null) {
            mining.clear();
        }
    }

    private ResolvedOffer resolveLiveOffer(PlayerShipSaleOffer offer) {
        SimulationSession session = runtime.world().findSession(offer.systemId()).orElse(null);
        if (session == null) {
            return null;
        }
        Entity seller = session.getEntityRegistry().find(offer.sellerStationId());
        MarketComponent sellerMarket = seller == null ? null : seller.getComponent(MarketComponent.class);
        WalletComponent sellerWallet = seller == null ? null : seller.getComponent(WalletComponent.class);
        FactionComponent sellerFaction = seller == null ? null : seller.getComponent(FactionComponent.class);
        IdentityComponent sellerIdentity = seller == null ? null : seller.getComponent(IdentityComponent.class);
        boolean validSeller = seller != null
                && sellerMarket != null
                && sellerWallet != null
                && sellerFaction != null
                && sellerIdentity != null
                && sellerIdentity.kind == IdentityComponent.Kind.STATION;
        if (!validSeller) {
            return new ResolvedOffer(session, null, null, null, null, null, null, null, false);
        }

        FleetPlacementState placement = runtime.world().findFleet(offer.fleetId()).orElse(null);
        Entity ship = null;
        IdentityComponent shipIdentity = null;
        ArchetypeComponent shipArchetype = null;
        FactionComponent shipFaction = null;
        if (placement != null
                && placement.locationKind() == FleetLocationKind.IN_SYSTEM
                && offer.systemId().equals(placement.systemId())
                && runtime.world().findFleetJump(offer.fleetId()).isEmpty()) {
            Entity candidate = session.getEntityRegistry().find(placement.localEntityId());
            if (candidate != null && candidate.getComponent(ShipComponent.class) != null) {
                ship = candidate;
                shipIdentity = candidate.getComponent(IdentityComponent.class);
                shipArchetype = candidate.getComponent(ArchetypeComponent.class);
                shipFaction = candidate.getComponent(FactionComponent.class);
                if (shipIdentity == null || shipArchetype == null || shipFaction == null) {
                    ship = null;
                }
            }
        }
        boolean factionMatches = ship != null && shipFaction.factionId == sellerFaction.factionId;
        return new ResolvedOffer(
                session,
                seller,
                sellerWallet,
                sellerIdentity,
                sellerFaction,
                ship,
                shipIdentity,
                shipArchetype,
                factionMatches);
    }

    private static boolean isDockedAtSeller(PlayerState player, PlayerShipSaleOffer offer) {
        DiscoveredObjectRef docked = player.dockedAt();
        return docked != null
                && offer.systemId().equals(docked.systemId())
                && offer.sellerStationId().equals(docked.entityId());
    }

    private record ResolvedOffer(
            SimulationSession session,
            Entity seller,
            WalletComponent sellerWallet,
            IdentityComponent sellerIdentity,
            FactionComponent sellerFaction,
            Entity ship,
            IdentityComponent shipIdentity,
            ArchetypeComponent shipArchetype,
            boolean sellerFactionMatchesShip) {
    }
}
