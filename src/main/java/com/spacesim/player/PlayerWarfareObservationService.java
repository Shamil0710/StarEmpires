package com.spacesim.player;

import com.spacesim.world.PhysicalWarfareOperation;
import com.spacesim.world.PhysicalWarfareOperationService;

import java.util.Objects;

/**
 * Explicit actor-observation boundary from physical Stage-19E operations to player threat intel.
 *
 * <p>The service never scans for warfare operations. A caller must supply an operation it has
 * actually observed plus the resulting non-negative danger assessment and confidence. Physical
 * anchoring is revalidated first, then the observation is written through the existing
 * {@link PlayerThreatIntelService}. Merely creating or maintaining an unobserved operation does not
 * mutate the player's knowledge or route planning.</p>
 */
public final class PlayerWarfareObservationService {
    private final PhysicalWarfareOperationService operations;
    private final PlayerThreatIntelService threatIntel;

    /**
     * Creates the observation bridge for one playable runtime.
     *
     * @param runtime current player/world runtime
     */
    public PlayerWarfareObservationService(PlayerRuntime runtime) {
        PlayerRuntime checked = Objects.requireNonNull(runtime, "runtime");
        this.operations = new PhysicalWarfareOperationService(checked.world());
        this.threatIntel = new PlayerThreatIntelService(checked);
    }

    /**
     * Records one explicitly observed, currently physical warfare operation.
     *
     * <p>Raid and blockade observations become ordinary system danger. Interdiction becomes
     * ordinary link danger. Existing discovery/topology validation, replacement ordering and
     * persistence remain owned by {@link PlayerThreatIntelService}.</p>
     *
     * @param operation operation actually observed by the player information domain
     * @param dangerScore non-negative observed exposure score, not a probability or combat-power stat
     * @param confidence observation confidence in {@code [0,1]}
     * @param observedTick authoritative tick of the observation
     * @return true when the operation is physically active and ordinary threat intel accepted it
     */
    public boolean observe(
            PhysicalWarfareOperation operation,
            float dangerScore,
            float confidence,
            long observedTick) {
        PhysicalWarfareOperation checked = Objects.requireNonNull(operation, "operation");
        if (!operations.isPhysicallyActive(checked)) {
            return false;
        }
        return switch (checked.type()) {
            case RAID, BLOCKADE -> threatIntel.observeSystem(
                    checked.systemA(), dangerScore, confidence, observedTick);
            case INTERDICTION -> threatIntel.observeLink(
                    checked.systemA(), checked.systemB(), dangerScore, confidence, observedTick);
        };
    }
}
