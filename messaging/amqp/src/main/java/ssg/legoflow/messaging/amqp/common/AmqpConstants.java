package ssg.legoflow.messaging.amqp.common;

/**
 * Constants defined by the AMQP 1.0 specification (ISO 19464).
 *
 * @since 0.1.0
 */
public final class AmqpConstants {

    private AmqpConstants() {}

    /** AMQP protocol header: "AMQP" + protocol-id + major + minor + revision. */
    public static final byte[] AMQP_HEADER = {'A', 'M', 'Q', 'P', 0, 1, 0, 0};

    /** SASL protocol header: "AMQP" + protocol-id(3) + major(1) + minor(0) + revision(0). */
    public static final byte[] SASL_HEADER = {'A', 'M', 'Q', 'P', 3, 1, 0, 0};

    /** Default AMQP port. */
    public static final int DEFAULT_PORT = 5672;

    /** Default AMQPS (TLS) port. */
    public static final int DEFAULT_TLS_PORT = 5671;

    /** Minimum frame size (512 bytes per spec). */
    public static final int MIN_MAX_FRAME_SIZE = 512;

    /** Default max frame size. */
    public static final int DEFAULT_MAX_FRAME_SIZE = 65536;

    /** Frame header size: 4 (size) + 1 (DOFF) + 1 (type) + 2 (channel). */
    public static final int FRAME_HEADER_SIZE = 8;

    /** AMQP frame type. */
    public static final byte FRAME_TYPE_AMQP = 0x00;

    /** SASL frame type. */
    public static final byte FRAME_TYPE_SASL = 0x01;

    /** Default channel limit. */
    public static final int DEFAULT_CHANNEL_MAX = 65535;

    /** Default idle timeout in milliseconds (0 = disabled). */
    public static final long DEFAULT_IDLE_TIMEOUT = 0;

    /** Default incoming window size. */
    public static final long DEFAULT_INCOMING_WINDOW = 2048;

    /** Default outgoing window size. */
    public static final long DEFAULT_OUTGOING_WINDOW = 2048;

    /** Default link credit. */
    public static final int DEFAULT_LINK_CREDIT = 100;

    /** AMQP 1.0 major version. */
    public static final int MAJOR_VERSION = 1;

    /** AMQP 1.0 minor version. */
    public static final int MINOR_VERSION = 0;

    /** AMQP 1.0 revision. */
    public static final int REVISION = 0;
}
