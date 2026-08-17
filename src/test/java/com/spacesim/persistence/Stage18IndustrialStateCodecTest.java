package com.spacesim.persistence;

import com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment;
import com.spacesim.content.Stage18ExtractionCatalog.SourceKind;
import com.spacesim.economy.Stage18ExtractionRuntime.PhysicalSourceState;
import com.spacesim.economy.Stage18FacilityConstructionRuntime.ConstructionOrderSnapshot;
import com.spacesim.economy.Stage18FacilityConstructionRuntime.OrderStatus;
import com.spacesim.economy.Stage18FacilityRuntime.InstalledFacilityState;
import com.spacesim.economy.Stage18ShipyardRuntime.InstalledYardState;
import com.spacesim.economy.Stage18StationStorage.StationStorageSnapshot;
import com.spacesim.persistence.Stage18IndustrialState.FacilityInstallationSnapshot;
import com.spacesim.persistence.Stage18IndustrialState.PhysicalSourceSnapshot;
import com.spacesim.persistence.Stage18IndustrialState.ProcessKind;
import com.spacesim.persistence.Stage18IndustrialState.ProcessOrderSnapshot;
import com.spacesim.persistence.Stage18IndustrialState.YardInstallationSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Stage18IndustrialStateCodecTest {
    @Test
    void deterministicRoundTripPreservesAllIndustrialExtensionState() {
        Stage18IndustrialState state = fixture();

        byte[] first = Stage18IndustrialStateCodec.encode(state);
        byte[] second = Stage18IndustrialStateCodec.encode(state);
        Stage18IndustrialState decoded = Stage18IndustrialStateCodec.decode(first);

        assertArrayEquals(first, second);
        assertEquals(state, decoded);
        assertEquals(state.sources().get(0).restore().remainingAccessibleMassKg(),
                decoded.sources().get(0).restore().remainingAccessibleMassKg(), 0d);
    }

    @Test
    void wrongIndustrialContentFingerprintFailsClosed() {
        Stage18IndustrialState state = fixture();
        byte[] bytes = Stage18IndustrialStateCodec.encode(state);

        assertThrows(IllegalArgumentException.class, () ->
                Stage18IndustrialStateCodec.decodeAgainstFingerprint(
                        bytes, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
    }

    @Test
    void truncatedIndustrialPayloadFailsClosed() {
        byte[] bytes = Stage18IndustrialStateCodec.encode(fixture());
        byte[] truncated = Arrays.copyOf(bytes, bytes.length - 7);

        assertThrows(IllegalArgumentException.class, () -> Stage18IndustrialStateCodec.decode(truncated));
    }

    private static Stage18IndustrialState fixture() {
        String stationId = "station.persistence.test";
        PhysicalSourceState source = new PhysicalSourceState(
                "source.persistence.metal",
                SourceKind.NATURAL_OCCURRENCE,
                "occurrence.metallic",
                ExtractionEnvironment.FREE_BODY,
                "commodity.feedstock.metallic_ore",
                1_000_000d,
                750_000d,
                0.8d,
                0.9d,
                Set.of("capability.extraction.asteroid_excavation"));
        StationStorageSnapshot storage = new StationStorageSnapshot(
                stationId,
                Map.of("storage.dry_bulk", 5_000_000d, "storage.oversized", 5_000_000d),
                Map.of("commodity.material.structural_alloy", 100_000d),
                Map.of("module.radiator_escort_v1", 1));
        InstalledFacilityState facility = new InstalledFacilityState(
                "facility.persistence.heavy",
                "facility.fabrication.heavy",
                0.85d,
                60_000_000d,
                40_000_000d,
                70d,
                3d,
                "location.orbital_station",
                true);
        InstalledYardState yard = new InstalledYardState(
                "yard.persistence.escort",
                "yard.orbital_escort_v1",
                0.9d,
                1_000_000_000d,
                10d,
                400,
                400,
                true);
        ConstructionOrderSnapshot construction = new ConstructionOrderSnapshot(
                "construction.persistence.recycling",
                "facility.persistence.recycling.new",
                "facility.processing.recycling",
                stationId,
                "location.orbital_station",
                Map.of("commodity.material.structural_alloy", 9_000_000d),
                Map.of("commodity.material.structural_alloy", 4_500_000d),
                1_800_000d,
                0d,
                OrderStatus.AWAITING_MATERIALS);
        ProcessOrderSnapshot process = new ProcessOrderSnapshot(
                "process.persistence.refine",
                ProcessKind.REFINING,
                "refining.structural_alloy",
                stationId,
                "",
                100_000d,
                0,
                0.4d,
                Map.of("commodity.feedstock.metallic_ore", 50_000d),
                Map.of());
        return new Stage18IndustrialState(
                Stage18IndustrialState.CURRENT_VERSION,
                Stage18IndustrialContentFingerprint.current(),
                42L,
                List.of(PhysicalSourceSnapshot.capture(source)),
                List.of(storage),
                List.of(new FacilityInstallationSnapshot(stationId, facility)),
                List.of(new YardInstallationSnapshot(stationId, yard)),
                List.of(construction),
                List.of(process));
    }
}
