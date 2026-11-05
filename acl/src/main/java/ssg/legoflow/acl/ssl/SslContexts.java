package ssg.legoflow.acl.ssl;

import ssg.legoflow.acl.cert.CertificateFactory;
import ssg.legoflow.acl.model.CertificateEntry;

import javax.net.ssl.*;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * SSL context helpers for creating SSLEngine instances from ACL domain certificates.
 * Used for TLS testing and certificate-based authentication.
 */
public final class SslContexts {
    private SslContexts() {}

    /**
     * Create an SSLContext configured for a server: loads the server certificate + private key,
     * trusts the CA and all domain certificates.
     */
    public static SSLContext serverContext(CertificateEntry serverCert, Collection<CertificateEntry> trustCerts,
                                            char[] storePassword) {
        try {
            var keyStoreBytes = CertificateFactory.toPKCS12(serverCert, storePassword);
            var keyStore = CertificateFactory.loadPKCS12(keyStoreBytes, storePassword);

            var trustStoreBytes = CertificateFactory.toTrustStorePKCS12(new ArrayList<>(trustCerts), storePassword);
            var trustStore = CertificateFactory.loadPKCS12(trustStoreBytes, storePassword);

            var kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, storePassword);

            var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            var ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
            return ctx;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create server SSLContext", e);
        }
    }

    /**
     * Create an SSLContext configured for a client: loads the client certificate + private key,
     * trusts the CA and all domain certificates.
     */
    public static SSLContext clientContext(CertificateEntry clientCert, Collection<CertificateEntry> trustCerts,
                                            char[] storePassword) {
        return serverContext(clientCert, trustCerts, storePassword);
    }

    /**
     * Create a trust-only SSLContext (no client cert). Used for clients that don't present a certificate.
     */
    public static SSLContext trustOnlyContext(Collection<CertificateEntry> trustCerts, char[] storePassword) {
        try {
            var trustStoreBytes = CertificateFactory.toTrustStorePKCS12(new ArrayList<>(trustCerts), storePassword);
            var trustStore = CertificateFactory.loadPKCS12(trustStoreBytes, storePassword);

            var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            var ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), new SecureRandom());
            return ctx;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create trust-only SSLContext", e);
        }
    }

    /**
     * Create an SSLEngine for the server side.
     */
    public static SSLEngine serverEngine(SSLContext context, String peerHost, int peerPort) {
        var engine = context.createSSLEngine(peerHost, peerPort);
        engine.setUseClientMode(false);
        return engine;
    }

    /**
     * Create an SSLEngine for the client side.
     */
    public static SSLEngine clientEngine(SSLContext context, String peerHost, int peerPort) {
        var engine = context.createSSLEngine(peerHost, peerPort);
        engine.setUseClientMode(true);
        return engine;
    }

    /**
     * Create a client SSLEngine that requests client authentication.
     */
    public static SSLEngine clientAuthServerEngine(SSLContext context, String peerHost, int peerPort) {
        var engine = serverEngine(context, peerHost, peerPort);
        engine.setNeedClientAuth(true);
        return engine;
    }

    /**
     * Extract the peer certificate chain from an SSLEngine after handshake.
     */
    public static X509Certificate[] getPeerCertificates(SSLEngine engine) {
        try {
            var certs = engine.getSession().getPeerCertificates();
            return java.util.Arrays.stream(certs)
                    .map(c -> (X509Certificate) c)
                    .toArray(X509Certificate[]::new);
        } catch (Exception e) {
            return new X509Certificate[0];
        }
    }
}
