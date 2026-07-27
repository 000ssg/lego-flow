package ssg.legoflow.messaging.kafka.common;

/**
 * Represents a Kafka broker node.
 *
 * @param id   the broker ID
 * @param host the broker hostname
 * @param port the broker port
 * @since 1.0.0
 */
public record Node(int id, String host, int port) {

    /**
     * Creates a broker node.
     *
     * @param id   the broker ID
     * @param host the broker hostname, must not be null
     * @param port the broker port
     */
    public Node {
        if (host == null) throw new IllegalArgumentException("host must not be null");
    }

    @Override
    public String toString() {
        return id + "@" + host + ":" + port;
    }
}
