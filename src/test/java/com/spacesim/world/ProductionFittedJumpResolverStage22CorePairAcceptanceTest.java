package com.spacesim.world;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.Stage22CorePairExperimentProtocol;
import com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader;
import com.spacesim.ship.ShipEngineeringRuntime.JumpFailure;
import com.spacesim.ship.ShipEngineeringRuntime.JumpPlan;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.Stage22CorePairTacticalFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M22.6 integration proof that exact core fits use the ordinary production FTL resolver. */
class ProductionFittedJumpResolverStage22CorePairAcceptanceTest {
    private static final int MAX_RECOVERY_STEPS = 10_000;
    private static final double RECOVERY_STEP_SECONDS = 0.25d;

    @Test
    void empireAndUnionExactFitsPlanAndCommitThroughOrdinaryProductionResolver() {
        var duel = Stage22CorePairTacticalFactory.createDestroyerDuel(
                Stage22CorePairExperimentProtocol.Permutation.DEFAULT);
        var resolver = new ProductionFittedJumpResolver();

        for (long entityId : new long[] {
                Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID,
                Stage22CorePairTacticalFactory.UNION_ENTITY_ID}) {
            EngineeringComponent source = duel.weapons().battleState().combatants().stream()
                    .filter(actor -> actor.spec().entityId() == entityId)
                    .findFirst()
                    .orElseThrow()
                    .engineering();
            EngineeringComponent component = copy(source);
            JumpPlan plan = awaitAllowed(resolver, component);

            var before = component.runtimeState;
            var committed = resolver.commit(component, plan);
            assertNotEquals(before, committed,
                    "ordinary FTL commit must change exact core physical operating state");
            component.setRuntimeState(committed);
            assertTrue(component.runtimeState.ftlCooldownSecondsByMount().values().stream()
                            .anyMatch(value -> value > 0d),
                    "ordinary FTL commit must leave the fitted exact-core drive in cooldown");
            assertEquals(JumpFailure.COOLDOWN_ACTIVE, resolver.plan(component).failure(),
                    "the next ordinary jump must observe the same fitted cooldown authority");
        }
    }

    @Test
    void unknownAndAmbiguousFitsStillFailClosed() {
        var duel = Stage22CorePairTacticalFactory.createDestroyerDuel(
                Stage22CorePairExperimentProtocol.Permutation.DEFAULT);
        EngineeringComponent source = duel.weapons().battleState().combatants().get(0).engineering();
        EngineeringComponent unknown = new EngineeringComponent(
                new InstalledFit("hull.stage22.unknown", List.of()),
                source.runtimeState,
                source.instanceState);
        assertThrows(IllegalArgumentException.class, () -> new ProductionFittedJumpResolver().plan(unknown));

        var core = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        var ambiguous = new ProductionFittedJumpResolver(List.of(core, core));
        assertThrows(IllegalArgumentException.class, () -> ambiguous.plan(copy(source)));
    }

    private static JumpPlan awaitAllowed(
            ProductionFittedJumpResolver resolver,
            EngineeringComponent component) {
        for (int attempt = 0; attempt < MAX_RECOVERY_STEPS; attempt++) {
            JumpPlan plan = resolver.plan(component);
            if (plan.allowed()) {
                return plan;
            }
            if (plan.failure() != JumpFailure.COOLDOWN_ACTIVE
                    && plan.failure() != JumpFailure.THERMAL_LIMIT
                    && plan.failure() != JumpFailure.CHARGE_POWER_UNAVAILABLE
                    && plan.failure() != JumpFailure.STORED_ENERGY_UNAVAILABLE) {
                throw new AssertionError("exact core fit has permanent ordinary FTL failure: " + plan.failure());
            }
            component.setRuntimeState(resolver.advanceIdle(component, RECOVERY_STEP_SECONDS));
        }
        throw new AssertionError("exact core fit did not recover to ordinary FTL spool boundary");
    }

    private static EngineeringComponent copy(EngineeringComponent source) {
        return new EngineeringComponent(source.fit, source.runtimeState, source.instanceState);
    }
}
