package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.Stage22CorePairExperimentProtocol.Permutation;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.Stage22CorePairCommandNetworkProjection;
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
    /** Stable Empire command-network wingman identity. */
    public static final long EMPIRE_COMMAND_WINGMAN_ID = 226_102L;
    /** Stable Industrial Union command-network wingman identity. */
    public static final long UNION_COMMAND_WINGMAN_ID = 226_202L;
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
    private static final double WINGMAN_Y_OFFSET_M = 180d;

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
                mirrored ? RIGHT_X_M : LEFT_X_M,
                CENTER_Y_M,
                true);
        ImportedCombatantState union = importedCombatant(
                content.engineering(),
                protection,
                UNION_ENTITY_ID,
                mirrored ? Side.ALPHA : Side.BETA,
                UNION_DESTROYER_FIT,
                UNION_AMMUNITION_ID,
                UNION_ROUND_MASS_KG,
                mirrored ? LEFT_X_M : RIGHT_X_M,
                CENTER_Y_M,
                true);

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

    /**
     * Creates a four-ship exact-content command-network skirmish for B11 degradation evidence.
     *
     * <p>Each side receives two ordinary command-network destroyer variants. The variant physically
     * replaces the authored {@code utility_defense} shield with the common fleet datalink, so this
     * scenario contains no synthetic command aura and no free defensive fallback. The seed only
     * chooses a small deterministic vertical geometry offset; it does not modify gameplay randomness
     * or faction capability. Mirroring swaps the complete side/topology assignment while preserving
     * faction identities.</p>
     *
     * @param permutation default or mirrored side/topology assignment
     * @param seed paired experiment seed used only for deterministic starting geometry
     * @return fresh ordinary Stage-19 control runtime over exact Stage-22 command variants
     */
    public static CommandNetworkSkirmish createCommandNetworkSkirmish(Permutation permutation, long seed) {
        Permutation checked = Objects.requireNonNull(permutation, "permutation");
        if (seed < 0L) {
            throw new IllegalArgumentException("seed must be non-negative");
        }
        RuntimeContent content = Stage22CorePairWeaponRuntimeCatalogLoader.loadCombined();
        ShipProtectionCatalog protection = Stage22CorePairProtectionCatalogLoader.project(content.engineering());
        boolean mirrored = checked == Permutation.MIRRORED;
        double seedOffsetM = (Math.floorMod(seed, 17L) - 8L) * 4d;
        double primaryY = CENTER_Y_M + seedOffsetM;
        double wingmanY = primaryY + WINGMAN_Y_OFFSET_M;

        ImportedCombatantState empirePrimary = importedCombatant(
                content.engineering(),
                protection,
                EMPIRE_ENTITY_ID,
                mirrored ? Side.BETA : Side.ALPHA,
                Stage22CorePairCommandNetworkProjection.EMPIRE_DESTROYER_COMMAND_FIT,
                EMPIRE_AMMUNITION_ID,
                EMPIRE_ROUND_MASS_KG,
                mirrored ? RIGHT_X_M : LEFT_X_M,
                primaryY,
                false);
        ImportedCombatantState empireWingman = importedCombatant(
                content.engineering(),
                protection,
                EMPIRE_COMMAND_WINGMAN_ID,
                mirrored ? Side.BETA : Side.ALPHA,
                Stage22CorePairCommandNetworkProjection.EMPIRE_DESTROYER_COMMAND_FIT,
                EMPIRE_AMMUNITION_ID,
                EMPIRE_ROUND_MASS_KG,
                mirrored ? RIGHT_X_M - 100d : LEFT_X_M + 100d,
                wingmanY,
                false);
        ImportedCombatantState unionPrimary = importedCombatant(
                content.engineering(),
                protection,
                UNION_ENTITY_ID,
                mirrored ? Side.ALPHA : Side.BETA,
                Stage22CorePairCommandNetworkProjection.UNION_DESTROYER_COMMAND_FIT,
                UNION_AMMUNITION_ID,
                UNION_ROUND_MASS_KG,
                mirrored ? LEFT_X_M : RIGHT_X_M,
                primaryY,
                false);
        ImportedCombatantState unionWingman = importedCombatant(
                content.engineering(),
                protection,
                UNION_COMMAND_WINGMAN_ID,
                mirrored ? Side.ALPHA : Side.BETA,
                Stage22CorePairCommandNetworkProjection.UNION_DESTROYER_COMMAND_FIT,
                UNION_AMMUNITION_ID,
                UNION_ROUND_MASS_KG,
                mirrored ? LEFT_X_M + 100d : RIGHT_X_M - 100d,
                wingmanY,
                false);

        LiveTacticalBattleRuntimeState battle = LiveTacticalBattleRuntimeState.importExact(
                List.of(empirePrimary, empireWingman, unionPrimary, unionWingman),
                content.engineering(),
                protection);
        return new CommandNetworkSkirmish(
                checked,
                seed,
                content,
                protection,
                new LiveTacticalBattleControlRuntime(battle));
    }

    private static ImportedCombatantState importedCombatant(
            ShipEngineeringCatalog engineering,
            ShipProtectionCatalog protection,
            long entityId,
            Side side,
            String fitId,
            String ammunitionId,
            double roundMassKg,
            double xM,
            double yM,
            boolean requireShield) {
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
        if (requireShield && shields.isEmpty()) {
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
        return new ImportedCombatantState(entityId, side, component, xM, yM, 0d, 0d);
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

    /**
     * Fresh B11 exact-command-network initial state.
     *
     * @param permutation mirrored assignment used to create the scenario
     * @param seed paired experiment seed used only for deterministic starting geometry
     * @param content combined Stage-22 engineering/weapon content
     * @param protection projected common protection content
     * @param control ordinary Stage-19 actor-bounded control runtime
     */
    public record CommandNetworkSkirmish(
            Permutation permutation,
            long seed,
            RuntimeContent content,
            ShipProtectionCatalog protection,
            LiveTacticalBattleControlRuntime control) {
        /** Validates immutable command-network skirmish references. */
        public CommandNetworkSkirmish {
            Objects.requireNonNull(permutation, "permutation");
            if (seed < 0L) {
                throw new IllegalArgumentException("seed must be non-negative");
            }
            Objects.requireNonNull(content, "content");
            Objects.requireNonNull(protection, "protection");
            Objects.requireNonNull(control, "control");
        }
    }
}
