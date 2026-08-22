package ssg.legoflow.messaging.amqp091.common;

/**
 * Constants for AMQP 0-9-1 protocol (RabbitMQ compatible).
 *
 * <p>AMQP 0-9-1 uses a different wire format than AMQP 1.0:
 * <pre>
 *  Frame: [TYPE(1) | SIZE(4) | TYPE2(2) | PAYLOAD(N) | END(1)]
 *  TYPE: 0x08=method, 0x09=header, 0x0A=body
 *  END:  0xCE (206)
 * </pre>
 *
 * @since 0.2.0
 */
public final class Amqp091Constants {

    private Amqp091Constants() {}

    // Frame types
    public static final byte FRAME_METHOD = (byte)0x08;
    public static final byte FRAME_HEADER = (byte)0x09;
    public static final byte FRAME_BODY = (byte)0x0A;

    // Frame end octet
    public static final byte FRAME_END = (byte)0xCE;

    // Default values
    public static final int DEFAULT_PORT = 5672;
    public static final int DEFAULT_MAX_FRAME_SIZE = 131072; // 128KB
    public static final int DEFAULT_HEARTBEAT = 60;
    public static final int DEFAULT_CHANNEL_MAX = 2047;

    // Frame header size (type + size + type2)
    public static final int FRAME_HEADER_SIZE = 7;

    // Minimum frame size (header + end)
    public static final int MIN_FRAME_SIZE = FRAME_HEADER_SIZE + 1;

    // Connection start constants
    public static final int CONNECTION_START = 10;
    public static final int CONNECTION_START_OK = 11;
    public static final int CONNECTION_TUNE = 20;
    public static final int CONNECTION_TUNE_OK = 21;
    public static final int CONNECTION_OPEN = 30;
    public static final int CONNECTION_OPEN_OK = 31;
    public static final int CONNECTION_CLOSE = 40;
    public static final int CONNECTION_CLOSE_OK = 41;
    public static final int CONNECTION_INDIRECT = 50;

    // Channel methods
    public static final int CHANNEL_OPEN = 10;
    public static final int CHANNEL_OPEN_OK = 11;
    public static final int CHANNEL_CLOSE = 40;
    public static final int CHANNEL_CLOSE_OK = 41;

    // Exchange methods
    public static final int EXCHANGE_DECLARE = 10;
    public static final int EXCHANGE_DECLARE_OK = 11;
    public static final int EXCHANGE_DELETE = 20;
    public static final int EXCHANGE_DELETE_OK = 21;

    // Queue methods
    public static final int QUEUE_DECLARE = 10;
    public static final int QUEUE_DECLARE_OK = 11;
    public static final int QUEUE_BIND = 20;
    public static final int QUEUE_BIND_OK = 21;
    public static final int QUEUE_UNBIND = 30;
    public static final int QUEUE_UNBIND_OK = 31;
    public static final int QUEUE_PURGE = 40;
    public static final int QUEUE_PURGE_OK = 41;
    public static final int QUEUE_DELETE = 50;
    public static final int QUEUE_DELETE_OK = 51;

    // Basic methods
    public static final int BASIC_QOS = 10;
    public static final int BASIC_QOS_OK = 11;
    public static final int BASIC_CONSUME = 20;
    public static final int BASIC_CONSUME_OK = 21;
    public static final int BASIC_CANCEL = 30;
    public static final int BASIC_CANCEL_OK = 31;
    public static final int BASIC_PUBLISH = 40;
    public static final int BASIC_DELIVER = 60;
    public static final int BASIC_GET = 70;
    public static final int BASIC_GET_OK = 71;
    public static final int BASIC_GET_EMPTY = 72;
    public static final int BASIC_ACK = 80;
    public static final int BASIC_REJECT = 140;
    public static final int BASIC_RECOVER_ASYNC = 150;
    public static final int BASIC_RECOVER = 150;
    public static final int BASIC_RECOVER_OK = 151;
    public static final int BASIC_NACK = 120;
}
