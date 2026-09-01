package com.spacesim.ship;

import com.spacesim.components.InventoryComponent;
import com.spacesim.content.Stage22EmpirePackageLoader;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipyardIndustrialCatalog;
import com.spacesim.content.ship.Stage22EmpireEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage22EmpireShipyardIndustrialCatalogLoader;
import com.spacesim.persistence.EntityId;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipyardEconomyBridge.PhysicalInputBinding;
import com.spacesim.ship.ShipyardEngineeringService.ShipyardCapability;
import com.spacesim.ship.ShipyardEngineeringService.WorkPlan;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M22.3 integration proof that Imperial authored content uses the accepted shared shipyard runtime. */
class Stage22EmpireShipyardRuntimeAcceptanceTest {
    @Test
    void corvetteBuildConsumesOrdinaryInventoryAndCompletesOnlyAfterPhysicalSettlement() {
        Fixture fixture = fixture();
        WorkPlan plan = fixture.service().planBuild(fixture.primary(), fixture.yard());

        assertTrue(plan.feasibility().feasible(), () -> plan.feasibility().issues().toString());
        assertFalse(plan.requirements().inputs().isEmpty());
        assertTrue(plan.requirements().totalWorkSeconds() > 0d);

        EntityId asset = new EntityId(22_300_001L);
        assertThrows(IllegalStateException.class,
                () -> fixture.service().completeBuild(asset, plan, ShipyardEngineeringService.WorkSettlement.empty()));

        InventoryComponent inventory = new InventoryComponent();
        TreeMap<String, Integer> runtimeIds = new TreeMap<>();
        int nextRuntimeId = 0;
        for (var input : plan.requirements().inputs()) {
            int runtimeId = nextRuntimeId++;
            runtimeIds.put(input.contentId(), runtimeId);
            long rounded = (long) Math.ceil(input.amount());
            assertTrue(rounded > 0L && rounded <= Integer.MAX_VALUE, input.contentId());
            inventory.stock[runtimeId] = (int) rounded;
        }
        var settlement = ShipyardEconomyBridge.consumeRequiredInputs(
                plan,
                inventory,
                new PhysicalInputBinding(runtimeIds),
                plan.requirements().totalWorkSeconds());
        assertTrue(runtimeIds.values().stream().allMatch(id -> inventory.stock[id] == 0));

        var completion = fixture.service().completeBuild(asset, plan, settlement);
        assertEquals(asset, completion.assetId());
        assertEquals(fixture.primary(), completion.fit());
    }

    @Test
    void authoredAlternateFitUsesSharedRefitPathAndPreservesPersistentIdentity() {
        Fixture fixture = fixture();
        EntityId asset = new EntityId(22_300_002L);
        ShipDamageRuntime.Snapshot pristine = pristineDamage(fixture.engineering(), fixture.primary());

        WorkPlan plan = fixture.service().planRefit(
                asset,
                fixture.primary(),
                fixture.refit(),
                ConsumableState.empty(),
                pristine,
                fixture.yard());

        assertTrue(plan.feasibility().feasible(), () -> plan.feasibility().issues().toString());
        assertEquals(fixture.primary().hullId(), fixture.refit().hullId());
        assertTrue(plan.requirements().totalWorkSeconds() > 0d);
        assertFalse(plan.affectedMounts().isEmpty());

        Map<String, Double> delivered = new LinkedHashMap<>();
        plan.requirements().inputs().forEach(input -> delivered.put(input.contentId(), input.amount()));
        var completion = fixture.service().completeRefit(
                plan,
                new ShipyardEngineeringService.WorkSettlement(delivered, plan.requirements().totalWorkSeconds()));

        assertEquals(asset, completion.assetId());
        assertEquals(fixture.refit(), completion.fit());
    }

    private static Fixture fixture() {
        ShipEngineeringCatalog engineering = Stage22EmpireEngineeringCatalogLoader.loadDefault();
        ShipyardIndustrialCatalog industrial = Stage22EmpireShipyardIndustrialCatalogLoader.loadDefault();
        ShipyardEngineeringService service = new ShipyardEngineeringService(engineering, industrial);
        var family = Stage22EmpirePackageLoader.loadDefault().findShipForRole("role.military.corvette");
        InstalledFit primary = InstalledFit.fromDemonstrator(engineering.findDemonstratorFit(family.primaryFitId()));
        InstalledFit refit = InstalledFit.fromDemonstrator(engineering.findDemonstratorFit(family.refitFitId()));
        return new Fixture(engineering, service, primary, refit, capability(engineering, industrial, primary));
    }

    private static ShipyardCapability capability(
            ShipEngineeringCatalog engineering,
            ShipyardIndustrialCatalog industrial,
            InstalledFit fit) {
        var hull = engineering.findHull(fit.hullId());
        TreeSet<String> fabrication = new TreeSet<>();
        TreeSet<String> tooling = new TreeSet<>();
        TreeSet<String> handledInputs = new TreeSet<>();
        double precision = 0d;
        double power = 0d;
        int labor = 0;
        int automation = 0;

        var hullProfile = industrial.findHullProfile(hull.id());
        fabrication.addAll(hullProfile.fabricationCapabilities());
        tooling.addAll(hullProfile.toolingTags());
        hullProfile.constructionInputs().forEach(input -> handledInputs.add(input.contentId()));
        precision = Math.max(precision, hullProfile.precisionRequirement());
        power = Math.max(power, hullProfile.industrialPowerW());
        labor = Math.max(labor, hullProfile.laborRequirement());
        automation = Math.max(automation, hullProfile.automationRequirement());

        Set<String> moduleIds = new TreeSet<>();
        moduleIds.addAll(fit.installedModules().stream().map(InstalledModuleDefinition::moduleId).toList());
        var packageCatalog = Stage22EmpirePackageLoader.loadDefault();
        var refit = engineering.findDemonstratorFit(
                packageCatalog.findShipForRole("role.military.corvette").refitFitId());
        moduleIds.addAll(refit.installedModules().stream().map(InstalledModuleDefinition::moduleId).toList());
        for (String moduleId : moduleIds) {
            var module = engineering.findModule(moduleId);
            var profile = industrial.findModuleProfile(moduleId);
            fabrication.addAll(profile.fabricationCapabilities());
            tooling.addAll(profile.toolingTags());
            module.constructionInputs().forEach(input -> handledInputs.add(input.contentId()));
            precision = Math.max(precision, profile.precisionRequirement());
            power = Math.max(power, profile.industrialPowerW());
            labor = Math.max(labor, profile.laborRequirement());
            automation = Math.max(automation, profile.automationRequirement());
        }

        Dimensions3d dimensions = hull.boundingDimensionsM();
        return new ShipyardCapability(
                "yard.empire_capital_service_v1",
                dimensions,
                hull.maxOperationalMassKg(),
                fabrication,
                handledInputs,
                tooling,
                precision,
                8d,
                labor,
                automation,
                power);
    }

    private static ShipDamageRuntime.Snapshot pristineDamage(ShipEngineeringCatalog engineering, InstalledFit fit) {
        Map<String, Double> compartments = new TreeMap<>();
        engineering.findHull(fit.hullId()).compartments().forEach(compartment -> compartments.put(compartment.id(), 1d));
        return new ShipDamageRuntime.Snapshot(compartments, DamageState.pristine());
    }

    private record Fixture(
            ShipEngineeringCatalog engineering,
            ShipyardEngineeringService service,
            InstalledFit primary,
            InstalledFit refit,
            ShipyardCapability yard) { }
}
