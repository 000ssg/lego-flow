package ssg.legoflow.acl.cert;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import ssg.legoflow.acl.model.CertificateEntry;

import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.spec.RSAKeyGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Certificate generation tools for creating test domains with self-signed or
 * CA-signed certificates. Uses BouncyCastle's JcaX509v3CertBuilder which is
 * the standard approach on JDK 25+ (internal sun.security.x509 APIs are sealed).
 */
public final class CertificateFactory {
    private CertificateFactory() {}

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    /** Generate a self-signed certificate for the given subject and key size. */
    public static CertificateEntry selfSigned(String alias, String subject, int keySize, long validityYears) {
        var seconds = validityYears * 365L * 24L * 3600L;
        return selfSigned(alias, subject, keySize, Instant.now(), Instant.now().plus(seconds, ChronoUnit.SECONDS));
    }

    /** Generate a self-signed certificate with explicit validity. */
    public static CertificateEntry selfSigned(String alias, String subject, int keySize,
                                                Instant notBefore, Instant notAfter) {
        try {
            var keySizeSpec = new RSAKeyGenParameterSpec(keySize, RSAKeyGenParameterSpec.F4);
            var keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(keySizeSpec);
            var keyPair = keyGen.generateKeyPair();

            var subjName = new X500Name(subject);
            var builder = new JcaX509v3CertificateBuilder(
                    subjName,
                    BigInteger.valueOf(System.currentTimeMillis() & Long.MAX_VALUE),
                    Date.from(notBefore), Date.from(notAfter),
                    subjName,
                    keyPair.getPublic());
            var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
            var certHolder = builder.build(signer);
            var cert = new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME).getCertificate(certHolder);
            return new CertificateEntry(alias, cert, keyPair.getPrivate());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate self-signed cert: " + subject, e);
        }
    }

    /**
     * Generate a CA-signed certificate. The issuer signs using the issuer's private key.
     */
    public static CertificateEntry caSigned(String alias, String subject, int keySize,
                                              CertificateEntry issuer,
                                              Instant notBefore, Instant notAfter) {
        try {
            var keySizeSpec = new RSAKeyGenParameterSpec(keySize, RSAKeyGenParameterSpec.F4);
            var keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(keySizeSpec);
            var keyPair = keyGen.generateKeyPair();

            var issuerName = new X500Name(issuer.certificate().getIssuerX500Principal().getName());
            var subjectName = new X500Name(subject);
            var builder = new JcaX509v3CertificateBuilder(
                    issuerName,
                    BigInteger.valueOf(System.currentTimeMillis() & Long.MAX_VALUE),
                    Date.from(notBefore), Date.from(notAfter),
                    subjectName,
                    keyPair.getPublic());
            var signer = new JcaContentSignerBuilder("SHA256withRSA").build(issuer.privateKey());
            var certHolder = builder.build(signer);
            var cert = new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME).getCertificate(certHolder);
            return new CertificateEntry(alias, cert, keyPair.getPrivate());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate CA-signed cert: " + subject, e);
        }
    }

    /** Generate a CA-signed certificate with default validity. */
    public static CertificateEntry caSigned(String alias, String subject, int keySize,
                                              CertificateEntry issuer, long validityYears) {
        var seconds = validityYears * 365L * 24L * 3600L;
        return caSigned(alias, subject, keySize, issuer, Instant.now(), Instant.now().plus(seconds, ChronoUnit.SECONDS));
    }

    /**
     * Generate a complete test domain with a CA and signed certificates for each user.
     * <p>Each subject is checked: if it already starts with "CN=" it is used as-is.
     * Otherwise "CN=<subject>,O=<domainName>" is constructed.</p>
     */
    public static DomainCerts generateDomainCerts(String domainName, int keySize, long validityYears,
                                                    String... subjects) {
        var caEntry = selfSigned(domainName + "-CA", "CN=" + domainName + " CA,O=" + domainName, keySize, validityYears);
        var signed = new ArrayList<CertificateEntry>();
        for (String subject : subjects) {
            String dn = subject.toLowerCase().startsWith("cn=") ? subject : "CN=" + subject + ",O=" + domainName;
            var entry = caSigned(subject, dn, keySize, caEntry, validityYears);
            signed.add(entry);
        }
        return new DomainCerts(caEntry, signed);
    }

    /** Convert a CertificateEntry to PKCS12 keystore. */
    public static byte[] toPKCS12(CertificateEntry entry, char[] storePassword) {
        try {
            var ks = KeyStore.getInstance("PKCS12");
            ks.load(null);
            ks.setKeyEntry(entry.alias(), entry.privateKey(), storePassword, entry.keyChain());
            var baos = new java.io.ByteArrayOutputStream();
            ks.store(baos, storePassword);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create PKCS12", e);
        }
    }

    /** Convert a list of certificates to a truststore (PKCS12). */
    public static byte[] toTrustStorePKCS12(List<CertificateEntry> entries, char[] storePassword) {
        try {
            var ks = KeyStore.getInstance("PKCS12");
            ks.load(null);
            for (var entry : entries) {
                ks.setCertificateEntry(entry.alias(), entry.certificate());
            }
            var baos = new java.io.ByteArrayOutputStream();
            ks.store(baos, storePassword);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create truststore", e);
        }
    }

    /** Load a PKCS12 keystore from bytes. */
    public static KeyStore loadPKCS12(byte[] bytes, char[] password) {
        try {
            var ks = KeyStore.getInstance("PKCS12");
            ks.load(new java.io.ByteArrayInputStream(bytes), password);
            return ks;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load PKCS12", e);
        }
    }
}
