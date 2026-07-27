package ssg.legoflow.upnp.gena;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents an active GENA event subscription.
 *
 * @param sid         the subscription ID assigned by the event source
 * @param callbackUrl the local callback URL for receiving NOTIFY messages
 * @param eventSubUrl the event subscription URL on the remote service
 * @param serviceId   the service identifier this subscription is for
 * @param timeout     the subscription timeout duration
 * @param expiresAt   the instant when this subscription expires
 * @since 1.0.0
 */
public record EventSubscription(
        String sid,
        URI callbackUrl,
        URI eventSubUrl,
        String serviceId,
        Duration timeout,
        Instant expiresAt
) {

    /**
     * Creates a new {@code EventSubscription} with validation.
     *
     * @throws NullPointerException if any required parameter is {@code null}
     */
    public EventSubscription {
        Objects.requireNonNull(sid, "sid must not be null");
        Objects.requireNonNull(callbackUrl, "callbackUrl must not be null");
        Objects.requireNonNull(eventSubUrl, "eventSubUrl must not be null");
        Objects.requireNonNull(serviceId, "serviceId must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    /**
     * Returns whether this subscription has expired.
     *
     * @return {@code true} if the subscription has expired
     * @since 1.0.0
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Returns whether this subscription is close to expiring and should be renewed.
     *
     * <p>Returns {@code true} if less than 20% of the timeout remains.
     *
     * @return {@code true} if renewal is recommended
     * @since 1.0.0
     */
    public boolean shouldRenew() {
        var remaining = Duration.between(Instant.now(), expiresAt);
        var threshold = timeout.dividedBy(5);
        return remaining.compareTo(threshold) < 0;
    }

    /**
     * Creates a renewed subscription with an updated expiry time.
     *
     * @param newTimeout the new timeout duration
     * @return a new subscription with updated timeout and expiry
     * @since 1.0.0
     */
    public EventSubscription renewed(Duration newTimeout) {
        return new EventSubscription(sid, callbackUrl, eventSubUrl, serviceId,
                newTimeout, Instant.now().plus(newTimeout));
    }
}
