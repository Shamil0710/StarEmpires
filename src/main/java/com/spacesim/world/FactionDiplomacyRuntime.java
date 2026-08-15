package com.spacesim.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Authoritative mutable runtime for persistent institutional diplomacy.
 *
 * <p>All player and AI treaty commands mutate this same aggregate. The runtime performs only legal
 * and political state transitions; it never transfers money, creates cargo or applies abstract
 * economic damage. Market-access consequences are re-materialized by {@link WorldSimulation}
 * through the ordinary transient policy projection after a successful transition.</p>
 */
final class FactionDiplomacyRuntime {
    private static final int BREACH_GRIEVANCE_SEVERITY = 60;
    private static final int BREACH_TRUST_PENALTY = 20;
    private static final int BREACH_CREDIBILITY_PENALTY = 25;
    private static final int EMBARGO_GRIEVANCE_SEVERITY = 40;

    private final FactionIdentityResolver identities;
    private List<FactionDiplomacyState> states;
    private Map<String, FactionDiplomacyState> byId;
    private long nextMarketAccessTransitionTick = -1L;

    FactionDiplomacyRuntime(
            FactionIdentityResolver identities,
            List<FactionDiplomacyState> initialStates) {
        this.identities = Objects.requireNonNull(identities, "FactionIdentityResolver not set");
        install(Objects.requireNonNull(initialStates, "Faction diplomacy states not set"));
    }

    List<FactionDiplomacyState> snapshots() {
        return states;
    }

    FactionDiplomacyState find(String factionContentId) {
        return factionContentId == null ? null : byId.get(factionContentId.strip());
    }

    DiplomaticTreatyState findTreaty(String treatyId) {
        TreatyLocation location = locateTreaty(treatyId);
        return location == null ? null : location.treaty();
    }

    /**
     * Applies one common unilateral embargo command at the authoritative world tick.
     *
     * @param command player/AI embargo command
     * @param worldTick authoritative world tick
     * @return immutable successful embargo transition result
     */
    DiplomaticEmbargoCommandResult apply(DiplomaticEmbargoCommand command, long worldTick) {
        DiplomaticEmbargoCommand checked = Objects.requireNonNull(command, "Diplomatic embargo command not set");
        requireWorldTick(worldTick);
        requireFaction(checked.actorFactionContentId());
        if (checked instanceof DiplomaticEmbargoCommand.Impose impose) {
            return impose(impose, worldTick);
        }
        if (checked instanceof DiplomaticEmbargoCommand.Revoke revoke) {
            return revoke(revoke, worldTick);
        }
        throw new IllegalArgumentException("Unsupported diplomatic embargo command: " + checked.getClass().getName());
    }

    /**
     * Applies one validated common treaty lifecycle command at the authoritative world tick.
     *
     * @param command player/AI treaty command
     * @param worldTick authoritative world tick
     * @return immutable result of the successful transition
     */
    DiplomaticTreatyCommandResult apply(DiplomaticTreatyCommand command, long worldTick) {
        DiplomaticTreatyCommand checked = Objects.requireNonNull(command, "Diplomatic treaty command not set");
        requireWorldTick(worldTick);
        requireFaction(checked.actorFactionContentId());

        if (checked instanceof DiplomaticTreatyCommand.Offer offer) {
            return offer(offer, worldTick, DiplomaticTreatyCommandResult.Operation.OFFERED, "");
        }
        if (checked instanceof DiplomaticTreatyCommand.CounterOffer counterOffer) {
            return counterOffer(counterOffer, worldTick);
        }
        if (checked instanceof DiplomaticTreatyCommand.Accept accept) {
            return accept(accept, worldTick);
        }
        if (checked instanceof DiplomaticTreatyCommand.Reject reject) {
            return reject(reject);
        }
        if (checked instanceof DiplomaticTreatyCommand.TerminateWithNotice terminate) {
            return terminate(terminate, worldTick);
        }
        if (checked instanceof DiplomaticTreatyCommand.Breach breach) {
            return breach(breach, worldTick);
        }
        if (checked instanceof DiplomaticTreatyCommand.Renew renew) {
            return renew(renew, worldTick);
        }
        throw new IllegalArgumentException("Unsupported diplomatic treaty command: " + checked.getClass().getName());
    }

