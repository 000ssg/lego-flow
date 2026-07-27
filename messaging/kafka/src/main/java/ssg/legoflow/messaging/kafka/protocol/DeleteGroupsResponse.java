package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * DeleteGroups response (API key 42).
 *
 * @param results the per-group deletion results
 * @since 1.0.0
 */
public record DeleteGroupsResponse(List<GroupResult> results) {

    /**
     * Per-group deletion result.
     *
     * @param groupId   the group ID
     * @param errorCode the error code
     */
    public record GroupResult(String groupId, short errorCode) {
    }
}
