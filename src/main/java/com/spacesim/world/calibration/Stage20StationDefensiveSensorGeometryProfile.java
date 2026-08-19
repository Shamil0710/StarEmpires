package com.spacesim.world.calibration;

import com.spacesim.ship.SensorDefinition.Mode;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;
import com.spacesim.world.calibration.Stage20SensorCalibrationCalculator.ThresholdDistances;
import com.spacesim.world.calibration.Stage20SensorTargetClassCoverageProfile.TargetClass;
import com.spacesim.world.calibration.Stage20SensorTargetClassCoverageProfile.TargetObservationSample;
import com.spacesim.world.calibration.Stage20WeaponTargetClassCoverageProfile.KineticP50Sample;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Versioned Stage-20A station defensive/sensor spatial-geometry authority.
 *
 * <p>This profile does not invent a station-only sensor equation, weapon equation or range
 * multiplier. Sensor warning/track geometry reuses the production escort observation runtime as a
 * conservative reference floor. Defensive exclusion geometry reuses accepted v0.3 kinetic P50
 * interaction rows as a separate provisional world-placement reference. Those two references are
 * intentionally not asserted to form one end-to-end station firing solution: final station sensors,
 * weapon mounts and fire-control coupling belong to Stage 22 content promotion.</p>
 *
 * <p>The only new Stage-20 design decision is which accepted reference tier each Stage-18 station
 * archetype consumes. Stage 20B may use the defensive exclusion reference for placement/arrival
 * geometry, but must not reinterpret it as a hard weapon range or as proof of a production station
 * combat capability.</p>
 *
 * @param version stable profile version
 * @param authority authority of the station capability-tier assignment
 * @param sensorReferenceProfileVersion exact sensor-coverage profile consumed
 * @param weaponReferenceProfileVersion exact weapon-target profile consumed
 * @param stage22ReviewRequired whether final content promotion must review these station assignments
 * @param stations explicit defensive/sensor geometry for all Stage-18 station archetypes
 */