    /**
     * Materializes finite active/terminating treaty expiry into persistent EXPIRED status.
     *
     * @param worldTick authoritative world tick
     * @return true when at least one persistent treaty state changed
     */
    boolean advanceTime(long worldTick) {
        requireWorldTick(worldTick);
        Map<String, FactionDiplomacyState> replacements = new HashMap<>();
        for (FactionDiplomacyState state : states) {
            List<DiplomaticTreatyState> updatedTreaties = null;
            for (int index = 0; index < state.treaties().size(); index++) {
                DiplomaticTreatyState treaty = state.treaties().get(index);
                if ((treaty.status() == DiplomaticTreatyState.Status.ACTIVE
                        || treaty.status() == DiplomaticTreatyState.Status.TERMINATING)
                        && treaty.expiresTick() >= 0L
                        && treaty.expiresTick() <= worldTick) {
                    if (updatedTreaties == null) {
                        updatedTreaties = new ArrayList<>(state.treaties());
                    }
                    updatedTreaties.set(index, copyTreaty(
                            treaty,
                            DiplomaticTreatyState.Status.EXPIRED,
                            treaty.effectiveTick(),
                            treaty.expiresTick()));
                }
            }
            if (updatedTreaties != null) {
                replacements.put(state.factionContentId(), copyState(
                        state,
                        state.standings(),
                        state.grievances(),
                        updatedTreaties,
                        state.embargoes()));
            }
        }
        if (replacements.isEmpty()) {
            return false;
        }
        install(replaceStates(replacements));
        return true;
    }

