package com.spacesim.ship;

import com.spacesim.ship.ObservedThreatAssessmentService.ObservedContact;
import com.spacesim.ship.ObservedTacticalIntentPlanner.TacticalContext;
import com.spacesim.ship.ObservedTacticalIntentPlanner.TacticalIntent;
import com.spacesim.ship.ObservedTacticalIntentPlanner.TacticalPosture;

import java.util.List;
import java.util.Objects;

/**
 * Pure Stage-19E raid composition over the existing Stage-19B tactical intent planner.
 *
 * <p>The caller supplies only contacts inside the raider actor's information domain. This wrapper
 * requests {@link TacticalPosture#INTERCEPT}; target ordering, movement and fire admission remain
 * owned by {@link ObservedTacticalIntentPlanner}, while actual motion, ammunition, weapons, damage
 * and destruction remain owned by the production Stage-17.5 runtimes.</p>
 */
public final class RaidTacticalPlanner {
    private final ObservedTacticalIntentPlanner tacticalPlanner;

    /** Creates a raid planner using the production Stage-19B tactical planner. */
    public RaidTacticalPlanner() {
        this(new ObservedTacticalIntentPlanner());
    }

    /**
     * Creates a raid planner with an explicit tactical dependency.
     *
     * @param tacticalPlanner production Stage-19B planner
     */
    public RaidTacticalPlanner(ObservedTacticalIntentPlanner tacticalPlanner) {
        this.tacticalPlanner = Objects.requireNonNull(tacticalPlanner, "tacticalPlanner");
    }

    /**
     * Produces intercept intent from actor-visible raid contacts only.
     *
     * <p>An unknown-disposition contact may influence movement under the inherited Stage-19B rules,
     * but autonomous fire remains prohibited until the actor knows the contact is hostile and has a
     * sufficient production track. No contact absent from {@code contacts} can influence the result.</p>
     *
     * @param contacts raid contacts visible to this actor only
     * @param actorXM raider x position in meters
     * @param actorYM raider y position in meters
     * @param nowSeconds authoritative current simulation time
     * @param tacticalReferenceRangeM positive Stage-19A range normalization scale
     * @param freshnessReferenceSeconds positive Stage-19A freshness scale
     * @return immutable Stage-19B intercept intent
     */
    public TacticalIntent intercept(
            List<ObservedContact> contacts,
            double actorXM,
            double actorYM,
            double nowSeconds,
            double tacticalReferenceRangeM,
            double freshnessReferenceSeconds) {
        Objects.requireNonNull(contacts, "contacts");
        TacticalContext context = new TacticalContext(
                TacticalPosture.INTERCEPT,
                actorXM,
                actorYM,
                false,
                0d,
                0d,
                0d,
                nowSeconds,
                tacticalReferenceRangeM,
                freshnessReferenceSeconds);
        return tacticalPlanner.plan(contacts, context);
    }
}
