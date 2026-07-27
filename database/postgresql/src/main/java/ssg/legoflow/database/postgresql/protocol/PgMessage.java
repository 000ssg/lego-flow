package ssg.legoflow.database.postgresql.protocol;

/**
 * Sealed interface for all PostgreSQL wire protocol messages.
 *
 * <p>Messages are divided into {@link FrontendMessage} (client to server)
 * and {@link BackendMessage} (server to client).
 *
 * @since 1.0.0
 */
public sealed interface PgMessage permits FrontendMessage, BackendMessage {

    /**
     * Returns the single-byte type identifier for this message,
     * or 0 for untyped messages (e.g., StartupMessage, SSLRequest).
     *
     * @return the type byte, or 0 if untyped
     */
    byte type();
}
