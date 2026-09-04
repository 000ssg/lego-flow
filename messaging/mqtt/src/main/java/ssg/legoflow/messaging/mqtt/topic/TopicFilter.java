package ssg.legoflow.messaging.mqtt.topic;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * MQTT topic filter with wildcard support.
 *
 * <p>Supports single-level wildcard ({@code +}) matching exactly one topic level,
 * and multi-level wildcard ({@code #}) matching all remaining levels (must be last).
 * Also supports MQTT 5.0 shared subscriptions ({@code $share/group/filter}).
 *
 * @since 0.1.0
 */
public final class TopicFilter {

    private final String filter;
    private final List<String> levels;

    /**
     * Creates a new topic filter from the given string.
     *
     * @param filter the topic filter string
     * @throws IllegalArgumentException if the filter is null or empty
     */
    public TopicFilter(String filter) {
        Objects.requireNonNull(filter, "Topic filter must not be null");
        if (filter.isEmpty()) {
            throw new IllegalArgumentException("Topic filter must not be empty");
        }
        this.filter = filter;
        // For shared subscriptions, extract the actual filter part
        String effectiveFilter = getEffectiveFilter();
        this.levels = Arrays.asList(effectiveFilter.split("/", -1));
    }

    /**
     * Tests whether this filter matches the given topic name.
     *
     * @param topicName the concrete topic name (no wildcards)
     * @return {@code true} if the topic name matches this filter
     */
    public boolean matches(String topicName) {
        if (topicName == null || topicName.isEmpty()) {
            return false;
        }
        // Topics starting with $ do not match wildcards at the first level
        if (topicName.startsWith("$") && !filter.startsWith("$")) {
            if (levels.get(0).equals("+") || levels.get(0).equals("#")) {
                return false;
            }
        }

        List<String> topicLevels = Arrays.asList(topicName.split("/", -1));
        return matchLevels(levels, topicLevels, 0, 0);
    }

    /**
     * Validates whether this topic filter conforms to the MQTT specification.
     *
     * @return {@code true} if the filter is valid
     */
    public boolean isValid() {
        String effectiveFilter = getEffectiveFilter();
        if (effectiveFilter.isEmpty()) {
            return false;
        }
        List<String> filterLevels = Arrays.asList(effectiveFilter.split("/", -1));
        for (int i = 0; i < filterLevels.size(); i++) {
            String level = filterLevels.get(i);
            if (level.equals("#")) {
                // # must be the last level
                return i == filterLevels.size() - 1;
            }
            if (level.equals("+")) {
                continue;
            }
            // Level must not contain + or # mixed with other characters
            if (level.contains("+") || level.contains("#")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the topic levels of this filter.
     *
     * @return the list of topic levels
     */
    public List<String> getLevels() {
        return levels;
    }

    /**
     * Returns whether this is a shared subscription (MQTT 5.0).
     *
     * <p>Shared subscriptions have the format {@code $share/group/filter}.
     *
     * @return {@code true} if this is a shared subscription
     */
    public boolean isSharedSubscription() {
        return filter.startsWith("$share/") && filter.indexOf('/', 7) > 7;
    }

    /**
     * Returns the share group name for shared subscriptions.
     *
     * @return the group name, or {@code null} if not a shared subscription
     */
    public String getShareGroup() {
        if (!isSharedSubscription()) {
            return null;
        }
        int secondSlash = filter.indexOf('/', 7);
        return filter.substring(7, secondSlash);
    }

    /**
     * Returns the raw filter string.
     *
     * @return the original filter string
     */
    public String getFilter() {
        return filter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TopicFilter that)) return false;
        return filter.equals(that.filter);
    }

    @Override
    public int hashCode() {
        return filter.hashCode();
    }

    @Override
    public String toString() {
        return filter;
    }

    // --- Private helpers ---

    private String getEffectiveFilter() {
        if (isSharedSubscription()) {
            int secondSlash = filter.indexOf('/', 7);
            return filter.substring(secondSlash + 1);
        }
        return filter;
    }

    private boolean matchLevels(List<String> filterLevels, List<String> topicLevels,
                                int fi, int ti) {
        while (fi < filterLevels.size() && ti < topicLevels.size()) {
            String fl = filterLevels.get(fi);
            if (fl.equals("#")) {
                return true; // # matches everything remaining
            }
            if (!fl.equals("+") && !fl.equals(topicLevels.get(ti))) {
                return false;
            }
            fi++;
            ti++;
        }
        // Check if we matched everything
        if (fi == filterLevels.size() && ti == topicLevels.size()) {
            return true;
        }
        // Special case: filter ends with /# and topic levels are exhausted
        if (fi == filterLevels.size() - 1 && filterLevels.get(fi).equals("#")) {
            return true;
        }
        return false;
    }
}
