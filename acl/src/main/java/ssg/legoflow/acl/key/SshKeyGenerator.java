package ssg.legoflow.acl.key;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.*;
import java.util.Base64;

public final class SshKeyGenerator {
    private SshKeyGenerator() {}

    public static SshKeyPair generate(String algorithm) { return generate(algorithm, 2048); }

    public static SshKeyPair generate(String algorithm, int keySize) {
        try {
            KeyPair kp;
            String wf;
            switch (algorithm.toLowerCase()) {
                case "rsa":
                    var kpg = KeyPairGenerator.getInstance("RSA");
                    kpg.initialize(new RSAKeyGenParameterSpec(keySize, RSAKeyGenParameterSpec.F4));
                    kp = kpg.generateKeyPair();
                    wf = "ssh-rsa";
                    break;
                case "ed25519":
                    var ed25519 = KeyPairGenerator.getInstance("Ed25519");
                    kp = ed25519.generateKeyPair();
                    wf = "ssh-ed25519";
                    break;
                case "ecdsa":
                    var ec = KeyPairGenerator.getInstance("EC");
                    ec.initialize(new ECGenParameterSpec("P-256"));
                    kp = ec.generateKeyPair();
                    wf = "ecdsa-sha2-nistp256";
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported SSH key algorithm: " + algorithm);
            }
            return new SshKeyPair(algorithm, wf, kp);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate SSH key pair: " + algorithm, e);
        }
    }

    public static SshKeyPair generateForUser(String algorithm, String username) {
        return generate(algorithm, algorithm.equalsIgnoreCase("rsa") ? 2048 : 0);
    }
}
