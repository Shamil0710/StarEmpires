package com.spacesim.ship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Stage19ScaledLiveTacticalFactoryScenarioTest {
    private static final long DAMAGED_ENTITY_ID = 191_304L;
    private static final long DEPLETED_ENTITY_ID = 191_400L;

    @Test
    void damagedDepletedFactoryReusesTheAcceptedPhysicalInitialState() {
        LiveTacticalBattleDeceptionRuntime runtime = Stage19ScaledLiveTacticalFactory.createDamagedDepleted8v8();
        LiveTacticalBattleRuntimeState battle = runtime.battleState();

        assertEquals(
                0.10d,
                battle.requireCombatant(DAMAGED_ENTITY_ID)
                        .engineering().instanceState.damage().moduleDamage()
                        .moduleIntegrityByMount().get("utility_datalink"),
                0d);
        assertEquals(
                0d,
                battle.requireCombatant(DEPLETED_ENTITY_ID)
                        .engineering().runtimeState.consumables().reactionMassKg(),
                0d);
    }

    @Test
    void damagedDepletedFactoryReplaysDeterministically() {
        LiveTacticalBattleDeceptionRuntime first = Stage19ScaledLiveTacticalFactory.createDamagedDepleted8v8();
        LiveTacticalBattleDeceptionRuntime second = Stage19ScaledLiveTacticalFactory.createDamagedDepleted8v8();

        for (int tick = 0; tick < 80; tick++) {
            first.advanceOneTick();
            second.advanceOneTick();
        }

        assertEquals(first.fingerprint(), second.fingerprint());
    }
}
