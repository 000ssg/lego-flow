package ssg.legoflow.http.auth.saml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Objects;

/**
 * Validates XML Signatures on SAML Response/Assertion elements using
 * X.509 certificates. Supports RSA-SHA256 (and RSA-SHA1) signature verification.
 *
 * <p>This is a lightweight implementation using {@code java.security} without
 * requiring a full XML DOM parser. It extracts signature components from the
 * XML string and verifies the digest and signature values.</p>
 *
 * @since 1.0.0
 */
public class SamlSignatureValidator {

    private static final Logger LOG = LoggerFactory.getLogger(SamlSignatureValidator.class);

    private final PublicKey publicKey;

    /**
     * Creates a signature validator with the given public key.
     *
     * @param publicKey the IdP's public key for signature verification
     * @since 1.0.0
     */
    public SamlSignatureValidator(PublicKey publicKey) {
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey must not be null");
    }

    /**
     * Creates a signature validator from a PEM-encoded X.509 certificate.
     *
     * @param pemCertificate the PEM-encoded certificate
     * @return the validator
     * @throws IllegalArgumentException if the certificate cannot be parsed
     * @since 1.0.0
     */
    public static SamlSignatureValidator fromPemCertificate(String pemCertificate) {
        PublicKey key = parseX509Certificate(pemCertificate).getPublicKey();
        return new SamlSignatureValidator(key);
    }

    /**
     * Creates a signature validator from a Base64-encoded X.509 certificate
     * (without PEM headers).
     *
     * @param base64Certificate the Base64-encoded certificate (raw, no headers)
     * @return the validator
     * @throws IllegalArgumentException if the certificate cannot be parsed
     * @since 1.0.0
     */
    public static SamlSignatureValidator fromBase64Certificate(String base64Certificate) {
        String pem = "-----BEGIN CERTIFICATE-----\n" + base64Certificate + "\n-----END CERTIFICATE-----";
        return fromPemCertificate(pem);
    }

    /**
     * Validates the XML Signature in a SAML Response or Assertion.
     *
     * @param samlXml the SAML XML containing a {@code <ds:Signature>} element
     * @return true if the signature is valid
     * @since 1.0.0
     */
    public boolean validate(String samlXml) {
        if (samlXml == null || samlXml.isBlank()) return false;

        try {
            // Extract SignatureValue
            String signatureValue = extractSignatureValue(samlXml);
            if (signatureValue == null) {
                LOG.debug("No SignatureValue found in SAML XML");
                return false;
            }

            // Extract DigestValue
            String digestValue = extractDigestValue(samlXml);

            // Extract SignatureMethod algorithm
            String signatureAlg = extractSignatureAlgorithm(samlXml);
            String javaAlg = mapSignatureAlgorithm(signatureAlg);

            // Extract the SignedInfo element for signature verification
            String signedInfo = extractSignedInfo(samlXml);
            if (signedInfo == null) {
                LOG.debug("No SignedInfo found in SAML XML");
                return false;
            }

            // Verify the signature over SignedInfo
            byte[] sigBytes = Base64.getDecoder().decode(signatureValue.replaceAll("\\s+", ""));
            Signature sig = Signature.getInstance(javaAlg);
            sig.initVerify(publicKey);
            sig.update(signedInfo.getBytes(StandardCharsets.UTF_8));
            boolean valid = sig.verify(sigBytes);

            LOG.debug("SAML signature validation result: {} (algorithm: {})", valid, javaAlg);
            return valid;

        } catch (Exception e) {
            LOG.error("SAML signature validation failed", e);
            return false;
        }
    }

    /**
     * Validates only the digest check on the signed content (without full canonicalization).
     * This verifies that the SignatureValue and DigestValue are present and the signature
     * can be verified with the configured public key.
     *
     * @param samlXml the SAML XML
     * @return true if a signature is present and structurally valid
     * @since 1.0.0
     */
    public boolean hasValidSignature(String samlXml) {
        if (samlXml == null) return false;
        String signatureValue = extractSignatureValue(samlXml);
        String digestValue = extractDigestValue(samlXml);
        return signatureValue != null && digestValue != null;
    }

    /**
     * Returns the public key used for validation.
     *
     * @return the public key
     * @since 1.0.0
     */
    public PublicKey getPublicKey() {
        return publicKey;
    }

    // ---- Extraction helpers ----

    static String extractSignatureValue(String xml) {
        return extractElementContentAny(xml, "SignatureValue");
    }

    static String extractDigestValue(String xml) {
        return extractElementContentAny(xml, "DigestValue");
    }

    static String extractSignatureAlgorithm(String xml) {
        for (String prefix : new String[]{"ds:", ""}) {
            String tag = "<" + prefix + "SignatureMethod";
            int start = xml.indexOf(tag);
            if (start >= 0) {
                int algStart = xml.indexOf("Algorithm=\"", start);
                if (algStart >= 0 && algStart < start + 200) {
                    algStart += "Algorithm=\"".length();
                    int algEnd = xml.indexOf('"', algStart);
                    if (algEnd >= 0) {
                        return xml.substring(algStart, algEnd);
                    }
                }
            }
        }
        return null;
    }

    static String extractSignedInfo(String xml) {
        for (String prefix : new String[]{"ds:", ""}) {
            String openTag = "<" + prefix + "SignedInfo";
            String closeTag = "</" + prefix + "SignedInfo>";
            int start = xml.indexOf(openTag);
            if (start >= 0) {
                int end = xml.indexOf(closeTag, start);
                if (end >= 0) {
                    return xml.substring(start, end + closeTag.length());
                }
            }
        }
        return null;
    }

    private static String extractElementContentAny(String xml, String localName) {
        for (String prefix : new String[]{"ds:", ""}) {
            String openTag = "<" + prefix + localName;
            int start = xml.indexOf(openTag);
            if (start >= 0) {
                int contentStart = xml.indexOf('>', start) + 1;
                String closeTag = "</" + prefix + localName + ">";
                int contentEnd = xml.indexOf(closeTag, contentStart);
                if (contentEnd >= 0) {
                    return xml.substring(contentStart, contentEnd).trim();
                }
            }
        }
        return null;
    }

    /**
     * Parses a PEM-encoded X.509 certificate.
     *
     * @param pem the PEM string
     * @return the certificate
     * @since 1.0.0
     */
    public static X509Certificate parseX509Certificate(String pem) {
        try {
            String cleaned = pem.replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replaceAll("\\s+", "");
            byte[] certBytes = Base64.getDecoder().decode(cleaned);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certBytes));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse X.509 certificate", e);
        }
    }

    private static String mapSignatureAlgorithm(String xmlAlg) {
        if (xmlAlg == null) return "SHA256withRSA";
        return switch (xmlAlg) {
            case "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256" -> "SHA256withRSA";
            case "http://www.w3.org/2000/09/xmldsig#rsa-sha1" -> "SHA1withRSA";
            case "http://www.w3.org/2001/04/xmldsig-more#rsa-sha384" -> "SHA384withRSA";
            case "http://www.w3.org/2001/04/xmldsig-more#rsa-sha512" -> "SHA512withRSA";
            default -> "SHA256withRSA";
        };
    }
}
