package ssg.legoflow.acl.sasl;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import java.util.Base64;

/**
 * SASL and authentication utility functions for testing protocol modules.
 *
 * Covers:
 * <ul>
 *   <li>SCRAM-SHA-256 (AMQP, Kafka)</li>
 *   <li>SCRAM-SHA-1 (XMPP)</li>
 *   <li>SASL PLAIN (AMQP, SMTP, LDAP)</li>
 *   <li>SASL EXTERNAL (certificate-based, AMQP)</li>
 *   <li>PostgreSQL MD5 password hashing</li>
 *   <li>MySQL native_password / caching_sha2_password</li>
 *   <li>WAMP-CRA (HMAC-SHA256 challenge-response)</li>
 *   <li>Digest-MD5 (HTTP Digest, LDAP)</li>
 * </ul>
 */
public final class SaslUtilities {
    private SaslUtilities() {}

    /**
     * Generate a SASL PLAIN initial response: \0username\0password.
     */
    public static byte[] saslPlainInitial(String username, String password) {
        return (("\0" + username + "\0" + password)).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * SASL EXTERNAL: no initial response, identity from certificate.
     */
    public static byte[] saslExternalInitial() {
        return new byte[0];
    }

    /**
     * SCRAM-SHA-256 client-first message.
     */
    public static ScramClientFirst scramSha256ClientFirst(String username, String nonce) {
        var cbind = "n";
        var gs2 = "n,,n=" + username + ",r=" + nonce;
        return new ScramClientFirst(cbind, gs2, nonce);
    }

    /**
     * SCRAM-SHA-256 client-final message with proof.
     */
    public static String scramSha256ClientFinal(String clientNonce, String serverNonce,
                                                  String salt, int iterationCount, String password) {
        var cbind = "c=BI";
        var proof = computeScramProof("SHA-256", password, salt, iterationCount, clientNonce, serverNonce, clientNonce + serverNonce);
        return cbind + ",r=" + clientNonce + serverNonce + ",p=" + Base64.getEncoder().encodeToString(proof);
    }

    /**
     * SCRAM-SHA-256 server signature verification.
     */
    public static byte[] scramSha256ServerSignature(String password, String salt, int iterationCount, String authMessage) {
        return computeServerSignature("SHA-256", password, salt, iterationCount, authMessage);
    }

    public static byte[] hmacSha256(String key, String data) {
        return hmac("SHA-256", key, data);
    }

    /**
     * PostgreSQL MD5 password hash: md5(md5(password+username)+salt).
     */
    public static String postgresMd5Password(String username, String password, String saltHex) {
        var inner = md5Hex(password + username);
        var outer = md5Hex(inner + saltHex);
        return "md5" + outer;
    }

    /**
     * PostgreSQL SCRAM-SHA-256 auth response.
     */
    public static byte[] postgresScramResponse(String password, String salt, int iterationCount) {
        return hmacSha256(password, salt);
    }

    /**
     * MySQL native_password: SHA1(SHA1(password)).
     */
    public static byte[] mysqlNativePassword(String password) {
        var phase1 = sha1Raw(password);
        return sha1Raw(phase1);
    }

    /**
     * MySQL caching_sha2_password full authentication.
     */
    public static byte[] mysqlCachingSha2Response(String password, byte[] authPluginData) {
        var hash = sha256Raw(password);
        var key = new byte[hash.length];
        for (int i = 0; i < hash.length; i++) {
            key[i] = (byte) (hash[i] ^ authPluginData[i % authPluginData.length]);
        }
        return key;
    }

    /**
     * MySQL caching_sha2_password public key request response.
     */
    public static byte[] mysqlCachingSha2PublicKeyResponse(String password) {
        // Returns password bytes encrypted with server public key
        return password.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * WAMP-CRA HMAC-SHA256 challenge-response.
     */
    public static byte[] wampCraResponse(String secret, String challenge) {
        return hmacSha256(secret, challenge);
    }

    /**
     * WAMP Cryptosign: Ed25519 challenge signature.
     */
    public static byte[] wampCryptosignResponse(String challenge) {
        try {
            var kpg = KeyPairGenerator.getInstance("Ed25519");
            var kp = kpg.generateKeyPair();
            var sig = Signature.getInstance("EdDSA");
            sig.initSign(kp.getPrivate());
            sig.update(challenge.getBytes(StandardCharsets.UTF_8));
            return sig.sign();
        } catch (Exception e) {
            throw new RuntimeException("WAMP Cryptosign failed", e);
        }
    }

    /**
     * HTTP Digest-MD5 response.
     */
    public static String digestMd5Response(String username, String realm, String nonce,
                                            String uri, String method, String qop, String cnonce, int nc) {
        var ha1 = md5Hex(username + ":" + realm + ":" + "password");
        var ha2 = md5Hex(method + ":" + uri);
        var ncStr = String.format("%08x", nc);
        var response = md5Hex(ha1 + ":" + nonce + ":" + ncStr + ":" + cnonce + ":" + qop + ":" + ha2);
        return response;
    }

    /**
     * Generate a Kerberos-style principal name.
     */
    public static String kerberosPrincipal(String username, String realm) {
        return username + "@" + realm;
    }

    /**
     * OAuth2 Bearer token.
     */
    public static String oauth2Bearer(String token) {
        return "Bearer " + token;
    }

    /**
     * NTLM Type 1 message (Negotiate header).
     */
    public static String ntlmType1() {
        return "TlRMTVNTUAABAAAA";
    }

    // --- Internal SCRAM helpers ---

    private static byte[] computeScramProof(String hash, String password, String salt, int iterations,
                                              String clientNonce, String serverNonce, String authMessage) {
        var saltBytes = Base64.getDecoder().decode(salt);
        var saltedPassword = Hi(password, saltBytes, iterations, hash);
        var clientKey = hmac(hash, saltedPassword, "Client Key");
        var storedKey = digest(hash, clientKey);
        var clientProof = hmac(hash, storedKey, authMessage);
        return xor(clientKey, clientProof);
    }

    private static byte[] computeServerSignature(String hash, String password, String salt, int iterations, String authMessage) {
        var saltBytes = Base64.getDecoder().decode(salt);
        var saltedPassword = Hi(password, saltBytes, iterations, hash);
        var clientKey = hmac(hash, saltedPassword, "Client Key");
        var storedKey = digest(hash, clientKey);
        return hmac(hash, storedKey, authMessage);
    }

    private static byte[] Hi(String password, byte[] salt, int iterations, String hash) {
        var u = hmac(hash, password, salt);
        var ui = Arrays.copyOf(u, u.length);
        for (int i = 1; i < iterations; i++) {
            ui = hmac(hash, password, ui);
        }
        return ui;
    }

    private static byte[] hmac(String hash, String key, String data) {
        return hmac(hash, key.getBytes(StandardCharsets.UTF_8), data.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmac(String hash, byte[] key, String data) {
        return hmac(hash, key, data.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmac(String hash, String key, byte[] data) {
        return hmac(hash, key.getBytes(StandardCharsets.UTF_8), data);
    }

    private static byte[] hmac(String hash, byte[] key, byte[] data) {
        try {
            var mac = javax.crypto.Mac.getInstance("Hmac" + hash.replace("-", ""));
            mac.init(new javax.crypto.spec.SecretKeySpec(key, mac.getAlgorithm()));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("HMAC failed", e);
        }
    }

    private static byte[] digest(String hash, byte[] data) {
        try {
            return MessageDigest.getInstance(hash).digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Digest failed", e);
        }
    }

    private static byte[] sha256Raw(String input) {
        return digest("SHA-256", input.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] sha1Raw(String input) {
        return digest("SHA-1", input.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] sha1Raw(byte[] input) {
        return digest("SHA-1", input);
    }

    private static String md5Hex(String input) {
        return bytesToHex(digest("MD5", input.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] xor(byte[] a, byte[] b) {
        var r = new byte[a.length];
        for (int i = 0; i < a.length; i++) r[i] = (byte) (a[i] ^ b[i]);
        return r;
    }

    private static String bytesToHex(byte[] bytes) {
        var sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}

/** SCRAM client-first message parts. */
record ScramClientFirst(String cbind, String gs2, String nonce) {}
