package ssg.legoflow.service.passthrough;

import java.nio.ByteBuffer;

/**
 * Interceptor for observing and transforming data flowing through a pass-through connection.
 * <p>
 * Interceptors are applied in registration order. If an interceptor returns {@code null},
 * the previous buffer is used instead.
 *
 * @since 0.1.0
 */
public interface DataInterceptor {

    /**
     * Intercepts data flowing from the local (client) socket to the remote (target) socket.
     *
     * @param connection the established connection carrying the data
     * @param data       the data buffer to inspect or transform
     * @return the (possibly transformed) data buffer, or {@code null} to use the previous buffer
     */
    default ByteBuffer onLocalToRemote(EstablishedConnection connection, ByteBuffer data) {
        return data;
    }

    /**
     * Intercepts data flowing from the remote (target) socket to the local (client) socket.
     *
     * @param connection the established connection carrying the data
     * @param data       the data buffer to inspect or transform
     * @return the (possibly transformed) data buffer, or {@code null} to use the previous buffer
     */
    default ByteBuffer onRemoteToLocal(EstablishedConnection connection, ByteBuffer data) {
        return data;
    }
}
