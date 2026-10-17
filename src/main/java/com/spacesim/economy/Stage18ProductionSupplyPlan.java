package com.spacesim.economy;

import com.spacesim.content.Stage18ManufacturingCatalog;
import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18RefiningCatalog;
import java.util.*;

/** Pure authored dependency expansion; it neither reserves stock nor issues production orders. */
public final class Stage18ProductionSupplyPlan {
    private static final double MASS_EPSILON_KG = 1e-9;
    private Stage18ProductionSupplyPlan() { }

    /** Existing player process families in execution order. */
    public enum Process {
        /** Gross feedstock processing with authored yield and waste. */
        REFINING,
        /** Finished component mass fabrication. */
        COMPONENTS
    }
    /**
     * One whole-kilogram player processing request.
     * @param process required process family
     * @param recipeId existing authored recipe
     * @param outputCommodityId material produced
     * @param kilograms gross refining mass or finished component mass
     * @param outputMassKg physical output after authored yield
     * @param inputs exact required source masses
     */
    public record Batch(Process process, String recipeId, String outputCommodityId, int kilograms,
                        double outputMassKg, Map<String, Double> inputs) {
        /**
         * Copies the batch inputs into immutable deterministic order.
         * @param process process family
         * @param recipeId authored recipe
         * @param outputCommodityId output material
         * @param kilograms requested mass
         * @param outputMassKg resulting output mass
         * @param inputs required input masses
         */
        public Batch { inputs = Collections.unmodifiableMap(new TreeMap<>(inputs)); }
    }
    /**
     * Read-only dependency quantities for one finished product.
     * @param productId existing finished product
     * @param directBill exact final assembly inputs
     * @param materialDemand aggregated direct and component material inputs
     * @param feedstockDemand aggregated gross refining inputs
     * @param missingFeedstock whole-kilogram deliveries after existing stock credit
     * @param batches refining then component processing requests
     */
    public record Plan(String productId, Map<String, Double> directBill,
                       Map<String, Double> materialDemand, Map<String, Double> feedstockDemand,
                       Map<String, Double> missingFeedstock, List<Batch> batches) {
        /**
         * Copies all quantities and process steps without retaining mutable caller state.
         * @param productId finished product
         * @param directBill final assembly input masses
         * @param materialDemand aggregated material inputs
         * @param feedstockDemand aggregated raw inputs
         * @param missingFeedstock required whole-kilogram deliveries
         * @param batches processing requests in order
         */
        public Plan {
            directBill = immutable(directBill); materialDemand = immutable(materialDemand);
            feedstockDemand = immutable(feedstockDemand); missingFeedstock = immutable(missingFeedstock);
            batches = List.copyOf(batches);
        }
    }

    /**
     * Plans one whole product from available own-station stock. Existing components reduce component
     * batches, all shared material demands are summed before available material is counted once,
     * and refining inputs are summed before feedstock is counted once. Batch requests round upward
     * to the existing integer-kilogram player command interface; feedstock deliveries also round
     * upward to whole kilograms and excess output remains physical stock.
     * Pending output, foreign stores and cargo are excluded until actually delivered/completed.
     * @param productId authored finished product
     * @param available available unreserved own-station commodity mass
     * @param products authored physical product registry
     * @param manufacturing authored component and product recipes
     * @param refining authored refining recipes
     * @return exact dependency plan for one product, without mutation
     */
    public static Plan forProduct(String productId, Map<String, Double> available,
            Stage18ManufacturingProductRegistry products, Stage18ManufacturingCatalog manufacturing,
            Stage18RefiningCatalog refining) {
        Objects.requireNonNull(available).forEach((id, mass) -> {
            if (id == null || mass == null || !Double.isFinite(mass) || mass < 0)
                throw new IllegalArgumentException("Invalid available production stock");
        });
        var product = products.findProduct(productId);
        var binding = manufacturing.findProductBinding(productId);
        if (product == null || binding == null) throw new IllegalArgumentException("Unknown production product");
        var direct = new TreeMap<String, Double>();
        manufacturing.findProductProfile(binding.profileId()).inputs().forEach(i ->
                add(direct, i.commodityId(), product.unitMassKg() * i.fractionOfOutputMass()));
        var material = new TreeMap<String, Double>();
        var componentBatches = new ArrayList<Batch>();
        for (var demand : direct.entrySet()) {
            var recipe = manufacturing.getComponentRecipes().stream()
                    .filter(r -> r.outputCommodityId().equals(demand.getKey())).findFirst().orElse(null);
            if (recipe == null) add(material, demand.getKey(), demand.getValue());
            else {
                double missing = Math.max(0, demand.getValue() - available.getOrDefault(demand.getKey(), 0d));
                if (missing <= MASS_EPSILON_KG) continue;
                int kilograms = kilograms(missing);
                var inputs = new TreeMap<String, Double>();
                recipe.inputs().forEach(i -> add(inputs, i.commodityId(), kilograms * i.fractionOfOutputMass()));
                inputs.forEach((id, mass) -> add(material, id, mass));
                componentBatches.add(new Batch(Process.COMPONENTS, recipe.id(), recipe.outputCommodityId(), kilograms, kilograms, inputs));
            }
        }
        var feedstock = new TreeMap<String, Double>();
        var batches = new ArrayList<Batch>();
        for (var demand : material.entrySet()) {
            double missing = Math.max(0, demand.getValue() - available.getOrDefault(demand.getKey(), 0d));
            if (missing <= MASS_EPSILON_KG) continue;
            var recipe = refining.getRecipes().stream().filter(r -> r.outputCommodityId().equals(demand.getKey()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("No refining recipe for " + demand.getKey()));
            int kilograms = kilograms(missing / recipe.outputMassFraction());
            var inputs = new TreeMap<String, Double>();
            recipe.inputs().forEach(i -> add(inputs, i.commodityId(), kilograms * i.fractionOfInputMass()));
            inputs.forEach((id, mass) -> add(feedstock, id, mass));
            batches.add(new Batch(Process.REFINING, recipe.id(), recipe.outputCommodityId(), kilograms,
                    kilograms * recipe.outputMassFraction(), inputs));
        }
        batches.addAll(componentBatches);
        var missing = new TreeMap<String, Double>();
        feedstock.forEach((id, mass) -> {
            double deficit = Math.max(0, mass - available.getOrDefault(id, 0d));
            if (deficit > MASS_EPSILON_KG) missing.put(id, (double) kilograms(deficit));
        });
        return new Plan(productId, direct, material, feedstock, missing, batches);
    }

    private static int kilograms(double mass) {
        double nearest = Math.rint(mass);
        if (Math.abs(mass - nearest) <= MASS_EPSILON_KG) mass = nearest;
        double rounded = Math.ceil(mass);
        if (!Double.isFinite(rounded) || rounded <= 0 || rounded > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Production batch exceeds player kilogram limit");
        return (int) rounded;
    }
    private static void add(Map<String, Double> target, String id, double mass) {
        double sum = target.getOrDefault(id, 0d) + mass;
        if (!Double.isFinite(sum) || sum < 0) throw new IllegalArgumentException("Production demand overflow");
        target.put(id, sum);
    }
    private static Map<String, Double> immutable(Map<String, Double> map) {
        return Collections.unmodifiableMap(new TreeMap<>(map));
    }
}
