package com.spacesim.ship;

import com.spacesim.content.Stage22CorePairExperimentProtocol.Permutation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M22.6 acceptance proving both exact Stage-22 core packages execute through the shared Stage-19 stack. */
class Stage22CorePairExactTacticalRuntimeAcceptanceTest {
    private static final int TICKS = 800;

    @Test
    void defaultAndMirroredDestroyerPairsSenseMoveFireConsumeAndResolvePhysicalImpacts() {
        DuelEvidence defaultRun = exercise(Permutation.DEFAULT);
        DuelEvidence mirroredRun = exercise(Permutation.MIRRORED);

        assertTrue(defaultRun.empireShots() > 0L);
        assertTrue(defaultRun.unionShots() > 0L);
        assertTrue(defaultRun.totalImpacts() > 0L);
        assertTrue(mirroredRun.empireShots() > 0L);
        assertTrue(mirroredRun.unionShots() > 0L);
        assertTrue(mirroredRun.totalImpacts() > 0L);

        assertTrue(defaultRun.empireRoundsRemaining() < 120L);
        assertTrue(defaultRun.unionRoundsRemaining() < 120L);
        assertTrue(mirroredRun.empireRoundsRemaining() < 120L);
        assertTrue(mirroredRun.unionRoundsRemaining() < 120L);

        assertEquals(Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID, defaultRun.empireEntityId());
        assertEquals(Stage22CorePairTacticalFactory.UNION_ENTITY_ID, defaultRun.unionEntityId());
        assertEquals(defaultRun.empireEntityId(), mirroredRun.empireEntityId());
        assertEquals(defaultRun.unionEntityId(), mirroredRun.unionEntityId());
    }

    @Test
    void packageNeutralExactImportUsesStage22FitsRatherThanCompatibilityDoctrineStats() {
        Stage22CorePairTacticalFactory.Duel duel =
                Stage22CorePairTacticalFactory.createDestroyerDuel(Permutation.DEFAULT);
        LiveTacticalBattleRuntimeState battle = duel.weapons().battleState();
        var empire = battle.requireCombatant(Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID);
        var union = battle.requireCombatant(Stage22CorePairTacticalFactory.UNION_ENTITY_ID);

        assertEquals("hull.empire_destroyer_v1", empire.engineering().fit.hullId());
        assertEquals("hull.industrial_union_destroyer_v1", union.engineering().fit.hullId());
        assertNotEquals(empire.doctrine().fitId(), Stage22CorePairTacticalFactory.EMPIRE_DESTROYER_FIT);
        assertNotEquals(union.doctrine().fitId(), Stage22CorePairTacticalFactory.UNION_DESTROYER_FIT);
        assertEquals(duel.content().engineering(), battle.engineeringCatalog());
        assertEquals(duel.protection(), battle.protectionCatalog());
        assertFalse(battle.visibleContacts(Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID).iterator().hasNext());
        assertFalse(battle.visibleContacts(Stage22CorePairTacticalFactory.UNION_ENTITY_ID).iterator().hasNext());

        duel.weapons().advanceOneTick();
        assertTrue(battle.visibleContacts(Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID).stream()
                .allMatch(value -> value.track().targetId() == Stage22CorePairTacticalFactory.UNION_ENTITY_ID));
        assertTrue(battle.visibleContacts(Stage22CorePairTacticalFactory.UNION_ENTITY_ID).stream()
                .allMatch(value -> value.track().targetId() == Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID));
    }

    private static DuelEvidence exercise(Permutation permutation) {
        Stage22CorePairTacticalFactory.Duel duel = Stage22CorePairTacticalFactory.createDestroyerDuel(permutation);
        LiveTacticalBattleWeaponRuntime runtime = duel.weapons();
        for (int tick = 0; tick < TICKS; tick++) {
            runtime.advanceOneTick();
        }
        var fingerprint = runtime.fingerprint();
        var empireSource = fingerprint.sources().stream()
                .filter(value -> value.entityId() == Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID)
                .findFirst().orElseThrow();
        var unionSource = fingerprint.sources().stream()
                .filter(value -> value.entityId() == Stage22CorePairTacticalFactory.UNION_ENTITY_ID)
                .findFirst().orElseThrow();
        assertTrue(runtime.battleState().visibleContacts(Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID).stream()
                .allMatch(value -> value.track().targetId() == Stage22CorePairTacticalFactory.UNION_ENTITY_ID));
        assertTrue(runtime.battleState().visibleContacts(Stage22CorePairTacticalFactory.UNION_ENTITY_ID).stream()
                .allMatch(value -> value.track().targetId() == Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID));
        return new DuelEvidence(
                Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID,
                Stage22CorePairTacticalFactory.UNION_ENTITY_ID,
                empireSource.shotsFired(),
                unionSource.shotsFired(),
                empireSource.ammunitionRounds(),
                unionSource.ammunitionRounds(),
                runtime.totalImpacts());
    }

    private record DuelEvidence(
            long empireEntityId,
            long unionEntityId,
            long empireShots,
            long unionShots,
            long empireRoundsRemaining,
            long unionRoundsRemaining,
            long totalImpacts) { }
}
