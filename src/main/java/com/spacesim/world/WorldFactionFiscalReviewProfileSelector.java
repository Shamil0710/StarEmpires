package com.spacesim.world;

import java.util.Objects;

/** Read-only world adapter for the pure Stage-17F.6 doctrine-to-fiscal-profile selector. */
public final class WorldFactionFiscalReviewProfileSelector {
    private WorldFactionFiscalReviewProfileSelector() {
        throw new AssertionError("Utility class");
    }

    /**
     * Derives one faction's fiscal review profile from its persistent doctrine and structural reserve scale.
     *
     * <p>The adapter is intentionally read-only. Current liquidity shortfall is not fed into profile
     * selection; it remains the live signal owned by {@link FactionFiscalPolicyReviewer}.</p>
     *
     * @param world authoritative world runtime
     * @param factionContentId stable faction content ID
     * @return deterministic fiscal review profile
     */
    public static FactionFiscalReviewProfile select(
            WorldSimulation world,
            String factionContentId) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "WorldSimulation not set");
        String factionId = Objects.requireNonNull(factionContentId, "Faction content ID not set").strip();
        if (factionId.isEmpty()) {
            throw new IllegalArgumentException("Faction content ID cannot be blank");
        }
        FactionDoctrineState doctrine = checkedWorld.findFactionStrategicState(factionId)
                .orElseThrow(() -> new IllegalArgumentException("Faction has no strategic state: " + factionId))
                .doctrine();
        long reserveTarget = FactionFiscalPositionAnalyzer.analyze(checkedWorld, factionId)
                .liquidityReserveTargetMilliCredits();
        return FactionFiscalReviewProfileSelector.select(doctrine, reserveTarget);
    }
}
