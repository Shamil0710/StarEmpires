package com.spacesim.content;

import com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 B02 immutable starting-assets fingerprint evidence over the canonical paired schedule.
 *
 * <p>This class authors only B02 starting conditions. Gameplay state remains owned by the accepted
 * Stage-18/19/21 authorities. Each core faction starts with one exact primary fit from every required
 * common role family plus its authored station set. The asset fingerprint is built only from accepted
 * package/production/engineering/manufacturing/shipyard/station semantic fingerprints and exact
 * {@link Stage22FitFingerprint} values. Mirroring changes only the scenario slot; it cannot alter a
 * faction's asset rows or content identities.</p>
 *
 * <p>This closes the immutable starting-assets-fingerprint slice only. It does not claim that the
 * declared roster has completed the full L1-L4 physical cold-start trajectory.</p>
 */
class Stage22CorePairColdStartAssetsFingerprintAcceptanceTest {
    private static final String EMPIRE = Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID;
    private static final String UNION = Stage22CorePairBalanceEvidence.UNION_FACTION_ID;

    @Test
    void b02ThirtyPairedColdStartsMirrorOnlySpawnSlotAndKeepExactAssetFingerprints() {
        ShipEngineeringCatalog engineering = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        var pair = Stage22CorePairBalanceEvidence.deriveCurrent();
        var empirePackage = Stage22EmpirePackageLoader.loadDefault();
        var unionPackage = Stage22IndustrialUnionPackageLoader.loadDefault();

        StartAssets empire = empireAssets(pair.empire(), empirePackage, engineering);
        StartAssets union = unionAssets(pair.industrialUnion(), unionPackage, engineering);
        assertEquals(Stage22EmpirePackageCatalog.REQUIRED_SHIP_FAMILIES, empire.ships().size());
        assertEquals(Stage22IndustrialUnionPackageCatalog.REQUIRED_SHIP_FAMILIES, union.ships().size());
        assertEquals(empire.ships().stream().map(ShipAsset::roleId).toList(),
                union.ships().stream().map(ShipAsset::roleId).toList(),
                "B02 core starts must cover the same common role-family floor");
        assertNotEquals(empire.fingerprint(), union.fingerprint(),
                "asymmetric authored packages must not collapse to one starting-assets fingerprint");

        ArrayList<CoordinateRow> rows = new ArrayList<>();
        for (Stage22CorePairExperimentProtocol.RunCoordinate coordinate
                : Stage22CorePairExperimentProtocol.tuningSchedule()) {
            boolean mirrored = coordinate.permutation() == Stage22CorePairExperimentProtocol.Permutation.MIRRORED;
            rows.add(new CoordinateRow(
                    coordinate.seed(),
                    coordinate.permutation().name(),
                    mirrored ? "B" : "A",
                    empire.fingerprint(),
                    mirrored ? "A" : "B",
                    union.fingerprint(),
                    empire.ships().size(),
                    union.ships().size(),
                    empire.stations().size(),
                    union.stations().size()));
        }

        assertEquals(Stage22CorePairExperimentProtocol.TUNING_SEED_COUNT * 2, rows.size());
        for (int index = 0; index < rows.size(); index += 2) {
            CoordinateRow normal = rows.get(index);
            CoordinateRow mirrored = rows.get(index + 1);
            assertEquals(normal.seed(), mirrored.seed());
            assertEquals("A", normal.empireSpawnSlot());
            assertEquals("B", mirrored.empireSpawnSlot());
            assertEquals("B", normal.unionSpawnSlot());
            assertEquals("A", mirrored.unionSpawnSlot());
            assertEquals(normal.empireStartingAssetsFingerprint(), mirrored.empireStartingAssetsFingerprint());
            assertEquals(normal.unionStartingAssetsFingerprint(), mirrored.unionStartingAssetsFingerprint());
        }

        LinkedHashMap<String, Object> archive = new LinkedHashMap<>();
        archive.put("scenario", "B02");
        archive.put("scenarioVariant", "immutable_cold_start_assets");
        archive.put("pairedSeedCount", Stage22CorePairExperimentProtocol.TUNING_SEED_COUNT);
        archive.put("empireStartingAssets", empire);
        archive.put("industrialUnionStartingAssets", union);
        archive.put("coordinates", rows);
        Stage22CorePairEvidenceArchive.write(
                "B02-immutable-cold-start-assets",
                archive,
                "Canonical 30-seed/default+mirrored B02 starting-condition archive. Each side retains one exact primary fit for every required role family and its authored station set. Mirroring swaps only scenario slot A/B; package, production, engineering, manufacturing, shipyard, station and exact-fit fingerprints remain unchanged. No free manufactured-product stock is introduced by this evidence fixture. Full L1-L4 physical cold-start progression and opportunity-cost trajectory remain required before B02 can be COMPLETE.");
    }

