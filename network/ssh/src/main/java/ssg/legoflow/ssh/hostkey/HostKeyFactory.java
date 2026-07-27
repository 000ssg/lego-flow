package ssg.legoflow.ssh.hostkey;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Factory for creating host key algorithm instances by name.
 *
 * <p>Supports both standard host key algorithms and certificate-based variants.
 * Certificate algorithms (e.g., {@code ssh-ed25519-cert-v01@openssh.com}) are
 * created with an auto-generated CA key pair for testing convenience. For production
 * use, prefer {@link #createCertificate(String, SshKeyPair)}.
 *
 * @since 1.0.0
 */
public final class HostKeyFactory {

    private static final Map<String, Supplier<HostKeyAlgorithm>> ALGORITHMS;

    static {
        Map<String, Supplier<HostKeyAlgorithm>> map = new HashMap<>();
        map.put("ssh-ed25519", Ed25519::new);
        map.put("ecdsa-sha2-nistp256", EcdsaSha2Nistp256::new);
        map.put("ecdsa-sha2-nistp384", EcdsaSha2Nistp384::new);
        map.put("rsa-sha2-256", RsaSha256::new);
        map.put("rsa-sha2-512", RsaSha512::new);
        map.put("ssh-ed25519-cert-v01@openssh.com", () -> {
            Ed25519 alg = new Ed25519();
            SshKeyPair caKey = SshKeyPair.generate(alg);
            return new CertificateHostKeyAlgorithm(alg, caKey);
        });
        map.put("ecdsa-sha2-nistp256-cert-v01@openssh.com", () -> {
            EcdsaSha2Nistp256 alg = new EcdsaSha2Nistp256();
            SshKeyPair caKey = SshKeyPair.generate(alg);
            return new CertificateHostKeyAlgorithm(alg, caKey);
        });
        map.put("rsa-sha2-256-cert-v01@openssh.com", () -> {
            RsaSha256 alg = new RsaSha256();
            SshKeyPair caKey = SshKeyPair.generate(alg);
            return new CertificateHostKeyAlgorithm(alg, caKey);
        });
        ALGORITHMS = Map.copyOf(map);
    }

    private HostKeyFactory() {}

    /**
     * Creates a host key algorithm instance.
     *
     * @param name the algorithm name
     * @return a new algorithm instance
     * @throws IllegalArgumentException if not supported
     */
    public static HostKeyAlgorithm create(String name) {
        Supplier<HostKeyAlgorithm> supplier = ALGORITHMS.get(name);
        if (supplier == null) {
            throw new IllegalArgumentException("Unsupported host key algorithm: " + name);
        }
        return supplier.get();
    }

    /**
     * Creates a certificate host key algorithm with a specific CA key pair.
     *
     * @param name  the certificate algorithm name (e.g., "ssh-ed25519-cert-v01@openssh.com")
     * @param caKey the CA key pair for signing certificates
     * @return the certificate algorithm instance
     * @throws IllegalArgumentException if the name is not a supported certificate type
     */
    public static CertificateHostKeyAlgorithm createCertificate(String name, SshKeyPair caKey) {
        String baseAlg = SshCertificate.baseAlgorithm(name);
        HostKeyAlgorithm underlying = create(baseAlg);
        return new CertificateHostKeyAlgorithm(underlying, caKey);
    }

    /**
     * Returns whether the algorithm is supported.
     *
     * @param name the algorithm name
     * @return true if supported
     */
    public static boolean isSupported(String name) {
        return ALGORITHMS.containsKey(name);
    }

    /**
     * Returns all supported algorithm names.
     *
     * @return set of algorithm names
     */
    public static Set<String> supportedAlgorithms() {
        return ALGORITHMS.keySet();
    }
}
