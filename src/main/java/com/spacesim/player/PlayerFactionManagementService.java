package com.spacesim.player;

import com.spacesim.world.DiplomaticEmbargoCommand;
import com.spacesim.world.DiplomaticEmbargoCommandResult;
import com.spacesim.world.DiplomaticTreatyCommand;
import com.spacesim.world.DiplomaticTreatyCommandResult;
import com.spacesim.world.FactionPolicyCommand;
import com.spacesim.world.FactionPolicyCommandExecutor;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.TerritorialClaimState;
import com.spacesim.world.TerritorialConstructionRightState;
import com.spacesim.world.TerritorialRecognitionState;

import java.util.Objects;

/**
 * Stage-17G player-facing command facade for faction management.
 *
 * <p>The facade contains no parallel economic, diplomatic or territorial rules. Every operation
 * resolves the player's current faction and delegates to the same authoritative Stage-17 boundary
 * used by AI/world callers. Independent players therefore have no hidden faction-management
 * authority, and UI commands cannot impersonate another faction.</p>
 */
public final class PlayerFactionManagementService {
    private final PlayerRuntime runtime;
    private final PlayerFactionTreasuryRuntimeService treasury;
    private final PlayerFactionAssetAffiliationService affiliation;

    /**
     * Creates one player-faction management adapter.
     *
     * @param runtime authoritative playable runtime
     */
    public PlayerFactionManagementService(PlayerRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "PlayerRuntime not set");
        this.treasury = new PlayerFactionTreasuryRuntimeService(runtime);
        this.affiliation = new PlayerFactionAssetAffiliationService(runtime);
    }

    /** @return current immutable faction-management projection */
    public FactionManagementSnapshot snapshot() {
        return FactionManagementModel.capture(runtime);
    }

    /**
     * Transfers real personal money into the player's faction treasury.
     *
     * @param amountMilliCredits strictly positive amount
     * @return true when the full conserved transfer succeeded
     */
    public boolean capitalizeTreasury(long amountMilliCredits) {
        requirePlayerFaction();
        return treasury.capitalize(amountMilliCredits);
    }

    /**
     * Transfers real faction-treasury money back to the player's personal wallet.
     *
     * @param amountMilliCredits strictly positive amount
     * @return true when the full conserved transfer succeeded
     */
    public boolean transferTreasuryToPersonal(long amountMilliCredits) {
        requirePlayerFaction();
        return treasury.transferToPersonal(amountMilliCredits);
    }

    /**
     * Affiliates the player's already-existing physical fleets and completed stations with the
     * current faction without replacing IDs, moving cargo or creating assets.
     *
     * @return combined local/transit/station affiliation reports
     */
    public AssetAffiliationResult affiliateOwnedAssets() {
        requirePlayerFaction();
        return new AssetAffiliationResult(
                affiliation.affiliateLocalOwnedFleets(),
                affiliation.affiliateTransitOwnedFleets(),
                affiliation.affiliateOwnedStations());
    }

    /**
     * Submits one ordinary shared faction policy command.
     *
     * @param command doctrine/fiscal/stock-production/apply command
     * @return common authoritative execution result
     */
    public FactionPolicyCommandExecutor.ExecutionResult submitPolicy(FactionPolicyCommand command) {
        requirePlayerFaction();
        return PlayerFactionPolicyService.submit(runtime, Objects.requireNonNull(command, "Policy command not set"));
    }

    /**
     * Submits one treaty lifecycle command after preventing faction impersonation.
     *
     * @param command common player/AI treaty command
     * @return authoritative persistent diplomacy transition
     */
    public DiplomaticTreatyCommandResult submitTreaty(DiplomaticTreatyCommand command) {
        DiplomaticTreatyCommand checked = Objects.requireNonNull(command, "Treaty command not set");
        requireActor(checked.actorFactionContentId());
        return runtime.world().applyDiplomaticTreatyCommand(checked);
    }

    /**
     * Submits one unilateral market-access embargo command after preventing faction impersonation.
     *
     * @param command common player/AI embargo command
     * @return authoritative persistent diplomacy transition
     */
    public DiplomaticEmbargoCommandResult submitEmbargo(DiplomaticEmbargoCommand command) {
        DiplomaticEmbargoCommand checked = Objects.requireNonNull(command, "Embargo command not set");
        requireActor(checked.actorFactionContentId());
        return runtime.world().applyDiplomaticEmbargoCommand(checked);
    }

    /**
     * Declares a political claim through ordinary stabilization rules; sovereignty is not granted.
     *
     * @param systemId target system
     * @return persistent claim state
     */
    public TerritorialClaimState declareClaim(StarSystemId systemId) {
        return runtime.world().declareTerritorialClaim(
                requirePlayerFaction(), Objects.requireNonNull(systemId, "Claim system not set"));
    }

    /**
     * Withdraws the player's faction non-established claim.
     *
     * @param systemId claimed system
     * @return true when a claim was removed
     */
    public boolean withdrawClaim(StarSystemId systemId) {
        return runtime.world().withdrawTerritorialClaim(
                requirePlayerFaction(), Objects.requireNonNull(systemId, "Claim system not set"));
    }

    /**
     * Voluntarily relinquishes established control without manufacturing a successor controller.
     *
     * @param systemId controlled system
     * @return true when control was relinquished
     */
    public boolean relinquishControl(StarSystemId systemId) {
        return runtime.world().relinquishTerritorialControl(
                requirePlayerFaction(), Objects.requireNonNull(systemId, "Controlled system not set"));
    }

    /**
     * Records the player's faction recognition of another faction's claim.
     *
     * @param targetFactionContentId recognized claimant
     * @param systemId claimed system
     * @return persistent recognition
     */
    public TerritorialRecognitionState recognizeClaim(
            String targetFactionContentId,
            StarSystemId systemId) {
        return runtime.world().recognizeTerritorialClaim(
                requirePlayerFaction(),
                requireTargetFaction(targetFactionContentId),
                Objects.requireNonNull(systemId, "Claim system not set"));
    }

    /**
     * Records the player's faction recognition of another faction's established control.
     *
     * @param targetFactionContentId recognized controller
     * @param systemId controlled system
     * @return persistent recognition
     */
    public TerritorialRecognitionState recognizeControl(
            String targetFactionContentId,
            StarSystemId systemId) {
        return runtime.world().recognizeTerritorialControl(
                requirePlayerFaction(),
                requireTargetFaction(targetFactionContentId),
                Objects.requireNonNull(systemId, "Controlled system not set"));
    }

    /**
     * Grants an explicit foreign construction right only through the existing territorial-law boundary.
     *
     * @param granteeFactionContentId foreign builder faction
     * @param systemId system currently controlled by the player's faction
     * @param expiresTick exclusive expiry tick or -1 for indefinite
     * @return persistent construction concession
     */
    public TerritorialConstructionRightState grantConstructionRight(
            String granteeFactionContentId,
            StarSystemId systemId,
            long expiresTick) {
        return runtime.world().grantTerritorialConstructionRight(
                requirePlayerFaction(),
                requireTargetFaction(granteeFactionContentId),
                Objects.requireNonNull(systemId, "Construction-right system not set"),
                expiresTick);
    }

    /**
     * Revokes an existing foreign construction concession granted by the player's faction.
     *
     * @param granteeFactionContentId foreign grantee
     * @param systemId affected controlled system
     * @return true when a right was removed
     */
    public boolean revokeConstructionRight(
            String granteeFactionContentId,
            StarSystemId systemId) {
        return runtime.world().revokeTerritorialConstructionRight(
                requirePlayerFaction(),
                requireTargetFaction(granteeFactionContentId),
                Objects.requireNonNull(systemId, "Construction-right system not set"));
    }

    private String requirePlayerFaction() {
        String factionId = runtime.player().factionContentId();
        if (factionId == null || runtime.world().findFactionRuntimeId(factionId).isEmpty()) {
            throw new IllegalStateException("Independent/unresolved player has no faction-management authority");
        }
        return factionId;
    }

    private void requireActor(String actorFactionContentId) {
        String playerFactionId = requirePlayerFaction();
        String actorId = Objects.requireNonNull(actorFactionContentId, "Command actor faction not set").strip();
        if (!playerFactionId.equals(actorId)) {
            throw new IllegalArgumentException("Player faction command cannot impersonate another faction");
        }
    }

    private String requireTargetFaction(String factionContentId) {
        String target = Objects.requireNonNull(factionContentId, "Target faction not set").strip();
        if (target.isEmpty() || runtime.world().findFactionRuntimeId(target).isEmpty()) {
            throw new IllegalArgumentException("Unknown target faction: " + target);
        }
        if (target.equals(requirePlayerFaction())) {
            throw new IllegalArgumentException("Faction-management target must be another faction");
        }
        return target;
    }

    /**
     * Combined id-preserving affiliation result for player-owned physical assets.
     *
     * @param localFleets local physical fleet affiliation report
     * @param transitFleets detached in-transit fleet affiliation report
     * @param stations completed station affiliation report
     */
    public record AssetAffiliationResult(
            PlayerFactionAssetAffiliationService.AffiliationReport localFleets,
            PlayerFactionAssetAffiliationService.TransitAffiliationReport transitFleets,
            PlayerFactionAssetAffiliationService.StationAffiliationReport stations) {
        /**
         * Validates all three authoritative affiliation reports.
         *
         * @param localFleets local fleet report
         * @param transitFleets transit fleet report
         * @param stations station report
         */
        public AssetAffiliationResult {
            Objects.requireNonNull(localFleets, "Local fleet affiliation report not set");
            Objects.requireNonNull(transitFleets, "Transit fleet affiliation report not set");
            Objects.requireNonNull(stations, "Station affiliation report not set");
        }
    }
}
