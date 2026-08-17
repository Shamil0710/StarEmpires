package com.spacesim.content.ship;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Data-driven thermal/ablation response used when physical beam energy reaches material protection.
 *
 * <p>This catalog is deliberately separate from heavy-impact calibration. Beam damage must not infer
 * vaporization/ablation energy from a kinetic response surface or a hidden global constant. Profiles
 * reference ordinary {@link ShipEngineeringCatalog.MaterialDefinition} IDs and therefore remain part
 * of production content semantics rather than Stage-17.5I-only combat coefficients.</p>
 */
public final class BeamMaterialResponseCatalog {
    private final int schemaVersion;
    private final List<MaterialResponse> responses;
    private final Map<String, MaterialResponse> byMaterialId;
    private final String fingerprint;

    BeamMaterialResponseCatalog(int schemaVersion, List<MaterialResponse> responses) {
        this.schemaVersion = schemaVersion;
        this.responses = Objects.requireNonNull(responses, "responses").stream()
                .sorted(Comparator.comparing(MaterialResponse::materialId))
                .toList();
        LinkedHashMap<String, MaterialResponse> index = new LinkedHashMap<>();
        for (MaterialResponse response : this.responses) {
            if (index.put(response.materialId(), response) != null) {
                throw new IllegalArgumentException("Duplicate beam material response: " + response.materialId());
            }
        }
        this.byMaterialId = Map.copyOf(index);
        this.fingerprint = fingerprint(this.responses);
    }

    /** @return schema version */
    public int schemaVersion() {
        return schemaVersion;
    }

    /** @return deterministic immutable material response rows */
    public List<MaterialResponse> responses() {
        return responses;
    }

    /**
     * Finds beam response by ordinary material ID.
     *
     * @param materialId engineering material ID
     * @return response or {@code null}
     */
    public MaterialResponse findByMaterialId(String materialId) {
        return byMaterialId.get(materialId);
    }

    /** @return lowercase SHA-256 semantic fingerprint */
    public String fingerprint() {
        return fingerprint;
    }

    /**
     * One beam/material thermal-ablation response.
     *
     * @param materialId ordinary engineering material ID
     * @param absorptionFraction fraction of incident beam energy coupled into the material
     * @param ablationSpecificEnergyJPerKg coupled energy required to remove one kilogram from the beam path
     * @param internalResidualCouplingFraction fraction of post-stack residual beam energy routed into local internal damage
     */
    public record MaterialResponse(
            String materialId,
            double absorptionFraction,
            double ablationSpecificEnergyJPerKg,
            double internalResidualCouplingFraction) {
        /**
         * Validates one physical beam response row.
         *
         * @param materialId ordinary engineering material ID
         * @param absorptionFraction beam/material coupling fraction in (0,1]
         * @param ablationSpecificEnergyJPerKg positive specific ablation energy
         * @param internalResidualCouplingFraction residual energy coupling into internal damage in [0,1]
         */
        public MaterialResponse {
            if (materialId == null || materialId.isBlank()) {
                throw new IllegalArgumentException("materialId must be non-blank");
            }
            requireFraction(absorptionFraction, "absorptionFraction", false);
            if (!Double.isFinite(ablationSpecificEnergyJPerKg) || ablationSpecificEnergyJPerKg <= 0d) {
                throw new IllegalArgumentException("ablationSpecificEnergyJPerKg must be finite and positive");
            }
            requireFraction(internalResidualCouplingFraction, "internalResidualCouplingFraction", true);
        }
    }

    private static String fingerprint(List<MaterialResponse> responses) {
        StringBuilder canonical = new StringBuilder();
        for (MaterialResponse value : responses) {
            canonical.append(value.materialId()).append('|')
                    .append(Long.toUnsignedString(Double.doubleToLongBits(value.absorptionFraction()), 16)).append('|')
                    .append(Long.toUnsignedString(Double.doubleToLongBits(value.ablationSpecificEnergyJPerKg()), 16)).append('|')
                    .append(Long.toUnsignedString(Double.doubleToLongBits(value.internalResidualCouplingFraction()), 16))
                    .append('\n');
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JVM", exception);
        }
    }

    private static void requireFraction(double value, String label, boolean zeroAllowed) {
        if (!Double.isFinite(value) || value > 1d || value < 0d || (!zeroAllowed && value == 0d)) {
            throw new IllegalArgumentException(label + " outside accepted fraction range");
        }
    }
}
