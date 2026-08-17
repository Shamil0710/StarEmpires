package com.spacesim.ship;

import com.spacesim.ship.ObservedThreatAssessmentService.ObservedContact;
import com.spacesim.ship.ObservedTacticalIntentPlanner.TacticalContext;
import com.spacesim.ship.ObservedTacticalIntentPlanner.TacticalIntent;
import com.spacesim.ship.ObservedTacticalIntentPlanner.TacticalPosture;

import java.util.List;
import java.util.Objects;

/**
 * Pure Stage-19D convoy protection composition over the Stage-19B tactical intent planner.
 *
 * <p>The protected convoy position and contact collection are supplied by the caller's information
 * domain. This class never discovers enemies, reads hidden faction state, integrates movement or
 * resolves weapons. It only requests the existing {@link TacticalPosture#SCREEN} posture so the
 * common Stage-19B command adapter and Stage-17.5 physical runtimes remain authoritative.</p>
 */
public final class ConvoyProtectionPlanner {
    private final ObservedTacticalIntentPlanner tacticalPlanner;

    /** Creates a convoy planner using the production Stage-19B tactical planner. */
    public ConvoyProtectionPlanner() {
        this(new ObservedTacticalIntentPlanner());
    }

    /**
     * Creates a convoy planner with an explicit tactical dependency.
     *
     * @param tacticalPlanner production Stage-19B planner
     */
    public ConvoyProtectionPlanner(ObservedTacticalIntentPlanner tacticalPlanner) {
        this.tacticalPlanner = Objects.requireNonNull(tacticalPlanner, "tacticalPlanner");
    }

    /**
     * Produces screen intent for one physical escort around a known protected convoy position.
     *
     * <p>Unknown-disposition contacts may cause cautious screening movement under the inherited
     * Stage-19B rules, but firing remains restricted to actor-known hostile tracks with sufficient
     * information quality. With no actionable actor-visible contact the result is a no-target
     * SCREEN intent rather than an omniscient intercept.</p>
     *
     * @param contacts contacts visible to this escort actor only
     * @param escortXM escort's own known x position in meters
     * @param escortYM escort's own known y position in meters
     * @param protectedXM protected convoy x position in meters
     * @param protectedYM protected convoy y position in meters
     * @param screenRadiusM desired physical screen offset from the protected convoy
     * @param nowSeconds authoritative current simulation time
     * @param tacticalReferenceRangeM positive Stage-19A range normalization scale
     * @param freshnessReferenceSeconds positive Stage-19A freshness scale
     * @return immutable Stage-19B SCREEN intent
     */
    public TacticalIntent screen(
            List<ObservedContact> contacts,
            double escortXM,
            double escortYM,
            double protectedXM,
            double protectedYM,
            double screenRadiusM,
            double nowSeconds,
            double tacticalReferenceRangeM,
            double freshnessReferenceSeconds) {
        Objects.requireNonNull(contacts, "contacts");
        TacticalContext context = new TacticalContext(
                TacticalPosture.SCREEN,
                escortXM,
                escortYM,
                true,
                protectedXM,
                protectedYM,
                screenRadiusM,
                nowSeconds,
                tacticalReferenceRangeM,
                freshnessReferenceSeconds);
        return tacticalPlanner.plan(contacts, context);
    }
}
