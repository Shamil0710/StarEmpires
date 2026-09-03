package com.spacesim.content;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Deterministic M22.6 freeze-manifest projection over the accepted core pair.
 *
 * <p>The current projection is used to discover and then pin the exact release-candidate fingerprints.
 * It is not a gameplay manifest and cannot mutate package/profile/migration state.</p>
 */
public final class Stage22CorePairFreezeManifest {
    /** Freeze schema version. */
    public static final int SCHEMA_VERSION = 1;
    /** Semantic freeze-manifest version. */
    public static final String MANIFEST_VERSION = "stage22.core_pair_freeze_manifest.v1";

    private Stage22CorePairFreezeManifest() {
        throw new AssertionError("utility class");
    }

    /**
     * Captures the current exact semantic freeze surface.
     *
     * @return immutable deterministic freeze snapshot
     */
    public static Snapshot captureCurrent() {
        Stage22CorePairBalanceEvidence.PairEvidence evidence = Stage22CorePairBalanceEvidence.deriveCurrent();
        Stage22EmpirePackageValidator.ValidationReport empire = Stage22EmpirePackageValidator.validateDefault();
        Stage22IndustrialUnionPackageValidator.ValidationReport union =
                Stage22IndustrialUnionPackageValidator.validateDefault();
        Stage22FactionProfileCatalog empireProfiles = Stage22EmpireFactionProfileCatalog.loadDefault();
        Stage22FactionProfileCatalog coreProfiles = Stage22FactionProfileLoader.loadDefault();
        Stage22EmpireCharacterLineup.Catalog empireCharacters = Stage22EmpireCharacterLineup.loadDefault();
        Stage22IndustrialUnionCharacterLineup.Catalog unionCharacters =
                Stage22IndustrialUnionCharacterLineup.loadDefault();
        List<String> scenarioVersions = Stage22CorePairBalanceCatalog.scenarios().stream()
                .map(Stage22CorePairBalanceCatalog.ScenarioDefinition::version)
                .toList();

        Snapshot provisional = new Snapshot(
                SCHEMA_VERSION,
                MANIFEST_VERSION,
                Stage22CorePairBalanceCatalog.SUITE_VERSION,
                evidence.empire().stableFactionId(),
                evidence.industrialUnion().stableFactionId(),
                empire.packageFingerprint(),
                union.packageFingerprint(),
                empire.productionFingerprint(),
                union.productionFingerprint(),
                empire.engineeringFingerprint(),
                union.engineeringFingerprint(),
                empire.manufacturingFingerprint(),
                union.manufacturingFingerprint(),
                empire.shipyardFingerprint(),
                union.shipyardFingerprint(),
                empire.stationFingerprint(),
                union.stationFingerprint(),
                empireProfiles.fingerprint(),
                coreProfiles.fingerprint(),
                empireCharacters.fingerprint(),
                unionCharacters.fingerprint(),
                empireProfiles.schemaVersion(),
                coreProfiles.schemaVersion(),
                Stage22IndustrialUnionProductionState.CURRENT_VERSION,
                scenarioVersions,
                "");
        return provisional.withFingerprint(computeFingerprint(provisional));
    }

