package com.spacesim.content;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.world.CustomsTariffResolver;
import com.spacesim.world.DiplomaticMarketAccessResolver;
import com.spacesim.world.DiplomaticTreatyClauseState;
import com.spacesim.world.DiplomaticTreatyCommand;
import com.spacesim.world.DiplomaticTreatyState;
import com.spacesim.world.FactionDiplomacyState;
import com.spacesim.world.FactionStrategicState;
import com.spacesim.world.SettlementRecoveryService;
import com.spacesim.world.SettlementRecoveryState;
import com.spacesim.world.SettlementRecoveryState.Settlement;
import com.spacesim.world.SettlementRecoveryState.SettlementStatus;
import com.spacesim.world.WorldSimulation;
import com.spacesim.world.WorldState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 machine evidence that crosses the accepted Stage-21G recovery and Stage-17 diplomacy seams.
 *
 * <p>The fixtures only author scenario inputs. Recovery state transitions, treaty persistence, market
 * admission and customs pricing are produced by their existing authorities; this test does not own a
 * balance simulator or faction-specific modifier.</p>
 */
class Stage22CorePairAuthorityMachineEvidenceAcceptanceTest {
    private static final String EMPIRE = Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID;
    private static final String UNION = Stage22CorePairBalanceEvidence.UNION_FACTION_ID;
    private static final String TRADE_LEAGUE = "faction.trade_league";
    private static final String MINERS = "faction.miners";

    @Test
    void b14CorePairPostWarRecoveryKeepsFiniteDemobilizationObligationAfterPlanFinalization() {
        var vector = Stage22CorePairMachineEvidenceBatch.runScenario(
                "B14",
                "post_war_recovery",
                "stage21g.current",
                Stage22CorePairExperimentProtocol.pairedSchedule(1),
                (scenario, variant, profile, coordinate) -> {
                    long tick = coordinate.seed();
                    String demobilizingFaction = coordinate.permutation()
                            == Stage22CorePairExperimentProtocol.Permutation.DEFAULT ? EMPIRE : UNION;
                    Settlement settlement = new Settlement(
                            1L,
                            "proposal.m22.6.b14",
                            "war.m22.6.b14",
                            EMPIRE,
                            UNION,
                            tick,
                            tick,
                            SettlementStatus.PENDING,
                            false);
                    SettlementRecoveryService recovery = new SettlementRecoveryService(new SettlementRecoveryState(
                            SettlementRecoveryState.CURRENT_VERSION,
                            tick,
                            2L,
                            1L,
                            List.of(settlement),
                            List.of(),
                            List.of(),
                            List.of(),
                            List.of()));

                    recovery.registerDemobilization(1L, 22_614L, demobilizingFaction, tick);
                    Settlement finalized = recovery.finalizeRecoveryPlan(1L, tick);
                    var snapshot = recovery.snapshot();
                    boolean finitePendingObligation = snapshot.demobilizations().size() == 1
                            && snapshot.demobilizations().get(0).factionContentId().equals(demobilizingFaction)
                            && snapshot.demobilizations().get(0).status()
                            == SettlementRecoveryState.ObligationStatus.PENDING;
                    boolean executing = finalized.status() == SettlementStatus.EXECUTING;

                    return payload(
                            Map.of(
                                    "demobilization_obligations", (double) snapshot.demobilizations().size(),
                                    "settlement_executing", executing ? 1d : 0d),
                            Map.of(
                                    "finite_pending_obligation", finitePendingObligation ? 1d : 0d,
                                    "stable_core_participant", demobilizingFaction.equals(EMPIRE)
                                            || demobilizingFaction.equals(UNION) ? 1d : 0d),
                            finitePendingObligation && executing
                                    ? List.of()
                                    : List.of("post_war_recovery_obligation_not_preserved"));
                });

        assertEquals(1d, vector.metricMeans().get("demobilization_obligations"));
        assertEquals(1d, vector.metricMeans().get("settlement_executing"));
        assertEquals(1d, vector.guardMetricMeans().get("finite_pending_obligation"));
        assertEquals(1d, vector.guardMetricMeans().get("stable_core_participant"));
        assertEquals(0, vector.hardRuleBreachCount());
    }

