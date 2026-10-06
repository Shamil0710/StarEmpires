package com.spacesim.content;

import com.badlogic.gdx.utils.JsonReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Physical yard construction bills, separate from provisional shipyard planner tokens. */
public final class Stage23YardConstructionCatalog {
    /** Current independently authored construction specification schema. */
    public static final int SCHEMA_VERSION = 1;
    /** Built-in physical yard structure and tooling specifications. */
    public static final String DEFAULT_RESOURCE = "data/content/stage23-yard-construction-v1.json";
    private static final int MAX_BYTES = 128 * 1024;
    private final Map<String, Specification> specifications;
    private final String fingerprint;

    private Stage23YardConstructionCatalog(Map<String, Specification> specifications) {
        this.specifications = Collections.unmodifiableMap(new TreeMap<>(specifications));
        StringBuilder canonical = new StringBuilder("stage23-yard-construction:1\n");
        for (var specification : this.specifications.values()) {
            canonical.append(specification.yardDefinitionId()).append('|').append(specification.profileId()).append('|')
                    .append(Double.toHexString(specification.installedMassKg())).append('|')
                    .append(Double.toHexString(specification.requiredWorkSeconds())).append('|')
                    .append(String.join(",", new java.util.TreeSet<>(specification.requiredCapabilityTags()))).append('\n');
            specification.requiredMassByCommodityKg().forEach((id, kg) -> canonical.append(id).append('=')
                    .append(Double.toHexString(kg)).append('\n'));
        }
        try {
            fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    /** @return immutable production specifications without granting inventory or work */
    public static Stage23YardConstructionCatalog loadDefault() { return Defaults.CATALOG; }
    private static final class Defaults {
        private static final Stage23YardConstructionCatalog CATALOG = readDefault();
        private static Stage23YardConstructionCatalog readDefault() {
            try (var input = Stage23YardConstructionCatalog.class.getClassLoader().getResourceAsStream(DEFAULT_RESOURCE)) {
                if (input == null) throw new IllegalStateException("Missing yard construction specifications");
                byte[] bytes = input.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Yard construction document exceeds bounds");
                return parse(new String(bytes, StandardCharsets.UTF_8));
            } catch (IOException failure) { throw new IllegalStateException("Cannot read yard construction specifications", failure); }
        }
    }

    /**
     * Validates authored bindings against actual yard designs and kilogram construction profiles.
     * @param json bounded specification document
     * @return immutable deterministic catalog
     */
    public static Stage23YardConstructionCatalog parse(String json) {
        Objects.requireNonNull(json);
        if (json.isBlank() || json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("Yard construction document outside bounds");
        com.badlogic.gdx.utils.JsonValue root;
        try { root = new JsonReader().parse(json); }
        catch (RuntimeException malformed) { throw new IllegalArgumentException("Malformed yard construction document", malformed); }
        var schema = root.get("schemaVersion");
        if (!root.isObject() || schema == null || !schema.isNumber() || schema.asDouble() != SCHEMA_VERSION)
            throw new IllegalArgumentException("Unsupported yard construction schema");
        var rows = root.get("yards");
        if (rows == null || !rows.isArray() || rows.size < 1 || rows.size > 64)
            throw new IllegalArgumentException("Yard construction bindings outside bounds");
        var yards = Stage22CivilianMiningProductionPath.loadRuntimeShipyards();
        var construction = Stage18FacilityConstructionCatalogLoader.loadDefault();
        var values = new TreeMap<String, Specification>();
        for (var row = rows.child; row != null; row = row.next) {
            if (!row.isObject()) throw new IllegalArgumentException("Yard construction binding must be an object");
            var yardValue = row.get("yardDefinitionId"); var profileValue = row.get("profileId"); var massValue = row.get("installedMassKg");
            if (yardValue == null || !yardValue.isString() || profileValue == null || !profileValue.isString()
                    || massValue == null || !massValue.isNumber()) throw new IllegalArgumentException("Invalid yard construction field type");
            String yardId = row.getString("yardDefinitionId"); String profileId = row.getString("profileId");
            double mass = row.getDouble("installedMassKg");
            if (yards.findYard(yardId) == null || values.containsKey(yardId))
                throw new IllegalArgumentException("Unknown or duplicate yard construction target");
            var profile = construction.findProfile(profileId);
            if (profile == null || !Double.isFinite(mass) || mass <= 0)
                throw new IllegalArgumentException("Invalid physical yard construction profile or mass");
            var bill = new TreeMap<String, Double>();
            for (var input : profile.inputs()) bill.put(input.commodityId(), mass * input.fractionOfInstalledMass());
            values.put(yardId, new Specification(yardId, profileId, mass, bill,
                    mass * profile.workSecondsPerInstalledKg(), profile.requiredCapabilityTags()));
        }
        return new Stage23YardConstructionCatalog(values);
    }

    /** @return deterministic authored yard construction specifications */
    public List<Specification> specifications() { return List.copyOf(specifications.values()); }
    /** @return exact physical bill/work fingerprint independent of old industrial checkpoints */
    public String fingerprint() { return fingerprint; }
    /**
     * Finds an actual construction target.
     * @param yardId existing yard definition identity
     * @return construction specification, or null for a design without a construction binding
     */
    public Specification find(String yardId) { return specifications.get(yardId); }

    /**
     * Actual structural materials and finite engineering work for one installed yard.
     * @param yardDefinitionId existing installed yard design
     * @param profileId existing physical construction composition
     * @param installedMassKg total structural and tooling mass
     * @param requiredMassByCommodityKg exact physical materials, never planner component units
     * @param requiredWorkSeconds finite engineering work
     * @param requiredCapabilityTags necessary fabrication capabilities
     */
    public record Specification(String yardDefinitionId, String profileId, double installedMassKg,
            Map<String, Double> requiredMassByCommodityKg, double requiredWorkSeconds, Set<String> requiredCapabilityTags) {
        /**
         * Validates conservation and finite positive requirements.
         * @param yardDefinitionId existing yard identity
         * @param profileId physical construction profile
         * @param installedMassKg actual total mass
         * @param requiredMassByCommodityKg exact input mass
         * @param requiredWorkSeconds actual engineering work
         * @param requiredCapabilityTags necessary fabrication capabilities
         */
        public Specification {
            if (yardDefinitionId == null || yardDefinitionId.isBlank() || profileId == null || profileId.isBlank()
                    || !Double.isFinite(installedMassKg) || installedMassKg <= 0
                    || !Double.isFinite(requiredWorkSeconds) || requiredWorkSeconds <= 0)
                throw new IllegalArgumentException("Invalid yard construction identity, mass or work");
            var bill = new TreeMap<>(Objects.requireNonNull(requiredMassByCommodityKg));
            double total = 0;
            for (var entry : bill.entrySet()) {
                if (entry.getKey().isBlank() || !Double.isFinite(entry.getValue()) || entry.getValue() <= 0)
                    throw new IllegalArgumentException("Invalid yard construction physical input");
                total += entry.getValue();
            }
            if (!Double.isFinite(total) || Math.abs(total - installedMassKg) > Math.max(1e-6, installedMassKg * 1e-12))
                throw new IllegalArgumentException("Yard construction input mass is not conserved");
            requiredMassByCommodityKg = Collections.unmodifiableMap(bill);
            requiredCapabilityTags = Set.copyOf(Objects.requireNonNull(requiredCapabilityTags));
            if (requiredCapabilityTags.isEmpty() || requiredCapabilityTags.stream().anyMatch(String::isBlank))
                throw new IllegalArgumentException("Yard construction requires physical fabrication capabilities");
        }
    }
}
