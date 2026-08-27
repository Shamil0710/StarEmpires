package com.spacesim.world.generation;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.content.ship.Stage175ICombatTestProtectionPack;
import com.spacesim.content.ship.Stage21GeneratedMilitaryEngineeringCatalog;
import com.spacesim.economy.EconomicTransaction.Type;
import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase;
import com.spacesim.persistence.Stage20FreightPersistentState.FreighterState;
import com.spacesim.persistence.Stage20FreightPersistentState.TransportOrderState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage20SourceOutpostMaterializer.MaterializedExtractionOutpost;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.player.PlayerState;
import com.spacesim.ship.ShipDamageRuntime;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipInstanceRuntimeState;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import com.spacesim.warfare.Stage19ConflictRuntime;
import com.spacesim.world.DiplomaticLifecycleService;
import com.spacesim.world.DiplomaticLifecycleState;
import com.spacesim.world.DiplomaticLifecycleState.CrisisEscalation;
import com.spacesim.world.DiplomaticLifecycleState.ProposalKind;
import com.spacesim.world.DiplomaticLifecycleState.RelationEvent;
import com.spacesim.world.DiplomaticLifecycleState.RelationFactor;
import com.spacesim.world.DiplomaticLifecycleState.WarGoal;
import com.spacesim.world.DiplomaticLifecycleState.WarGoalKind;
import com.spacesim.world.DiplomaticLifecycleState.WarStatus;
import com.spacesim.world.FactionActorObservationSnapshot.InterestKind;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;
import com.spacesim.world.FactionIdentityResolver;
import com.spacesim.world.FactionLivingActorState;
import com.spacesim.world.FactionStrategicGoalPlanner;
import com.spacesim.world.FactionStrategicIntentState;
import com.spacesim.world.FleetCommandState;
import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.FleetOrderState;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.FleetCommandState.OrderStatus;
import com.spacesim.world.FleetCommandState.OrderType;
import com.spacesim.world.FleetForceRegistry;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetOperationalAvailability;
import com.spacesim.world.FleetOrderSubmissionService;
import com.spacesim.world.FleetOrderSubmissionService.ServiceCapability;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.FleetReadinessEvaluator;
import com.spacesim.world.FleetReadinessState;
import com.spacesim.world.FleetStrategicRoutePlanner;
import com.spacesim.world.GeneratedWorldFtlTestSupport;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.SettlementRecoveryService;
import com.spacesim.world.SettlementRecoveryState;
import com.spacesim.world.SettlementRecoveryState.Settlement;
import com.spacesim.world.SettlementRecoveryState.SettlementStatus;
import com.spacesim.world.Stage21EPhysicalConsequenceService;
import com.spacesim.world.Stage21HNpcMissionService;
import com.spacesim.world.Stage21HNpcMissionState;
import com.spacesim.world.Stage21HNpcMissionState.KnowledgeKind;
import com.spacesim.world.Stage21HNpcMissionState.MissionContract;
import com.spacesim.world.Stage21HNpcMissionState.MissionObjective;
import com.spacesim.world.Stage21HNpcMissionState.MissionStatus;
import com.spacesim.world.Stage21HNpcMissionState.MissionTemplate;
import com.spacesim.world.Stage21HNpcMissionState.NpcAvailability;
import com.spacesim.world.Stage21HNpcMissionState.NpcKnowledgeFact;
import com.spacesim.world.Stage21HNpcMissionState.NpcRole;
import com.spacesim.world.Stage21HNpcMissionState.NpcState;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveAuthority;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveKind;
import com.spacesim.world.Stage21HPlayerMissionAuthority;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StrategicGoalCandidate;
import com.spacesim.world.StrategicGoalEvidence;
import com.spacesim.world.StrategicGoalOutcomeSignal;
import com.spacesim.world.StrategicGoalType;
import com.spacesim.world.StrategicOperationState;
import com.spacesim.world.StrategicOperationState.ContactState;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import com.spacesim.world.StrategicPlanningEnvelope;
import com.spacesim.world.TerritorialControlRuntime;
import com.spacesim.world.TerritorialTransitionService;
import com.spacesim.world.TerritorialTransitionState;
import com.spacesim.world.TerritorialTransitionState.OccupationStatus;
import com.spacesim.world.WorldSimulation;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Final non-vacuous Stage-21 living-world acceptance over one ordinary generated runtime.
 *
 * <p>The fixture supplies actor-known pressure and a critically damaged but still surviving hostile
 * ship. Every consequential transition after those explicit inputs is owned by the production
 * Stage-21/19/18/17 boundaries: strategic intent, legal crisis/war, ordinary FTL, exact tactical
 * destruction and finite stores, gradual territorial control, peace/demobilization, physical
 * extraction/freight and player-owned mission completion. No test-only combat score, territory flag,
 * cargo mint or mission-completion flag exists in this chain.</p>
 */
final class Stage21IFinalLivingWorldSoakAcceptanceTest {
    private static final double CRITICAL_INTEGRITY = 1e-6d;
    private static final String LAST_LIVE_MOUNT = "utility_storage";
    private static final double ENGAGEMENT_SEPARATION_M = 600d;
    private static final double MASS_EPSILON_KG = 1e-9d;
    private static final double HANDLING_SECONDS = 3_600d;
    private static final long MISSION_REWARD = 1_000L;

