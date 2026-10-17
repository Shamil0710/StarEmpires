package com.spacesim.persistence;

import com.spacesim.world.SmallCraftId;
import java.io.*;
import java.util.List;
import java.util.TreeSet;

/** Bounded personal property register, independent of faction, host and wing assignment. */
public final class PersonalCraftOwnershipCodec {
    /** Maximum payload size. */
    public static final int MAX_BYTES = 8 + 8 * 100_000;
    private PersonalCraftOwnershipCodec() { }

    /**
     * @param ids saved rights, including lost craft
     * @return canonical bounded payload
     */
    public static byte[] encode(List<SmallCraftId> ids) {
        var sorted = new TreeSet<>(ids);
        if (sorted.size() != ids.size() || ids.size() > 100_000)
            throw new IllegalArgumentException("Invalid personal craft property register");
        try {
            var buffer = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(buffer)) {
                out.writeInt(1); out.writeInt(sorted.size());
                for (var id : sorted) out.writeLong(id.value());
            }
            return buffer.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    /**
     * @param bytes exact saved rights
     * @return immutable register without inferred acquisitions
     */
    public static List<SmallCraftId> decode(byte[] bytes) {
        if (bytes.length < 8 || bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Personal craft property payload outside bounds");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != 1) throw new IllegalArgumentException("Unknown personal craft property version");
            int count = in.readInt();
            if (count < 0 || count > 100_000 || in.available() != 8L * count)
                throw new IllegalArgumentException("Invalid personal craft property count");
            var ids = new TreeSet<SmallCraftId>();
            for (int i = 0; i < count; i++) if (!ids.add(new SmallCraftId(in.readLong())))
                throw new IllegalArgumentException("Duplicate personal craft property identity");
            return List.copyOf(ids);
        } catch (IOException error) { throw new IllegalArgumentException("Malformed personal craft property payload", error); }
    }
}
