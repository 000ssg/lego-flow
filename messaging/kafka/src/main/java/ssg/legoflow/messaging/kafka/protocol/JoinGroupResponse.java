package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * JoinGroup response (API key 11).
 *
 * @param errorCode    the error code
 * @param generationId the group generation ID
 * @param protocolName the selected protocol name
 * @param leader       the leader member ID
 * @param memberId     the assigned member ID
 * @param members      the group members (only sent to leader)
 * @since 1.0.0
 */
public record JoinGroupResponse(short errorCode, int generationId, String protocolName,
                                String leader, String memberId,
                                List<Member> members) {

    /**
     * A group member.
     *
     * @param memberId the member ID
     * @param metadata the member's protocol metadata
     */
    public record Member(String memberId, byte[] metadata) {
    }
}
