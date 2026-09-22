package com.spacesim.content;

import com.spacesim.content.Stage18ShipConsumableCatalog.ShipConsumableBinding;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.Stage228SmallCraftEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage228SmallCraftProductionProjection;

import java.util.ArrayList;

/**
 * M22.8J production servicing overlay for the compact carrier small-craft drive.
 *
 * <p>The accepted Stage-22 consumable catalog remains frozen. This loader appends one ordinary
 * Stage-18I commodity-to-interface binding for the common M22.8J compact reaction-mass drive so
 * persistent production craft can be lawfully refuelled through the existing G station-supply
 * authority rather than receiving hidden docking fuel.</p>
 */
public final class Stage228SmallCraftShipConsumableCatalogLoader {
    /** Stable servicing binding for the common production small-craft drive. */
    public static final String REACTION_MASS_BINDING_ID =
            "ship_consumable.reaction_mass.small_craft_water_v1";

    private Stage228SmallCraftShipConsumableCatalogLoader() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads accepted Stage-22 bindings plus the M22.8J small-craft drive binding.
     *
     * @return immutable servicing catalog including physical small-craft reaction mass
     */
    public static Stage18ShipConsumableCatalog loadDefault() {
        Stage18ShipConsumableCatalog base = Stage22ShipConsumableCatalogLoader.loadDefault();
        var engineering = Stage228SmallCraftEngineeringCatalogLoader.loadDefault();
        var drive = engineering.findModule(Stage228SmallCraftProductionProjection.DRIVE_ID);
        if (drive == null
                || drive.interfaces().stream().noneMatch(value ->
                        value.kind() == InterfaceKind.REACTION_MASS
                                && value.id().equals("propellant_feed"))) {
            throw new IllegalStateException(
                    "M22.8J production small-craft drive lacks reaction-mass interface");
        }
        if (base.findBinding(REACTION_MASS_BINDING_ID) != null) {
            throw new IllegalStateException(
                    "M22.8J small-craft reaction-mass binding already exists");
        }
        ArrayList<ShipConsumableBinding> bindings = new ArrayList<>(base.getBindings());
        bindings.add(new ShipConsumableBinding(
                REACTION_MASS_BINDING_ID,
                Stage228SmallCraftProductionProjection.DRIVE_ID,
                "propellant_feed",
                InterfaceKind.REACTION_MASS,
                "commodity.material.purified_water",
                1d));
        return new Stage18ShipConsumableCatalog(base.getSchemaVersion(), bindings);
    }
}
