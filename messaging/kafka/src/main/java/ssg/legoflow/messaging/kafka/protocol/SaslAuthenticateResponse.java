package ssg.legoflow.messaging.kafka.protocol;

/**
 * SaslAuthenticate response (API key 36).
 *
 * @param errorCode         the error code
 * @param authBytes         the authentication response bytes from the server
 * @param sessionLifetimeMs the session lifetime in milliseconds (0 for unlimited)
 * @since 1.0.0
 */
public record SaslAuthenticateResponse(short errorCode, byte[] authBytes, long sessionLifetimeMs) {
}
