package com.spacesim.ui;

import com.spacesim.content.ship.Stage21GeneratedMilitaryEngineeringCatalog;
import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.persistence.Stage20DiscoveryPersistentState;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.LiveRuntime;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21AGeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage21AGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21BGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21CGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21DGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21EGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21FGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21HGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21HGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimeMigration;
import com.spacesim.warfare.Stage19ConflictRuntime;
import com.spacesim.world.DiplomaticLifecycleService;
import com.spacesim.world.DiplomaticLifecycleState;
import com.spacesim.world.DiplomaticLifecycleState.ProposalKind;
import com.spacesim.world.DiplomaticLifecycleState.RelationEvent;
import com.spacesim.world.DiplomaticLifecycleState.RelationFactor;
import com.spacesim.world.DiplomaticLifecycleState.WarGoal;
import com.spacesim.world.DiplomaticLifecycleState.WarGoalKind;
import com.spacesim.world.FactionStrategicIntentState;
import com.spacesim.world.FleetCommandState;
import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.FleetOrderState;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.FleetCommandState.OrderStatus;
import com.spacesim.world.FleetCommandState.OrderType;
import com.spacesim.world.FleetForceRegistry;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetReadinessEvaluator;
import com.spacesim.world.FleetReadinessState;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.Stage20DiscoveryKnowledgeState;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryEvidence;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoverySource;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryState;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.ResourceKnowledge;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticKnowledge;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectKind;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectRef;
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
import com.spacesim.world.StrategicOperationState;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import com.spacesim.world.TerritorialTransitionState;
import com.spacesim.world.TerritorialTransitionState.OccupationState;
import com.spacesim.world.TerritorialTransitionState.OccupationStatus;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Non-vacuous acceptance for every Stage-21I read-only presentation surface. */
final class Stage21IFullSurfaceUiProjectorAcceptanceTest {

    @Test
    void allRequiredLivingWorldSurfacesProjectAuthoritativeEvidenceAndRespectViewerBounds() {
        LiveRuntime stage20 = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED + 43L).runtime();
        var initialWorld = stage20.captureState().worldState();
        FleetForceRegistry forces = FleetForceRegistry.reconstruct(
                initialWorld,
                new FleetReadinessEvaluator(Stage21GeneratedMilitaryEngineeringCatalog.load()),
                java.util.Map.of());
        var selected = forces.entries().stream()
                .filter(entry -> entry.factionId() >= 0)
                .filter(entry -> entry.locationKind() == FleetLocationKind.IN_SYSTEM)
                .findFirst()
                .orElseThrow();
        String viewer = initialWorld.factionIdentities().stream()
                .filter(identity -> identity.runtimeFactionId() == selected.factionId())
                .map(identity -> identity.stableFactionId())
                .findFirst()
                .orElseThrow();
        String counterparty = initialWorld.factions().stream()
                .map(value -> value.factionContentId())
                .filter(value -> !value.equals(viewer))
                .findFirst()
                .orElseThrow();
        var viewerStrategy = initialWorld.factionStrategies().stream()
                .filter(value -> value.factionContentId().equals(viewer))
                .findFirst()
                .orElseThrow();
        var claimTarget = initialWorld.topology().systems().stream()
                .map(value -> value.id())
                .filter(value -> !viewerStrategy.controlledSystems().contains(value))
                .findFirst()
                .orElseThrow();
        stage20.world().declareTerritorialClaim(viewer, claimTarget);

