package com.spacesim.world;

import com.spacesim.components.WalletComponent;
import com.spacesim.economy.Stage18ShipyardRuntime.YardWorkBudget;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftPhysicalLogisticsService.BuildResult;
import com.spacesim.world.SmallCraftPhysicalLogisticsService.DeliveryResult;
import com.spacesim.world.SmallCraftPhysicalLogisticsService.LogisticsState;
import com.spacesim.world.SmallCraftPhysicalLogisticsService.ProductionPlan;

import java.util.Objects;

/**
 * M22.8H post-battle carrier recovery seam over ordinary treasury, Stage-18 industry and G logistics.
 *
 * <p>Treasury funding moves already-existing money into a caller-owned procurement wallet through
 * {@link WorldSimulation#transferFromFactionTreasury}; it does not manufacture inputs or buy them
 * implicitly. Physical construction still succeeds only when {@link SmallCraftPhysicalLogisticsService}
 * can settle the exact Stage-18 materials, finished modules and yard work already present. Completed
 * craft remain pending delivery until the existing G logistics authority receives a physical arrival
 * receipt and assigns the same fresh identity to a compatible bay.</p>
 */
public final class CarrierPostBattleRecoveryService {
    private static final String FUNDING_REASON = "m22.8-carrier-small-craft-recovery";

    private final SmallCraftPhysicalLogisticsService logistics;
    private final TreasuryFundingAuthority treasury;

    /**
     * Creates the production recovery seam using the ordinary world faction treasury.
     *
     * @param world accepted ordinary world/treasury authority
     * @param logistics M22.8G physical production and delivery authority
     * @return recovery coordinator with no independent money or production state
     */
    public static CarrierPostBattleRecoveryService production(
            WorldSimulation world,
            SmallCraftPhysicalLogisticsService logistics) {
        WorldSimulation acceptedWorld = Objects.requireNonNull(world, "world");
        return new CarrierPostBattleRecoveryService(
                Objects.requireNonNull(logistics, "logistics"),
                (factionId, destination, ledgerName, amount) ->
                        acceptedWorld.transferFromFactionTreasury(
                                factionId,
                                destination,
                                ledgerName,
                                amount,
                                FUNDING_REASON));
    }

    CarrierPostBattleRecoveryService(
            SmallCraftPhysicalLogisticsService logistics,
            TreasuryFundingAuthority treasury) {
        this.logistics = Objects.requireNonNull(logistics, "logistics");
        this.treasury = Objects.requireNonNull(treasury, "treasury");
    }

    /**
     * Funds one replacement procurement envelope, then attempts the ordinary finite Stage-18 build.
     *
     * <p>A rejected build does not reverse the lawful treasury transfer: the money remains conserved
     * in the procurement wallet for later market/logistics use. It cannot become materials, yard work
     * or a craft unless the normal G settlement succeeds.</p>
     *
     * @param factionStableId stable faction paying for recovery procurement
     * @param procurementWallet ordinary destination wallet that owns the transferred funds
     * @param procurementLedgerName diagnostic ledger identity for that wallet
     * @param fundingMilliCredits explicit positive treasury authorization
     * @param logisticsState current G pending-delivery state
     * @param plan already validated physical G production plan
     * @param storage exact Stage-18 production-station storage
     * @param budget finite Stage-18 yard work budget
     * @return funding/build result without hidden supply or replacement grants
     */
    public FundedBuildResult fundAndSettleBuild(
            String factionStableId,
            WalletComponent procurementWallet,
            String procurementLedgerName,
            long fundingMilliCredits,
            LogisticsState logisticsState,
            ProductionPlan plan,
            Stage18StationStorage storage,
            YardWorkBudget budget) {
        String faction = requireText(factionStableId, "factionStableId");
        WalletComponent wallet = Objects.requireNonNull(procurementWallet, "procurementWallet");
        String ledgerName = requireText(procurementLedgerName, "procurementLedgerName");
        if (fundingMilliCredits <= 0L) {
            throw new IllegalArgumentException("fundingMilliCredits must be positive");
        }
        LogisticsState current = Objects.requireNonNull(logisticsState, "logisticsState");
        ProductionPlan checkedPlan = Objects.requireNonNull(plan, "plan");
        Stage18StationStorage checkedStorage = Objects.requireNonNull(storage, "storage");
        YardWorkBudget checkedBudget = Objects.requireNonNull(budget, "budget");
        if (!faction.equals(checkedPlan.stableFactionId())) {
            throw new IllegalArgumentException(
                    "replacement production plan belongs to another stable faction");
        }
        if (!checkedPlan.sourceStationId().equals(checkedStorage.stationId())) {
            throw new IllegalArgumentException(
                    "replacement production storage differs from planned physical station");
        }

        boolean funded = treasury.transfer(
                faction,
                wallet,
                ledgerName,
                fundingMilliCredits);
        if (!funded) {
            return new FundedBuildResult(
                    false,
                    0L,
                    current,
                    null);
        }

        BuildResult build = logistics.settleBuild(
                current,
                checkedPlan,
                checkedStorage,
                checkedBudget);
        return new FundedBuildResult(
                true,
                fundingMilliCredits,
                build.logisticsState(),
                build);
    }

    /**
     * Delegates final replacement arrival to the existing M22.8G physical delivery authority.
     *
     * @param logisticsState current G pending-delivery state
     * @param craftId freshly produced persistent identity
     * @param bay physical destination carrier/station bay
     * @param authoritativeTick current world tick
     * @return ordinary G delivery result; arrival enters servicing rather than free readiness
     */
    public DeliveryResult confirmReplacementDelivery(
            LogisticsState logisticsState,
            SmallCraftId craftId,
            BayDefinition bay,
            long authoritativeTick) {
        return logistics.confirmDelivery(
                Objects.requireNonNull(logisticsState, "logisticsState"),
                Objects.requireNonNull(craftId, "craftId"),
                Objects.requireNonNull(bay, "bay"),
                authoritativeTick);
    }

    /** One H-layer result retaining the ordinary G build result and conserved funding provenance. */
    public record FundedBuildResult(
            boolean treasuryFunded,
            long fundedMilliCredits,
            LogisticsState logisticsState,
            BuildResult build) {
        /** Validates one immutable recovery result. */
        public FundedBuildResult {
            Objects.requireNonNull(logisticsState, "logisticsState");
            if (fundedMilliCredits < 0L) {
                throw new IllegalArgumentException("fundedMilliCredits cannot be negative");
            }
            if (treasuryFunded != (fundedMilliCredits > 0L)) {
                throw new IllegalArgumentException(
                        "treasury funding flag must match positive conserved transfer");
            }
            if (!treasuryFunded && build != null) {
                throw new IllegalArgumentException(
                        "physical replacement build cannot run without treasury funding");
            }
        }

        /** @return whether Stage-18 settlement physically produced a fresh replacement craft */
        public boolean replacementProduced() {
            return build != null && build.producedCraftOptional().isPresent();
        }
    }

    @FunctionalInterface
    interface TreasuryFundingAuthority {
        boolean transfer(
                String factionStableId,
                WalletComponent destinationWallet,
                String destinationLedgerName,
                long amountMilliCredits);
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
