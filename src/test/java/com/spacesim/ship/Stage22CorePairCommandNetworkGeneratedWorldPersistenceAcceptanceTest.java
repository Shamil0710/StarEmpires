package com.spacesim.ship;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.content.Stage22CorePairEvidenceArchive;
import com.spacesim.content.Stage22CorePairExperimentProtocol.Permutation;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistentState;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.ImportedCombatantState;
import com.spacesim.ship.TacticalSurvivalPlanner.DecisionReason;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 B01/B11 generated-world save-continuation for degraded command and sensors.
 *
 * <p>Four already-existing generated-world military FleetIds receive the exact Stage-22 command
 * variants. Both primary ships have their fitted local radar physically failed before the ordinary
 * Stage-20.5 checkpoint is captured. The complete generated world is encoded/decoded through the
 * production runtime codec, restored, and only then imported into the ordinary Stage-19 tactical
 * runtime. Direct and restored runs use identical authored geometry and actor-bounded datalink
 * behavior; no tactical contacts, hostile transforms or control decisions are persisted.</p>
 *
 * <p>The expected continuation is therefore causal: each damaged primary may recover awareness only
 * from its intact same-side wingman's current measurement through two physical datalink endpoints.
 * Persistence must retain the failed radar and exact fitted network while Stage-19 reconstructs all
 * contacts and decisions from scratch after load.</p>
 */
class Stage22CorePairCommandNetworkGeneratedWorldPersistenceAcceptanceTest {
    private static final String SENSOR_MOUNT = "utility_sensor";
    private static final int CONTROL_WINDOW_TICKS = 20;
    private static final long SCENARIO_SEED = 22_611L;

    @Test
    void b11ExactCommandNetworkReconstructsSameActorBoundedControlAfterGeneratedWorldRestore() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        List<FactionFleetPair> carriers = carrierPairs(runtime);

        Stage22CorePairTacticalFactory.CommandNetworkSkirmish source =
                Stage22CorePairTacticalFactory.createCommandNetworkSkirmish(
                        Permutation.DEFAULT, SCENARIO_SEED);
        LiveTacticalBattleRuntimeState sourceBattle = source.control().battleState();
        LiveTacticalInitialReadinessService initial = new LiveTacticalInitialReadinessService();
        initial.setModuleIntegrity(
                sourceBattle.requireCombatant(Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID),
                SENSOR_MOUNT,
                0d);
        initial.setModuleIntegrity(
                sourceBattle.requireCombatant(Stage22CorePairTacticalFactory.UNION_ENTITY_ID),
                SENSOR_MOUNT,
                0d);

        List<Binding> bindings = List.of(
                new Binding(Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID, carriers.get(0).firstFleetId()),
                new Binding(Stage22CorePairTacticalFactory.EMPIRE_COMMAND_WINGMAN_ID, carriers.get(0).secondFleetId()),
                new Binding(Stage22CorePairTacticalFactory.UNION_ENTITY_ID, carriers.get(1).firstFleetId()),
                new Binding(Stage22CorePairTacticalFactory.UNION_COMMAND_WINGMAN_ID, carriers.get(1).secondFleetId()));
        for (Binding binding : bindings) {
            EngineeringComponent exact = sourceBattle.requireCombatant(binding.tacticalEntityId()).engineering();
            entity(runtime, binding.fleetId()).add(copy(exact));
        }

        Stage20GeneratedWorldRuntimePersistentState checkpoint = runtime.captureState();
        byte[] encoded = Stage20GeneratedWorldRuntimePersistenceCodec.encode(checkpoint);
        Stage20GeneratedWorldRuntimePersistentState decoded =
                Stage20GeneratedWorldRuntimePersistenceCodec.decode(encoded);
        assertArrayEquals(encoded, Stage20GeneratedWorldRuntimePersistenceCodec.encode(decoded),
                "command-network generated-world checkpoint must be byte-stable");
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime restored =
                Stage20GeneratedWorldRuntimeBridge.restore(decoded);

        ScenarioResult direct = runFromGeneratedWorld(runtime, source, bindings);
        ScenarioResult reloaded = runFromGeneratedWorld(restored, source, bindings);
        assertEquals(direct, reloaded,
                "B11 actor-bounded command continuation must be identical after Stage-20.5 save/load");

        assertTrue(direct.empireContacts() > 0);
        assertTrue(direct.unionContacts() > 0);
        assertTrue(direct.empireTargetSelected());
        assertTrue(direct.unionTargetSelected());
        assertFalse(direct.empireFireAuthorized());
        assertFalse(direct.unionFireAuthorized());
        assertEquals(DecisionReason.SUBSYSTEM_DAMAGE, direct.empireSurvivalReason());
        assertEquals(DecisionReason.SUBSYSTEM_DAMAGE, direct.unionSurvivalReason());

