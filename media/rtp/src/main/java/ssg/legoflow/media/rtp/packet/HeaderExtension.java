package ssg.legoflow.media.rtp.packet;

import java.util.Arrays;
import java.util.Objects;

/**
 * RTP header extension as defined in RFC 3550 Section 5.3.1.
 *
 * <p>The header extension contains a 16-bit profile-specific identifier
 * and variable-length extension data. The extension data length is
 * measured in 32-bit words.
 *
 * @param profile the profile-specific extension identifier (16 bits)
 * @param data    the extension data (length must be a multiple of 4 bytes)
 * @since 0.1.0
 */
public record HeaderExtension(int profile, byte[] data) {

    /**
     * Creates a header extension with validation.
     */
    public HeaderExtension {
        Objects.requireNonNull(data, "data");
        if (profile < 0 || profile > 0xFFFF) {
            throw new IllegalArgumentException("Profile must be 0-65535: " + profile);
        }
        if (data.length % 4 != 0) {
            throw new IllegalArgumentException(
                    "Extension data length must be a multiple of 4 bytes: " + data.length);
        }
        data = data.clone();
    }

    /**
     * Returns the extension data length in 32-bit words.
     *
     * @return the length in words
     */
    public int lengthInWords() {
        return data.length / 4;
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HeaderExtension that)) return false;
        return profile == that.profile && Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return 31 * profile + Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return "HeaderExtension[profile=0x%04X, length=%d words]".formatted(profile, lengthInWords());
    }
}
