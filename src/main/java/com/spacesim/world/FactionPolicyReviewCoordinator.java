package com.spacesim.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Stage-17F.6 deterministic coordinator for autonomous faction policy reviews.
 *
 * <p>The caller explicitly supplies the factions that are allowed to use autonomous policy review.
 * The coordinator never discovers or enables player factions implicitly. Stable faction IDs are
 * normalized, deduplicated and sorted before planning so identical world state and command input
 * produce the same review order.</p>
 *
 * <p>For each faction the coordinator derives the current doctrine-backed fiscal response profile,
 * builds a read-only fiscal plan, and then claims the shared persistent review window at most once.
 * Policy mutation happens only after a successful common claim. This establishes the orchestration
 * seam that later stock/resilience reviewers can join without independently consuming the same
 * review window.</p>
 */
public final class FactionPolicyReviewCoordinator {
    private FactionPolicyReviewCoordinator() {
        throw new AssertionError("Utility class");
    }

    /**
     * Reviews the explicitly authorized autonomous factions using their stable staggered default cadence.
     *
     * @param world authoritative world runtime
     * @param autonomousFactionContentIds stable IDs explicitly authorized for autonomous review
     * @return deterministic immutable report in stable faction-ID order
     */
    public static Report reviewFiscalPolicies(
            WorldSimulation world,
            Collection<String> autonomousFactionContentIds) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "WorldSimulation not set");
        List<String> factionIds = normalizedFactionIds(autonomousFactionContentIds);
        long observationTick = checkedWorld.getAuthoritativeWorldTick();
        List<FactionReview> reviews = new ArrayList<>(factionIds.size());

        for (String factionId : factionIds) {
            FactionPolicyReviewCadence cadence = FactionPolicyReviewCadence.defaultForFaction(factionId);
            FactionPolicyReviewState reviewState = checkedWorld.findFactionPolicyReviewState(factionId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Faction has no policy-review state: " + factionId));
            FactionFiscalReviewProfile profile = WorldFactionFiscalReviewProfileSelector.select(
                    checkedWorld, factionId);
            FactionFiscalPolicyReviewer.Plan fiscalPlan = FactionFiscalPolicyReviewer.plan(
                    checkedWorld, factionId, profile);

            if (!cadence.isDue(reviewState, observationTick)
                    || !checkedWorld.tryBeginFactionPolicyReview(factionId, cadence)) {
                reviews.add(new FactionReview(
                        factionId,
                        false,
                        unclaimedFiscalResult(fiscalPlan)));
                continue;
            }

            FactionFiscalPolicyReviewer.Result fiscalResult = FactionFiscalPolicyReviewer.applyClaimed(
                    checkedWorld, factionId, fiscalPlan);
            reviews.add(new FactionReview(factionId, true, fiscalResult));
        }

        return new Report(observationTick, List.copyOf(reviews));
    }

    private static FactionFiscalPolicyReviewer.Result unclaimedFiscalResult(
            FactionFiscalPolicyReviewer.Plan plan) {
        return new FactionFiscalPolicyReviewer.Result(
                false,
                false,
                plan.zone(),
                plan.liquidityShortfallBasisPoints(),
                plan.previousPolicy(),
                plan.previousPolicy());
    }

    private static List<String> normalizedFactionIds(Collection<String> factionContentIds) {
        Collection<String> checked = Objects.requireNonNull(
                factionContentIds, "Autonomous faction IDs not set");
        TreeSet<String> sorted = new TreeSet<>();
        for (String rawId : checked) {
            String factionId = Objects.requireNonNull(rawId, "Autonomous faction ID not set").strip();
            if (factionId.isEmpty()) {
                throw new IllegalArgumentException("Autonomous faction ID cannot be blank");
            }
            sorted.add(factionId);
        }
        return List.copyOf(sorted);
    }

    /**
     * Result for one explicitly authorized faction.
     *
     * @param factionContentId stable faction content ID
     * @param reviewClaimed whether this coordinator call claimed the common review window
     * @param fiscalReview fiscal plan/result observed during this coordinator call
     */
    public record FactionReview(
            String factionContentId,
            boolean reviewClaimed,
            FactionFiscalPolicyReviewer.Result fiscalReview) {

        /**
         * Validates one immutable faction review result.
         *
         * @param factionContentId stable faction content ID
         * @param reviewClaimed whether the common window was claimed
         * @param fiscalReview fiscal result
         */
        public FactionReview {
            factionContentId = Objects.requireNonNull(factionContentId, "Faction content ID not set").strip();
            if (factionContentId.isEmpty()) {
                throw new IllegalArgumentException("Faction content ID cannot be blank");
            }
            Objects.requireNonNull(fiscalReview, "Fiscal review result not set");
            if (reviewClaimed != fiscalReview.reviewClaimed()) {
                throw new IllegalArgumentException("Coordinator and fiscal claim flags must agree");
            }
        }
    }

    /**
     * Immutable report for one coordinator observation tick.
     *
     * @param observationTick authoritative world tick observed before any review mutation
     * @param factionReviews stable-ID ordered faction results
     */
    public record Report(long observationTick, List<FactionReview> factionReviews) {

        /**
         * Validates one immutable coordinator report.
         *
         * @param observationTick authoritative world tick
         * @param factionReviews stable-ID ordered faction results
         */
        public Report {
            if (observationTick < 0L) {
                throw new IllegalArgumentException("Observation tick cannot be negative");
            }
            factionReviews = List.copyOf(Objects.requireNonNull(
                    factionReviews, "Faction review results not set"));
        }

        /** @return number of factions that claimed the common review window */
        public long claimedReviewCount() {
            return factionReviews.stream().filter(FactionReview::reviewClaimed).count();
        }

        /** @return number of fiscal policies changed after a successful review claim */
        public long changedFiscalPolicyCount() {
            return factionReviews.stream()
                    .filter(review -> review.fiscalReview().policyChanged())
                    .count();
        }
    }
}
