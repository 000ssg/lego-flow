package ssg.legoflow.messaging.amqp.types;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sealed interface representing all AMQP 1.0 type system values.
 *
 * <p>The AMQP type system is a self-describing binary format where each value
 * is preceded by a constructor byte (type code). This sealed hierarchy models
 * every primitive, composite, and described type defined in the specification.
 *
 * @since 1.0.0
 */
public sealed interface AmqpType
        permits AmqpType.Null, AmqpType.Bool, AmqpType.UByte, AmqpType.UShort,
                AmqpType.UInt, AmqpType.ULong, AmqpType.Byte, AmqpType.Short,
                AmqpType.Int, AmqpType.Long, AmqpType.Float, AmqpType.Double,
                AmqpType.Char, AmqpType.Timestamp, AmqpType.Uuid, AmqpType.Binary,
                AmqpType.AmqpString, AmqpType.Symbol, AmqpType.AmqpList,
                AmqpType.AmqpMap, AmqpType.AmqpArray, AmqpType.Described {

    /** AMQP null value. */
    record Null() implements AmqpType {}

    /** AMQP boolean value. */
    record Bool(boolean value) implements AmqpType {}

    /** AMQP unsigned byte (0..255). */
    record UByte(short value) implements AmqpType {
        public UByte {
            if (value < 0 || value > 255) throw new IllegalArgumentException("ubyte range: 0..255");
        }
    }

    /** AMQP unsigned short (0..65535). */
    record UShort(int value) implements AmqpType {
        public UShort {
            if (value < 0 || value > 65535) throw new IllegalArgumentException("ushort range: 0..65535");
        }
    }

    /** AMQP unsigned int (0..4294967295). */
    record UInt(long value) implements AmqpType {
        public UInt {
            if (value < 0 || value > 0xFFFFFFFFL) throw new IllegalArgumentException("uint range: 0..4294967295");
        }
    }

    /** AMQP unsigned long (0..2^64-1), stored as Java long (interpreted unsigned). */
    record ULong(long value) implements AmqpType {}

    /** AMQP signed byte (-128..127). */
    record Byte(byte value) implements AmqpType {}

    /** AMQP signed short (-32768..32767). */
    record Short(short value) implements AmqpType {}

    /** AMQP signed int. */
    record Int(int value) implements AmqpType {}

    /** AMQP signed long. */
    record Long(long value) implements AmqpType {}

    /** AMQP IEEE 754 single-precision float. */
    record Float(float value) implements AmqpType {}

    /** AMQP IEEE 754 double-precision float. */
    record Double(double value) implements AmqpType {}

    /** AMQP UTF-32 character. */
    record Char(int codePoint) implements AmqpType {}

    /** AMQP timestamp (milliseconds since Unix epoch). */
    record Timestamp(long millis) implements AmqpType {}

    /** AMQP UUID. */
    record Uuid(UUID value) implements AmqpType {}

    /** AMQP binary data. */
    record Binary(byte[] value) implements AmqpType {}

    /** AMQP UTF-8 string. */
    record AmqpString(String value) implements AmqpType {}

    /** AMQP symbol (ASCII subset). */
    record Symbol(String value) implements AmqpType {}

    /** AMQP list (ordered, heterogeneous). */
    record AmqpList(List<AmqpType> elements) implements AmqpType {}

    /** AMQP map (key-value pairs, keys and values are AmqpType). */
    record AmqpMap(Map<AmqpType, AmqpType> entries) implements AmqpType {}

    /** AMQP array (ordered, homogeneous — all elements share the same type code). */
    record AmqpArray(List<AmqpType> elements) implements AmqpType {}

    /**
     * AMQP described type. A descriptor followed by the described value.
     * Used for all performatives and message sections.
     *
     * @param descriptor the descriptor (typically ULong or Symbol)
     * @param described  the described value (typically an AmqpList for performatives)
     */
    record Described(AmqpType descriptor, AmqpType described) implements AmqpType {}
}
