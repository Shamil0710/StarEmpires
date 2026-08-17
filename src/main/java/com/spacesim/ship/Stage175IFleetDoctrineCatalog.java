package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.WeaponLoadoutState.FeedBinding;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Physical Stage-17.5I doctrine fixtures used only by deterministic acceptance scenarios.
 *
 * <p>Doctrine IDs select authored fits and initial physical stores; they grant no numeric bonus.
 * Every performance difference is resolved later from ordinary fitted modules, physical ammunition,
 * reaction mass, damage, sensor/EW and formation state. These fixtures are content-provisional and
 * cannot silently become Stage-22 faction doctrine.</p>
 */
public final class Stage175IFleetDoctrineCatalog {
    /** Acceptance-only doctrine identity; enum values themselves carry no performance modifiers. */
    public enum DoctrineId {
        /** Kinetic line combat fixture. */ A_KINETIC_LINE,
        /** Guided missile strike fixture. */ B_MISSILE_STRIKE,
        /** High-mobility directed-energy fixture. */ C_HIGH_MOBILITY_BEAM,
        /** Defensive shield/EW fixture. */ D_DEFENSIVE_EW,
        /** Mixed balanced control fixture. */ E_BALANCED_CONTROL
    }

    /**
     * One deterministic acceptance doctrine.
     *
     * @param id fixture identity only
     * @param fitId ordinary engineering fit content ID
     * @param initialConsumables physical initial reaction mass and ammunition
     * @param weaponLoadout ammunition content identity for each occupied physical feed
     * @param defaultFleetCount default equal-count matrix size
     * @param defaultSpacingM default formation spacing in meters
     */
    public record Doctrine(
            DoctrineId id,
            String fitId,
            ConsumableState initialConsumables,
            WeaponLoadoutState weaponLoadout,
            int defaultFleetCount,
            double defaultSpacingM) {
        /** Validates one immutable acceptance fixture. */
        public Doctrine {
            Objects.requireNonNull(id, "id");
            if (fitId == null || fitId.isBlank()) {
                throw new IllegalArgumentException("fitId must be non-blank");
            }
            Objects.requireNonNull(initialConsumables, "initialConsumables");
            Objects.requireNonNull(weaponLoadout, "weaponLoadout");
            if (defaultFleetCount <= 0) {
                throw new IllegalArgumentException("defaultFleetCount must be positive");
            }
            if (!Double.isFinite(defaultSpacingM) || defaultSpacingM <= 0d) {
                throw new IllegalArgumentException("defaultSpacingM must be finite and positive");
            }
        }
    }

    private static final double REACTION_MASS_KG = 1_000_000d;
    private static final int DEFAULT_COUNT = 4;
    private static final double DEFAULT_SPACING_M = 12_000d;
    private static final Map<DoctrineId, Doctrine> DOCTRINES = build();

    private Stage175IFleetDoctrineCatalog() {
        throw new AssertionError("utility class");
    }

    /** @return deterministic doctrine fixture by acceptance identity */
    public static Doctrine get(DoctrineId id) {
        Doctrine result = DOCTRINES.get(Objects.requireNonNull(id, "id"));
        if (result == null) {
            throw new IllegalArgumentException("unknown Stage 17.5I doctrine: " + id);
        }
        return result;
    }

    /** @return immutable complete A-E acceptance doctrine set */
    public static List<Doctrine> all() {
        return List.copyOf(DOCTRINES.values());
    }

