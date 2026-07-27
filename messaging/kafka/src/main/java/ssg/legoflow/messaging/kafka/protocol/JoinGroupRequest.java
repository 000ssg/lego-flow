package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * JoinGroup request (API key 11).
 *
 * @param groupId          the consumer group ID
 * @param sessionTimeoutMs the session timeout
 * @param rebalanceTimeoutMs the rebalance timeout
 * @param memberId         the member ID (empty string on first join)
 * @param protocolType     the protocol type (e.g., "consumer")
 * @param protocols        the supported assignment protocols
 * @since 1.0.0
 */
public record JoinGroupRequest(String groupId, int sessionTimeoutMs, int rebalanceTimeoutMs,
                               String memberId, String protocolType,
                               List<Protocol> protocols) {

    /**
     * A group protocol with metadata.
     *
     * @param name     the protocol name (e.g., "range", "roundrobin")
     * @param metadata the protocol metadata bytes
     */
    public record Protocol(String name, byte[] metadata) {
    }
}
