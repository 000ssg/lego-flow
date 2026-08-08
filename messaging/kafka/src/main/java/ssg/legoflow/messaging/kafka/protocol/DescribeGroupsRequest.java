package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * DescribeGroups request (API key 15).
 *
 * @param groups the group IDs to describe
 * @since 0.1.0
 */
public record DescribeGroupsRequest(List<String> groups) {
}
