package com.spacesim.content;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.spacesim.content.weapon.Stage22CorePairWeaponRuntimeCatalogLoader;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistenceContract;

/**
 * Deterministic M22.6 freeze-manifest projection over the accepted core pair.
 *
 * <p>The semantic release-candidate surface is pinned literally after exact-head discovery. The
 * frozen capture is fail-closed: any package/profile/industrial/runtime/persistence/scenario drift
 * must update the versioned freeze intentionally rather than silently redefining the candidate.
 * This manifest owns no gameplay state and does not replace the separate B18-B20 human visual and
 * causal-comprehension acceptance gates.</p>
 */
public final class Stage22CorePairFreezeManifest {
    /** Freeze schema version. */
    public static final int SCHEMA_VERSION = 3;
    /** Semantic freeze-manifest version. */
    public static final String MANIFEST_VERSION = "stage22.core_pair_freeze_manifest.v3";

    private static final Snapshot EXPECTED_FREEZE = buildExpectedFreeze();

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
        var runtime = Stage22CorePairWeaponRuntimeCatalogLoader.loadCombined();
        Map<String, String> runtimePins = Map.of(
                "engineering", runtime.engineering().getFingerprint(),
                "ammunition", runtime.ammunition().getFingerprint(),
                "launchers", runtime.launchers().getFingerprint(),
                "engineering.schemaMigration", runtime.engineering().getSchemaVersion() + ":"
                        + runtime.engineering().getMigrationVersion(),
                "ammunition.schemaMigration", runtime.ammunition().getSchemaVersion() + ":"
                        + runtime.ammunition().getMigrationVersion(),
                "launchers.schemaMigration", runtime.launchers().getSchemaVersion() + ":"
                        + runtime.launchers().getMigrationVersion());

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
                Stage20GeneratedWorldRuntimePersistenceContract.CURRENT_BRIDGE_VERSION,
                Stage20GeneratedWorldRuntimePersistenceContract.CURRENT_CHECKPOINT_SCHEMA_VERSION,
                Stage20GeneratedWorldRuntimePersistenceContract.CURRENT_FILE_FORMAT_VERSION,
                Stage20GeneratedWorldRuntimePersistenceContract.MIGRATION_VERSION,
                Stage20GeneratedWorldRuntimePersistenceContract.SUPPORTED_FILE_FORMAT_VERSIONS,
                scenarioVersions,
                runtimePins,
                "");
        return provisional.withFingerprint(computeFingerprint(provisional));
    }

    /**
     * Requires the current semantic surface to equal the exact schema-3 release-candidate pins.
     *
     * @return current snapshot when every literal pin matches
     * @throws IllegalStateException when any frozen field drifts
     */
    public static Snapshot captureFrozen() {
        Snapshot current = captureCurrent();
        if (!EXPECTED_FREEZE.equals(current)) {
            throw new IllegalStateException(
                    "M22.6 core-pair freeze drift: expected=" + EXPECTED_FREEZE.freezeFingerprint()
                            + ", actual=" + current.freezeFingerprint());
        }
        return current;
    }

    /** @return literal aggregate fingerprint of the schema-3 semantic freeze candidate */
    public static String frozenFingerprint() {
        return EXPECTED_FREEZE.freezeFingerprint();
    }

    private static Snapshot buildExpectedFreeze() {
        Snapshot expected = new Snapshot(
                3,
                "stage22.core_pair_freeze_manifest.v3",
                "stage22.core_pair_balance_suite.v1",
                "faction.imperial_directorate",
                "faction.industrial_combine",
                "53e74820d135495aa3b9cc518c7a295c03d7112997a1903767248354e4da97b3",
                "e42309a19e5f61675b96255556ec34f09c1f21c96d14c0dc2f19daa556efc5a7",
                "e9266a8f998197d8e1b54a387023ff8593c222108d802ec7e0aa823a51e66f05",
                "b61e939f5fe7963379d253992e1b4682f7b9509a88fe5e14ea89eed701b9004b",
                "465c25304591faf850c48730b8c82f73f379d300367a5e41e3d535ede12f5c24",
                "d03531431e0054afa4adb1e61fd4854d26d3f13bf098829d814244231e527506",
                "5fb1a97eb30b044e1c2d7c1efaa3e787a944105831b510443b17579acf08d19a",
                "8f9811783f1094d6a3e1546c69db9728e8309dc46c093f383c68b5cdcf03917f",
                "53a2525b9a76383c13f3b837330c6d3f29de13057afba92f7ceec7a30bf57448",
                "9c0aa7b1c235bc4271a4e875f116ecef33310839c512332302423c03fd5bbe3a",
                "2168d68878246a5b73023a3ff28688739df95866c1b1ac50d33d337f44914743",
                "2168d68878246a5b73023a3ff28688739df95866c1b1ac50d33d337f44914743",
                "7da3b54e02b7ee57cef1d403e863e466ff72fbdc63f4b25b0c1db7ab77a7d521",
                "4269ac307ee11f29ba4fc64ddc6c276e3d2f7cb319bae6660c8fb871b1b1580d",
                "c350beff8eda3e5f2b2b638db23aa898a80f56ead0a96896f086acd881535cea",
                "fe66d6faaa00b6511fdca96dc3df641012d4a4cbe7f7dc1c900db0811c71d307",
                1,
                1,
                1,
                "stage20_5.generated-world-runtime-bridge.v1",
                3,
                4,
                "stage20_5.generated-world-runtime-migration.v1",
                List.of(1, 2, 3, 4),
                List.of(
                        "stage22.core_pair_balance_suite.v1.b00",
                        "stage22.core_pair_balance_suite.v1.b01",
                        "stage22.core_pair_balance_suite.v1.b02",
                        "stage22.core_pair_balance_suite.v1.b03",
                        "stage22.core_pair_balance_suite.v1.b04",
                        "stage22.core_pair_balance_suite.v1.b05",
                        "stage22.core_pair_balance_suite.v1.b06",
                        "stage22.core_pair_balance_suite.v1.b07",
                        "stage22.core_pair_balance_suite.v1.b08",
                        "stage22.core_pair_balance_suite.v1.b09",
                        "stage22.core_pair_balance_suite.v1.b10",
                        "stage22.core_pair_balance_suite.v1.b11",
                        "stage22.core_pair_balance_suite.v1.b12",
                        "stage22.core_pair_balance_suite.v1.b13",
                        "stage22.core_pair_balance_suite.v1.b14",
                        "stage22.core_pair_balance_suite.v1.b15",
                        "stage22.core_pair_balance_suite.v1.b16",
                        "stage22.core_pair_balance_suite.v1.b17",
                        "stage22.core_pair_balance_suite.v1.b18",
                        "stage22.core_pair_balance_suite.v1.b19",
                        "stage22.core_pair_balance_suite.v1.b20"),
                Map.of(
                        "engineering", "3e568c106a9c4607587aa216333d6de366e06ac95b16629641933575e74056bf",
                        "ammunition", "f0aff3cfce04d5df87aab37f70acc847e35845c630338f93bb09e1ffd965f591",
                        "launchers", "b855113394d552ea887130de5bbca80da7485f04834c0f19e0ee6a9a289756f1",
                        "engineering.schemaMigration", "1:1",
                        "ammunition.schemaMigration", "1:1",
                        "launchers.schemaMigration", "1:1"),
                "6705d39d21d234335d55a33d22460e6750941cf8a57719c24b88c1e4a659d6d4");
        String computed = computeFingerprint(expected);
        if (!expected.freezeFingerprint().equals(computed)) {
            throw new ExceptionInInitializerError(
                    "Pinned M22.6 freeze fingerprint is inconsistent with its literal fields: " + computed);
        }
        return expected;
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
                value.generatedRuntimeBridgeVersion(),
                Integer.toString(value.generatedRuntimeCheckpointSchemaVersion()),
                Integer.toString(value.generatedRuntimeCheckpointFileFormatVersion()),
                value.generatedRuntimeMigrationVersion(),
                value.generatedRuntimeSupportedFileFormats().toString(),
                String.join(",", value.scenarioVersions()),
                value.runtimeContentFingerprints().toString());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide mandatory SHA-256", exception);
        }
    }

    /**
     * Immutable exact semantic freeze surface.
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
     * @param unionShipyardFingerprint Union physical shipyard fingerprint
     * @param empireStationFingerprint shared Stage-18 station-infrastructure fingerprint observed by Empire validation
     * @param unionStationFingerprint shared Stage-18 station-infrastructure fingerprint observed by Union validation
     * @param empireProfileFingerprint Empire promoted profile-catalog fingerprint
     * @param coreProfileCatalogFingerprint shared Stage-22 profile-catalog fingerprint containing Union profile
     * @param empireCharacterFingerprint Empire character-lineup fingerprint
     * @param unionCharacterFingerprint Industrial Union character-lineup fingerprint
     * @param empireProfileSchemaVersion Empire profile schema version
     * @param coreProfileSchemaVersion shared profile schema version
     * @param unionProductionStateVersion Industrial Union production sidecar save version
     * @param generatedRuntimeBridgeVersion Stage-20.5 generated-world runtime composition contract
     * @param generatedRuntimeCheckpointSchemaVersion Stage-20.5 atomic checkpoint value schema
     * @param generatedRuntimeCheckpointFileFormatVersion Stage-20.5 atomic checkpoint binary format
     * @param generatedRuntimeMigrationVersion Stage-20.5 migration-table identity
     * @param generatedRuntimeSupportedFileFormats intentionally readable Stage-20.5 binary formats
     * @param scenarioVersions exact B00-B20 scenario version IDs
     * @param runtimeContentFingerprints combined runtime engineering, weapon and schema/migration pins
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
            String generatedRuntimeBridgeVersion,
            int generatedRuntimeCheckpointSchemaVersion,
            int generatedRuntimeCheckpointFileFormatVersion,
            String generatedRuntimeMigrationVersion,
            List<Integer> generatedRuntimeSupportedFileFormats,
            List<String> scenarioVersions,
            Map<String, String> runtimeContentFingerprints,
            String freezeFingerprint) {
        /**
         * Freezes collection ordering and rejects null mutable views.
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
         * @param unionShipyardFingerprint Union physical shipyard fingerprint
         * @param empireStationFingerprint shared Stage-18 station-infrastructure fingerprint observed by Empire validation
         * @param unionStationFingerprint shared Stage-18 station-infrastructure fingerprint observed by Union validation
         * @param empireProfileFingerprint Empire promoted profile-catalog fingerprint
         * @param coreProfileCatalogFingerprint shared Stage-22 profile-catalog fingerprint containing Union profile
         * @param empireCharacterFingerprint Empire character-lineup fingerprint
         * @param unionCharacterFingerprint Industrial Union character-lineup fingerprint
         * @param empireProfileSchemaVersion Empire profile schema version
         * @param coreProfileSchemaVersion shared profile schema version
         * @param unionProductionStateVersion Industrial Union production sidecar save version
         * @param generatedRuntimeBridgeVersion Stage-20.5 generated-world runtime composition contract
         * @param generatedRuntimeCheckpointSchemaVersion Stage-20.5 atomic checkpoint value schema
         * @param generatedRuntimeCheckpointFileFormatVersion Stage-20.5 atomic checkpoint binary format
         * @param generatedRuntimeMigrationVersion Stage-20.5 migration-table identity
         * @param generatedRuntimeSupportedFileFormats intentionally readable Stage-20.5 binary formats
         * @param scenarioVersions exact B00-B20 scenario version IDs
         * @param runtimeContentFingerprints combined runtime engineering, weapon and schema/migration pins
         * @param freezeFingerprint aggregate semantic freeze fingerprint
         */
        public Snapshot {
            generatedRuntimeSupportedFileFormats = List.copyOf(generatedRuntimeSupportedFileFormats);
            scenarioVersions = List.copyOf(scenarioVersions);
            runtimeContentFingerprints = Collections.unmodifiableMap(new TreeMap<>(runtimeContentFingerprints));
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
                    generatedRuntimeBridgeVersion,
                    generatedRuntimeCheckpointSchemaVersion,
                    generatedRuntimeCheckpointFileFormatVersion,
                    generatedRuntimeMigrationVersion,
                    generatedRuntimeSupportedFileFormats,
                    scenarioVersions, runtimeContentFingerprints, fingerprint);
        }
    }
}
