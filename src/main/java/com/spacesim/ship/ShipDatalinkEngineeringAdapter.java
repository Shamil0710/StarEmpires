package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.ShipEngineeringState.InstalledCapability;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Damage-aware projection from fitted engineering state to physical fleet-datalink capacity.
 *
 * <p>The current Stage-17.5I content authors only {@code support_channels}; it does not author a
 * separate network range, latency or transport-noise surface. This adapter therefore exposes only
 * what the content actually owns. A destroyed module provides no link and partial integrity reduces
 * simultaneous support channels linearly, matching the existing damage-aware sensor capability
 * convention without inventing a doctrine or faction bonus.</p>
 */
public final class ShipDatalinkEngineeringAdapter {
    private static final String SUPPORT_CHANNELS = "support_channels";
    private static final double MIN_OPERATIONAL_INTEGRITY = 1e-6d;

    /**
     * Resolves operational fitted datalink modules in deterministic mount order.
     *
     * @param derivedState current common damage-aware ship state
     * @return immutable fitted datalink projections
     */
    public List<FittedDatalink> derive(DerivedShipState derivedState) {
        DerivedShipState state = Objects.requireNonNull(derivedState, "derivedState");
        List<FittedDatalink> result = new ArrayList<>();
        for (InstalledCapability capability : state.installedCapabilities()) {
            if (capability.family() != ModuleFamily.COMMUNICATION_DATALINK) {
                continue;
            }
            double integrity = integrity(capability.parameters());
            if (integrity <= MIN_OPERATIONAL_INTEGRITY) {
                continue;
            }
            double authoredChannels = required(capability.parameters(), SUPPORT_CHANNELS);
            int channels = (int) Math.floor(authoredChannels * integrity + 1e-9d);
            if (channels <= 0) {
                continue;
            }
            result.add(new FittedDatalink(
                    capability.mountId(),
                    capability.moduleId(),
                    channels,
                    integrity));
        }
        result.sort(Comparator.comparing(FittedDatalink::mountId)
                .thenComparing(FittedDatalink::moduleId));
        return List.copyOf(result);
    }

    /**
     * Returns total currently operational simultaneous support channels.
     *
     * @param derivedState current common damage-aware ship state
     * @return non-negative channel count
     */
    public int totalSupportChannels(DerivedShipState derivedState) {
        int result = 0;
        for (FittedDatalink datalink : derive(derivedState)) {
            result = Math.addExact(result, datalink.supportChannels());
        }
        return result;
    }

    private static double integrity(Map<String, Double> parameters) {
        double value = parameters.getOrDefault(DerivedShipCalculator.RUNTIME_INTEGRITY, 1d);
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException("Invalid runtime datalink integrity");
        }
        return value;
    }

    private static double required(Map<String, Double> parameters, String key) {
        Double value = parameters.get(key);
        if (value == null || !Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException("Invalid fitted datalink capability parameter: " + key);
        }
        return value;
    }

    /**
     * One physical fitted datalink projection.
     *
     * @param mountId physical fitted module mount
     * @param moduleId installed module content ID
     * @param supportChannels currently operational simultaneous support channels
     * @param integrity current local module integrity in [0,1]
     */
    public record FittedDatalink(
            String mountId,
            String moduleId,
            int supportChannels,
            double integrity) {
        /**
         * Validates one fitted datalink projection.
         *
         * @param mountId physical fitted module mount
         * @param moduleId installed module content ID
         * @param supportChannels positive operational channel count
         * @param integrity current local module integrity in (0,1]
         */
        public FittedDatalink {
            if (mountId == null || mountId.isBlank() || moduleId == null || moduleId.isBlank()) {
                throw new IllegalArgumentException("datalink mount/module IDs must be non-blank");
            }
            if (supportChannels <= 0) {
                throw new IllegalArgumentException("supportChannels must be positive");
            }
            if (!Double.isFinite(integrity) || integrity <= 0d || integrity > 1d) {
                throw new IllegalArgumentException("integrity must be finite in (0,1]");
            }
        }
    }
}
