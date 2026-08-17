package com.spacesim.combat.acceptance;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog.GuidedAmmunitionDefinition;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog.KineticAmmunitionDefinition;
import com.spacesim.content.weapon.WeaponLauncherCatalog.LauncherProfile;
import com.spacesim.ship.DerivedShipCalculator;
import com.spacesim.ship.ShieldFieldRuntime;
import com.spacesim.ship.ShipDamageRuntime.Snapshot;
import com.spacesim.ship.ShipEngineeringRuntime;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipInstanceRuntimeState;
import com.spacesim.ship.ShipShieldEngineeringAdapter;
import com.spacesim.ship.ShipyardEngineeringService.MaintenanceState;
import com.spacesim.ship.WeaponDefinition.Family;
import com.spacesim.ship.WeaponLoadoutState;
import com.spacesim.ship.WeaponLoadoutState.FeedBinding;
import com.spacesim.ship.WeaponMountRuntime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Materializes Stage-17.5I representative ships through the ordinary production physical state.
 *
 * <p>No fleet doctrine or matchup ID enters any physics calculation. The materializer starts from an
 * ordinary demonstrator fit, allocates physical reaction mass and ammunition inside the hull's actual
 * remaining operational-mass envelope, applies the requested pre-damage/thermal variation through
 * Stage-17.5F/H state, initializes the existing engineering runtime, and charges only genuinely fitted
 * shield emitters. This is acceptance setup, not a second ship runtime.</p>
 */
public final class Stage175IShipMaterializer {
    private static final double REACTION_MASS_SLACK_FRACTION = 0.25d;
    private static final double AMMUNITION_SLACK_FRACTION = 0.08d;
    private static final double EPSILON = 1e-9d;

    private final Stage175ICombatTestContentPack pack;
    private final DerivedShipCalculator calculator;
    private final ShipEngineeringRuntime engineeringRuntime;
    private final ShipShieldEngineeringAdapter shieldAdapter = new ShipShieldEngineeringAdapter();
    private final ShieldFieldRuntime shieldRuntime = new ShieldFieldRuntime();

    /**
     * Creates a materializer over one exact acceptance content pack.
     *
     * @param pack validated production content pack
     */
    public Stage175IShipMaterializer(Stage175ICombatTestContentPack pack) {
        this.pack = Objects.requireNonNull(pack, "pack");
        this.calculator = new DerivedShipCalculator(pack.engineering());
        this.engineeringRuntime = new ShipEngineeringRuntime(pack.engineering());
    }

    /**
     * Builds one authoritative initial ship state for an acceptance variation.
     *
     * @param fitId ordinary demonstrator-fit content ID
     * @param variation deterministic initial-condition variation
     * @return physical materialized ship state
     */
    public MaterializedShip materialize(
            String fitId,
            Stage175ICombatTestManifest.VariationDefinition variation) {
        Stage175ICombatTestManifest.VariationDefinition checkedVariation =
                Objects.requireNonNull(variation, "variation");
        ShipEngineeringCatalog.DemonstratorFitDefinition definition =
                pack.engineering().findDemonstratorFit(requireNonBlank(fitId, "fitId"));
        if (definition == null) {
            throw new IllegalArgumentException("Unknown Stage-17.5I fit: " + fitId);
        }
        ShipEngineeringCatalog.HullDefinition hull = pack.engineering().findHull(definition.hullId());
        InstalledFit fit = InstalledFit.fromDemonstrator(definition);
        DerivedShipState empty = calculator.derive(hull, fit, ConsumableState.empty(), DamageState.pristine());
        if (!empty.validation().isValid()) {
            throw new IllegalArgumentException("Cannot materialize invalid fit: " + fitId);
        }

        double massSlackKg = Math.max(0d, hull.maxOperationalMassKg() - empty.totalMassKg());
        ConsumableAndLoadout physicalLoads = buildPhysicalLoads(
                fit,
                massSlackKg * REACTION_MASS_SLACK_FRACTION,
                massSlackKg * AMMUNITION_SLACK_FRACTION * checkedVariation.ammunitionLoadFraction());
        DamageState damage = damageState(fit, checkedVariation.preDamageIntegrity());
        DerivedShipState derived = calculator.derive(hull, fit, physicalLoads.consumables(), damage);
        if (!derived.validation().isValid()) {
            throw new IllegalStateException("Variation created invalid physical fit: " + fitId);
        }

        RuntimeState operating = engineeringRuntime.initialize(fit, physicalLoads.consumables(), damage);
        operating = applyInitialThermalLoad(fit, damage, operating, checkedVariation.initialThermalLoadFraction());
        Snapshot snapshot = new Snapshot(
                compartmentIntegrity(hull, checkedVariation.preDamageIntegrity()),
                damage);
        Map<String, ShieldFieldRuntime.State> shields = new TreeMap<>();
        for (ShipShieldEngineeringAdapter.FittedShield shield : shieldAdapter.derive(derived)) {
            shields.put(shield.mountId(), shield.chargedState(shieldRuntime));
        }
        ShipInstanceRuntimeState instance = new ShipInstanceRuntimeState(
                snapshot,
                shields,
                new MaintenanceState(Map.of()),
                physicalLoads.loadout(),
                WeaponMountRuntime.RuntimeState.empty());
        EngineeringComponent component = new EngineeringComponent(fit, operating, instance);
        return new MaterializedShip(definition.id(), component, derived);
    }

