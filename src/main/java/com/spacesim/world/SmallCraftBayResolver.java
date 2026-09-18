package com.spacesim.world;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipInstanceRuntimeState;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import com.spacesim.world.SmallCraftHangarCapacity.HostKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves installed Stage-17.5 hangar modules into the shared M22.8B physical bay model.
 *
 * <p>Ship bays come only from actually installed {@link ModuleFamily#HANGAR_SMALL_CRAFT} modules.
 * Module physical dimensions provide the single-craft envelope, occupied integration volume is the
 * bounded physical bay volume, and {@code supported_craft_mass_kg} provides authored support mass.
 * Current module integrity reduces aggregate mass/volume capacity without deleting embarked craft.</p>
 */
public final class SmallCraftBayResolver {
    /** Required capability key on production hangar modules. */
    public static final String SUPPORTED_CRAFT_MASS_KG = "supported_craft_mass_kg";

    private final ShipEngineeringCatalog catalog;

    /**
     * Creates a resolver over one immutable production engineering catalog.
     *
     * @param catalog production Stage-17.5 catalog
     */
    public SmallCraftBayResolver(ShipEngineeringCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    /**
     * Resolves every physically installed hangar module on one ship.
     *
     * @param hostStableId persistent host identity
     * @param fit current installed fit
     * @param instanceState current damage/maintenance instance state
     * @return deterministic mount-ID ordered physical bay definitions
     */
    public List<BayDefinition> resolveShipBays(
            String hostStableId,
            InstalledFit fit,
            ShipInstanceRuntimeState instanceState) {
        String host = requireText(hostStableId, "hostStableId");
        InstalledFit checkedFit = Objects.requireNonNull(fit, "fit");
        ShipInstanceRuntimeState checkedInstance = Objects.requireNonNull(instanceState, "instanceState");
        Map<String, Double> integrityByMount =
                checkedInstance.damage().moduleDamage().moduleIntegrityByMount();

        ArrayList<BayDefinition> result = new ArrayList<>();
        for (var installed : checkedFit.installedModules()) {
            var module = catalog.findModule(installed.moduleId());
            if (module == null) {
                throw new IllegalArgumentException("Installed module not found in catalog: " + installed.moduleId());
            }
            if (module.family() != ModuleFamily.HANGAR_SMALL_CRAFT) {
                continue;
            }
            double supportedMassKg = requiredPositiveCapability(
                    module.capabilityParameters(), SUPPORTED_CRAFT_MASS_KG, module.id());
            double condition = integrityByMount.getOrDefault(installed.mountId(), 1d);
            result.add(new BayDefinition(
                    new BayId(host, installed.mountId()),
                    HostKind.SHIP,
                    module.physicalDimensionsM(),
                    module.occupiedVolumeM3(),
                    supportedMassKg,
                    condition));
        }
        result.sort(Comparator.comparing(BayDefinition::id));
        return List.copyOf(result);
    }

    /**
     * Creates a station/outpost bay using the same physical capacity model.
     *
     * <p>This is the Stage-18 station integration seam: station content supplies explicit SI
     * envelope, usable volume, support mass and condition rather than receiving a station-name bonus.</p>
     *
     * @param hostStableId persistent station identity
     * @param bayStableId station-local bay identity
     * @param singleCraftEnvelopeM maximum physical craft envelope
     * @param usableVolumeM3 pristine physical bay volume
     * @param supportedMassKg pristine total supported craft mass
     * @param conditionFraction current physical bay condition
     * @return shared physical bay definition
     */
    public BayDefinition stationBay(
            String hostStableId,
            String bayStableId,
            Dimensions3d singleCraftEnvelopeM,
            double usableVolumeM3,
            double supportedMassKg,
            double conditionFraction) {
        return new BayDefinition(
                new BayId(hostStableId, bayStableId),
                HostKind.STATION,
                singleCraftEnvelopeM,
                usableVolumeM3,
                supportedMassKg,
                conditionFraction);
    }

    private static double requiredPositiveCapability(
            Map<String, Double> capabilities,
            String key,
            String moduleId) {
        Double value = Objects.requireNonNull(capabilities, "capabilities").get(key);
        if (value == null || !Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(
                    "Hangar module lacks positive " + key + ": " + moduleId);
        }
        return value;
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
