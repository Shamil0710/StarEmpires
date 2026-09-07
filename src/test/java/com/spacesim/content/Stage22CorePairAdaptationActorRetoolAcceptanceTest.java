package com.spacesim.content;

import com.spacesim.persistence.Stage22IndustrialUnionProductionStateCodec;
import com.spacesim.world.FactionActorObservationSnapshot;
import com.spacesim.world.FactionActorObservationSnapshot.ActorObservation;
import com.spacesim.world.FactionActorObservationSnapshot.Domain;
import com.spacesim.world.FactionActorObservationSnapshot.InterestKind;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;
import com.spacesim.world.FactionInterestResolver;
import com.spacesim.world.FactionLivingActorState;
import com.spacesim.world.FactionStrategicGoalCandidateResolver;
import com.spacesim.world.FactionStrategicGoalPlanner;
import com.spacesim.world.FactionStrategicIntentState;
import com.spacesim.world.FactionStrategicIntentStateCodec;
import com.spacesim.world.StrategicGoalCandidate;
import com.spacesim.world.StrategicGoalType;
import com.spacesim.world.StrategicPlanningEnvelope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 B17 composition evidence: bounded new-enemy knowledge must drive an ordinary strategic
 * decision before the Industrial Union pays the already-authored finite series-change burden.
 *
 * <p>This test does not create an adaptation score or a faction-specific planner. It composes the
 * existing Stage-21A actor observation/interest authority, Stage-22 declarative doctrine feeding the
 * common Stage-21B candidate/planner authority, ordinary intent persistence, and the existing M22.4
 * Industrial Union yard/retool state. During the retool both the previous logistics family and the
 * requested screen family are unavailable, making adaptation carry an explicit production
 * opportunity cost instead of a free counter-switch.</p>
 */
class Stage22CorePairAdaptationActorRetoolAcceptanceTest {
    private static final String UNION_ID = Stage22CorePairBalanceEvidence.UNION_FACTION_ID;
    private static final String LOGISTICS_FAMILY = "ship_family.industrial_union.freight";
    private static final String SCREEN_FAMILY = "ship_family.industrial_union.destroyer";
    private static final String FINGERPRINT =
            "1717171717171717171717171717171717171717171717171717171717171717";
    private static final long REVIEW_TICK = 170L;
    private static final StrategicPlanningEnvelope BUDGET =
            new StrategicPlanningEnvelope(16L, 24L, 8L, 32L);