    @Test
    void generatedWorldRunsCausalWarTerritoryPeaceTradeAndMissionChainDeterministically() {
        ScenarioResult first = runScenario();
        ScenarioResult second = runScenario();

        assertEquals(first.digest(), second.digest(),
                "same generated seed and explicit actor evidence must produce the same living-world outcomes");
        assertArrayEquals(first.finalPhysicalCheckpoint(), second.finalPhysicalCheckpoint(),
                "the complete ordinary physical world must be byte-identical after the deterministic soak");
        assertArrayEquals(first.finalStage21ICheckpoint(), second.finalStage21ICheckpoint(),
                "the final compatibility checkpoint must remain byte-identical for the repeated soak");
    }

    private static ScenarioResult runScenario() {
        var generated = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime = generated.runtime();
        long initialNextFleetId = runtime.world().snapshot().nextFleetIdValue();
        int initialFleetCount = runtime.world().getFleetPlacements().size();

        MilitaryFleet attacker = doctrineFleet(runtime, DoctrineId.E_BALANCED_CONTROL, -1);
        MilitaryFleet target = doctrineFleet(runtime, DoctrineId.D_DEFENSIVE_EW, attacker.factionId());
        assertFalse(attacker.systemId().equals(target.systemId()),
                "final soak requires a real inter-system military move before contact");

        FactionIdentityResolver identities = FactionIdentityResolver.createDefault(
                generated.content(), runtime.world().snapshot().factionIdentities());
        String attackerStable = identities.stableId(attacker.factionId()).orElseThrow();
        String targetStable = identities.stableId(target.factionId()).orElseThrow();
        long intentTick = runtime.world().getAuthoritativeWorldTick();

        StrategicGoalCandidate coercion = coercionCandidate(targetStable, intentTick);
        var planned = FactionStrategicGoalPlanner.review(
                FactionLivingActorState.initial(attackerStable, intentTick),
                FactionStrategicIntentState.initial(attackerStable),
                List.of(coercion),
                StrategicPlanningEnvelope.balanced(5L),
                intentTick);
        assertEquals(1, planned.state().activeGoals().size());
        var strategicGoal = planned.state().activeGoals().get(0);
        assertEquals(StrategicGoalType.COERCE, strategicGoal.type());
        assertEquals(InterestKind.BORDER_SECURITY, strategicGoal.sourceEvidence().kind());

        Stage19ConflictRuntime strategicWarfare = new Stage19ConflictRuntime(
                Stage19ConflictState.empty(intentTick));
        DiplomaticLifecycleService diplomacy = new DiplomaticLifecycleService(
                runtime.world(), strategicWarfare, DiplomaticLifecycleState.empty(intentTick));
        diplomacy.remember(attackerStable, targetStable, new RelationEvent(
                "stage21i.memory.security-pressure",
                RelationFactor.THREAT,
                -90,
                intentTick,
                strategicGoal.sourceEvidence().observations().get(0).provenanceId()));
        var ultimatum = diplomacy.propose(new DiplomaticLifecycleService.ProposalRequest(
                strategicGoal.goalId(),
                attackerStable,
                targetStable,
                ProposalKind.ULTIMATUM,
                "stage21i.security-crisis",
                List.of(),
                List.of(),
                intentTick + 500L));
        var crisis = diplomacy.openCrisis(
                ultimatum.proposalId(), strategicGoal.goalId(), intentTick + 500L);
        crisis = diplomacy.escalateCrisis(crisis.crisisId(), "stage21i.pressure", intentTick + 500L);
        crisis = diplomacy.escalateCrisis(crisis.crisisId(), "stage21i.ultimatum", intentTick + 500L);
        crisis = diplomacy.escalateCrisis(crisis.crisisId(), "stage21i.war-authorized", intentTick + 500L);
        assertEquals(CrisisEscalation.WAR_AUTHORIZED, crisis.escalation());
        var war = diplomacy.declareWarFromCrisis(
                crisis.crisisId(),
                List.of(
                        new WarGoal(
                                "stage21i.war-goal.attacker",
                                attackerStable,
                                WarGoalKind.SECURITY,
                                "security:" + target.systemId().value(),
                                true),
                        new WarGoal(
                                "stage21i.war-goal.target",
                                targetStable,
                                WarGoalKind.SECURITY,
                                "security:" + attacker.systemId().value(),
                                true)));
        assertEquals(WarStatus.ACTIVE, war.status());
        assertEquals(2, war.stage19ConflictIds().size());
        war.stage19ConflictIds().forEach(conflictId -> assertTrue(strategicWarfare.find(conflictId).isPresent()));

        StarSystemId attackerHome = attacker.systemId();
        moveFleetByOrdinaryRoute(runtime, attacker.fleetId(), target.systemId());
        assertEquals(target.systemId(), runtime.world().findFleet(attacker.fleetId()).orElseThrow().systemId());

        EngineeringComponent attackerEngineering = engineering(runtime, attacker.fleetId());
        double ammunitionBeforeKg = attackerEngineering.runtimeState.consumables().ammunitionMassKg();
        double reactionMassBeforeKg = attackerEngineering.runtimeState.consumables().reactionMassKg();
        assertTrue(ammunitionBeforeKg > 0d || reactionMassBeforeKg > 0d,
                "operation fleet must carry finite physical supply before the exact exchange");

        EngineeringComponent targetEngineering = engineering(runtime, target.fleetId());
        InstalledFit destroyedTargetFit = targetEngineering.fit;
        applyCriticalButSurvivingPhysicalState(targetEngineering);
        FleetPlacementState attackerPlacement = runtime.world().findFleet(attacker.fleetId()).orElseThrow();
        FleetPlacementState targetPlacement = runtime.world().findFleet(target.fleetId()).orElseThrow();
        LocalPhysicalKinematics targetPhysical = runtime.arrival().materialization(target.systemId())
                .physicalState(targetPlacement.localEntityId()).orElseThrow();
        runtime.arrival().materialization(target.systemId()).updatePhysicalState(
                attackerPlacement.localEntityId(),
                LocalPhysicalKinematics.stationary(
                        targetPhysical.position().translated(0d, ENGAGEMENT_SEPARATION_M)));

        Map<FleetId, FleetOperationalAvailability> battleAvailability = Map.of(
                attacker.fleetId(), fullAvailability(),
                target.fleetId(), fullAvailability());
        FleetReadinessEvaluator evaluator = new FleetReadinessEvaluator(
                Stage21GeneratedMilitaryEngineeringCatalog.load());
        FleetForceRegistry beforeBattle = FleetForceRegistry.reconstruct(
                runtime.world().snapshot(), evaluator, battleAvailability);
        long battleTick = runtime.world().getAuthoritativeWorldTick();
        CommandGroupState battleGroup = new CommandGroupState(
                1L,
                attacker.factionId(),
                "Stage21I causal-war group",
                List.of(attacker.fleetId()),
                attackerHome,
                false,
                false,
                FleetReadinessState.FULL);
        FleetOrderState interceptOrder = new FleetOrderState(
                1L,
                battleGroup.id(),
                OrderType.INTERCEPT,
                OrderSource.AI,
                target.systemId(),
                List.of(target.systemId()),
                0,
                battleTick,
                battleTick + 100L,
                OrderStatus.ACTIVE);
        FleetCommandState battleCommand = new FleetCommandState(
                2L, 2L, List.of(battleGroup), List.of(interceptOrder));
        ContactState contact = new ContactState(
                target.fleetId(),
                target.systemId(),
                ObservationChannel.LOCAL_SENSOR_REPORT,
                "stage21i.contact:" + target.fleetId().value(),
                battleTick,
                battleTick + 100L);
        OperationState battleOperation = new OperationState(
                1L,
                OperationType.INTERCEPTION,
                battleGroup.id(),
                interceptOrder.id(),
                attacker.factionId(),
                List.of(attacker.fleetId()),
                target.systemId(),
                target.systemId(),
                "system:" + target.systemId().value(),
                RulesOfEngagement.IDENTIFIED_HOSTILES,
                new SupplyPolicy(0, 0, 100L),
                new WithdrawalPolicy(attackerHome, 0, true, true),
                OperationStatus.CONTACT_CONFIRMED,
                battleTick,
                battleTick,
                -1L,
                contact,
                null);
        StrategicOperationState battleOperations = new StrategicOperationState(
                2L, List.of(battleOperation));
        var executed = new Stage21EGeneratedWorldTacticalExecutionService(
                new Stage21EGeneratedWorldStage19Authority(runtime))
                .execute(battleCommand, battleOperations, battleOperation.id(), beforeBattle, battleTick);

        FleetForceRegistry afterBattle = FleetForceRegistry.reconstruct(
                runtime.world().snapshot(), evaluator, battleAvailability);
        Stage21EPhysicalConsequenceService.ConsequenceReport consequences =
                new Stage21EPhysicalConsequenceService().reconcile(
                        battleOperation, beforeBattle, afterBattle);
        assertEquals(List.of(target.fleetId()), consequences.losses(),
                "production Stage 19 must create the exact ordinary FleetId casualty");
        assertTrue(runtime.world().findFleet(target.fleetId()).isEmpty());
        assertTrue(runtime.world().findFleet(attacker.fleetId()).isPresent());
        assertFalse(executed.owningGroupDestroyed());
        assertNotNull(executed.operationState().requireOperation(battleOperation.id()).encounter());
        assertFalse(executed.operationState().requireOperation(battleOperation.id()).encounter().active());
        assertEquals(initialFleetCount - 1, runtime.world().getFleetPlacements().size(),
                "the casualty must remain physically lost with no free replacement");

        EngineeringComponent survivingAttacker = engineering(runtime, attacker.fleetId());
        double ammunitionAfterKg = survivingAttacker.runtimeState.consumables().ammunitionMassKg();
        double reactionMassAfterKg = survivingAttacker.runtimeState.consumables().reactionMassKg();
        assertTrue(
                ammunitionAfterKg + MASS_EPSILON_KG < ammunitionBeforeKg
                        || reactionMassAfterKg + MASS_EPSILON_KG < reactionMassBeforeKg,
                "exact warfare must consume ammunition and/or reaction mass from the surviving FleetId");

        awaitFittedCooldown(runtime, attacker.fleetId());
        StarSystemId occupationTarget = findOccupationTarget(runtime, attacker.fleetId());
        moveFleetByOrdinaryRoute(runtime, attacker.fleetId(), occupationTarget);
        assertEquals(occupationTarget, runtime.world().findFleet(attacker.fleetId()).orElseThrow().systemId());

        long occupationTick = runtime.world().getAuthoritativeWorldTick();
        long occupationGroupId = 21L;
        OperationState invasion = new OperationState(
                21L,
                OperationType.INVASION,
                occupationGroupId,
                21L,
                attacker.factionId(),
                List.of(attacker.fleetId()),
                occupationTarget,
                occupationTarget,
                "system:" + occupationTarget.value(),
                RulesOfEngagement.DECLARED_HOSTILES,
                new SupplyPolicy(0, 0, 300L),
                new WithdrawalPolicy(attackerHome, 0, true, true),
                OperationStatus.ACTIVE,
                occupationTick,
                occupationTick,
                -1L,
                null,
                null);
        StrategicOperationState invasionOperations = new StrategicOperationState(22L, List.of(invasion));
        FleetForceRegistry occupationForces = FleetForceRegistry.reconstruct(
                runtime.world().snapshot(), evaluator,
                Map.of(attacker.fleetId(), fullAvailability()));
        TerritorialTransitionService territory = new TerritorialTransitionService();
        var initialOccupation = territory.advance(
                TerritorialTransitionState.empty(),
                runtime.world(),
                invasionOperations,
                occupationForces,
                identities,
                invasion.id(),
                occupationTick);
        assertEquals(OccupationStatus.OCCUPYING, initialOccupation.occupation().status());
        assertTrue(initialOccupation.securityReady());
        assertTrue(initialOccupation.supplyReady());

        advanceWorldTo(runtime, occupationTick + TerritorialTransitionService.REQUIRED_OCCUPATION_TICKS);
        var securedOccupation = territory.advance(
                initialOccupation.transitions(),
                runtime.world(),
                initialOccupation.operations(),
                occupationForces,
                identities,
                invasion.id(),
                runtime.world().getAuthoritativeWorldTick());
        assertEquals(OccupationStatus.SECURED, securedOccupation.occupation().status());
        assertTrue(securedOccupation.claimCreated());
        assertNotNull(runtime.world().findFactionStrategicState(attackerStable).orElseThrow()
                .claimFor(occupationTarget));

        runtime.world().createEntity(occupationTarget, new Entity()
                .add(new IdentityComponent("Stage21I stabilization anchor", IdentityComponent.Kind.STATION))
                .add(new MarketComponent())
                .add(new FactionComponent(attacker.factionId())));
        long controlDeadline = runtime.world().getAuthoritativeWorldTick()
                + TerritorialControlRuntime.REQUIRED_STABILIZATION_TICKS + 1_000L;
        while (runtime.world().getAuthoritativeWorldTick() < controlDeadline
                && !attackerStable.equals(runtime.world().controllingFaction(occupationTarget).orElse(null))) {
            runtime.advanceFrame(1.0f);
        }
        assertEquals(attackerStable, runtime.world().controllingFaction(occupationTarget).orElseThrow(),
                "Stage-17 stabilization must establish control only after the physical anchor and time threshold");
        var establishedOccupation = territory.advance(
                securedOccupation.transitions(),
                runtime.world(),
                securedOccupation.operations(),
                occupationForces,
                identities,
                invasion.id(),
                runtime.world().getAuthoritativeWorldTick());
        assertTrue(establishedOccupation.occupation().controlEverEstablished());

        long peaceTick = runtime.world().getAuthoritativeWorldTick();
        var peaceProposal = diplomacy.propose(new DiplomaticLifecycleService.ProposalRequest(
                strategicGoal.goalId(),
                attackerStable,
                targetStable,
                ProposalKind.PEACE,
                war.warId(),
                List.of(),
                List.of(),
                peaceTick + 500L));
        diplomacy.accept(peaceProposal.proposalId());
        var peacefulWar = diplomacy.snapshot().wars().stream()
                .filter(saved -> saved.warId().equals(war.warId()))
                .findFirst().orElseThrow();
        assertEquals(WarStatus.PEACE, peacefulWar.status());
        assertThrows(IllegalStateException.class, () -> diplomacy.declareWarFromObservedAttack(
                attackerStable,
                targetStable,
                "stage21i.illegal-immediate-reescalation",
                runtime.world().getAuthoritativeWorldTick(),
                List.of(
                        new WarGoal("stage21i.reescalation.a", attackerStable, WarGoalKind.SECURITY,
                                "security:" + occupationTarget.value(), true),
                        new WarGoal("stage21i.reescalation.b", targetStable, WarGoalKind.SECURITY,
                                "security:" + occupationTarget.value(), true))));

        Settlement settlement = new Settlement(
                1L,
                peaceProposal.proposalId(),
                war.warId(),
                attackerStable,
                targetStable,
                peaceTick,
                peaceTick,
                SettlementStatus.PENDING,
                false);
        SettlementRecoveryService recovery = new SettlementRecoveryService(new SettlementRecoveryState(
                SettlementRecoveryState.CURRENT_VERSION,
                peaceTick,
                2L,
                1L,
                List.of(settlement),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
        recovery.recordPhysicalLosses(
                1L, battleOperation.id(), consequences, beforeBattle, identities, peaceTick);
        assertTrue(recovery.snapshot().losses().stream()
                .anyMatch(loss -> loss.lostFleetId().equals(target.fleetId())));
        recovery.registerDemobilization(1L, occupationGroupId, attackerStable, peaceTick);
        recovery.finalizeRecoveryPlan(1L, peaceTick);

        FleetForceRegistry demobilizationForces = FleetForceRegistry.reconstruct(
                runtime.world().snapshot(), evaluator,
                Map.of(attacker.fleetId(), fullAvailability()));
        CommandGroupState occupationGroup = new CommandGroupState(
                occupationGroupId,
                attacker.factionId(),
                "Stage21I post-war occupation group",
                List.of(attacker.fleetId()),
                attackerHome,
                false,
                false,
                FleetReadinessState.FULL);
        FleetCommandState demobilizationCommand = new FleetCommandState(
                occupationGroupId + 1L, 1L, List.of(occupationGroup), List.of());
        FleetOrderSubmissionService submission = new FleetOrderSubmissionService(
                new FleetStrategicRoutePlanner(runtime.world().getTopology()));
        var demobilized = recovery.submitReturnOrder(
                demobilizationCommand,
                demobilizationForces,
                identities,
                submission,
                1L,
                occupationGroupId,
                OrderSource.AI,
                peaceTick,
                (factionId, from, to, tick, destination) -> true,
                (factionId, systemId, tick) -> new ServiceCapability(true, true, true, 1L, 1L),
                (factionId, type, route, tick) -> 0);
        assertEquals(OrderType.RETURN, demobilized.returnOrder().type());
        assertEquals(route(runtime, occupationTarget, attackerHome), demobilized.returnOrder().route());
        assertEquals(SettlementStatus.COMPLETE, recovery.snapshot().requireSettlement(1L).status());

        awaitFittedCooldown(runtime, attacker.fleetId());
        moveFleetByOrdinaryRoute(runtime, attacker.fleetId(), attackerHome);
        assertEquals(attackerHome, runtime.world().findFleet(attacker.fleetId()).orElseThrow().systemId(),
                "the surviving force must demobilize through the same ordinary FleetId movement authority");

        FreightMissionContext freightMission = createFreightMission(
                runtime, attackerStable, targetStable);
        double firstDelivery = performDeliveryCycle(runtime, freightMission.freighter());
        assertTrue(firstDelivery > 0d);
        WalletComponent playerWallet = new WalletComponent();
        PlayerState player = playerState(freightMission.freighter().fleetId());
        MissionContract completed = freightMission.service().reconcilePlayerMission(
                runtime.world(),
                runtime.freight().capture(),
                freightMission.savedIndustry(),
                freightMission.discovery(),
                null,
                StrategicOperationState.empty(),
                player,
                freightMission.mission().missionId(),
                playerWallet);
        assertEquals(MissionStatus.COMPLETED, completed.status());
        assertEquals(MISSION_REWARD, playerWallet.getBalanceMilliCredits());
        assertEquals(10, freightMission.service().snapshot().reputations().get(0).derivedValue());
        assertEquals(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID,
                freightMission.service().snapshot().reputations().get(0).subjectActorId());
        long issuerTreasuryAfterMission = runtime.world().findFactionEconomicState(
                freightMission.order().stableFactionId()).orElseThrow().treasuryMilliCredits();
        assertEquals(
                freightMission.issuerTreasuryBeforeOffer(),
                Math.addExact(issuerTreasuryAfterMission, playerWallet.getBalanceMilliCredits()),
                "mission escrow/payout must conserve the explicitly seeded treasury balance");
        assertNoStage21HMoneySourceOrSink(runtime.world());

        runtime.freight().dispatchReturn(freightMission.freighter().fleetId());
        completeFreightRoute(runtime, freightMission.freighter().fleetId(), FreightPhase.AT_SOURCE);
        double deliveredBeforeSecondCycle = runtime.freight().findOrder(
                freightMission.order().orderId()).orElseThrow().deliveredMassKg();
        double secondDelivery = performDeliveryCycle(runtime,
                runtime.freight().findFreighter(freightMission.freighter().fleetId()).orElseThrow());
        assertTrue(secondDelivery > 0d);
        assertTrue(runtime.freight().findOrder(freightMission.order().orderId()).orElseThrow().deliveredMassKg()
                > deliveredBeforeSecondCycle,
                "post-war physical logistics must continue for another bounded production/freight cycle");

        assertEquals(initialNextFleetId, runtime.world().snapshot().nextFleetIdValue(),
                "loss, territory, recovery and trade must not allocate hidden fleet identities");
        Set<FleetId> uniqueFleetIds = new HashSet<>();
        runtime.world().getFleetPlacements().forEach(fleet -> assertTrue(uniqueFleetIds.add(fleet.id())));
        assertEquals(initialFleetCount - 1, uniqueFleetIds.size());

        byte[] finalStage20 = Stage20GeneratedWorldRuntimePersistenceCodec.encode(runtime.captureState());
        var finalStage21I = Stage21IGeneratedWorldRuntimePersistenceCodec.decodeOrMigrate(finalStage20);
        byte[] finalStage21IBytes = Stage21IGeneratedWorldRuntimePersistenceCodec.encode(finalStage21I);
        assertArrayEquals(
                finalStage21IBytes,
                Stage21IGeneratedWorldRuntimePersistenceCodec.encode(
                        Stage21IGeneratedWorldRuntimePersistenceCodec.decode(finalStage21IBytes)),
                "final living-world compatibility checkpoint must round-trip byte-stably");
        assertEquals(runtime.world().snapshot().factions().size(),
                finalStage21I.stage21HRuntime().stage21GRuntime().stage21FRuntime().stage21ERuntime()
                        .stage21DRuntime().stage21CRuntime().stage21BRuntime().stage21ARuntime()
                        .livingActors().size());

        ScenarioDigest digest = new ScenarioDigest(
                strategicGoal.goalId(),
                crisis.crisisId(),
                war.warId(),
                target.fleetId(),
                attacker.fleetId(),
                occupationTarget,
                peacefulWar.status(),
                recovery.snapshot().requireSettlement(1L).status(),
                completed.status(),
                freightMission.service().snapshot().reputations().get(0).derivedValue(),
                runtime.freight().findOrder(freightMission.order().orderId()).orElseThrow().deliveredMassKg(),
                uniqueFleetIds.stream().sorted().toList());
        return new ScenarioResult(finalStage20, finalStage21IBytes, digest);
    }

    private static StrategicGoalCandidate coercionCandidate(String targetFactionId, long tick) {
        StrategicGoalEvidence evidence = new StrategicGoalEvidence(
                InterestKind.BORDER_SECURITY,
                targetFactionId,
                9_000,
                List.of(new ObservationEvidence(
                        ObservationChannel.LOCAL_SENSOR_REPORT,
                        "stage21i.actor-security-report:" + targetFactionId,
                        tick,
                        -1L)));
        return new StrategicGoalCandidate(
                StrategicGoalType.COERCE,
                targetFactionId,
                evidence,
                9_000,
                9_000,
                9_000,
                8_000,
                StrategicPlanningEnvelope.balanced(5L),
                List.of(),
                -1L,
                30L,
                StrategicGoalOutcomeSignal.NONE);
    }

    private static FreightMissionContext createFreightMission(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            String firstParticipant,
            String secondParticipant) {
        FreighterState freighter = assignedSourceFreighter(runtime, Set.of(firstParticipant, secondParticipant));
        TransportOrderState order = runtime.freight().findOrder(freighter.activeOrderId()).orElseThrow();
        long tick = runtime.world().getAuthoritativeWorldTick();
        NpcKnowledgeFact fact = new NpcKnowledgeFact(
                "stage21i.fact.freight." + order.orderId(),
                order.orderId(),
                KnowledgeKind.ACTOR_OBSERVATION,
                "ECONOMIC.RESOURCE_DEFICIT",
                8_000,
                "stage21i.report.freight." + order.orderId(),
                tick,
                -1L);
        NpcState npc = new NpcState(
                "stage21i.npc.logistics." + order.stableFactionId(),
                "stage21i.npc.logistics.name",
                NpcRole.TRADE_LOGISTICS,
                order.stableFactionId(),
                order.orderedSystems().get(0),
                NpcAvailability.AVAILABLE,
                List.of(fact));
        Stage21HNpcMissionService service = new Stage21HNpcMissionService(new Stage21HNpcMissionState(
                Stage21HNpcMissionState.CURRENT_VERSION,
                tick,
                1L,
                List.of(npc),
                List.of(),
                List.of(),
                List.of()));

        ensureSpendable(runtime.world(), order.stableFactionId(), MISSION_REWARD);
        long treasuryBeforeOffer = runtime.world().findFactionEconomicState(
                order.stableFactionId()).orElseThrow().treasuryMilliCredits();
        var saved = runtime.captureState();
        MissionObjective objective = new MissionObjective(
                ObjectiveAuthority.FREIGHT,
                ObjectiveKind.FREIGHT_ORDER_DELIVERED_KG_AT_LEAST,
                order.orderId(),
                0L,
                (long) Math.floor(order.deliveredMassKg()) + 1L,
                "");
        MissionContract mission = service.offerMission(
                runtime.world(),
                runtime.freight().capture(),
                saved.campaign().industrialState(),
                saved.campaign().discoveryState().knowledgeFor(order.stableFactionId()),
                StrategicOperationState.empty(),
                npc.npcId(),
                MissionTemplate.EMERGENCY_SUPPLY_DELIVERY,
                objective,
                List.of(fact.factId()),
                tick + 1_000_000L,
                MISSION_REWARD);
        service.acceptMission(mission.missionId(), tick);
        return new FreightMissionContext(
                freighter,
                order,
                service,
                mission,
                saved.campaign().industrialState(),
                saved.campaign().discoveryState().knowledgeFor(order.stableFactionId()),
                treasuryBeforeOffer);
    }

    private static FreighterState assignedSourceFreighter(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            Set<String> preferredFactions) {
        List<FreighterState> candidates = runtime.freight().capture().freighters().stream()
                .filter(value -> value.phase() == FreightPhase.AT_SOURCE)
                .filter(value -> !value.activeOrderId().isBlank())
                .filter(value -> runtime.freight().findOrder(value.activeOrderId())
                        .map(order -> order.orderedSystems().size() > 1)
                        .orElse(false))
                .filter(value -> matchingOutpost(runtime, value) != null)
                .sorted(java.util.Comparator.comparing(FreighterState::fleetId))
                .toList();
        return candidates.stream()
                .filter(value -> preferredFactions.contains(value.stableFactionId()))
                .findFirst()
                .orElseGet(() -> candidates.stream().findFirst().orElseThrow(() -> new AssertionError(
                        "generated final soak lacks routed source freight with a physical outpost")));
    }

    private static MaterializedExtractionOutpost matchingOutpost(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FreighterState freighter) {
        TransportOrderState order = runtime.freight().findOrder(freighter.activeOrderId()).orElse(null);
        if (order == null) return null;
        StarSystemId source = order.orderedSystems().get(0);
        return runtime.industry().sourceOutposts().outposts().stream()
                .filter(value -> value.site().systemId().equals(source))
                .filter(value -> value.source().sourceState().outputCommodityId().equals(order.commodityId()))
                .findFirst().orElse(null);
    }

    private static double performDeliveryCycle(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FreighterState freighter) {
        FreighterState current = runtime.freight().findFreighter(freighter.fleetId()).orElseThrow();
        assertEquals(FreightPhase.AT_SOURCE, current.phase());
        MaterializedExtractionOutpost outpost = matchingOutpost(runtime, current);
        if (outpost == null) throw new AssertionError("freight source has no matching extraction outpost");
        var extraction = runtime.extract(outpost.site().siteId(), 10_000d, HANDLING_SECONDS);
        assertTrue(extraction.committed(), "post-war production must extract finite physical source mass");
        double mass = Math.min(extraction.outputMassStoredKg(), 1_000d);
        assertTrue(mass > 0d);
        var hubTransfer = runtime.transferOutpostToOrderSource(
                current.fleetId(), outpost.site().siteId(), mass, HANDLING_SECONDS);
        assertTrue(hubTransfer.transferred(), "extracted mass must move into the ordinary source hub");
        var load = runtime.loadAtOrderSource(current.fleetId(), mass, 0d, HANDLING_SECONDS);
        assertTrue(load.transferred(), "ordinary freight hold must receive real source inventory");
        double deliveredBefore = runtime.freight().findOrder(current.activeOrderId()).orElseThrow().deliveredMassKg();
        runtime.freight().dispatchOutbound(
                current.fleetId(), runtime.world().getAuthoritativeWorldTick());
        completeFreightRoute(runtime, current.fleetId(), FreightPhase.AT_DESTINATION);
        var unload = runtime.unloadAtOrderDestination(current.fleetId(), mass, HANDLING_SECONDS);
        assertTrue(unload.transferred(), "physical cargo must unload into the exact generated destination");
        double deliveredAfter = runtime.freight().findOrder(current.activeOrderId()).orElseThrow().deliveredMassKg();
        assertTrue(deliveredAfter > deliveredBefore);
        return deliveredAfter - deliveredBefore;
    }

    private static void completeFreightRoute(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId,
            FreightPhase terminalPhase) {
        for (int hop = 0; hop < 64; hop++) {
            FreighterState current = runtime.freight().findFreighter(fleetId).orElseThrow();
            if (current.phase() == terminalPhase) return;
            runtime.requestNextRouteHop(fleetId);
            for (int attempt = 0; attempt < 4_000 && runtime.world().findFleetJump(fleetId).isPresent(); attempt++) {
                runtime.advanceFrame(1.0f);
            }
            assertTrue(runtime.world().findFleetJump(fleetId).isEmpty(),
                    "ordinary freight hop must complete through the exact-arrival FSM");
        }
        throw new AssertionError("freight route exceeded the bounded generated topology hop budget");
    }

    private static MilitaryFleet doctrineFleet(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            DoctrineId doctrineId,
            int excludedFactionId) {
        InstalledFit expected = strategicFit(doctrineId);
        ArrayList<MilitaryFleet> matches = new ArrayList<>();
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) continue;
            Entity entity = runtime.world().findSession(placement.systemId()).orElseThrow()
                    .getEntityRegistry().require(placement.localEntityId());
            EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            if (engineering == null || faction == null || faction.factionId == excludedFactionId) continue;
            if (engineering.fit.equals(expected)) {
                matches.add(new MilitaryFleet(placement.id(), faction.factionId, placement.systemId()));
            }
        }
        matches.sort(java.util.Comparator.comparing(MilitaryFleet::fleetId));
        if (matches.isEmpty()) {
            throw new AssertionError("generated world lacks exact strategic doctrine fleet: " + doctrineId);
        }
        return matches.get(0);
    }

