package com.spacesim.content;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage23YardConstructionCatalogTest {
    private static final String ROW = "{\"yardDefinitionId\":\"yard.orbital_escort_v1\",\"profileId\":\"construction.profile.heavy_industrial\",\"installedMassKg\":100000000}";
    private static String document(String rows) { return "{\"schemaVersion\":1,\"yards\":[" + rows + "]}"; }

    @Test void physicalBillsConserveMassAndReuseExistingCapabilityAndWorkProfiles() {
        var catalog = Stage23YardConstructionCatalog.loadDefault();
        assertEquals(3, catalog.specifications().size());
        var yards = Stage22CivilianMiningProductionPath.loadRuntimeShipyards();
        var profiles = Stage18FacilityConstructionCatalogLoader.loadDefault();
        for (var spec : catalog.specifications()) {
            assertNotNull(yards.findYard(spec.yardDefinitionId()));
            var profile = profiles.findProfile(spec.profileId()); assertNotNull(profile);
            assertEquals(spec.installedMassKg(), spec.requiredMassByCommodityKg().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-6);
            assertEquals(spec.installedMassKg() * profile.workSecondsPerInstalledKg(), spec.requiredWorkSeconds());
            assertEquals(profile.requiredCapabilityTags(), spec.requiredCapabilityTags());
            for (var input : profile.inputs())
                assertEquals(spec.installedMassKg() * input.fractionOfInstalledMass(), spec.requiredMassByCommodityKg().get(input.commodityId()));
            assertThrows(UnsupportedOperationException.class, () -> spec.requiredMassByCommodityKg().put("unknown", 1d));
        }
        assertEquals(64, catalog.fingerprint().length());
        var a = Stage23YardConstructionCatalog.parse(document(ROW));
        assertEquals(a.fingerprint(), Stage23YardConstructionCatalog.parse(document(ROW)).fingerprint());
        assertNotEquals(a.fingerprint(), Stage23YardConstructionCatalog.parse(document(ROW.replace("100000000", "100000001"))).fingerprint());
    }

    @Test void rejectsInvalidBindingsAndNonPhysicalInputsInsteadOfCreatingAFreeYard() {
        for (String json : new String[]{"", "{", document(""), document(ROW + ',' + ROW),
                document(ROW.replace("yard.orbital_escort_v1", "yard.unknown")),
                document(ROW.replace("construction.profile.heavy_industrial", "component.heavy")),
                document(ROW.replace("100000000", "0")), document(ROW.replace("100000000", "-1")),
                document(ROW.replace("100000000", "\"100000000\"")), document(ROW).replace("\"schemaVersion\":1", "\"schemaVersion\":1.5")})
            assertThrows(IllegalArgumentException.class, () -> Stage23YardConstructionCatalog.parse(json));
        assertThrows(IllegalArgumentException.class, () -> new Stage23YardConstructionCatalog.Specification(
                "yard.orbital_escort_v1", "profile", 100, Map.of("commodity.material.structural_alloy", 99d), 1, Set.of("fabrication")));
        assertThrows(IllegalArgumentException.class, () -> new Stage23YardConstructionCatalog.Specification(
                "yard.orbital_escort_v1", "profile", 100, Map.of("commodity.material.structural_alloy", 100d), Double.POSITIVE_INFINITY, Set.of("fabrication")));
    }
}
