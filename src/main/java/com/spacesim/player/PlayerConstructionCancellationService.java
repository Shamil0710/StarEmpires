package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.ConstructionMaterialState;
import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.ConstructionProjectState;
import com.spacesim.world.ConstructionProjectStatus;
import com.spacesim.world.ConstructionSettlementKind;

import java.util.Objects;

/**
 * Authoritative Stage-16 player cancellation/refund boundary for external construction projects.
 *
 * <p>The world construction core remains responsible for project cancellation and physical site
 * removal. This adapter only supplies the settlement step that the faction-oriented Stage-9 core
 * cannot perform for an independent player: the complete live site wallet is transferred back to
 * the persistent personal wallet before the ordinary world cancellation runs.</p>
 *
 * <p>Voluntary cancellation is deliberately conservative. Once any required material is physically
 * present at the construction site, cancellation is rejected until an explicit recoverable-salvage
 * policy exists. BUILDING projects are likewise rejected before salvage-by-progress semantics are
 * implemented. No delivered resource is silently deleted.</p>
 */
public final class PlayerConstructionCancellationService {
    private static final String PLAYER_LEDGER_NAME = "PLAYER";

    private final PlayerRuntime runtime;

    /**
     * Creates a cancellation adapter for one playable runtime.
     *
     * @param runtime current authoritative player/world runtime
     */
    public PlayerConstructionCancellationService(PlayerRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "PlayerRuntime not set");
    }

    /**
     * Evaluates cancellation against current physical site state without mutating the world.
     *
     * @param projectId candidate construction project
     * @return stable read-only cancellation decision and full refundable site liquidity
     */
    public PlayerConstructionCancellationView preview(ConstructionProjectId projectId) {
        ConstructionProjectId checkedId = Objects.requireNonNull(projectId, "ConstructionProjectId not set");
        PlayerState player = runtime.player();
        if (!player.ownedConstructionProjectIds().contains(checkedId)) {
            return rejected(checkedId, PlayerConstructionCancellationRejection.NOT_OWNED, 0L);
        }
        ConstructionProjectState project = runtime.world().findConstructionProject(checkedId).orElse(null);
        if (project == null) {
            return rejected(checkedId, PlayerConstructionCancellationRejection.SITE_UNAVAILABLE, 0L);
        }
        requireExternalContract(project);
        if (isTerminal(project.status())) {
            return rejected(checkedId, PlayerConstructionCancellationRejection.TERMINAL, 0L);
        }
        if (project.status() == ConstructionProjectStatus.BUILDING) {
            return rejected(checkedId, PlayerConstructionCancellationRejection.BUILDING, 0L);
        }

        Context context = resolveContext(player, project);
        if (context == null) {
            return rejected(checkedId, PlayerConstructionCancellationRejection.SITE_UNAVAILABLE, 0L);
        }
        if (containsRequiredMaterial(project, context.inventory())) {
            return rejected(
                    checkedId,
                    PlayerConstructionCancellationRejection.MATERIALS_DELIVERED,
                    context.siteWallet().getBalanceMilliCredits());
        }
        long refund = context.siteWallet().getBalanceMilliCredits();
        try {
            Math.addExact(player.walletMilliCredits(), refund);
        } catch (ArithmeticException exception) {
            return rejected(checkedId, PlayerConstructionCancellationRejection.PLAYER_WALLET_CAPACITY, refund);
        }
        return new PlayerConstructionCancellationView(
                checkedId,
                true,
                PlayerConstructionCancellationRejection.NONE,
                refund);
    }

    /**
     * Cancels an eligible project and returns its complete live site wallet to the player.
     *
     * <p>The money transfer is rolled back if the ordinary world cancellation rejects or fails.
     * The final ledger entry is emitted only after the world core has successfully removed the site
     * and marked the project CANCELLED.</p>
     *
     * @param projectId player-owned external construction project
     * @return true after complete refund plus ordinary world cancellation; false for a stable rejection
     */
    public boolean cancel(ConstructionProjectId projectId) {
        PlayerConstructionCancellationView decision = preview(projectId);
        if (!decision.allowed()) {
            return false;
        }
        PlayerState previous = runtime.player();
        ConstructionProjectState project = runtime.world().findConstructionProject(projectId).orElseThrow();
        Context context = resolveContext(previous, project);
        if (context == null) {
            return false;
        }
        long refund = context.siteWallet().getBalanceMilliCredits();
        if (refund != decision.refundableMilliCredits()) {
            return false;
        }

        WalletComponent playerWallet = new WalletComponent(previous.walletMilliCredits());
        if (refund > 0L && !context.siteWallet().transferTo(playerWallet, refund)) {
            return false;
        }
        long resultingWallet = Math.addExact(previous.walletMilliCredits(), refund);
        PlayerState candidate = PlayerRuntime.copyWithOwnershipAndWallet(
                previous,
                resultingWallet,
                previous.ownedFleetIds(),
                previous.activeFleetId());

        try {
            runtime.replacePlayerState(candidate);
            if (!runtime.world().cancelConstructionProject(projectId)) {
                throw new IllegalStateException("World construction cancellation returned false");
            }
        } catch (RuntimeException exception) {
            runtime.replacePlayerState(previous);
            if (refund > 0L && !playerWallet.transferTo(context.siteWallet(), refund)) {
                exception.addSuppressed(new IllegalStateException(
                        "Construction cancellation rollback could not restore site money"));
            }
            throw exception;
        }

        if (refund > 0L) {
            context.session().getLedger().recordMoneyTransfer(
                    siteLedgerName(projectId),
                    PLAYER_LEDGER_NAME,
                    refund,
                    "player-construction-cancel-refund");
        }
        runtime.player();
        return true;
    }

    private Context resolveContext(PlayerState player, ConstructionProjectState project) {
        SimulationSession session = runtime.world().findSession(project.systemId()).orElse(null);
        Entity site = session == null ? null : session.getEntityRegistry().find(project.constructionSiteEntityId());
        WalletComponent wallet = site == null ? null : site.getComponent(WalletComponent.class);
        InventoryComponent inventory = site == null ? null : site.getComponent(InventoryComponent.class);
        if (session == null || site == null || wallet == null || inventory == null
                || !player.ownedConstructionProjectIds().contains(project.id())) {
            return null;
        }
        return new Context(session, wallet, inventory);
    }

    private boolean containsRequiredMaterial(ConstructionProjectState project, InventoryComponent inventory) {
        for (ConstructionMaterialState material : project.materials()) {
            ContentCatalog.ItemDefinition item = runtime.content().findItem(material.itemContentId());
            if (item == null) {
                return true;
            }
            if (inventory.stock[item.runtimeId()] > 0) {
                return true;
            }
        }
        return false;
    }

    private static PlayerConstructionCancellationView rejected(
            ConstructionProjectId projectId,
            PlayerConstructionCancellationRejection rejection,
            long refund) {
        return new PlayerConstructionCancellationView(projectId, false, rejection, refund);
    }

    private static void requireExternalContract(ConstructionProjectState project) {
        if (project.settlementKind() != ConstructionSettlementKind.EXTERNAL_OWNER
                || project.ownerFactionContentId() != null) {
            throw new IllegalStateException("Player project has invalid external settlement contract");
        }
    }

    private static boolean isTerminal(ConstructionProjectStatus status) {
        return status == ConstructionProjectStatus.COMPLETED
                || status == ConstructionProjectStatus.CANCELLED
                || status == ConstructionProjectStatus.FAILED;
    }

    private static String siteLedgerName(ConstructionProjectId projectId) {
        return "construction:" + projectId.value() + ":site";
    }

    private record Context(
            SimulationSession session,
            WalletComponent siteWallet,
            InventoryComponent inventory) {
    }
}
