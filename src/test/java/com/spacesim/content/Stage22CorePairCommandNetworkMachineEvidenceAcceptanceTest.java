package com.spacesim.content;

import com.spacesim.content.Stage22CorePairExperimentProtocol.RunCoordinate;
import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.Stage22CorePairCommandNetworkProjection;
import com.spacesim.ship.LiveTacticalBattleControlRuntime;
import com.spacesim.ship.LiveTacticalBattleRuntimeState;
import com.spacesim.ship.LiveTacticalInitialReadinessService;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.Stage22CorePairTacticalFactory;
import com.spacesim.ship.TacticalSurvivalPlanner.DecisionReason;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 B11 paired machine evidence for degraded sensors and the physical core command network.
 *
 * <p>The probe uses exact Stage-22 command-network destroyer variants and the ordinary Stage-19
 * observation/datalink/control runtime. A primary ship starts with its local radar physically failed.
 * It may select a hostile only when a same-side wingman's current local measurement crosses two
 * intact fitted datalink endpoints. Destroying either receiver or sender network mount removes the
 * track and therefore removes the actor-bounded tactical target on a fresh run.</p>
 *
 * <p>The failed radar is also real subsystem damage, so the ordinary survival planner remains
 * authoritative: once the relay exposes a threat, the primary must still retreat for
 * {@link DecisionReason#SUBSYSTEM_DAMAGE} and final fire authorization must remain false. The relay
 * is allowed to improve awareness and tactical intent, never to bypass own-ship damage state.</p>
 *
 * <p>No hidden hostile transform is exposed to the planner, no command bonus is synthesized and the
 * refit pays a literal utility-slot cost: the command variants have no shield at the displaced
 * {@code utility_defense} mount. The canonical 30-seed mirrored tuning schedule advances a bounded
 * one-second ordinary control window; no track quality, fire state or survival decision is injected.</p>
 */
class Stage22CorePairCommandNetworkMachineEvidenceAcceptanceTest {
    private static final String SENSOR_MOUNT = "utility_sensor";
    private static final String NETWORK_MOUNT = "utility_defense";
    private static final int CONTROL_WINDOW_TICKS = 20;

    @Test
    void b11RunsThirtyPairedExactCommandNetworkDegradationCells() {
        var vector = Stage22CorePairMachineEvidenceBatch.runScenario(
                "B11",
                "exact_command_network_sensor_degradation",
                Stage22CorePairCommandNetworkProjection.VERSION,
                Stage22CorePairExperimentProtocol.tuningSchedule(),
                (scenario, variant, profile, coordinate) -> observe(coordinate));

        assertEquals(Stage22CorePairExperimentProtocol.TUNING_SEED_COUNT, vector.pairedSeedCount());
        assertEquals(Stage22CorePairExperimentProtocol.TUNING_SEED_COUNT * 2, vector.runCount());
        assertTrue(vector.metricMeans().get("empire_linked_contacts") > 0d);
        assertTrue(vector.metricMeans().get("union_linked_contacts") > 0d);
        assertEquals(0d, vector.metricMeans().get("empire_receiver_severed_contacts"));
        assertEquals(0d, vector.metricMeans().get("union_receiver_severed_contacts"));
        assertEquals(0d, vector.metricMeans().get("empire_sender_severed_contacts"));
        assertEquals(0d, vector.metricMeans().get("union_sender_severed_contacts"));
        assertEquals(1d, vector.guardMetricMeans().get("exact_command_fits"));
        assertEquals(1d, vector.guardMetricMeans().get("paid_shield_tradeoff"));
        assertEquals(1d, vector.guardMetricMeans().get("linked_actor_bounded_targets"));
        assertEquals(1d, vector.guardMetricMeans().get("linked_ai_targets_relay"));
        assertEquals(1d, vector.guardMetricMeans().get("linked_survival_respects_sensor_damage"));
        assertEquals(1d, vector.guardMetricMeans().get("receiver_break_fail_closed"));
        assertEquals(1d, vector.guardMetricMeans().get("sender_break_fail_closed"));
        assertEquals(1d, vector.guardMetricMeans().get("deterministic_repeat"));
        assertEquals(0, vector.hardRuleBreachCount());

        LinkedHashMap<String, Object> archive = new LinkedHashMap<>();
        archive.put("scenarioId", vector.scenarioId());
        archive.put("scenarioVersion", vector.scenarioVersion());
        archive.put("variantId", vector.variantId());
        archive.put("profileId", vector.profileId());
        archive.put("pairedSeedCount", vector.pairedSeedCount());
        archive.put("runCount", vector.runCount());
        archive.put("metricMeans", vector.metricMeans());
        archive.put("guardMetricMeans", vector.guardMetricMeans());
        archive.put("hardRuleBreachCount", vector.hardRuleBreachCount());
        archive.put("evidenceFingerprint", vector.evidenceFingerprint());
        archive.put("observations", vector.observations());
        Stage22CorePairEvidenceArchive.write(
                "B11-command-network-degradation-paired-30",
                archive,
                "Thirty paired/mirrored exact Stage-22 command-network cells. Primaries with failed local radar acquire actor-bounded hostile awareness and target selection only from current allied measurements relayed through two physical fitted datalink endpoints during a bounded one-second ordinary control window. Because the failed radar is real subsystem damage, the ordinary survival planner retains authority: linked primaries retreat for SUBSYSTEM_DAMAGE and final fire authorization remains false. Breaking either datalink endpoint on a fresh run removes all hostile tracks and target selection. Command variants physically displace the authored utility shield; no faction-name command modifier or hidden hostile state is introduced.");
    }

    private static Stage22CorePairMachineEvidenceBatch.ObservationPayload observe(RunCoordinate coordinate) {
        ScenarioResult linked = run(coordinate, BreakMode.NONE);
        ScenarioResult linkedRepeat = run(coordinate, BreakMode.NONE);
        ScenarioResult receiverSevered = run(coordinate, BreakMode.RECEIVER);
        ScenarioResult senderSevered = run(coordinate, BreakMode.SENDER);

        boolean exactCommandFits = linked.exactCommandFits();
        boolean paidShieldTradeoff = linked.paidShieldTradeoff();
        boolean linkedActorBounded = linked.actorBoundedContacts();
        boolean linkedAiTargetsRelay = linked.empireContacts() > 0
                && linked.unionContacts() > 0
                && linked.empireTargetSelected()
                && linked.unionTargetSelected();
        boolean linkedSurvivalRespectsSensorDamage = linked.empireSurvivalReason() == DecisionReason.SUBSYSTEM_DAMAGE
                && linked.unionSurvivalReason() == DecisionReason.SUBSYSTEM_DAMAGE
                && !linked.empireFireAuthorized()
                && !linked.unionFireAuthorized();
        boolean receiverBreakFailClosed = receiverSevered.empireContacts() == 0
                && receiverSevered.unionContacts() == 0
                && !receiverSevered.empireTargetSelected()
                && !receiverSevered.unionTargetSelected()
                && !receiverSevered.empireFireAuthorized()
                && !receiverSevered.unionFireAuthorized();
        boolean senderBreakFailClosed = senderSevered.empireContacts() == 0
                && senderSevered.unionContacts() == 0
                && !senderSevered.empireTargetSelected()
                && !senderSevered.unionTargetSelected()
                && !senderSevered.empireFireAuthorized()
                && !senderSevered.unionFireAuthorized();
        boolean deterministicRepeat = linked.fingerprint().equals(linkedRepeat.fingerprint());

        List<String> breaches = new ArrayList<>();
        if (!exactCommandFits) breaches.add("b11_exact_command_fit_drift");
        if (!paidShieldTradeoff) breaches.add("b11_command_network_no_longer_pays_shield_slot");
        if (!linkedActorBounded) breaches.add("b11_relay_exposed_non_hostile_or_hidden_contact");
        if (!linkedAiTargetsRelay) breaches.add("b11_ai_does_not_target_physical_allied_relay_contact");
        if (!linkedSurvivalRespectsSensorDamage) breaches.add("b11_relay_bypasses_sensor_damage_survival_authority");
        if (!receiverBreakFailClosed) breaches.add("b11_receiver_datalink_break_does_not_fail_closed");
        if (!senderBreakFailClosed) breaches.add("b11_sender_datalink_break_does_not_fail_closed");
        if (!deterministicRepeat) breaches.add("b11_same_seed_input_not_deterministic");

        return new Stage22CorePairMachineEvidenceBatch.ObservationPayload(
                Map.ofEntries(
                        Map.entry("empire_linked_contacts", (double) linked.empireContacts()),
                        Map.entry("union_linked_contacts", (double) linked.unionContacts()),
                        Map.entry("empire_linked_fire_requested", linked.empireFireRequested() ? 1d : 0d),
                        Map.entry("union_linked_fire_requested", linked.unionFireRequested() ? 1d : 0d),
                        Map.entry("empire_linked_fire_authorized", linked.empireFireAuthorized() ? 1d : 0d),
                        Map.entry("union_linked_fire_authorized", linked.unionFireAuthorized() ? 1d : 0d),
                        Map.entry("empire_receiver_severed_contacts", (double) receiverSevered.empireContacts()),
                        Map.entry("union_receiver_severed_contacts", (double) receiverSevered.unionContacts()),
                        Map.entry("empire_sender_severed_contacts", (double) senderSevered.empireContacts()),
                        Map.entry("union_sender_severed_contacts", (double) senderSevered.unionContacts())),
                Map.ofEntries(
                        Map.entry("exact_command_fits", exactCommandFits ? 1d : 0d),
                        Map.entry("paid_shield_tradeoff", paidShieldTradeoff ? 1d : 0d),
                        Map.entry("linked_actor_bounded_targets", linkedActorBounded ? 1d : 0d),
                        Map.entry("linked_ai_targets_relay", linkedAiTargetsRelay ? 1d : 0d),
                        Map.entry("linked_survival_respects_sensor_damage", linkedSurvivalRespectsSensorDamage ? 1d : 0d),
                        Map.entry("receiver_break_fail_closed", receiverBreakFailClosed ? 1d : 0d),
                        Map.entry("sender_break_fail_closed", senderBreakFailClosed ? 1d : 0d),
                        Map.entry("deterministic_repeat", deterministicRepeat ? 1d : 0d)),
                breaches);
    }

    private static ScenarioResult run(RunCoordinate coordinate, BreakMode breakMode) {
        Stage22CorePairTacticalFactory.CommandNetworkSkirmish skirmish =
                Stage22CorePairTacticalFactory.createCommandNetworkSkirmish(
                        coordinate.permutation(), coordinate.seed());
        LiveTacticalBattleControlRuntime control = skirmish.control();
        LiveTacticalBattleRuntimeState battle = control.battleState();
        LiveTacticalInitialReadinessService initial = new LiveTacticalInitialReadinessService();

        initial.setModuleIntegrity(
                battle.requireCombatant(Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID), SENSOR_MOUNT, 0d);
        initial.setModuleIntegrity(
                battle.requireCombatant(Stage22CorePairTacticalFactory.UNION_ENTITY_ID), SENSOR_MOUNT, 0d);
        if (breakMode == BreakMode.RECEIVER) {
            initial.setModuleIntegrity(
                    battle.requireCombatant(Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID), NETWORK_MOUNT, 0d);
            initial.setModuleIntegrity(
                    battle.requireCombatant(Stage22CorePairTacticalFactory.UNION_ENTITY_ID), NETWORK_MOUNT, 0d);
        } else if (breakMode == BreakMode.SENDER) {
            initial.setModuleIntegrity(
                    battle.requireCombatant(Stage22CorePairTacticalFactory.EMPIRE_COMMAND_WINGMAN_ID), NETWORK_MOUNT, 0d);
            initial.setModuleIntegrity(
                    battle.requireCombatant(Stage22CorePairTacticalFactory.UNION_COMMAND_WINGMAN_ID), NETWORK_MOUNT, 0d);
        }

        boolean exactCommandFits = matchesFit(
                skirmish,
                Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID,
                Stage22CorePairCommandNetworkProjection.EMPIRE_DESTROYER_COMMAND_FIT)
                && matchesFit(
                skirmish,
                Stage22CorePairTacticalFactory.UNION_ENTITY_ID,
                Stage22CorePairCommandNetworkProjection.UNION_DESTROYER_COMMAND_FIT);
        boolean paidShieldTradeoff = paidNetworkTradeoff(
                battle, Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID)
                && paidNetworkTradeoff(battle, Stage22CorePairTacticalFactory.UNION_ENTITY_ID);

        for (int tick = 0; tick < CONTROL_WINDOW_TICKS; tick++) {
            control.advanceOneTick();
        }
        var empireControl = control.controlState(Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID);
        var unionControl = control.controlState(Stage22CorePairTacticalFactory.UNION_ENTITY_ID);
        int empireContacts = battle.visibleContacts(Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID).size();
        int unionContacts = battle.visibleContacts(Stage22CorePairTacticalFactory.UNION_ENTITY_ID).size();
        boolean actorBounded = battle.visibleContacts(Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID).stream()
                .allMatch(value -> battle.requireCombatant(value.track().targetId()).spec().side()
                        != battle.requireCombatant(Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID).spec().side())
                && battle.visibleContacts(Stage22CorePairTacticalFactory.UNION_ENTITY_ID).stream()
                .allMatch(value -> battle.requireCombatant(value.track().targetId()).spec().side()
                        != battle.requireCombatant(Stage22CorePairTacticalFactory.UNION_ENTITY_ID).spec().side());

        return new ScenarioResult(
                exactCommandFits,
                paidShieldTradeoff,
                actorBounded,
                empireContacts,
                unionContacts,
                empireControl.intent().targetSelected(),
                unionControl.intent().targetSelected(),
                empireControl.intent().fireRequested(),
                unionControl.intent().fireRequested(),
                empireControl.fireAuthorized(),
                unionControl.fireAuthorized(),
                empireControl.survivalDecision().reason(),
                unionControl.survivalDecision().reason(),
                control.fingerprint());
    }

    private static boolean matchesFit(
            Stage22CorePairTacticalFactory.CommandNetworkSkirmish skirmish,
            long entityId,
            String expectedFitId) {
        DemonstratorFitDefinition expected = skirmish.content().engineering().findDemonstratorFit(expectedFitId);
        if (expected == null) {
            return false;
        }
        InstalledFit actual = skirmish.control().battleState().requireCombatant(entityId).engineering().fit;
        return actual.equals(InstalledFit.fromDemonstrator(expected));
    }

    private static boolean paidNetworkTradeoff(LiveTacticalBattleRuntimeState battle, long entityId) {
        var engineering = battle.requireCombatant(entityId).engineering();
        boolean datalinkOwnsPaidMount = engineering.fit.installedModules().stream()
                .anyMatch(value -> value.mountId().equals(NETWORK_MOUNT)
                        && value.moduleId().equals(Stage22CorePairCommandNetworkProjection.DATALINK_MODULE_ID));
        return datalinkOwnsPaidMount && engineering.instanceState.shieldStatesByMount().isEmpty();
    }

    private enum BreakMode {
        NONE,
        RECEIVER,
        SENDER
    }

    private record ScenarioResult(
            boolean exactCommandFits,
            boolean paidShieldTradeoff,
            boolean actorBoundedContacts,
            int empireContacts,
            int unionContacts,
            boolean empireTargetSelected,
            boolean unionTargetSelected,
            boolean empireFireRequested,
            boolean unionFireRequested,
            boolean empireFireAuthorized,
            boolean unionFireAuthorized,
            DecisionReason empireSurvivalReason,
            DecisionReason unionSurvivalReason,
            LiveTacticalBattleControlRuntime.BattleControlFingerprint fingerprint) { }
}
