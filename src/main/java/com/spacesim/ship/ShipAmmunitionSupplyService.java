package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.Stage22CivilianMiningProductionPath;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.Stage23RuntimeEngineeringCatalogLoader;
import com.spacesim.content.weapon.Stage228SmallCraftWeaponRuntimeCatalogLoader;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.economy.Stage19WarfareSupplyService;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

/** Replenishes an already bound kinetic feed through the ordinary finite ammunition authority. */
public final class ShipAmmunitionSupplyService {
    private final ProductionEngineeringRuntimeResolver resolver = new ProductionEngineeringRuntimeResolver();

    private static final class Catalog {
        private static final com.spacesim.content.ship.ShipEngineeringCatalog ENGINEERING = Stage23RuntimeEngineeringCatalogLoader.loadDefault();
        private static final com.spacesim.content.weapon.Stage22CorePairWeaponRuntimeCatalogLoader.RuntimeContent WEAPONS =
                Stage228SmallCraftWeaponRuntimeCatalogLoader.loadCombined();
    }

    /**
     * Reads only compatible, installed, operational kinetic feeds with an existing ammunition identity.
     * @param ship current fitted ship
     * @return immutable replenishment targets, including exhausted magazines
     */
    public List<Feed> feeds(EngineeringComponent ship) {
        Objects.requireNonNull(ship);
        var derived = resolver.derive(ship);
        if (!derived.validation().isValid()) return List.of();
        var products = com.spacesim.content.Stage23SmallCraftProductionCatalogs.products();
        var mounts = new ArrayList<ShipWeaponEngineeringAdapter.FittedKineticMount>();
        var adapter = new ShipWeaponEngineeringAdapter();
        for (var installed : ship.fit.installedModules()) {
            var profile = Catalog.WEAPONS.launchers().findByModuleId(installed.moduleId());
            if (profile == null || profile.family() != WeaponDefinition.Family.KINETIC
                    || ship.instanceState.weaponLoadout().ammunitionContentId(installed.mountId(), profile.ammunitionInterfaceId()).isEmpty()) continue;
            try {
                mounts.addAll(adapter.deriveKineticMounts(derived, Catalog.WEAPONS.ammunition(),
                        Catalog.WEAPONS.launchers(), ship.instanceState.weaponLoadout(), installed.mountId()));
            } catch (IllegalArgumentException unavailable) {
                // This feed is outside the admitted physical ammunition vocabulary.
            }
        }
        return mounts.stream().map(mount -> {
            var module = Catalog.ENGINEERING.findModule(mount.moduleId());
            var port = module.interfaces().stream().filter(i -> i.kind() == InterfaceKind.AMMUNITION
                    && i.id().equals(mount.launcher().ammunitionInterfaceId())).findFirst().orElseThrow();
            String productId = ship.instanceState.weaponLoadout().ammunitionContentId(mount.mountId(), port.id()).orElseThrow();
            var product = products.findProduct(productId);
            if (product == null || Math.abs(product.unitMassKg() - mount.round().massKg()) > Math.max(1d, mount.round().massKg()) * 1e-9)
                throw new IllegalStateException("Ammunition product disagrees with the bound physical round");
            var load = ship.runtimeState.consumables().interfaceLoads().stream().filter(l ->
                    l.mountId().equals(mount.mountId()) && l.interfaceId().equals(port.id()) && l.kind() == InterfaceKind.AMMUNITION)
                    .findFirst().orElse(null);
            return new Feed(mount.mountId(), productId, module.displayName(), product.unitMassKg(),
                    load == null ? 0 : load.itemCount(), Math.max(0, port.capacity() - (load == null ? 0 : load.amount())),
                    mount.launcher(), port);
        }).toList();
    }

    /**
     * Transfers existing countable rounds without replacing their physical feed identity.
     * Caller owns berth, ownership and shared endpoint handling authorization.
     * @param ship authoritative fitted ship
     * @param mountId existing kinetic mount
     * @param rounds positive whole round count
     * @param station canonical finite source storage
     * @return conserved loaded mass
     */
    public double load(EngineeringComponent ship, String mountId, int rounds, Stage18StationStorage station) {
        var feed = feeds(ship).stream().filter(f -> f.mountId().equals(mountId)).findFirst().orElseThrow();
        double mass = feed.unitMassKg() * rounds;
        var hull = Catalog.ENGINEERING.findHull(ship.fit.hullId());
        if (rounds <= 0 || !Double.isFinite(mass) || hull == null
                || resolver.derive(ship).totalMassKg() + mass > hull.maxOperationalMassKg())
            throw new IllegalStateException("Ammunition load exceeds fitted hull mass or count bounds");
        var old = ship.runtimeState;
        var result = new Stage19WarfareSupplyService(com.spacesim.content.Stage23SmallCraftProductionCatalogs.products()).loadAmmunition(
                feed.productId(), mountId, rounds, feed.launcher(), feed.port(), old.consumables(), station);
        if (!result.committed()) throw new IllegalStateException("Physical ammunition loading rejected: " + result.status());
        ship.setRuntimeState(new ShipEngineeringRuntime.RuntimeState(result.consumables(), old.sharedBusEnergyJ(),
                old.shipHeatStoredJ(), old.localHeatJByMount(), old.thrustLimitNByMount(), old.coolantBusCapacityW(), old.ftlCooldownSecondsByMount()));
        return result.loadedMassKg();
    }

    /**
     * A physically authored replenishment target; no stock or readiness is granted.
     * @param mountId fitted weapon mount
     * @param productId already bound round identity
     * @param weaponName authored module label
     * @param unitMassKg physical mass per round
     * @param currentRounds remaining rounds
     * @param freeAmount remaining interface-native capacity
     * @param launcher ordinary physical feed definition
     * @param port fitted ammunition interface
     */
    public record Feed(String mountId, String productId, String weaponName, double unitMassKg,
            long currentRounds, double freeAmount, WeaponDefinition.Launcher launcher, InterfaceDefinition port) { }
}