    private ConsumableAndLoadout buildPhysicalLoads(
            InstalledFit fit,
            double reactionMassBudgetKg,
            double ammunitionBudgetKg) {
        List<ConsumableLoad> loads = new ArrayList<>();
        List<FeedBinding> bindings = new ArrayList<>();
        List<InstalledModule> installed = fit.installedModules().stream()
                .map(value -> new InstalledModule(
                        value.mountId(), requireModule(value.moduleId())))
                .sorted(Comparator.comparing(InstalledModule::mountId))
                .toList();

        List<InstalledModule> reactionFeeds = installed.stream()
                .filter(value -> interfaceOfKind(value.module(), InterfaceKind.REACTION_MASS) != null)
                .toList();
        if (!reactionFeeds.isEmpty() && reactionMassBudgetKg > 0d) {
            double perFeedBudgetKg = reactionMassBudgetKg / reactionFeeds.size();
            for (InstalledModule installedModule : reactionFeeds) {
                InterfaceDefinition feed = interfaceOfKind(installedModule.module(), InterfaceKind.REACTION_MASS);
                double massKg = Math.min(feed.capacity(), perFeedBudgetKg);
                if (massKg > EPSILON) {
                    loads.add(new ConsumableLoad(
                            installedModule.mountId(), feed.id(), InterfaceKind.REACTION_MASS,
                            massKg, massKg, 0L));
                }
            }
        }

        List<WeaponFeed> weaponFeeds = new ArrayList<>();
        for (InstalledModule installedModule : installed) {
            LauncherProfile launcher = pack.launchers().findByModuleId(installedModule.module().id());
            if (launcher == null) {
                continue;
            }
            InterfaceDefinition feed = installedModule.module().interfaces().stream()
                    .filter(value -> value.kind() == InterfaceKind.AMMUNITION)
                    .filter(value -> value.id().equals(launcher.ammunitionInterfaceId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Launcher lost physical ammunition interface: " + installedModule.module().id()));
            AmmunitionSelection ammunition = selectAmmunition(installedModule.module(), launcher);
            weaponFeeds.add(new WeaponFeed(installedModule.mountId(), feed, launcher, ammunition));
        }
        if (!weaponFeeds.isEmpty() && ammunitionBudgetKg > 0d) {
            double perFeedBudgetKg = ammunitionBudgetKg / weaponFeeds.size();
            for (WeaponFeed weaponFeed : weaponFeeds) {
                double unitMassKg = weaponFeed.ammunition().massKg();
                long massLimitedCount = (long) Math.floor(perFeedBudgetKg / unitMassKg);
                long interfaceLimitedCount = (long) Math.floor(
                        weaponFeed.feed().capacity() / weaponFeed.launcher().ammunitionAmountPerShot());
                long count = Math.max(0L, Math.min(massLimitedCount, interfaceLimitedCount));
                if (count <= 0L && perFeedBudgetKg + EPSILON >= unitMassKg && interfaceLimitedCount > 0L) {
                    count = 1L;
                }
                if (count > 0L) {
                    double amount = count * weaponFeed.launcher().ammunitionAmountPerShot();
                    double massKg = count * unitMassKg;
                    loads.add(new ConsumableLoad(
                            weaponFeed.mountId(), weaponFeed.feed().id(), InterfaceKind.AMMUNITION,
                            amount, massKg, count));
                    bindings.add(new FeedBinding(
                            weaponFeed.mountId(), weaponFeed.feed().id(),
                            weaponFeed.ammunition().contentId()));
                }
            }
        }
        return new ConsumableAndLoadout(
                new ConsumableState(0d, 0d, 0d, 0d, loads),
                new WeaponLoadoutState(bindings));
    }

    private AmmunitionSelection selectAmmunition(ModuleDefinition module, LauncherProfile launcher) {
        if (launcher.family() == Family.KINETIC) {
            Double expectedMassKg = module.capabilityParameters().get("projectile_mass_kg");
            if (expectedMassKg == null) {
                throw new IllegalStateException("Kinetic module lacks projectile_mass_kg: " + module.id());
            }
            KineticAmmunitionDefinition selected = pack.ammunition().getKineticAmmunition().stream()
                    .filter(value -> Math.abs(value.massKg() - expectedMassKg) <= 1e-6d)
                    .filter(value -> withinEnvelope(value.massKg(), value.lengthM(), value.diameterM(), launcher))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No physical kinetic round matches launcher/module: " + module.id()));
            return new AmmunitionSelection(selected.id(), selected.massKg());
        }
        if (launcher.family() == Family.GUIDED) {
            Comparator<GuidedAmmunitionDefinition> byMass = Comparator.comparingDouble(
                    value -> value.toRuntimeWeapon().wetMassKg());
            boolean interceptorFeed = launcher.ammunitionInterfaceId().contains("interceptor");
            GuidedAmmunitionDefinition selected = pack.ammunition().getGuidedAmmunition().stream()
                    .filter(value -> withinEnvelope(
                            value.toRuntimeWeapon().wetMassKg(), value.lengthM(), value.diameterM(), launcher))
                    .min(interceptorFeed ? byMass : byMass.reversed())
                    .orElseThrow(() -> new IllegalStateException(
                            "No physical guided body matches launcher: " + module.id()));
            return new AmmunitionSelection(selected.id(), selected.toRuntimeWeapon().wetMassKg());
        }
        throw new IllegalStateException("Unsupported Stage-17.5I launcher family: " + launcher.family());
    }

    private static boolean withinEnvelope(
            double massKg,
            double lengthM,
            double diameterM,
            LauncherProfile launcher) {
        return massKg <= launcher.maxProjectileMassKg() + EPSILON
                && lengthM <= launcher.maxProjectileLengthM() + EPSILON
                && diameterM <= launcher.maxProjectileDiameterM() + EPSILON;
    }

    private RuntimeState applyInitialThermalLoad(
            InstalledFit fit,
            DamageState damage,
            RuntimeState state,
            double thermalFraction) {
        if (thermalFraction <= 0d) {
            return state;
        }
        TreeMap<String, Double> localHeat = new TreeMap<>(state.localHeatJByMount());
        for (ShipEngineeringCatalog.InstalledModuleDefinition assignment : fit.installedModules()) {
            ModuleDefinition module = requireModule(assignment.moduleId());
            double integrity = damage.moduleIntegrityByMount().getOrDefault(assignment.mountId(), 1d);
            double capacityJ = module.localThermalCapacityJ() * integrity;
            if (capacityJ > 0d) {
                localHeat.put(assignment.mountId(), capacityJ * thermalFraction);
            }
        }
        return new RuntimeState(
                state.consumables(),
                state.sharedBusEnergyJ(),
                state.shipHeatStoredJ(),
                localHeat,
                state.thrustLimitNByMount(),
                state.coolantBusCapacityW(),
                state.ftlCooldownSecondsByMount());
    }

    private ModuleDefinition requireModule(String moduleId) {
        ModuleDefinition module = pack.engineering().findModule(moduleId);
        if (module == null) {
            throw new IllegalStateException("Fit references unknown module: " + moduleId);
        }
        return module;
    }

    private static InterfaceDefinition interfaceOfKind(ModuleDefinition module, InterfaceKind kind) {
        return module.interfaces().stream().filter(value -> value.kind() == kind).findFirst().orElse(null);
    }

    private static DamageState damageState(InstalledFit fit, double integrity) {
        if (integrity >= 1d) {
            return DamageState.pristine();
        }
        TreeMap<String, Double> damage = new TreeMap<>();
        fit.installedModules().forEach(value -> damage.put(value.mountId(), integrity));
        return new DamageState(damage);
    }

    private static Map<String, Double> compartmentIntegrity(
            ShipEngineeringCatalog.HullDefinition hull,
            double integrity) {
        TreeMap<String, Double> result = new TreeMap<>();
        hull.compartments().forEach(value -> result.put(value.id(), integrity));
        return Map.copyOf(result);
    }

    private static String requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
        return value;
    }

    /**
     * Fully initialized physical ship used by Stage-17.5I headless scenarios.
     *
     * @param fitId ordinary production demonstrator-fit ID
     * @param engineering authoritative fitted engineering component
     * @param derived common damage-aware derived capability snapshot at materialization time
     */
    public record MaterializedShip(
            String fitId,
            EngineeringComponent engineering,
            DerivedShipState derived) {
        /**
         * Validates one materialized acceptance ship.
         *
         * @param fitId ordinary production demonstrator-fit ID
         * @param engineering authoritative fitted engineering component
         * @param derived common damage-aware derived capability snapshot
         */
        public MaterializedShip {
            requireNonBlank(fitId, "fitId");
            Objects.requireNonNull(engineering, "engineering");
            Objects.requireNonNull(derived, "derived");
        }
    }

    private record InstalledModule(String mountId, ModuleDefinition module) { }

    private record AmmunitionSelection(String contentId, double massKg) { }

    private record WeaponFeed(
            String mountId,
            InterfaceDefinition feed,
            LauncherProfile launcher,
            AmmunitionSelection ammunition) { }

    private record ConsumableAndLoadout(ConsumableState consumables, WeaponLoadoutState loadout) { }
}
