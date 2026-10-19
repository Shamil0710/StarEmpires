package com.spacesim.persistence;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * The pilot's terms for selling one commodity from an own station to other fleets: a price (or the
 * station's market price) and a reserve that is never sold. Absent terms mean market price, no reserve.
 *
 * @param stationId personally owned station
 * @param commodityId commodity offered
 * @param askMilliCreditsPerKg the pilot's price per kilogram, or 0 for the station's market price
 * @param reserveKg stock kept back from any sale
 */
public record StationSalePolicy(String stationId, String commodityId, long askMilliCreditsPerKg, long reserveKg) {
    /** Maximum bounded payload. */
    public static final int MAX_BYTES = 1024 * 1024;
    /** Upper bound of a pilot price, far above any market quote. */
    public static final long MAX_ASK_MILLI_CREDITS_PER_KG = 100_000_000L;
    private static final int MAX_POLICIES = 4096;

    /** Validates one set of terms. */
    public StationSalePolicy {
        if (stationId == null || commodityId == null || stationId.isBlank() || commodityId.isBlank()
                || stationId.length() > 256 || commodityId.length() > 256)
            throw new IllegalArgumentException("Station sale terms require a station and a commodity");
        if (askMilliCreditsPerKg < 0 || askMilliCreditsPerKg > MAX_ASK_MILLI_CREDITS_PER_KG || reserveKg < 0)
            throw new IllegalArgumentException("Station sale price and reserve must be non-negative and bounded");
    }

    /** @return whether these terms are the defaults (market price, no reserve) and need not be kept */
    public boolean defaults() { return askMilliCreditsPerKg == 0 && reserveKg == 0; }

    /**
     * @param policies terms in any order
     * @return canonical sorted list without default terms or duplicate station/commodity pairs
     */
    public static List<StationSalePolicy> validate(List<StationSalePolicy> policies) {
        if (Objects.requireNonNull(policies, "policies").size() > MAX_POLICIES)
            throw new IllegalArgumentException("Too many station sale terms");
        var keys = new HashSet<String>();
        var sorted = new ArrayList<StationSalePolicy>();
        for (var policy : policies) {
            Objects.requireNonNull(policy, "policy");
            if (!keys.add(policy.stationId() + '\u0000' + policy.commodityId()))
                throw new IllegalArgumentException("Duplicate station sale terms");
            if (!policy.defaults()) sorted.add(policy);
        }
        sorted.sort(Comparator.comparing(StationSalePolicy::stationId).thenComparing(StationSalePolicy::commodityId));
        return List.copyOf(sorted);
    }

    /**
     * @param policies terms
     * @return bounded exact payload
     */
    public static byte[] encode(List<StationSalePolicy> policies) {
        var checked = validate(policies);
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                out.writeInt(1);
                out.writeInt(checked.size());
                for (var policy : checked) {
                    out.writeUTF(policy.stationId());
                    out.writeUTF(policy.commodityId());
                    out.writeLong(policy.askMilliCreditsPerKg());
                    out.writeLong(policy.reserveKg());
                }
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * @param bytes exact bounded payload
     * @return canonical terms
     */
    public static List<StationSalePolicy> decode(byte[] bytes) {
        if (bytes.length < 8 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Station sale payload outside bounds");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != 1) throw new IllegalArgumentException("Unknown station sale schema");
            int count = in.readInt();
            if (count < 0 || count > MAX_POLICIES || count > in.available() / 20)
                throw new IllegalArgumentException("Invalid station sale count");
            var result = new ArrayList<StationSalePolicy>();
            for (int i = 0; i < count; i++)
                result.add(new StationSalePolicy(in.readUTF(), in.readUTF(), in.readLong(), in.readLong()));
            if (in.read() != -1) throw new IllegalArgumentException("Trailing station sale payload");
            var checked = validate(result);
            if (!checked.equals(result)) throw new IllegalArgumentException("Station sale terms are not canonical");
            return checked;
        } catch (IOException invalid) {
            throw new IllegalArgumentException("Malformed station sale terms", invalid);
        }
    }
}
