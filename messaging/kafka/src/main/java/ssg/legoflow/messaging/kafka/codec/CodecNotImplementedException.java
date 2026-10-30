package ssg.legoflow.messaging.kafka.codec;

/**
 * Thrown when a request or response is encoded/decoded at an API version that has no
 * implemented code path.
 *
 * <p>Phase 6a contract: the per-version codec classes implement API versions one sub-task
 * at a time (v0 first, then v1, … — see
 * {@code doc/plans/messaging/PHASE6A_KAFKA_CODEC_VERSIONS.md} §3). An unimplemented version
 * must fail loudly, never fall through silently to a different version's layout: the two
 * layouts may share a field prefix but diverge later in the body, so a silent fallback
 * would produce corrupt frames that decode "successfully" with wrong values.
 *
 * @since 0.1.0
 */
public class CodecNotImplementedException extends RuntimeException {

    /**
     * Creates a new codec-not-implemented exception.
     *
     * @param message the error message identifying the API and version
     */
    public CodecNotImplementedException(String message) {
        super(message);
    }

    /**
     * Creates a new codec-not-implemented exception with a cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public CodecNotImplementedException(String message, Throwable cause) {
        super(message, cause);
    }
}
