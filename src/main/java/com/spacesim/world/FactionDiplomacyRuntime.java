package com.spacesim.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Authoritative runtime projection of persistent institutional diplomacy.
 *
 * <p>The runtime currently owns immutable snapshots and access-transition scheduling. Stage 17E.2
 * command transitions mutate this same aggregate rather than introducing a parallel diplomacy
 * store.</p>
 */
final class FactionDiplomacyRuntime {
    private final FactionIdentityResolver identities;
    private final List<FactionDiplomacyState> states;
    private final Map<String, FactionDiplomacyState> byId;
    private long nextMarketAccessTransitionTick = -1L;

    FactionDiplomacyRuntime(
            FactionIdentityResolver identities,
            List<FactionDiplomacyState> initialStates) {
        this.identities = Objects.requireNonNull(identities, "FactionIdentityResolver not set");
        Objects.requireNonNull(initialStates, "Faction diplomacy states not set");
        List<FactionDiplomacyState> canonical = new ArrayList<>(initialStates.size());
        Map<String, FactionDiplomacyState> indexed = new HashMap<>();
        for (FactionDiplomacyState state : initialStates) {
            FactionDiplomacyState value = Objects.requireNonNull(state, "FactionDiplomacyState not set");
            requireKnownFaction(value.factionContentId());
            validateReferences(value);
            if (indexed.putIfAbsent(value.factionContentId(), value) != null) {
                throw new IllegalArgumentException("Duplicate faction diplomacy state: " + value.factionContentId());
            }
            canonical.add(value);
        }
        canonical.sort(Comparator.naturalOrder());
        states = List.copyOf(canonical);
        byId = Map.copyOf(indexed);
    }

    List<FactionDiplomacyState> snapshots() {
        return states;
    }

    FactionDiplomacyState find(String factionContentId) {
        return factionContentId == null ? null : byId.get(factionContentId.strip());
    }

    /** Recomputes the next tick where market-access law can activate or expire. */
    void noteMarketAccessPolicyRefreshed(long worldTick) {
        if (worldTick < 0L) {
            throw new IllegalArgumentException("Authoritative world tick cannot be negative");
        }
        long next = -1L;
        for (FactionDiplomacyState state : states) {
            for (DiplomaticEmbargoState embargo : state.embargoes()) {
                if (embargo.scope() != DiplomaticEmbargoState.Scope.MARKET_ACCESS) {
                    continue;
                }
                if (embargo.imposedTick() > worldTick) {
                    next = earlier(next, embargo.imposedTick());
                }
                if (embargo.expiresTick() > worldTick) {
                    next = earlier(next, embargo.expiresTick());
                }
            }
            for (DiplomaticTreatyState treaty : state.treaties()) {
                if (!treaty.containsMarketAccessClause()
                        || (treaty.status() != DiplomaticTreatyState.Status.ACTIVE
                        && treaty.status() != DiplomaticTreatyState.Status.TERMINATING)) {
                    continue;
                }
                if (treaty.effectiveTick() > worldTick) {
                    next = earlier(next, treaty.effectiveTick());
                }
                if (treaty.expiresTick() > worldTick) {
                    next = earlier(next, treaty.expiresTick());
                }
            }
        }
        nextMarketAccessTransitionTick = next;
    }

    /** @return true when the cached ECS access projection crossed an activation/expiry boundary */
    boolean marketAccessExpiryCrossed(long worldTick) {
        if (worldTick < 0L) {
            throw new IllegalArgumentException("Authoritative world tick cannot be negative");
        }
        return nextMarketAccessTransitionTick >= 0L && worldTick >= nextMarketAccessTransitionTick;
    }

    private void validateReferences(FactionDiplomacyState state) {
        for (DiplomaticStandingState standing : state.standings()) {
            requireKnownFaction(standing.targetFactionContentId());
        }
        for (DiplomaticGrievanceState grievance : state.grievances()) {
            requireKnownFaction(grievance.targetFactionContentId());
        }
        for (DiplomaticTreatyState treaty : state.treaties()) {
            requireKnownFaction(treaty.counterpartyFactionContentId());
        }
        for (DiplomaticEmbargoState embargo : state.embargoes()) {
            requireKnownFaction(embargo.targetFactionContentId());
        }
    }

    private void requireKnownFaction(String factionContentId) {
        if (identities.runtimeId(factionContentId).isEmpty()) {
            throw new IllegalArgumentException("Unknown faction diplomacy identity: " + factionContentId);
        }
    }

    private static long earlier(long current, long candidate) {
        return current < 0L || candidate < current ? candidate : current;
    }
}