    /** Recomputes the next tick where market-access law can activate or expire. */
    void noteMarketAccessPolicyRefreshed(long worldTick) {
        requireWorldTick(worldTick);
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
    boolean marketAccessTransitionCrossed(long worldTick) {
        requireWorldTick(worldTick);
        return nextMarketAccessTransitionTick >= 0L && worldTick >= nextMarketAccessTransitionTick;
    }

    /** Source-compatible name retained for the Stage-17E.1 scheduler acceptance test. */
    boolean marketAccessExpiryCrossed(long worldTick) {
        return marketAccessTransitionCrossed(worldTick);
    }

    private DiplomaticEmbargoCommandResult impose(
            DiplomaticEmbargoCommand.Impose command,
            long worldTick) {
        String actorId = requireFaction(command.actorFactionContentId());
        String targetId = requireFaction(command.targetFactionContentId());
        if (actorId.equals(targetId)) {
            throw new IllegalArgumentException("Faction cannot embargo itself");
        }
        requireFutureExpiry(command.expiresTick(), worldTick);
        FactionDiplomacyState actor = byId.get(actorId);
        DiplomaticEmbargoState existing = marketEmbargoToward(actor, targetId);
        if (existing != null && existing.activeAt(worldTick)) {
            throw new IllegalStateException("Active market embargo already exists: " + actorId + " -> " + targetId);
        }

        DiplomaticEmbargoState embargo = new DiplomaticEmbargoState(
                targetId,
                DiplomaticEmbargoState.Scope.MARKET_ACCESS,
                worldTick,
                command.expiresTick(),
                command.reasonKey());
        FactionDiplomacyState target = byId.get(targetId);
        String grievanceId = allocateEmbargoGrievanceId(target, actorId, worldTick);
        List<DiplomaticGrievanceState> grievances = new ArrayList<>(target.grievances());
        String subject = command.reasonKey().isEmpty()
                ? "embargo:" + actorId + "->" + targetId
                : "embargo:" + actorId + "->" + targetId + ":" + command.reasonKey();
        grievances.add(new DiplomaticGrievanceState(
                grievanceId,
                actorId,
                DiplomaticGrievanceState.Kind.EMBARGO,
                EMBARGO_GRIEVANCE_SEVERITY,
                worldTick,
                -1L,
                subject));

        Map<String, FactionDiplomacyState> replacements = new HashMap<>();
        replacements.put(actorId, withMarketEmbargo(actor, embargo));
        replacements.put(targetId, copyState(
                target, target.standings(), grievances, target.treaties(), target.embargoes()));
        install(replaceStates(replacements));
        return new DiplomaticEmbargoCommandResult(
                DiplomaticEmbargoCommandResult.Operation.IMPOSED,
                actorId,
                targetId,
                embargo,
                grievanceId);
    }

    private DiplomaticEmbargoCommandResult revoke(
            DiplomaticEmbargoCommand.Revoke command,
            long worldTick) {
        String actorId = requireFaction(command.actorFactionContentId());
        String targetId = requireFaction(command.targetFactionContentId());
        FactionDiplomacyState actor = byId.get(actorId);
        DiplomaticEmbargoState existing = marketEmbargoToward(actor, targetId);
        if (existing == null || !existing.activeAt(worldTick)) {
            throw new IllegalStateException("No active market embargo to revoke: " + actorId + " -> " + targetId);
        }
        List<DiplomaticEmbargoState> embargoes = new ArrayList<>(actor.embargoes());
        boolean removed = embargoes.removeIf(embargo ->
                embargo.scope() == DiplomaticEmbargoState.Scope.MARKET_ACCESS
                        && embargo.targetFactionContentId().equals(targetId));
        if (!removed) {
            throw new IllegalStateException("Active embargo disappeared during revocation");
        }
        install(replaceStates(Map.of(actorId, copyState(
                actor, actor.standings(), actor.grievances(), actor.treaties(), embargoes))));
        return new DiplomaticEmbargoCommandResult(
                DiplomaticEmbargoCommandResult.Operation.REVOKED,
                actorId,
                targetId,
                existing,
                "");
    }

    private static DiplomaticEmbargoState marketEmbargoToward(
            FactionDiplomacyState state,
            String targetFactionContentId) {
        for (DiplomaticEmbargoState embargo : state.embargoes()) {
            if (embargo.scope() == DiplomaticEmbargoState.Scope.MARKET_ACCESS
                    && embargo.targetFactionContentId().equals(targetFactionContentId)) {
                return embargo;
            }
        }
        return null;
    }

    private static FactionDiplomacyState withMarketEmbargo(
            FactionDiplomacyState state,
            DiplomaticEmbargoState replacement) {
        List<DiplomaticEmbargoState> embargoes = new ArrayList<>(state.embargoes());
        boolean replaced = false;
        for (int index = 0; index < embargoes.size(); index++) {
            DiplomaticEmbargoState current = embargoes.get(index);
            if (current.scope() == replacement.scope()
                    && current.targetFactionContentId().equals(replacement.targetFactionContentId())) {
                embargoes.set(index, replacement);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            embargoes.add(replacement);
        }
        return copyState(state, state.standings(), state.grievances(), state.treaties(), embargoes);
    }

    private DiplomaticTreatyCommandResult offer(
            DiplomaticTreatyCommand.Offer offer,
            long worldTick,
            DiplomaticTreatyCommandResult.Operation operation,
            String relatedTreatyId) {
        String ownerId = requireFaction(offer.actorFactionContentId());
        String counterpartyId = requireFaction(offer.counterpartyFactionContentId());
        if (ownerId.equals(counterpartyId)) {
            throw new IllegalArgumentException("Faction cannot offer a treaty to itself");
        }
        requireFutureExpiry(offer.expiresTick(), worldTick);
        DiplomaticTreatyState proposal = createProposal(
                ownerId,
                counterpartyId,
                offer.clauses(),
                offer.expiresTick(),
                worldTick);
        FactionDiplomacyState owner = byId.get(ownerId);
        install(replaceStates(Map.of(ownerId, withAddedTreaty(owner, proposal))));
        return new DiplomaticTreatyCommandResult(operation, ownerId, proposal, relatedTreatyId, "");
    }

    private DiplomaticTreatyCommandResult counterOffer(
            DiplomaticTreatyCommand.CounterOffer command,
            long worldTick) {
        TreatyLocation original = requireTreaty(command.treatyId());
        requireProposalResponseActor(original, command.actorFactionContentId());
        requireOpenProposal(original.treaty(), worldTick);
        requireFutureExpiry(command.expiresTick(), worldTick);

        DiplomaticTreatyState rejected = copyTreaty(
                original.treaty(),
                DiplomaticTreatyState.Status.REJECTED,
                -1L,
                original.treaty().expiresTick());
        DiplomaticTreatyState replacement = createProposal(
                command.actorFactionContentId(),
                original.ownerFactionContentId(),
                command.clauses(),
                command.expiresTick(),
                worldTick);

        Map<String, FactionDiplomacyState> replacements = new HashMap<>();
        replacements.put(
                original.ownerFactionContentId(),
                withReplacedTreaty(byId.get(original.ownerFactionContentId()), rejected));
        replacements.put(
                command.actorFactionContentId(),
                withAddedTreaty(byId.get(command.actorFactionContentId()), replacement));
        install(replaceStates(replacements));
        return new DiplomaticTreatyCommandResult(
                DiplomaticTreatyCommandResult.Operation.COUNTEROFFERED,
                command.actorFactionContentId(),
                replacement,
                original.treaty().treatyId(),
                "");
    }

    private DiplomaticTreatyCommandResult accept(
            DiplomaticTreatyCommand.Accept command,
            long worldTick) {
        TreatyLocation location = requireTreaty(command.treatyId());
        requireProposalResponseActor(location, command.actorFactionContentId());
        requireOpenProposal(location.treaty(), worldTick);
        DiplomaticTreatyState active = copyTreaty(
                location.treaty(),
                DiplomaticTreatyState.Status.ACTIVE,
                worldTick,
                location.treaty().expiresTick());
        install(replaceStates(Map.of(
                location.ownerFactionContentId(),
                withReplacedTreaty(byId.get(location.ownerFactionContentId()), active))));
        return new DiplomaticTreatyCommandResult(
                DiplomaticTreatyCommandResult.Operation.ACCEPTED,
                location.ownerFactionContentId(),
                active,
                "",
                "");
    }

    private DiplomaticTreatyCommandResult reject(DiplomaticTreatyCommand.Reject command) {
        TreatyLocation location = requireTreaty(command.treatyId());
        requireProposalResponseActor(location, command.actorFactionContentId());
        if (location.treaty().status() != DiplomaticTreatyState.Status.PROPOSED) {
            throw new IllegalStateException("Only a proposed treaty can be rejected: " + command.treatyId());
        }
        DiplomaticTreatyState rejected = copyTreaty(
                location.treaty(),
                DiplomaticTreatyState.Status.REJECTED,
                -1L,
                location.treaty().expiresTick());
        install(replaceStates(Map.of(
                location.ownerFactionContentId(),
                withReplacedTreaty(byId.get(location.ownerFactionContentId()), rejected))));
        return new DiplomaticTreatyCommandResult(
                DiplomaticTreatyCommandResult.Operation.REJECTED,
                location.ownerFactionContentId(),
                rejected,
                "",
                "");
    }

    private DiplomaticTreatyCommandResult terminate(
            DiplomaticTreatyCommand.TerminateWithNotice command,
            long worldTick) {
        TreatyLocation location = requireTreaty(command.treatyId());
        requireTreatyParty(location, command.actorFactionContentId());
        DiplomaticTreatyState treaty = location.treaty();
        if (treaty.status() != DiplomaticTreatyState.Status.ACTIVE) {
            throw new IllegalStateException("Only an active treaty can enter termination notice: " + treaty.treatyId());
        }
        if (!treaty.activeAt(worldTick)) {
            throw new IllegalStateException("Treaty is not active at the authoritative tick: " + treaty.treatyId());
        }
        long noticeExpiry = safeAdd(worldTick, command.noticeTicks());
        long effectiveExpiry = treaty.expiresTick() < 0L
                ? noticeExpiry
                : Math.min(treaty.expiresTick(), noticeExpiry);
        DiplomaticTreatyState terminating = copyTreaty(
                treaty,
                DiplomaticTreatyState.Status.TERMINATING,
                treaty.effectiveTick(),
                effectiveExpiry);
        install(replaceStates(Map.of(
                location.ownerFactionContentId(),
                withReplacedTreaty(byId.get(location.ownerFactionContentId()), terminating))));
        return new DiplomaticTreatyCommandResult(
                DiplomaticTreatyCommandResult.Operation.TERMINATING,
                location.ownerFactionContentId(),
                terminating,
                "",
                "");
    }

    private DiplomaticTreatyCommandResult breach(
            DiplomaticTreatyCommand.Breach command,
            long worldTick) {
        TreatyLocation location = requireTreaty(command.treatyId());
        requireTreatyParty(location, command.actorFactionContentId());
        DiplomaticTreatyState treaty = location.treaty();
        if ((treaty.status() != DiplomaticTreatyState.Status.ACTIVE
                && treaty.status() != DiplomaticTreatyState.Status.TERMINATING)
                || !treaty.activeAt(worldTick)) {
            throw new IllegalStateException("Only an in-force treaty can be breached: " + treaty.treatyId());
        }
        String breacherId = command.actorFactionContentId();
        String offendedId = otherParty(location, breacherId);
        DiplomaticTreatyState breached = copyTreaty(
                treaty,
                DiplomaticTreatyState.Status.BREACHED,
                treaty.effectiveTick(),
                treaty.expiresTick());

        Map<String, FactionDiplomacyState> replacements = new HashMap<>();
        replacements.put(
                location.ownerFactionContentId(),
                withReplacedTreaty(byId.get(location.ownerFactionContentId()), breached));
        FactionDiplomacyState offendedBase = replacements.getOrDefault(offendedId, byId.get(offendedId));
        replacements.put(offendedId, withBreachConsequences(
                offendedBase,
                breacherId,
                treaty.treatyId(),
                command.reasonKey(),
                worldTick));
        install(replaceStates(replacements));
        return new DiplomaticTreatyCommandResult(
                DiplomaticTreatyCommandResult.Operation.BREACHED,
                location.ownerFactionContentId(),
                breached,
                "",
                offendedId);
    }

    private DiplomaticTreatyCommandResult renew(
            DiplomaticTreatyCommand.Renew command,
            long worldTick) {
        TreatyLocation existing = requireTreaty(command.treatyId());
        requireTreatyParty(existing, command.actorFactionContentId());
        DiplomaticTreatyState treaty = existing.treaty();
        if (treaty.status() == DiplomaticTreatyState.Status.PROPOSED
                || treaty.status() == DiplomaticTreatyState.Status.REJECTED
                || treaty.status() == DiplomaticTreatyState.Status.BREACHED) {
            throw new IllegalStateException("Treaty state cannot be renewed: " + treaty.status());
        }
        String counterparty = otherParty(existing, command.actorFactionContentId());
        DiplomaticTreatyCommand.Offer renewal = new DiplomaticTreatyCommand.Offer(
                command.actorFactionContentId(),
                counterparty,
                treaty.clauses(),
                command.expiresTick());
        return offer(
                renewal,
                worldTick,
                DiplomaticTreatyCommandResult.Operation.RENEWAL_OFFERED,
                treaty.treatyId());
    }

    private DiplomaticTreatyState createProposal(
            String ownerId,
            String counterpartyId,
            List<DiplomaticTreatyClauseState> clauses,
            long expiresTick,
            long worldTick) {
        String treatyId = allocateTreatyId(ownerId, counterpartyId, worldTick);
        return new DiplomaticTreatyState(
                treatyId,
                counterpartyId,
                DiplomaticTreatyState.Status.PROPOSED,
                worldTick,
                -1L,
                expiresTick,
                clauses);
    }

    private FactionDiplomacyState withBreachConsequences(
            FactionDiplomacyState state,
            String breacherId,
            String treatyId,
            String reasonKey,
            long worldTick) {
        List<DiplomaticGrievanceState> grievances = new ArrayList<>(state.grievances());
        String grievanceId = allocateGrievanceId(state, treatyId, breacherId, worldTick);
        String subject = reasonKey.isEmpty() ? "treaty:" + treatyId : "treaty:" + treatyId + ":" + reasonKey;
        grievances.add(new DiplomaticGrievanceState(
                grievanceId,
                breacherId,
                DiplomaticGrievanceState.Kind.TREATY_BREACH,
                BREACH_GRIEVANCE_SEVERITY,
                worldTick,
                -1L,
                subject));

        List<DiplomaticStandingState> standings = new ArrayList<>(state.standings());
        DiplomaticStandingState previous = state.standingToward(breacherId);
        DiplomaticStandingState updated = new DiplomaticStandingState(
                breacherId,
                clamp(previous == null ? -BREACH_TRUST_PENALTY : previous.trust() - BREACH_TRUST_PENALTY, -100, 100),
                clamp(previous == null
                        ? DiplomaticStandingState.NEUTRAL_CREDIBILITY - BREACH_CREDIBILITY_PENALTY
                        : previous.credibility() - BREACH_CREDIBILITY_PENALTY, 0, 100),
                worldTick);
        boolean replaced = false;
        for (int index = 0; index < standings.size(); index++) {
            if (standings.get(index).targetFactionContentId().equals(breacherId)) {
                standings.set(index, updated);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            standings.add(updated);
        }
        return copyState(state, standings, grievances, state.treaties(), state.embargoes());
    }

    private void install(List<FactionDiplomacyState> source) {
        List<FactionDiplomacyState> canonical = new ArrayList<>(source.size());
        Map<String, FactionDiplomacyState> indexed = new HashMap<>();
        Set<String> treatyIds = new HashSet<>();
        for (FactionDiplomacyState state : source) {
            FactionDiplomacyState value = Objects.requireNonNull(state, "FactionDiplomacyState not set");
            requireKnownFaction(value.factionContentId());
            validateReferences(value);
            if (indexed.putIfAbsent(value.factionContentId(), value) != null) {
                throw new IllegalArgumentException("Duplicate faction diplomacy state: " + value.factionContentId());
            }
            for (DiplomaticTreatyState treaty : value.treaties()) {
                if (!treatyIds.add(treaty.treatyId())) {
                    throw new IllegalArgumentException("Duplicate world treaty ID: " + treaty.treatyId());
                }
            }
            canonical.add(value);
        }
        canonical.sort(Comparator.naturalOrder());
        states = List.copyOf(canonical);
        byId = Map.copyOf(indexed);
    }

    private List<FactionDiplomacyState> replaceStates(Map<String, FactionDiplomacyState> replacements) {
        List<FactionDiplomacyState> candidate = new ArrayList<>(states.size());
        for (FactionDiplomacyState state : states) {
            candidate.add(replacements.getOrDefault(state.factionContentId(), state));
        }
        return List.copyOf(candidate);
    }

    private static FactionDiplomacyState withAddedTreaty(
            FactionDiplomacyState state,
            DiplomaticTreatyState treaty) {
        List<DiplomaticTreatyState> treaties = new ArrayList<>(state.treaties());
        treaties.add(treaty);
        return copyState(state, state.standings(), state.grievances(), treaties, state.embargoes());
    }

    private static FactionDiplomacyState withReplacedTreaty(
            FactionDiplomacyState state,
            DiplomaticTreatyState replacement) {
        List<DiplomaticTreatyState> treaties = new ArrayList<>(state.treaties());
        boolean found = false;
        for (int index = 0; index < treaties.size(); index++) {
            if (treaties.get(index).treatyId().equals(replacement.treatyId())) {
                treaties.set(index, replacement);
                found = true;
                break;
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Treaty is not in expected directory: " + replacement.treatyId());
        }
        return copyState(state, state.standings(), state.grievances(), treaties, state.embargoes());
    }

    private static FactionDiplomacyState copyState(
            FactionDiplomacyState state,
            List<DiplomaticStandingState> standings,
            List<DiplomaticGrievanceState> grievances,
            List<DiplomaticTreatyState> treaties,
            List<DiplomaticEmbargoState> embargoes) {
        return new FactionDiplomacyState(
                state.factionContentId(), standings, grievances, treaties, embargoes);
    }

    private static DiplomaticTreatyState copyTreaty(
            DiplomaticTreatyState treaty,
            DiplomaticTreatyState.Status status,
            long effectiveTick,
            long expiresTick) {
        return new DiplomaticTreatyState(
                treaty.treatyId(),
                treaty.counterpartyFactionContentId(),
                status,
                treaty.createdTick(),
                effectiveTick,
                expiresTick,
                treaty.clauses());
    }

    private TreatyLocation requireTreaty(String treatyId) {
        TreatyLocation location = locateTreaty(treatyId);
        if (location == null) {
            throw new IllegalArgumentException("Unknown treaty: " + treatyId);
        }
        return location;
    }

    private TreatyLocation locateTreaty(String treatyId) {
        if (treatyId == null) {
            return null;
        }
        String id = treatyId.strip();
        if (id.isEmpty()) {
            return null;
        }
        for (FactionDiplomacyState state : states) {
            for (DiplomaticTreatyState treaty : state.treaties()) {
                if (treaty.treatyId().equals(id)) {
                    return new TreatyLocation(state.factionContentId(), treaty);
                }
            }
        }
        return null;
    }

    private void requireProposalResponseActor(TreatyLocation location, String actorFactionContentId) {
        if (!location.treaty().counterpartyFactionContentId().equals(actorFactionContentId)) {
            throw new IllegalArgumentException("Only the receiving counterparty may respond to a treaty proposal");
        }
    }

    private static void requireOpenProposal(DiplomaticTreatyState treaty, long worldTick) {
        if (treaty.status() != DiplomaticTreatyState.Status.PROPOSED) {
            throw new IllegalStateException("Treaty is not an open proposal: " + treaty.treatyId());
        }
        if (treaty.expiresTick() >= 0L && treaty.expiresTick() <= worldTick) {
            throw new IllegalStateException("Treaty proposal can no longer activate after its expiry: "
                    + treaty.treatyId());
        }
    }

    private static void requireTreatyParty(TreatyLocation location, String actorFactionContentId) {
        if (!location.ownerFactionContentId().equals(actorFactionContentId)
                && !location.treaty().counterpartyFactionContentId().equals(actorFactionContentId)) {
            throw new IllegalArgumentException("Faction is not a party to treaty: " + location.treaty().treatyId());
        }
    }

    private static String otherParty(TreatyLocation location, String actorFactionContentId) {
        return location.ownerFactionContentId().equals(actorFactionContentId)
                ? location.treaty().counterpartyFactionContentId()
                : location.ownerFactionContentId();
    }

    private String allocateTreatyId(String ownerId, String counterpartyId, long worldTick) {
        String prefix = "treaty:" + ownerId + "->" + counterpartyId + ":" + worldTick + ":";
        int sequence = 1;
        while (locateTreaty(prefix + sequence) != null) {
            sequence++;
        }
        return prefix + sequence;
    }

    private static String allocateEmbargoGrievanceId(
            FactionDiplomacyState state,
            String embargoingFactionId,
            long worldTick) {
        String prefix = "grievance:embargo:" + embargoingFactionId + ":" + worldTick + ":";
        int sequence = 1;
        while (containsGrievance(state, prefix + sequence)) {
            sequence++;
        }
        return prefix + sequence;
    }

    private static String allocateGrievanceId(
            FactionDiplomacyState state,
            String treatyId,
            String breacherId,
            long worldTick) {
        String prefix = "grievance:treaty-breach:" + treatyId + ":" + breacherId + ":" + worldTick + ":";
        int sequence = 1;
        while (containsGrievance(state, prefix + sequence)) {
            sequence++;
        }
        return prefix + sequence;
    }

    private static boolean containsGrievance(FactionDiplomacyState state, String grievanceId) {
        for (DiplomaticGrievanceState grievance : state.grievances()) {
            if (grievance.grievanceId().equals(grievanceId)) {
                return true;
            }
        }
        return false;
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

    private String requireFaction(String factionContentId) {
        String factionId = requireKnownFaction(factionContentId);
        if (!byId.containsKey(factionId)) {
            throw new IllegalArgumentException("Faction has no diplomacy state: " + factionId);
        }
        return factionId;
    }

    private String requireKnownFaction(String factionContentId) {
        String factionId = Objects.requireNonNull(factionContentId, "Faction content ID not set").strip();
        if (factionId.isEmpty() || identities.runtimeId(factionId).isEmpty()) {
            throw new IllegalArgumentException("Unknown faction diplomacy identity: " + factionId);
        }
        return factionId;
    }

    private static void requireFutureExpiry(long expiresTick, long worldTick) {
        if (expiresTick != -1L && expiresTick <= worldTick) {
            throw new IllegalArgumentException("Treaty expiry must be in the future or -1");
        }
    }

    private static void requireWorldTick(long worldTick) {
        if (worldTick < 0L) {
            throw new IllegalArgumentException("Authoritative world tick cannot be negative");
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long earlier(long current, long candidate) {
        return current < 0L || candidate < current ? candidate : current;
    }

    private static long safeAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Treaty notice tick overflow", exception);
        }
    }

    private record TreatyLocation(String ownerFactionContentId, DiplomaticTreatyState treaty) {
    }
}
