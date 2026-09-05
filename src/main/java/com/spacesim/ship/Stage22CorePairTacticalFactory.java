package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.Stage22CorePairExperimentProtocol.Permutation;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipProtectionCatalog;
import com.spacesim.content.ship.Stage22CorePairProtectionCatalogLoader;
import com.spacesim.content.weapon.Stage22CorePairWeaponRuntimeCatalogLoader;
import com.spacesim.content.weapon.Stage22CorePairWeaponRuntimeCatalogLoader.RuntimeContent;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.ImportedCombatantState;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipyardEngineeringService.MaintenanceState;
import com.spacesim.ship.WeaponLoadoutState.FeedBinding;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * M22.6 exact-content entry point into the existing Stage-19 tactical runtime.
 *
 * <p>The factory authors scenario initial state only: exact fit IDs, equal physical stores, stable
 * identities and mirrored geometry. It creates no combat statistics and applies no faction-name
 * modifier. The returned runtime is the ordinary Stage-19 control/weapon/protection chain consuming
 * one combined Stage-22 engineering universe.</p>
 */
public final class Stage22CorePairTacticalFactory {
    /** Stable Empire combatant identity used by deterministic M22.6 paired runs. */
    public static final long EMPIRE_ENTITY_ID = 226_101L;
    /** Stable Industrial Union combatant identity used by deterministic M22.6 paired runs. */
    public static final long UNION_ENTITY_ID = 226_201L;
    /** Empire equal-role destroyer fit. */
    public static final String EMPIRE_DESTROYER_FIT = "fit.empire.destroyer.screen_v1";
    /** Industrial Union equal-role destroyer fit. */
    public static final String UNION_DESTROYER_FIT = "fit.industrial_union.destroyer.line_v1";

    private static final String EMPIRE_AMMUNITION_ID = "ammo.empire_axial_dart_150kg_v1";
    private static final String UNION_AMMUNITION_ID = "ammo.industrial_union_dart_140kg_v1";
    private static final double EMPIRE_ROUND_MASS_KG = 150d;
    private static final double UNION_ROUND_MASS_KG = 140d;
    private static final long STARTING_ROUNDS = 120L;
    private static final double STARTING_REACTION_MASS_KG = 1_000_000d;
    private static final double LEFT_X_M = 250d;
    private static final double RIGHT_X_M = 1_650d;
    private static final double CENTER_Y_M = 700d;

    private Stage22CorePairTacticalFactory() {
        throw new AssertionError("utility class");
    }

    /**
     * Creates one fresh equal-role destroyer duel using the requested mirrored assignment.
     *
     * @param permutation default or mirrored side/geometry assignment
     * @return fresh Stage-19 weapon runtime plus immutable core-content references
     */
    public static Duel createDestroyerDuel(Permutation permutation) {
        Permutation checked = Objects.requireNonNull(permutation, "permutation");
        RuntimeContent content = Stage22CorePairWeaponRuntimeCatalogLoader.loadCombined();
        ShipProtectionCatalog protection = Stage22CorePairProtectionCatalogLoader.project(content.engineering());

        boolean mirrored = checked == Permutation.MIRRORED;
        ImportedCombatantState empire = importedCombatant(
                content.engineering(),
                protection,
                EMPIRE_ENTITY_ID,
                mirrored ? Side.BETA : Side.ALPHA,
                EMPIRE_DESTROYER_FIT,
                EMPIRE_AMMUNITION_ID,
                EMPIRE_ROUND_MASS_KG,
                mirrored ? RIGHT_X_M : LEFT_X_M);
        ImportedCombatantState union = importedCombatant(
                content.engineering(),
                protection,
                UNION_ENTITY_ID,
                mirrored ? Side.ALPHA : Side.BETA,
                UNION_DESTROYER_FIT,
                UNION_AMMUNITION_ID,
                UNION_ROUND_MASS_KG,
                mirrored ? LEFT_X_M : RIGHT_X_M);

        LiveTacticalBattleRuntimeState battle = LiveTacticalBattleRuntimeState.importExact(
                List.of(empire, union),
                content.engineering(),
                protection);
        LiveTacticalBattleControlRuntime control = new LiveTacticalBattleControlRuntime(battle);
        LiveTacticalBattleWeaponRuntime weapons = new LiveTacticalBattleWeaponRuntime(
                control,
                content.ammunition(),
                content.launchers());
        return new Duel(checked, content, protection, weapons);
    }

