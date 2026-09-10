package com.spacesim.content;

import com.spacesim.LargeDemoGalaxyFactory;
import com.spacesim.content.Stage22FactionProfileCatalog.AuthoritySeam;
import com.spacesim.content.Stage22FactionProfileCatalog.PolicyKind;
import com.spacesim.world.FactionActorObservationSnapshot;
import com.spacesim.world.FactionActorObservationSnapshot.ActorObservation;
import com.spacesim.world.FactionActorObservationSnapshot.Domain;
import com.spacesim.world.FactionActorObservationSnapshot.InterestKind;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;
import com.spacesim.world.FactionIdentityResolver;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M22.1 architecture and core-pair proof over existing identity and strategic authorities. */
class Stage22FactionProfileAuthorityAcceptanceTest {
    private static final String EMPIRE_ID = "faction.imperial_directorate";
    private static final String UNION_ID = "faction.industrial_combine";
    private static final long REVIEW_TICK = 24L;
    private static final StrategicPlanningEnvelope CONTESTED_BUDGET =
            new StrategicPlanningEnvelope(16L, 24L, 8L, 32L);

    @Test
    void everyProfilePolicyIsDataBoundToTheSingleExistingAuthoritySeam() {
        Stage22FactionProfileCatalog catalog = Stage22FactionProfileLoader.loadDefault();
        Map<PolicyKind, AuthoritySeam> expected = Map.of(
                PolicyKind.INDUSTRIAL, AuthoritySeam.STAGE18_INDUSTRY,
                PolicyKind.PROCUREMENT, AuthoritySeam.FACTION_POLICY_COMMAND,
                PolicyKind.LOGISTICS, AuthoritySeam.STAGE20_FREIGHT,
                PolicyKind.FLEET, AuthoritySeam.STAGE21_FLEET_COMMAND,
                PolicyKind.DIPLOMACY, AuthoritySeam.FACTION_DIPLOMACY,
                PolicyKind.TERRITORY, AuthoritySeam.TERRITORIAL_CONTROL,
                PolicyKind.KNOWLEDGE, AuthoritySeam.DISCOVERY_KNOWLEDGE,
                PolicyKind.RECOVERY, AuthoritySeam.SETTLEMENT_RECOVERY);

        assertEquals(java.util.Set.of(2L), catalog.policyBindings().stream()
                .collect(Collectors.groupingBy(
                        Stage22FactionProfileCatalog.PolicyBindingDefinition::kind,
                        Collectors.counting()))
                .values().stream().collect(Collectors.toSet()));
        catalog.policyBindings().forEach(binding ->
                assertEquals(expected.get(binding.kind()), binding.authoritySeam(), binding.id()));
    }

    @Test
    void publicCorePackagesReuseExistingStableAndRuntimeIdentityOwners() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        var world = LargeDemoGalaxyFactory.createState(22_101L, content);
        FactionIdentityResolver resolver = FactionIdentityResolver.createDefault(content, world.factionIdentities());
        Stage22FactionProfileCatalog catalog = Stage22FactionProfileLoader.loadDefault();

