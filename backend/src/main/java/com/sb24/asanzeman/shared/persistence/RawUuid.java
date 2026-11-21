package com.sb24.asanzeman.shared.persistence;

import java.nio.ByteBuffer;
import java.util.UUID;

/** A {@link UUID} as the 16 bytes of an Oracle {@code RAW(16)} column (big-endian), and back. */
public final class RawUuid {

    private RawUuid() {
    }

    /** @return the 16 bytes, or {@code null} for {@code null} */
    public static byte[] toBytes(UUID id) {
        if (id == null) {
            return null;
        }
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits())
                .array();
    }

    /** @return the id, or {@code null} for {@code null} */
    public static UUID fromBytes(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
