package com.spacesim.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Persistent bilateral treaty record stored once in the directory of the faction that originated it.
 *
 * @param treatyId stable treaty identity unique in the world
 * @param counterpartyFactionContentId second treaty party
 * @param status lifecycle state
 * @param createdTick authoritative creation tick
 * @param effectiveTick first effective tick, or -1 before activation
 * @param expiresTick exclusive expiry tick or -1 for indefinite
 * @param clauses explicit rights/obligations
 */
public record DiplomaticTreatyState(
        String treatyId,
        String counterpartyFactionContentId,
        Status status,
        long createdTick,
        long effectiveTick,
        long expiresTick,
        List<DiplomaticTreatyClauseState> clauses) implements Comparable<DiplomaticTreatyState> {

    /** Lifecycle states used by the Stage-17E proposal/response engine. */
    public enum Status {
        /** Offered but not yet accepted. */
        PROPOSED,
        /** In force. */
        ACTIVE,
        /** Still in force during a notice/termination interval. */
        TERMINATING,
        /** Obligation was violated and the treaty is no longer considered normally fulfilled. */
        BREACHED,
        /** Treaty reached its normal expiry. */
        EXPIRED,
        /** Proposal was rejected before activation. */
        REJECTED
    }

    /** Validates timing and canonical clause ordering. */
    public DiplomaticTreatyState {
        treatyId = requireId(treatyId, "Treaty ID");
        counterpartyFactionContentId = requireId(counterpartyFactionContentId, "Treaty counterparty faction ID");
        status = Objects.requireNonNull(status, "Treaty status not set");
        if (createdTick < 0L) {
            throw new IllegalArgumentException("Treaty creation tick cannot be negative");
        }
        if (effectiveTick < -1L || (effectiveTick >= 0L && effectiveTick < createdTick)) {
            throw new IllegalArgumentException("Treaty effective tick is invalid");
        }
        if ((status == Status.ACTIVE || status == Status.TERMINATING || status == Status.BREACHED
                || status == Status.EXPIRED) && effectiveTick < 0L) {
            throw new IllegalArgumentException("Activated treaty state requires an effective tick");
        }
        if ((status == Status.PROPOSED || status == Status.REJECTED) && effectiveTick != -1L) {
            throw new IllegalArgumentException("Unactivated treaty state cannot have an effective tick");
        }
        long expiryFloor = effectiveTick >= 0L ? effectiveTick : createdTick;
        if (expiresTick != -1L && expiresTick <= expiryFloor) {
            throw new IllegalArgumentException("Treaty expiry must follow its effective/creation tick or be -1");
        }
        Objects.requireNonNull(clauses, "Treaty clauses not set");
        if (clauses.isEmpty()) {
            throw new IllegalArgumentException("Treaty must contain at least one explicit clause");
        }
        List<DiplomaticTreatyClauseState> sorted = new ArrayList<>(clauses.size());
        Set<DiplomaticTreatyClauseState> unique = new HashSet<>();
        for (DiplomaticTreatyClauseState clause : clauses) {
            DiplomaticTreatyClauseState value = Objects.requireNonNull(clause, "Treaty clause not set");
            if (!unique.add(value)) {
                throw new IllegalArgumentException("Duplicate treaty clause: " + value);
            }
            sorted.add(value);
        }
        sorted.sort(null);
        clauses = List.copyOf(sorted);
    }

    /** @return true while the treaty remains legally in force at the supplied tick */
    public boolean activeAt(long worldTick) {
        return (status == Status.ACTIVE || status == Status.TERMINATING)
                && worldTick >= effectiveTick
                && (expiresTick < 0L || worldTick < expiresTick);
    }

    /** @return true when this treaty contains at least one market-access clause */
    public boolean containsMarketAccessClause() {
        return clauses.stream().anyMatch(clause -> clause.kind() == DiplomaticTreatyClauseState.Kind.MARKET_ACCESS);
    }

    @Override
    public int compareTo(DiplomaticTreatyState other) {
        return treatyId.compareTo(Objects.requireNonNull(other, "DiplomaticTreatyState not set").treatyId);
    }

    private static String requireId(String value, String label) {
        String normalized = Objects.requireNonNull(value, label + " not set").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return normalized;
    }
}
