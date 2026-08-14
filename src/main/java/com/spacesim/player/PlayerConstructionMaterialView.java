package com.spacesim.player;

import java.util.Objects;

/**
 * Read-only material fulfillment row for one player construction project.
 *
 * @param itemContentId stable material content ID
 * @param displayName localized/display material name
 * @param requiredUnits total project requirement
 * @param deliveredUnits real units already present/credited at the construction site
 * @param missingUnits remaining real units required before assembly can start
 */
public record PlayerConstructionMaterialView(
        String itemContentId,
        String displayName,
        int requiredUnits,
        int deliveredUnits,
        int missingUnits) implements Comparable<PlayerConstructionMaterialView> {

    /**
     * Validates one immutable material row.
     *
     * @param itemContentId stable material content ID
     * @param displayName display material name
     * @param requiredUnits positive total requirement
     * @param deliveredUnits delivered units in range [0, required]
     * @param missingUnits required minus delivered
     */
    public PlayerConstructionMaterialView {
        itemContentId = requireText(itemContentId, "Construction material content ID not set");
        displayName = requireText(displayName, "Construction material display name not set");
        if (requiredUnits <= 0 || deliveredUnits < 0 || deliveredUnits > requiredUnits
                || missingUnits != requiredUnits - deliveredUnits) {
            throw new IllegalArgumentException("Invalid construction material fulfillment row");
        }
    }

    @Override
    public int compareTo(PlayerConstructionMaterialView other) {
        return itemContentId.compareTo(Objects.requireNonNull(other, "Other material view not set").itemContentId);
    }

    private static String requireText(String value, String message) {
        String checked = Objects.requireNonNull(value, message).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return checked;
    }
}
