package com.spacesim.world;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure legal resolver for effective faction market access.
 *
 * <p>Precedence is intentionally explicit: hard embargo first, then an active treaty right, then
 * the legacy directed relation threshold. No branch transfers money or materializes economic harm.
 * A full persisted world supplies complete diplomacy coverage, while source-compatible isolated
 * policy projections may omit the participant's own strategic/diplomatic state. In that narrow
 * compatibility case only owner-side explicit instruments and the owner's relation fallback are
 * available; absence of participant state never manufactures a permission or prohibition.</p>
 */
public final class DiplomaticMarketAccessResolver {
    private DiplomaticMarketAccessResolver() {
        throw new AssertionError("Utility class");
    }

    /** Explanation of the legal branch that decided access. */
    public enum Reason {
        /** Market owner always has access to its own market. */
        SELF,
        /** A currently active embargo on either side blocks ordinary trade. */
        EMBARGO,
        /** An explicit active treaty clause grants access. */
        EXPLICIT_TREATY_RIGHT,
        /** Legacy directed relation threshold allows access. */
        RELATION_THRESHOLD_ALLOW,
        /** Legacy directed relation threshold denies access. */
        RELATION_THRESHOLD_DENY,
        /** Unfactioned access follows the owner's existing threshold policy and is allowed. */
        UNFACTIONED_ALLOW,
        /** Unfactioned access follows the owner's existing threshold policy and is denied. */
        UNFACTIONED_DENY
    }

    /**
     * @param allowed final legal access result
     * @param reason precedence branch that decided the result
     * @param relation owner-to-participant summary relation used by threshold fallback
     * @param threshold owner's configured relation threshold
     * @param instrumentId treaty ID or embargo diagnostic ID when applicable, otherwise empty
     */
    public record Decision(
            boolean allowed,
            Reason reason,
            int relation,
            int threshold,
            String instrumentId) {
        /**
         * Validates one explainable access result.
         *
         * @param allowed final legal access result
         * @param reason precedence branch that decided the result
         * @param relation owner-to-participant summary relation used by threshold fallback
         * @param threshold owner's configured relation threshold
         * @param instrumentId treaty ID or embargo diagnostic ID when applicable, otherwise empty
         */
        public Decision {
            reason = Objects.requireNonNull(reason, "Market-access reason not set");
            instrumentId = Objects.requireNonNull(instrumentId, "Market-access instrument ID not set");
        }
    }

    /**
     * Evaluates one market owner/participant pair from persistent diplomacy only.
     *
     * @param strategies persistent strategic states containing legacy relation thresholds
     * @param diplomacyStates explicit Stage-17E diplomacy aggregates
     * @param marketOwnerFactionContentId market-owning faction
     * @param participantFactionContentId participant faction, or {@code null} when unfactioned
     * @param worldTick authoritative world tick
     * @return explainable deterministic legal decision
     */
    public static Decision evaluate(
            List<FactionStrategicState> strategies,
            List<FactionDiplomacyState> diplomacyStates,
            String marketOwnerFactionContentId,
            String participantFactionContentId,
            long worldTick) {
        Objects.requireNonNull(strategies, "Faction strategic states not set");
        Objects.requireNonNull(diplomacyStates, "Faction diplomacy states not set");
        if (worldTick < 0L) {
            throw new IllegalArgumentException("Authoritative world tick cannot be negative");
        }
        String ownerId = requireId(marketOwnerFactionContentId, "Market owner faction ID");
        String participantId = participantFactionContentId == null ? null : participantFactionContentId.strip();
        if (participantId != null && participantId.isEmpty()) {
            participantId = null;
        }

        Map<String, FactionStrategicState> strategyById = indexStrategies(strategies);
        Map<String, FactionDiplomacyState> diplomacyById = indexDiplomacy(diplomacyStates);
        FactionStrategicState ownerStrategy = strategyById.get(ownerId);
        if (ownerStrategy == null) {
            throw new IllegalArgumentException("Market owner has no strategic state: " + ownerId);
        }
        if (!diplomacyById.containsKey(ownerId)) {
            throw new IllegalArgumentException("Market owner has no diplomacy state: " + ownerId);
        }

        int threshold = ownerStrategy.minimumMarketAccessRelation();
        if (participantId == null) {
            int relation = ownerStrategy.relationTo("");
            boolean allowed = relation >= threshold;
            return new Decision(
                    allowed,
                    allowed ? Reason.UNFACTIONED_ALLOW : Reason.UNFACTIONED_DENY,
                    relation,
                    threshold,
                    "");
        }
        if (ownerId.equals(participantId)) {
            return new Decision(true, Reason.SELF, 100, threshold, "");
        }

        String embargo = embargoInstrument(diplomacyById, ownerId, participantId, worldTick);
        int relation = ownerStrategy.relationTo(participantId);
        if (embargo != null) {
            return new Decision(false, Reason.EMBARGO, relation, threshold, embargo);
        }

        String treaty = grantingTreaty(diplomacyStates, ownerId, participantId, worldTick);
        if (treaty != null) {
            return new Decision(true, Reason.EXPLICIT_TREATY_RIGHT, relation, threshold, treaty);
        }

        boolean allowed = relation >= threshold;
        return new Decision(
                allowed,
                allowed ? Reason.RELATION_THRESHOLD_ALLOW : Reason.RELATION_THRESHOLD_DENY,
                relation,
                threshold,
                "");
    }