        long now = stage20.world().getAuthoritativeWorldTick();
        Stage19ConflictRuntime warfare = new Stage19ConflictRuntime(Stage19ConflictState.empty(now));
        DiplomaticLifecycleService diplomacy = new DiplomaticLifecycleService(
                stage20.world(), warfare, DiplomaticLifecycleState.empty(now));
        diplomacy.remember(viewer, counterparty, new RelationEvent(
                "stage21i.ui.relation",
                RelationFactor.THREAT,
                -40,
                now,
                "stage21i.ui.observation"));
        var ultimatum = diplomacy.propose(new DiplomaticLifecycleService.ProposalRequest(
                "goal.stage21i.ui.war",
                viewer,
                counterparty,
                ProposalKind.ULTIMATUM,
                "stage21i.ui.security",
                List.of(),
                List.of(),
                now + 500L));
        var crisis = diplomacy.openCrisis(ultimatum.proposalId(), "goal.stage21i.ui.war", now + 500L);
        crisis = diplomacy.escalateCrisis(crisis.crisisId(), "stage21i.ui.pressure", now + 500L);
        crisis = diplomacy.escalateCrisis(crisis.crisisId(), "stage21i.ui.ultimatum", now + 500L);
        crisis = diplomacy.escalateCrisis(crisis.crisisId(), "stage21i.ui.war-authorized", now + 500L);
        var war = diplomacy.declareWarFromCrisis(crisis.crisisId(), List.of(
                new WarGoal("stage21i.ui.war-goal.viewer", viewer, WarGoalKind.SECURITY,
                        "system:" + selected.systemId().value(), true),
                new WarGoal("stage21i.ui.war-goal.counterparty", counterparty, WarGoalKind.SECURITY,
                        "system:" + claimTarget.value(), true)));

        List<String> actors = List.of(viewer, counterparty).stream().sorted().toList();
        Stage21AGeneratedWorldRuntimePersistentState stage21A = withViewerKnowledge(
                Stage21AGeneratedWorldRuntimeBridge.materializeBootstrap(stage20, actors, 30L).captureState(),
                viewer,
                claimTarget,
                now);
        Stage21BGeneratedWorldRuntimePersistentState stage21B = new Stage21BGeneratedWorldRuntimePersistentState(
                Stage21BGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                Stage21BGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                stage21A,
                actors.stream().map(FactionStrategicIntentState::initial).toList());
        Stage21CGeneratedWorldRuntimePersistentState stage21C = new Stage21CGeneratedWorldRuntimePersistentState(
                Stage21CGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                Stage21CGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                stage21B,
                diplomacy.snapshot(),
                warfare.snapshot());

        CommandGroupState group = new CommandGroupState(
                1L,
                selected.factionId(),
                "Stage-21I UI operation group",
                List.of(selected.fleetId()),
                selected.systemId(),
                false,
                false,
                FleetReadinessState.FULL);
        FleetOrderState order = new FleetOrderState(
                1L,
                group.id(),
                OrderType.INVADE,
                OrderSource.AI,
                selected.systemId(),
                List.of(selected.systemId()),
                0,
                now,
                now + 500L,
                OrderStatus.ACTIVE);
        FleetCommandState command = new FleetCommandState(2L, 2L, List.of(group), List.of(order));
        Stage21DGeneratedWorldRuntimePersistentState stage21D =
                Stage21DGeneratedWorldRuntimePersistentState.compose(stage21C, command);

        OperationState operation = new OperationState(
                1L,
                OperationType.INVASION,
                group.id(),
                order.id(),
                selected.factionId(),
                List.of(selected.fleetId()),
                selected.systemId(),
                selected.systemId(),
                "system:" + selected.systemId().value(),
                RulesOfEngagement.DECLARED_HOSTILES,
                new SupplyPolicy(3_000, 1_000, 200L),
                new WithdrawalPolicy(selected.systemId(), 2_000, true, true),
                OperationStatus.ACTIVE,
                now,
                now,
                -1L,
                null,
                null);
        Stage21EGeneratedWorldRuntimePersistentState stage21E =
                Stage21EGeneratedWorldRuntimePersistentState.compose(
                        stage21D, new StrategicOperationState(2L, List.of(operation)));
        OccupationState occupation = new OccupationState(
                viewer,
                selected.systemId(),
                operation.id(),
                now,
                now,
                120L,
                -1L,
                false,
                false,
                OccupationStatus.OCCUPYING);
        Stage21FGeneratedWorldRuntimePersistentState stage21F =
                Stage21FGeneratedWorldRuntimePersistentState.compose(
                        stage21E, new TerritorialTransitionState(List.of(occupation)));

        Stage21HGeneratedWorldRuntimePersistentState checkpoint = withNpcMission(
                Stage21IGeneratedWorldRuntimeMigration.migrate(stage21F).stage21HRuntime(),
                viewer,
                selected.systemId());
        byte[] before = Stage21HGeneratedWorldRuntimePersistenceCodec.encode(checkpoint);

