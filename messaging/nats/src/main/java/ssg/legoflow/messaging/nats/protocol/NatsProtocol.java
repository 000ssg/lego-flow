package ssg.legoflow.messaging.nats.protocol;

/**
 * NATS protocol constants and version information.
 *
 * <p>Defines the text-based protocol framing used by NATS clients and servers.
 * All messages are terminated with {@code \r\n} (CRLF). The protocol is
 * human-readable and uses simple text commands for control operations,
 * with binary payloads following size-prefixed headers.
 *
 * @since 0.1.0
 */
public final class NatsProtocol {

    /** Protocol version supported by this implementation. */
    public static final int PROTOCOL_VERSION = 1;

    /** Client language identifier. */
    public static final String LANG = "java";

    /** Client library version. */
    public static final String VERSION = "1.0.0";

    /** CRLF line terminator. */
    public static final String CRLF = "\r\n";

    /** CRLF as byte array. */
    public static final byte[] CRLF_BYTES = {'\r', '\n'};

    /** Space separator. */
    public static final byte SP = ' ';

    // Client-to-server operations
    /** CONNECT operation — client sends connection options. */
    public static final String OP_CONNECT = "CONNECT";
    /** PUB operation — publish a message. */
    public static final String OP_PUB = "PUB";
    /** HPUB operation — publish a message with headers. */
    public static final String OP_HPUB = "HPUB";
    /** SUB operation — subscribe to a subject. */
    public static final String OP_SUB = "SUB";
    /** UNSUB operation — unsubscribe. */
    public static final String OP_UNSUB = "UNSUB";

    // Server-to-client operations
    /** INFO operation — server info payload. */
    public static final String OP_INFO = "INFO";
    /** MSG operation — message delivery. */
    public static final String OP_MSG = "MSG";
    /** HMSG operation — message delivery with headers. */
    public static final String OP_HMSG = "HMSG";
    /** +OK operation — acknowledgement in verbose mode. */
    public static final String OP_OK = "+OK";
    /** -ERR operation — error message. */
    public static final String OP_ERR = "-ERR";

    // Bidirectional
    /** PING operation — keep-alive request. */
    public static final String OP_PING = "PING";
    /** PONG operation — keep-alive response. */
    public static final String OP_PONG = "PONG";

    /** Header version prefix for NATS headers. */
    public static final String HDR_VERSION = "NATS/1.0";

    /** Default maximum payload size (1 MB). */
    public static final int DEFAULT_MAX_PAYLOAD = 1_048_576;

    /** Default server port. */
    public static final int DEFAULT_PORT = 4222;

    /** Inbox prefix for request/reply pattern. */
    public static final String INBOX_PREFIX = "_INBOX.";

    /** JetStream API prefix. */
    public static final String JS_API_PREFIX = "$JS.API.";

    /** Subject level separator. */
    public static final char SUBJECT_SEPARATOR = '.';

    /** Single-token wildcard. */
    public static final String WILDCARD_TOKEN = "*";

    /** Multi-level wildcard (must be last token). */
    public static final String WILDCARD_FULL = ">";

    private NatsProtocol() {
        // constants only
    }
}
