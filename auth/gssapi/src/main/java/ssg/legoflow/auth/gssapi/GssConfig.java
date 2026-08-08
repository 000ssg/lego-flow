package ssg.legoflow.auth.gssapi;

import java.util.Objects;

/**
 * Configuration for GSS-API / Kerberos authentication.
 *
 * <p>Holds the Kerberos realm, KDC hostname, service principal, and optional
 * keytab path. Can apply settings as JVM system properties for the built-in
 * Kerberos implementation.</p>
 *
 * @since 0.1.0
 */
public final class GssConfig {

    private final String realm;
    private final String kdc;
    private final String servicePrincipal;
    private final String keytabPath;
    private final boolean useSubjectCredsOnly;

    private GssConfig(Builder builder) {
        this.realm = Objects.requireNonNull(builder.realm, "realm must not be null");
        this.kdc = Objects.requireNonNull(builder.kdc, "kdc must not be null");
        this.servicePrincipal = Objects.requireNonNull(builder.servicePrincipal, "servicePrincipal must not be null");
        this.keytabPath = builder.keytabPath;
        this.useSubjectCredsOnly = builder.useSubjectCredsOnly;
    }

    /**
     * Returns the Kerberos realm (e.g., "EXAMPLE.COM").
     *
     * @return the realm
     * @since 0.1.0
     */
    public String realm() {
        return realm;
    }

    /**
     * Returns the KDC hostname.
     *
     * @return the KDC hostname
     * @since 0.1.0
     */
    public String kdc() {
        return kdc;
    }

    /**
     * Returns the service principal (e.g., "host/server.example.com@EXAMPLE.COM").
     *
     * @return the service principal
     * @since 0.1.0
     */
    public String servicePrincipal() {
        return servicePrincipal;
    }

    /**
     * Returns the path to the keytab file, or null if not set.
     *
     * @return the keytab path, or null
     * @since 0.1.0
     */
    public String keytabPath() {
        return keytabPath;
    }

    /**
     * Returns whether to use only credentials from the Subject.
     *
     * @return true if only Subject credentials should be used
     * @since 0.1.0
     */
    public boolean useSubjectCredsOnly() {
        return useSubjectCredsOnly;
    }

    /**
     * Applies this configuration as JVM system properties for the Kerberos subsystem.
     *
     * <p>Sets {@code java.security.krb5.realm} and {@code java.security.krb5.kdc}.</p>
     *
     * @since 0.1.0
     */
    public void applyAsSystemProperties() {
        System.setProperty("java.security.krb5.realm", realm);
        System.setProperty("java.security.krb5.kdc", kdc);
        System.setProperty("javax.security.auth.useSubjectCredsOnly",
                String.valueOf(useSubjectCredsOnly));
    }

    /**
     * Creates a new builder for GssConfig.
     *
     * @return the builder
     * @since 0.1.0
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "GssConfig{realm='" + realm + "', kdc='" + kdc +
                "', servicePrincipal='" + servicePrincipal +
                "', keytabPath='" + keytabPath +
                "', useSubjectCredsOnly=" + useSubjectCredsOnly + '}';
    }

    /**
     * Builder for {@link GssConfig}.
     *
     * @since 0.1.0
     */
    public static final class Builder {

        private String realm;
        private String kdc;
        private String servicePrincipal;
        private String keytabPath;
        private boolean useSubjectCredsOnly = true;

        private Builder() {
        }

        /**
         * Sets the Kerberos realm.
         *
         * @param realm the realm (e.g., "EXAMPLE.COM")
         * @return this builder
         * @since 0.1.0
         */
        public Builder realm(String realm) {
            this.realm = realm;
            return this;
        }

        /**
         * Sets the KDC hostname.
         *
         * @param kdc the KDC hostname
         * @return this builder
         * @since 0.1.0
         */
        public Builder kdc(String kdc) {
            this.kdc = kdc;
            return this;
        }

        /**
         * Sets the service principal.
         *
         * @param servicePrincipal the service principal
         * @return this builder
         * @since 0.1.0
         */
        public Builder servicePrincipal(String servicePrincipal) {
            this.servicePrincipal = servicePrincipal;
            return this;
        }

        /**
         * Sets the keytab file path.
         *
         * @param keytabPath the keytab file path, or null
         * @return this builder
         * @since 0.1.0
         */
        public Builder keytabPath(String keytabPath) {
            this.keytabPath = keytabPath;
            return this;
        }

        /**
         * Sets whether to use only credentials from the Subject.
         *
         * @param useSubjectCredsOnly true to use only Subject credentials (default: true)
         * @return this builder
         * @since 0.1.0
         */
        public Builder useSubjectCredsOnly(boolean useSubjectCredsOnly) {
            this.useSubjectCredsOnly = useSubjectCredsOnly;
            return this;
        }

        /**
         * Builds the GssConfig.
         *
         * @return the configuration
         * @throws NullPointerException if required fields are missing
         * @since 0.1.0
         */
        public GssConfig build() {
            return new GssConfig(this);
        }
    }
}
