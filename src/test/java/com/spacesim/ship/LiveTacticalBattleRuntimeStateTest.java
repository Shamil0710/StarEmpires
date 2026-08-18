package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.ship.LiveTacticalBattleScenario.CombatantSpec;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.ObservedThreatAssessmentService.ContactDisposition;
import com.spacesim.ship.ObservedThreatAssessmentService.ObservedContact;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTacticalBattleRuntimeStateTest {
    @Test
    void scenarioMaterializesIndependentTransformsInCanonicalOrder() {
        LiveTacticalBattleScenario scenario = LiveTacticalBattleScenario.balanced4v4();
        LiveTacticalBattleRuntimeState state = new LiveTacticalBattleRuntimeState(scenario);

        assertEquals(8, state.combatants().size());
        assertEquals(
                scenario.combatants().stream().map(CombatantSpec::entityId).toList(),
                state.combatants().stream().map(value -> value.spec().entityId()).toList());

        CombatantSpec firstSpec = scenario.combatants().get(0);
        var first = state.requireCombatant(firstSpec.entityId());
        assertEquals(firstSpec.xM(), first.transform().position.x, 1e-6d);
        assertEquals(firstSpec.yM(), first.transform().position.y, 1e-6d);

        long secondId = scenario.combatants().get(1).entityId();
        var second = state.requireCombatant(secondId);
        float secondXBefore = second.transform().position.x;
        first.transform().position.x += 25f;
        first.transform().velocity.x = 10f;

        assertEquals(secondXBefore, second.transform().position.x, 0f,
                "each materialized combatant must own an independent physical transform");
        assertEquals(0f, second.transform().velocity.x, 0f);
    }

    @Test
    void balanced4v4MaterializesProductionFitDamageShieldsAndFiniteStoresPerCombatant() {
        LiveTacticalBattleScenario scenario = LiveTacticalBattleScenario.balanced4v4();
        LiveTacticalBattleRuntimeState state = new LiveTacticalBattleRuntimeState(scenario);

        for (var combatant : state.combatants()) {
            var engineering = combatant.engineering();
            var instance = engineering.instanceState;

            assertEquals(combatant.spec().doctrineId(), combatant.doctrine().id());
            assertEquals(combatant.hull().id(), engineering.fit.hullId());
            assertEquals(combatant.hull().id(), combatant.damageLayout().hullId());
            assertEquals(combatant.doctrine().initialConsumables(), engineering.runtimeState.consumables(),
                    "scenario doctrine must materialize physical production stores without hidden grants");
            assertEquals(combatant.doctrine().weaponLoadout(), instance.weaponLoadout());
            assertTrue(instance.damage().compartmentIntegrityById().values().stream()
                    .allMatch(value -> value == 1d));
            assertTrue(reactionMassKg(engineering.runtimeState.consumables()) > 0d,
                    "each combatant must own finite physical reaction mass");
            assertTrue(ammunitionRounds(engineering.runtimeState.consumables()) > 0L,
                    "balanced-control combatants must own finite physical ammunition");
            assertFalse(instance.shieldStatesByMount().isEmpty(),
                    "fitted production shields must materialize into authoritative instance state");
            assertTrue(instance.shieldStatesByMount().values().stream()
                    .allMatch(value -> value.reserveJ() > 0d && !value.collapsed()),
                    "new combatants must begin with their fitted shield state physically charged");
        }
    }

    @Test
    void productionRuntimeObjectsAreIndependentBetweenCombatants() {
        LiveTacticalBattleRuntimeState state =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.balanced4v4());
        var first = state.combatants().get(0);
        var second = state.combatants().get(1);

        assertNotSame(first.engineering(), second.engineering());
        assertNotSame(first.engineering().runtimeState, second.engineering().runtimeState);
        assertNotSame(first.engineering().instanceState, second.engineering().instanceState);
        assertNotSame(
                first.engineering().instanceState.damage(),
                second.engineering().instanceState.damage(),
                "damage continuity must be independently replaceable for every physical combatant");
    }

    @Test
    void actorVisibleContactsDoNotLeakBetweenCombatants() {
        LiveTacticalBattleScenario scenario = LiveTacticalBattleScenario.balanced4v4();
        LiveTacticalBattleRuntimeState state = new LiveTacticalBattleRuntimeState(scenario);
        long observerA = scenario.combatantsFor(Side.ALPHA).get(0).entityId();
        long observerB = scenario.combatantsFor(Side.ALPHA).get(1).entityId();
        long target = scenario.combatantsFor(Side.BETA).get(0).entityId();
        ObservedContact contact = hostileTrack(target, 1_690d, 520d);

        state.replaceVisibleContacts(observerA, List.of(contact));

        assertEquals(List.of(contact), state.visibleContacts(observerA));
        assertTrue(state.visibleContacts(observerB).isEmpty(),
                "one combatant's observed target must not become omniscient shared fleet knowledge");
    }

    @Test
    void visibleContactsAreCanonicalAndRejectDuplicatesOrSelfTracks() {
        LiveTacticalBattleScenario scenario = LiveTacticalBattleScenario.balanced4v4();
        LiveTacticalBattleRuntimeState state = new LiveTacticalBattleRuntimeState(scenario);
        long observer = scenario.combatantsFor(Side.ALPHA).get(0).entityId();
        long targetA = scenario.combatantsFor(Side.BETA).get(0).entityId();
        long targetB = scenario.combatantsFor(Side.BETA).get(1).entityId();
        ObservedContact higherId = hostileTrack(targetB, 1_690d, 640d);
        ObservedContact lowerId = hostileTrack(targetA, 1_690d, 520d);

        state.replaceVisibleContacts(observer, List.of(higherId, lowerId));

        assertEquals(List.of(targetA, targetB), state.visibleContacts(observer).stream()
                .map(value -> value.track().targetId())
                .toList());
        assertThrows(IllegalArgumentException.class,
                () -> state.replaceVisibleContacts(observer, List.of(lowerId, lowerId)));
        assertThrows(IllegalArgumentException.class,
                () -> state.replaceVisibleContacts(observer, List.of(hostileTrack(observer, 260d, 520d))));
    }

    @Test
    void unknownCombatantCannotReadOrWriteActorInformationDomain() {
        LiveTacticalBattleRuntimeState state =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.balanced4v4());

        assertThrows(IllegalArgumentException.class, () -> state.requireCombatant(999_999L));
        assertThrows(IllegalArgumentException.class, () -> state.visibleContacts(999_999L));
        assertThrows(IllegalArgumentException.class,
                () -> state.replaceVisibleContacts(999_999L, List.of()));
    }

    private static double reactionMassKg(ShipEngineeringState.ConsumableState state) {
        return state.interfaceLoads().stream()
                .filter(value -> value.kind() == InterfaceKind.REACTION_MASS)
                .mapToDouble(ShipEngineeringState.ConsumableLoad::massKg)
                .sum();
    }

    private static long ammunitionRounds(ShipEngineeringState.ConsumableState state) {
        return state.interfaceLoads().stream()
                .filter(value -> value.kind() == InterfaceKind.AMMUNITION)
                .mapToLong(ShipEngineeringState.ConsumableLoad::itemCount)
                .sum();
    }

    private static ObservedContact hostileTrack(long targetId, double xM, double yM) {
        return new ObservedContact(
                new TrackState(
                        targetId,
                        TrackState.InformationState.TRACKED,
                        true,
                        xM,
                        yM,
                        new TrackCovariance(100d, 0.01d, 100d),
                        1d,
                        0d,
                        1,
                        1),
                ContactDisposition.HOSTILE);
    }
}
