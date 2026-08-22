package ssg.legoflow.http.auth.token;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
/**
 * JWT token provider implementing token generation and validation from scratch.
 * Supports HMAC-SHA256 (HS256) and RSA-SHA256 (RS256) signing algorithms.
 *
 * <p>Token format: Base64url(header).Base64url(payload).Base64url(signature)</p>
 *
 * @since 0.1.0
 */
public class JwtTokenProvider implements TokenProvider {

    private static final Logger LOG = LoggerFactory.getLogger(JwtTokenProvider.class);

    /** HMAC-SHA256 algorithm identifier. */
    public static final String HS256 = "HS256";
    /** RSA-SHA256 algorithm identifier. */
    public static final String RS256 = "RS256";

    private final String algorithm;
    private final byte[] hmacSecret;
    private final PrivateKey rsaPrivateKey;
    private final PublicKey rsaPublicKey;
    private final String issuer;
    private final Duration tokenLifetime;

    private JwtTokenProvider(String algorithm, byte[] hmacSecret, PrivateKey rsaPrivateKey,
                             PublicKey rsaPublicKey, String issuer, Duration tokenLifetime) {
        this.algorithm = algorithm;
        this.hmacSecret = hmacSecret;
        this.rsaPrivateKey = rsaPrivateKey;
        this.rsaPublicKey = rsaPublicKey;
        this.issuer = issuer;
        this.tokenLifetime = tokenLifetime;
    }

    /**
     * Creates an HMAC-SHA256 JWT provider.
     *
     * @param secret        the HMAC secret key
     * @param issuer        the token issuer
     * @param tokenLifetime the token lifetime
     * @return the JWT provider
     * @since 0.1.0
     */
    public static JwtTokenProvider hmac256(byte[] secret, String issuer, Duration tokenLifetime) {
        Objects.requireNonNull(secret, "secret must not be null");
        if (secret.length < 32) {
            throw new IllegalArgumentException("HMAC-SHA256 secret must be at least 32 bytes");
        }
        return new JwtTokenProvider(HS256, secret.clone(), null, null, issuer, tokenLifetime);
    }

    /**
     * Creates an HMAC-SHA256 JWT provider with a string secret.
     *
     * @param secret        the secret string
     * @param issuer        the token issuer
     * @param tokenLifetime the token lifetime
     * @return the JWT provider
     * @since 0.1.0
     */
    public static JwtTokenProvider hmac256(String secret, String issuer, Duration tokenLifetime) {
        return hmac256(secret.getBytes(StandardCharsets.UTF_8), issuer, tokenLifetime);
    }

    /**
     * Creates an RSA-SHA256 JWT provider.
     *
     * @param privateKey    the RSA private key (for signing)
     * @param publicKey     the RSA public key (for verification)
     * @param issuer        the token issuer
     * @param tokenLifetime the token lifetime
     * @return the JWT provider
     * @since 0.1.0
     */
    public static JwtTokenProvider rsa256(PrivateKey privateKey, PublicKey publicKey,
                                          String issuer, Duration tokenLifetime) {
        return new JwtTokenProvider(RS256, null, privateKey, publicKey, issuer, tokenLifetime);
    }

    /**
     * Creates an RSA-SHA256 JWT provider from raw key bytes.
     *
     * @param privateKeyBytes PKCS8-encoded private key bytes
     * @param publicKeyBytes  X509-encoded public key bytes
     * @param issuer          the token issuer
     * @param tokenLifetime   the token lifetime
     * @return the JWT provider
     * @throws GeneralSecurityException if key parsing fails
     * @since 0.1.0
     */
    public static JwtTokenProvider rsa256(byte[] privateKeyBytes, byte[] publicKeyBytes,
                                          String issuer, Duration tokenLifetime)
            throws GeneralSecurityException {
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
        PublicKey pub = kf.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
        return rsa256(priv, pub, issuer, tokenLifetime);
    }

    @Override
    public String generateToken(String subject, Map<String, Object> claims) {
        Objects.requireNonNull(subject, "subject must not be null");

        JwtHeader header = JwtHeader.of(algorithm);
        Instant now = Instant.now();

        JwtClaims jwtClaims = new JwtClaims()
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(tokenLifetime))
                .jwtId(UUID.randomUUID().toString());

        if (issuer != null) {
            jwtClaims.issuer(issuer);
        }

        // Add custom claims
        for (var entry : claims.entrySet()) {
            jwtClaims.claim(entry.getKey(), entry.getValue());
        }

        String headerB64 = base64UrlEncode(header.toJson().getBytes(StandardCharsets.UTF_8));
        String payloadB64 = base64UrlEncode(jwtClaims.toJson().getBytes(StandardCharsets.UTF_8));
        String signingInput = headerB64 + "." + payloadB64;

