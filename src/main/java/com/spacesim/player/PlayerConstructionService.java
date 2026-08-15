package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.economy.EconomicLedger;
import com.spacesim.economy.Money;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.ConstructionDurationPolicy;
import com.spacesim.world.ConstructionMaterialState;
import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.ConstructionProjectState;
import com.spacesim.world.ConstructionProjectStatus;
import com.spacesim.world.ConstructionSettlementKind;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StarSystemId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Authoritative Stage-16/17 player adapter for physical construction projects.
 *
 * <p>This service does not spawn a completed station. It creates the same world-level construction
 * project/site used by the simulation core and records only human ownership in {@link PlayerState}.
 * Economic settlement remains external/personal, while the project's legal faction identity is
 * the player's current faction when affiliated. Thus joining/founding a faction does not silently
 * merge the personal wallet with faction treasury.</p>
 *
 * <p>Project authoring is guarded by {@link ConstructionPlacementPolicy}; UI code may preview the
 * same authoritative result but cannot bypass bounds, permanent-object clearance, jump-arrival or
 * current territorial access rules.</p>
 *
 * <p>Player funding is a real atomic money transfer from the persistent personal wallet into the
 * physical construction-site {@link WalletComponent}. The site can therefore pay ordinary market
 * suppliers; extra funding changes liquidity only and never rewrites persisted build duration.</p>
 *
 * <p>Manual material delivery is also physical: the source must be a real player-owned FleetId in
 * the same system, locally materialized inside berth range and nearly stopped. Only then does this
 * adapter invoke the ordinary world construction-material transfer. UI code therefore cannot move
 * cargo across a system or out of jump transit.</p>
 */
public final class PlayerConstructionService {
    private static final String PLAYER_LEDGER_NAME = "PLAYER";
    private static final float DEFAULT_TRANSFER_RANGE = 10f;
    private static final float MAX_TRANSFER_SPEED = 0.25f;

    private final PlayerRuntime runtime;