    private static InstalledFit strategicFit(DoctrineId doctrineId) {
        ShipEngineeringCatalog catalog = Stage21GeneratedMilitaryEngineeringCatalog.load();
        var doctrine = Stage175IFleetDoctrineCatalog.get(doctrineId);
        return InstalledFit.fromDemonstrator(catalog.findDemonstratorFit(
                Stage175ICombatTestContentPack.stage21StrategicFitId(doctrine.fitId())));
    }

    private static void applyCriticalButSurvivingPhysicalState(EngineeringComponent engineering) {
        ShipEngineeringCatalog catalog = Stage21GeneratedMilitaryEngineeringCatalog.load();
        var protection = Stage175ICombatTestProtectionPack.load();
        var hull = catalog.findHull(engineering.fit.hullId());
        var layout = protection.findHullDamageLayout(hull.id());
        TreeMap<String, Double> moduleIntegrity = new TreeMap<>();
        engineering.fit.installedModules().forEach(installed -> moduleIntegrity.put(installed.mountId(), 0d));
        if (!moduleIntegrity.containsKey(LAST_LIVE_MOUNT)) {
            throw new AssertionError("generated defensive strategic fit lacks expected physical storage mount");
        }
        moduleIntegrity.put(LAST_LIVE_MOUNT, CRITICAL_INTEGRITY);
        ShipDamageRuntime.Snapshot damage = new ShipDamageRuntime.Snapshot(
                Map.of(
                        "engineering", CRITICAL_INTEGRITY,
                        "mission_core", 0d,
                        "weapons", 0d),
                new DamageState(moduleIntegrity));
        assertFalse(ShipDamageRuntime.isFullyDestroyed(hull, engineering.fit, layout, damage),
                "hostile fixture must remain physically alive before Stage 19 resolves combat");
        ShipInstanceRuntimeState previous = engineering.instanceState;
        engineering.setInstanceState(new ShipInstanceRuntimeState(
                damage,
                Map.of(),
                previous.maintenance(),
                previous.weaponLoadout(),
                previous.weaponMountRuntime()));
    }

