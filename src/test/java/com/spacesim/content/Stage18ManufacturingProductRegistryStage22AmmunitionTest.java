package com.spacesim.content;

import com.spacesim.content.Stage18ManufacturingProductRegistry.ProductKind;
import com.spacesim.content.Stage18ManufacturingProductRegistry.Provenance;
import com.spacesim.content.weapon.Stage22CorePairWeaponRuntimeCatalogLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** M22.6 B12 registration seam for exact core-pair ammunition through Stage-18 finished products. */
class Stage18ManufacturingProductRegistryStage22AmmunitionTest {
    private static final String EMPIRE_AMMO = "ammo.empire_axial_dart_150kg_v1";
    private static final String UNION_AMMO = "ammo.industrial_union_dart_140kg_v1";

    @Test
    void exactCoreAmmunitionRetainsPhysicalMassStorageClassAndAuthoredProvenance() {
        var ammunition = Stage22CorePairWeaponRuntimeCatalogLoader.loadCombined().ammunition();
        var products = Stage18ManufacturingProductRegistry.loadDefault()
                .withAmmunitionCatalog(ammunition, Provenance.STAGE22_AUTHORED);

        var empire = products.findProduct(EMPIRE_AMMO);
        var union = products.findProduct(UNION_AMMO);

        assertEquals(ProductKind.AMMUNITION, empire.kind());
        assertEquals(ProductKind.AMMUNITION, union.kind());
        assertEquals(150d, empire.unitMassKg(), 1e-12d);
        assertEquals(140d, union.unitMassKg(), 1e-12d);
        assertEquals(Stage18ManufacturingProductRegistry.AMMUNITION_STORAGE_CLASS, empire.storageClassId());
        assertEquals(Stage18ManufacturingProductRegistry.AMMUNITION_STORAGE_CLASS, union.storageClassId());
        assertEquals(Provenance.STAGE22_AUTHORED, empire.provenance());
        assertEquals(Provenance.STAGE22_AUTHORED, union.provenance());
    }

    @Test
    void duplicateLaterStageAmmunitionRegistrationFailsClosed() {
        var ammunition = Stage22CorePairWeaponRuntimeCatalogLoader.loadCombined().ammunition();
        var once = Stage18ManufacturingProductRegistry.loadDefault()
                .withAmmunitionCatalog(ammunition, Provenance.STAGE22_AUTHORED);

        assertThrows(IllegalArgumentException.class,
                () -> once.withAmmunitionCatalog(ammunition, Provenance.STAGE22_AUTHORED));
    }
}
