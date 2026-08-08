package ssg.legoflow.media.rtp.rtcp;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * RTCP Application-Defined (APP) packet (RFC 3550 Section 6.7).
 *
 * <p>Intended for experimental use and for features that carry
 * application-specific information. The name field is a 4-character ASCII
 * identifier chosen by the application.
 *
 * @param ssrc    the SSRC/CSRC identifier
 * @param subtype the application-dependent subtype (0-31)
 * @param name    the 4-character ASCII name
 * @param data    the application-dependent data (length must be a multiple of 4 bytes)
 * @since 0.1.0
 */
public record ApplicationDefined(
        long ssrc,
        int subtype,
        String name,
        byte[] data
) implements RtcpPacket {

    /**
     * Creates an application-defined packet with validation.
     */
    public ApplicationDefined {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(data, "data");
        if (subtype < 0 || subtype > 31) {
            throw new IllegalArgumentException("Subtype must be 0-31: " + subtype);
        }
        byte[] nameBytes = name.getBytes(StandardCharsets.US_ASCII);
        if (nameBytes.length != 4) {
            throw new IllegalArgumentException("Name must be exactly 4 ASCII characters: " + name);
        }
        if (data.length % 4 != 0) {
            throw new IllegalArgumentException(
                    "APP data length must be a multiple of 4 bytes: " + data.length);
        }
        data = data.clone();
    }

    @Override
    public int packetType() {
        return PT_APP;
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApplicationDefined that)) return false;
        return ssrc == that.ssrc && subtype == that.subtype
                && name.equals(that.name) && Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ssrc, subtype, name, Arrays.hashCode(data));
    }

    @Override
    public String toString() {
        return "ApplicationDefined[ssrc=0x%08X, subtype=%d, name=%s, data=%d bytes]"
                .formatted(ssrc, subtype, name, data.length);
    }
}
