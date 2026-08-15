package com.spacesim.world;

import java.util.List;
import java.util.Objects;

/**
 * Common Stage-17E treaty command boundary used by player/UI and AI callers.
 *
 * <p>Commands express institutional intent only. They do not create money, cargo, infrastructure or
 * economic damage. Legal/economic consequences are projected by the ordinary systems after the
 * persistent treaty transition succeeds.</p>
 */
public sealed interface DiplomaticTreatyCommand permits
        DiplomaticTreatyCommand.Offer,
        DiplomaticTreatyCommand.CounterOffer,
        DiplomaticTreatyCommand.Accept,
        DiplomaticTreatyCommand.Reject,
        DiplomaticTreatyCommand.TerminateWithNotice,
        DiplomaticTreatyCommand.Breach,
        DiplomaticTreatyCommand.Renew {

    /** @return stable faction ID of the actor issuing the command */
    String actorFactionContentId();

    /**
     * Offers a new bilateral treaty.
     *
     * @param actorFactionContentId proposing faction
     * @param counterpartyFactionContentId receiving faction
     * @param clauses explicit proposed rights/obligations
     * @param expiresTick treaty expiry after acceptance, or -1 for indefinite
     */
    record Offer(
            String actorFactionContentId,
            String counterpartyFactionContentId,
            List<DiplomaticTreatyClauseState> clauses,
            long expiresTick) implements DiplomaticTreatyCommand {
        /** Validates immutable offer input. */
        public Offer {
            actorFactionContentId = requireId(actorFactionContentId, "Treaty proposer faction ID");
            counterpartyFactionContentId = requireId(counterpartyFactionContentId, "Treaty counterparty faction ID");
            if (actorFactionContentId.equals(counterpartyFactionContentId)) {
                throw new IllegalArgumentException("Faction cannot offer a treaty to itself");
            }
            clauses = canonicalClauses(clauses);
            requireExpiry(expiresTick);
        }
    }

    /**
     * Rejects an incoming proposal and replaces it with a new proposal from the responding faction.
     *
     * @param actorFactionContentId responding faction
     * @param treatyId incoming proposed treaty ID
     * @param clauses replacement proposed rights/obligations
     * @param expiresTick replacement treaty expiry after acceptance, or -1 for indefinite
     */
    record CounterOffer(
            String actorFactionContentId,
            String treatyId,
            List<DiplomaticTreatyClauseState> clauses,
            long expiresTick) implements DiplomaticTreatyCommand {
        /** Validates immutable counteroffer input. */
        public CounterOffer {
            actorFactionContentId = requireId(actorFactionContentId, "Counteroffer actor faction ID");
            treatyId = requireId(treatyId, "Countered treaty ID");
            clauses = canonicalClauses(clauses);
            requireExpiry(expiresTick);
        }
    }

    /**
     * Accepts an incoming proposed treaty and activates it at the authoritative world tick.
     *
     * @param actorFactionContentId accepting faction
     * @param treatyId proposed treaty ID
     */
    record Accept(String actorFactionContentId, String treatyId) implements DiplomaticTreatyCommand {
        /** Validates immutable acceptance input. */
        public Accept {
            actorFactionContentId = requireId(actorFactionContentId, "Treaty acceptance actor faction ID");
            treatyId = requireId(treatyId, "Accepted treaty ID");
        }
    }

    /**
     * Rejects an incoming proposed treaty.
     *
     * @param actorFactionContentId rejecting faction
     * @param treatyId proposed treaty ID
     */
    record Reject(String actorFactionContentId, String treatyId) implements DiplomaticTreatyCommand {
        /** Validates immutable rejection input. */
        public Reject {
            actorFactionContentId = requireId(actorFactionContentId, "Treaty rejection actor faction ID");
            treatyId = requireId(treatyId, "Rejected treaty ID");
        }
    }

    /**
     * Starts treaty termination while keeping obligations in force through a notice interval.
     *
     * @param actorFactionContentId terminating treaty party
     * @param treatyId active treaty ID
     * @param noticeTicks strictly positive notice duration in authoritative ticks
     */
    record TerminateWithNotice(
            String actorFactionContentId,
            String treatyId,
            long noticeTicks) implements DiplomaticTreatyCommand {
        /** Validates immutable termination input. */
        public TerminateWithNotice {
            actorFactionContentId = requireId(actorFactionContentId, "Treaty termination actor faction ID");
            treatyId = requireId(treatyId, "Terminated treaty ID");
            if (noticeTicks <= 0L) {
                throw new IllegalArgumentException("Treaty termination notice must be positive");
            }
        }
    }

    /**
     * Records an explicit treaty breach by one treaty party.
     *
     * @param actorFactionContentId breaching faction
     * @param treatyId active treaty ID
     * @param reasonKey stable diagnostic reason key, possibly empty
     */
    record Breach(
            String actorFactionContentId,
            String treatyId,
            String reasonKey) implements DiplomaticTreatyCommand {
        /** Validates immutable breach input. */
        public Breach {
            actorFactionContentId = requireId(actorFactionContentId, "Treaty breach actor faction ID");
            treatyId = requireId(treatyId, "Breached treaty ID");
            reasonKey = Objects.requireNonNull(reasonKey, "Treaty breach reason key not set").strip();
        }
    }

    /**
     * Proposes a fresh treaty with the same clauses as an existing treaty.
     *
     * <p>Renewal is deliberately consensual: this command creates a normal {@link Offer}; the other
     * party must still accept it. The existing treaty remains independently in force until its own
     * lifecycle ends.</p>
     *
     * @param actorFactionContentId party proposing renewal
     * @param treatyId existing treaty ID whose clauses are copied
     * @param expiresTick new treaty expiry after acceptance, or -1 for indefinite
     */
    record Renew(
            String actorFactionContentId,
            String treatyId,
            long expiresTick) implements DiplomaticTreatyCommand {
        /** Validates immutable renewal input. */
        public Renew {
            actorFactionContentId = requireId(actorFactionContentId, "Treaty renewal actor faction ID");
            treatyId = requireId(treatyId, "Renewed treaty ID");
            requireExpiry(expiresTick);
        }
    }

    private static List<DiplomaticTreatyClauseState> canonicalClauses(
            List<DiplomaticTreatyClauseState> source) {
        Objects.requireNonNull(source, "Treaty command clauses not set");
        if (source.isEmpty()) {
            throw new IllegalArgumentException("Treaty command must contain at least one clause");
        }
        return List.copyOf(source);
    }

    private static void requireExpiry(long expiresTick) {
        if (expiresTick < -1L) {
            throw new IllegalArgumentException("Treaty expiry must be -1 or non-negative");
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
