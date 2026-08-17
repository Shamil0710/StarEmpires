package com.spacesim.combat.acceptance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Immutable data-driven Stage-17.5I fleet/scenario manifest.
 *
 * <p>The manifest contains only acceptance composition and initial-condition vocabulary. It does not
 * contain damage, hit-chance, DPS, class bonuses or other hidden combat authority. Every ship entry
 * points to an ordinary production-valid engineering fit and every later scenario runner must derive
 * capability from the same Stage-17.5 runtime used by normal ships.</p>
 */
public final class Stage175ICombatTestManifest {
    private final int schemaVersion;
    private final String contentStatus;
    private final boolean stage22ReviewRequired;
    private final List<FleetDefinition> fleets;
    private final List<MatchupDefinition> matchups;
    private final List<VariationDefinition> variations;
    private final String fingerprint;

    Stage175ICombatTestManifest(
            int schemaVersion,
            String contentStatus,
            boolean stage22ReviewRequired,
            List<FleetDefinition> fleets,
            List<MatchupDefinition> matchups,
            List<VariationDefinition> variations) {
        this.schemaVersion = schemaVersion;
        this.contentStatus = requireNonBlank(contentStatus, "contentStatus");
        this.stage22ReviewRequired = stage22ReviewRequired;
        this.fleets = sortedCopy(fleets, Comparator.comparing(FleetDefinition::id));
        this.matchups = sortedCopy(matchups, Comparator.comparing(MatchupDefinition::id));
        this.variations = sortedCopy(variations, Comparator.comparing(VariationDefinition::id));
        this.fingerprint = computeFingerprint();
    }

    /** @return version of the acceptance-manifest schema */
    public int schemaVersion() {
        return schemaVersion;
    }

    /** @return explicit provisional-content status token */
    public String contentStatus() {
        return contentStatus;
    }

    /** @return whether Stage 22 must review/re-author or explicitly promote this content */
    public boolean stage22ReviewRequired() {
        return stage22ReviewRequired;
    }

    /** @return deterministic immutable representative fleet definitions */
    public List<FleetDefinition> fleets() {
        return fleets;
    }

    /** @return deterministic immutable required matchup definitions */
    public List<MatchupDefinition> matchups() {
        return matchups;
    }

    /** @return deterministic immutable scenario-variation definitions */
    public List<VariationDefinition> variations() {
        return variations;
    }

    /** @return lowercase SHA-256 semantic fingerprint of the manifest */
    public String fingerprint() {
        return fingerprint;
    }

    /**
     * Finds a fleet by stable acceptance ID.
     *
     * @param id fleet ID
     * @return fleet definition or {@code null}
     */
    public FleetDefinition findFleet(String id) {
        return fleets.stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
    }

    /**
     * Finds a variation by stable acceptance ID.
     *
     * @param id variation ID
     * @return variation definition or {@code null}
     */
    public VariationDefinition findVariation(String id) {
        return variations.stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
    }

    /** Representative fleet doctrine label; the enum grants no physical capability. */
    public enum Doctrine {
        /** Armor and kinetic launcher line. */ KINETIC_LINE,
        /** Guided-weapon saturation formation. */ MISSILE_STRIKE,
        /** High-thrust beam-oriented formation. */ HIGH_MOBILITY_BEAM,
        /** Point-defense, shield and electronic-warfare formation. */ DEFENSIVE_EW,
        /** Mixed control formation used as a neutral comparison. */ BALANCED_CONTROL
    }

    /** Initial information quality used to seed an acceptance scenario without omniscience. */
    public enum InformationQuality {
        /** Contact exists but does not begin with a complete solution. */ DETECTED,
        /** Position-known track exists but may still be insufficient for some weapons. */ TRACKED,
        /** Fire-control-quality position/covariance information is available at scenario start. */ FIRE_CONTROL
    }

    /**
     * Number of identical production fits assigned to one representative fleet.
     *
     * @param fitId stable ordinary engineering demonstrator-fit ID
     * @param count positive number of physical ships using the fit
     */
    public record ShipEntry(String fitId, int count) {
        /**
         * Validates one immutable fleet composition row.
         *
         * @param fitId stable ordinary engineering demonstrator-fit ID
         * @param count positive number of physical ships using the fit
         */
        public ShipEntry {
            requireNonBlank(fitId, "fitId");
            if (count <= 0) {
                throw new IllegalArgumentException("count must be positive");
            }
        }
    }

    /**
     * Data-driven representative fleet composition.
     *
     * @param id stable acceptance fleet ID
     * @param doctrine descriptive doctrine label only
     * @param ships ordinary production-fit composition
     */
    public record FleetDefinition(String id, Doctrine doctrine, List<ShipEntry> ships) {
        /**
         * Validates and freezes one representative fleet definition.
         *
         * @param id stable acceptance fleet ID
         * @param doctrine descriptive doctrine label only
         * @param ships ordinary production-fit composition
         */
        public FleetDefinition {
            requireNonBlank(id, "id");
            Objects.requireNonNull(doctrine, "doctrine");
            ships = List.copyOf(Objects.requireNonNull(ships, "ships"));
            if (ships.isEmpty() || ships.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("ships must be non-empty and contain no null");
            }
        }

