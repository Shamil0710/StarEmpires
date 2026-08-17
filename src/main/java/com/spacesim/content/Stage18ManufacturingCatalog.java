package com.spacesim.content;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable Stage-18D catalog for industrial components and finished-product manufacturing recipes.
 *
 * <p>Component recipes transform Stage-18C materials/consumables into the three compact component
 * families. Product profiles then express mass-closed input composition for existing Stage-17.5
 * modules and ammunition. Bindings connect product identities to reusable profiles without changing
 * the Stage-17.5 combat catalogs.</p>
 */
public final class Stage18ManufacturingCatalog {
    private final int schemaVersion;
    private final List<ComponentRecipeDefinition> componentRecipes;
    private final List<ProductProfileDefinition> productProfiles;
    private final List<ProductBindingDefinition> productBindings;
    private final Map<String, ComponentRecipeDefinition> componentRecipesById;
    private final Map<String, ProductProfileDefinition> productProfilesById;
    private final Map<String, ProductBindingDefinition> productBindingsByProductId;
    private final String fingerprint;

    Stage18ManufacturingCatalog(
            int schemaVersion,
            List<ComponentRecipeDefinition> componentRecipes,
            List<ProductProfileDefinition> productProfiles,
            List<ProductBindingDefinition> productBindings) {
        this.schemaVersion = schemaVersion;
        this.componentRecipes = sortedCopy(componentRecipes, Comparator.comparing(ComponentRecipeDefinition::id));
        this.productProfiles = sortedCopy(productProfiles, Comparator.comparing(ProductProfileDefinition::id));
        this.productBindings = sortedCopy(productBindings, Comparator.comparing(ProductBindingDefinition::productContentId));
        this.componentRecipesById = index(this.componentRecipes, ComponentRecipeDefinition::id, "component recipe");
        this.productProfilesById = index(this.productProfiles, ProductProfileDefinition::id, "product profile");
        this.productBindingsByProductId = index(
                this.productBindings, ProductBindingDefinition::productContentId, "product binding");
        this.fingerprint = computeFingerprint();
    }

    /** @return supported Stage-18D manufacturing schema version */
    public int getSchemaVersion() {
        return schemaVersion;
    }

    /** @return deterministic immutable component recipes */
    public List<ComponentRecipeDefinition> getComponentRecipes() {
        return componentRecipes;
    }

    /** @return deterministic immutable reusable finished-product profiles */
    public List<ProductProfileDefinition> getProductProfiles() {
        return productProfiles;
    }

    /** @return deterministic immutable product-to-profile bindings */
    public List<ProductBindingDefinition> getProductBindings() {
        return productBindings;
    }

    /** @return lowercase SHA-256 fingerprint of Stage-18D manufacturing semantics */
    public String getFingerprint() {
        return fingerprint;
    }

    /**
     * Finds a component recipe by stable ID.
     *
     * @param id recipe ID
     * @return recipe definition, or {@code null}
     */
    public ComponentRecipeDefinition findComponentRecipe(String id) {
        return componentRecipesById.get(id);
    }

    /**
     * Finds a reusable product profile by stable ID.
     *
     * @param id profile ID
     * @return profile definition, or {@code null}
     */
    public ProductProfileDefinition findProductProfile(String id) {
        return productProfilesById.get(id);
    }

    /**
     * Finds the manufacturing binding for an existing module/ammunition identity.
     *
     * @param productContentId existing Stage-17.5 product content ID
     * @return binding, or {@code null}
     */
    public ProductBindingDefinition findProductBinding(String productContentId) {
        return productBindingsByProductId.get(productContentId);
    }

