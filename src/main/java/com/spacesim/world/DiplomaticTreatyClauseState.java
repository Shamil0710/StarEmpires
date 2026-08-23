package com.spacesim.world;

import java.util.Objects;

/**
 * One explicit treaty obligation/right. Direction is relative to the faction that owns the treaty
 * directory entry and its counterparty.
 *
 * @param kind semantic clause kind
 * @param direction grant/obligation direction relative to treaty owner and counterparty
 * @param systemId optional territorial scope; required only for construction rights
 */
public record DiplomaticTreatyClauseState(
        Kind kind,
        Direction direction,
        StarSystemId systemId) implements Comparable<DiplomaticTreatyClauseState> {

    /** Clause kinds admitted by the Stage-17 institutional diplomacy contract. */
    public enum Kind {
        /** Explicit legal access to ordinary faction markets. */
        MARKET_ACCESS,
        /** Explicit construction/basing right in one scoped StarSystem. */
        CONSTRUCTION_RIGHT,
        /** Strategic transit permission; route integration is added by its owning stage. */
        TRANSIT_RIGHT,
        /** Exemption from a future transaction/customs tariff. */
        CUSTOMS_TARIFF_EXEMPTION,
        /** Security guarantee consumed by Stage 18 conflict logic. */
        GUARANTEE
    }

    /** Direction relative to the treaty directory owner. */
    public enum Direction {
        /** Treaty owner grants the right/obligation to the counterparty. */
        OWNER_TO_COUNTERPARTY,
        /** Counterparty grants the right/obligation to the treaty owner. */
        COUNTERPARTY_TO_OWNER,
        /** Both parties grant/assume the clause symmetrically. */
        MUTUAL
    }

    /**
     * Validates scope semantics and canonical values.
     *
     * @param kind semantic clause kind
     * @param direction grant/obligation direction relative to treaty owner and counterparty
     * @param systemId optional territorial scope; required only for construction rights
     */
    public DiplomaticTreatyClauseState {
        kind = Objects.requireNonNull(kind, "Treaty clause kind not set");
        direction = Objects.requireNonNull(direction, "Treaty clause direction not set");
        if (kind == Kind.CONSTRUCTION_RIGHT && systemId == null) {
            throw new IllegalArgumentException("Construction-right treaty clause requires StarSystem scope");
        }
        if (kind != Kind.CONSTRUCTION_RIGHT && systemId != null) {
            throw new IllegalArgumentException("Only construction-right treaty clauses may have StarSystem scope");
        }
    }

    @Override
    public int compareTo(DiplomaticTreatyClauseState other) {
        DiplomaticTreatyClauseState value = Objects.requireNonNull(other, "DiplomaticTreatyClauseState not set");
        int byKind = kind.compareTo(value.kind);
        if (byKind != 0) {
            return byKind;
        }
        int byDirection = direction.compareTo(value.direction);
        if (byDirection != 0) {
            return byDirection;
        }
        if (systemId == null) {
            return value.systemId == null ? 0 : -1;
        }
        return value.systemId == null ? 1 : systemId.compareTo(value.systemId);
    }
}