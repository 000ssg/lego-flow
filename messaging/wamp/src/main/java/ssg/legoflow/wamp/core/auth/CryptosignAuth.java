package ssg.legoflow.wamp.core.auth;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.EdECPrivateKeySpec;
import java.security.spec.EdECPublicKeySpec;
import java.security.spec.NamedParameterSpec;
import java.util.HexFormat;
import java.util.Map;

/**
 * WAMP Cryptosign Authentication using Ed25519 digital signatures.
 *
 * <p>Flow:
 * <ol>
 *   <li>Client sends HELLO with {@code authid}, {@code authmethods: ["cryptosign"]},
 *       and its Ed25519 public key in hex</li>
 *   <li>Router sends CHALLENGE with a random hex-encoded challenge</li>
 *   <li>Client signs the challenge with its Ed25519 private key</li>
 *   <li>Router verifies the signature with the client's public key</li>
 * </ol>
 *
 * @since 0.1.0
 */
public class CryptosignAuth {

    /** The authentication method identifier for cryptosign. */
    public static final String AUTH_METHOD = "cryptosign";

    /**
     * Generates a random challenge for cryptosign authentication.
     *
     * @return the challenge details map with hex-encoded challenge
     */
    public static Map<String, Object> generateChallenge() {
        byte[] challenge = new byte[32];
        new java.security.SecureRandom().nextBytes(challenge);
        return Map.of("challenge", HexFormat.of().formatHex(challenge));
    }

    /**
     * Generates an Ed25519 key pair for cryptosign authentication.
     *
     * @return the generated key pair
     */
    public static KeyPair generateKeyPair() {
        try {
            var gen = KeyPairGenerator.getInstance("Ed25519");
            return gen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Ed25519 not available", e);
        }
    }

    /**
     * Signs a hex-encoded challenge with an Ed25519 private key.
     *
     * @param challengeHex the hex-encoded challenge string
     * @param privateKey   the Ed25519 private key
     * @return the hex-encoded signature concatenated with the challenge
     */
    public static String sign(String challengeHex, PrivateKey privateKey) {
        try {
            var sig = Signature.getInstance("Ed25519");
            sig.initSign(privateKey);
            byte[] challengeBytes = HexFormat.of().parseHex(challengeHex);
            sig.update(challengeBytes);
            byte[] signature = sig.sign();
            return HexFormat.of().formatHex(signature) + challengeHex;
        } catch (Exception e) {
            throw new RuntimeException("Cryptosign signing failed", e);
        }
    }

    /**
     * Verifies a cryptosign signature against a challenge using the client's public key.
     *
     * @param signatureAndChallenge the hex signature followed by the hex challenge
     * @param challengeHex          the original hex challenge
     * @param publicKey             the client's Ed25519 public key
     * @return {@code true} if the signature is valid
     */
    public static boolean verify(String signatureAndChallenge, String challengeHex, PublicKey publicKey) {
        try {
            // Signature is 64 bytes = 128 hex chars, followed by challenge
            if (signatureAndChallenge.length() < 128) return false;
            String sigHex = signatureAndChallenge.substring(0, 128);
            byte[] sigBytes = HexFormat.of().parseHex(sigHex);
            byte[] challengeBytes = HexFormat.of().parseHex(challengeHex);

            var sig = Signature.getInstance("Ed25519");
            sig.initVerify(publicKey);
            sig.update(challengeBytes);
            return sig.verify(sigBytes);
        } catch (Exception e) {
            return false;
        }
    }
}