public record Stage20StationDefensiveSensorGeometryProfile(
        String version,
        CalibrationAuthority authority,
        String sensorReferenceProfileVersion,
        String weaponReferenceProfileVersion,
        boolean stage22ReviewRequired,
        List<StationDefensiveSensorGeometry> stations) {
    /** Current station defensive/sensor geometry profile version. */
    public static final String CURRENT_VERSION = "stage20a.station-defensive-sensor-geometry.v1";

    private static final Set<String> REQUIRED_STATION_IDS = Set.of(
            "station.infrastructure.mining_outpost",
            "station.infrastructure.volatile_depot",
            "station.infrastructure.refinery_complex",
            "station.infrastructure.industrial_station",
            "station.infrastructure.high_tech_hub",
            "station.infrastructure.trade_logistics_hub",
            "station.infrastructure.naval_ordnance_depot",
            "station.infrastructure.frontier_multipurpose");

    /** Provisional station-security tier expressed only as a reference selection. */
    public enum SecurityTier {
        /** Basic local security using the accepted corvette direct-fire interaction reference. */ BASIC_SECURITY,
        /** Hardened civil/industrial security using the accepted frigate interaction reference. */ HARDENED_SECURITY,
        /** Strategic naval site using the accepted capital interaction reference. */ NAVAL_FORTIFIED
    }

    /**
     * Validates and freezes one deterministic station capability profile.
     *
     * @param version stable profile version
     * @param authority authority of the station capability-tier assignment
     * @param sensorReferenceProfileVersion exact sensor-coverage profile consumed
     * @param weaponReferenceProfileVersion exact weapon-target profile consumed
     * @param stage22ReviewRequired whether final content promotion must review these assignments
     * @param stations explicit defensive/sensor geometry for all station archetypes
     */
    public Stage20StationDefensiveSensorGeometryProfile {
        requireText(version, "version");
        Objects.requireNonNull(authority, "authority");
        requireText(sensorReferenceProfileVersion, "sensorReferenceProfileVersion");
        requireText(weaponReferenceProfileVersion, "weaponReferenceProfileVersion");
        Objects.requireNonNull(stations, "stations");
        ArrayList<StationDefensiveSensorGeometry> copy = new ArrayList<>(stations);
        if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("stations must be non-empty and contain no nulls");
        }
        copy.sort(Comparator.comparing(StationDefensiveSensorGeometry::stationArchetypeId));
        if (copy.stream().map(StationDefensiveSensorGeometry::stationArchetypeId).distinct().count() != copy.size()) {
            throw new IllegalArgumentException("station archetype IDs must be unique");
        }
        stations = List.copyOf(copy);
        if (authority == CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE && !stage22ReviewRequired) {
            throw new IllegalArgumentException("provisional station capability assignments require Stage-22 review");
        }
    }

    /**
     * Derives the current station capability geometry exclusively from accepted Stage-20 references.
     *
     * @return deterministic current station defensive/sensor profile
     */
    public static Stage20StationDefensiveSensorGeometryProfile deriveCurrent() {
        Stage20SensorTargetClassCoverageProfile sensors = Stage20SensorTargetClassCoverageProfile.deriveCurrent();
        Stage20WeaponTargetClassCoverageProfile weapons = Stage20WeaponTargetClassCoverageProfile.deriveCurrent();

        return new Stage20StationDefensiveSensorGeometryProfile(
                CURRENT_VERSION,
                CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE,
                sensors.version(),
                weapons.version(),
                true,
                List.of(
                        deriveStation(
                                "station.infrastructure.mining_outpost",
                                SecurityTier.BASIC_SECURITY,
                                TargetClass.TORPEDO_CORVETTE,
                                Stage20WeaponTargetClassCoverageProfile.TargetClass.CORVETTE,
                                sensors,
                                weapons),
                        deriveStation(
                                "station.infrastructure.volatile_depot",
                                SecurityTier.BASIC_SECURITY,
                                TargetClass.TORPEDO_CORVETTE,
                                Stage20WeaponTargetClassCoverageProfile.TargetClass.CORVETTE,
                                sensors,
                                weapons),
                        deriveStation(
                                "station.infrastructure.refinery_complex",
                                SecurityTier.HARDENED_SECURITY,
                                TargetClass.TORPEDO_CORVETTE,
                                Stage20WeaponTargetClassCoverageProfile.TargetClass.FRIGATE,
                                sensors,
                                weapons),
                        deriveStation(
                                "station.infrastructure.industrial_station",
                                SecurityTier.HARDENED_SECURITY,
                                TargetClass.TORPEDO_CORVETTE,
                                Stage20WeaponTargetClassCoverageProfile.TargetClass.FRIGATE,
                                sensors,
                                weapons),
                        deriveStation(
                                "station.infrastructure.high_tech_hub",
                                SecurityTier.HARDENED_SECURITY,
                                TargetClass.TORPEDO_CORVETTE,
                                Stage20WeaponTargetClassCoverageProfile.TargetClass.FRIGATE,
                                sensors,
                                weapons),
                        deriveStation(
                                "station.infrastructure.trade_logistics_hub",
                                SecurityTier.HARDENED_SECURITY,
                                TargetClass.TORPEDO_CORVETTE,
                                Stage20WeaponTargetClassCoverageProfile.TargetClass.FRIGATE,
                                sensors,
                                weapons),
                        deriveStation(
                                "station.infrastructure.naval_ordnance_depot",
                                SecurityTier.NAVAL_FORTIFIED,
                                TargetClass.BATTLESHIP,
                                Stage20WeaponTargetClassCoverageProfile.TargetClass.BATTLESHIP,
                                sensors,
                                weapons),
                        deriveStation(
                                "station.infrastructure.frontier_multipurpose",
                                SecurityTier.HARDENED_SECURITY,
                                TargetClass.TORPEDO_CORVETTE,
                                Stage20WeaponTargetClassCoverageProfile.TargetClass.FRIGATE,
                                sensors,
                                weapons)));
    }

    /**
     * Returns whether every required station has usable sensor geometry and an explicit defensive
     * exclusion reference with visible provisional authority.
     *
     * <p>Closure is a world-geometry contract, not an end-to-end combat-capability claim. In
     * particular, an escort-derived fire-control reference is not required to reach the independent
     * P50-derived defensive exclusion reference. Final station fire-control integration remains a
     * Stage-22 content responsibility.</p>
     *
     * @return true when this profile closes the Stage-20B station defensive/sensor geometry entry requirement
     */
    public boolean closesStage20BEntryCoverage() {
        Set<String> actualIds = stations.stream()
                .map(StationDefensiveSensorGeometry::stationArchetypeId)
                .collect(Collectors.toSet());
        return authority == CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE
                && stage22ReviewRequired
                && sensorReferenceProfileVersion.equals(Stage20SensorTargetClassCoverageProfile.CURRENT_VERSION)
                && weaponReferenceProfileVersion.equals(Stage20WeaponTargetClassCoverageProfile.CURRENT_VERSION)
                && actualIds.equals(REQUIRED_STATION_IDS)
                && stations.stream().allMatch(StationDefensiveSensorGeometry::sensorGeometryNested)
                && stations.stream().allMatch(value -> value.defensiveExclusionReferenceM() > 0d);
    }

    /**
     * Returns the explicit station capability row for one Stage-18 archetype.
     *
     * @param stationArchetypeId stable Stage-18 station archetype ID
     * @return matching station defensive/sensor geometry
     */
    public StationDefensiveSensorGeometry station(String stationArchetypeId) {
        requireText(stationArchetypeId, "stationArchetypeId");
        return stations.stream()
                .filter(value -> stationArchetypeId.equals(value.stationArchetypeId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No Stage-20 station defensive/sensor geometry for " + stationArchetypeId));
    }

    private static StationDefensiveSensorGeometry deriveStation(
            String stationId,
            SecurityTier tier,
            TargetClass sensorTarget,
            Stage20WeaponTargetClassCoverageProfile.TargetClass defenseTarget,
            Stage20SensorTargetClassCoverageProfile sensors,
            Stage20WeaponTargetClassCoverageProfile weapons) {
        TargetObservationSample passive = sensors.sample(sensorTarget, Mode.PASSIVE_THERMAL);
        TargetObservationSample active = sensors.sample(sensorTarget, Mode.ACTIVE_RADAR);
        ThresholdDistances passiveThresholds = passive.thresholds();
        ThresholdDistances activeThresholds = active.thresholds();
        double passiveDetection = passiveThresholds.detectedMaxDistanceM().orElseThrow(() ->
                new IllegalStateException("Station sensor reference has no passive detection envelope: " + sensorTarget));
        double activeDetection = activeThresholds.detectedMaxDistanceM().orElseThrow(() ->
                new IllegalStateException("Station sensor reference has no active detection envelope: " + sensorTarget));
        double activeClassification = activeThresholds.classifiedMaxDistanceM().orElseThrow(() ->
                new IllegalStateException("Station sensor reference has no active classification envelope: " + sensorTarget));
        double activeTrack = activeThresholds.trackedMaxDistanceM().orElseThrow(() ->
                new IllegalStateException("Station sensor reference has no active track envelope: " + sensorTarget));
        double activeFireControl = activeThresholds.fireControlMaxDistanceM().orElseThrow(() ->
                new IllegalStateException("Station sensor reference has no active fire-control envelope: " + sensorTarget));

        KineticP50Sample defense = weapons.kineticP50Samples().stream()
                .filter(value -> value.targetClass() == defenseTarget)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Station defense reference has no authored P50 row: " + defenseTarget));

        return new StationDefensiveSensorGeometry(
                stationId,
                tier,
                sensorTarget,
                "Stage20SensorTargetClassCoverageProfile:" + sensors.version(),
                passiveDetection,
                activeDetection,
                activeClassification,
                activeTrack,
                activeFireControl,
                defenseTarget,
                defense.weaponId(),
                "Stage20WeaponTargetClassCoverageProfile:" + weapons.version()
                        + ":" + Stage20WeaponTargetClassCoverageProfile.BENCHMARK_PROVENANCE,
                defense.p50RangeM());
    }

    /**
     * One station-specific spatial capability row built from independent accepted sensor and weapon references.
     *
     * @param stationArchetypeId stable Stage-18 station archetype ID
     * @param securityTier provisional security tier
     * @param sensorReferenceTarget representative target used to measure sensor geometry
     * @param sensorProvenance exact sensor-profile provenance
     * @param passiveDetectionWarningM passive-thermal detection warning envelope
     * @param activeDetectionWarningM active-radar detection envelope
     * @param activeClassificationM active-radar classification envelope
     * @param activeTrackM active-radar tracked envelope
     * @param activeFireControlM active-radar fire-control reference envelope
     * @param defenseReferenceTarget authored target class behind the exclusion reference
     * @param defenseReferenceWeaponId authored benchmark weapon behind the exclusion reference
     * @param defenseProvenance exact accepted weapon benchmark provenance
     * @param defensiveExclusionReferenceM accepted P50 interaction scale used only as provisional exclusion geometry
     */
    public record StationDefensiveSensorGeometry(
            String stationArchetypeId,
            SecurityTier securityTier,
            TargetClass sensorReferenceTarget,
            String sensorProvenance,
            double passiveDetectionWarningM,
            double activeDetectionWarningM,
            double activeClassificationM,
            double activeTrackM,
            double activeFireControlM,
            Stage20WeaponTargetClassCoverageProfile.TargetClass defenseReferenceTarget,
            String defenseReferenceWeaponId,
            String defenseProvenance,
            double defensiveExclusionReferenceM) {
        /**
         * Validates one station-specific spatial-reference row.
         *
         * @param stationArchetypeId stable Stage-18 station archetype ID
         * @param securityTier provisional security tier
         * @param sensorReferenceTarget representative sensor target
         * @param sensorProvenance exact sensor provenance
         * @param passiveDetectionWarningM passive detection envelope
         * @param activeDetectionWarningM active detection envelope
         * @param activeClassificationM active classification envelope
         * @param activeTrackM active track envelope
         * @param activeFireControlM active fire-control reference envelope
         * @param defenseReferenceTarget representative weapon target
         * @param defenseReferenceWeaponId accepted benchmark weapon ID
         * @param defenseProvenance exact weapon provenance
         * @param defensiveExclusionReferenceM accepted provisional exclusion reference
         */
        public StationDefensiveSensorGeometry {
            requireText(stationArchetypeId, "stationArchetypeId");
            Objects.requireNonNull(securityTier, "securityTier");
            Objects.requireNonNull(sensorReferenceTarget, "sensorReferenceTarget");
            requireText(sensorProvenance, "sensorProvenance");
            requirePositive(passiveDetectionWarningM, "passiveDetectionWarningM");
            requirePositive(activeDetectionWarningM, "activeDetectionWarningM");
            requirePositive(activeClassificationM, "activeClassificationM");
            requirePositive(activeTrackM, "activeTrackM");
            requirePositive(activeFireControlM, "activeFireControlM");
            Objects.requireNonNull(defenseReferenceTarget, "defenseReferenceTarget");
            requireText(defenseReferenceWeaponId, "defenseReferenceWeaponId");
            requireText(defenseProvenance, "defenseProvenance");
            requirePositive(defensiveExclusionReferenceM, "defensiveExclusionReferenceM");
            if (!sensorGeometryNestedValues(
                    activeDetectionWarningM,
                    activeClassificationM,
                    activeTrackM,
                    activeFireControlM)) {
                throw new IllegalArgumentException("station sensor evidence states must remain nested");
            }
        }

        /**
         * Returns whether stronger active-sensor information states remain inside weaker states.
         *
         * <p>This deliberately does not compare the independent defensive exclusion reference to the
         * escort-derived sensor floor; such a comparison would falsely claim an authored station
         * fire-control chain that does not yet exist.</p>
         *
         * @return true when active sensor evidence-state geometry is physically nested
         */
        public boolean sensorGeometryNested() {
            return sensorGeometryNestedValues(
                    activeDetectionWarningM,
                    activeClassificationM,
                    activeTrackM,
                    activeFireControlM);
        }
    }

    private static boolean sensorGeometryNestedValues(
            double detection,
            double classification,
            double track,
            double fireControl) {
        return detection >= classification
                && classification >= track
                && track >= fireControl;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static void requirePositive(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }
}
