package com.spacesim.content;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.CombatComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.CustomsTariffResolver;
import com.spacesim.world.DiplomaticMarketAccessResolver;
import com.spacesim.world.DiplomaticTreatyClauseState;
import com.spacesim.world.DiplomaticTreatyCommand;
import com.spacesim.world.DiplomaticTreatyState;
import com.spacesim.world.FactionDiplomacyState;
import com.spacesim.world.FactionStrategicState;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.PhysicalWarfareOperationService;
import com.spacesim.world.Stage21EOperationTrafficPolicy;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StrategicOperationState;
import com.spacesim.world.WorldSimulation;
import com.spacesim.world.WorldState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioral acceptance for the two M22.5 balance scenarios that must cross existing authorities.
 *
 * <p>The tests deliberately create no Stage-22 mutable simulation state: B08 goes through the existing
 * Stage-19 physical-warfare resolver and Stage-21E traffic policy, while B16 goes through the existing
 * persisted treaty command boundary plus the legal market-access and customs resolvers.</p>
 */
class Stage22CivilianMinorScenarioAcceptanceTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";
    private static final String MINERS = "faction.miners";

    @Test
    void b08PhysicalInterdictionDeniesExactCivilianTrafficEdgeUntilWarfareFleetLeavesItsAnchor() {
        WorldSimulation world = DemoGalaxyFactory.create(22_508L);
        CombatFleet aggressor = operationalCombatFleet(world, DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        StarSystemId neighbor = world.getTopology().neighbors(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).get(0);
        int tradeLeagueRuntimeId = world.findFactionRuntimeId(TRADE_LEAGUE).orElseThrow();
        int minersRuntimeId = world.findFactionRuntimeId(MINERS).orElseThrow();
        int civilianTrafficFactionId = aggressor.factionId() == tradeLeagueRuntimeId
                ? minersRuntimeId
                : tradeLeagueRuntimeId;

        StrategicOperationState.OperationState interdiction = new StrategicOperationState.OperationState(
                1L,
                StrategicOperationState.OperationType.INTERCEPTION,
                1L,
                1L,
                aggressor.factionId(),
                List.of(aggressor.placement().id()),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                neighbor,
                "m22.5:b08:anchor-corona",
                StrategicOperationState.RulesOfEngagement.IDENTIFIED_HOSTILES,
                new StrategicOperationState.SupplyPolicy(0, 0, 0L),
                new StrategicOperationState.WithdrawalPolicy(
                        DemoGalaxyFactory.ACTIVE_SYSTEM_ID, 0, false, false),
                StrategicOperationState.OperationStatus.ACTIVE,
                0L,
                0L,
                -1L,
                null,
                null);
        StrategicOperationState operations = new StrategicOperationState(2L, List.of(interdiction));
        Stage21EOperationTrafficPolicy policy = new Stage21EOperationTrafficPolicy(
                new PhysicalWarfareOperationService(world));

        WorldState beforeRead = world.snapshot();
        Stage21EOperationTrafficPolicy.EdgeAvailability denied = policy.edgeAvailability(
                operations,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                neighbor,
                civilianTrafficFactionId);

        assertEquals(beforeRead, world.snapshot(), "B08 traffic admission must remain read-only");
        assertFalse(denied.allowsTraffic());
        assertEquals(
                Stage21EOperationTrafficPolicy.Availability.DENIED_BY_PHYSICAL_INTERDICTION,
                denied.availability());
        assertEquals(interdiction.id(), denied.denyingOperationId());
        assertEquals(aggressor.placement().id(), denied.denyingFleetId());

        world.beginFleetTransfer(aggressor.placement().id(), neighbor);
        Stage21EOperationTrafficPolicy.EdgeAvailability reopened = policy.edgeAvailability(
                operations,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                neighbor,
                civilianTrafficFactionId);

        assertTrue(reopened.allowsTraffic(),
                "an interdiction must stop denying freight once its ordinary physical fleet leaves the edge anchor");
        assertEquals(Stage21EOperationTrafficPolicy.Availability.AVAILABLE, reopened.availability());
    }

    @Test
    void b16AcceptedTreatyOpensDeniedMarketAndRemovesTariffThenBreachRestoresTheShock() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState base = DemoGalaxyFactory.createState(22_516L, content);
        WorldSimulation world = restore(withB16Pressure(base), content);

        DiplomaticMarketAccessResolver.Decision beforeAccess = marketAccess(world);
        CustomsTariffResolver.Decision beforeTariff = tariff(world);
        assertFalse(beforeAccess.allowed());
        assertEquals(DiplomaticMarketAccessResolver.Reason.RELATION_THRESHOLD_DENY, beforeAccess.reason());
        assertEquals(750, beforeTariff.basisPoints());
        assertEquals(CustomsTariffResolver.Reason.STANDARD_RATE, beforeTariff.reason());

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

        DiplomaticMarketAccessResolver.Decision treatyAccess = marketAccess(world);
        CustomsTariffResolver.Decision treatyTariff = tariff(world);
        assertTrue(treatyAccess.allowed());
        assertEquals(DiplomaticMarketAccessResolver.Reason.EXPLICIT_TREATY_RIGHT, treatyAccess.reason());
        assertEquals(offer.treatyId(), treatyAccess.instrumentId());
        assertEquals(0, treatyTariff.basisPoints());
        assertEquals(CustomsTariffResolver.Reason.TREATY_EXEMPTION, treatyTariff.reason());
        assertEquals(offer.treatyId(), treatyTariff.instrumentId());

        world.applyDiplomaticTreatyCommand(new DiplomaticTreatyCommand.Breach(
                TRADE_LEAGUE,
                offer.treatyId(),
                "m22.5-b16-market-access-shock"));

        DiplomaticMarketAccessResolver.Decision afterBreachAccess = marketAccess(world);
        CustomsTariffResolver.Decision afterBreachTariff = tariff(world);
        assertFalse(afterBreachAccess.allowed());
        assertEquals(DiplomaticMarketAccessResolver.Reason.RELATION_THRESHOLD_DENY, afterBreachAccess.reason());
        assertEquals(750, afterBreachTariff.basisPoints());
        assertEquals(CustomsTariffResolver.Reason.STANDARD_RATE, afterBreachTariff.reason());
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

    private static CombatFleet operationalCombatFleet(WorldSimulation world, StarSystemId systemId) {
        SimulationSession session = world.findSession(systemId).orElseThrow();
        for (FleetPlacementState placement : world.getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM || !systemId.equals(placement.systemId())) {
                continue;
            }
            Entity entity = session.getEntityRegistry().find(placement.localEntityId());
            CombatComponent combat = entity == null ? null : entity.getComponent(CombatComponent.class);
            FactionComponent faction = entity == null ? null : entity.getComponent(FactionComponent.class);
            if (combat != null && combat.isOperational() && faction != null) {
                return new CombatFleet(placement, faction.factionId);
            }
        }
        throw new AssertionError("No operational combat fleet in system " + systemId);
    }

    private record CombatFleet(FleetPlacementState placement, int factionId) {}
}
