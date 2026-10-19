package com.spacesim.ui;

import java.util.Objects;

/**
 * One personal mining contact of the player, as the system map shows it: a beam from the ship to
 * the contacted source while the order is active, and the extraction sparks.
 *
 * <p>Both facts are authoritative. {@code active} is the existence of the mining order, which the
 * campaign removes on the first tick that cannot extract (or after a tick that depletes the
 * source). {@code lastExtractionTick} is the tick of the newest extraction receipt aboard the ship
 * for this source; it survives the order's removal, so the final extraction of a depleted source
 * still counts, and a stop and restart of the order is not an extraction.</p>
 *
 * @param shipStableId system-map id of the mining ship ({@code fleet:<id>})
 * @param sourceStableId system-map id of the contacted source ({@code resource:<id>})
 * @param active whether the mining order exists
 * @param lastExtractionTick tick of the newest extraction receipt, or -1 without one
 */
public record MiningBeam(String shipStableId, String sourceStableId, boolean active, long lastExtractionTick) {
    /**
     * Validates one beam.
     *
     * @param shipStableId system-map id of the mining ship
     * @param sourceStableId system-map id of the contacted source
     * @param active whether the mining order exists
     * @param lastExtractionTick tick of the newest extraction receipt, or -1 without one
     */
    public MiningBeam {
        Objects.requireNonNull(shipStableId, "shipStableId");
        Objects.requireNonNull(sourceStableId, "sourceStableId");
        if (lastExtractionTick < -1) throw new IllegalArgumentException("invalid extraction tick");
    }

    /** @return identity of the ship-source pair */
    String key() {
        return shipStableId + '|' + sourceStableId;
    }
}
