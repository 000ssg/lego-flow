package ssg.legoflow.email.smtp.server;

import java.util.*;

/**
 * Configuration for SMTP relay restrictions.
 *
 * <p>Controls which senders and recipients are allowed, whether authentication
 * is required, and maximum message size.
 *
 * @since 0.1.0
 */
public final class RelayConfig {

    private final Set<String> allowedDomains;
    private final Set<String> allowedSenders;
    private final Set<String> blockedSenders;
    private final boolean requireAuth;
    private final boolean openRelay;
    private final long maxMessageSize;

    private RelayConfig(Builder builder) {
        this.allowedDomains = Set.copyOf(builder.allowedDomains);
        this.allowedSenders = Set.copyOf(builder.allowedSenders);
        this.blockedSenders = Set.copyOf(builder.blockedSenders);
        this.requireAuth = builder.requireAuth;
        this.openRelay = builder.openRelay;
        this.maxMessageSize = builder.maxMessageSize;
    }

    /**
     * Returns a new builder.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a permissive relay config (open relay, no restrictions).
     *
     * @return an open relay config
     */
    public static RelayConfig openRelay() {
        return builder().openRelay(true).build();
    }

    /**
     * Returns whether a sender is allowed.
     *
     * @param sender the sender address
     * @return true if allowed
     */
    public boolean isSenderAllowed(String sender) {
        if (sender == null || sender.isEmpty()) return true; // bounce
        String lower = sender.toLowerCase();
        if (blockedSenders.contains(lower)) return false;
        if (allowedSenders.isEmpty()) return true;
        return allowedSenders.contains(lower);
    }

    /**
     * Returns whether a recipient is allowed (for relay).
     *
     * @param recipient the recipient address
     * @return true if the recipient's domain is in the allowed set or relay is open
     */
    public boolean isRecipientAllowed(String recipient) {
        if (openRelay || allowedDomains.isEmpty()) return true;
        if (recipient == null) return false;
        int atIdx = recipient.indexOf('@');
        if (atIdx < 0) return false;
        String domain = recipient.substring(atIdx + 1).toLowerCase();
        return allowedDomains.contains(domain);
    }

    /**
     * Returns whether authentication is required.
     *
     * @return true if auth is required
     */
    public boolean requireAuth() {
        return requireAuth;
    }

    /**
     * Returns whether this is an open relay.
     *
     * @return true if open relay
     */
    public boolean isOpenRelay() {
        return openRelay;
    }

    /**
     * Returns the maximum message size in bytes.
     *
     * @return the max size, or 0 for unlimited
     */
    public long maxMessageSize() {
        return maxMessageSize;
    }

    /**
     * Builder for {@link RelayConfig}.
     */
    public static final class Builder {
        private final Set<String> allowedDomains = new HashSet<>();
        private final Set<String> allowedSenders = new HashSet<>();
        private final Set<String> blockedSenders = new HashSet<>();
        private boolean requireAuth = false;
        private boolean openRelay = false;
        private long maxMessageSize = 10 * 1024 * 1024; // 10 MB default

        /**
         * Adds an allowed recipient domain.
         *
         * @param domain the domain (e.g., "example.com")
         * @return this builder
         */
        public Builder allowDomain(String domain) {
            allowedDomains.add(domain.toLowerCase());
            return this;
        }

        /**
         * Adds an allowed sender address.
         *
         * @param sender the sender address
         * @return this builder
         */
        public Builder allowSender(String sender) {
            allowedSenders.add(sender.toLowerCase());
            return this;
        }

        /**
         * Adds a blocked sender address.
         *
         * @param sender the sender address
         * @return this builder
         */
        public Builder blockSender(String sender) {
            blockedSenders.add(sender.toLowerCase());
            return this;
        }

        /**
         * Sets whether authentication is required.
         *
         * @param requireAuth true to require auth
         * @return this builder
         */
        public Builder requireAuth(boolean requireAuth) {
            this.requireAuth = requireAuth;
            return this;
        }

        /**
         * Sets whether this is an open relay.
         *
         * @param openRelay true for open relay
         * @return this builder
         */
        public Builder openRelay(boolean openRelay) {
            this.openRelay = openRelay;
            return this;
        }

        /**
         * Sets the maximum message size in bytes.
         *
         * @param maxMessageSize the max size (0 for unlimited)
         * @return this builder
         */
        public Builder maxMessageSize(long maxMessageSize) {
            this.maxMessageSize = maxMessageSize;
            return this;
        }

        /**
         * Builds the relay configuration.
         *
         * @return the relay config
         */
        public RelayConfig build() {
            return new RelayConfig(this);
        }
    }
}
