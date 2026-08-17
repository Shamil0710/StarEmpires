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
 * Immutable Stage-18C catalog of feedstock-to-material refining recipes.
 *
 * <p>Recipes are expressed per kilogram of gross input batch. Input fractions must sum to one and
 * output plus discarded mass must close to the same input mass. Recipes consume only Stage-18
 * extracted feedstocks and produce only Stage-18 engineering materials or industrial consumables;
 * component fabrication remains owned by Stage 18D.</p>
 */
public final class Stage18RefiningCatalog {
    private final int schemaVersion;
    private final List<RefiningRecipeDefinition> recipes;
    private final Map<String, RefiningRecipeDefinition> recipesById;
    private final String fingerprint;

    Stage18RefiningCatalog(int schemaVersion, List<RefiningRecipeDefinition> recipes) {
        this.schemaVersion = schemaVersion;
        List<RefiningRecipeDefinition> copy = new ArrayList<>(Objects.requireNonNull(recipes, "recipes"));
        copy.sort(Comparator.comparing(RefiningRecipeDefinition::id));
        this.recipes = List.copyOf(copy);
        Map<String, RefiningRecipeDefinition> index = new LinkedHashMap<>();
        for (RefiningRecipeDefinition recipe : this.recipes) {
            if (index.putIfAbsent(recipe.id(), recipe) != null) {
                throw new IllegalArgumentException("Duplicate refining recipe: " + recipe.id());
            }
        }
        this.recipesById = Collections.unmodifiableMap(index);
        this.fingerprint = computeFingerprint();
    }

    /** @return refining schema version */
    public int getSchemaVersion() {
        return schemaVersion;
    }

    /** @return deterministic immutable recipe definitions */
    public List<RefiningRecipeDefinition> getRecipes() {
        return recipes;
    }

    /** @return lowercase SHA-256 fingerprint of refining semantics */
    public String getFingerprint() {
        return fingerprint;
    }

    /** @return recipe definition for a stable ID, or {@code null} */
    public RefiningRecipeDefinition findRecipe(String id) {
        return recipesById.get(id);
    }

    private String computeFingerprint() {
        StringBuilder canonical = new StringBuilder(8192);
        canonical.append("schema=").append(schemaVersion).append('\n');
        for (RefiningRecipeDefinition recipe : recipes) {
            canonical.append("recipe|").append(recipe.id()).append('|')
                    .append(recipe.displayName()).append('|');
            for (RecipeInputDefinition input : recipe.inputs()) {
                canonical.append(input.commodityId()).append('=')
                        .append(Double.toHexString(input.fractionOfInputMass())).append(',');
            }
            canonical.append('|').append(recipe.outputCommodityId()).append('|')
                    .append(Double.toHexString(recipe.outputMassFraction())).append('|')
                    .append(Double.toHexString(recipe.discardedMassFraction())).append('|')
                    .append(String.join(",", recipe.requiredCapabilityTags())).append('|')
                    .append(Double.toHexString(recipe.energyJPerInputKg())).append('|')
                    .append(Double.toHexString(recipe.workSecondsPerInputKg())).append('|')
                    .append(Double.toHexString(recipe.maintenanceWorkSecondsPerInputKg())).append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide mandatory SHA-256", exception);
        }
    }

    /** One feedstock share in a gross refining input batch. */
    public record RecipeInputDefinition(String commodityId, double fractionOfInputMass) {
        public RecipeInputDefinition {
            requireText(commodityId, "input commodityId");
            requireFraction(fractionOfInputMass, "fractionOfInputMass");
        }
    }

    /**
     * One data-driven refining recipe.
     *
     * @param id stable recipe ID
     * @param displayName diagnostic/display name
     * @param inputs extracted-feedstock fractions that sum to one
     * @param outputCommodityId engineering material or industrial consumable produced
     * @param outputMassFraction useful output fraction of the gross input batch
     * @param discardedMassFraction tailings/byproduct mass not retained by this baseline recipe
     * @param requiredCapabilityTags physical process capabilities required by the recipe
     * @param energyJPerInputKg process energy per kilogram of gross input
     * @param workSecondsPerInputKg engineering work-seconds per kilogram of gross input
     * @param maintenanceWorkSecondsPerInputKg maintenance work-seconds per kilogram of gross input
     */
    public record RefiningRecipeDefinition(
            String id,
            String displayName,
            List<RecipeInputDefinition> inputs,
            String outputCommodityId,
            double outputMassFraction,
            double discardedMassFraction,
            Set<String> requiredCapabilityTags,
            double energyJPerInputKg,
            double workSecondsPerInputKg,
            double maintenanceWorkSecondsPerInputKg) {
        private static final double EPSILON = 1e-9d;

        public RefiningRecipeDefinition {
            requireText(id, "recipe id");
            requireText(displayName, "recipe displayName");
            Objects.requireNonNull(inputs, "inputs");
            if (inputs.isEmpty()) {
                throw new IllegalArgumentException("Refining recipe must contain inputs: " + id);
            }
            List<RecipeInputDefinition> sortedInputs = new ArrayList<>(inputs);
            sortedInputs.sort(Comparator.comparing(RecipeInputDefinition::commodityId));
            TreeSet<String> inputIds = new TreeSet<>();
            double inputTotal = 0d;
            for (RecipeInputDefinition input : sortedInputs) {
                if (!inputIds.add(input.commodityId())) {
                    throw new IllegalArgumentException("Duplicate recipe input " + input.commodityId() + " for " + id);
                }
                inputTotal += input.fractionOfInputMass();
            }
            if (Math.abs(inputTotal - 1d) > EPSILON) {
                throw new IllegalArgumentException("Recipe input fractions must sum to 1: " + id);
            }
            inputs = List.copyOf(sortedInputs);
            requireText(outputCommodityId, "outputCommodityId");
            requireFraction(outputMassFraction, "outputMassFraction");
            requireNonNegativeFraction(discardedMassFraction, "discardedMassFraction");
            if (Math.abs(outputMassFraction + discardedMassFraction - 1d) > EPSILON) {
                throw new IllegalArgumentException("Recipe output plus discarded mass must sum to 1: " + id);
            }
            requiredCapabilityTags = immutableSortedSet(requiredCapabilityTags, "requiredCapabilityTags");
            if (requiredCapabilityTags.isEmpty()) {
                throw new IllegalArgumentException("Refining recipe must require at least one capability: " + id);
            }
            requirePositive(energyJPerInputKg, "energyJPerInputKg");
            requirePositive(workSecondsPerInputKg, "workSecondsPerInputKg");
            requirePositive(maintenanceWorkSecondsPerInputKg, "maintenanceWorkSecondsPerInputKg");
        }
    }

    private static Set<String> immutableSortedSet(Set<String> source, String name) {
        Objects.requireNonNull(source, name);
        TreeSet<String> copy = new TreeSet<>();
        for (String value : source) {
            requireText(value, name + " entry");
            if (!copy.add(value)) {
                throw new IllegalArgumentException("Duplicate " + name + " entry: " + value);
            }
        }
        return Collections.unmodifiableSet(copy);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be non-blank");
        }
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireFraction(double value, String name) {
        if (!Double.isFinite(value) || value <= 0d || value > 1d) {
            throw new IllegalArgumentException(name + " must be in (0, 1]");
        }
    }

    private static void requireNonNegativeFraction(double value, String name) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(name + " must be in [0, 1]");
        }
    }
}