        Stage21ILivingWorldUiProjector projector = new Stage21ILivingWorldUiProjector();
        Stage21ILivingWorldUiSnapshot snapshot = projector.project(checkpoint, viewer);
        Stage21ILivingWorldUiSnapshot repeated = projector.project(checkpoint, viewer);

        assertEquals(snapshot, repeated);
        assertArrayEquals(before, Stage21HGeneratedWorldRuntimePersistenceCodec.encode(checkpoint));
        Set<String> overlayKinds = snapshot.overlays().stream()
                .map(Stage21ILivingWorldUiSnapshot.OverlayRow::kind)
                .collect(Collectors.toSet());
        assertTrue(overlayKinds.containsAll(Set.of(
                "MARKET_ACCESS",
                "TERRITORIAL_CLAIM",
                "TERRITORIAL_CONTROL",
                "OCCUPATION",
                "WAR",
                "FRONT",
                "KNOWN_INTELLIGENCE")));
        assertTrue(snapshot.overlays().stream()
                .filter(row -> row.kind().equals("KNOWN_INTELLIGENCE") || row.kind().equals("FRONT"))
                .allMatch(row -> row.actorId().equals(viewer) && row.visibility().equals("PRIVATE")));
        assertTrue(snapshot.overlays().stream()
                .filter(row -> Set.of("TERRITORIAL_CLAIM", "TERRITORIAL_CONTROL", "OCCUPATION", "WAR")
                        .contains(row.kind()))
                .allMatch(row -> row.visibility().equals("PUBLIC")));

        var counterpartyRow = snapshot.factions().stream()
                .filter(row -> row.factionId().equals(counterparty))
                .findFirst()
                .orElseThrow();
        assertFalse(counterpartyRow.crises().isEmpty());
        assertTrue(counterpartyRow.wars().stream().anyMatch(value -> value.startsWith(war.warId() + ":")));
        assertEquals(1, snapshot.military().size());
        assertFalse(snapshot.military().get(0).operation().equals("NONE"));
        assertFalse(snapshot.military().get(0).route().isEmpty());

        assertEquals(1, snapshot.npcMissions().size());
        var npc = snapshot.npcMissions().get(0);
        assertFalse(npc.knownFacts().isEmpty());
        assertEquals("mission.stage21i.ui.completed", npc.missionId());
        assertEquals("COMPLETED", npc.missionStatus());
        assertTrue(snapshot.timeline().stream().anyMatch(row ->
                row.eventType().equals("RELATION_THREAT") && row.evidenceRef().equals("stage21i.ui.relation")));
        assertTrue(snapshot.timeline().stream().anyMatch(row ->
                row.eventType().equals("MISSION_COMPLETED")
                        && row.evidenceRef().equals("mission.stage21i.ui.completed")));

