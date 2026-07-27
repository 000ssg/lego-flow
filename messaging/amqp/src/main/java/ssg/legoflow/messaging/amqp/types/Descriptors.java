package ssg.legoflow.messaging.amqp.types;

/**
 * AMQP 1.0 descriptor codes for performatives, SASL frames, and message sections.
 *
 * <p>Each performative and section type has a unique numeric descriptor that
 * identifies it on the wire. The descriptor is encoded as a ulong following
 * the described-type constructor byte ({@code 0x00}).
 *
 * @since 1.0.0
 */
public final class Descriptors {

    private Descriptors() {}

    // -- Transport performatives (section 2.7) --

    /** open performative descriptor. */
    public static final long OPEN = 0x0000_0000_0000_0010L;

    /** begin performative descriptor. */
    public static final long BEGIN = 0x0000_0000_0000_0011L;

    /** attach performative descriptor. */
    public static final long ATTACH = 0x0000_0000_0000_0012L;

    /** flow performative descriptor. */
    public static final long FLOW = 0x0000_0000_0000_0013L;

    /** transfer performative descriptor. */
    public static final long TRANSFER = 0x0000_0000_0000_0014L;

    /** disposition performative descriptor. */
    public static final long DISPOSITION = 0x0000_0000_0000_0015L;

    /** detach performative descriptor. */
    public static final long DETACH = 0x0000_0000_0000_0016L;

    /** end performative descriptor. */
    public static final long END = 0x0000_0000_0000_0017L;

    /** close performative descriptor. */
    public static final long CLOSE = 0x0000_0000_0000_0018L;

    // -- SASL frames (section 5.3) --

    /** sasl-mechanisms descriptor. */
    public static final long SASL_MECHANISMS = 0x0000_0000_0000_0040L;

    /** sasl-init descriptor. */
    public static final long SASL_INIT = 0x0000_0000_0000_0041L;

    /** sasl-challenge descriptor. */
    public static final long SASL_CHALLENGE = 0x0000_0000_0000_0042L;

    /** sasl-response descriptor. */
    public static final long SASL_RESPONSE = 0x0000_0000_0000_0043L;

    /** sasl-outcome descriptor. */
    public static final long SASL_OUTCOME = 0x0000_0000_0000_0044L;

    // -- Message sections (section 3.2) --

    /** message header descriptor. */
    public static final long HEADER = 0x0000_0000_0000_0070L;

    /** delivery-annotations descriptor. */
    public static final long DELIVERY_ANNOTATIONS = 0x0000_0000_0000_0071L;

    /** message-annotations descriptor. */
    public static final long MESSAGE_ANNOTATIONS = 0x0000_0000_0000_0072L;

    /** properties descriptor. */
    public static final long PROPERTIES = 0x0000_0000_0000_0073L;

    /** application-properties descriptor. */
    public static final long APPLICATION_PROPERTIES = 0x0000_0000_0000_0074L;

    /** data section descriptor (binary body). */
    public static final long DATA = 0x0000_0000_0000_0075L;

    /** amqp-sequence section descriptor (list body). */
    public static final long AMQP_SEQUENCE = 0x0000_0000_0000_0076L;

    /** amqp-value section descriptor (any body). */
    public static final long AMQP_VALUE = 0x0000_0000_0000_0077L;

    /** footer descriptor. */
    public static final long FOOTER = 0x0000_0000_0000_0078L;

    // -- Delivery state / Outcome (section 3.4) --

    /** received delivery state descriptor. */
    public static final long RECEIVED = 0x0000_0000_0000_0023L;

    /** accepted outcome descriptor. */
    public static final long ACCEPTED = 0x0000_0000_0000_0024L;

    /** rejected outcome descriptor. */
    public static final long REJECTED = 0x0000_0000_0000_0025L;

    /** released outcome descriptor. */
    public static final long RELEASED = 0x0000_0000_0000_0026L;

    /** modified outcome descriptor. */
    public static final long MODIFIED = 0x0000_0000_0000_0027L;

    // -- Addressing (section 3.5) --

    /** source descriptor. */
    public static final long SOURCE = 0x0000_0000_0000_0028L;

    /** target descriptor. */
    public static final long TARGET = 0x0000_0000_0000_0029L;

    // -- Transactions (section 4.5) --

    /** coordinator descriptor. */
    public static final long COORDINATOR = 0x0000_0000_0000_0030L;

    /** declare descriptor. */
    public static final long DECLARE = 0x0000_0000_0000_0031L;

    /** discharge descriptor. */
    public static final long DISCHARGE = 0x0000_0000_0000_0032L;

    /** declared outcome descriptor. */
    public static final long DECLARED = 0x0000_0000_0000_0033L;

    /** transactional-state descriptor. */
    public static final long TRANSACTIONAL_STATE = 0x0000_0000_0000_0034L;

    // -- Error (section 2.8.1) --

    /** error descriptor. */
    public static final long ERROR = 0x0000_0000_0000_001DL;
}
