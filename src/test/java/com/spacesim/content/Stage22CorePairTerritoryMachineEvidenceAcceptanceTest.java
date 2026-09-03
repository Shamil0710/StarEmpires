package com.spacesim.content;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.world.FactionEconomicState;
import com.spacesim.world.FactionIdentityResolver;
import com.spacesim.world.FactionStrategicState;
import com.spacesim.world.FleetId;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.Stage22TerritorialTransitionProbe;
import com.spacesim.world.TerritorialTransitionService;
import com.spacesim.world.WorldSimulation;
import com.spacesim.world.WorldState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M22.6 B15 evidence over the accepted Stage-21F territorial transition/control authority. */
class Stage22CorePairTerritoryMachineEvidenceAcceptanceTest {
    private static final String EMPIRE = Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID;
    private static final String UNION = Stage22CorePairBalanceEvidence.UNION_FACTION_ID;
    private static final StarSystemId TARGET = DemoGalaxyFactory.FRONTIER_SYSTEM_ID;

    @Test
    void b15MirrorsCoreInvaderIdentityAndRequiresSustainedPhysicalOccupationBeforeClaim() {
        var vector = Stage22CorePairMachineEvidenceBatch.runScenario(
                "B15",
                "territory_occupation",
                "stage21f.current",
                Stage22CorePairExperimentProtocol.pairedSchedule(1),
                (scenario, variant, profile, coordinate) -> {
                    Fixture fixture = fixture(coordinate.seed());
                    boolean mirrored = coordinate.permutation()
                            == Stage22CorePairExperimentProtocol.Permutation.MIRRORED;
                    String invader = mirrored ? UNION : EMPIRE;
                    int invaderRuntimeId = fixture.identities().runtimeId(invader).orElseThrow();
                    FleetId invasionFleet = new FleetId(22_615_000L + (mirrored ? 2L : 1L));
                    var observed = Stage22TerritorialTransitionProbe.run(
                            fixture.world(),
                            fixture.identities(),
                            invader,
                            invaderRuntimeId,
                            invasionFleet,
                            TARGET);

                    return payload(
                            Map.of(
                                    "secured_ticks", (double) observed.securedTicks(),
                                    "claim_created", observed.claimCreated() ? 1d : 0d),
                            Map.of(
                                    "initial_physical_gate", observed.initialPhysicalGate() ? 1d : 0d,
                                    "physical_claim_gate", observed.physicalClaimGate() ? 1d : 0d,
                                    "sovereignty_not_immediate", observed.sovereigntyNotImmediate() ? 1d : 0d,
                                    "stable_core_invader", invader.equals(EMPIRE) || invader.equals(UNION) ? 1d : 0d),
                            observed.initialPhysicalGate()
                                            && observed.physicalClaimGate()
                                            && observed.sovereigntyNotImmediate()
                                    ? List.of()
                                    : List.of("territorial_transition_authority_drift"));
                });

        assertTrue(vector.metricMeans().get("secured_ticks")
                >= TerritorialTransitionService.REQUIRED_OCCUPATION_TICKS);
        assertEquals(1d, vector.metricMeans().get("claim_created"));
        assertEquals(1d, vector.guardMetricMeans().get("initial_physical_gate"));
        assertEquals(1d, vector.guardMetricMeans().get("physical_claim_gate"));
        assertEquals(1d, vector.guardMetricMeans().get("sovereignty_not_immediate"));
        assertEquals(1d, vector.guardMetricMeans().get("stable_core_invader"));
        assertEquals(0, vector.hardRuleBreachCount());
    }

    private static Stage22CorePairMachineEvidenceBatch.ObservationPayload payload(
            Map<String, Double> metrics,
            Map<String, Double> guards,
            List<String> breaches) {
        return new Stage22CorePairMachineEvidenceBatch.ObservationPayload(metrics, guards, breaches);
    }

    private static Fixture fixture(long seed) {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState base = DemoGalaxyFactory.create(seed).snapshot();
        List<FactionStrategicState> strategies = new ArrayList<>();
        for (FactionStrategicState strategy : base.factionStrategies()) {
            List<StarSystemId> controlled = strategy.controlledSystems().stream()
                    .filter(systemId -> !systemId.equals(TARGET))
                    .toList();
            strategies.add(new FactionStrategicState(
                    strategy.factionContentId(),
                    strategy.minimumMarketAccessRelation(),
                    strategy.relations(),
                    controlled,
                    strategy.stationTaxBasisPoints(),
                    strategy.foreignTerritoryTariffBasisPoints(),
                    strategy.stockPolicies(),
                    strategy.productionPolicies(),
                    strategy.strategicGoals()));
        }
        appendStrategyIfMissing(strategies, EMPIRE);
        appendStrategyIfMissing(strategies, UNION);

        List<FactionEconomicState> factions = new ArrayList<>(base.factions());
        appendEconomyIfMissing(factions, EMPIRE);
        appendEconomyIfMissing(factions, UNION);

        WorldState state = new WorldState(
                WorldState.CURRENT_VERSION,
                base.topology(),
                base.systems(),
                factions,
                strategies,
                base.nextConstructionProjectIdValue(),
                base.constructionProjects(),
                base.factionEconomicPressures(),
                base.nextFleetIdValue(),
                base.fleets(),
                base.fleetJumps(),
                base.factionIdentities());
        WorldSimulation world = WorldSimulation.restore(
                state,
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        return new Fixture(world, FactionIdentityResolver.createDefault(content, base.factionIdentities()));
    }

    private static void appendStrategyIfMissing(List<FactionStrategicState> strategies, String factionId) {
        if (strategies.stream().noneMatch(strategy -> strategy.factionContentId().equals(factionId))) {
            strategies.add(new FactionStrategicState(factionId, 0, List.of(), List.of()));
        }
    }

    private static void appendEconomyIfMissing(List<FactionEconomicState> factions, String factionId) {
        if (factions.stream().noneMatch(faction -> faction.factionContentId().equals(factionId))) {
            factions.add(new FactionEconomicState(factionId, 0L, 0L, 0L));
        }
    }

    private record Fixture(WorldSimulation world, FactionIdentityResolver identities) {}
}
