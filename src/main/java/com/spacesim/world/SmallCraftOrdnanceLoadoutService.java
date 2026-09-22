package com.spacesim.world;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.Stage22CarrierEngineeringCatalogLoader;
import com.spacesim.content.weapon.Stage22CarrierWeaponRuntimeCatalogLoader;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog;
import com.spacesim.content.weapon.WeaponLauncherCatalog;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipInstanceRuntimeState;
import com.spacesim.ship.WeaponDefinition.Family;
import com.spacesim.ship.WeaponLoadoutState;
import com.spacesim.ship.WeaponLoadoutState.FeedBinding;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;

import java.util.ArrayList;
import java.util.Objects;

/**
 * M22.8J identity binding between already-loaded physical ammunition and production ammo content.
 *
 * <p>This service creates no ammunition, mass or quantity. It may operate only on a physically
 * embarked SERVICING craft whose common consumable state already contains a non-zero AMMUNITION
 * load. Binding succeeds only when that mass/count closes against the requested production
 * ammunition body and the installed weapon/launcher interface. The result changes only
 * {@link WeaponLoadoutState}; all physical inventory remains owned by the existing turnaround
 * authority.</p>
 */
public final class SmallCraftOrdnanceLoadoutService {
    private static final double RELATIVE_TOLERANCE = 1e-9d;

    private final SmallCraftRegistry craft;
    private final SmallCraftHangarRegistry hangars;
    private final ShipEngineeringCatalog engineering;
    private final WeaponLauncherCatalog launchers;
    private final WeaponAmmunitionCatalog ammunition;

    /**
     * Creates the production loadout binder from the exact M22.8J tactical content bundle.
     *
     * @param craft persistent individual-craft authority
     * @param hangars physical hangar occupancy authority
     * @return servicing-only ordnance identity binder
     */
    public static SmallCraftOrdnanceLoadoutService production(
            SmallCraftRegistry craft,
            SmallCraftHangarRegistry hangars) {
        Stage22CarrierWeaponRuntimeCatalogLoader.RuntimeContent content =
                Stage22CarrierWeaponRuntimeCatalogLoader.loadCombined();
        SmallCraftRegistry registry = Objects.requireNonNull(craft, "craft");
        if (!registry.engineeringCatalogFingerprint()
                .equals(content.engineering().getFingerprint())) {
            throw new IllegalArgumentException(
                    "small-craft registry and ordnance engineering catalog must match");
        }
        return new SmallCraftOrdnanceLoadoutService(
                registry,
                Objects.requireNonNull(hangars, "hangars"),
                content.engineering(),
                content.launchers(),
                content.ammunition());
    }

    SmallCraftOrdnanceLoadoutService(
            SmallCraftRegistry craft,
            SmallCraftHangarRegistry hangars,
            ShipEngineeringCatalog engineering,
            WeaponLauncherCatalog launchers,
            WeaponAmmunitionCatalog ammunition) {
        this.craft = Objects.requireNonNull(craft, "craft");
        this.hangars = Objects.requireNonNull(hangars, "hangars");
        this.engineering = Objects.requireNonNull(engineering, "engineering");
        this.launchers = Objects.requireNonNull(launchers, "launchers");
        this.ammunition = Objects.requireNonNull(ammunition, "ammunition");
    }

