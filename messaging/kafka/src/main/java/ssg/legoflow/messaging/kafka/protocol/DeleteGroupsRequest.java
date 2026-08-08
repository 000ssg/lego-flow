package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * DeleteGroups request (API key 42).
 *
 * @param groups the group IDs to delete
 * @since 0.1.0
 */
public record DeleteGroupsRequest(List<String> groups) {
}
