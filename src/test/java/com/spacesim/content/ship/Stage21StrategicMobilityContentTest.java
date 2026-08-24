package com.spacesim.content.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.ship.ShipEngineeringRuntime;
import com.spacesim.ship.ShipEngineeringRuntime.JumpPlan;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipFittingValidator;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.Doctrine;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage21StrategicMobilityContentTest {
    @Test
    void stage19BaselineRemainsStableWhileEveryStage21DoctrineGetsOneValidPhysicalFtlTradeoff() {
        ShipEngineeringCatalog baseline = Stage175ICombatTestContentPack.loadDoctrines();
        ShipEngineeringCatalog strategic = Stage175ICombatTestContentPack.loadStage21StrategicDoctrines();
        ShipFittingValidator validator = new ShipFittingValidator(strategic);
        ShipEngineeringRuntime runtime = new ShipEngineeringRuntime(strategic);

        assertNull(baseline.findModule(Stage175ICombatTestContentPack.STAGE21_STRATEGIC_FTL_MODULE_ID));
        assertNotNull(strategic.findModule(Stage175ICombatTestContentPack.STAGE21_STRATEGIC_FTL_MODULE_ID));
        assertEquals(baseline.getDemonstratorFits().size() + 5, strategic.getDemonstratorFits().size());

        for (Doctrine doctrine : Stage175IFleetDoctrineCatalog.all()) {
            DemonstratorFitDefinition base = baseline.findDemonstratorFit(doctrine.fitId());
            DemonstratorFitDefinition variant = strategic.findDemonstratorFit(
                    Stage175ICombatTestContentPack.stage21StrategicFitId(doctrine.fitId()));
            assertNotNull(base);
            assertNotNull(variant);
            assertEquals(base.hullId(), variant.hullId());

            Map<String, String> baseAssignments = assignments(base);
            Map<String, String> strategicAssignments = assignments(variant);
            assertEquals("module.test_datalink_v1", baseAssignments.get("utility_datalink"));
            assertEquals(
                    Stage175ICombatTestContentPack.STAGE21_STRATEGIC_FTL_MODULE_ID,
                    strategicAssignments.get("utility_datalink"));

            TreeMap<String, String> expected = new TreeMap<>(baseAssignments);
            expected.put(
                    "utility_datalink",
                    Stage175ICombatTestContentPack.STAGE21_STRATEGIC_FTL_MODULE_ID);
            assertEquals(expected, strategicAssignments,
                    "strategic fit may differ only at the existing datalink utility mount");

            long ftlAssignments = variant.installedModules().stream()
                    .filter(value -> strategic.findModule(value.moduleId()).family() == ModuleFamily.FTL_JUMP)
                    .count();
            assertEquals(1L, ftlAssignments);

            InstalledFit installed = InstalledFit.fromDemonstrator(variant);
            var validation = validator.validate(
                    strategic.findHull(installed.hullId()),
                    installed,
                    doctrine.initialConsumables(),
                    DamageState.pristine());
            assertTrue(validation.isValid(),
                    () -> variant.id() + " must remain an ordinary physically valid fit: " + validation.issues());

            var initialized = runtime.initialize(
                    installed, doctrine.initialConsumables(), DamageState.pristine());
            JumpPlan plan = runtime.planJump(installed, initialized, DamageState.pristine());
            assertTrue(plan.allowed(),
                    () -> variant.id() + " must be strategically mobile: " + plan.failure());
            assertEquals("utility_datalink", plan.mountId());
            assertTrue(plan.translatedMassKg() < 32_000_000d);
        }
    }

    @Test
    void strategicFitCommitsFinitePhysicalJumpCosts() {
        ShipEngineeringCatalog catalog = Stage175ICombatTestContentPack.loadStage21StrategicDoctrines();
        Doctrine doctrine = Stage175IFleetDoctrineCatalog.all().get(0);
        InstalledFit fit = InstalledFit.fromDemonstrator(catalog.findDemonstratorFit(
                Stage175ICombatTestContentPack.stage21StrategicFitId(doctrine.fitId())));
        ShipEngineeringRuntime runtime = new ShipEngineeringRuntime(catalog);
        var state = runtime.initialize(fit, doctrine.initialConsumables(), DamageState.pristine());

        JumpPlan plan = runtime.planJump(fit, state, DamageState.pristine());

        assertTrue(plan.allowed(), () -> "strategic FTL plan rejected: " + plan.failure());
        assertTrue(plan.requiredEnergyJ() > 0d);
        assertTrue(plan.jumpHeatJ() > 0d);
        assertTrue(plan.cooldownSeconds() > 0d);

        var committed = runtime.commitJump(state, plan);
        assertEquals(
                state.sharedBusEnergyJ() - plan.storedEnergyDrawJ(),
                committed.sharedBusEnergyJ(),
                1e-6d);
        assertEquals(
                state.localHeatJByMount().getOrDefault(plan.mountId(), 0d) + plan.jumpHeatJ(),
                committed.localHeatJByMount().get(plan.mountId()),
                1e-6d);
        assertEquals(plan.cooldownSeconds(), committed.ftlCooldownSecondsByMount().get(plan.mountId()), 1e-9d);
        assertFalse(committed.equals(state));
    }

    private static Map<String, String> assignments(DemonstratorFitDefinition fit) {
        TreeMap<String, String> result = new TreeMap<>();
        for (InstalledModuleDefinition module : fit.installedModules()) {
            result.put(module.mountId(), module.moduleId());
        }
        return result;
    }
}