        LinkedHashMap<String, Object> archive = new LinkedHashMap<>();
        archive.put("scenarioSeed", SCENARIO_SEED);
        archive.put("checkpointBytes", encoded.length);
        archive.put("bindings", bindings.stream().map(binding -> new BindingEvidence(
                binding.tacticalEntityId(), binding.fleetId().value())).toList());
        archive.put("direct", direct);
        archive.put("restored", reloaded);
        archive.put("continuationEqual", direct.equals(reloaded));
        Stage22CorePairEvidenceArchive.write(
                "B11-generated-world-command-network-save-continuation",
                archive,
                "Four ordinary generated-world FleetIds persist exact Stage-22 command fits and pre-save physical primary-radar failures through the production Stage-20.5 codec. Stage-19 reconstructs contacts and decisions from the restored engineering state rather than persisting tactical knowledge. Both primaries regain actor-bounded awareness only through intact allied datalinks and still retreat for physical subsystem damage. Wider campaign-level degraded-command scheduling remains a separate operational-horizon requirement.");
    }

    private static ScenarioResult runFromGeneratedWorld(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            Stage22CorePairTacticalFactory.CommandNetworkSkirmish source,
            List<Binding> bindings) {
        LiveTacticalBattleRuntimeState sourceBattle = source.control().battleState();
        ArrayList<ImportedCombatantState> imported = new ArrayList<>();
        for (Binding binding : bindings) {
            var template = sourceBattle.requireCombatant(binding.tacticalEntityId());
            EngineeringComponent engineering = entity(runtime, binding.fleetId())
                    .getComponent(EngineeringComponent.class);
            if (engineering == null) {
                throw new AssertionError("restored command-network FleetId lost engineering: " + binding.fleetId());
            }
            imported.add(new ImportedCombatantState(
                    binding.tacticalEntityId(),
                    template.spec().side(),
                    copy(engineering),
                    template.transform().position.x,
                    template.transform().position.y,
                    template.transform().velocity.x,
                    template.transform().velocity.y));
        }

        LiveTacticalBattleRuntimeState battle = LiveTacticalBattleRuntimeState.importExact(
                imported,
                source.content().engineering(),
                source.protection());
        LiveTacticalBattleControlRuntime control = new LiveTacticalBattleControlRuntime(battle);
        for (int tick = 0; tick < CONTROL_WINDOW_TICKS; tick++) {
            control.advanceOneTick();
        }

        var empire = control.controlState(Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID);
        var union = control.controlState(Stage22CorePairTacticalFactory.UNION_ENTITY_ID);
        return new ScenarioResult(
                battle.visibleContacts(Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID).size(),
                battle.visibleContacts(Stage22CorePairTacticalFactory.UNION_ENTITY_ID).size(),
                empire.intent().targetSelected(),
                union.intent().targetSelected(),
                empire.fireAuthorized(),
                union.fireAuthorized(),
                empire.survivalDecision().reason(),
                union.survivalDecision().reason(),
                control.fingerprint());
    }

    private static List<FactionFleetPair> carrierPairs(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime) {
        TreeMap<Integer, ArrayList<FleetId>> byFaction = new TreeMap<>();
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) continue;
            Entity entity = entity(runtime, placement.id());
            EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            if (engineering == null || faction == null) continue;
            byFaction.computeIfAbsent(faction.factionId, ignored -> new ArrayList<>())
                    .add(placement.id());
        }

        ArrayList<FactionFleetPair> result = new ArrayList<>();
        for (var entry : byFaction.entrySet()) {
            List<FleetId> fleets = entry.getValue().stream().sorted().toList();
            if (fleets.size() < 2) continue;
            result.add(new FactionFleetPair(entry.getKey(), fleets.get(0), fleets.get(1)));
            if (result.size() == 2) break;
        }
        if (result.size() < 2) {
            throw new AssertionError("generated world lacks two factions with two military FleetIds each");
        }
        return List.copyOf(result);
    }

    private static Entity entity(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId) {
        FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
        if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
            throw new IllegalStateException("command-network persistence carrier must be local");
        }
        return runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().require(placement.localEntityId());
    }

    private static EngineeringComponent copy(EngineeringComponent source) {
        return new EngineeringComponent(source.fit, source.runtimeState, source.instanceState);
    }

    private record FactionFleetPair(
            int factionId,
            FleetId firstFleetId,
            FleetId secondFleetId) { }

    private record Binding(long tacticalEntityId, FleetId fleetId) { }

    private record BindingEvidence(long tacticalEntityId, long fleetId) { }

    private record ScenarioResult(
            int empireContacts,
            int unionContacts,
            boolean empireTargetSelected,
            boolean unionTargetSelected,
            boolean empireFireAuthorized,
            boolean unionFireAuthorized,
            DecisionReason empireSurvivalReason,
            DecisionReason unionSurvivalReason,
            LiveTacticalBattleControlRuntime.BattleControlFingerprint fingerprint) { }
}