    @Test
    void b17ObservedEnemyPressureSelectsPersistedDefenseThenPaysFiniteRetoolOpportunityCost() {
        var catalog = Stage22FactionProfileLoader.loadDefault();
        var doctrine = catalog.findDoctrine(catalog.findProfileForFaction(UNION_ID).doctrineProfileRef())
                .strategicDoctrine();

        var noThreatTrace = FactionInterestResolver.resolve(observations(false));
        List<StrategicGoalCandidate> noThreatCandidates =
                FactionStrategicGoalCandidateResolver.resolve(noThreatTrace, doctrine);
        assertFalse(noThreatCandidates.stream().anyMatch(candidate -> candidate.type() == StrategicGoalType.DEFEND),
                "B17 adaptation must not manufacture an unobserved enemy pressure");

        var threatTrace = FactionInterestResolver.resolve(observations(true));
        List<StrategicGoalCandidate> threatCandidates =
                FactionStrategicGoalCandidateResolver.resolve(threatTrace, doctrine);
        assertTrue(threatCandidates.stream().anyMatch(candidate -> candidate.type() == StrategicGoalType.DEFEND),
                "actor-known border threat must expose an ordinary defensive candidate");
        var decision = FactionStrategicGoalPlanner.review(
                FactionLivingActorState.initial(UNION_ID, REVIEW_TICK),
                FactionStrategicIntentState.initial(UNION_ID),
                threatCandidates,
                BUDGET,
                REVIEW_TICK);
        assertEquals(1, decision.state().activeGoals().size());
        assertEquals(StrategicGoalType.DEFEND, decision.state().activeGoals().get(0).type(),
                "with only the observed new-enemy pressure, common planner must select defense");

        byte[] intentCheckpoint = FactionStrategicIntentStateCodec.encode(List.of(decision.state()));
        var restoredIntent = FactionStrategicIntentStateCodec.decode(intentCheckpoint);
        assertEquals(List.of(decision.state()), restoredIntent);
        assertArrayEquals(intentCheckpoint, FactionStrategicIntentStateCodec.encode(restoredIntent));

        var unqualified = Stage22IndustrialUnionProductionState.unqualifiedYard(
                Stage22IndustrialUnionIndustrialProgram.YARD_ID);
        var logisticsPending = Stage22IndustrialUnionIndustrialProgram.beginRetool(unqualified, LOGISTICS_FAMILY);
        var logisticsPaid = Stage22IndustrialUnionIndustrialProgram.applyRetoolInputs(
                logisticsPending,
                logisticsPending.retoolWorkRemainingSeconds(),
                logisticsPending.retoolEnergyRemainingJ());
        var logisticsQualified = Stage22IndustrialUnionIndustrialProgram.completeRetool(logisticsPaid);
        Stage22IndustrialUnionIndustrialProgram.modifierFor(logisticsQualified, LOGISTICS_FAMILY);

        var adaptation = Stage22IndustrialUnionIndustrialProgram.beginRetool(logisticsQualified, SCREEN_FAMILY);
        long totalWork = adaptation.retoolWorkRemainingSeconds();
        long totalEnergy = adaptation.retoolEnergyRemainingJ();
        assertTrue(totalWork > 0L);
        assertTrue(totalEnergy > 0L);
        assertFamilyBlocked(adaptation, LOGISTICS_FAMILY);
        assertFamilyBlocked(adaptation, SCREEN_FAMILY);

        long firstWorkPayment = Math.max(1L, totalWork / 2L);
        long firstEnergyPayment = Math.max(1L, totalEnergy / 2L);
        var midpoint = Stage22IndustrialUnionIndustrialProgram.applyRetoolInputs(
                adaptation, firstWorkPayment, firstEnergyPayment);
        assertTrue(midpoint.retooling());
        assertTrue(midpoint.retoolWorkRemainingSeconds() > 0L);
        assertTrue(midpoint.retoolEnergyRemainingJ() > 0L);
        assertFamilyBlocked(midpoint, LOGISTICS_FAMILY);
        assertFamilyBlocked(midpoint, SCREEN_FAMILY);

        var sidecar = new Stage22IndustrialUnionProductionState(
                Stage22IndustrialUnionProductionState.CURRENT_VERSION,
                UNION_ID,
                FINGERPRINT,
                1L,
                List.of(midpoint));
        byte[] productionCheckpoint = Stage22IndustrialUnionProductionStateCodec.encode(sidecar);
        var restoredProduction = Stage22IndustrialUnionProductionStateCodec.decode(productionCheckpoint);
        assertEquals(sidecar.yards(), restoredProduction.yards());
        assertArrayEquals(productionCheckpoint, Stage22IndustrialUnionProductionStateCodec.encode(restoredProduction));

        var restoredMidpoint = restoredProduction.yards().get(0);
        var fullyPaid = Stage22IndustrialUnionIndustrialProgram.applyRetoolInputs(
                restoredMidpoint,
                restoredMidpoint.retoolWorkRemainingSeconds(),
                restoredMidpoint.retoolEnergyRemainingJ());
        var screenQualified = Stage22IndustrialUnionIndustrialProgram.completeRetool(fullyPaid);
        var screenModifier = Stage22IndustrialUnionIndustrialProgram.modifierFor(screenQualified, SCREEN_FAMILY);
        assertFalse(screenQualified.retooling());
        assertTrue(screenModifier.workMultiplier() > 0d);
        assertTrue(screenModifier.energyMultiplier() > 0d);
        assertThrows(IllegalStateException.class,
                () -> Stage22IndustrialUnionIndustrialProgram.modifierFor(screenQualified, LOGISTICS_FAMILY),
                "completed adaptation must retain the series-change opportunity cost until a later paid retool back");

        Stage22CorePairEvidenceArchive.write(
                "B17-actor-bounded-adaptation-paid-retool",
                Map.of(
                        "factionId", UNION_ID,
                        "observedThreatTarget", "system:stage22-new-enemy-border",
                        "selectedGoal", decision.state().activeGoals().get(0).type().name(),
                        "sourceFamily", LOGISTICS_FAMILY,
                        "targetFamily", SCREEN_FAMILY,
                        "retoolWorkSeconds", totalWork,
                        "retoolEnergyJ", totalEnergy,
                        "midpointWorkRemainingSeconds", midpoint.retoolWorkRemainingSeconds(),
                        "midpointEnergyRemainingJ", midpoint.retoolEnergyRemainingJ(),
                        "oldFamilyBlockedDuringRetool", true,
                        "targetFamilyBlockedDuringRetool", true,
                        "intentCheckpointBytes", intentCheckpoint.length,
                        "productionCheckpointBytes", productionCheckpoint.length),
                "Industrial Union adaptation is causally gated by actor-bounded Stage-21A security evidence and a common Stage-21B DEFEND decision. The response changes from the qualified logistics series to the destroyer/screen series only through the existing finite M22.4 retool contract. Positive work/energy debt, blocked old/new production during changeover, midpoint binary persistence and the need for a later paid retool to regain logistics capacity are preserved as raw opportunity-cost dimensions; no faction-specific hidden counter bonus or omniscient enemy lookup is used.");
    }

    private static FactionActorObservationSnapshot observations(boolean includeThreat) {
        List<ActorObservation> security = includeThreat
                ? List.of(new ActorObservation(
                        Domain.SECURITY,
                        InterestKind.BORDER_SECURITY,
                        "system:stage22-new-enemy-border",
                        9_000,
                        new ObservationEvidence(
                                ObservationChannel.LOCAL_SENSOR_REPORT,
                                "sensor-report:stage22-new-enemy-border",
                                REVIEW_TICK,
                                REVIEW_TICK + 16L)))
                : List.of();
        return new FactionActorObservationSnapshot(
                UNION_ID,
                REVIEW_TICK,
                List.of(),
                List.of(),
                security,
                List.of());
    }

    private static void assertFamilyBlocked(
            Stage22IndustrialUnionProductionState.YardSeriesState state,
            String familyId) {
        assertTrue(state.retooling());
        assertThrows(IllegalStateException.class,
                () -> Stage22IndustrialUnionIndustrialProgram.modifierFor(state, familyId));
    }
}