    private static ImportedCombatantState importedCombatant(
            ShipEngineeringCatalog engineering,
            ShipProtectionCatalog protection,
            long entityId,
            Side side,
            String fitId,
            String ammunitionId,
            double roundMassKg,
            double xM) {
        ShipEngineeringCatalog.DemonstratorFitDefinition definition = engineering.findDemonstratorFit(fitId);
        if (definition == null) {
            throw new IllegalStateException("Missing M22.6 tactical fit: " + fitId);
        }
        InstalledFit fit = InstalledFit.fromDemonstrator(definition);
        HullDefinition hull = engineering.findHull(fit.hullId());
        if (hull == null) {
            throw new IllegalStateException("M22.6 tactical fit references missing hull: " + fitId);
        }
        ShipProtectionCatalog.HullDamageLayout layout = protection.findHullDamageLayout(hull.id());
        if (layout == null) {
            throw new IllegalStateException("M22.6 tactical hull lacks protection layout: " + hull.id());
        }

        ConsumableState consumables = new ConsumableState(
                0d,
                0d,
                0d,
                0d,
                List.of(
                        new ConsumableLoad(
                                "core_drive",
                                "propellant_feed",
                                InterfaceKind.REACTION_MASS,
                                STARTING_REACTION_MASS_KG,
                                STARTING_REACTION_MASS_KG,
                                0L),
                        new ConsumableLoad(
                                "weapon_primary",
                                "kinetic_feed",
                                InterfaceKind.AMMUNITION,
                                STARTING_ROUNDS,
                                STARTING_ROUNDS * roundMassKg,
                                STARTING_ROUNDS)));
        ShipDamageRuntime.Snapshot damage = ShipDamageRuntime.Snapshot.pristine(hull, layout);
        ShipEngineeringRuntime engineeringRuntime = new ShipEngineeringRuntime(engineering);
        ShipEngineeringRuntime.RuntimeState operating = engineeringRuntime.initialize(
                fit, consumables, damage.moduleDamage());
        ShipEngineeringState.DerivedShipState derived = new DerivedShipCalculator(engineering).derive(
                hull, fit, consumables, damage.moduleDamage());
        TreeMap<String, ShieldFieldRuntime.State> shields = new TreeMap<>();
        ShieldFieldRuntime shieldRuntime = new ShieldFieldRuntime();
        for (ShipShieldEngineeringAdapter.FittedShield shield : new ShipShieldEngineeringAdapter().derive(derived)) {
            shields.put(shield.mountId(), shield.chargedState(shieldRuntime));
        }
        if (shields.isEmpty()) {
            throw new IllegalStateException("M22.6 equal-role destroyer must expose its authored shield: " + fitId);
        }
        ShipInstanceRuntimeState instance = new ShipInstanceRuntimeState(
                damage,
                shields,
                new MaintenanceState(Map.of()),
                new WeaponLoadoutState(List.of(new FeedBinding(
                        "weapon_primary", "kinetic_feed", ammunitionId))),
                WeaponMountRuntime.RuntimeState.empty());
        EngineeringComponent component = new EngineeringComponent(fit, operating, instance);
        return new ImportedCombatantState(entityId, side, component, xM, CENTER_Y_M, 0d, 0d);
    }

    /**
     * One fresh deterministic paired tactical runtime.
     *
     * @param permutation mirrored assignment used to create the scenario
     * @param content combined Stage-22 engineering/weapon content
     * @param protection projected common Stage-17.5F protection content
     * @param weapons ordinary Stage-19 physical weapon/control/protection runtime
     */
    public record Duel(
            Permutation permutation,
            RuntimeContent content,
            ShipProtectionCatalog protection,
            LiveTacticalBattleWeaponRuntime weapons) {
        /**
         * Validates immutable duel references.
         *
         * @param permutation mirrored assignment used to create the scenario
         * @param content combined Stage-22 engineering/weapon content
         * @param protection projected common Stage-17.5F protection content
         * @param weapons ordinary Stage-19 physical weapon/control/protection runtime
         */
        public Duel {
            Objects.requireNonNull(permutation, "permutation");
            Objects.requireNonNull(content, "content");
            Objects.requireNonNull(protection, "protection");
            Objects.requireNonNull(weapons, "weapons");
        }
    }
}