    private static String computeFingerprint(Snapshot value) {
        String canonical = String.join("|",
                Integer.toString(value.schemaVersion()),
                value.manifestVersion(),
                value.scenarioSuiteVersion(),
                value.empireFactionId(),
                value.unionFactionId(),
                value.empirePackageFingerprint(),
                value.unionPackageFingerprint(),
                value.empireProductionFingerprint(),
                value.unionProductionFingerprint(),
                value.empireEngineeringFingerprint(),
                value.unionEngineeringFingerprint(),
                value.empireManufacturingFingerprint(),
                value.unionManufacturingFingerprint(),
                value.empireShipyardFingerprint(),
                value.unionShipyardFingerprint(),
                value.empireStationFingerprint(),
                value.unionStationFingerprint(),
                value.empireProfileFingerprint(),
                value.coreProfileCatalogFingerprint(),
                value.empireCharacterFingerprint(),
                value.unionCharacterFingerprint(),
                Integer.toString(value.empireProfileSchemaVersion()),
                Integer.toString(value.coreProfileSchemaVersion()),
                Integer.toString(value.unionProductionStateVersion()),
                String.join(",", value.scenarioVersions()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide mandatory SHA-256", exception);
        }
    }

    /**
     * Immutable freeze surface. Literal expected pins are added only after exact-head discovery.
     *
     * @param schemaVersion freeze schema version
     * @param manifestVersion freeze semantic version
     * @param scenarioSuiteVersion canonical scenario suite version
     * @param empireFactionId stable Empire save/runtime ID
     * @param unionFactionId stable Industrial Union save/runtime ID
     * @param empirePackageFingerprint Empire package fingerprint
     * @param unionPackageFingerprint Industrial Union package fingerprint
     * @param empireProductionFingerprint Empire production-manifest fingerprint
     * @param unionProductionFingerprint Industrial Union production-manifest fingerprint
     * @param empireEngineeringFingerprint Empire engineering fingerprint
     * @param unionEngineeringFingerprint Industrial Union engineering fingerprint
     * @param empireManufacturingFingerprint Empire manufacturing fingerprint
     * @param unionManufacturingFingerprint Industrial Union manufacturing fingerprint
     * @param empireShipyardFingerprint Empire physical shipyard fingerprint
     * @param unionShipyardFingerprint Industrial Union physical shipyard fingerprint
     * @param empireStationFingerprint shared Stage-18 station-infrastructure fingerprint observed by Empire validation
     * @param unionStationFingerprint shared Stage-18 station-infrastructure fingerprint observed by Union validation
     * @param empireProfileFingerprint Empire promoted profile-catalog fingerprint
     * @param coreProfileCatalogFingerprint shared Stage-22 profile-catalog fingerprint containing Union profile
     * @param empireCharacterFingerprint Empire character-lineup fingerprint
     * @param unionCharacterFingerprint Industrial Union character-lineup fingerprint
     * @param empireProfileSchemaVersion Empire profile schema version
     * @param coreProfileSchemaVersion shared profile schema version
     * @param unionProductionStateVersion Industrial Union production sidecar save version
     * @param scenarioVersions exact B00-B20 scenario version IDs
     * @param freezeFingerprint aggregate semantic freeze fingerprint
     */
    public record Snapshot(
            int schemaVersion,
            String manifestVersion,
            String scenarioSuiteVersion,
            String empireFactionId,
            String unionFactionId,
            String empirePackageFingerprint,
            String unionPackageFingerprint,
            String empireProductionFingerprint,
            String unionProductionFingerprint,
            String empireEngineeringFingerprint,
            String unionEngineeringFingerprint,
            String empireManufacturingFingerprint,
            String unionManufacturingFingerprint,
            String empireShipyardFingerprint,
            String unionShipyardFingerprint,
            String empireStationFingerprint,
            String unionStationFingerprint,
            String empireProfileFingerprint,
            String coreProfileCatalogFingerprint,
            String empireCharacterFingerprint,
            String unionCharacterFingerprint,
            int empireProfileSchemaVersion,
            int coreProfileSchemaVersion,
            int unionProductionStateVersion,
            List<String> scenarioVersions,
            String freezeFingerprint) {
        /** Freezes scenario-version ordering. */
        public Snapshot {
            scenarioVersions = List.copyOf(scenarioVersions);
        }

        private Snapshot withFingerprint(String fingerprint) {
            return new Snapshot(
                    schemaVersion, manifestVersion, scenarioSuiteVersion,
                    empireFactionId, unionFactionId,
                    empirePackageFingerprint, unionPackageFingerprint,
                    empireProductionFingerprint, unionProductionFingerprint,
                    empireEngineeringFingerprint, unionEngineeringFingerprint,
                    empireManufacturingFingerprint, unionManufacturingFingerprint,
                    empireShipyardFingerprint, unionShipyardFingerprint,
                    empireStationFingerprint, unionStationFingerprint,
                    empireProfileFingerprint, coreProfileCatalogFingerprint,
                    empireCharacterFingerprint, unionCharacterFingerprint,
                    empireProfileSchemaVersion, coreProfileSchemaVersion, unionProductionStateVersion,
                    scenarioVersions, fingerprint);
        }
    }
}
