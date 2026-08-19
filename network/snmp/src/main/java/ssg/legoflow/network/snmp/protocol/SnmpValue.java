package ssg.legoflow.network.snmp.protocol;

import ssg.legoflow.network.common.oid.ObjectIdentifier;
import java.util.Arrays;
/**
 * Sealed interface representing all SNMP data value types.
 *
 * <p>Each permitted implementation corresponds to an SMIv2 data type used in
 * SNMP variable bindings. The sealed hierarchy enables exhaustive pattern
 * matching in {@code switch} expressions.
 *
 * @since 0.1.0
 */
public sealed interface SnmpValue {

    /**
     * 32-bit signed integer (SMIv2 Integer32 / INTEGER).
     *
     * @param value the integer value
     * @since 0.1.0
     */
    record Integer32(int value) implements SnmpValue {}

    /**
     * 32-bit unsigned counter (SMIv2 Counter32), wraps at 2^32 - 1.
     *
     * @param value the counter value as unsigned long
     * @since 0.1.0
     */
    record Counter32(long value) implements SnmpValue {
        /**
         * Creates a Counter32 with validation.
         *
         * @param value the counter value (0 to 2^32 - 1)
         */
        public Counter32 {
            if (value < 0 || value > 0xFFFFFFFFL) {
                throw new IllegalArgumentException("Counter32 value out of range: " + value);
            }
        }
    }

    /**
     * 64-bit unsigned counter (SMIv2 Counter64), wraps at 2^64 - 1.
     *
     * @param value the counter value as unsigned long
     * @since 0.1.0
     */
    record Counter64(long value) implements SnmpValue {}

    /**
     * 32-bit unsigned gauge (SMIv2 Gauge32 / Unsigned32), latches at maximum.
     *
     * @param value the gauge value as unsigned long
     * @since 0.1.0
     */
    record Gauge32(long value) implements SnmpValue {
        /**
         * Creates a Gauge32 with validation.
         *
         * @param value the gauge value (0 to 2^32 - 1)
         */
        public Gauge32 {
            if (value < 0 || value > 0xFFFFFFFFL) {
                throw new IllegalArgumentException("Gauge32 value out of range: " + value);
            }
        }
    }

    /**
     * Time ticks in hundredths of a second since an epoch (SMIv2 TimeTicks).
     *
     * @param value the ticks value as unsigned long
     * @since 0.1.0
     */
    record TimeTicks(long value) implements SnmpValue {
        /**
         * Creates a TimeTicks with validation.
         *
         * @param value the ticks value (0 to 2^32 - 1)
         */
        public TimeTicks {
            if (value < 0 || value > 0xFFFFFFFFL) {
                throw new IllegalArgumentException("TimeTicks value out of range: " + value);
            }
        }
    }

    /**
     * Arbitrary byte string (SMIv2 OCTET STRING).
     *
     * @param value the byte data
     * @since 0.1.0
     */
    record OctetString(byte[] value) implements SnmpValue {
        /**
         * Creates an OctetString with defensive copy.
         *
         * @param value the byte data (must not be null)
         */
        public OctetString {
            if (value == null) {
                throw new IllegalArgumentException("Value must not be null");
            }
            value = value.clone();
        }

        /**
         * Returns a copy of the byte data.
         *
         * @return copy of the value
         */
        @Override
        public byte[] value() {
            return value.clone();
        }