        catalog.systemicProfiles().forEach(profile -> {
            int runtimeId = resolver.runtimeId(profile.stableFactionId()).orElseThrow();
            assertEquals(profile.stableFactionId(), resolver.stableId(runtimeId).orElseThrow());
        });
        assertFalse(resolver.containsStableId("faction.empire"));
        assertFalse(resolver.containsStableId("faction.industrial-union"));
    }

    @Test
    void equivalentActorKnownEvidenceDivergesOnlyThroughExplicitProfileDoctrineAndPersists() {
        Stage22FactionProfileCatalog catalog = Stage22FactionProfileLoader.loadDefault();
        var empireDoctrine = catalog.findDoctrine(catalog.findProfileForFaction(EMPIRE_ID).doctrineProfileRef())
                .strategicDoctrine();
        var unionDoctrine = catalog.findDoctrine(catalog.findProfileForFaction(UNION_ID).doctrineProfileRef())
                .strategicDoctrine();

        var empireTrace = FactionInterestResolver.resolve(actorKnownPressure(EMPIRE_ID, true));
        var unionTrace = FactionInterestResolver.resolve(actorKnownPressure(UNION_ID, true));
        assertEquals(empireTrace.orderedEvidence(), unionTrace.orderedEvidence(),
                "matched actors must reason from identical bounded evidence");

        List<StrategicGoalCandidate> empireOptions =
                FactionStrategicGoalCandidateResolver.resolve(empireTrace, empireDoctrine);
        List<StrategicGoalCandidate> unionOptions =
                FactionStrategicGoalCandidateResolver.resolve(unionTrace, unionDoctrine);
        assertEquals(empireOptions,
                FactionStrategicGoalCandidateResolver.resolve(empireTrace, empireDoctrine),
                "candidate generation must be deterministic");
        assertEquals(unionOptions,
                FactionStrategicGoalCandidateResolver.resolve(unionTrace, unionDoctrine),
                "candidate generation must be deterministic");

        var empire = review(EMPIRE_ID, empireOptions);
        var union = review(UNION_ID, unionOptions);
        assertEquals(1, empire.state().activeGoals().size());
        assertEquals(1, union.state().activeGoals().size());
        assertEquals(StrategicGoalType.DEFEND, empire.state().activeGoals().get(0).type(),
                "Empire doctrine should spend the contested capacity on border defense");
        assertEquals(StrategicGoalType.STOCKPILE, union.state().activeGoals().get(0).type(),
                "Industrial Union doctrine should spend the same capacity on shortage resilience");
        assertNotEquals(
                empire.state().activeGoals().get(0).type(),
                union.state().activeGoals().get(0).type());

        List<StrategicGoalCandidate> unionWithEmpireDoctrine =
                FactionStrategicGoalCandidateResolver.resolve(unionTrace, empireDoctrine);
        assertEquals(empireOptions, unionWithEmpireDoctrine,
                "stable faction identity alone must not alter common candidate generation");
        var sameDoctrineDifferentFaction = review(UNION_ID, unionWithEmpireDoctrine);
        assertEquals(
                empire.state().activeGoals().get(0).type(),
                sameDoctrineDifferentFaction.state().activeGoals().get(0).type(),
                "the same doctrine and evidence must choose the same goal regardless of faction name");

        byte[] checkpoint = FactionStrategicIntentStateCodec.encode(List.of(empire.state(), union.state()));
        List<FactionStrategicIntentState> restored = FactionStrategicIntentStateCodec.decode(checkpoint);
        assertEquals(List.of(empire.state(), union.state()), restored,
                "profile-driven operational intent must survive ordinary Stage-21B persistence");
        assertArrayEquals(checkpoint, FactionStrategicIntentStateCodec.encode(restored),
                "restored intent checkpoint must remain byte deterministic");

        var unionWithoutDeficit = FactionInterestResolver.resolve(actorKnownPressure(UNION_ID, false));
        List<StrategicGoalCandidate> boundedOptions =
                FactionStrategicGoalCandidateResolver.resolve(unionWithoutDeficit, unionDoctrine);
        assertFalse(boundedOptions.stream().anyMatch(option -> option.type() == StrategicGoalType.STOCKPILE),
                "high Union stockpile preference must not manufacture an unobserved shortage");
        assertTrue(unionOptions.stream().anyMatch(option -> option.type() == StrategicGoalType.STOCKPILE));
    }

    private static FactionStrategicGoalPlanner.PlanningResult review(
            String factionId,
            List<StrategicGoalCandidate> candidates) {
        return FactionStrategicGoalPlanner.review(
                FactionLivingActorState.initial(factionId, REVIEW_TICK),
                FactionStrategicIntentState.initial(factionId),
                candidates,
                CONTESTED_BUDGET,
                REVIEW_TICK);
    }

    private static FactionActorObservationSnapshot actorKnownPressure(String factionId, boolean includeDeficit) {
        ActorObservation borderPressure = new ActorObservation(
                Domain.SECURITY,
                InterestKind.BORDER_SECURITY,
                "system:stage22-contested-border",
                8_000,
                new ObservationEvidence(
                        ObservationChannel.LOCAL_SENSOR_REPORT,
                        "sensor-report:stage22-contested-border",
                        REVIEW_TICK,
                        -1L));
        List<ActorObservation> economic = includeDeficit
                ? List.of(new ActorObservation(
                        Domain.ECONOMIC,
                        InterestKind.RESOURCE_DEFICIT,
                        "resource:stage22-reactor-feedstock",
                        8_000,
                        new ObservationEvidence(
                                ObservationChannel.ECONOMIC_LEDGER,
                                "economic-ledger:stage22-reactor-feedstock",
                                REVIEW_TICK,
                                -1L)))
                : List.of();
        return new FactionActorObservationSnapshot(
                factionId,
                REVIEW_TICK,
                economic,
                List.of(),
                List.of(borderPressure),
                List.of());
    }
}
