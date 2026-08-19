package ssg.legoflow.network.dns.rdata.dnssec;

import ssg.legoflow.network.dns.protocol.RecordType;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
/**
 * Utility for encoding and decoding NSEC/NSEC3 type bit maps (RFC 4034, Section 4.1.2).
 *
 * <p>Type bit maps encode which record types exist at a given name.
 * They are organized by window (block) number, where each window
 * covers 256 type values.
 *
 * @since 0.1.0
 */
public final class TypeBitMaps {

    private TypeBitMaps() {}

    /**
     * Encodes a set of record types into type bit map wire format.
     *
     * @param types the record types to encode
     * @return the encoded bytes
     * @since 0.1.0
     */
    public static byte[] encode(Set<RecordType> types) {
        // Group types by window number (high byte)
        Map<Integer, byte[]> windows = new TreeMap<>();
        for (RecordType rt : types) {
            int typeVal = rt.value();
            int window = typeVal / 256;
            int bit = typeVal % 256;
            int byteIndex = bit / 8;
            int bitIndex = 7 - (bit % 8);

            byte[] bitmap = windows.computeIfAbsent(window, w -> new byte[32]);
            bitmap[byteIndex] |= (1 << bitIndex);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Map.Entry<Integer, byte[]> entry : windows.entrySet()) {
            byte[] bitmap = entry.getValue();
            // Find the last non-zero byte
            int lastNonZero = bitmap.length - 1;
            while (lastNonZero >= 0 && bitmap[lastNonZero] == 0) {
                lastNonZero--;
            }
            if (lastNonZero < 0) continue;

            int bitmapLen = lastNonZero + 1;
            out.write(entry.getKey()); // window number
            out.write(bitmapLen);       // bitmap length
            out.write(bitmap, 0, bitmapLen);
        }
        return out.toByteArray();
    }

    /**
     * Decodes type bit maps from wire format.
     *
     * @param data   the raw bytes
     * @param offset the starting offset
     * @param length the total length
     * @return the set of record types
     * @since 0.1.0
     */
    public static Set<RecordType> decode(byte[] data, int offset, int length) {
        Set<RecordType> types = new TreeSet<>();
        int end = offset + length;
        int pos = offset;

        while (pos < end) {
            if (pos + 2 > end) break;
            int window = data[pos++] & 0xFF;
            int bitmapLen = data[pos++] & 0xFF;
            if (pos + bitmapLen > end) break;

            for (int i = 0; i < bitmapLen; i++) {
                int b = data[pos + i] & 0xFF;
                for (int bit = 0; bit < 8; bit++) {
                    if ((b & (1 << (7 - bit))) != 0) {
                        int typeVal = window * 256 + i * 8 + bit;
                        RecordType rt = RecordType.fromValueOrNull(typeVal);
                        if (rt != null) {
                            types.add(rt);
                        }
                    }
                }
            }
            pos += bitmapLen;
        }
        return types;
    }
}
