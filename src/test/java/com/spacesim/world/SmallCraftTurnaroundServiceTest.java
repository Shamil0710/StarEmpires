package com.spacesim.world;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipyardIndustrialCatalog;
import com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage22CorePairShipyardIndustrialCatalogLoader;
import com.spacesim.ship.ShipyardEngineeringService;
import com.spacesim.ship.ShipyardEngineeringService.ShipyardCapability;
import com.spacesim.ship.ShipyardEngineeringService.WorkPlan;
import com.spacesim.ship.ShipyardEngineeringService.WorkSettlement;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import com.spacesim.world.SmallCraftHangarCapacity.HostKind;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;
import com.spacesim.world.SmallCraftTurnaroundService.ConsumableTransfer;
import com.spacesim.world.SmallCraftTurnaroundService.ServiceProfile;
import com.spacesim.world.SmallCraftTurnaroundService.TurnaroundRequest;
import com.spacesim.world.SmallCraftTurnaroundService.TurnaroundSettlement;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmallCraftTurnaroundServiceTest {
    @Test
    void finiteFuelAndAmmoRequireExactDeliveryAndHandlingWorkBeforeReady() {
        Fixture fixture = fixture(1d, 100d);
        SmallCraftId id = fixture.registry().snapshot().get(0).id();
        ConsumableTransfer fuel = new ConsumableTransfer(
                "core_drive", "propellant_feed", InterfaceKind.REACTION_MASS,
                100d, 100d, 0L);
        ConsumableTransfer ammo = new ConsumableTransfer(
                "weapon_primary", "kinetic_feed", InterfaceKind.AMMUNITION,
                2d, 20d, 2L);
        ServiceProfile profile = new ServiceProfile(
                fixture.bay().id(),
                5d,
                Map.of(
                        InterfaceKind.REACTION_MASS, 50d,
                        InterfaceKind.AMMUNITION, 2d));
        var plan = fixture.service().plan(
                id,
                fixture.bay(),
                profile,
                new TurnaroundRequest(List.of(fuel, ammo), false, false),
                fixture.yard());

        assertEquals(8d, plan.requiredHandlingWorkSeconds(), 1e-9);
        assertThrows(IllegalStateException.class, () -> fixture.service().complete(
                plan,
                new TurnaroundSettlement(
                        List.of(fuel, ammo),
                        7.99d,
                        WorkSettlement.empty(),
                        WorkSettlement.empty()),
                fixture.bay()));
        assertEquals(OccupancyState.SERVICING,
                fixture.hangars().find(id).orElseThrow().state());

        SmallCraftState completed = fixture.service().complete(
                plan,
                new TurnaroundSettlement(
                        List.of(fuel, ammo),
                        8d,
                        WorkSettlement.empty(),
                        WorkSettlement.empty()),
                fixture.bay());

        assertEquals(OccupancyState.READY,
                fixture.hangars().find(id).orElseThrow().state());
        assertEquals(4_100d, completed.runtimeState().consumables().reactionMassKg(), 1e-9);
        assertEquals(100d, completed.runtimeState().consumables().ammunitionMassKg(), 1e-9);
        assertEquals(10L, completed.runtimeState().consumables().ammunitionCount());
    }

    @Test
    void authoredInterfaceCapacityRejectsImpossibleFreeOverfillAtPlanningBoundary() {
        Fixture fixture = fixture(1d, 100d);
        SmallCraftId id = fixture.registry().snapshot().get(0).id();
        ServiceProfile profile = new ServiceProfile(
                fixture.bay().id(),
                5d,
                Map.of(InterfaceKind.REACTION_MASS, 50d));

        assertThrows(IllegalArgumentException.class, () -> fixture.service().plan(
                id,
                fixture.bay(),
                profile,
                new TurnaroundRequest(
                        List.of(new ConsumableTransfer(
                                "core_drive",
                                "propellant_feed",
                                InterfaceKind.REACTION_MASS,
                                100_000_000d,
                                100_000_000d,
                                0L)),
                        false,
                        false),
                fixture.yard()));
    }

    @Test
    void repairAndMaintenanceReuseOrdinaryShipyardRequirementsAndSettlement() {
        Fixture fixture = fixture(0.5d, 1_000_000d);
        SmallCraftId id = fixture.registry().snapshot().get(0).id();
        ServiceProfile profile = new ServiceProfile(
                fixture.bay().id(),
                5d,
                Map.of());
        var plan = fixture.service().plan(
                id,
                fixture.bay(),
                profile,
                new TurnaroundRequest(List.of(), true, true),
                fixture.yard());

        assertTrue(plan.feasible());
        assertTrue(plan.repairPlan().requirements().totalWorkSeconds() > 0d);
        assertTrue(plan.repairPlan().requirements().inputs().stream()
                .anyMatch(value -> value.amount() > 0d));
        assertTrue(plan.maintenancePlan().requirements().totalWorkSeconds() > 0d);

        assertThrows(IllegalStateException.class, () -> fixture.service().complete(
                plan,
                new TurnaroundSettlement(
                        List.of(),
                        5d,
                        WorkSettlement.empty(),
                        WorkSettlement.empty()),
                fixture.bay()));

        SmallCraftState completed = fixture.service().complete(
                plan,
                new TurnaroundSettlement(
                        List.of(),
                        5d,
                        fullySettled(plan.repairPlan()),
                        fullySettled(plan.maintenancePlan())),
                fixture.bay());

        assertTrue(completed.instanceState().damage().moduleDamage().isPristine());
        assertEquals(0d,
                completed.instanceState().maintenance()
                        .secondsSinceServiceByMount().get("core_drive"),
                1e-9);
        assertEquals(OccupancyState.READY,
                fixture.hangars().find(id).orElseThrow().state());
    }

    @Test
    void addedConsumableMassMustStillFitCurrentDamagedBayCapacity() {
        Fixture fixture = fixture(1d, 100d);
        SmallCraftId id = fixture.registry().snapshot().get(0).id();
        double currentMass = fixture.registry().physicalFootprint(id).currentMassKg();
        BayDefinition constrained = new BayDefinition(
                fixture.bay().id(),
                fixture.bay().hostKind(),
                fixture.bay().singleCraftEnvelopeM(),
                fixture.bay().pristineUsableVolumeM3(),
                currentMass + 50d,
                1d);
        ServiceProfile profile = new ServiceProfile(
                constrained.id(),
                5d,
                Map.of(InterfaceKind.REACTION_MASS, 100d));
        ConsumableTransfer fuel = new ConsumableTransfer(
                "core_drive", "propellant_feed", InterfaceKind.REACTION_MASS,
                100d, 100d, 0L);
        var plan = fixture.service().plan(
                id,
                constrained,
                profile,
                new TurnaroundRequest(List.of(fuel), false, false),
                fixture.yard());

        assertThrows(IllegalStateException.class, () -> fixture.service().complete(
                plan,
                new TurnaroundSettlement(
                        List.of(fuel),
                        plan.requiredHandlingWorkSeconds(),
                        WorkSettlement.empty(),
                        WorkSettlement.empty()),
                constrained));
        assertEquals(4_000d,
                fixture.registry().find(id).orElseThrow()
                        .runtimeState().consumables().reactionMassKg(),
                1e-9);
        assertEquals(OccupancyState.SERVICING,
                fixture.hangars().find(id).orElseThrow().state());
    }

    private static Fixture fixture(double weaponIntegrity, double maintenanceAgeSeconds) {
        ShipEngineeringCatalog engineering = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        ShipyardIndustrialCatalog industrial =
                Stage22CorePairShipyardIndustrialCatalogLoader.loadEmpireDefault();
        ShipyardEngineeringService shipyard =
                new ShipyardEngineeringService(engineering, industrial);
        SmallCraftFitAuthority fitAuthority = new SmallCraftFitAuthority(engineering);
        SmallCraftRegistry registry = SmallCraftRegistry.empty(fitAuthority);
        SmallCraftId id = registry.reserveIdentityForCompletedProduction();
        registry.registerProducedCraft(ProductionSmallCraftFixture.craft(
                id, 8L, 80d, 4_000d, weaponIntegrity, maintenanceAgeSeconds));
        SmallCraftHangarRegistry hangars = SmallCraftHangarRegistry.empty(registry);
        var footprint = registry.physicalFootprint(id);
        BayDefinition bay = new BayDefinition(
                new BayId("carrier:turnaround-test", "mission_primary"),
                HostKind.SHIP,
                new Dimensions3d(1_000d, 1_000d, 1_000d),
                footprint.envelopeVolumeM3() * 4d,
                footprint.currentMassKg() * 4d,
                1d);
        hangars.assign(id, bay, OccupancyState.SERVICING);
        ShipyardCapability yard = capableYard(
                engineering,
                industrial,
                registry.find(id).orElseThrow().fit());
        return new Fixture(
                registry,
                hangars,
                bay,
                yard,
                new SmallCraftTurnaroundService(registry, hangars, engineering, shipyard));
    }

    private static ShipyardCapability capableYard(
            ShipEngineeringCatalog engineering,
            ShipyardIndustrialCatalog industrial,
            com.spacesim.ship.ShipEngineeringState.InstalledFit fit) {
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
        moduleIds.addAll(fit.installedModules().stream()
                .map(InstalledModuleDefinition::moduleId)
                .toList());
        for (String moduleId : moduleIds) {
            var module = engineering.findModule(moduleId);
            var moduleProfile = industrial.findModuleProfile(moduleId);
            fabrication.addAll(moduleProfile.fabricationCapabilities());
            tooling.addAll(moduleProfile.toolingTags());
            module.constructionInputs().forEach(input -> handledInputs.add(input.contentId()));
            precision = Math.max(precision, moduleProfile.precisionRequirement());
            power = Math.max(power, moduleProfile.industrialPowerW());
            labor = Math.max(labor, moduleProfile.laborRequirement());
            automation = Math.max(automation, moduleProfile.automationRequirement());
        }

        return new ShipyardCapability(
                "yard.smallcraft.turnaround",
                hull.boundingDimensionsM(),
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

    private static WorkSettlement fullySettled(WorkPlan plan) {
        Map<String, Double> inputs = new LinkedHashMap<>();
        plan.requirements().inputs().forEach(
                input -> inputs.put(input.contentId(), input.amount()));
        return new WorkSettlement(inputs, plan.requirements().totalWorkSeconds());
    }

    private record Fixture(
            SmallCraftRegistry registry,
            SmallCraftHangarRegistry hangars,
            BayDefinition bay,
            ShipyardCapability yard,
            SmallCraftTurnaroundService service) { }
}
