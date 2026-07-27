package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * DescribeGroups response (API key 15).
 *
 * @param groups the described groups
 * @since 1.0.0
 */
public record DescribeGroupsResponse(List<GroupDescription> groups) {

    /**
     * A group description.
     *
     * @param errorCode    the error code
     * @param groupId      the group ID
     * @param state        the group state
     * @param protocolType the protocol type
     * @param protocol     the selected protocol
     * @param members      the group members
     */
    public record GroupDescription(short errorCode, String groupId, String state,
                                   String protocolType, String protocol,
                                   List<MemberDescription> members) {
    }

    /**
     * A group member description.
     *
     * @param memberId         the member ID
     * @param clientId         the client ID
     * @param clientHost       the client host
     * @param memberMetadata   the member metadata
     * @param memberAssignment the member assignment
     */
    public record MemberDescription(String memberId, String clientId, String clientHost,
                                    byte[] memberMetadata, byte[] memberAssignment) {
    }
}
