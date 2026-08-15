package com.spacesim.world;

import com.spacesim.content.ContentCatalog;
import com.spacesim.trade.FleetTradeProfile;
import com.spacesim.trade.RouteRedundancyPolicy;

import java.util.Objects;

/**
 * World-backed Stage-17F.5 route-redundancy preference for ordinary inter-system procurement.
 *
 * <p>The adapter only consumes existing doctrine and Stage-17E/17F resilience diagnostics. It does
 * not create topology or route capacity. When a commodity is exposed to a unique corridor and policy
 * recommends redundancy, the maximum accepted real expected-profit sacrifice is the same
 * doctrine-weighted replacement premium used as the resilience willingness-to-pay signal.</p>
 */
public final class WorldRouteRedundancyPolicy implements RouteRedundancyPolicy {
    private final WorldSimulation world;

    /**
     * Creates the live route-redundancy policy adapter.
     *
     * @param world authoritative world runtime
     */
    public WorldRouteRedundancyPolicy(WorldSimulation world) {
        this.world = Objects.requireNonNull(world, "WorldSimulation not set");
    }

    @Override
    public Assessment assess(FleetTradeProfile fleet, int itemId) {
        FleetTradeProfile checkedFleet = Objects.requireNonNull(fleet, "FleetTradeProfile not set");
        if (checkedFleet.factionId() < 0 || itemId < 0) {
            return Assessment.inactive();
        }
        String factionId = world.findFactionStableId(checkedFleet.factionId()).orElse(null);
        if (factionId == null || world.findFactionStrategicState(factionId).isEmpty()) {
            return Assessment.inactive();
        }
        String itemContentId = itemContentId(itemId);
        if (itemContentId == null) {
            return Assessment.inactive();
        }
        FactionResiliencePlan plan = FactionResiliencePlanner.analyze(world, factionId);
        FactionResilienceItemDecision decision = plan.items().stream()
                .filter(item -> item.itemContentId().equals(itemContentId))
                .findFirst()
                .orElse(null);
        if (decision == null || !decision.routeRedundancyRecommended()) {
            return Assessment.inactive();
        }
        return new Assessment(
                true,
                percentageCeil(
                        decision.worstReplacementPremiumMilliCredits(),
                        plan.economicResiliencePriority()));
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
