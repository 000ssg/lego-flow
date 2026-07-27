package ssg.legoflow.http.auth.spnego;

import ssg.legoflow.auth.gssapi.GssConfig;

import java.util.Objects;

/**
 * Configuration for SPNEGO (Negotiate) HTTP authentication.
 *
 * <p>Combines a {@link GssConfig} for the underlying Kerberos/GSSAPI settings
 * with SPNEGO-specific options like realm stripping from principal names.</p>
 *
 * @since 1.0.0
 */
public final class SpnegoConfig {

    private final GssConfig gssConfig;
    private final boolean stripRealmFromPrincipal;

    private SpnegoConfig(Builder builder) {
        this.gssConfig = Objects.requireNonNull(builder.gssConfig, "gssConfig must not be null");
        this.stripRealmFromPrincipal = builder.stripRealmFromPrincipal;
    }

    /**
     * Returns the underlying GSS-API / Kerberos configuration.
     *
     * @return the GSS configuration
     * @since 1.0.0
     */
    public GssConfig gssConfig() {
        return gssConfig;
    }

    /**
     * Returns whether the realm portion should be stripped from the authenticated
     * principal name (e.g., "user@EXAMPLE.COM" becomes "user").
     *
     * @return true if realm stripping is enabled (default: true)
     * @since 1.0.0
     */
    public boolean stripRealmFromPrincipal() {
        return stripRealmFromPrincipal;
    }

    /**
     * Creates a new builder for SpnegoConfig.
     *
     * @return the builder
     * @since 1.0.0
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a SpnegoConfig with default settings using the given GssConfig.
     *
     * @param gssConfig the GSS configuration
     * @return the SPNEGO configuration with defaults
     * @since 1.0.0
     */
    public static SpnegoConfig of(GssConfig gssConfig) {
        return builder().gssConfig(gssConfig).build();
    }

    @Override
    public String toString() {
        return "SpnegoConfig{gssConfig=" + gssConfig +
                ", stripRealmFromPrincipal=" + stripRealmFromPrincipal + '}';
    }

    /**
     * Builder for {@link SpnegoConfig}.
     *
     * @since 1.0.0
     */
    public static final class Builder {

        private GssConfig gssConfig;
        private boolean stripRealmFromPrincipal = true;

        private Builder() {
        }

        /**
         * Sets the underlying GSS-API configuration.
         *
         * @param gssConfig the GSS configuration
         * @return this builder
         * @since 1.0.0
         */
        public Builder gssConfig(GssConfig gssConfig) {
            this.gssConfig = gssConfig;
            return this;
        }

        /**
         * Sets whether to strip the realm from the authenticated principal name.
         *
         * @param strip true to strip realm (default: true)
         * @return this builder
         * @since 1.0.0
         */
        public Builder stripRealmFromPrincipal(boolean strip) {
            this.stripRealmFromPrincipal = strip;
            return this;
        }

        /**
         * Builds the SpnegoConfig.
         *
         * @return the configuration
         * @throws NullPointerException if gssConfig is null
         * @since 1.0.0
         */
        public SpnegoConfig build() {
            return new SpnegoConfig(this);
        }
    }
}
