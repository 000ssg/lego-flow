package ssg.legoflow.messaging.nats.subject;

import ssg.legoflow.messaging.nats.protocol.NatsProtocol;
import java.util.Objects;
/**
 * NATS subject model with token splitting.
 *
 * <p>Subjects use {@code .} as the level separator. Each segment between
 * separators is a token. Subjects must not be empty and must not contain
 * spaces.
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code foo.bar.baz} — 3 tokens
 *   <li>{@code foo.*} — wildcard matching one token
 *   <li>{@code foo.>} — wildcard matching one or more tokens
 * </ul>
 *
 * @param value  the raw subject string
 * @param tokens the split tokens
 * @since 0.1.0
 */
public record Subject(String value, String[] tokens) {

    /**
     * Creates a subject from a raw string.
     *
     * @param value  the subject string
     * @param tokens the split tokens
     */
    public Subject {
        Objects.requireNonNull(value, "subject must not be null");
        if (value.isEmpty()) throw new IllegalArgumentException("subject must not be empty");
        if (value.contains(" ")) throw new IllegalArgumentException("subject must not contain spaces");
    }

    /**
     * Parses a subject string into a Subject instance.
     *
     * @param subject the subject string
     * @return the parsed subject
     * @throws IllegalArgumentException if the subject is invalid
     */
    public static Subject of(String subject) {
        Objects.requireNonNull(subject, "subject must not be null");
        String[] tokens = subject.split("\\.");
        return new Subject(subject, tokens);
    }

    /**
     * Returns the number of tokens.
     *
     * @return token count
     */
    public int tokenCount() {
        return tokens.length;
    }

    /**
     * Returns the token at the given index.
     *
     * @param index the token index
     * @return the token
     * @throws ArrayIndexOutOfBoundsException if index is out of range
     */
    public String tokenAt(int index) {
        return tokens[index];
    }

    /**
     * Returns whether this subject contains any wildcards.
     *
     * @return true if wildcards are present
     */
    public boolean hasWildcards() {
        for (String token : tokens) {
            if (NatsProtocol.WILDCARD_TOKEN.equals(token)
                    || NatsProtocol.WILDCARD_FULL.equals(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether this subject is valid as a publish subject (no wildcards).
     *
     * @return true if no wildcards
     */
    public boolean isPublishable() {
        return !hasWildcards();
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Subject s)) return false;
        return value.equals(s.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
