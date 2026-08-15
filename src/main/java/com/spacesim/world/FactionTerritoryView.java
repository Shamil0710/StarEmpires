package com.spacesim.world;

import java.util.Objects;

/**
 * Immutable Stage-17D read model describing one faction's territorial position in one star system.
 *
 * <p>The view separates physical presence, political claim progress, diplomatic recognition and
 * established sovereignty. No one dimension silently implies another.</p>
 *
 * @param systemId star system being assessed
 * @param factionContentId stable faction content ID whose position is being assessed
 * @param jurisdiction derived territorial relationship between faction and system
 * @param physicalPresence whether at least one local physical ECS entity belongs to the faction
 * @param controllingFactionContentId stable controller ID, or {@code null} when the system is unclaimed
 * @param claimedByFaction whether the assessed faction has an explicit political claim
 * @param claimStatus current persistent claim status, or {@code null} without a claim
 * @param stabilizationTicks accumulated qualifying claim stabilization time
 * @param contested whether this assessed claim is currently materially contested
 * @param recognitionCount number of other factions recognizing the relevant claim/control position
 */
public record FactionTerritoryView(
        StarSystemId systemId,
        String factionContentId,
        Jurisdiction jurisdiction,
        boolean physicalPresence,
        String controllingFactionContentId,
        boolean claimedByFaction,
        TerritorialClaimState.Status claimStatus,
        long stabilizationTicks,
        boolean contested,
        int recognitionCount) {

    /** Derived Stage-17D jurisdiction state for the assessed faction/system pair. */
    public enum Jurisdiction {
        /** No controller, claim or physical presence by the assessed faction. */
        UNCLAIMED,
        /** No controller/claim, but the assessed faction has physical presence. */
        PRESENT,
        /** Explicit claim exists but currently lacks qualifying stabilization evidence. */
        CLAIMED,
        /** Explicit uncontested claim is accumulating qualifying stabilization time. */
        STABILIZING,
        /** Explicit claim currently faces incompatible material territorial evidence. */
        CONTESTED,
        /** The assessed faction is the persistent controller. */
        SELF_CONTROLLED,
        /** Another faction is the persistent controller. */
        FOREIGN_CONTROLLED
    }

    /**
     * Source-compatible Stage-17D.1a constructor for a view without claim diagnostics.
     *
     * @param systemId assessed system
     * @param factionContentId assessed faction
     * @param jurisdiction derived jurisdiction
     * @param physicalPresence physical presence flag
     * @param controllingFactionContentId controller or {@code null}
     */
    public FactionTerritoryView(
            StarSystemId systemId,
            String factionContentId,
            Jurisdiction jurisdiction,
            boolean physicalPresence,
            String controllingFactionContentId) {
        this(
                systemId,
                factionContentId,
                jurisdiction,
                physicalPresence,
                controllingFactionContentId,
                false,
                null,
                0L,
                false,
                0);
    }

    /**
     * Canonicalizes stable IDs and rejects internally inconsistent territory views.
     *
     * @param systemId star system being assessed
     * @param factionContentId stable assessed faction ID
     * @param jurisdiction derived jurisdiction
     * @param physicalPresence true when the faction has a local physical entity
     * @param controllingFactionContentId stable controller ID or {@code null}
     * @param claimedByFaction explicit-claim flag
     * @param claimStatus claim status or {@code null}
     * @param stabilizationTicks non-negative accumulated stabilization
     * @param contested material-contest flag
     * @param recognitionCount non-negative relevant directed recognitions
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
        if (stabilizationTicks < 0L || recognitionCount < 0) {
            throw new IllegalArgumentException("Territorial diagnostics cannot be negative");
        }
        if (claimedByFaction != (claimStatus != null)) {
            throw new IllegalArgumentException("Claim flag and claim status must agree");
        }
        if (!claimedByFaction && (stabilizationTicks != 0L || contested)) {
            throw new IllegalArgumentException("Claim diagnostics require an explicit claim");
        }
        if (contested != (claimStatus == TerritorialClaimState.Status.CONTESTED)) {
            throw new IllegalArgumentException("Contested flag must match claim status");
        }

        switch (jurisdiction) {
            case UNCLAIMED -> {
                if (physicalPresence || controllingFactionContentId != null || claimedByFaction) {
                    throw new IllegalArgumentException(
                            "UNCLAIMED requires no controller, claim or assessed physical presence");
                }
            }
            case PRESENT -> {
                if (!physicalPresence || controllingFactionContentId != null || claimedByFaction) {
                    throw new IllegalArgumentException(
                            "PRESENT requires physical presence without controller or claim");
                }
            }
            case CLAIMED -> {
                if (!claimedByFaction || controllingFactionContentId != null
                        || claimStatus != TerritorialClaimState.Status.ACTIVE) {
                    throw new IllegalArgumentException("CLAIMED requires an ACTIVE claim in an uncontrolled system");
                }
            }
            case STABILIZING -> {
                if (!claimedByFaction || controllingFactionContentId != null
                        || claimStatus != TerritorialClaimState.Status.STABILIZING) {
                    throw new IllegalArgumentException(
                            "STABILIZING requires a stabilizing claim in an uncontrolled system");
                }
            }
            case CONTESTED -> {
                if (!claimedByFaction || claimStatus != TerritorialClaimState.Status.CONTESTED) {
                    throw new IllegalArgumentException("CONTESTED requires a contested explicit claim");
                }
            }
            case SELF_CONTROLLED -> {
                if (!factionContentId.equals(controllingFactionContentId)) {
                    throw new IllegalArgumentException(
                            "SELF_CONTROLLED requires controller assessed faction");
                }
            }
            case FOREIGN_CONTROLLED -> {
                if (controllingFactionContentId == null
                        || factionContentId.equals(controllingFactionContentId)) {
                    throw new IllegalArgumentException(
                            "FOREIGN_CONTROLLED requires controller another faction");
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
