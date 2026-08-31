package com.spacesim.content;

import com.spacesim.LargeDemoGalaxyFactory;
import com.spacesim.content.Stage22FactionProfileCatalog.AuthoritySeam;
import com.spacesim.content.Stage22FactionProfileCatalog.PolicyKind;
import com.spacesim.world.FactionActorObservationSnapshot.InterestKind;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;
import com.spacesim.world.FactionIdentityResolver;
import com.spacesim.world.FactionLivingActorState;
import com.spacesim.world.FactionStrategicGoalPlanner;
import com.spacesim.world.FactionStrategicIntentState;
import com.spacesim.world.StrategicGoalCandidate;
import com.spacesim.world.StrategicGoalEvidence;
import com.spacesim.world.StrategicGoalOutcomeSignal;
import com.spacesim.world.StrategicGoalType;
import com.spacesim.world.StrategicPlanningEnvelope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** M22.1 architecture and core-pair proof over existing identity and strategic authorities. */
class Stage22FactionProfileAuthorityAcceptanceTest {
    private static final String EMPIRE_ID = "faction.imperial_directorate";
    private static final String UNION_ID = "faction.industrial_combine";

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
    void equivalentEvidenceDivergesOnlyThroughExplicitProfileDoctrine() {
        Stage22FactionProfileCatalog catalog = Stage22FactionProfileLoader.loadDefault();
        var empireDoctrine = catalog.findDoctrine(catalog.findProfileForFaction(EMPIRE_ID).doctrineProfileRef())
                .strategicDoctrine();
        var unionDoctrine = catalog.findDoctrine(catalog.findProfileForFaction(UNION_ID).doctrineProfileRef())
                .strategicDoctrine();

        List<StrategicGoalCandidate> empireOptions = supplyOptions(
                empireDoctrine.preferenceBasisPoints(StrategicGoalType.DEFEND),
                empireDoctrine.preferenceBasisPoints(StrategicGoalType.STOCKPILE));
        List<StrategicGoalCandidate> unionOptions = supplyOptions(
                unionDoctrine.preferenceBasisPoints(StrategicGoalType.DEFEND),
                unionDoctrine.preferenceBasisPoints(StrategicGoalType.STOCKPILE));

        var empire = review(EMPIRE_ID, empireOptions);
        var union = review(UNION_ID, unionOptions);
        assertEquals(StrategicGoalType.DEFEND, empire.state().activeGoals().get(0).type());
        assertEquals(StrategicGoalType.STOCKPILE, union.state().activeGoals().get(0).type());
        assertNotEquals(
                empire.state().activeGoals().get(0).type(),
                union.state().activeGoals().get(0).type());

        var sameInputsDifferentName = review(UNION_ID, empireOptions);
        assertEquals(
                empire.state().activeGoals().get(0).type(),
                sameInputsDifferentName.state().activeGoals().get(0).type());
        assertEquals(
                empire.projections().stream().map(row -> row.scoreBasisPoints()).toList(),
                sameInputsDifferentName.projections().stream().map(row -> row.scoreBasisPoints()).toList());
    }

    private static FactionStrategicGoalPlanner.PlanningResult review(
            String factionId,
            List<StrategicGoalCandidate> candidates) {
        return FactionStrategicGoalPlanner.review(
                FactionLivingActorState.initial(factionId, 24L),
                FactionStrategicIntentState.initial(factionId),
                candidates,
                StrategicPlanningEnvelope.balanced(5L),
                24L);
    }

    private static List<StrategicGoalCandidate> supplyOptions(int defendPreference, int stockpilePreference) {
        String target = "route:stage22-profile-supply";
        StrategicGoalEvidence evidence = new StrategicGoalEvidence(
                InterestKind.SUPPLY_DEPENDENCY,
                target,
                8_000,
                List.of(new ObservationEvidence(
                        ObservationChannel.ECONOMIC_LEDGER,
                        "economic-ledger:stage22-profile-supply",
                        24L,
                        -1L)));
        return List.of(
                candidate(StrategicGoalType.DEFEND, target, evidence, defendPreference),
                candidate(StrategicGoalType.STOCKPILE, target, evidence, stockpilePreference));
    }

    private static StrategicGoalCandidate candidate(
            StrategicGoalType type,
            String target,
            StrategicGoalEvidence evidence,
            int doctrinePreference) {
        return new StrategicGoalCandidate(
                type,
                target,
                evidence,
                evidence.priorityBasisPoints(),
                8_500,
                8_500,
                doctrinePreference,
                StrategicPlanningEnvelope.balanced(5L),
                List.of(),
                -1L,
                24L,
                StrategicGoalOutcomeSignal.NONE);
    }
}
