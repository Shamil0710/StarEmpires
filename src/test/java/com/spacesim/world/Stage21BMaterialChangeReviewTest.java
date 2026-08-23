package com.spacesim.world;

import com.spacesim.world.FactionActorObservationSnapshot.InterestKind;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;
import com.spacesim.world.FactionLivingActorState.EventWakeup;
import com.spacesim.world.FactionLivingActorState.WakeupReason;
import com.spacesim.world.StrategicGoalState.Lifecycle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage21BMaterialChangeReviewTest {

    @Test
    void materialObservationActorReviewWakesGoalOnceBeforeCadence() {
        FactionLivingActorState initialActor = FactionLivingActorState.initial("faction.alpha", 100L);
        StrategicGoalCandidate initialCandidate = defendCandidate(7_000, "intel:border:v1", 100L);

        var seeded = FactionStrategicGoalPlanner.review(
                initialActor,
                FactionStrategicIntentState.initial("faction.alpha"),
                List.of(initialCandidate),
                StrategicPlanningEnvelope.balanced(20L),
                10L);
        StrategicGoalState seededGoal = seeded.state().activeGoals().get(0);
        assertEquals(110L, seededGoal.nextReviewTick());
        assertEquals(0L, seeded.state().lastActorReviewCount());

        FactionLivingActorState changedActor = initialActor
                .withWakeup(new EventWakeup(
                        WakeupReason.MATERIAL_OBSERVATION_CHANGED,
                        "material-change:border:1",
                        12L,
                        12L))
                .afterReview(12L, 100L);
        StrategicGoalCandidate changedCandidate = defendCandidate(9_000, "intel:border:v2", 100L);

        var awakened = FactionStrategicGoalPlanner.review(
                changedActor,
                seeded.state(),
                List.of(changedCandidate),
                StrategicPlanningEnvelope.balanced(20L),
                12L);
        StrategicGoalState awakenedGoal = awakened.state().activeGoals().get(0);

        assertEquals(seededGoal.goalId(), awakenedGoal.goalId());
        assertEquals(12L, awakenedGoal.updatedAtTick());
        assertEquals(112L, awakenedGoal.nextReviewTick());
        assertEquals(1L, awakened.state().lastActorReviewCount());
        assertEquals(FactionStrategicGoalPlanner.PlanningTrigger.ACTOR_REVIEW_ADVANCED,
                awakened.planningTrigger());
        assertEquals("actor-review-advanced:actor-review:1", awakened.whyNowCode());
        assertEquals("intel:border:v2",
                awakenedGoal.sourceEvidence().provenance().get(0).provenanceId());

        var repeated = FactionStrategicGoalPlanner.review(
                changedActor,
                awakened.state(),
                List.of(changedCandidate),
                StrategicPlanningEnvelope.balanced(20L),
                13L);
        StrategicGoalState repeatedGoal = repeated.state().activeGoals().get(0);

        assertEquals(awakenedGoal.goalId(), repeatedGoal.goalId());
        assertEquals(12L, repeatedGoal.updatedAtTick());
        assertEquals(1L, repeated.state().lastActorReviewCount());
        assertEquals(FactionStrategicGoalPlanner.PlanningTrigger.CADENCE_OR_EXPLICIT,
                repeated.planningTrigger());
    }

    @Test
    void minimumCommitmentDefersNonterminalDisplacementAfterMaterialReview() {
        FactionLivingActorState committedActor = FactionLivingActorState.initial("faction.alpha", 100L)
                .withCommitmentUntilTick(100L);
        StrategicGoalCandidate candidate = defendCandidate(7_000, "intel:border:seed", 100L);
        var seeded = FactionStrategicGoalPlanner.review(
                committedActor,
                FactionStrategicIntentState.initial("faction.alpha"),
                List.of(candidate),
                StrategicPlanningEnvelope.balanced(20L),
                10L);
        String goalId = seeded.state().activeGoals().get(0).goalId();

        FactionLivingActorState changedActor = committedActor
                .withWakeup(new EventWakeup(
                        WakeupReason.MATERIAL_OBSERVATION_CHANGED,
                        "material-change:border:lost-evidence",
                        20L,
                        20L))
                .afterReview(20L, 100L);
        var protectedReview = FactionStrategicGoalPlanner.review(
                changedActor,
                seeded.state(),
                List.of(),
                StrategicPlanningEnvelope.balanced(20L),
                20L);

        assertEquals(goalId, protectedReview.state().openGoals().get(0).goalId());
        assertEquals(Lifecycle.ACTIVE, protectedReview.state().openGoals().get(0).lifecycle());
        assertEquals(1L, protectedReview.state().lastActorReviewCount());

        var afterCommitment = FactionStrategicGoalPlanner.review(
                changedActor,
                protectedReview.state(),
                List.of(),
                StrategicPlanningEnvelope.balanced(20L),
                110L);

        assertTrue(afterCommitment.state().openGoals().isEmpty());
        assertEquals(Lifecycle.CANCELLED, afterCommitment.state().goals().get(0).lifecycle());
    }

    @Test
    void actorReviewConsumptionSurvivesStrategicIntentPersistence() {
        FactionStrategicIntentState state = new FactionStrategicIntentState(
                "faction.alpha", 1L, 7L, List.of());

        byte[] encoded = FactionStrategicIntentStateCodec.encode(List.of(state));
        List<FactionStrategicIntentState> decoded = FactionStrategicIntentStateCodec.decode(encoded);

        assertEquals(List.of(state), decoded);
        assertEquals(7L, decoded.get(0).lastActorReviewCount());
    }

    @Test
    void plannerRejectsActorLifecycleOlderThanPersistedConsumption() {
        FactionLivingActorState staleActor = FactionLivingActorState.initial("faction.alpha", 0L);
        FactionStrategicIntentState futureIntent = new FactionStrategicIntentState(
                "faction.alpha", 1L, 1L, List.of());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> FactionStrategicGoalPlanner.review(
                        staleActor,
                        futureIntent,
                        List.of(),
                        StrategicPlanningEnvelope.ZERO,
                        0L));

        assertTrue(exception.getMessage().contains("future actor review"));
    }

    private static StrategicGoalCandidate defendCandidate(
            int urgency,
            String provenanceId,
            long reviewCadenceTicks) {
        String target = "border:alpha";
        StrategicGoalEvidence evidence = new StrategicGoalEvidence(
                InterestKind.BORDER_SECURITY,
                target,
                urgency,
                List.of(new ObservationEvidence(
                        ObservationChannel.INTELLIGENCE_REPORT,
                        provenanceId,
                        1L,
                        -1L)));
        return new StrategicGoalCandidate(
                StrategicGoalType.DEFEND,
                target,
                evidence,
                urgency,
                10_000,
                10_000,
                10_000,
                StrategicPlanningEnvelope.balanced(5L),
                List.of(),
                -1L,
                reviewCadenceTicks,
                StrategicGoalOutcomeSignal.NONE);
    }
}
