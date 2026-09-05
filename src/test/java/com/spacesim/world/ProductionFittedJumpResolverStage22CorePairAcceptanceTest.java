package com.spacesim.world;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.Stage22CorePairExperimentProtocol;
import com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader;
import com.spacesim.ship.ShipEngineeringRuntime.JumpFailure;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.Stage22CorePairTacticalFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** M22.6 integration proof that exact core fits resolve through the ordinary production FTL authority. */
class ProductionFittedJumpResolverStage22CorePairAcceptanceTest {
    @Test
    void exactCoreDestroyersResolveNormallyButCannotJumpWithoutAnAuthoredFtlModule() {
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

            var plan = resolver.plan(copy(source));
            assertFalse(plan.allowed(),
                    "M22.6 must not invent strategic lift for a core destroyer that carries no FTL module");
            assertEquals(JumpFailure.NO_FTL_MODULE, plan.failure(),
                    "ordinary production resolver must recognize the exact core catalog and expose its physical no-FTL boundary");
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

    private static EngineeringComponent copy(EngineeringComponent source) {
        return new EngineeringComponent(source.fit, source.runtimeState, source.instanceState);
    }
}
