package com.spacesim.world;

import java.util.List;
import java.util.Objects;

/**
 * Pure resolver for the effective transaction/customs tariff between a market owner and trader.
 *
 * <p>This tariff is intentionally separate from the Stage-8 foreign-territory station levy. It is
 * charged on an actual market transaction by the faction that owns the market. Domestic trade is
 * exempt, and an active treaty {@link DiplomaticTreatyClauseState.Kind#CUSTOMS_TARIFF_EXEMPTION}
 * granted by the market owner overrides the ordinary rate.</p>
 */
public final class CustomsTariffResolver {
    private CustomsTariffResolver() {
        throw new AssertionError("Utility class");
    }

    /** Why a particular effective tariff applies. */
    public enum Reason {
        /** Market owner and trader are the same faction. */
        DOMESTIC,
        /** An active treaty grants a customs exemption. */
        TREATY_EXEMPTION,
        /** The market owner's ordinary foreign-transaction rate applies. */
        STANDARD_RATE
    }

    /**
     * @param basisPoints effective transaction tariff in basis points
     * @param reason legal branch producing the result
     * @param instrumentId treaty ID for an exemption, otherwise empty
     */
    public record Decision(int basisPoints, Reason reason, String instrumentId) {
        /**
         * Validates one effective tariff decision.
         *
         * @param basisPoints effective transaction tariff in basis points
         * @param reason legal branch producing the result
         * @param instrumentId treaty ID for an exemption, otherwise empty
         */
        public Decision {
            if (basisPoints < 0 || basisPoints > 10_000) {
                throw new IllegalArgumentException("Customs tariff must be in range 0..10000 bps");
            }
            reason = Objects.requireNonNull(reason, "Customs tariff reason not set");
            instrumentId = Objects.requireNonNull(instrumentId, "Customs tariff instrument ID not set");
        }
    }

    /**
     * Resolves the tariff without mutating diplomacy or economic state.
     *
     * <p>Compatibility worlds created through pre-17E source constructors can intentionally have
     * no explicit diplomacy aggregate. Such an absent market-owner entry means neutral legacy
     * policy ({@code 0 bps}), never a hidden tariff. Current persisted worlds still enforce normal
     * diplomacy coverage at their world-state validation boundary.</p>
     *
     * @param diplomacyStates persistent diplomacy aggregates
     * @param marketOwnerFactionContentId faction owning the market and collecting customs
     * @param participantFactionContentId trader faction, or {@code null} for an independent actor
     * @param worldTick authoritative world tick
     * @return deterministic effective customs decision
     */
    public static Decision evaluate(
            List<FactionDiplomacyState> diplomacyStates,
            String marketOwnerFactionContentId,
            String participantFactionContentId,
            long worldTick) {
        Objects.requireNonNull(diplomacyStates, "Faction diplomacy states not set");
        if (worldTick < 0L) {
            throw new IllegalArgumentException("Authoritative world tick cannot be negative");
        }
        String ownerId = requireId(marketOwnerFactionContentId, "Market owner faction ID");
        String participantId = participantFactionContentId == null ? null : participantFactionContentId.strip();
        if (participantId != null && participantId.isEmpty()) {
            participantId = null;
        }
        FactionDiplomacyState owner = find(diplomacyStates, ownerId);
        if (ownerId.equals(participantId)) {
            return new Decision(0, Reason.DOMESTIC, "");
        }
        if (owner == null) {
            return new Decision(0, Reason.STANDARD_RATE, "");
        }
        if (participantId != null) {
            String exemption = exemptionTreaty(diplomacyStates, ownerId, participantId, worldTick);
            if (exemption != null) {
                return new Decision(0, Reason.TREATY_EXEMPTION, exemption);
            }
        }
        return new Decision(owner.customsTariffBasisPoints(), Reason.STANDARD_RATE, "");
    }

    private static String exemptionTreaty(
            List<FactionDiplomacyState> states,
            String grantorId,
            String beneficiaryId,
            long worldTick) {
        for (FactionDiplomacyState directory : states) {
            String treatyOwner = directory.factionContentId();
            for (DiplomaticTreatyState treaty : directory.treaties()) {
                if (!treaty.activeAt(worldTick)) {
                    continue;
                }
                String counterparty = treaty.counterpartyFactionContentId();
                if (!pairMatches(treatyOwner, counterparty, grantorId, beneficiaryId)) {
                    continue;
                }
                for (DiplomaticTreatyClauseState clause : treaty.clauses()) {
                    if (clause.kind() != DiplomaticTreatyClauseState.Kind.CUSTOMS_TARIFF_EXEMPTION) {
                        continue;
                    }
                    if (grants(clause.direction(), treatyOwner, counterparty, grantorId, beneficiaryId)) {
                        return treaty.treatyId();
                    }
                }
            }
        }
        return null;
    }

    private static boolean grants(
            DiplomaticTreatyClauseState.Direction direction,
            String treatyOwner,
            String counterparty,
            String grantor,
            String beneficiary) {
        return switch (direction) {
            case OWNER_TO_COUNTERPARTY -> treatyOwner.equals(grantor) && counterparty.equals(beneficiary);
            case COUNTERPARTY_TO_OWNER -> counterparty.equals(grantor) && treatyOwner.equals(beneficiary);
            case MUTUAL -> pairMatches(treatyOwner, counterparty, grantor, beneficiary);
        };
    }

    private static boolean pairMatches(String first, String second, String left, String right) {
        return (first.equals(left) && second.equals(right)) || (first.equals(right) && second.equals(left));
    }

    private static FactionDiplomacyState find(List<FactionDiplomacyState> states, String factionId) {
        for (FactionDiplomacyState state : states) {
            FactionDiplomacyState value = Objects.requireNonNull(state, "Faction diplomacy state not set");
            if (value.factionContentId().equals(factionId)) {
                return value;
            }
        }
        return null;
    }

    private static String requireId(String value, String label) {
        String normalized = Objects.requireNonNull(value, label + " not set").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return normalized;
    }
}
