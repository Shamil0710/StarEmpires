package com.spacesim.ship;

import com.spacesim.ship.LiveTacticalBattleScenario.CombatantSpec;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.ObservedThreatAssessmentService.ContactDisposition;
import com.spacesim.ship.ObservedThreatAssessmentService.ObservedContact;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