        String signature = sign(signingInput);
        return signingInput + "." + signature;
    }

    @Override
    public Optional<Map<String, Object>> validateToken(String token) {
        if (token == null || token.isBlank()) return Optional.empty();

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            LOG.debug("Invalid JWT format: expected 3 parts, got {}", parts.length);
            return Optional.empty();
        }

        // Verify signature
        String signingInput = parts[0] + "." + parts[1];
        if (!verify(signingInput, parts[2])) {
            LOG.debug("JWT signature verification failed");
            return Optional.empty();
        }

        // Parse claims
        String payloadJson = new String(base64UrlDecode(parts[1]), StandardCharsets.UTF_8);
        JwtClaims claims = JwtClaims.fromJson(payloadJson);

        // Check expiration
        if (claims.isExpired()) {
            LOG.debug("JWT has expired");
            return Optional.empty();
        }

        // Check not-before
        if (claims.isNotYetValid()) {
            LOG.debug("JWT is not yet valid");
            return Optional.empty();
        }

        // Check issuer
        if (issuer != null && !issuer.equals(claims.getIssuer())) {
            LOG.debug("JWT issuer mismatch: expected {}, got {}", issuer, claims.getIssuer());
            return Optional.empty();
        }

        return Optional.of(claims.toMap());
    }

    @Override
    public Optional<String> getSubject(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        String[] parts = token.split("\\.");
        if (parts.length != 3) return Optional.empty();

        try {
            String payloadJson = new String(base64UrlDecode(parts[1]), StandardCharsets.UTF_8);
            JwtClaims claims = JwtClaims.fromJson(payloadJson);
            return Optional.ofNullable(claims.getSubject());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean isExpired(String token) {
        if (token == null || token.isBlank()) return true;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return true;

        try {
            String payloadJson = new String(base64UrlDecode(parts[1]), StandardCharsets.UTF_8);
            JwtClaims claims = JwtClaims.fromJson(payloadJson);
            return claims.isExpired();
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Parses the header from a JWT token without validation.
     *
     * @param token the JWT token
     * @return the header, or empty if malformed
     * @since 0.1.0
     */
    public Optional<JwtHeader> parseHeader(String token) {
        if (token == null) return Optional.empty();
        String[] parts = token.split("\\.");
        if (parts.length < 2) return Optional.empty();
        try {
            String headerJson = new String(base64UrlDecode(parts[0]), StandardCharsets.UTF_8);
            return Optional.of(JwtHeader.fromJson(headerJson));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Parses claims from a JWT token without signature validation.
     *
     * @param token the JWT token
     * @return the claims, or empty if malformed
     * @since 0.1.0
     */
    public Optional<JwtClaims> parseClaims(String token) {
        if (token == null) return Optional.empty();
        String[] parts = token.split("\\.");
        if (parts.length < 2) return Optional.empty();
        try {
            String payloadJson = new String(base64UrlDecode(parts[1]), StandardCharsets.UTF_8);
            return Optional.of(JwtClaims.fromJson(payloadJson));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Returns the signing algorithm.
     *
     * @return the algorithm
     * @since 0.1.0
     */
    public String getAlgorithm() {
        return algorithm;
    }

    // ---- Signing and verification ----

    private String sign(String input) {
        byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
        return switch (algorithm) {
            case HS256 -> signHmac(inputBytes);
            case RS256 -> signRsa(inputBytes);
            default -> throw new IllegalStateException("Unsupported algorithm: " + algorithm);
        };
    }

    private boolean verify(String input, String signatureB64) {
        byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
        byte[] signature = base64UrlDecode(signatureB64);
        return switch (algorithm) {
            case HS256 -> verifyHmac(inputBytes, signature);
            case RS256 -> verifyRsa(inputBytes, signature);
            default -> false;
        };
    }

    private String signHmac(byte[] input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret, "HmacSHA256"));
            byte[] sig = mac.doFinal(input);
            return base64UrlEncode(sig);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("HMAC-SHA256 signing failed", e);
        }
    }

    private boolean verifyHmac(byte[] input, byte[] signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret, "HmacSHA256"));
            byte[] expected = mac.doFinal(input);
            return MessageDigest.isEqual(expected, signature);
        } catch (GeneralSecurityException e) {
            LOG.warn("HMAC-SHA256 verification error", e);
            return false;
        }
    }

    private String signRsa(byte[] input) {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(rsaPrivateKey);
            sig.update(input);
            return base64UrlEncode(sig.sign());
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("RSA-SHA256 signing failed", e);
        }
    }

    private boolean verifyRsa(byte[] input, byte[] signature) {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(rsaPublicKey);
            sig.update(input);
            return sig.verify(signature);
        } catch (GeneralSecurityException e) {
            LOG.warn("RSA-SHA256 verification error", e);
            return false;
        }
    }

    // ---- Base64url encoding/decoding ----

    /**
     * Base64url encodes data (no padding).
     *
     * @param data the data to encode
     * @return the base64url-encoded string
     * @since 0.1.0
     */
    public static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    /**
     * Base64url decodes a string.
     *
     * @param encoded the base64url-encoded string
     * @return the decoded bytes
     * @since 0.1.0
     */
    public static byte[] base64UrlDecode(String encoded) {
        return Base64.getUrlDecoder().decode(encoded);
    }
}