        /**
         * Creates an OctetString from a UTF-8 string.
         *
         * @param text the string
         * @return the OctetString
         */
        public static OctetString of(String text) {
            return new OctetString(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        /**
         * Interprets this OctetString as a UTF-8 string.
         *
         * @return the string value
         */
        public String asString() {
            return new String(value, java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof OctetString other && Arrays.equals(value, other.value);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(value);
        }

        @Override
        public String toString() {
            return "OctetString[length=" + value.length + "]";
        }
    }

    /**
     * ASN.1 OBJECT IDENTIFIER value.
     *
     * @param value the OID
     * @since 0.1.0
     */
    record Oid(ObjectIdentifier value) implements SnmpValue {
        /**
         * Creates an OID value with validation.
         *
         * @param value the OID (must not be null)
         */
        public Oid {
            if (value == null) {
                throw new IllegalArgumentException("OID must not be null");
            }
        }

        /**
         * Creates an OID value from dotted notation.
         *
         * @param dotted the dotted OID string
         * @return the OID value
         */
        public static Oid of(String dotted) {
            return new Oid(ObjectIdentifier.parse(dotted));
        }
    }

    /**
     * IPv4 address (SMIv2 IpAddress), 4 bytes.
     *
     * @param address the 4-byte address
     * @since 0.1.0
     */
    record IpAddress(byte[] address) implements SnmpValue {
        /**
         * Creates an IpAddress with validation and defensive copy.
         *
         * @param address the 4-byte address
         */
        public IpAddress {
            if (address == null || address.length != 4) {
                throw new IllegalArgumentException("IpAddress must be exactly 4 bytes");
            }
            address = address.clone();
        }

        /**
         * Returns a copy of the address bytes.
         *
         * @return copy of the address
         */
        @Override
        public byte[] address() {
            return address.clone();
        }

        /**
         * Creates an IpAddress from dotted-quad notation.
         *
         * @param dottedQuad the address string (e.g. "192.168.1.1")
         * @return the IpAddress
         */
        public static IpAddress of(String dottedQuad) {
            String[] parts = dottedQuad.split("\\.");
            if (parts.length != 4) {
                throw new IllegalArgumentException("Invalid IP address: " + dottedQuad);
            }
            byte[] addr = new byte[4];
            for (int i = 0; i < 4; i++) {
                int octet = Integer.parseInt(parts[i]);
                if (octet < 0 || octet > 255) {
                    throw new IllegalArgumentException("Invalid octet: " + parts[i]);
                }
                addr[i] = (byte) octet;
            }
            return new IpAddress(addr);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof IpAddress other && Arrays.equals(address, other.address);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(address);
        }

        @Override
        public String toString() {
            return "IpAddress[%d.%d.%d.%d]".formatted(
                    address[0] & 0xFF, address[1] & 0xFF,
                    address[2] & 0xFF, address[3] & 0xFF);
        }
    }

    /**
     * Opaque data (SMIv2 Opaque), carries arbitrary ASN.1 encoded data.
     *
     * @param value the opaque byte data
     * @since 0.1.0
     */
    record Opaque(byte[] value) implements SnmpValue {
        /**
         * Creates an Opaque with defensive copy.
         *
         * @param value the byte data (must not be null)
         */
        public Opaque {
            if (value == null) {
                throw new IllegalArgumentException("Value must not be null");
            }
            value = value.clone();
        }

        /**
         * Returns a copy of the byte data.
         *
         * @return copy of the value
         */
        @Override
        public byte[] value() {
            return value.clone();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Opaque other && Arrays.equals(value, other.value);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(value);
        }
    }

    /**
     * ASN.1 NULL value, used for GetRequest variable bindings.
     *
     * @since 0.1.0
     */
    record Null() implements SnmpValue {
        /** Singleton NULL instance. */
        public static final Null INSTANCE = new Null();
    }

    /**
     * noSuchObject exception value (context-specific tag 0, primitive).
     *
     * @since 0.1.0
     */
    record NoSuchObject() implements SnmpValue {
        /** Singleton instance. */
        public static final NoSuchObject INSTANCE = new NoSuchObject();
    }

    /**
     * noSuchInstance exception value (context-specific tag 1, primitive).
     *
     * @since 0.1.0
     */
    record NoSuchInstance() implements SnmpValue {
        /** Singleton instance. */
        public static final NoSuchInstance INSTANCE = new NoSuchInstance();
    }

    /**
     * endOfMibView exception value (context-specific tag 2, primitive).
     *
     * @since 0.1.0
     */
    record EndOfMibView() implements SnmpValue {
        /** Singleton instance. */
        public static final EndOfMibView INSTANCE = new EndOfMibView();
    }
}
