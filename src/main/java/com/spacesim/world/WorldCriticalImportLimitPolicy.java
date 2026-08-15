package com.spacesim.world;

import com.spacesim.content.ContentCatalog;
import com.spacesim.trade.CriticalImportLimitPolicy;
import com.spacesim.trade.FleetTradeProfile;

import java.util.Objects;

/**
 * World-backed Stage-17F.5 hard critical-import policy for autonomous faction procurement.
 *
 * <p>The adapter uses only current structural dependence diagnostics and the existing resilience plan.
 * A commodity becomes hard-limited only when the same plan already recommends local production because
 * losing a foreign partner would leave real uncovered requirements. The maximum accepted supplier share
 * is the doctrine-derived concentration ceiling already used by resilience planning.</p>
 */
public final class WorldCriticalImportLimitPolicy implements CriticalImportLimitPolicy {
    private final WorldSimulation world;

    /**
     * Creates the live policy adapter for one authoritative world.
     *
     * @param world authoritative world runtime
     */
    public WorldCriticalImportLimitPolicy(WorldSimulation world) {
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
        if (sourceFaction == null || supplierFaction == null || sourceFaction.equals(supplierFaction)) {
            return Assessment.inactive();
        }
        String itemContentId = itemContentId(itemId);
        if (itemContentId == null) {
            return Assessment.inactive();
        }

        FactionResilienceItemDecision decision = FactionResiliencePlanner.analyze(world, sourceFaction)
                .items().stream()
                .filter(item -> item.itemContentId().equals(itemContentId))
                .findFirst()
                .orElse(null);
        if (decision == null || !decision.localProductionRecommended()) {
            return Assessment.inactive();
        }

        int supplierShare = partnerShareBasisPoints(sourceFaction, supplierFaction, itemContentId);
        return new Assessment(
                true,
                supplierShare,
                decision.preferredMaximumPartnerShareBasisPoints());
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
}
