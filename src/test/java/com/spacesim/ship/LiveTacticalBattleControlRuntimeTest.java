package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.ship.LiveTacticalBattleScenario.CombatantSpec;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTacticalBattleControlRuntimeTest {
    private static final long NETWORK_RECEIVER_ID = 191_971L;

    @Test
    void balanced4v4UsesOneSharedActorBoundedControlAndPhysicalFlightTick() {
        LiveTacticalBattleRuntimeState battle =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.balanced4v4());
        LiveTacticalBattleControlRuntime runtime = new LiveTacticalBattleControlRuntime(battle);
        Map<Long, Double> initialReactionMass = reactionMassByEntity(battle);

        for (int index = 0; index < 120; index++) {
            runtime.advanceOneTick();
        }

        assertEquals(120L, runtime.tick());
        for (var combatant : battle.combatants()) {
            long entityId = combatant.spec().entityId();
            var contacts = battle.visibleContacts(entityId);
            var control = runtime.controlState(entityId);

            assertTrue(!contacts.isEmpty(),
                    "every balanced-control combatant should acquire at least one production hostile track");
            assertTrue(contacts.stream().allMatch(contact ->
                            battle.requireCombatant(contact.track().targetId()).spec().side()
                                    != combatant.spec().side()),
                    "actor-local information must not promote friendly/self contacts into hostile targets");
            assertTrue(control.intent().targetSelected(),
                    "production tactical policy should select from the actor-visible hostile domain");
            assertTrue(contacts.stream().anyMatch(contact ->
                            contact.track().targetId() == control.intent().targetId()),
                    "selected target must be present in the actor-visible TrackState domain");

            double displacement = Math.hypot(
                    combatant.transform().position.x - combatant.spec().xM(),
                    combatant.transform().position.y - combatant.spec().yM());
            assertTrue(displacement > 0d || combatant.transform().velocity.len2() > 0f,
                    "AI intent must reach the shared production flight integrator");
            assertTrue(reactionMassKg(combatant) < initialReactionMass.get(entityId),
                    "physical maneuver must consume that combatant's finite reaction mass");
        }
    }

    @Test
    void damagedLocalSensorCanUseOnlyPhysicalAlliedDatalinkAndLosesTargetWhenReceiverLinkIsDestroyed() {
        LiveTacticalBattleRuntimeState linkedBattle = new LiveTacticalBattleRuntimeState(commandNetworkScenario());
        disableMounts(linkedBattle, NETWORK_RECEIVER_ID, Set.of("utility_sensor"));
        LiveTacticalBattleControlRuntime linked = new LiveTacticalBattleControlRuntime(linkedBattle);

        linked.advanceOneTick();

        var linkedContacts = linkedBattle.visibleContacts(NETWORK_RECEIVER_ID);
        var linkedControl = linked.controlState(NETWORK_RECEIVER_ID);
        assertFalse(linkedContacts.isEmpty(),
                "sensor-blind receiver should acquire an allied measurement while both fitted datalink endpoints survive");
        assertTrue(linkedContacts.stream().allMatch(contact ->
                        linkedBattle.requireCombatant(contact.track().targetId()).spec().side() == Side.BETA),
                "allied datalink may relay measurements but must not manufacture friendly or hidden truth contacts");
        assertTrue(linkedControl.intent().targetSelected(),
                "shared bounded measurement should be usable by the ordinary production tactical planner");
        assertTrue(linkedContacts.stream().anyMatch(contact ->
                        contact.track().targetId() == linkedControl.intent().targetId()),
                "production control may only select a target materialized in the receiver-visible track domain");

        LiveTacticalBattleRuntimeState severedBattle = new LiveTacticalBattleRuntimeState(commandNetworkScenario());
        disableMounts(severedBattle, NETWORK_RECEIVER_ID, Set.of("utility_sensor", "utility_datalink"));
        LiveTacticalBattleControlRuntime severed = new LiveTacticalBattleControlRuntime(severedBattle);

        severed.advanceOneTick();

        assertTrue(severedBattle.visibleContacts(NETWORK_RECEIVER_ID).isEmpty(),
                "destroyed local sensor plus destroyed fitted receiver datalink must leave no hostile measurement authority");
        assertFalse(severed.controlState(NETWORK_RECEIVER_ID).intent().targetSelected(),
                "command planner must not recover a target from hidden battle truth after the network endpoint is lost");
        assertFalse(severed.controlState(NETWORK_RECEIVER_ID).fireAuthorized(),
                "no actor-visible target means no fire authorization after command-network severance");
    }

    @Test
    void same4v4FixedTicksProduceIdenticalWholeBattleControlFingerprint() {
        LiveTacticalBattleControlRuntime first = new LiveTacticalBattleControlRuntime(
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.balanced4v4()));
        LiveTacticalBattleControlRuntime second = new LiveTacticalBattleControlRuntime(
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.balanced4v4()));

        for (int index = 0; index < 240; index++) {
            first.advanceOneTick();
            second.advanceOneTick();
        }

        assertEquals(first.fingerprint(), second.fingerprint(),
                "same scenario and fixed-tick schedule must preserve deterministic 4v4 control/flight state");
    }

    @Test
    void readOnlyControlQueriesDoNotAdvanceBattleTimeOrPhysicalState() {
        LiveTacticalBattleControlRuntime runtime = new LiveTacticalBattleControlRuntime(
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.balanced4v4()));
        var before = runtime.fingerprint();

        runtime.battleState();
        for (var combatant : runtime.battleState().combatants()) {
            runtime.controlState(combatant.spec().entityId());
            runtime.battleState().visibleContacts(combatant.spec().entityId());
        }

        assertEquals(0L, runtime.tick());
        assertEquals(before, runtime.fingerprint());
    }

    private static LiveTacticalBattleScenario commandNetworkScenario() {
        return new LiveTacticalBattleScenario(List.of(
                new CombatantSpec(NETWORK_RECEIVER_ID, Side.ALPHA, DoctrineId.B_MISSILE_STRIKE, 260d, 640d),
                new CombatantSpec(191_972L, Side.ALPHA, DoctrineId.B_MISSILE_STRIKE, 260d, 760d),
                new CombatantSpec(191_981L, Side.BETA, DoctrineId.B_MISSILE_STRIKE, 1_690d, 640d),
                new CombatantSpec(191_982L, Side.BETA, DoctrineId.B_MISSILE_STRIKE, 1_690d, 760d)));
    }

    private static void disableMounts(
            LiveTacticalBattleRuntimeState battle,
            long entityId,
            Set<String> mountIds) {
        var combatant = battle.requireCombatant(entityId);
        var component = combatant.engineering();
        var before = component.instanceState;
        TreeMap<String, Double> integrity = new TreeMap<>(before.damage().moduleDamage().moduleIntegrityByMount());
        for (String mountId : mountIds) {
            assertTrue(component.fit.installedModules().stream().anyMatch(row -> row.mountId().equals(mountId)),
                    "network-degradation fixture must damage an actually installed mount: " + mountId);
            integrity.put(mountId, 0d);
        }
        var damage = new ShipDamageRuntime.Snapshot(
                before.damage().compartmentIntegrityById(),
                new ShipEngineeringState.DamageState(integrity));
        component.setRuntimeState(new ShipEngineeringRuntime(battle.engineeringCatalog()).initialize(
                component.fit,
                component.runtimeState.consumables(),
                damage.moduleDamage()));
        component.setInstanceState(new ShipInstanceRuntimeState(
                damage,
                before.shieldStatesByMount(),
                before.maintenance(),
                before.weaponLoadout(),
                before.weaponMountRuntime()));
    }

    private static Map<Long, Double> reactionMassByEntity(LiveTacticalBattleRuntimeState battle) {
        TreeMap<Long, Double> values = new TreeMap<>();
        for (var combatant : battle.combatants()) {
            values.put(combatant.spec().entityId(), reactionMassKg(combatant));
        }
        return Map.copyOf(values);
    }

    private static double reactionMassKg(LiveTacticalBattleRuntimeState.CombatantRuntime combatant) {
        return combatant.engineering().runtimeState.consumables().interfaceLoads().stream()
                .filter(value -> value.kind() == InterfaceKind.REACTION_MASS)
                .mapToDouble(ShipEngineeringState.ConsumableLoad::massKg)
                .sum();
    }
}
