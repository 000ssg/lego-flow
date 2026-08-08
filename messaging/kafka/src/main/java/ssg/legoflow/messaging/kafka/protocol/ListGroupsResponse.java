package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * ListGroups response (API key 16).
 *
 * @param errorCode the top-level error code
 * @param groups    the group listings
 * @since 0.1.0
 */
public record ListGroupsResponse(short errorCode, List<GroupListing> groups) {

    /**
     * A consumer group listing.
     *
     * @param groupId      the group ID
     * @param protocolType the protocol type (e.g. "consumer")
     * @param state        the group state
     */
    public record GroupListing(String groupId, String protocolType, String state) {
    }
}
