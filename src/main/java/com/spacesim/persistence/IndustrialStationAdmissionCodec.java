package com.spacesim.persistence;

import java.io.*;
import java.util.Map;
import java.util.TreeMap;

/** Explicit admission of existing infrastructure; decoding never creates resources or ownership. */
public final class IndustrialStationAdmissionCodec {
    /** Maximum bounded registry payload. */
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    private IndustrialStationAdmissionCodec() { }

    /**
     * @param entries existing station IDs and their legal operator IDs
     * @return canonical immutable register
     */
    public static Map<String, String> validate(Map<String, String> entries) {
        if (entries.size() > 4096) throw new IllegalArgumentException("Too many admitted industrial stations");
        var sorted = new TreeMap<String, String>();
        entries.forEach((id, owner) -> {
            if (id == null || owner == null || id.isBlank() || owner.isBlank() || id.length() > 256 || owner.length() > 256)
                throw new IllegalArgumentException("Invalid industrial admission identity");
            sorted.put(id, owner);
        });
        return java.util.Collections.unmodifiableMap(sorted);
    }

    /**
     * @param entries explicitly admitted station/operator identities
     * @return bounded exact payload
     */
    public static byte[] encode(Map<String, String> entries) {
        var checked = validate(entries);
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                out.writeInt(1); out.writeInt(checked.size());
                for (var entry : checked.entrySet()) { out.writeUTF(entry.getKey()); out.writeUTF(entry.getValue()); }
            }
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    /**
     * @param bytes exact bounded payload
     * @return explicitly retained admissions, without inferring them from storage snapshots
     */
    public static Map<String, String> decode(byte[] bytes) {
        if (bytes.length < 8 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Admission payload outside bounds");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != 1) throw new IllegalArgumentException("Unknown admission schema");
            int count = in.readInt();
            if (count < 0 || count > 4096 || count > in.available() / 4) throw new IllegalArgumentException("Invalid admission count");
            var result = new TreeMap<String, String>();
            for (int i = 0; i < count; i++) if (result.putIfAbsent(in.readUTF(), in.readUTF()) != null)
                throw new IllegalArgumentException("Duplicate admitted station");
            if (in.read() != -1) throw new IllegalArgumentException("Trailing admission payload");
            return validate(result);
        } catch (IOException invalid) { throw new IllegalArgumentException("Malformed admission registry", invalid); }
    }
}
