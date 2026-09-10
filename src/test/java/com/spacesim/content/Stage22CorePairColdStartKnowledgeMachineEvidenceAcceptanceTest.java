package com.spacesim.content;

import com.spacesim.world.FactionActorObservationSnapshot;
import com.spacesim.world.FactionActorObservationSnapshot.ActorObservation;
import com.spacesim.world.FactionActorObservationSnapshot.Domain;
import com.spacesim.world.FactionActorObservationSnapshot.InterestKind;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;
import com.spacesim.world.FactionInterestEvidence;
import com.spacesim.world.FactionInterestResolver;
import com.spacesim.world.FactionLivingActorState;
import com.spacesim.world.FactionStrategicGoalCandidateResolver;
import com.spacesim.world.FactionStrategicGoalPlanner;
import com.spacesim.world.FactionStrategicIntentState;
import com.spacesim.world.StrategicGoalType;
import com.spacesim.world.StrategicPlanningEnvelope;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * M22.6 B02 actor-knowledge equality over the canonical paired cold-start schedule.
 *
 * <p>This is deliberately narrower than complete B02 campaign closure. Every Empire/Industrial
 * Union coordinate receives the same actor-bounded Stage-21A reports and therefore the same
 * identity-neutral knowledge fingerprint. Only after that equality is established are the existing
 * declarative Stage-22 doctrine profiles applied through the common Stage-21 candidate resolver and
 * strategic planner. No hidden world truth, faction-name branch or test-local gameplay score is
 * introduced.</p>
 */
class Stage22CorePairColdStartKnowledgeMachineEvidenceAcceptanceTest {
    private static final String EMPIRE_ID = "faction.imperial_directorate";
    private static final String UNION_ID = "faction.industrial_combine";
    private static final StrategicPlanningEnvelope COLD_START_BUDGET =
            new StrategicPlanningEnvelope(16L, 24L, 8L, 32L);

    @Test
    void b02ThirtyPairedColdStartsHaveIdenticalActorKnowledgeBeforeDoctrineChoice() {
        Stage22FactionProfileCatalog profiles = Stage22FactionProfileLoader.loadDefault();
        var empireDoctrine = profiles.findDoctrine(profiles.findProfileForFaction(EMPIRE_ID).doctrineProfileRef())
                .strategicDoctrine();
        var unionDoctrine = profiles.findDoctrine(profiles.findProfileForFaction(UNION_ID).doctrineProfileRef())
                .strategicDoctrine();
        ArrayList<KnowledgeRow> evidence = new ArrayList<>();

        for (Stage22CorePairExperimentProtocol.RunCoordinate coordinate
                : Stage22CorePairExperimentProtocol.tuningSchedule()) {
            long tick = coordinate.seed();
            var empireTrace = FactionInterestResolver.resolve(snapshot(EMPIRE_ID, coordinate));
            var unionTrace = FactionInterestResolver.resolve(snapshot(UNION_ID, coordinate));

            assertEquals(empireTrace.orderedEvidence(), unionTrace.orderedEvidence(),
                    "matched cold-start actors must receive identical bounded evidence before doctrine");
            String empireFingerprint = knowledgeFingerprint(empireTrace);
            String unionFingerprint = knowledgeFingerprint(unionTrace);
            assertEquals(empireFingerprint, unionFingerprint,
                    "faction identity must not change the identity-neutral actor knowledge fingerprint");

            var empireOptions = FactionStrategicGoalCandidateResolver.resolve(empireTrace, empireDoctrine);
            var unionOptions = FactionStrategicGoalCandidateResolver.resolve(unionTrace, unionDoctrine);
            var empireReview = FactionStrategicGoalPlanner.review(
                    FactionLivingActorState.initial(EMPIRE_ID, tick),
                    FactionStrategicIntentState.initial(EMPIRE_ID),
                    empireOptions,
                    COLD_START_BUDGET,
                    tick);
            var unionReview = FactionStrategicGoalPlanner.review(
                    FactionLivingActorState.initial(UNION_ID, tick),
                    FactionStrategicIntentState.initial(UNION_ID),
                    unionOptions,
                    COLD_START_BUDGET,
                    tick);
            assertFalse(empireReview.state().activeGoals().isEmpty());
            assertFalse(unionReview.state().activeGoals().isEmpty());
            StrategicGoalType empireGoal = empireReview.state().activeGoals().get(0).type();
            StrategicGoalType unionGoal = unionReview.state().activeGoals().get(0).type();
            assertEquals(StrategicGoalType.DEFEND, empireGoal,
                    "matched border/deficit evidence should express Empire preservation doctrine");
            assertEquals(StrategicGoalType.STOCKPILE, unionGoal,
                    "matched border/deficit evidence should express Union flow-resilience doctrine");

            evidence.add(new KnowledgeRow(
                    coordinate.seed(),
                    coordinate.permutation().name(),
                    empireFingerprint,
                    empireTrace.orderedEvidence().size(),
                    empireGoal.name(),
                    unionGoal.name()));
        }

        assertEquals(Stage22CorePairExperimentProtocol.TUNING_SEED_COUNT * 2, evidence.size());
        LinkedHashMap<String, Object> archive = new LinkedHashMap<>();
        archive.put("scenario", "B02");
        archive.put("pairedSeedCount", Stage22CorePairExperimentProtocol.TUNING_SEED_COUNT);
        archive.put("runCount", evidence.size());
        archive.put("rows", evidence);
        Stage22CorePairEvidenceArchive.write(
                "B02-matched-cold-start-actor-knowledge",
                archive,
                "Canonical 30-seed/default+mirrored cold-start schedule. Empire and Industrial Union receive identical Stage-21A actor-bounded observations and identity-neutral knowledge fingerprints for every coordinate. Common Stage-21 candidate/planner logic then diverges only after declarative Stage-22 doctrine is applied (Empire DEFEND, Union STOCKPILE). This closes the actor-knowledge-fingerprint slice only; full generated physical cold-start campaign trajectories remain required before B02 can be COMPLETE.");
    }

