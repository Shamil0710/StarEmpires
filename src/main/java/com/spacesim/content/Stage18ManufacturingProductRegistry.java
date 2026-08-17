package com.spacesim.content;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.content.weapon.Stage175ICombatTestWeaponPack;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog.GuidedAmmunitionDefinition;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog.KineticAmmunitionDefinition;
import com.spacesim.content.weapon.WeaponAmmunitionCatalogLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stage-18D registry of already-existing Stage-17.5 manufactured module and ammunition identities.
 *
 * <p>The registry does not redefine combat content. It projects physical unit mass and storage
 * handling for manufacturing recipes while preserving whether an identity is an ordinary
 * production-schema demonstrator or explicitly provisional Stage-17.5I acceptance content.
 * Stage 22 remains responsible for final content promotion/re-authoring.</p>
 */
public final class Stage18ManufacturingProductRegistry {
    /** Storage class used by finished ship modules in the Stage-18D baseline. */
    public static final String MODULE_STORAGE_CLASS = "storage.oversized";
    /** Storage class used by finished physical ammunition in the Stage-18D baseline. */
    public static final String AMMUNITION_STORAGE_CLASS = "storage.hazardous_controlled";

    private final List<ProductDefinition> products;
    private final Map<String, ProductDefinition> productsById;

    private Stage18ManufacturingProductRegistry(List<ProductDefinition> products) {
        List<ProductDefinition> copy = new ArrayList<>(Objects.requireNonNull(products, "products"));
        copy.sort(Comparator.comparing(ProductDefinition::contentId));
        Map<String, ProductDefinition> index = new LinkedHashMap<>();
        for (ProductDefinition product : copy) {
            if (index.putIfAbsent(product.contentId(), product) != null) {
                throw new IllegalArgumentException("Duplicate manufactured product ID: " + product.contentId());
            }
        }
        this.products = List.copyOf(copy);
        this.productsById = Collections.unmodifiableMap(index);
    }

    /**
     * Builds the production Stage-18D manufacturing registry from authoritative Stage-17.5 loaders.
     *
     * @return deterministic registry containing all currently manufactured modules and ammunition
     */
    public static Stage18ManufacturingProductRegistry loadDefault() {
        List<ProductDefinition> products = new ArrayList<>();
        addModules(
                products,
                ShipEngineeringCatalogLoader.loadDefault(),
                Provenance.STAGE17_5_PRODUCTION_SCHEMA_DEMONSTRATOR);
        addModules(
                products,
                Stage175ICombatTestContentPack.load(),
                Provenance.STAGE17_5I_CONTENT_PROVISIONAL);
        addModules(
                products,
                Stage175ICombatTestContentPack.loadDoctrines(),
                Provenance.STAGE17_5I_CONTENT_PROVISIONAL);
        addAmmunition(
                products,
                WeaponAmmunitionCatalogLoader.loadDefault(),
                Provenance.STAGE17_5_PRODUCTION_SCHEMA_DEMONSTRATOR);
        addAmmunition(
                products,
                Stage175ICombatTestWeaponPack.loadAmmunition(),
                Provenance.STAGE17_5I_CONTENT_PROVISIONAL);
        return new Stage18ManufacturingProductRegistry(products);
    }

    /** @return deterministic immutable manufactured-product definitions */
    public List<ProductDefinition> getProducts() {
        return products;
    }

    /**
     * Finds a manufactured product by its existing content ID.
     *
     * @param contentId Stage-17.5 module or ammunition content ID
     * @return product definition, or {@code null} when the ID is unknown
     */
    public ProductDefinition findProduct(String contentId) {
        return productsById.get(contentId);
    }

    private static void addModules(
            List<ProductDefinition> products,
            ShipEngineeringCatalog catalog,
            Provenance provenance) {
        for (ModuleDefinition module : catalog.getModules()) {
            products.add(new ProductDefinition(
                    module.id(), ProductKind.MODULE, module.massKg(), MODULE_STORAGE_CLASS, provenance));
        }
    }

    private static void addAmmunition(
            List<ProductDefinition> products,
            WeaponAmmunitionCatalog catalog,
            Provenance provenance) {
        for (KineticAmmunitionDefinition ammunition : catalog.getKineticAmmunition()) {
            products.add(new ProductDefinition(
                    ammunition.id(),
                    ProductKind.AMMUNITION,
                    ammunition.massKg(),
                    AMMUNITION_STORAGE_CLASS,
                    provenance));
        }
        for (GuidedAmmunitionDefinition ammunition : catalog.getGuidedAmmunition()) {
            products.add(new ProductDefinition(
                    ammunition.id(),
                    ProductKind.AMMUNITION,
                    ammunition.dryMassKg() + ammunition.propellantMassKg(),
                    AMMUNITION_STORAGE_CLASS,
                    provenance));
        }
    }

    /** Kind of existing Stage-17.5 identity manufactured by Stage 18D. */
    public enum ProductKind {
        /** Installed or inventory ship module. */
        MODULE,
        /** Finite physical kinetic or guided ammunition body. */
        AMMUNITION
    }

    /** Content-lifecycle provenance retained by the manufacturing bridge. */
    public enum Provenance {
        /** Production-schema engineering/ammunition demonstrator, still subject to later content review. */
        STAGE17_5_PRODUCTION_SCHEMA_DEMONSTRATOR,
        /** Stage-17.5I acceptance vocabulary that is explicitly content-provisional until Stage 22. */
        STAGE17_5I_CONTENT_PROVISIONAL
    }

    /**
     * One existing finished-product identity projected into industrial manufacturing.
     *
     * @param contentId existing Stage-17.5 content ID
     * @param kind module or physical ammunition
     * @param unitMassKg authoritative physical mass of one manufactured unit
     * @param storageClassId Stage-18 storage class used for finished inventory
     * @param provenance content-lifecycle provenance that prevents accidental canon promotion
     */
    public record ProductDefinition(
            String contentId,
            ProductKind kind,
            double unitMassKg,
            String storageClassId,
            Provenance provenance) {
        /**
         * Validates one immutable manufacturing product projection.
         *
         * @param contentId existing Stage-17.5 content ID
         * @param kind module or ammunition
         * @param unitMassKg authoritative physical mass of one manufactured unit
         * @param storageClassId Stage-18 storage class used for finished inventory
         * @param provenance content-lifecycle provenance
         */
        public ProductDefinition {
            requireText(contentId, "contentId");
            Objects.requireNonNull(kind, "kind");
            if (!Double.isFinite(unitMassKg) || unitMassKg <= 0d) {
                throw new IllegalArgumentException("unitMassKg must be finite and positive");
            }
            requireText(storageClassId, "storageClassId");
            Objects.requireNonNull(provenance, "provenance");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be non-blank");
        }
        return value;
    }
}