    private String computeFingerprint() {
        StringBuilder canonical = new StringBuilder(16_384);
        canonical.append("schema=").append(schemaVersion).append('\n');
        for (ComponentRecipeDefinition recipe : componentRecipes) {
            canonical.append("component|").append(recipe.id()).append('|')
                    .append(recipe.displayName()).append('|').append(recipe.outputCommodityId()).append('|');
            appendInputs(canonical, recipe.inputs());
            canonical.append('|').append(String.join(",", recipe.requiredCapabilityTags())).append('|')
                    .append(Double.toHexString(recipe.energyJPerOutputKg())).append('|')
                    .append(Double.toHexString(recipe.workSecondsPerOutputKg())).append('|')
                    .append(Double.toHexString(recipe.maintenanceWorkSecondsPerOutputKg())).append('\n');
        }
        for (ProductProfileDefinition profile : productProfiles) {
            canonical.append("profile|").append(profile.id()).append('|').append(profile.displayName()).append('|');
            appendInputs(canonical, profile.inputs());
            canonical.append('|').append(String.join(",", profile.requiredCapabilityTags())).append('|')
                    .append(Double.toHexString(profile.energyJPerOutputKg())).append('|')
                    .append(Double.toHexString(profile.workSecondsPerOutputKg())).append('|')
                    .append(Double.toHexString(profile.maintenanceWorkSecondsPerOutputKg())).append('\n');
        }
        for (ProductBindingDefinition binding : productBindings) {
            canonical.append("binding|").append(binding.productContentId()).append('|')
                    .append(binding.profileId()).append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide mandatory SHA-256", exception);
        }
    }

    private static void appendInputs(StringBuilder canonical, List<ManufacturingInputDefinition> inputs) {
        for (ManufacturingInputDefinition input : inputs) {
            canonical.append(input.commodityId()).append('=')
                    .append(Double.toHexString(input.fractionOfOutputMass())).append(',');
        }
    }

    /**
     * One commodity share of manufactured output mass.
     *
     * @param commodityId Stage-18 material, consumable, or component commodity ID
     * @param fractionOfOutputMass fraction in {@code (0, 1]}; recipe/profile fractions sum to one
     */
    public record ManufacturingInputDefinition(String commodityId, double fractionOfOutputMass) {
        /**
         * Validates one immutable manufacturing input share.
         *
         * @param commodityId Stage-18 commodity ID
         * @param fractionOfOutputMass mass share in {@code (0, 1]}
         */
        public ManufacturingInputDefinition {
            requireText(commodityId, "commodityId");
            requireFraction(fractionOfOutputMass, "fractionOfOutputMass");
        }
    }

    /**
     * Recipe for one compact Stage-18 industrial component family.
     *
     * @param id stable recipe ID
     * @param displayName diagnostic/display name
     * @param inputs material/consumable mass shares that sum to one
     * @param outputCommodityId component-family commodity produced
     * @param requiredCapabilityTags fabrication capabilities required
     * @param energyJPerOutputKg process energy per kilogram of finished component
     * @param workSecondsPerOutputKg engineering work-seconds per kilogram
     * @param maintenanceWorkSecondsPerOutputKg maintenance work-seconds per kilogram
     */
    public record ComponentRecipeDefinition(
            String id,
            String displayName,
            List<ManufacturingInputDefinition> inputs,
            String outputCommodityId,
            Set<String> requiredCapabilityTags,
            double energyJPerOutputKg,
            double workSecondsPerOutputKg,
            double maintenanceWorkSecondsPerOutputKg) {
        /**
         * Validates and freezes one component recipe.
         *
         * @param id stable recipe ID
         * @param displayName diagnostic/display name
         * @param inputs input mass shares
         * @param outputCommodityId component-family output
         * @param requiredCapabilityTags fabrication capabilities
         * @param energyJPerOutputKg process energy per output kilogram
         * @param workSecondsPerOutputKg engineering work per output kilogram
         * @param maintenanceWorkSecondsPerOutputKg maintenance work per output kilogram
         */
        public ComponentRecipeDefinition {
            requireText(id, "component recipe id");
            requireText(displayName, "component recipe displayName");
            inputs = freezeInputs(inputs, id);
            requireText(outputCommodityId, "outputCommodityId");
            requiredCapabilityTags = immutableTags(requiredCapabilityTags, id);
            requirePositive(energyJPerOutputKg, "energyJPerOutputKg");
            requirePositive(workSecondsPerOutputKg, "workSecondsPerOutputKg");
            requirePositive(maintenanceWorkSecondsPerOutputKg, "maintenanceWorkSecondsPerOutputKg");
        }
    }