    private static StartAssets empireAssets(
            Stage22CorePairBalanceEvidence.PackageVector vector,
            Stage22EmpirePackageCatalog packageCatalog,
            ShipEngineeringCatalog engineering) {
        List<ShipAsset> ships = packageCatalog.shipFamilies().stream()
                .map(family -> new ShipAsset(
                        family.roleId(),
                        family.familyId(),
                        family.primaryFitId(),
                        Stage22FitFingerprint.compute(engineering, family.primaryFitId()),
                        family.productionManifestId(),
                        1))
                .sorted(Comparator.comparing(ShipAsset::roleId))
                .toList();
        List<StationAsset> stations = packageCatalog.stations().stream()
                .map(station -> new StationAsset(station.id(), station.stage18ArchetypeId(), 1))
                .sorted(Comparator.comparing(StationAsset::stationVariantId))
                .toList();
        return freeze(vector, ships, stations);
    }

    private static StartAssets unionAssets(
            Stage22CorePairBalanceEvidence.PackageVector vector,
            Stage22IndustrialUnionPackageCatalog packageCatalog,
            ShipEngineeringCatalog engineering) {
        List<ShipAsset> ships = packageCatalog.shipFamilies().stream()
                .map(family -> new ShipAsset(
                        family.roleId(),
                        family.familyId(),
                        family.primaryFitId(),
                        Stage22FitFingerprint.compute(engineering, family.primaryFitId()),
                        family.productionManifestId(),
                        1))
                .sorted(Comparator.comparing(ShipAsset::roleId))
                .toList();
        List<StationAsset> stations = packageCatalog.stations().stream()
                .map(station -> new StationAsset(station.id(), station.stage18ArchetypeId(), 1))
                .sorted(Comparator.comparing(StationAsset::stationVariantId))
                .toList();
        return freeze(vector, ships, stations);
    }

    private static StartAssets freeze(
            Stage22CorePairBalanceEvidence.PackageVector vector,
            List<ShipAsset> ships,
            List<StationAsset> stations) {
        assertTrue(ships.stream().allMatch(row -> row.count() == 1));
        assertTrue(stations.stream().allMatch(row -> row.count() == 1));
        StringBuilder canonical = new StringBuilder("stage22-b02-starting-assets-v1\n")
                .append("faction=").append(vector.stableFactionId()).append('\n')
                .append("package=").append(vector.packageFingerprint()).append('\n')
                .append("production=").append(vector.productionFingerprint()).append('\n')
                .append("engineering=").append(vector.engineeringFingerprint()).append('\n')
                .append("manufacturing=").append(vector.manufacturingFingerprint()).append('\n')
                .append("shipyard=").append(vector.shipyardFingerprint()).append('\n')
                .append("station=").append(vector.stationFingerprint()).append('\n')
                .append("freeManufacturedProductStock=0\n");
        ships.forEach(row -> canonical.append("ship|")
                .append(row.roleId()).append('|')
                .append(row.familyId()).append('|')
                .append(row.fitId()).append('|')
                .append(row.fitFingerprint()).append('|')
                .append(row.productionManifestId()).append('|')
                .append(row.count()).append('\n'));
        stations.forEach(row -> canonical.append("station|")
                .append(row.stationVariantId()).append('|')
                .append(row.stage18ArchetypeId()).append('|')
                .append(row.count()).append('\n'));
        return new StartAssets(
                vector.stableFactionId(),
                vector.packageFingerprint(),
                vector.productionFingerprint(),
                vector.engineeringFingerprint(),
                vector.manufacturingFingerprint(),
                vector.shipyardFingerprint(),
                vector.stationFingerprint(),
                ships,
                stations,
                0,
                sha256(canonical.toString()));
    }

    private static String sha256(String canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide mandatory SHA-256", exception);
        }
    }

    private record ShipAsset(
            String roleId,
            String familyId,
            String fitId,
            String fitFingerprint,
            String productionManifestId,
            int count) { }

    private record StationAsset(String stationVariantId, String stage18ArchetypeId, int count) { }

    private record StartAssets(
            String stableFactionId,
            String packageFingerprint,
            String productionFingerprint,
            String engineeringFingerprint,
            String manufacturingFingerprint,
            String shipyardFingerprint,
            String stationFingerprint,
            List<ShipAsset> ships,
            List<StationAsset> stations,
            int freeManufacturedProductStock,
            String fingerprint) { }

    private record CoordinateRow(
            long seed,
            String permutation,
            String empireSpawnSlot,
            String empireStartingAssetsFingerprint,
            String unionSpawnSlot,
            String unionStartingAssetsFingerprint,
            int empireShipCount,
            int unionShipCount,
            int empireStationCount,
            int unionStationCount) { }
}
