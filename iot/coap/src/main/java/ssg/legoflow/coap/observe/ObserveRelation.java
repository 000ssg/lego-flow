package ssg.legoflow.coap.observe;

import java.net.SocketAddress;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents an observe relationship between a client and a server resource
 * as defined in RFC 7641.
 *
 * <p>Each relation is identified by the token and tracks the observer's endpoint,
 * the resource path, and a monotonically increasing sequence number for notifications.
 *
 * @since 1.0.0
 */
public final class ObserveRelation {

    private final byte[] token;
    private final String resourcePath;
    private final SocketAddress observer;
    private final AtomicInteger sequenceNumber = new AtomicInteger(0);
    private final AtomicBoolean active = new AtomicBoolean(true);

    /**
     * Creates a new observe relation.
     *
     * @param token        the token identifying this observation
     * @param resourcePath the observed resource path
     * @param observer     the observer's socket address
     * @throws NullPointerException if any argument is {@code null}
     * @since 1.0.0
     */
    public ObserveRelation(byte[] token, String resourcePath, SocketAddress observer) {
        Objects.requireNonNull(token, "token must not be null");
        Objects.requireNonNull(resourcePath, "resourcePath must not be null");
        Objects.requireNonNull(observer, "observer must not be null");
        this.token = token.clone();
        this.resourcePath = resourcePath;
        this.observer = observer;
    }

    /**
     * Returns a copy of the observation token.
     *
     * @return the token bytes
     * @since 1.0.0
     */
    public byte[] token() {
        return token.clone();
    }

    /**
     * Returns the observed resource path.
     *
     * @return the resource path
     * @since 1.0.0
     */
    public String resourcePath() {
        return resourcePath;
    }

    /**
     * Returns the observer's socket address.
     *
     * @return the observer endpoint
     * @since 1.0.0
     */
    public SocketAddress observer() {
        return observer;
    }

    /**
     * Returns the current observe sequence number.
     *
     * @return the sequence number
     * @since 1.0.0
     */
    public int sequenceNumber() {
        return sequenceNumber.get();
    }

    /**
     * Increments and returns the next sequence number.
     *
     * @return the new sequence number
     * @since 1.0.0
     */
    public int nextSequenceNumber() {
        return sequenceNumber.incrementAndGet();
    }

    /**
     * Returns whether this observe relation is active.
     *
     * @return {@code true} if the relation is active
     * @since 1.0.0
     */
    public boolean isActive() {
        return active.get();
    }

    /**
     * Cancels this observe relation, marking it as inactive.
     *
     * @since 1.0.0
     */
    public void cancel() {
        active.set(false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ObserveRelation that)) return false;
        return Arrays.equals(token, that.token) && resourcePath.equals(that.resourcePath)
                && observer.equals(that.observer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(token), resourcePath, observer);
    }

    @Override
    public String toString() {
        return "ObserveRelation{path='" + resourcePath + "', observer=" + observer
                + ", seq=" + sequenceNumber.get() + ", active=" + active.get() + "}";
    }
}