    /**
     * Creates a player construction adapter for one playable runtime.
     *
     * @param runtime current player/world runtime
     */
    public PlayerConstructionService(PlayerRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "PlayerRuntime not set");
    }

    /**
     * Returns all currently constructible station archetypes with authoritative cost/work data.
     *
     * <p>Technology/unlock filtering is intentionally not fabricated before the future tech-tier
     * model exists. For this Stage-16 slice, every catalog station carrying a construction
     * definition is exposed.</p>
     *
     * @return immutable list sorted by stable archetype content ID
     */
    public List<PlayerConstructionArchetypeView> buildableArchetypes() {
        ContentCatalog catalog = runtime.content();
        List<PlayerConstructionArchetypeView> result = new ArrayList<>();
        for (ContentCatalog.StationArchetypeDefinition station : catalog.getStationArchetypes()) {
            if (station.construction() == null) {
                continue;
            }
            ConstructionDurationPolicy.Estimate estimate = ConstructionDurationPolicy.estimate(catalog, station);
            Map<String, Integer> materials = new TreeMap<>(station.construction().materials());
            result.add(new PlayerConstructionArchetypeView(
                    station.id(),
                    station.displayName(),
                    Money.fromCredits(station.construction().fundingCredits()),
                    materials,
                    estimate.materialWorkUnits(),
                    estimate.totalSeconds()));
        }
        result.sort(Comparator.comparing(PlayerConstructionArchetypeView::archetypeContentId));
        return List.copyOf(result);
    }

    /**
     * Previews one construction location using exactly the same policy as project creation.
     *
     * @param x requested local X coordinate
     * @param y requested local Y coordinate
     * @return authoritative read-only placement decision
     * @throws IllegalStateException when no active owned fleet is physically present in a discovered system
     */
    public PlayerConstructionPlacementView previewPlacement(float x, float y) {
        PlayerState player = runtime.player();
        FleetPlacementState placement = authoringPlacement(player);
        return ConstructionPlacementPolicy.evaluate(
                runtime.world(), player, placement.systemId(), x, y);
    }

    /**
     * Creates one personally funded player-owned construction project in the active fleet's system.
     *
     * <p>If the player is affiliated with a faction, that faction is the project's legal identity
     * for territorial authorization and resulting station affiliation. Payment nevertheless remains
     * {@link ConstructionSettlementKind#EXTERNAL_OWNER} and therefore comes from the personal wallet.</p>
     *
     * @param stationArchetypeContentId constructible station archetype content ID
     * @param x finite local-system X coordinate
     * @param y finite local-system Y coordinate
     * @return stable world-level construction project ID
     * @throws IllegalArgumentException for unknown/non-constructible archetype or rejected placement
     * @throws IllegalStateException when no owned active fleet is physically present in a discovered system
     */
    public ConstructionProjectId createProject(
            String stationArchetypeContentId,
            float x,
            float y) {
        String archetypeId = requireConstructible(stationArchetypeContentId).id();
        PlayerState current = runtime.player();
        FleetPlacementState placement = authoringPlacement(current);
        PlayerConstructionPlacementView placementView = ConstructionPlacementPolicy.evaluate(
                runtime.world(), current, placement.systemId(), x, y);
        if (!placementView.allowed()) {
            throw new IllegalArgumentException(
                    "Construction placement rejected: " + placementView.rejection());
        }

        ConstructionProjectId projectId = runtime.world().createConstructionProject(
                null,
                current.factionContentId(),
                archetypeId,
                placement.systemId(),
                x,
                y);
        try {
            ConstructionProjectState state = runtime.world().findConstructionProject(projectId).orElseThrow();
            requireExternalContract(state);
            if (!Objects.equals(current.factionContentId(), state.legalFactionContentId())) {
                throw new IllegalStateException("Player construction legal faction differs from current affiliation");
            }
            List<ConstructionProjectId> ownedProjects = new ArrayList<>(current.ownedConstructionProjectIds());
            ownedProjects.add(projectId);
            runtime.replacePlayerState(PlayerRuntime.copyWithConstructionOwnership(
                    current, ownedProjects, current.ownedStations()));
            return projectId;
        } catch (RuntimeException exception) {
            rollbackEmptyProject(projectId, exception);
            throw exception;
        }
    }

    /**
     * Transfers real player money into an owned external construction site's wallet.
     *
     * <p>The operation changes no build-time parameters. Project lifecycle observes the physical
     * site wallet on the ordinary world tick and can then transition from PLANNED to FUNDED.</p>
     *
     * @param projectId player-owned external project
     * @param amountMilliCredits positive amount to transfer
     * @return transferred amount, or zero when the player/site wallet cannot perform the transfer
     */
    public long fundProject(ConstructionProjectId projectId, long amountMilliCredits) {
        if (amountMilliCredits <= 0L) {
            throw new IllegalArgumentException("Construction funding amount must be positive");
        }
        ConstructionProjectState project = requireOwnedExternalProject(projectId);
        if (project.status() == ConstructionProjectStatus.BUILDING) {
            throw new IllegalStateException("Cannot fund an already BUILDING project");
        }
        if (isTerminal(project.status())) {
            throw new IllegalStateException("Cannot fund a terminal construction project");
        }

        SimulationSession session = runtime.world().findSession(project.systemId()).orElseThrow();
        Entity site = session.getEntityRegistry().find(project.constructionSiteEntityId());
        WalletComponent siteWallet = site == null ? null : site.getComponent(WalletComponent.class);
        if (siteWallet == null) {
            throw new IllegalStateException("Construction project has no live site wallet");
        }

        PlayerState previous = runtime.player();
        WalletComponent playerWallet = new WalletComponent(previous.walletMilliCredits());
        if (!playerWallet.canDebit(amountMilliCredits) || !siteWallet.canCredit(amountMilliCredits)) {
            return 0L;
        }
        long resultingWallet = Math.subtractExact(previous.walletMilliCredits(), amountMilliCredits);
        PlayerState candidate = PlayerRuntime.copyWithOwnershipAndWallet(
                previous,
                resultingWallet,
                previous.ownedFleetIds(),
                previous.activeFleetId());

        if (!playerWallet.transferTo(siteWallet, amountMilliCredits)) {
            return 0L;
        }
        try {
            runtime.replacePlayerState(candidate);
            EconomicLedger ledger = session.getLedger();
            ledger.recordMoneyTransfer(
                    PLAYER_LEDGER_NAME,
                    siteLedgerName(projectId),
                    amountMilliCredits,
                    "player-construction-funding");
            return amountMilliCredits;
        } catch (RuntimeException exception) {
            rollbackFunding(previous, playerWallet, siteWallet, amountMilliCredits, exception);
            throw exception;
        }
    }

    /**
     * Physically transfers required construction cargo from one player-owned fleet into the site.
     *
     * @param projectId player-owned external construction project
     * @param sourceFleetId player-owned physical source fleet
     * @param itemContentId required item content ID
     * @param amount requested positive whole units
     * @return accepted units, or zero when the source is not physically berthed/eligible
     */
    public int deliverMaterial(
            ConstructionProjectId projectId,
            FleetId sourceFleetId,
            String itemContentId,
            int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Construction delivery amount must be positive");
        }
        ConstructionProjectState project = requireOwnedExternalProject(projectId);
        if (project.status() == ConstructionProjectStatus.BUILDING || isTerminal(project.status())) {
            throw new IllegalStateException("Construction project no longer accepts materials");
        }
        FleetId fleetId = Objects.requireNonNull(sourceFleetId, "Source FleetId not set");
        PlayerState player = runtime.player();
        if (!player.ownedFleetIds().contains(fleetId)) {
            return 0;
        }
        ContentCatalog.ItemDefinition item = runtime.content().findItem(normalizedItemId(itemContentId));
        if (item == null || !requiresItem(project, item.id())) {
            return 0;
        }

        FleetPlacementState placement = runtime.world().findFleet(fleetId).orElse(null);
        if (placement == null
                || placement.locationKind() != FleetLocationKind.IN_SYSTEM
                || !project.systemId().equals(placement.systemId())
                || runtime.world().findFleetJump(fleetId).isPresent()) {
            return 0;
        }
        SimulationSession session = runtime.world().findSession(project.systemId()).orElse(null);
        if (session == null) {
            return 0;
        }
        Entity ship = session.getEntityRegistry().find(placement.localEntityId());
        Entity site = session.getEntityRegistry().find(project.constructionSiteEntityId());
        TransformComponent shipTransform = ship == null ? null : ship.getComponent(TransformComponent.class);
        TransformComponent siteTransform = site == null ? null : site.getComponent(TransformComponent.class);
        InventoryComponent shipInventory = ship == null ? null : ship.getComponent(InventoryComponent.class);
        if (shipTransform == null || siteTransform == null || shipInventory == null) {
            return 0;
        }
        MiningComponent mining = ship.getComponent(MiningComponent.class);
        float speed = mining == null ? 0f : mining.movementVelocity.len();
        if (speed > MAX_TRANSFER_SPEED) {
            return 0;
        }
        if (shipTransform.position.dst(siteTransform.position) > DEFAULT_TRANSFER_RANGE) {
            return 0;
        }
        int available = shipInventory.stock[item.runtimeId()];
        if (available <= 0) {
            return 0;
        }
        return runtime.world().deliverConstructionMaterial(
                projectId, placement.localEntityId(), item.id(), Math.min(amount, available));
    }

    /**
     * Cancels an empty player-owned external construction project.
     *
     * @param projectId player-owned project to cancel
     * @return true when cancelled
     */
    public boolean cancelProject(ConstructionProjectId projectId) {
        ConstructionProjectState state = requireOwnedExternalProject(projectId);
        if (state.totalDeliveredUnits() != 0L) {
            throw new IllegalStateException("Cannot cancel player project after material delivery");
        }
        if (state.projectWalletMilliCredits() != 0L) {
            throw new IllegalStateException("Funded external project requires an explicit player refund operation");
        }
        boolean cancelled = runtime.world().cancelConstructionProject(projectId);
        if (cancelled) {
            PlayerState current = runtime.player();
            List<ConstructionProjectId> owned = new ArrayList<>(current.ownedConstructionProjectIds());
            owned.remove(projectId);
            runtime.replacePlayerState(PlayerRuntime.copyWithConstructionOwnership(
                    current, owned, current.ownedStations()));
        }
        return cancelled;
    }

    private ContentCatalog.StationArchetypeDefinition requireConstructible(String value) {
        String id = Objects.requireNonNull(value, "Construction archetype not set").strip();
        ContentCatalog.StationArchetypeDefinition station = runtime.content().findStationArchetype(id);
        if (station == null || station.construction() == null) {
            throw new IllegalArgumentException("Unknown/non-constructible station archetype: " + id);
        }
        return station;
    }

    private FleetPlacementState authoringPlacement(PlayerState player) {
        FleetId activeFleetId = player.activeFleetId();
        if (activeFleetId == null || !player.ownedFleetIds().contains(activeFleetId)) {
            throw new IllegalStateException("Player has no active owned fleet for construction authoring");
        }
        FleetPlacementState placement = runtime.world().findFleet(activeFleetId).orElseThrow(
                () -> new IllegalStateException("Active FleetId is missing from world placements"));
        if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                || runtime.world().findFleetJump(activeFleetId).isPresent()) {
            throw new IllegalStateException("Active fleet must be physically present in a StarSystem");
        }
        if (!player.discoveredSystemIds().contains(placement.systemId())) {
            throw new IllegalStateException("Construction requires a discovered target system");
        }
        return placement;
    }

    private ConstructionProjectState requireOwnedExternalProject(ConstructionProjectId projectId) {
        ConstructionProjectId id = Objects.requireNonNull(projectId, "ConstructionProjectId not set");
        if (!runtime.player().ownedConstructionProjectIds().contains(id)) {
            throw new IllegalArgumentException("Construction project is not owned by player: " + id);
        }
        ConstructionProjectState state = runtime.world().findConstructionProject(id).orElseThrow(
                () -> new IllegalStateException("Owned construction project missing from world: " + id));
        requireExternalContract(state);
        return state;
    }

    private static void requireExternalContract(ConstructionProjectState state) {
        if (state.settlementKind() != ConstructionSettlementKind.EXTERNAL_OWNER
                || state.ownerFactionContentId() != null) {
            throw new IllegalStateException("Player construction project must use external settlement");
        }
    }

    private static boolean requiresItem(ConstructionProjectState project, String itemId) {
        for (ConstructionMaterialState material : project.materials()) {
            if (material.itemContentId().equals(itemId) && material.remainingAmount() > 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTerminal(ConstructionProjectStatus status) {
        return status == ConstructionProjectStatus.COMPLETED
                || status == ConstructionProjectStatus.CANCELLED
                || status == ConstructionProjectStatus.FAILED;
    }

    private static String normalizedItemId(String value) {
        String id = Objects.requireNonNull(value, "Construction item not set").strip();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Construction item cannot be blank");
        }
        return id;
    }

    private void rollbackEmptyProject(ConstructionProjectId projectId, RuntimeException original) {
        try {
            ConstructionProjectState state = runtime.world().findConstructionProject(projectId).orElse(null);
            if (state != null
                    && !isTerminal(state.status())
                    && state.projectWalletMilliCredits() == 0L
                    && state.totalDeliveredUnits() == 0L) {
                runtime.world().cancelConstructionProject(projectId);
            }
        } catch (RuntimeException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private void rollbackFunding(
            PlayerState previous,
            WalletComponent playerWallet,
            WalletComponent siteWallet,
            long amountMilliCredits,
            RuntimeException original) {
        try {
            if (!siteWallet.transferTo(playerWallet, amountMilliCredits)) {
                throw new IllegalStateException("Could not reverse construction funding wallet transfer");
            }
            runtime.replacePlayerState(previous);
        } catch (RuntimeException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private static String siteLedgerName(ConstructionProjectId projectId) {
        return "construction:" + projectId.value() + ":site";
    }
}
