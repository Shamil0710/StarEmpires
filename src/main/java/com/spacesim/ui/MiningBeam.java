package com.spacesim.ui;

import java.util.Objects;

/**
 * One active personal mining order of the player, as the system map shows it: a beam from the
 * ship to the contacted source.
 *
 * <p>The order exists only while extraction succeeds; the campaign removes it on the first tick
 * that cannot extract. {@code lastProcessedTick} is the order's authoritative watermark: it is the
 * assignment tick right after the start and then the last tick that extracted, so each advance of
 * it is one completed extraction.</p>
 *
 * @param shipStableId system-map id of the mining ship ({@code fleet:<id>})
 * @param sourceStableId system-map id of the contacted source ({@code resource:<id>})
 * @param lastProcessedTick authoritative tick watermark of the order
 */
public record MiningBeam(String shipStableId, String sourceStableId, long lastProcessedTick) {
    /**
     * Validates one beam.
     *
     * @param shipStableId system-map id of the mining ship
     * @param sourceStableId system-map id of the contacted source
     * @param lastProcessedTick non-negative tick watermark
     */
    public MiningBeam {
        Objects.requireNonNull(shipStableId, "shipStableId");
        Objects.requireNonNull(sourceStableId, "sourceStableId");
        if (lastProcessedTick < 0) throw new IllegalArgumentException("negative mining tick");
    }

    /** @return identity of the ship-source pair, stable while the order lasts */
    String key() {
        return shipStableId + '|' + sourceStableId;
    }
}
