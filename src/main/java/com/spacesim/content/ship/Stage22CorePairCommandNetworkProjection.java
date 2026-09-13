package com.spacesim.content.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.ConstructionInputDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.IntegrationCategory;
import com.spacesim.content.ship.ShipEngineeringCatalog.MaintenanceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * M22.6 common command-network completion for the two Stage-22 core engineering packages.
 *
 * <p>The accepted M22.3/M22.4 base catalogs remain unchanged. This projection adds one ordinary
 * Stage-17.5 communication/datalink module and explicit command-network destroyer variants. The
 * physical datalink replaces the already-authored {@code utility_defense} shield, so networked
 * coordination consumes mass, power, heat, crew and a real utility slot rather than granting a
 * faction-name aura or free command bonus.</p>
 *
 * <p>Only {@code support_channels} is authored because that is the physical network surface currently
 * owned by the common engineering runtime. Range, latency and transport noise are deliberately not
 * invented here; the shared tactical runtime may relay only real sensor measurements through these
 * damage-aware fitted channels.</p>
 */
public final class Stage22CorePairCommandNetworkProjection {
    /** Semantic version pinned by the M22.6 freeze surface. */
    public static final String VERSION = "stage22.core_pair_command_network_projection.v1";
    /** Shared physical fleet datalink installed by both core command-network variants. */
    public static final String DATALINK_MODULE_ID = "module.stage22_core_fleet_datalink_v1";
    /** Empire command-network destroyer. */
    public static final String EMPIRE_DESTROYER_COMMAND_FIT = "fit.empire.destroyer.command_network_v1";
    /** Industrial Union command-network destroyer. */
    public static final String UNION_DESTROYER_COMMAND_FIT = "fit.industrial_union.destroyer.command_network_v1";

    private static final String NETWORK_MOUNT = "utility_defense";
    private static final List<VariantSpec> VARIANTS = List.of(
            new VariantSpec("fit.empire.destroyer.screen_v1", EMPIRE_DESTROYER_COMMAND_FIT),
            new VariantSpec("fit.industrial_union.destroyer.line_v1", UNION_DESTROYER_COMMAND_FIT));

    private Stage22CorePairCommandNetworkProjection() {
        throw new AssertionError("utility class");
    }

    /**
     * Adds the common damage-aware datalink and exactly two paid-slot command-network variants.
     *
     * @param source combined runtime-completed core engineering catalog
     * @return immutable catalog with explicit command-network variants
     */
    public static ShipEngineeringCatalog apply(ShipEngineeringCatalog source) {
        ShipEngineeringCatalog checked = Objects.requireNonNull(source, "source");
        if (checked.findModule(DATALINK_MODULE_ID) != null) {
            throw new IllegalArgumentException("Stage-22 core fleet datalink already exists");
        }

        ArrayList<ModuleDefinition> modules = new ArrayList<>(checked.getModules());
        modules.add(datalinkModule());
        ArrayList<DemonstratorFitDefinition> fits = new ArrayList<>(checked.getDemonstratorFits());
        for (VariantSpec spec : VARIANTS) {
            if (checked.findDemonstratorFit(spec.commandFitId()) != null) {
                throw new IllegalArgumentException("Stage-22 command-network fit already exists: " + spec.commandFitId());
            }
            DemonstratorFitDefinition base = checked.findDemonstratorFit(spec.baseFitId());
            if (base == null) {
                throw new IllegalStateException("Missing Stage-22 command-network base fit: " + spec.baseFitId());
            }
            ArrayList<InstalledModuleDefinition> assignments = new ArrayList<>(base.installedModules());
            int replaced = 0;
            for (int index = 0; index < assignments.size(); index++) {
                InstalledModuleDefinition assignment = assignments.get(index);
                if (!NETWORK_MOUNT.equals(assignment.mountId())) {
                    continue;
                }
                ModuleDefinition displaced = checked.findModule(assignment.moduleId());
                if (displaced == null || displaced.family() != ModuleFamily.SHIELD_FIELD) {
                    throw new IllegalStateException(
                            "Command network must displace the authored defensive module: " + spec.baseFitId());
                }
                assignments.set(index, new InstalledModuleDefinition(NETWORK_MOUNT, DATALINK_MODULE_ID));
                replaced++;
            }
            if (replaced != 1) {
                throw new IllegalStateException(
                        "Command network requires exactly one paid utility_defense tradeoff: " + spec.baseFitId());
            }
            fits.add(new DemonstratorFitDefinition(spec.commandFitId(), base.hullId(), List.copyOf(assignments)));
        }

        return new ShipEngineeringCatalog(
                checked.getSchemaVersion(),
                checked.getMigrationVersion(),
                checked.getMaterials(),
                checked.getResponseSurfaces(),
                checked.getProtectionStacks(),
                checked.getHulls(),
                modules,
                fits);
    }

    private static ModuleDefinition datalinkModule() {
        return new ModuleDefinition(
                DATALINK_MODULE_ID,
                "Core Fleet Datalink",
                ModuleFamily.COMMUNICATION_DATALINK,
                List.of(IntegrationCategory.UTILITY),
                List.of(),
                new Dimensions3d(12d, 8d, 6d),
                320_000d,
                500d,
                2_500_000d,
                0d,
                80_000_000d,
                160_000_000d,
                2_000_000_000d,
                30_000_000d,
                8_000_000_000d,
                30_000_000d,
                0d,
                8,
                12,
                List.of(),
                Map.of("thermal_w", 30_000_000d),
                List.of(
                        new ConstructionInputDefinition("component.heavy", 180d),
                        new ConstructionInputDefinition("component.electrical", 520d),
                        new ConstructionInputDefinition("component.precision", 620d)),
                new MaintenanceDefinition(259_200d, 14_400d, 0.68d),
                Map.of("support_channels", 64d));
    }

    private record VariantSpec(String baseFitId, String commandFitId) { }
}
