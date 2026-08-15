package com.spacesim.world;

import java.util.Objects;

/**
 * Immutable Stage-17D read model describing one faction's territorial position in one star system.
 *
 * <p>The view deliberately separates physical presence from sovereignty. A faction may have ships
 * or stations in a system without controlling it; legacy persistent control remains the
 * authoritative sovereignty source until Stage 17D claim/stabilization state replaces it.</p>
 *
 * @param systemId star system being assessed
 * @param factionContentId stable faction content ID whose position is being assessed
 * @param jurisdiction derived territorial relationship between faction and system
 * @param physicalPresence whether at least one local physical ECS entity belongs to the faction
 * @param controllingFactionContentId stable controller ID, or {@code null} when the system is unclaimed
 */
public record FactionTerritoryView(
        StarSystemId systemId,
        String factionContentId,
        Jurisdiction jurisdiction,
        boolean physicalPresence,
        String controllingFactionContentId) {

    /** Derived Stage-17D jurisdiction states available before persistent claims are introduced. */
    public enum Jurisdiction {
        /** No controller and no physical presence by the assessed faction. */
        UNCLAIMED,
        /** No controller, but the assessed faction has physical presence. */
        PRESENT,
        /** The assessed faction is the persistent controller. */
        SELF_CONTROLLED,
        /** Another faction is the persistent controller. */
        FOREIGN_CONTROLLED
    }

    /**
     * Canonicalizes stable IDs and rejects internally inconsistent territory views.
     *
     * @param systemId star system being assessed
     * @param factionContentId stable assessed faction ID
     * @param jurisdiction derived jurisdiction
     * @param physicalPresence true when the faction has a local physical entity
     * @param controllingFactionContentId stable controller ID or {@code null}
     */
    public FactionTerritoryView {
        systemId = Objects.requireNonNull(systemId, "StarSystemId не задан");
        factionContentId = normalizedId(factionContentId, "Faction content ID не задан");
        jurisdiction = Objects.requireNonNull(jurisdiction, "Territory jurisdiction не задан");
        if (controllingFactionContentId != null) {
            controllingFactionContentId = normalizedId(
                    controllingFactionContentId,
                    "Controlling faction content ID не задан");
        }

        switch (jurisdiction) {
            case UNCLAIMED -> {
                if (physicalPresence || controllingFactionContentId != null) {
                    throw new IllegalArgumentException(
                            "UNCLAIMED требует отсутствия controller и physical presence");
                }
            }
            case PRESENT -> {
                if (!physicalPresence || controllingFactionContentId != null) {
                    throw new IllegalArgumentException(
                            "PRESENT требует physical presence без controller");
                }
            }
            case SELF_CONTROLLED -> {
                if (!factionContentId.equals(controllingFactionContentId)) {
                    throw new IllegalArgumentException(
                            "SELF_CONTROLLED требует controller assessed faction");
                }
            }
            case FOREIGN_CONTROLLED -> {
                if (controllingFactionContentId == null
                        || factionContentId.equals(controllingFactionContentId)) {
                    throw new IllegalArgumentException(
                            "FOREIGN_CONTROLLED требует controller другой faction");
                }
            }
        }
    }

    /** @return true when any faction controls the assessed system */
    public boolean controlled() {
        return controllingFactionContentId != null;
    }

    /** @return true when the assessed faction itself controls the system */
    public boolean controlledByFaction() {
        return jurisdiction == Jurisdiction.SELF_CONTROLLED;
    }

    private static String normalizedId(String value, String missingMessage) {
        String normalized = Objects.requireNonNull(value, missingMessage).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Faction content ID не может быть пустым");
        }
        return normalized;
    }
}
