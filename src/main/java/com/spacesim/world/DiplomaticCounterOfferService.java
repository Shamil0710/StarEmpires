package com.spacesim.world;

import com.spacesim.world.DiplomaticLifecycleService.ProposalRequest;
import com.spacesim.world.DiplomaticLifecycleState.Proposal;
import com.spacesim.world.DiplomaticLifecycleState.ProposalKind;
import com.spacesim.world.DiplomaticLifecycleState.Term;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Stage-21C bounded counter-offer transition composed entirely from the diplomatic lifecycle API.
 *
 * <p>A counter-offer explicitly rejects the currently open proposal, which also closes any linked
 * Stage-17 treaty offer through {@link DiplomaticLifecycleService#reject(String)}, then creates one
 * reversed open proposal. Counter lineage is persisted in the existing source identity as
 * {@code counter-proposal:<proposalId>} so no parallel negotiation authority is introduced.</p>
 */
public final class DiplomaticCounterOfferService {
    private static final String COUNTER_PREFIX = "counter-proposal:";

    private DiplomaticCounterOfferService() {
        throw new AssertionError("No instances");
    }

    /**
     * Replaces one open proposal with a bounded reversed counter-offer.
     *
     * @param lifecycle authoritative Stage-21C lifecycle service
     * @param proposalId exact currently open proposal identity
     * @param counterKind counter proposal family
     * @param demands terms requested by the countering faction from the original proposer
     * @param concessions terms offered by the countering faction
     * @param deadlineTick future response deadline for the counter-offer
     * @return newly persistent open counter-offer
     */
    public static Proposal counter(
            DiplomaticLifecycleService lifecycle,
            String proposalId,
            ProposalKind counterKind,
            List<Term> demands,
            List<Term> concessions,
            long deadlineTick) {
        DiplomaticLifecycleService service = Objects.requireNonNull(lifecycle, "Diplomatic lifecycle not set");
        String id = requireText(proposalId, "Proposal ID");
        Proposal current = service.snapshot().proposals().stream()
                .filter(proposal -> proposal.proposalId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown proposal: " + id));

        service.reject(id);
        return service.propose(new ProposalRequest(
                COUNTER_PREFIX + current.proposalId(),
                current.recipientFactionId(),
                current.proposerFactionId(),
                Objects.requireNonNull(counterKind, "Counter proposal kind not set"),
                current.issueId(),
                List.copyOf(Objects.requireNonNull(demands, "Counter demands not set")),
                List.copyOf(Objects.requireNonNull(concessions, "Counter concessions not set")),
                deadlineTick));
    }

    /**
     * Resolves persisted counter-offer lineage without interpreting ordinary strategic-goal IDs.
     *
     * @param proposal proposal to inspect
     * @return parent proposal identity only for a counter-offer created by this boundary
     */
    public static Optional<String> causalProposalId(Proposal proposal) {
        Proposal checked = Objects.requireNonNull(proposal, "Proposal not set");
        if (!checked.sourceGoalId().startsWith(COUNTER_PREFIX)) {
            return Optional.empty();
        }
        String parent = checked.sourceGoalId().substring(COUNTER_PREFIX.length()).strip();
        if (parent.isEmpty()) {
            throw new IllegalArgumentException("Counter-offer source is missing its causal proposal ID");
        }
        return Optional.of(parent);
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
