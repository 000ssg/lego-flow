package ssg.legoflow.media.rtp.rtcp;

import java.util.Objects;

/**
 * SDES (Source Description) item (RFC 3550 Section 6.5).
 *
 * <p>Each SDES item consists of a type identifier and a text value.
 *
 * @param type  the SDES item type
 * @param value the text value
 * @since 0.1.0
 */
public record SdesItem(Type type, String value) {

    /**
     * Creates an SDES item with validation.
     */
    public SdesItem {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
        if (value.length() > 255) {
            throw new IllegalArgumentException("SDES item value must be <= 255 bytes: " + value.length());
        }
    }

    /**
     * SDES item types as defined in RFC 3550 Section 6.5.
     *
     * @since 0.1.0
     */
    public enum Type {
        /** End of SDES list (used internally). */
        END(0),
        /** Canonical name -- unique within session. */
        CNAME(1),
        /** User name. */
        NAME(2),
        /** Electronic mail address. */
        EMAIL(3),
        /** Phone number. */
        PHONE(4),
        /** Geographic user location. */
        LOC(5),
        /** Application or tool name. */
        TOOL(6),
        /** Notice/status text. */
        NOTE(7),
        /** Private extension. */
        PRIV(8);

        private final int code;

        Type(int code) {
            this.code = code;
        }

        /**
         * Returns the numeric code for this item type.
         *
         * @return the type code
         */
        public int code() {
            return code;
        }

        /**
         * Returns the item type for the given code.
         *
         * @param code the type code
         * @return the item type
         * @throws IllegalArgumentException if the code is unknown
         */
        public static Type fromCode(int code) {
            for (Type t : values()) {
                if (t.code == code) {
                    return t;
                }
            }
            throw new IllegalArgumentException("Unknown SDES item type: " + code);
        }
    }
}
