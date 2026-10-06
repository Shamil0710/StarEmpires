package com.spacesim.content.ship;

import com.spacesim.content.*;

import com.spacesim.content.ship.ShipyardIndustrialCatalog;
import com.spacesim.content.ship.ShipyardIndustrialCatalog.ModuleIndustrialProfile;
import com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Physical production/service closure for the already installed long-haul freight drives. */
public final class Stage22FreightStrategicProductionCatalogs {
    private Stage22FreightStrategicProductionCatalogs() { }
    private record Drive(String derivative, String original) { }
    private static final List<Drive> DRIVES = List.of(
            new Drive(Stage22FreightStrategicEngineeringCatalogLoader.EMPIRE_LONG_HAUL_DRIVE, "module.empire_drive_endurance_v1"),
            new Drive(Stage22FreightStrategicEngineeringCatalogLoader.UNION_LONG_HAUL_DRIVE, "module.industrial_union_drive_bank_v1"));

    /**
     * Adds both faction product bindings and the long-haul variants to the same manufacturing grammar.
     * The drive recipe is evaluated against the derivative's actual mass, including added tankage.
     * @param base common/Union manufacturing catalog
     * @return immutable complete manufacturing vocabulary, without inventory
     */
    public static Stage18ManufacturingCatalog manufacturing(Stage18ManufacturingCatalog base) {
        var empire = Stage22EmpireManufacturingCatalogLoader.loadDefault();
        var merged = Stage22AuthoredProductionBridge.withProductBindings(base, empire.getProductBindings().stream()
                .filter(b -> base.findProductBinding(b.productContentId()) == null).toList());
        var bindings = DRIVES.stream().map(d -> new Stage18ManufacturingCatalog.ProductBindingDefinition(d.derivative(),
                Objects.requireNonNull(merged.findProductBinding(d.original())).profileId())).toList();
        return Stage22AuthoredProductionBridge.withProductBindings(merged, bindings);
    }

    /**
     * Adds explicit long-haul repair and service bills proportional to actual dry hardware mass.
     * Tankage uses the original drive's material mix and facility envelope; it is not free hardware.
     * @param base combined physical shipyard catalog
     * @return extended catalog without repair, stock or installation grants
     */
    public static Stage18ShipyardCatalog shipyards(Stage18ShipyardCatalog base) {
        var additions = DRIVES.stream().map(d -> {
            var profile = Objects.requireNonNull(base.findModuleProfile(d.original()));
            double ratio = massRatio(d);
            return new Stage18ShipyardCatalog.ModuleServiceProfile(d.derivative(),
                    profile.repairInputsAtFullLossKg().stream().map(i -> new Stage18ShipyardCatalog.PhysicalInputDefinition(i.commodityId(), i.massKg() * ratio)).toList(),
                    profile.maintenanceInputsKg().stream().map(i -> new Stage18ShipyardCatalog.PhysicalInputDefinition(i.commodityId(), i.massKg() * ratio)).toList());
        }).toList();
        return Stage22AuthoredProductionBridge.withShipyardProfiles(base, List.of(), List.of(), additions);
    }

    /**
     * Adds the relevant faction derivative with paid work scaled to its larger dry hardware mass.
     * Existing precision, tooling and simultaneous facility requirements remain authoritative.
     * @param base faction industrial catalog
     * @return extended immutable catalog without performed work
     */
    public static ShipyardIndustrialCatalog industrial(ShipyardIndustrialCatalog base) {
        var modules = new ArrayList<>(base.getModuleProfiles());
        for (var drive : DRIVES) {
            var p = base.findModuleProfile(drive.original());
            if (p == null) continue;
            double ratio = massRatio(drive);
            modules.add(new ModuleIndustrialProfile(drive.derivative(), p.fabricationCapabilities(), p.toolingTags(),
                    p.precisionRequirement(), p.industrialPowerW(), p.laborRequirement(), p.automationRequirement(),
                    p.manufacturingWorkSeconds() * ratio, p.installationWorkSeconds() * ratio, p.removalWorkSeconds() * ratio));
        }
        return new ShipyardIndustrialCatalog(base.getSchemaVersion(), base.getHullProfiles(), modules);
    }

    private static double massRatio(Drive drive) {
        var engineering = EngineeringHolder.VALUE;
        return Objects.requireNonNull(engineering.findModule(drive.derivative())).massKg()
                / Objects.requireNonNull(engineering.findModule(drive.original())).massKg();
    }
    private static final class EngineeringHolder {
        private static final com.spacesim.content.ship.ShipEngineeringCatalog VALUE = Stage22FreightStrategicEngineeringCatalogLoader.loadDefault();
    }
}