        /** @return total physical ship count in the fleet */
        public int totalShipCount() {
            return ships.stream().mapToInt(ShipEntry::count).sum();
        }
    }

    /**
     * Required pairwise fleet matchup.
     *
     * @param id stable matchup ID
     * @param fleetAId first representative fleet ID
     * @param fleetBId second representative fleet ID
     */
    public record MatchupDefinition(String id, String fleetAId, String fleetBId) {
        /**
         * Validates one immutable matchup.
         *
         * @param id stable matchup ID
         * @param fleetAId first representative fleet ID
         * @param fleetBId second representative fleet ID
         */
        public MatchupDefinition {
            requireNonBlank(id, "id");
            requireNonBlank(fleetAId, "fleetAId");
            requireNonBlank(fleetBId, "fleetBId");
        }
    }

    /**
     * Deterministic initial-condition variation applied to the same physical scenario runner.
     *
     * @param id stable variation ID
     * @param initialSeparationM initial fleet-centroid separation in meters
     * @param formationSpacingM nominal same-side ship spacing in meters
     * @param ammunitionLoadFraction fraction of authored physical ammunition capacity initially loaded
     * @param preDamageIntegrity initial common subsystem-integrity multiplier used for pre-damage cases
     * @param initialThermalLoadFraction fraction of local thermal capacity initially occupied
     * @param informationQuality initial information state
     */
    public record VariationDefinition(
            String id,
            double initialSeparationM,
            double formationSpacingM,
            double ammunitionLoadFraction,
            double preDamageIntegrity,
            double initialThermalLoadFraction,
            InformationQuality informationQuality) {
        /**
         * Validates one immutable deterministic scenario variation.
         *
         * @param id stable variation ID
         * @param initialSeparationM initial fleet-centroid separation in meters
         * @param formationSpacingM nominal same-side ship spacing in meters
         * @param ammunitionLoadFraction physical ammunition fill fraction in [0,1]
         * @param preDamageIntegrity initial integrity in (0,1]
         * @param initialThermalLoadFraction local thermal fill fraction in [0,1]
         * @param informationQuality initial information state
         */
        public VariationDefinition {
            requireNonBlank(id, "id");
            requirePositiveFinite(initialSeparationM, "initialSeparationM");
            requirePositiveFinite(formationSpacingM, "formationSpacingM");
            requireUnitInterval(ammunitionLoadFraction, "ammunitionLoadFraction", true);
            requireUnitInterval(preDamageIntegrity, "preDamageIntegrity", false);
            requireUnitInterval(initialThermalLoadFraction, "initialThermalLoadFraction", true);
            Objects.requireNonNull(informationQuality, "informationQuality");
        }
    }

    private String computeFingerprint() {
        StringBuilder canonical = new StringBuilder();
        canonical.append(schemaVersion).append('|').append(contentStatus).append('|')
                .append(stage22ReviewRequired).append('\n');
        for (FleetDefinition fleet : fleets) {
            canonical.append("F|").append(fleet.id()).append('|').append(fleet.doctrine()).append('\n');
            fleet.ships().stream().sorted(Comparator.comparing(ShipEntry::fitId)).forEach(entry ->
                    canonical.append("S|").append(entry.fitId()).append('|').append(entry.count()).append('\n'));
        }
        for (MatchupDefinition matchup : matchups) {
            canonical.append("M|").append(matchup.id()).append('|').append(matchup.fleetAId())
                    .append('|').append(matchup.fleetBId()).append('\n');
        }
        for (VariationDefinition variation : variations) {
            canonical.append("V|").append(variation.id()).append('|')
                    .append(Double.toHexString(variation.initialSeparationM())).append('|')
                    .append(Double.toHexString(variation.formationSpacingM())).append('|')
                    .append(Double.toHexString(variation.ammunitionLoadFraction())).append('|')
                    .append(Double.toHexString(variation.preDamageIntegrity())).append('|')
                    .append(Double.toHexString(variation.initialThermalLoadFraction())).append('|')
                    .append(variation.informationQuality()).append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JVM", exception);
        }
    }

    private static <T> List<T> sortedCopy(List<T> source, Comparator<T> comparator) {
        List<T> copy = new ArrayList<>(Objects.requireNonNull(source, "source"));
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("source must not contain null");
        }
        copy.sort(comparator);
        return List.copyOf(copy);
    }

    private static String requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
        return value;
    }

    private static void requirePositiveFinite(double value, String label) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
    }

    private static void requireUnitInterval(double value, String label, boolean zeroAllowed) {
        if (!Double.isFinite(value) || value > 1d || value < 0d || (!zeroAllowed && value == 0d)) {
            throw new IllegalArgumentException(label + " must be inside the accepted unit interval");
        }
    }
}
