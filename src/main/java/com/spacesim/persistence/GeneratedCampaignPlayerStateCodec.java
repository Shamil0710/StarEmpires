package com.spacesim.persistence;

import com.spacesim.player.PlayableWorldState;
import com.spacesim.player.PlayerState;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Bounded player-only payload for the composed generated checkpoint.
 *
 * <p>Delegates every player field to the existing playable codec. No duplicate WorldState, second
 * cargo balance, starter grant or simulation clock is encoded. Absence is explicit and remains
 * distinct from an initialized player with a zero wallet.</p>
 */
public final class GeneratedCampaignPlayerStateCodec {
    private static final int MAGIC = 0x53323350; // S23P
    private static final int FILE_VERSION = 1;
    private static final int MAX_BYTES = 32 * 1024 * 1024;

    private GeneratedCampaignPlayerStateCodec() {
        throw new AssertionError("No instances");
    }

    /**
     * Encodes exact existing player data with bounded deterministic field ordering.
     *
     * @param player exact state, or null before initialization
     * @return current player payload
     */
    public static byte[] encode(PlayerState player) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                out.writeInt(MAGIC);
                out.writeInt(FILE_VERSION);
                out.writeInt(PlayableWorldState.CURRENT_VERSION);
                out.writeBoolean(player != null);
                if (player != null) {
                    PlayableWorldStateCodec.writePlayer(out, player);
                }
            }
            byte[] result = buffer.toByteArray();
            requireSize(result);
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot encode in-memory player payload", exception);
        }
    }

    /**
     * Decodes current player data without migrating, granting or repairing player assets.
     *
     * @param bytes bounded native player payload
     * @return exact player, or null for explicitly absent state
     */
    public static PlayerState decode(byte[] bytes) {
        requireSize(Objects.requireNonNull(bytes, "bytes"));
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC || in.readInt() != FILE_VERSION
                    || in.readInt() != PlayableWorldState.CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported generated player payload identity");
            }
            int presence = in.readUnsignedByte();
            if (presence != 0 && presence != 1) {
                throw new IllegalArgumentException("Invalid player presence marker");
            }
            PlayerState result = presence == 1
                    ? PlayableWorldStateCodec.readPlayer(in, PlayableWorldState.CURRENT_VERSION) : null;
            if (in.read() != -1) {
                throw new IllegalArgumentException("Trailing generated player payload bytes");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Truncated generated player payload", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegal) {
                throw illegal;
            }
            throw new IllegalArgumentException("Invalid generated player payload", exception);
        }
    }

    private static void requireSize(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Generated player payload size outside bounds");
        }
    }
}
