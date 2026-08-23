package ssg.legoflow.messaging.amqp091.common;

/**
 * Constants for AMQP 0-9-1 protocol (RabbitMQ compatible).
 *
 * <p>AMQP 0-9-1 uses a different wire format than AMQP 1.0:
 * <pre>
 *  Frame: [TYPE(1) | CHANNEL(2) | SIZE(4) | PAYLOAD(N) | END(1)]
 *  TYPE: 1=method, 2=header, 3=body, 8=heartbeat
 *  END:  0xCE (206)
 *
 *  Method payload (RabbitMQ wire format):
 *  [CLASS_ID(2) | METHOD_ID(2) | ARGS(N)]
 *  CLASS_ID and METHOD_ID are separate 2-byte fields.
 * </pre>
 *
 * @since 0.2.0
 */
public final class Amqp091Constants {

    private Amqp091Constants() {}

    // Frame types
    public static final byte FRAME_METHOD = 1;
    public static final byte FRAME_HEADER = 2;
    public static final byte FRAME_BODY = 3;
    public static final byte FRAME_HEARTBEAT = 8;

    // Frame end octet
    public static final byte FRAME_END = (byte)0xCE;

    // Default values
    public static final int DEFAULT_PORT = 5672;
    public static final int DEFAULT_MAX_FRAME_SIZE = 131072;
    public static final int DEFAULT_HEARTBEAT = 60;
    public static final int DEFAULT_CHANNEL_MAX = 2047;

    public static final int FRAME_HEADER_SIZE = 7;
    public static final int MIN_FRAME_SIZE = FRAME_HEADER_SIZE + 1;

    // Wire format method IDs encoded as: class_id << 16 | method_id
    // Connection class = 10

    // Connection methods
    public static final int CONNECTION_START            = (10 << 16) | 10;
    public static final int CONNECTION_START_OK         = (10 << 16) | 11;
    public static final int CONNECTION_SECURE           = (10 << 16) | 20;
    public static final int CONNECTION_SECURE_OK        = (10 << 16) | 21;
    public static final int CONNECTION_TUNE             = (10 << 16) | 30;
    public static final int CONNECTION_TUNE_OK          = (10 << 16) | 31;
    public static final int CONNECTION_OPEN             = (10 << 16) | 40;
    public static final int CONNECTION_OPEN_OK          = (10 << 16) | 41;
    public static final int CONNECTION_CLOSE            = (10 << 16) | 50;
    public static final int CONNECTION_CLOSE_OK         = (10 << 16) | 51;
    public static final int CONNECTION_BLOCKED          = (10 << 16) | 60;
    public static final int CONNECTION_UNBLOCKED        = (10 << 16) | 61;

    // Channel class = 20
    public static final int CHANNEL_OPEN            = (20 << 16) | 10;
    public static final int CHANNEL_OPEN_OK         = (20 << 16) | 11;
    public static final int CHANNEL_CLOSE           = (20 << 16) | 40;
    public static final int CHANNEL_CLOSE_OK        = (20 << 16) | 41;

    // Exchange class = 40
    public static final int EXCHANGE_DECLARE        = (40 << 16) | 10;
    public static final int EXCHANGE_DECLARE_OK     = (40 << 16) | 11;
    public static final int EXCHANGE_DELETE         = (40 << 16) | 20;
    public static final int EXCHANGE_DELETE_OK      = (40 << 16) | 21;

    // Queue class = 50
    public static final int QUEUE_DECLARE           = (50 << 16) | 10;
    public static final int QUEUE_DECLARE_OK        = (50 << 16) | 11;
    public static final int QUEUE_BIND              = (50 << 16) | 20;
    public static final int QUEUE_BIND_OK           = (50 << 16) | 21;
    public static final int QUEUE_UNBIND            = (50 << 16) | 30;
    public static final int QUEUE_UNBIND_OK         = (50 << 16) | 31;
    public static final int QUEUE_PURGE             = (50 << 16) | 40;
    public static final int QUEUE_PURGE_OK          = (50 << 16) | 41;
    public static final int QUEUE_DELETE            = (50 << 16) | 50;
    public static final int QUEUE_DELETE_OK         = (50 << 16) | 51;

    // Basic class = 60
    public static final int BASIC_QOS             = (60 << 16) | 10;
    public static final int BASIC_QOS_OK          = (60 << 16) | 11;
    public static final int BASIC_CONSUME         = (60 << 16) | 20;
    public static final int BASIC_CONSUME_OK      = (60 << 16) | 21;
    public static final int BASIC_CANCEL          = (60 << 16) | 30;
    public static final int BASIC_CANCEL_OK       = (60 << 16) | 31;
    public static final int BASIC_PUBLISH         = (60 << 16) | 40;
    public static final int BASIC_DELIVER         = (60 << 16) | 60;
    public static final int BASIC_GET             = (60 << 16) | 70;
    public static final int BASIC_GET_OK          = (60 << 16) | 71;
    public static final int BASIC_GET_EMPTY       = (60 << 16) | 72;
    public static final int BASIC_ACK             = (60 << 16) | 80;
    public static final int BASIC_NACK            = (60 << 16) | 120;
    public static final int BASIC_REJECT          = (60 << 16) | 140;
    public static final int BASIC_RECOVER         = (60 << 16) | 150;
    public static final int BASIC_RECOVER_OK      = (60 << 16) | 151;
    public static final int BASIC_RECOVER_ASYNC   = (60 << 16) | 150;
}
