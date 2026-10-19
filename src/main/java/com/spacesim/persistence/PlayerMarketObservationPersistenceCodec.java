package com.spacesim.persistence;

import com.spacesim.player.PlayerMarketObservationState;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Exact bounded codec for the player's observed market quotes; decoding never reconstructs prices. */
public final class PlayerMarketObservationPersistenceCodec {
    /** Largest accepted payload. */
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final int SCHEMA = 1;
    /** Bytes of one encoded quote: tick, buy and sell as longs. */
    private static final int QUOTE_BYTES = 3 * Long.BYTES;

    private PlayerMarketObservationPersistenceCodec() {
    }

    /**
     * @param state observed quotes
     * @return canonical payload
     */
    public static byte[] encode(PlayerMarketObservationState state) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                out.writeInt(SCHEMA);
                out.writeInt(state.series().size());
                for (var series : state.series()) {
                    out.writeUTF(series.stationId());
                    out.writeUTF(series.commodityId());
                    out.writeInt(series.quotes().size());
                    for (var quote : series.quotes()) {
                        out.writeLong(quote.tick());
                        out.writeLong(quote.buyMilliPerKg());
                        out.writeLong(quote.sellMilliPerKg());
                    }
                }
            }
            if (bytes.size() > MAX_BYTES) throw new IllegalArgumentException("Market observations exceed bounded size");
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * @param bytes exact payload
     * @return validated observations
     */
    public static PlayerMarketObservationState decode(byte[] bytes) {
        if (bytes.length < 2 * Integer.BYTES || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Market observation payload outside bounds");
        }
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != SCHEMA) throw new IllegalArgumentException("Unknown market observation schema");
            int seriesCount = in.readInt();
            if (seriesCount < 0 || seriesCount > PlayerMarketObservationState.MAX_SERIES) {
                throw new IllegalArgumentException("Invalid market observation series count");
            }
            var series = new ArrayList<PlayerMarketObservationState.Series>(Math.min(seriesCount, 64));
            for (int index = 0; index < seriesCount; index++) {
                String station = in.readUTF();
                String commodity = in.readUTF();
                int quoteCount = in.readInt();
                if (quoteCount <= 0 || quoteCount > PlayerMarketObservationState.MAX_QUOTES_PER_SERIES
                        || (long) quoteCount * QUOTE_BYTES > in.available()) {
                    throw new IllegalArgumentException("Invalid market observation quote count");
                }
                var quotes = new ArrayList<PlayerMarketObservationState.Quote>(quoteCount);
                for (int quote = 0; quote < quoteCount; quote++) {
                    quotes.add(new PlayerMarketObservationState.Quote(in.readLong(), in.readLong(), in.readLong()));
                }
                series.add(new PlayerMarketObservationState.Series(station, commodity, List.copyOf(quotes)));
            }
            if (in.read() != -1) throw new IllegalArgumentException("Trailing market observation payload");
            return new PlayerMarketObservationState(series);
        } catch (IOException invalid) {
            throw new IllegalArgumentException("Malformed market observations", invalid);
        }
    }
}