    private static Map<DoctrineId, Doctrine> build() {
        EnumMap<DoctrineId, Doctrine> values = new EnumMap<>(DoctrineId.class);
        values.put(DoctrineId.A_KINETIC_LINE, new Doctrine(
                DoctrineId.A_KINETIC_LINE,
                "fit.test_doctrine_a_kinetic_v1",
                consumables(
                        reaction("core_drive", 2_200_000d),
                        ammunition("weapon_primary", "kinetic_feed", 120, 150d),
                        ammunition("weapon_secondary", "pd_feed", 1000, 5d),
                        ammunition("weapon_defense", "pd_feed", 1000, 5d)),
                new WeaponLoadoutState(List.of(
                        new FeedBinding("weapon_primary", "kinetic_feed", "ammo.test_kinetic_dart_150kg_v1"),
                        new FeedBinding("weapon_secondary", "pd_feed", "ammo.test_pd_slug_5kg_v1"),
                        new FeedBinding("weapon_defense", "pd_feed", "ammo.test_pd_slug_5kg_v1"))),
                DEFAULT_COUNT,
                DEFAULT_SPACING_M));
        values.put(DoctrineId.B_MISSILE_STRIKE, new Doctrine(
                DoctrineId.B_MISSILE_STRIKE,
                "fit.test_doctrine_b_missile_v1",
                consumables(
                        reaction("core_drive", 2_200_000d),
                        ammunition("weapon_primary", "guided_feed", 24, 2000d),
                        ammunition("weapon_secondary", "guided_feed", 24, 2000d),
                        ammunition("weapon_defense", "pd_feed", 1000, 5d)),
                new WeaponLoadoutState(List.of(
                        new FeedBinding("weapon_primary", "guided_feed", "ammo.test_anti_ship_missile_2t_v1"),
                        new FeedBinding("weapon_secondary", "guided_feed", "ammo.test_anti_ship_missile_2t_v1"),
                        new FeedBinding("weapon_defense", "pd_feed", "ammo.test_pd_slug_5kg_v1"))),
                DEFAULT_COUNT,
                DEFAULT_SPACING_M));
        values.put(DoctrineId.C_HIGH_MOBILITY_BEAM, new Doctrine(
                DoctrineId.C_HIGH_MOBILITY_BEAM,
                "fit.test_doctrine_c_beam_v1",
                consumables(
                        reaction("core_drive", 2_600_000d),
                        ammunition("weapon_defense", "pd_feed", 1000, 5d)),
                new WeaponLoadoutState(List.of(
                        new FeedBinding("weapon_defense", "pd_feed", "ammo.test_pd_slug_5kg_v1"))),
                DEFAULT_COUNT,
                DEFAULT_SPACING_M));
        values.put(DoctrineId.D_DEFENSIVE_EW, new Doctrine(
                DoctrineId.D_DEFENSIVE_EW,
                "fit.test_doctrine_d_defensive_ew_v1",
                consumables(
                        reaction("core_drive", 2_200_000d),
                        ammunition("weapon_primary", "pd_feed", 1000, 5d),
                        ammunition("weapon_secondary", "pd_feed", 1000, 5d),
                        ammunition("weapon_defense", "pd_feed", 1000, 5d)),
                new WeaponLoadoutState(List.of(
                        new FeedBinding("weapon_primary", "pd_feed", "ammo.test_pd_slug_5kg_v1"),
                        new FeedBinding("weapon_secondary", "pd_feed", "ammo.test_pd_slug_5kg_v1"),
                        new FeedBinding("weapon_defense", "pd_feed", "ammo.test_pd_slug_5kg_v1"))),
                DEFAULT_COUNT,
                DEFAULT_SPACING_M));
        values.put(DoctrineId.E_BALANCED_CONTROL, new Doctrine(
                DoctrineId.E_BALANCED_CONTROL,
                "fit.test_doctrine_e_balanced_v1",
                consumables(
                        reaction("core_drive", 2_200_000d),
                        ammunition("weapon_primary", "kinetic_feed", 80, 150d),
                        ammunition("weapon_secondary", "guided_feed", 20, 2000d),
                        ammunition("weapon_defense", "pd_feed", 1000, 5d)),
                new WeaponLoadoutState(List.of(
                        new FeedBinding("weapon_primary", "kinetic_feed", "ammo.test_kinetic_dart_150kg_v1"),
                        new FeedBinding("weapon_secondary", "guided_feed", "ammo.test_anti_ship_missile_2t_v1"),
                        new FeedBinding("weapon_defense", "pd_feed", "ammo.test_pd_slug_5kg_v1"))),
                DEFAULT_COUNT,
                DEFAULT_SPACING_M));
        return Map.copyOf(values);
    }

    private static ConsumableState consumables(ConsumableLoad... loads) {
        return new ConsumableState(0d, 20_000d, 0d, 0d, List.of(loads));
    }

    private static ConsumableLoad reaction(String mountId, double capacityKg) {
        double loadedKg = Math.min(REACTION_MASS_KG, capacityKg);
        return new ConsumableLoad(
                mountId, "propellant_feed", InterfaceKind.REACTION_MASS, loadedKg, loadedKg, 0L);
    }

    private static ConsumableLoad ammunition(String mountId, String interfaceId, long count, double roundMassKg) {
        return new ConsumableLoad(
                mountId,
                interfaceId,
                InterfaceKind.AMMUNITION,
                count,
                count * roundMassKg,
                count);
    }
}
