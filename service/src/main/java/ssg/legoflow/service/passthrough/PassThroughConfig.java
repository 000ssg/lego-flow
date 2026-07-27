package ssg.legoflow.service.passthrough;

import java.net.InetSocketAddress;

/**
 * Configuration for a single TCP port redirection route.
 *
 * @param localPort     the local port to listen on for incoming connections
 * @param remoteAddress the remote host and port to forward connections to
 * @since 1.0.0
 */
public record PassThroughConfig(int localPort, InetSocketAddress remoteAddress) {

    /**
     * Creates a new pass-through configuration.
     *
     * @param localPort     the local port to listen on, must be between 0 and 65535
     * @param remoteAddress the remote address to forward to, must not be null
     * @throws IllegalArgumentException if localPort is out of range or remoteAddress is null
     */
    public PassThroughConfig {
        if (localPort < 0 || localPort > 65535) {
            throw new IllegalArgumentException("localPort must be between 0 and 65535: " + localPort);
        }
        if (remoteAddress == null) {
            throw new IllegalArgumentException("remoteAddress must not be null");
        }
    }
}
