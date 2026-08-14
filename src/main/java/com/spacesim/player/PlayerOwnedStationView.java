package com.spacesim.player;

import com.spacesim.world.StarSystemId;

import java.util.Objects;

/**
 * Authoritative read-only management projection of one physical station owned by the player.
 *
 * @param reference persistent player ownership reference
 * @param stationArchetypeContentId physical station archetype content ID
 * @param stationDisplayName content display name
 * @param systemId physical station system
 * @param x current local-system X coordinate
 * @param y current local-system Y coordinate
 * @param walletMilliCredits current real station operating-wallet balance
 * @param legalFactionContentId optional current faction/legal affiliation of the physical station
 */
public record PlayerOwnedStationView(
        OwnedStationRef reference,
        String stationArchetypeContentId,
        String stationDisplayName,
        StarSystemId systemId,
        float x,
        float y,
        long walletMilliCredits,
        String legalFactionContentId) implements Comparable<PlayerOwnedStationView> {

    /**
     * Validates one immutable station management row.
     *
     * @param reference persistent ownership reference
     * @param stationArchetypeContentId physical station archetype ID
     * @param stationDisplayName station display name
     * @param systemId physical system
     * @param x local X
     * @param y local Y
     * @param walletMilliCredits non-negative station operating balance
     * @param legalFactionContentId optional faction/legal content ID
     */
    public PlayerOwnedStationView {
        Objects.requireNonNull(reference, "Owned station reference not set");
        stationArchetypeContentId = requireText(stationArchetypeContentId, "Owned station archetype ID not set");
        stationDisplayName = requireText(stationDisplayName, "Owned station display name not set");
        Objects.requireNonNull(systemId, "Owned station system not set");
        if (!reference.systemId().equals(systemId)
                || !Float.isFinite(x) || !Float.isFinite(y)
                || walletMilliCredits < 0L) {
            throw new IllegalArgumentException("Invalid owned station management values");
        }
        if (legalFactionContentId != null) {
            legalFactionContentId = requireText(legalFactionContentId, "Owned station faction ID cannot be blank");
        }
    }

    @Override
    public int compareTo(PlayerOwnedStationView other) {
        return reference.compareTo(Objects.requireNonNull(other, "Other station view not set").reference);
    }

    private static String requireText(String value, String message) {
        String checked = Objects.requireNonNull(value, message).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return checked;
    }
}