    private static EngineeringComponent engineering(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId) {
        FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
        if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
            throw new AssertionError("engineering inspection requires an in-system ordinary FleetId");
        }
        Entity entity = runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().require(placement.localEntityId());
        EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
        if (engineering == null) throw new AssertionError("ordinary military FleetId lacks engineering state");
        return engineering;
    }

    private static StarSystemId findOccupationTarget(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId) {
        StarSystemId current = runtime.world().findFleet(fleetId).orElseThrow().systemId();
        return runtime.world().getTopology().systems().stream()
                .map(system -> system.id())
                .sorted()
                .filter(system -> !system.equals(current))
                .filter(system -> runtime.world().controllingFaction(system).isEmpty())
                .filter(system -> runtime.world().getFleetPlacements().stream()
                        .noneMatch(fleet -> fleet.locationKind() == FleetLocationKind.IN_SYSTEM
                                && system.equals(fleet.systemId())))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "generated final soak lacks an empty unclaimed territorial objective"));
    }

    private static void moveFleetByOrdinaryRoute(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId,
            StarSystemId destination) {
        FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
        List<StarSystemId> path = route(runtime, placement.systemId(), destination);
        for (int index = 1; index < path.size(); index++) {
            GeneratedWorldFtlTestSupport.placeAtOutgoingEndpoint(runtime, fleetId, path.get(index));
            runtime.world().requestFleetJump(fleetId, path.get(index));
            for (int attempt = 0; attempt < 400 && runtime.world().findFleetJump(fleetId).isPresent(); attempt++) {
                runtime.advanceFrame(0.25f);
            }
            assertTrue(runtime.world().findFleetJump(fleetId).isEmpty(),
                    "ordinary military movement must finish every topology hop");
            assertEquals(path.get(index), runtime.world().findFleet(fleetId).orElseThrow().systemId());
            if (index + 1 < path.size()) awaitFittedCooldown(runtime, fleetId);
        }
    }

    private static void awaitFittedCooldown(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId) {
        for (int attempt = 0; attempt < 400; attempt++) {
            FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
            Entity entity = runtime.world().findSession(placement.systemId()).orElseThrow()
                    .getEntityRegistry().require(placement.localEntityId());
            EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
            if (engineering == null || engineering.runtimeState.ftlCooldownSecondsByMount().values().stream()
                    .noneMatch(value -> value > 0d)) return;
            runtime.advanceFrame(0.25f);
        }
        throw new AssertionError("ordinary fitted FTL cooldown did not clear through simulation time");
    }

    private static List<StarSystemId> route(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            StarSystemId origin,
            StarSystemId destination) {
        if (origin.equals(destination)) return List.of(origin);
        ArrayDeque<StarSystemId> queue = new ArrayDeque<>();
        Map<StarSystemId, StarSystemId> previous = new HashMap<>();
        queue.add(origin);
        previous.put(origin, null);
        while (!queue.isEmpty()) {
            StarSystemId current = queue.removeFirst();
            for (StarSystemId neighbor : runtime.world().getTopology().neighbors(current)) {
                if (previous.containsKey(neighbor)) continue;
                previous.put(neighbor, current);
                if (neighbor.equals(destination)) {
                    ArrayList<StarSystemId> reverse = new ArrayList<>();
                    StarSystemId cursor = destination;
                    while (cursor != null) {
                        reverse.add(cursor);
                        cursor = previous.get(cursor);
                    }
                    Collections.reverse(reverse);
                    return List.copyOf(reverse);
                }
                queue.addLast(neighbor);
            }
        }
        throw new AssertionError("generated topology has no route between required living-world systems");
    }

    private static void advanceWorldTo(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            long targetTick) {
        for (int attempt = 0; attempt < 5_000 && runtime.world().getAuthoritativeWorldTick() < targetTick; attempt++) {
            runtime.advanceFrame(1.0f);
        }
        assertTrue(runtime.world().getAuthoritativeWorldTick() >= targetTick,
                "ordinary generated world failed to advance to the required deterministic deadline");
    }

    private static FleetOperationalAvailability fullAvailability() {
        return new FleetOperationalAvailability(Integer.MAX_VALUE, FleetReadinessState.FULL);
    }

    private static PlayerState playerState(FleetId ownedFleet) {
        List<FleetId> fleets = List.of(ownedFleet);
        return new PlayerState(
                0L,
                null,
                List.of(),
                fleets,
                ownedFleet,
                List.of(),
                List.of(),
                null);
    }

    private static void ensureSpendable(WorldSimulation world, String faction, long required) {
        var economy = world.findFactionEconomicState(faction).orElseThrow();
        long spendable = Math.max(0L,
                economy.treasuryMilliCredits() - economy.treasuryReserveFloorMilliCredits());
        if (spendable >= required) return;
        long amount = required - spendable;
        WalletComponent source = new WalletComponent(amount);
        assertTrue(world.transferToFactionTreasury(
                faction,
                source,
                "stage21i-explicit-mission-fixture-funding",
                amount,
                "stage21i-explicit-mission-fixture-funding"));
        assertEquals(0L, source.getBalanceMilliCredits());
    }

    private static void assertNoStage21HMoneySourceOrSink(WorldSimulation world) {
        boolean forbidden = world.snapshot().systems().stream()
                .flatMap(system -> system.simulationState().ledger().entries().stream())
                .anyMatch(entry -> entry.reason().startsWith("stage21h-mission")
                        && (entry.type() == Type.MONEY_SOURCE || entry.type() == Type.MONEY_SINK));
        assertFalse(forbidden);
    }

    private record MilitaryFleet(FleetId fleetId, int factionId, StarSystemId systemId) { }

    private record FreightMissionContext(
            FreighterState freighter,
            TransportOrderState order,
            Stage21HNpcMissionService service,
            MissionContract mission,
            com.spacesim.persistence.Stage18IndustrialState savedIndustry,
            com.spacesim.world.Stage20DiscoveryKnowledgeState discovery,
            long issuerTreasuryBeforeOffer) { }

    private record ScenarioDigest(
            String goalId,
            String crisisId,
            String warId,
            FleetId destroyedFleetId,
            FleetId survivingFleetId,
            StarSystemId controlledSystemId,
            WarStatus finalWarStatus,
            SettlementStatus settlementStatus,
            MissionStatus missionStatus,
            int reputation,
            double deliveredMassKg,
            List<FleetId> remainingFleetIds) { }

    private record ScenarioResult(
            byte[] finalPhysicalCheckpoint,
            byte[] finalStage21ICheckpoint,
            ScenarioDigest digest) { }
}
