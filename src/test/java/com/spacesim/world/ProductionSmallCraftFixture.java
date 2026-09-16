package com.spacesim.world;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader;
import com.spacesim.ship.ShipDamageRuntime;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipInstanceRuntimeState;
import com.spacesim.ship.ShipyardEngineeringService.MaintenanceState;
import com.spacesim.ship.WeaponLoadoutState;
import com.spacesim.ship.WeaponMountRuntime;

import java.util.List;
import java.util.Map;

/** Shared production-catalog fixture for M22.8A identity/persistence acceptance tests. */
public final class ProductionSmallCraftFixture {
    /** Existing Stage-22 authored fit used only to prove the common production-content validation seam. */
    public static final String DESIGN_ID = "fit.empire.corvette.line_v1";

    private ProductionSmallCraftFixture() {
        throw new AssertionError("utility class");
    }

    /** @return ordinary M22.8A fitting authority over the accepted Stage-22 core-pair catalog */
    public static SmallCraftFitAuthority fitAuthority() {
        return new SmallCraftFitAuthority(Stage22CorePairEngineeringCatalogLoader.loadDefault());
    }

    /**
     * Creates one physically valid state from an existing authored production fit.
     *
     * <p>M22.8J will author actual small-craft production definitions; this fixture deliberately does
     * not pre-empt that content slice. It proves that M22.8A uses the same fit IDs, interfaces and
     * Stage-17.5 budgets as existing production ships.</p>
     *
     * @param id stable test craft identity
     * @param ammunitionCount physical ammunition item count
     * @param ammunitionMassKg physical ammunition mass
     * @param reactionMassKg physical reaction mass
     * @param weaponIntegrity weapon mount integrity in [0,1]
     * @param maintenanceAgeSeconds physical service age for the drive mount
     * @return valid production-catalog-backed craft state
     */
    public static SmallCraftState craft(
            SmallCraftId id,
            long ammunitionCount,
            double ammunitionMassKg,
            double reactionMassKg,
            double weaponIntegrity,
            double maintenanceAgeSeconds) {
        ShipEngineeringCatalog catalog = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        InstalledFit fit = InstalledFit.fromDemonstrator(catalog.findDemonstratorFit(DESIGN_ID));
        ConsumableState consumables = new ConsumableState(
                0d,
                20d,
                0d,
                0d,
                List.of(
                        new ConsumableLoad(
                                "weapon_primary",
                                "kinetic_feed",
                                InterfaceKind.AMMUNITION,
                                ammunitionCount,
                                ammunitionMassKg,
                                ammunitionCount),
                        new ConsumableLoad(
                                "core_drive",
                                "propellant_feed",
                                InterfaceKind.REACTION_MASS,
                                reactionMassKg,
                                reactionMassKg,
                                0L)));
        RuntimeState runtime = new RuntimeState(
                consumables,
                32_000d,
                1_600d,
                Map.of("core_drive", 310d),
                Map.of("core_drive", 22_000d),
                9_000d,
                Map.of());
        ShipInstanceRuntimeState instance = new ShipInstanceRuntimeState(
                new ShipDamageRuntime.Snapshot(
                        Map.of("citadel", 0.79d),
                        new DamageState(Map.of("weapon_primary", weaponIntegrity))),
                Map.of(),
                new MaintenanceState(Map.of("core_drive", maintenanceAgeSeconds)),
                WeaponLoadoutState.empty(),
                WeaponMountRuntime.RuntimeState.empty());
        return new SmallCraftState(
                id,
                "faction.empire",
                DESIGN_ID,
                fit,
                runtime,
                instance);
    }
}