    private static String embargoInstrument(
            Map<String, FactionDiplomacyState> diplomacyById,
            String ownerId,
            String participantId,
            long worldTick) {
        FactionDiplomacyState owner = diplomacyById.get(ownerId);
        if (owner.hasActiveMarketEmbargoAgainst(participantId, worldTick)) {
            return "embargo:" + ownerId + "->" + participantId;
        }
        FactionDiplomacyState participant = diplomacyById.get(participantId);
        if (participant != null && participant.hasActiveMarketEmbargoAgainst(ownerId, worldTick)) {
            return "embargo:" + participantId + "->" + ownerId;
        }
        return null;
    }

    private static String grantingTreaty(
            List<FactionDiplomacyState> diplomacyStates,
            String grantorId,
            String beneficiaryId,
            long worldTick) {
        for (FactionDiplomacyState directory : diplomacyStates) {
            String ownerId = directory.factionContentId();
            for (DiplomaticTreatyState treaty : directory.treaties()) {
                if (!treaty.activeAt(worldTick) || !treaty.containsMarketAccessClause()) {
                    continue;
                }
                String counterpartyId = treaty.counterpartyFactionContentId();
                if (!pairMatches(ownerId, counterpartyId, grantorId, beneficiaryId)) {
                    continue;
                }
                for (DiplomaticTreatyClauseState clause : treaty.clauses()) {
                    if (clause.kind() != DiplomaticTreatyClauseState.Kind.MARKET_ACCESS) {
                        continue;
                    }
                    if (grants(clause.direction(), ownerId, counterpartyId, grantorId, beneficiaryId)) {
                        return treaty.treatyId();
                    }
                }
            }
        }
        return null;
    }

    private static boolean pairMatches(String first, String second, String left, String right) {
        return (first.equals(left) && second.equals(right)) || (first.equals(right) && second.equals(left));
    }

    private static boolean grants(
            DiplomaticTreatyClauseState.Direction direction,
            String treatyOwnerId,
            String counterpartyId,
            String grantorId,
            String beneficiaryId) {
        return switch (direction) {
            case OWNER_TO_COUNTERPARTY -> treatyOwnerId.equals(grantorId) && counterpartyId.equals(beneficiaryId);
            case COUNTERPARTY_TO_OWNER -> counterpartyId.equals(grantorId) && treatyOwnerId.equals(beneficiaryId);
            case MUTUAL -> pairMatches(treatyOwnerId, counterpartyId, grantorId, beneficiaryId);
        };
    }

    private static Map<String, FactionStrategicState> indexStrategies(List<FactionStrategicState> strategies) {
        Map<String, FactionStrategicState> result = new HashMap<>();
        for (FactionStrategicState strategy : strategies) {
            FactionStrategicState value = Objects.requireNonNull(strategy, "Faction strategic state not set");
            if (result.putIfAbsent(value.factionContentId(), value) != null) {
                throw new IllegalArgumentException("Duplicate strategic faction: " + value.factionContentId());
            }
        }
        return Map.copyOf(result);
    }

    private static Map<String, FactionDiplomacyState> indexDiplomacy(List<FactionDiplomacyState> diplomacyStates) {
        Map<String, FactionDiplomacyState> result = new HashMap<>();
        for (FactionDiplomacyState state : diplomacyStates) {
            FactionDiplomacyState value = Objects.requireNonNull(state, "Faction diplomacy state not set");
            if (result.putIfAbsent(value.factionContentId(), value) != null) {
                throw new IllegalArgumentException("Duplicate diplomacy faction: " + value.factionContentId());
            }
        }
        return Map.copyOf(result);
    }

    private static String requireId(String value, String label) {
        String normalized = Objects.requireNonNull(value, label + " not set").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return normalized;
    }
}