    /**
     * Reusable mass-composition profile for a family of existing finished products.
     *
     * @param id stable profile ID
     * @param displayName diagnostic/display name
     * @param inputs material/consumable/component mass shares that sum to one
     * @param requiredCapabilityTags manufacturing capabilities required
     * @param energyJPerOutputKg process energy per kilogram of finished product
     * @param workSecondsPerOutputKg engineering work-seconds per kilogram
     * @param maintenanceWorkSecondsPerOutputKg maintenance work-seconds per kilogram
     */
    public record ProductProfileDefinition(
            String id,
            String displayName,
            List<ManufacturingInputDefinition> inputs,
            Set<String> requiredCapabilityTags,
            double energyJPerOutputKg,
            double workSecondsPerOutputKg,
            double maintenanceWorkSecondsPerOutputKg) {
        /**
         * Validates and freezes one product manufacturing profile.
         *
         * @param id stable profile ID
         * @param displayName diagnostic/display name
         * @param inputs input mass shares
         * @param requiredCapabilityTags manufacturing capabilities
         * @param energyJPerOutputKg process energy per output kilogram
         * @param workSecondsPerOutputKg engineering work per output kilogram
         * @param maintenanceWorkSecondsPerOutputKg maintenance work per output kilogram
         */
        public ProductProfileDefinition {
            requireText(id, "product profile id");
            requireText(displayName, "product profile displayName");
            inputs = freezeInputs(inputs, id);
            requiredCapabilityTags = immutableTags(requiredCapabilityTags, id);
            requirePositive(energyJPerOutputKg, "energyJPerOutputKg");
            requirePositive(workSecondsPerOutputKg, "workSecondsPerOutputKg");
            requirePositive(maintenanceWorkSecondsPerOutputKg, "maintenanceWorkSecondsPerOutputKg");
        }
    }

    /**
     * Maps one existing Stage-17.5 module/ammunition identity to a manufacturing profile.
     *
     * @param productContentId existing product content ID
     * @param profileId Stage-18D manufacturing profile ID
     */
    public record ProductBindingDefinition(String productContentId, String profileId) {
        /**
         * Validates one immutable product binding.
         *
         * @param productContentId existing product content ID
         * @param profileId manufacturing profile ID
         */
        public ProductBindingDefinition {
            requireText(productContentId, "productContentId");
            requireText(profileId, "profileId");
        }
    }

    private static List<ManufacturingInputDefinition> freezeInputs(
            List<ManufacturingInputDefinition> source, String subject) {
        Objects.requireNonNull(source, "inputs");
        if (source.isEmpty()) {
            throw new IllegalArgumentException("Manufacturing inputs must not be empty: " + subject);
        }
        List<ManufacturingInputDefinition> copy = new ArrayList<>(source);
        copy.sort(Comparator.comparing(ManufacturingInputDefinition::commodityId));
        TreeSet<String> ids = new TreeSet<>();
        double total = 0d;
        for (ManufacturingInputDefinition input : copy) {
            if (!ids.add(input.commodityId())) {
                throw new IllegalArgumentException("Duplicate manufacturing input " + input.commodityId() + " for " + subject);
            }
            total += input.fractionOfOutputMass();
        }
        if (Math.abs(total - 1d) > 1e-9d) {
            throw new IllegalArgumentException("Manufacturing input fractions must sum to 1: " + subject);
        }
        return List.copyOf(copy);
    }

    private static Set<String> immutableTags(Set<String> source, String subject) {
        Objects.requireNonNull(source, "requiredCapabilityTags");
        TreeSet<String> copy = new TreeSet<>();
        for (String tag : source) {
            requireText(tag, "capability tag");
            if (!copy.add(tag)) {
                throw new IllegalArgumentException("Duplicate capability tag " + tag + " for " + subject);
            }
        }
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("Manufacturing definition requires capability tags: " + subject);
        }
        return Collections.unmodifiableSet(copy);
    }

    private static <T> List<T> sortedCopy(List<T> source, Comparator<T> comparator) {
        List<T> copy = new ArrayList<>(Objects.requireNonNull(source, "source"));
        copy.sort(comparator);
        return List.copyOf(copy);
    }

    private static <T> Map<String, T> index(
            List<T> values, java.util.function.Function<T, String> idFunction, String kind) {
        Map<String, T> result = new LinkedHashMap<>();
        for (T value : values) {
            String id = idFunction.apply(value);
            if (result.putIfAbsent(id, value) != null) {
                throw new IllegalArgumentException("Duplicate " + kind + ": " + id);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be non-blank");
        }
        return value;
    }

    private static void requireFraction(double value, String name) {
        if (!Double.isFinite(value) || value <= 0d || value > 1d) {
            throw new IllegalArgumentException(name + " must be in (0, 1]");
        }
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }
}
