package com.spacesim.content;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.constants.Constants;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityState;
import com.spacesim.world.FactionEconomicState;
import com.spacesim.world.FactionIdentityResolver;
import com.spacesim.world.FactionStrategicState;
import com.spacesim.world.FleetForceRegistry;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetReadinessState;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StrategicOperationState;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import com.spacesim.world.TerritorialTransitionService;
import com.spacesim.world.TerritorialTransitionState;
import com.spacesim.world.TerritorialTransitionState.OccupationStatus;
import com.spacesim.world.WorldFactionIdentityState;
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
    private static final int EMPIRE_RUNTIME_ID = Constants.LEGACY_FACTION_COUNT;
    private static final int UNION_RUNTIME_ID = Constants.LEGACY_FACTION_COUNT + 1;
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
                    int invaderRuntimeId = mirrored ? UNION_RUNTIME_ID : EMPIRE_RUNTIME_ID;
                    FleetId invasionFleet = new FleetId(22_615_000L + (mirrored ? 2L : 1L));
                    TerritorialTransitionService service = new TerritorialTransitionService();
                    StrategicOperationState operations = operations(activeInvasion(invasionFleet, invaderRuntimeId));
                    FleetForceRegistry supplied = registry(entry(
                            invasionFleet,
                            invaderRuntimeId,
                            TARGET,
                            new FleetReadinessState(10_000, 10_000, 10_000, 10_000, 10_000, 10_000, 10_000)));

                    long start = fixture.world().getAuthoritativeWorldTick();
                    var initial = service.advance(
                            TerritorialTransitionState.empty(),
                            fixture.world(),
                            operations,
                            supplied,
                            fixture.identities(),
                            1L,
                            start);
                    boolean initialGate = initial.occupation().status() == OccupationStatus.OCCUPYING
                            && !initial.claimCreated()
                            && fixture.world().controllingFaction(TARGET).isEmpty();

                    advanceToAtLeast(
                            fixture.world(),
                            start + TerritorialTransitionService.REQUIRED_OCCUPATION_TICKS);
                    var secured = service.advance(
                            initial.transitions(),
                            fixture.world(),
                            initial.operations(),
                            supplied,
                            fixture.identities(),
                            1L,
                            fixture.world().getAuthoritativeWorldTick());
                    boolean physicalClaimGate = secured.occupation().status() == OccupationStatus.SECURED
                            && secured.claimCreated()
                            && secured.operations().requireOperation(1L).status() == OperationStatus.COMPLETED
                            && fixture.world().findFactionStrategicState(invader).orElseThrow().claimFor(TARGET) != null;
                    boolean sovereigntyNotRecolored = fixture.world().controllingFaction(TARGET).isEmpty();

                    return payload(
                            Map.of(
                                    "secured_ticks", (double) secured.occupation().securedTicks(),
                                    "claim_created", secured.claimCreated() ? 1d : 0d),
                            Map.of(
                                    "initial_physical_gate", initialGate ? 1d : 0d,
                                    "physical_claim_gate", physicalClaimGate ? 1d : 0d,
                                    "sovereignty_not_immediate", sovereigntyNotRecolored ? 1d : 0d,
                                    "stable_core_invader", invader.equals(EMPIRE) || invader.equals(UNION) ? 1d : 0d),
                            initialGate && physicalClaimGate && sovereigntyNotRecolored
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
        strategies.add(new FactionStrategicState(EMPIRE, 0, List.of(), List.of()));
        strategies.add(new FactionStrategicState(UNION, 0, List.of(), List.of()));

        List<FactionEconomicState> factions = new ArrayList<>(base.factions());
        factions.add(new FactionEconomicState(EMPIRE, 0L, 0L, 0L));
        factions.add(new FactionEconomicState(UNION, 0L, 0L, 0L));
        List<WorldFactionIdentityState> identities = new ArrayList<>(base.factionIdentities());
        identities.add(new WorldFactionIdentityState(
                EMPIRE,
                EMPIRE_RUNTIME_ID,
                "Empire M22.6",
                WorldFactionIdentityState.Origin.AUTHORED));
        identities.add(new WorldFactionIdentityState(
                UNION,
                UNION_RUNTIME_ID,
                "Industrial Union M22.6",
                WorldFactionIdentityState.Origin.AUTHORED));

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
                identities);
        WorldSimulation world = WorldSimulation.restore(
                state,
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        return new Fixture(world, FactionIdentityResolver.createDefault(content, identities));
    }

    private static StrategicOperationState operations(OperationState operation) {
        return new StrategicOperationState(2L, List.of(operation));
    }

    private static OperationState activeInvasion(FleetId fleetId, int factionRuntimeId) {
        return new OperationState(
                1L,
                OperationType.INVASION,
                1L,
                1L,
                factionRuntimeId,
                List.of(fleetId),
                TARGET,
                TARGET,
                "system:" + TARGET.value(),
                RulesOfEngagement.DECLARED_HOSTILES,
                new SupplyPolicy(2_000, 2_000, 300L),
                new WithdrawalPolicy(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, 1_500, true, true),
                OperationStatus.ACTIVE,
                0L,
                0L,
                -1L,
                null,
                null);
    }

    private static FleetForceRegistry registry(FleetForceRegistry.Entry entry) {
        return new FleetForceRegistry(List.of(entry));
    }

    private static FleetForceRegistry.Entry entry(
            FleetId fleetId,
            int factionId,
            StarSystemId systemId,
            FleetReadinessState readiness) {
        EntityState entity = new EntityState(
                new EntityId(30_000L + fleetId.value()),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new EntityState.FactionState(factionId),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        return new FleetForceRegistry.Entry(
                fleetId,
                factionId,
                FleetLocationKind.IN_SYSTEM,
                systemId,
                null,
                null,
                entity,
                readiness);
    }

    private static void advanceToAtLeast(WorldSimulation world, long targetTick) {
        int guard = 0;
        while (world.getAuthoritativeWorldTick() < targetTick) {
            world.advanceFrame(1.0f);
            if (++guard > 20_000) {
                throw new AssertionError("world did not reach target authoritative tick");
            }
        }
    }

    private record Fixture(WorldSimulation world, FactionIdentityResolver identities) {}
}
