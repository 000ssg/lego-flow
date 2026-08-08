package ssg.legoflow.messaging.nats.subject;

import ssg.legoflow.messaging.nats.protocol.NatsProtocol;

/**
 * Matches NATS subjects against subscription patterns with wildcard support.
 *
 * <p>Wildcard rules:
 * <ul>
 *   <li>{@code *} matches exactly one token: {@code foo.*.baz} matches {@code foo.bar.baz}
 *   <li>{@code >} matches one or more tokens and must be the last token:
 *       {@code foo.>} matches {@code foo.bar} and {@code foo.bar.baz}
 *   <li>Exact tokens must match exactly
 * </ul>
 *
 * @since 0.1.0
 */
public final class SubjectMatcher {

    private SubjectMatcher() {
        // utility class
    }

    /**
     * Tests whether a subject matches a subscription pattern.
     *
     * @param pattern the subscription pattern (may contain wildcards)
     * @param subject the published subject (no wildcards)
     * @return true if the subject matches the pattern
     */
    public static boolean matches(String pattern, String subject) {
        if (pattern.equals(subject)) return true;

        String[] patternTokens = pattern.split("\\.");
        String[] subjectTokens = subject.split("\\.");

        return matchTokens(patternTokens, subjectTokens);
    }

    /**
     * Tests whether a subject matches a parsed subscription pattern.
     *
     * @param pattern the parsed pattern subject
     * @param subject the parsed published subject
     * @return true if the subject matches the pattern
     */
    public static boolean matches(Subject pattern, Subject subject) {
        if (pattern.value().equals(subject.value())) return true;
        return matchTokens(pattern.tokens(), subject.tokens());
    }

    private static boolean matchTokens(String[] patternTokens, String[] subjectTokens) {
        for (int i = 0; i < patternTokens.length; i++) {
            String pt = patternTokens[i];

            // Full wildcard — matches rest of subject (one or more tokens)
            if (NatsProtocol.WILDCARD_FULL.equals(pt)) {
                return i < subjectTokens.length; // must match at least one token
            }

            // Past the end of subject tokens — no match
            if (i >= subjectTokens.length) return false;

            // Single token wildcard — matches any single token
            if (NatsProtocol.WILDCARD_TOKEN.equals(pt)) {
                continue;
            }

            // Exact token match
            if (!pt.equals(subjectTokens[i])) return false;
        }

        // Pattern consumed; subject must also be fully consumed
        return patternTokens.length == subjectTokens.length;
    }
}