    /**
     * Binds a production ammunition identity to one already-loaded physical feed.
     *
     * @param craftId individual craft identity
     * @param mountId installed weapon mount
     * @param interfaceId installed AMMUNITION interface
     * @param ammunitionContentId production ammunition content ID
     * @return updated authoritative individual craft state
     */
    public SmallCraftState bindLoadedAmmunition(
            SmallCraftId craftId,
            String mountId,
            String interfaceId,
            String ammunitionContentId) {
        SmallCraftId id = Objects.requireNonNull(craftId, "craftId");
        String mount = requireText(mountId, "mountId");
        String iface = requireText(interfaceId, "interfaceId");
        String ammoId = requireText(ammunitionContentId, "ammunitionContentId");
        SmallCraftHangarRegistry.Assignment assignment = hangars.find(id).orElseThrow(
                () -> new IllegalStateException(
                        "ordnance binding requires physically embarked craft"));
        if (assignment.state() != OccupancyState.SERVICING) {
            throw new IllegalStateException(
                    "ordnance identity may be changed only while craft is SERVICING");
        }

        SmallCraftState state = craft.find(id).orElseThrow(
                () -> new IllegalArgumentException("unknown small craft: " + id));
        var installed = state.fit().installedModules().stream()
                .filter(value -> value.mountId().equals(mount))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "ordnance binding references uninstalled mount: " + mount));
        var module = engineering.findModule(installed.moduleId());
        if (module == null) {
            throw new IllegalStateException(
                    "installed weapon disappeared from engineering catalog: "
                            + installed.moduleId());
        }
        var launcher = launchers.findByModuleId(module.id());
        if (launcher == null || !launcher.ammunitionInterfaceId().equals(iface)) {
            throw new IllegalArgumentException(
                    "installed weapon has no matching production launcher feed");
        }
        if (launcher.family() != Family.KINETIC) {
            throw new IllegalArgumentException(
                    "M22.8J loadout binder currently accepts production kinetic bodies only");
        }
        WeaponAmmunitionCatalog.KineticAmmunitionDefinition ammo =
                ammunition.findKinetic(ammoId);
        if (ammo == null) {
            throw new IllegalArgumentException(
                    "unknown/non-kinetic production ammunition: " + ammoId);
        }
        if (ammo.massKg() > launcher.maxProjectileMassKg()
                || ammo.lengthM() > launcher.maxProjectileLengthM()
                || ammo.diameterM() > launcher.maxProjectileDiameterM()) {
            throw new IllegalArgumentException(
                    "requested ammunition exceeds installed launcher envelope");
        }

        ConsumableLoad physical = state.runtimeState().consumables().interfaceLoads().stream()
                .filter(value -> value.mountId().equals(mount)
                        && value.interfaceId().equals(iface)
                        && value.kind() == InterfaceKind.AMMUNITION)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "cannot bind ammo identity before physical ammunition is loaded"));
        if (physical.amount() <= 0d || physical.massKg() <= 0d || physical.itemCount() <= 0L) {
            throw new IllegalStateException(
                    "cannot bind ammo identity to an empty physical feed");
        }
        if (!nearlyEqual(physical.amount(), physical.itemCount())) {
            throw new IllegalStateException(
                    "kinetic feed native amount must close against physical item count");
        }
        double expectedMass = ammo.massKg() * physical.itemCount();
        if (!nearlyEqual(physical.massKg(), expectedMass)) {
            throw new IllegalArgumentException(
                    "physical loaded ammunition mass disagrees with production ammo identity");
        }

        ArrayList<FeedBinding> feeds =
                new ArrayList<>(state.instanceState().weaponLoadout().feeds());
        feeds.removeIf(value -> value.mountId().equals(mount)
                && value.interfaceId().equals(iface));
        feeds.add(new FeedBinding(mount, iface, ammoId));
        ShipInstanceRuntimeState current = state.instanceState();
        SmallCraftState updated = new SmallCraftState(
                state.id(),
                state.stableFactionId(),
                state.designId(),
                state.fit(),
                state.runtimeState(),
                new ShipInstanceRuntimeState(
                        current.damage(),
                        current.shieldStatesByMount(),
                        current.maintenance(),
                        new WeaponLoadoutState(feeds),
                        current.weaponMountRuntime()));
        craft.replacePhysicalState(updated);
        return updated;
    }

    private static boolean nearlyEqual(double first, double second) {
        double scale = Math.max(1d, Math.max(Math.abs(first), Math.abs(second)));
        return Math.abs(first - second) <= scale * RELATIVE_TOLERANCE;
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
