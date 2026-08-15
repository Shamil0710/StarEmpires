package com.spacesim.world;

import java.util.Objects;

/**
 * Common player/AI command boundary for unilateral market-access embargoes.
 *
 * <p>An embargo changes legal access only. It never deletes cargo, changes prices directly, drains a
 * wallet or creates abstract economic damage.</p>
 */
public sealed interface DiplomaticEmbargoCommand permits
        DiplomaticEmbargoCommand.Impose,
        DiplomaticEmbargoCommand.Revoke {

    /** @return stable faction ID issuing the embargo command */
    String actorFactionContentId();

    /**
     * Imposes or re-imposes a market-access embargo against another faction.
     *
     * @param actorFactionContentId faction imposing the embargo
     * @param targetFactionContentId faction whose ordinary market access is prohibited
     * @param expiresTick exclusive expiry tick or -1 for indefinite
     * @param reasonKey stable political/diagnostic reason key, possibly empty
     */
    record Impose(
            String actorFactionContentId,
            String targetFactionContentId,
            long expiresTick,
            String reasonKey) implements DiplomaticEmbargoCommand {
        /**
         * Validates immutable embargo input.
         *
         * @param actorFactionContentId faction imposing the embargo
         * @param targetFactionContentId sanctioned faction
         * @param expiresTick exclusive expiry tick or -1 for indefinite
         * @param reasonKey stable political/diagnostic reason key, possibly empty
         */
        public Impose {
            actorFactionContentId = requireId(actorFactionContentId, "Embargo actor faction ID");
            targetFactionContentId = requireId(targetFactionContentId, "Embargo target faction ID");
            if (actorFactionContentId.equals(targetFactionContentId)) {
                throw new IllegalArgumentException("Faction cannot embargo itself");
            }
            if (expiresTick < -1L) {
                throw new IllegalArgumentException("Embargo expiry must be -1 or non-negative");
            }
            reasonKey = Objects.requireNonNull(reasonKey, "Embargo reason key not set").strip();
        }
    }

    /**
     * Revokes the actor's current market-access embargo against a target faction.
     *
     * @param actorFactionContentId faction lifting the embargo
     * @param targetFactionContentId faction whose access prohibition is removed
     */
    record Revoke(
            String actorFactionContentId,
            String targetFactionContentId) implements DiplomaticEmbargoCommand {
        /**
         * Validates immutable revocation input.
         *
         * @param actorFactionContentId faction lifting the embargo
         * @param targetFactionContentId sanctioned faction
         */
        public Revoke {
            actorFactionContentId = requireId(actorFactionContentId, "Embargo revocation actor faction ID");
            targetFactionContentId = requireId(targetFactionContentId, "Embargo revocation target faction ID");
            if (actorFactionContentId.equals(targetFactionContentId)) {
                throw new IllegalArgumentException("Faction cannot revoke a self-embargo");
            }
        }
    }

    private static String requireId(String value, String label) {
        String normalized = Objects.requireNonNull(value, label + " not set").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return normalized;
    }
}