    private static FactionActorObservationSnapshot snapshot(
            String factionId,
            Stage22CorePairExperimentProtocol.RunCoordinate coordinate) {
        long tick = coordinate.seed();
        String coordinateId = coordinate.seed() + ":" + coordinate.permutation().name().toLowerCase();
        ActorObservation deficit = new ActorObservation(
                Domain.ECONOMIC,
                InterestKind.RESOURCE_DEFICIT,
                "resource:core-pair-cold-start-critical:" + coordinateId,
                8_000,
                new ObservationEvidence(
                        ObservationChannel.ECONOMIC_LEDGER,
                        "ledger:core-pair-cold-start-critical:" + coordinateId,
                        tick,
                        -1L));
        ActorObservation border = new ActorObservation(
                Domain.SECURITY,
                InterestKind.BORDER_SECURITY,
                "system:core-pair-cold-start-border:" + coordinateId,
                8_000,
                new ObservationEvidence(
                        ObservationChannel.LOCAL_SENSOR_REPORT,
                        "sensor:core-pair-cold-start-border:" + coordinateId,
                        tick,
                        -1L));
        return new FactionActorObservationSnapshot(
                factionId,
                tick,
                List.of(deficit),
                List.of(),
                List.of(border),
                List.of());
    }

    private static String knowledgeFingerprint(FactionInterestResolver.DecisionTrace trace) {
        StringBuilder canonical = new StringBuilder("stage22-b02-actor-knowledge-v1\n");
        for (FactionInterestEvidence item : trace.orderedEvidence()) {
            canonical.append(item.kind().name()).append('|')
                    .append(item.targetId()).append('|')
                    .append(item.priorityBasisPoints()).append('\n');
            for (ActorObservation observation : item.supportingObservations()) {
                canonical.append(observation.domain().name()).append('|')
                        .append(observation.interestKind().name()).append('|')
                        .append(observation.targetId()).append('|')
                        .append(observation.severityBasisPoints()).append('|')
                        .append(observation.evidence().channel().name()).append('|')
                        .append(observation.evidence().provenanceId()).append('|')
                        .append(observation.evidence().observedAtTick()).append('|')
                        .append(observation.evidence().freshUntilTick()).append('\n');
            }
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide mandatory SHA-256", exception);
        }
    }

    private record KnowledgeRow(
            long seed,
            String permutation,
            String knowledgeFingerprint,
            int evidenceRowCount,
            String empireGoal,
            String industrialUnionGoal) { }
}
