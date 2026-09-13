package com.spacesim.world;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.Stage22CorePairExperimentProtocol;
import com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage22CorePairStrategicMobilityProjection;
import com.spacesim.ship.ShipEngineeringRuntime;
import com.spacesim.ship.ShipEngineeringRuntime.JumpFailure;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipInstanceRuntimeState;
import com.spacesim.ship.Stage22CorePairTacticalFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M22.6 integration proof that core base/strategic fits resolve through the ordinary production FTL authority. */
class ProductionFittedJumpResolverStage22CorePairAcceptanceTest {
    @Test
    void exactCoreDestroyersRemainNoFtlUntilThePaidSlotStrategicVariantIsSelected() {
        var duel = Stage22CorePairTacticalFactory.createDestroyerDuel(
                Stage22CorePairExperimentProtocol.Permutation.DEFAULT);
        var resolver = new ProductionFittedJumpResolver();

        verifyBaseAndStrategic(
                duel,
                resolver,
                Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID,
                Stage22CorePairStrategicMobilityProjection.EMPIRE_DESTROYER_STRATEGIC_FIT);
        verifyBaseAndStrategic(
                duel,
                resolver,
                Stage22CorePairTacticalFactory.UNION_ENTITY_ID,
                Stage22CorePairStrategicMobilityProjection.UNION_DESTROYER_STRATEGIC_FIT);
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

    private static void verifyBaseAndStrategic(
            Stage22CorePairTacticalFactory.Duel duel,
            ProductionFittedJumpResolver resolver,
            long entityId,
            String strategicFitId) {
        EngineeringComponent base = duel.weapons().battleState().combatants().stream()
                .filter(actor -> actor.spec().entityId() == entityId)
                .findFirst()
                .orElseThrow()
                .engineering();
        var basePlan = resolver.plan(copy(base));
        assertFalse(basePlan.allowed(),
                "M22.6 must not invent strategic lift for a core destroyer that carries no FTL module");
        assertEquals(JumpFailure.NO_FTL_MODULE, basePlan.failure());

        var catalog = duel.content().engineering();
        InstalledFit strategicFit = InstalledFit.fromDemonstrator(
                catalog.findDemonstratorFit(strategicFitId));
        assertTrue(strategicFit.installedModules().stream().anyMatch(value ->
                        value.mountId().equals("utility_defense")
                                && value.moduleId().equals(Stage22CorePairStrategicMobilityProjection.FTL_MODULE_ID)),
                "strategic mobility must occupy the authored defensive utility slot");
        assertFalse(strategicFit.installedModules().stream().anyMatch(value ->
                        value.mountId().equals("utility_defense")
                                && catalog.findModule(value.moduleId()).family()
                                == com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily.SHIELD_FIELD),
                "strategic fit must pay for lift by giving up its defensive emitter");

        ShipEngineeringRuntime runtime = new ShipEngineeringRuntime(catalog);
        var operating = runtime.initialize(
                strategicFit,
                base.runtimeState.consumables(),
                DamageState.pristine());
        EngineeringComponent strategic = new EngineeringComponent(
                strategicFit,
                operating,
                ShipInstanceRuntimeState.legacyNeutral());
        var plan = resolver.plan(strategic);
        assertTrue(plan.allowed(), "paid-slot strategic variant must use the ordinary production FTL resolver: " + plan);
        assertEquals(Stage22CorePairStrategicMobilityProjection.FTL_MODULE_ID,
                strategicFit.installedModules().stream()
                        .filter(value -> value.mountId().equals(plan.mountId()))
                        .findFirst().orElseThrow().moduleId());
        assertTrue(plan.translatedMassKg() > 0d);
        assertTrue(plan.requiredEnergyJ() > 0d);
        assertTrue(plan.spoolSeconds() > 0d);
        assertTrue(plan.cooldownSeconds() > 0d);

        var committed = resolver.commit(strategic, plan);
        assertEquals(plan.cooldownSeconds(), committed.ftlCooldownSecondsByMount().get(plan.mountId()));
        assertTrue(committed.localHeatJByMount().get(plan.mountId()) >= plan.jumpHeatJ());
        EngineeringComponent cooling = new EngineeringComponent(
                strategicFit,
                committed,
                strategic.instanceState);
        assertEquals(JumpFailure.COOLDOWN_ACTIVE, resolver.plan(cooling).failure(),
                "ordinary resolver must expose the physical post-jump cooldown rather than permitting free tempo");
    }

    private static EngineeringComponent copy(EngineeringComponent source) {
        return new EngineeringComponent(source.fit, source.runtimeState, source.instanceState);
    }
}
