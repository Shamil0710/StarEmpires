package com.spacesim.persistence;

import com.spacesim.player.PlayerJournalState;
import com.spacesim.player.PlayerJournalState.Entry;
import com.spacesim.player.PlayerJournalState.Kind;
import java.io.*;
import java.util.ArrayList;
import java.util.Objects;

/** Deterministic bounded native personal journal payload, schema 1. */
public final class PlayerJournalPersistenceCodec {
    /** Maximum payload length, checked before allocating rows. */
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    private PlayerJournalPersistenceCodec() { throw new AssertionError("No instances"); }

    /**
     * Encodes exact retained evidence and acknowledgement.
     * @param state personal journal
     * @return deterministic bytes
     */
    public static byte[] encode(PlayerJournalState state) {
        Objects.requireNonNull(state);
        try {
            var buffer = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(buffer)) {
                out.writeInt(1); out.writeLong(state.nextSequence()); out.writeLong(state.acknowledgedThroughSequence());
                out.writeInt(state.entries().size());
                for (var e : state.entries()) {
                    out.writeLong(e.sequence()); out.writeLong(e.tick()); out.writeUTF(e.kind().name());
                    out.writeUTF(e.action()); out.writeUTF(e.endpoint()); out.writeUTF(e.subject());
                    out.writeLong(e.quantity()); out.writeLong(e.walletDeltaMilliCredits()); out.writeLong(e.fleetId());
                }
            }
            byte[] result = buffer.toByteArray();
            if (result.length > MAX_BYTES) throw new IllegalArgumentException("Journal too large");
            return result;
        } catch (IOException e) { throw new IllegalStateException("Cannot encode journal", e); }
    }

    /**
     * Decodes exact bounded evidence; rejects truncation, unknown kinds and trailing bytes.
     * @param bytes native schema-1 payload
     * @return validated personal journal
     */
    public static PlayerJournalState decode(byte[] bytes) {
        Objects.requireNonNull(bytes);
        if (bytes.length < 24 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Journal size outside bounds");
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != 1) throw new IllegalArgumentException("Unknown journal schema");
            long next = in.readLong(), read = in.readLong();
            int count = in.readInt();
            if (count < 0 || count > PlayerJournalState.CAPACITY || count > in.available() / 56)
                throw new IllegalArgumentException("Journal count outside bounds");
            var entries = new ArrayList<Entry>(count);
            for (int i = 0; i < count; i++) entries.add(new Entry(in.readLong(), in.readLong(), Kind.valueOf(in.readUTF()),
                    in.readUTF(), in.readUTF(), in.readUTF(), in.readLong(), in.readLong(), in.readLong()));
            if (in.read() != -1) throw new IllegalArgumentException("Trailing journal bytes");
            return new PlayerJournalState(next, read, entries);
        } catch (IOException e) { throw new IllegalArgumentException("Truncated journal", e); }
    }
}
