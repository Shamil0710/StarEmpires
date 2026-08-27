package com.spacesim.ui;

import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.Stage21HGeneratedWorldRuntimePersistentState;
import com.spacesim.world.DiplomaticLifecycleState;
import com.spacesim.world.FactionDiplomacyState;
import com.spacesim.world.FactionIdentityResolver;
import com.spacesim.world.FactionStrategicIntentState;
import com.spacesim.world.FleetCommandState;
import com.spacesim.world.Stage21HNpcMissionState;
import com.spacesim.world.StrategicGoalState;
import com.spacesim.world.StrategicOperationState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic actor-bounded Stage-21I presentation projector.
 *
 * <p>This adapter only reads accepted Stage-20/21A..H persistence authorities. It deliberately has no
 * command methods and therefore cannot mutate diplomacy, fleets, economy, operations, territory,
 * missions or knowledge while preparing UI state.</p>
 */
public final class Stage21ILivingWorldUiProjector {
    /**
     * Projects one immutable actor-bounded UI snapshot.
     *
     * @param checkpoint accepted Stage-21H checkpoint containing the Stage-21A..H authority chain
     * @param viewerFactionId faction whose bounded knowledge and owned commands are being inspected
     * @return deterministic read-only presentation snapshot for the requested viewer
     */
    public Stage21ILivingWorldUiSnapshot project(
            Stage21HGeneratedWorldRuntimePersistentState checkpoint,
            String viewerFactionId) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        String viewer = requireText(viewerFactionId, "viewerFactionId");

        var stage21G = checkpoint.stage21GRuntime();
        var stage21F = stage21G.stage21FRuntime();
        var stage21E = stage21F.stage21ERuntime();
        var stage21D = stage21E.stage21DRuntime();
        var stage21C = stage21D.stage21CRuntime();
        var stage21B = stage21C.stage21BRuntime();
        var stage21A = stage21B.stage21ARuntime();
        var world = stage21A.stage20Runtime().worldState();

        Set<String> actorIds = stage21A.livingActors().stream()
                .map(actor -> actor.factionContentId())
                .collect(Collectors.toUnmodifiableSet());
        if (!actorIds.contains(viewer)) {
            throw new IllegalArgumentException("unknown Stage-21I viewer faction: " + viewer);
        }

        FactionIdentityResolver identities = FactionIdentityResolver.createDefault(
                ContentCatalogLoader.loadDefault(), world.factionIdentities());
        int viewerRuntimeId = identities.runtimeId(viewer)
                .orElseThrow(() -> new IllegalArgumentException("viewer has no runtime faction identity: " + viewer));

        Map<String, FactionStrategicIntentState> intentsByFaction = stage21B.strategicIntents().stream()
                .collect(Collectors.toUnmodifiableMap(FactionStrategicIntentState::factionContentId, value -> value));
        DiplomaticLifecycleState diplomacy = stage21C.diplomacyLifecycle();

        List<Stage21ILivingWorldUiSnapshot.FactionRow> factions = projectFactions(
                actorIds,
                viewer,
                identities,
                intentsByFaction,
                diplomacy,
                world.factionDiplomacyStates());
        List<Stage21ILivingWorldUiSnapshot.MilitaryRow> military = projectMilitary(
                viewerRuntimeId, stage21D.fleetCommandState(), stage21E.operationState());
        List<Stage21ILivingWorldUiSnapshot.TimelineRow> timeline = projectTimeline(
                viewer, intentsByFaction.get(viewer), diplomacy, checkpoint.npcMissionState());
        List<Stage21ILivingWorldUiSnapshot.NpcMissionRow> npcMissions = projectNpcMissions(
                viewer, checkpoint.npcMissionState());

