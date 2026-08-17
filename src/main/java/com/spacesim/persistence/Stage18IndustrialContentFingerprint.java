package com.spacesim.persistence;

import com.spacesim.content.Stage18ExtractionCatalogLoader;
import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.content.Stage18FacilityConstructionCatalogLoader;
import com.spacesim.content.Stage18ManufacturingCatalogLoader;
import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18RefiningCatalogLoader;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage18ShipConsumableCatalogLoader;
import com.spacesim.content.Stage18ShipyardCatalogLoader;
import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Computes one semantic fingerprint covering the complete Stage-18A-I industrial vocabulary. */
public final class Stage18IndustrialContentFingerprint {
    private Stage18IndustrialContentFingerprint() {
        throw new AssertionError("No instances");
    }

    /**
     * Computes the current production industrial fingerprint from every authoritative upstream catalog.
     *
     * <p>The Stage-17.5 engineering fingerprint, Stage-18D physical product projection and Stage-18I
     * ship-consumable bindings are included because storage, handling, shipyard and servicing semantics
     * depend on exact module/ammunition masses and explicit commodity-to-interface bindings.</p>
     *
     * @return lowercase SHA-256 fingerprint
     */
    public static String current() {
        StringBuilder canonical = new StringBuilder(16_384);
        canonical.append("engineering=")
                .append(ShipEngineeringCatalogLoader.loadDefault().getFingerprint()).append('\n');
        canonical.append("ontology=")
                .append(Stage18ResourceOntologyLoader.loadDefault().getFingerprint()).append('\n');
        canonical.append("extraction=")
                .append(Stage18ExtractionCatalogLoader.loadDefault().getFingerprint()).append('\n');
        canonical.append("refining=")
                .append(Stage18RefiningCatalogLoader.loadDefault().getFingerprint()).append('\n');
        canonical.append("manufacturing=")
                .append(Stage18ManufacturingCatalogLoader.loadDefault().getFingerprint()).append('\n');
        canonical.append("products|");
        for (Stage18ManufacturingProductRegistry.ProductDefinition product
                : Stage18ManufacturingProductRegistry.loadDefault().getProducts()) {
            canonical.append(product.contentId()).append('|')
                    .append(product.kind().name()).append('|')
                    .append(Double.toHexString(product.unitMassKg())).append('|')
                    .append(product.storageClassId()).append('|')
                    .append(product.provenance()).append(';');
        }
        canonical.append('\n');
        canonical.append("facilities=")
                .append(Stage18FacilityCatalogLoader.loadDefault().getFingerprint()).append('\n');
        canonical.append("stations=")
                .append(Stage18StationInfrastructureCatalogLoader.loadDefault().getFingerprint()).append('\n');
        canonical.append("shipyards=")
                .append(Stage18ShipyardCatalogLoader.loadDefault().getFingerprint()).append('\n');
        canonical.append("construction=")
                .append(Stage18FacilityConstructionCatalogLoader.loadDefault().getFingerprint()).append('\n');
        canonical.append("ship-consumables|");
        for (var binding : Stage18ShipConsumableCatalogLoader.loadDefault().getBindings()) {
            canonical.append(binding.id()).append('|')
                    .append(binding.moduleId()).append('|')
                    .append(binding.interfaceId()).append('|')
                    .append(binding.interfaceKind().name()).append('|')
                    .append(binding.commodityId()).append('|')
                    .append(Double.toHexString(binding.amountPerKg())).append(';');
        }
        canonical.append('\n');
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide mandatory SHA-256", exception);
        }
    }
}