    @Test
    void b16PersistedTreatyActuallyOpensDeniedMarketAndRemovesTariffThenBreachRestoresShock() {
        var vector = Stage22CorePairMachineEvidenceBatch.runScenario(
                "B16",
                "treaty_market_access_shock",
                "stage17.current",
                Stage22CorePairExperimentProtocol.pairedSchedule(1),
                (scenario, variant, profile, coordinate) -> {
                    ContentCatalog content = ContentCatalogLoader.loadDefault();
                    WorldState base = DemoGalaxyFactory.createState(coordinate.seed(), content);
                    WorldSimulation world = restore(withB16Pressure(base), content);

                    var beforeAccess = marketAccess(world);
                    var beforeTariff = tariff(world);
                    boolean initiallyDenied = !beforeAccess.allowed()
                            && beforeAccess.reason() == DiplomaticMarketAccessResolver.Reason.RELATION_THRESHOLD_DENY
                            && beforeTariff.basisPoints() == 750;

                    DiplomaticTreatyState offer = world.applyDiplomaticTreatyCommand(new DiplomaticTreatyCommand.Offer(
                            TRADE_LEAGUE,
                            MINERS,
                            List.of(
                                    new DiplomaticTreatyClauseState(
                                            DiplomaticTreatyClauseState.Kind.MARKET_ACCESS,
                                            DiplomaticTreatyClauseState.Direction.MUTUAL,
                                            null),
                                    new DiplomaticTreatyClauseState(
                                            DiplomaticTreatyClauseState.Kind.CUSTOMS_TARIFF_EXEMPTION,
                                            DiplomaticTreatyClauseState.Direction.MUTUAL,
                                            null)),
                            -1L)).treaty();
                    world.applyDiplomaticTreatyCommand(new DiplomaticTreatyCommand.Accept(MINERS, offer.treatyId()));

                    var treatyAccess = marketAccess(world);
                    var treatyTariff = tariff(world);
                    boolean treatyOpened = treatyAccess.allowed()
                            && treatyAccess.reason() == DiplomaticMarketAccessResolver.Reason.EXPLICIT_TREATY_RIGHT
                            && treatyTariff.basisPoints() == 0
                            && treatyTariff.reason() == CustomsTariffResolver.Reason.TREATY_EXEMPTION;

                    String breachingFaction = coordinate.permutation()
                            == Stage22CorePairExperimentProtocol.Permutation.DEFAULT ? TRADE_LEAGUE : MINERS;
                    world.applyDiplomaticTreatyCommand(new DiplomaticTreatyCommand.Breach(
                            breachingFaction,
                            offer.treatyId(),
                            "m22.6-b16-market-access-shock"));
                    var afterAccess = marketAccess(world);
                    var afterTariff = tariff(world);
                    boolean shockRestored = !afterAccess.allowed()
                            && afterAccess.reason() == DiplomaticMarketAccessResolver.Reason.RELATION_THRESHOLD_DENY
                            && afterTariff.basisPoints() == 750
                            && afterTariff.reason() == CustomsTariffResolver.Reason.STANDARD_RATE;

                    return payload(
                            Map.of(
                                    "tariff_before_basis_points", (double) beforeTariff.basisPoints(),
                                    "tariff_during_treaty_basis_points", (double) treatyTariff.basisPoints(),
                                    "tariff_after_breach_basis_points", (double) afterTariff.basisPoints()),
                            Map.of(
                                    "initially_denied", initiallyDenied ? 1d : 0d,
                                    "treaty_opened_market", treatyOpened ? 1d : 0d,
                                    "breach_restored_shock", shockRestored ? 1d : 0d),
                            initiallyDenied && treatyOpened && shockRestored
                                    ? List.of()
                                    : List.of("treaty_market_access_authority_drift"));
                });

        assertEquals(750d, vector.metricMeans().get("tariff_before_basis_points"));
        assertEquals(0d, vector.metricMeans().get("tariff_during_treaty_basis_points"));
        assertEquals(750d, vector.metricMeans().get("tariff_after_breach_basis_points"));
        assertEquals(1d, vector.guardMetricMeans().get("initially_denied"));
        assertEquals(1d, vector.guardMetricMeans().get("treaty_opened_market"));
        assertEquals(1d, vector.guardMetricMeans().get("breach_restored_shock"));
        assertEquals(0, vector.hardRuleBreachCount());
    }

    private static Stage22CorePairMachineEvidenceBatch.ObservationPayload payload(
            Map<String, Double> metrics,
            Map<String, Double> guards,
            List<String> breaches) {
        return new Stage22CorePairMachineEvidenceBatch.ObservationPayload(metrics, guards, breaches);
    }

    private static WorldState withB16Pressure(WorldState base) {
        ArrayList<FactionStrategicState> strategies = new ArrayList<>();
        for (FactionStrategicState strategy : base.factionStrategies()) {
            if (!strategy.factionContentId().equals(TRADE_LEAGUE)) {
                strategies.add(strategy);
                continue;
            }
            strategies.add(new FactionStrategicState(
                    strategy.factionContentId(),
                    50,
                    strategy.relations(),
                    strategy.controlledSystems(),
                    strategy.stationTaxBasisPoints(),
                    strategy.foreignTerritoryTariffBasisPoints(),
                    strategy.stockPolicies(),
                    strategy.productionPolicies(),
                    strategy.strategicGoals(),
                    strategy.territorialClaims(),
                    strategy.territorialControlStates(),
                    strategy.territorialRecognitions(),
                    strategy.constructionRightsGranted(),
                    strategy.doctrine()));
        }
        List<FactionDiplomacyState> diplomacy = List.of(
                FactionDiplomacyState.neutral("faction.neutral"),
                new FactionDiplomacyState(TRADE_LEAGUE, List.of(), List.of(), List.of(), List.of(), 750),
                FactionDiplomacyState.neutral(MINERS));
        return new WorldState(
                base.schemaVersion(),
                base.topology(),
                base.systems(),
                base.factions(),
                strategies,
                base.nextConstructionProjectIdValue(),
                base.constructionProjects(),
                base.factionEconomicPressures(),
                base.nextFleetIdValue(),
                base.fleets(),
                base.fleetJumps(),
                base.factionIdentities(),
                diplomacy);
    }

    private static DiplomaticMarketAccessResolver.Decision marketAccess(WorldSimulation world) {
        WorldState state = world.snapshot();
        return DiplomaticMarketAccessResolver.evaluate(
                state.factionStrategies(),
                state.factionDiplomacyStates(),
                TRADE_LEAGUE,
                MINERS,
                world.getAuthoritativeWorldTick());
    }

    private static CustomsTariffResolver.Decision tariff(WorldSimulation world) {
        return CustomsTariffResolver.evaluate(
                world.snapshot().factionDiplomacyStates(),
                TRADE_LEAGUE,
                MINERS,
                world.getAuthoritativeWorldTick());
    }

    private static WorldSimulation restore(WorldState state, ContentCatalog content) {
        return WorldSimulation.restore(
                state,
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
    }
}