        return new Stage21ILivingWorldUiSnapshot(
                viewer,
                checkpoint.npcMissionState().simulationTick(),
                factions,
                military,
                timeline,
                npcMissions);
    }

    private static List<Stage21ILivingWorldUiSnapshot.FactionRow> projectFactions(
            Set<String> actorIds,
            String viewer,
            FactionIdentityResolver identities,
            Map<String, FactionStrategicIntentState> intentsByFaction,
            DiplomaticLifecycleState diplomacy,
            List<FactionDiplomacyState> institutionalDiplomacy) {
        Map<String, DiplomaticLifecycleState.RelationMemory> viewerRelations = new HashMap<>();
        diplomacy.relationMemories().stream()
                .filter(memory -> memory.ownerFactionId().equals(viewer))
                .forEach(memory -> viewerRelations.put(memory.targetFactionId(), memory));

        ArrayList<Stage21ILivingWorldUiSnapshot.FactionRow> result = new ArrayList<>();
        actorIds.stream().sorted().forEach(factionId -> {
            boolean own = factionId.equals(viewer);
            FactionStrategicIntentState intents = intentsByFaction.get(factionId);
            List<StrategicGoalState> visibleGoals = own && intents != null ? intents.openGoals() : List.of();
            DiplomaticLifecycleState.RelationMemory relation = viewerRelations.get(factionId);
            String relationValue = own ? "SELF" : relation == null ? "UNKNOWN" : Integer.toString(relation.derivedRelation());

            List<String> treaties = own
                    ? List.of()
                    : projectActiveTreaties(viewer, factionId, institutionalDiplomacy, diplomacy.simulationTick());
            List<String> crises = diplomacy.crises().stream()
                    .filter(crisis -> crisis.includes(viewer) && crisis.includes(factionId))
                    .map(crisis -> crisis.crisisId() + ":" + crisis.escalation())
                    .sorted().toList();
            List<String> wars = diplomacy.wars().stream()
                    .filter(war -> includesPair(war, viewer, factionId))
                    .map(war -> war.warId() + ":" + war.status())
                    .sorted().toList();
            List<String> interests = visibleGoals.stream()
                    .map(goal -> goal.type().toString())
                    .distinct().sorted().toList();
            List<String> goals = visibleGoals.stream()
                    .map(goal -> goal.goalId() + ":" + goal.type() + ":" + goal.targetId() + ":" + goal.lifecycle())
                    .sorted().toList();
            List<String> evidence = visibleGoals.stream()
                    .map(goal -> goal.goalId() + "<-" + goal.sourceEvidence())
                    .sorted().toList();

            result.add(new Stage21ILivingWorldUiSnapshot.FactionRow(
                    factionId,
                    identities.displayName(factionId).orElse(factionId),
                    relationValue,
                    interests,
                    treaties,
                    crises,
                    wars,
                    goals,
                    evidence,
                    own
                            ? "stage21b.strategic-intents+stage20.institutional-diplomacy+stage21c.viewer-diplomacy"
                            : "stage20.institutional-diplomacy+stage21c.viewer-diplomacy"));
        });
        return List.copyOf(result);
    }

    private static List<String> projectActiveTreaties(
            String viewer,
            String counterparty,
            List<FactionDiplomacyState> diplomacyStates,
            long tick) {
        return diplomacyStates.stream()
                .flatMap(owner -> owner.treaties().stream()
                        .filter(treaty -> owner.factionContentId().equals(viewer)
                                        && treaty.counterpartyFactionContentId().equals(counterparty)
                                || owner.factionContentId().equals(counterparty)
                                        && treaty.counterpartyFactionContentId().equals(viewer))
                        .filter(treaty -> treaty.activeAt(tick))
                        .map(treaty -> treaty.treatyId()
                                + ":" + treaty.status()
                                + ":" + treaty.clauses().stream()
                                        .map(clause -> clause.kind().toString())
                                        .distinct()
                                        .sorted()
                                        .collect(Collectors.joining(","))))
                .distinct()
                .sorted()
                .toList();
    }

    private static boolean includesPair(DiplomaticLifecycleState.War war, String viewer, String other) {
        if (viewer.equals(other)) return false;
        return war.factionA().equals(viewer) && war.factionB().equals(other)
                || war.factionB().equals(viewer) && war.factionA().equals(other);
    }

    private static List<Stage21ILivingWorldUiSnapshot.MilitaryRow> projectMilitary(
            int viewerRuntimeId,
            FleetCommandState commands,
            StrategicOperationState operations) {
        Map<Long, StrategicOperationState.OperationState> operationByGroup = new HashMap<>();
        operations.operations().stream()
                .filter(operation -> operation.factionId() == viewerRuntimeId)
                .sorted(Comparator.comparingLong(StrategicOperationState.OperationState::id))
                .forEach(operation -> operationByGroup.put(operation.commandGroupId(), operation));

        return commands.groups().stream()
                .filter(group -> group.factionId() == viewerRuntimeId)
                .map(group -> {
                    var order = commands.activeOrderFor(group.id()).orElse(null);
                    var operation = operationByGroup.get(group.id());
                    List<String> route = order == null ? List.of() : order.route().stream().map(Object::toString).toList();
                    return new Stage21ILivingWorldUiSnapshot.MilitaryRow(
                            group.id(),
                            group.name(),
                            group.memberFleetIds().stream().map(Object::toString).toList(),
                            order == null ? "IDLE" : order.id() + ":" + order.type() + ":" + order.status(),
                            "PHYSICAL_FLEET_AUTHORITY",
                            route,
                            "PHYSICAL_LOGISTICS_AUTHORITY",
                            operation == null ? "NONE" : operation.id() + ":" + operation.status(),
                            order == null ? group.homeSystemId().toString() : order.targetSystemId().toString(),
                            "stage21d.command+stage21e.operation;readiness/supply=ordinary-physical-authority");
                })
                .sorted(Comparator.comparingLong(Stage21ILivingWorldUiSnapshot.MilitaryRow::commandGroupId))
                .toList();
    }

    private static List<Stage21ILivingWorldUiSnapshot.TimelineRow> projectTimeline(
            String viewer,
            FactionStrategicIntentState viewerIntents,
            DiplomaticLifecycleState diplomacy,
            Stage21HNpcMissionState npcState) {
        ArrayList<Stage21ILivingWorldUiSnapshot.TimelineRow> rows = new ArrayList<>();
        diplomacy.relationMemories().stream()
                .filter(memory -> memory.ownerFactionId().equals(viewer))
                .forEach(memory -> memory.events().forEach(event -> rows.add(
                        new Stage21ILivingWorldUiSnapshot.TimelineRow(
                                event.observedTick(), "PRIVATE", viewer, "RELATION_" + event.factor(),
                                memory.targetFactionId() + ":" + event.impact(), event.eventId()))));
        if (viewerIntents != null) {
            viewerIntents.goals().forEach(goal -> rows.add(new Stage21ILivingWorldUiSnapshot.TimelineRow(
                    goal.updatedAtTick(), "PRIVATE", viewer, "STRATEGIC_GOAL_" + goal.lifecycle(),
                    goal.type() + " -> " + goal.targetId(), goal.goalId())));
        }
        npcState.missions().stream()
                .filter(mission -> mission.issuerFactionId().equals(viewer))
                .forEach(mission -> rows.add(new Stage21ILivingWorldUiSnapshot.TimelineRow(
                        mission.statusUpdatedTick(), "PRIVATE", viewer, "MISSION_" + mission.status(),
                        mission.template() + " -> " + mission.objective().subjectId(), mission.missionId())));
        rows.sort(Comparator.comparingLong(Stage21ILivingWorldUiSnapshot.TimelineRow::tick)
                .thenComparing(Stage21ILivingWorldUiSnapshot.TimelineRow::evidenceRef));
        return List.copyOf(rows);
    }

    private static List<Stage21ILivingWorldUiSnapshot.NpcMissionRow> projectNpcMissions(
            String viewer,
            Stage21HNpcMissionState state) {
        Map<String, Stage21HNpcMissionState.MissionContract> missionByIssuer = new HashMap<>();
        state.missions().stream()
                .filter(mission -> mission.issuerFactionId().equals(viewer))
                .sorted()
                .forEach(mission -> missionByIssuer.putIfAbsent(mission.issuerNpcId(), mission));

        return state.npcs().stream()
                .filter(npc -> npc.factionContentId().equals(viewer))
                .map(npc -> {
                    Stage21HNpcMissionState.MissionContract mission = missionByIssuer.get(npc.npcId());
                    List<String> facts = npc.currentKnowledge(state.simulationTick()).stream()
                            .map(fact -> fact.factId() + ":" + fact.claimCode() + "<-" + fact.provenanceId())
                            .sorted().toList();
                    return new Stage21ILivingWorldUiSnapshot.NpcMissionRow(
                            npc.npcId(), npc.nameKey(), npc.role().toString(), npc.availability().toString(),
                            npc.locationSystemId().toString(), facts,
                            mission == null ? "" : mission.missionId(),
                            mission == null ? "" : mission.template().toString(),
                            mission == null ? "" : mission.status().toString(),
                            mission == null ? "" : mission.objective().authority() + ":" + mission.objective().kind()
                                    + ":" + mission.objective().subjectId(),
                            mission == null ? -1L : mission.deadlineTick(),
                            mission == null ? 0L : mission.escrowMilliCredits(),
                            mission == null ? "stage21h.npc-knowledge" : "stage21h.mission->" + mission.objective().authority());
                })
                .sorted(Comparator.comparing(Stage21ILivingWorldUiSnapshot.NpcMissionRow::npcId))
                .toList();
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) throw new IllegalArgumentException(label + " cannot be blank");
        return checked;
    }
}