        Stage21ILivingWorldUiSnapshot otherViewer = projector.project(checkpoint, counterparty);
        assertTrue(otherViewer.npcMissions().isEmpty(),
                "viewer-bounded NPC inspection must not expose another faction's contacts");
        assertTrue(otherViewer.timeline().stream().noneMatch(row ->
                row.evidenceRef().equals("mission.stage21i.ui.completed")
                        || row.evidenceRef().equals("stage21i.ui.relation")));
        assertTrue(otherViewer.overlays().stream().noneMatch(row ->
                row.kind().equals("KNOWN_INTELLIGENCE") && row.subjectId().contains("stage21i.ui")));
    }

    private static Stage21AGeneratedWorldRuntimePersistentState withViewerKnowledge(
            Stage21AGeneratedWorldRuntimePersistentState stage21A,
            String viewer,
            com.spacesim.world.StarSystemId systemId,
            long now) {
        Stage20GeneratedWorldRuntimePersistentState stage20 = stage21A.stage20Runtime();
        Stage20GeneratedCampaignPersistentState campaign = stage20.campaign();
        Stage20DiscoveryPersistentState discovery = campaign.discoveryState();

        DiscoveryEvidence evidence = new DiscoveryEvidence(
                DiscoverySource.PHYSICAL_VISIT_OR_SURVEY,
                "stage21i.ui.discovery-evidence",
                (double) now,
                OptionalDouble.empty());
        StaticKnowledge known = new StaticKnowledge(
                new StaticObjectRef(systemId, StaticObjectKind.INFRASTRUCTURE, "station.stage21i.ui"),
                DiscoveryState.KNOWN_STATIC_LOCATION,
                Optional.of("station.stage21i.ui"),
                Optional.of(LocalPhysicalPosition.origin()),
                ResourceKnowledge.none(),
                List.of(evidence),
                (double) now,
                (double) now);
        ArrayList<Stage20DiscoveryKnowledgeState> knowledge = new ArrayList<>(discovery.knowledgeStates());
        knowledge.removeIf(state -> state.ownerId().equals(viewer));
        knowledge.add(new Stage20DiscoveryKnowledgeState(viewer, List.of(known)));
        Stage20DiscoveryPersistentState enrichedDiscovery = new Stage20DiscoveryPersistentState(
                discovery.envelopeVersion(),
                discovery.rootSeed(),
                discovery.worldGenerationVersion(),
                discovery.worldFingerprint(),
                knowledge);
        Stage20GeneratedCampaignPersistentState enrichedCampaign = new Stage20GeneratedCampaignPersistentState(
                campaign.schemaVersion(),
                campaign.generationIdentity(),
                campaign.materializedWorld(),
                campaign.materializationState(),
                campaign.industrialState(),
                enrichedDiscovery,
                campaign.openRuntimeBoundaries());
        Stage20GeneratedWorldRuntimePersistentState enrichedStage20 =
                new Stage20GeneratedWorldRuntimePersistentState(
                        stage20.schemaVersion(),
                        stage20.bridgeVersion(),
                        enrichedCampaign,
                        stage20.worldState(),
                        stage20.activeSystemId(),
                        stage20.freight(),
                        stage20.localFleetPhysicalStates());
        return new Stage21AGeneratedWorldRuntimePersistentState(
                stage21A.schemaVersion(),
                stage21A.runtimeVersion(),
                enrichedStage20,
                stage21A.livingActors());
    }

    private static Stage21HGeneratedWorldRuntimePersistentState withNpcMission(
            Stage21HGeneratedWorldRuntimePersistentState checkpoint,
            String viewer,
            com.spacesim.world.StarSystemId location) {
        long tick = checkpoint.npcMissionState().simulationTick();
        NpcKnowledgeFact fact = new NpcKnowledgeFact(
                "fact.stage21i.ui.shortage",
                viewer,
                KnowledgeKind.ACTOR_OBSERVATION,
                "ECONOMIC.RESOURCE_DEFICIT",
                4_000,
                "stage21i.ui.shortage-observation",
                tick,
                -1L);
        NpcState npc = new NpcState(
                "npc.stage21i.ui.logistics",
                "npc.stage21i.ui.logistics.name",
                NpcRole.TRADE_LOGISTICS,
                viewer,
                location,
                NpcAvailability.AVAILABLE,
                List.of(fact));
        MissionContract mission = new MissionContract(
                "mission.stage21i.ui.completed",
                MissionTemplate.EMERGENCY_SUPPLY_DELIVERY,
                1,
                npc.npcId(),
                viewer,
                List.of(fact.factId()),
                new MissionObjective(
                        ObjectiveAuthority.FREIGHT,
                        ObjectiveKind.FREIGHT_ORDER_DELIVERED_KG_AT_LEAST,
                        "freight-order.stage21i.ui",
                        0L,
                        1L,
                        ""),
                tick,
                tick + 500L,
                MissionStatus.COMPLETED,
                tick,
                1_000L,
                0L,
                "COMPLETED_STAGE21I_UI",
                List.of());
        Stage21HNpcMissionState npcState = new Stage21HNpcMissionState(
                Stage21HNpcMissionState.CURRENT_VERSION,
                tick,
                1L,
                List.of(npc),
                List.of(mission),
                List.of(),
                List.of());
        return Stage21HGeneratedWorldRuntimePersistentState.compose(
                checkpoint.stage21GRuntime(), npcState);
    }
}
