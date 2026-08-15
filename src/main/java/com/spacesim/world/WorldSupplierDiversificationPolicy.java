package com.spacesim.world;

import com.spacesim.content.ContentCatalog;
import com.spacesim.trade.FleetTradeProfile;
import com.spacesim.trade.SupplierDiversificationPolicy;

import java.util.Objects;

/**
 * World-backed Stage-17F.5 supplier-diversification policy for ordinary trade planning.
 *
 * <p>The adapter consumes only already-authoritative doctrine and Stage-17E structural-dependence
 * diagnostics. It does not modify market prices or wallets. The accepted resilience spending is a
 * doctrine-weighted fraction of the currently measured replacement premium; if no premium/risk is
 * measurable, the policy cannot justify sacrificing positive expected profit.</p>
 */
public final class WorldSupplierDiversificationPolicy implements SupplierDiversificationPolicy {
    private final WorldSimulation world;

    /**
     * Creates the live policy adapter for one world runtime.
     *
     * @param world authoritative world runtime
     */
    public WorldSupplierDiversificationPolicy(WorldSimulation world) {
        this.world = Objects.requireNonNull(world, "WorldSimulation not set");
    }

    @Override
    public Assessment assess(FleetTradeProfile fleet, int supplierFactionId, int itemId) {
        FleetTradeProfile checkedFleet = Objects.requireNonNull(fleet, "FleetTradeProfile not set");
        if (checkedFleet.factionId() < 0 || supplierFactionId < 0 || itemId < 0) {
            return Assessment.inactive();
        }
        String sourceFaction = world.findFactionStableId(checkedFleet.factionId()).orElse(null);
        String supplierFaction = world.findFactionStableId(supplierFactionId).orElse(null);
        if (sourceFaction == null
                || supplierFaction == null
                || world.findFactionStrategicState(sourceFaction).isEmpty()) {
            return Assessment.inactive();
        }
        String itemContentId = itemContentId(itemId);
        if (itemContentId == null) {
            return Assessment.inactive();
        }

        FactionResiliencePlan plan = FactionResiliencePlanner.analyze(world, sourceFaction);
        FactionResilienceItemDecision decision = plan.items().stream()
                .filter(item -> item.itemContentId().equals(itemContentId))
                .findFirst()
                .orElse(null);
        if (decision == null || !decision.diversifySuppliersRecommended()) {
            return Assessment.inactive();
        }

        long acceptedSacrifice = percentageCeil(
                decision.worstReplacementPremiumMilliCredits(),
                plan.economicResiliencePriority());
        int supplierShare = sourceFaction.equals(supplierFaction)
                ? 0
                : partnerShareBasisPoints(sourceFaction, supplierFaction, itemContentId);
        return new Assessment(true, supplierShare, acceptedSacrifice);
    }

    private int partnerShareBasisPoints(
            String sourceFaction,
            String supplierFaction,
            String itemContentId) {
        FactionEconomicDependenceDiagnostics diagnostics = world.analyzeEconomicDependence(
                sourceFaction, supplierFaction);
        for (FactionItemDependenceDiagnostic item : diagnostics.items()) {
            if (item.itemContentId().equals(itemContentId)) {
                return item.partnerSupplyShareBasisPoints();
            }
        }
        return 10_000;
    }

    private String itemContentId(int runtimeItemId) {
        ContentCatalog content = world.findSession(world.getActiveSystemId())
                .orElseThrow(() -> new IllegalStateException("Active world session not found"))
                .getContentCatalog();
        for (ContentCatalog.ItemDefinition item : content.getItems()) {
            if (item.runtimeId() == runtimeItemId) {
                return item.id();
            }
        }
        return null;
    }

    private static long percentageCeil(long value, int percent) {
        if (value <= 0L || percent <= 0) {
            return 0L;
        }
        long whole = value / 100L;
        long remainder = value % 100L;
        long scaledWhole = Math.multiplyExact(whole, (long) percent);
        long scaledRemainder = (remainder * percent + 99L) / 100L;
        return Math.addExact(scaledWhole, scaledRemainder);
    }
}
