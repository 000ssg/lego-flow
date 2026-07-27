package ssg.legoflow.ssh.hostkey;

import ssg.legoflow.ssh.transport.SshTransportCodec;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * OpenSSH certificate structure per PROTOCOL.certkeys.
 *
 * <p>Supports parsing and encoding SSH certificates, including time validity
 * and principal matching. Certificate types supported:
 * <ul>
 *   <li>{@code ssh-ed25519-cert-v01@openssh.com}</li>
 *   <li>{@code ecdsa-sha2-nistp256-cert-v01@openssh.com}</li>
 *   <li>{@code rsa-sha2-256-cert-v01@openssh.com}</li>
 * </ul>
 *
 * @since 1.0.0
 */
public final class SshCertificate {

    /** Certificate type suffix for OpenSSH certs. */
    public static final String CERT_SUFFIX = "-cert-v01@openssh.com";

    private static final Map<String, String> CERT_TO_BASE = Map.of(
            "ssh-ed25519-cert-v01@openssh.com", "ssh-ed25519",
            "ecdsa-sha2-nistp256-cert-v01@openssh.com", "ecdsa-sha2-nistp256",
            "rsa-sha2-256-cert-v01@openssh.com", "rsa-sha2-256"
    );

    private final String certType;
    private final byte[] nonce;
    private final byte[] publicKey;
    private final long serial;
    private final CertType type;
    private final String keyId;
    private final List<String> validPrincipals;
    private final long validAfter;
    private final long validBefore;
    private final byte[] criticalOptions;
    private final byte[] extensions;
    private final byte[] reserved;
    private final byte[] signatureKey;
    private final byte[] signature;

    /**
     * Creates a new SSH certificate.
     *
     * @param certType        the certificate type string
     * @param nonce           random nonce
     * @param publicKey       the subject public key bytes (algorithm-specific)
     * @param serial          certificate serial number
     * @param type            USER or HOST
     * @param keyId           key identifier string
     * @param validPrincipals list of valid principals
     * @param validAfter      validity start (Unix seconds)
     * @param validBefore     validity end (Unix seconds)
     * @param criticalOptions encoded critical options
     * @param extensions      encoded extensions
     * @param reserved        reserved field (empty)
     * @param signatureKey    CA public key blob
     * @param signature       CA signature over the certificate
     */
    public SshCertificate(String certType, byte[] nonce, byte[] publicKey, long serial,
                          CertType type, String keyId, List<String> validPrincipals,
                          long validAfter, long validBefore,
                          byte[] criticalOptions, byte[] extensions,
                          byte[] reserved, byte[] signatureKey, byte[] signature) {
        this.certType = Objects.requireNonNull(certType);
        this.nonce = nonce.clone();
        this.publicKey = publicKey.clone();
        this.serial = serial;
        this.type = Objects.requireNonNull(type);
        this.keyId = Objects.requireNonNull(keyId);
        this.validPrincipals = List.copyOf(validPrincipals);
        this.validAfter = validAfter;
        this.validBefore = validBefore;
        this.criticalOptions = criticalOptions.clone();
        this.extensions = extensions.clone();
        this.reserved = reserved.clone();
        this.signatureKey = signatureKey.clone();
        this.signature = signature.clone();
    }

    /**
     * Returns the base algorithm name for a certificate type.
     *
     * @param certTypeName the certificate type string
     * @return the base algorithm name
     * @throws IllegalArgumentException if not a recognized cert type
     */
    public static String baseAlgorithm(String certTypeName) {
        String base = CERT_TO_BASE.get(certTypeName);
        if (base == null) {
            throw new IllegalArgumentException("Unknown certificate type: " + certTypeName);
        }
        return base;
    }

    /**
     * Returns the certificate type name for a base algorithm.
     *
     * @param baseAlgorithm the base algorithm name
     * @return the certificate type string
     */
    public static String certTypeName(String baseAlgorithm) {
        return baseAlgorithm + CERT_SUFFIX;
    }

    /**
     * Returns whether the given name is a certificate algorithm.
     *
     * @param name the algorithm name
     * @return true if it's a certificate type
     */
    public static boolean isCertificate(String name) {
        return CERT_TO_BASE.containsKey(name);
    }

    /**
     * Parses an SSH certificate from a wire format blob.
     *
     * @param certBlob the certificate blob
     * @return the parsed certificate
     */
    public static SshCertificate parse(byte[] certBlob) {
        ByteBuffer buf = ByteBuffer.wrap(certBlob);
        String certTypeName = SshTransportCodec.readString(buf);
        byte[] nonce = SshTransportCodec.readBinary(buf);

        // Read algorithm-specific public key fields
        byte[] publicKey = readPublicKeyFields(certTypeName, buf);

        long serial = buf.getLong();
        int typeValue = buf.getInt();
        CertType type = CertType.fromValue(typeValue);
        String keyId = SshTransportCodec.readString(buf);

        // Valid principals is a nested string list
        byte[] principalsBlob = SshTransportCodec.readBinary(buf);
        List<String> principals = parsePrincipals(principalsBlob);

        long validAfter = buf.getLong();
        long validBefore = buf.getLong();
        byte[] criticalOptions = SshTransportCodec.readBinary(buf);
        byte[] extensions = SshTransportCodec.readBinary(buf);
        byte[] reserved = SshTransportCodec.readBinary(buf);
        byte[] signatureKey = SshTransportCodec.readBinary(buf);
        byte[] signature = SshTransportCodec.readBinary(buf);

        return new SshCertificate(certTypeName, nonce, publicKey, serial, type, keyId,
                principals, validAfter, validBefore, criticalOptions, extensions,
                reserved, signatureKey, signature);
    }

    /**
     * Encodes this certificate to SSH wire format.
     *
     * @return the encoded certificate blob
     */
    public byte[] encode() {
        byte[] principalsBlob = encodePrincipals(validPrincipals);
        int publicKeySize = publicKey.length;

        ByteBuffer buf = ByteBuffer.allocate(4096 + publicKeySize + principalsBlob.length
                + criticalOptions.length + extensions.length + signatureKey.length + signature.length);

        SshTransportCodec.writeString(buf, certType);
        SshTransportCodec.writeBinary(buf, nonce);
        buf.put(publicKey);
        buf.putLong(serial);
        buf.putInt(type.value());
        SshTransportCodec.writeString(buf, keyId);
        SshTransportCodec.writeBinary(buf, principalsBlob);
        buf.putLong(validAfter);
        buf.putLong(validBefore);
        SshTransportCodec.writeBinary(buf, criticalOptions);
        SshTransportCodec.writeBinary(buf, extensions);
        SshTransportCodec.writeBinary(buf, reserved);
        SshTransportCodec.writeBinary(buf, signatureKey);
        SshTransportCodec.writeBinary(buf, signature);

        buf.flip();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    /**
     * Returns the bytes to be signed (everything except the final signature field).
     *
     * @return the to-be-signed data
     */
    public byte[] toBeSigned() {
        byte[] principalsBlob = encodePrincipals(validPrincipals);
        int publicKeySize = publicKey.length;

        ByteBuffer buf = ByteBuffer.allocate(4096 + publicKeySize + principalsBlob.length
                + criticalOptions.length + extensions.length + signatureKey.length);

        SshTransportCodec.writeString(buf, certType);
        SshTransportCodec.writeBinary(buf, nonce);
        buf.put(publicKey);
        buf.putLong(serial);
        buf.putInt(type.value());
        SshTransportCodec.writeString(buf, keyId);
        SshTransportCodec.writeBinary(buf, principalsBlob);
        buf.putLong(validAfter);
        buf.putLong(validBefore);
        SshTransportCodec.writeBinary(buf, criticalOptions);
        SshTransportCodec.writeBinary(buf, extensions);
        SshTransportCodec.writeBinary(buf, reserved);
        SshTransportCodec.writeBinary(buf, signatureKey);

        buf.flip();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    /**
     * Checks whether this certificate is valid for the given hostname and time.
     *
     * @param hostname the hostname to check against principals
     * @param now      the current time
     * @return true if the certificate is valid
     */
    public boolean isValid(String hostname, Instant now) {
        long nowSecs = now.getEpochSecond();
        if (nowSecs < validAfter || nowSecs >= validBefore) {
            return false;
        }
        if (validPrincipals.isEmpty()) {
            return true; // empty principals means valid for any
        }
        return validPrincipals.contains(hostname);
    }

    /** @return the certificate type string */
    public String certType() { return certType; }
    /** @return the nonce */
    public byte[] nonce() { return nonce.clone(); }
    /** @return the public key bytes */
    public byte[] publicKey() { return publicKey.clone(); }
    /** @return the serial number */
    public long serial() { return serial; }
    /** @return USER or HOST */
    public CertType type() { return type; }
    /** @return the key ID */
    public String keyId() { return keyId; }
    /** @return the valid principals */
    public List<String> validPrincipals() { return validPrincipals; }
    /** @return validity start as Unix seconds */
    public long validAfter() { return validAfter; }
    /** @return validity end as Unix seconds */
    public long validBefore() { return validBefore; }
    /** @return critical options */
    public byte[] criticalOptions() { return criticalOptions.clone(); }
    /** @return extensions */
    public byte[] extensions() { return extensions.clone(); }
    /** @return the CA signature key blob */
    public byte[] signatureKey() { return signatureKey.clone(); }
    /** @return the CA signature */
    public byte[] signature() { return signature.clone(); }

    private static byte[] readPublicKeyFields(String certTypeName, ByteBuffer buf) {
        // Record position before reading, then capture all public key fields
        int start = buf.position();
        String baseAlg = baseAlgorithm(certTypeName);
        switch (baseAlg) {
            case "ssh-ed25519" -> {
                SshTransportCodec.readBinary(buf); // ed25519 public key
            }
            case "ecdsa-sha2-nistp256" -> {
                SshTransportCodec.readString(buf); // curve identifier
                SshTransportCodec.readBinary(buf); // EC point
            }
            case "rsa-sha2-256" -> {
                SshTransportCodec.readBinary(buf); // e (exponent)
                SshTransportCodec.readBinary(buf); // n (modulus)
            }
            default -> throw new IllegalArgumentException("Unsupported certificate base: " + baseAlg);
        }
        int end = buf.position();
        byte[] publicKeyFields = new byte[end - start];
        buf.position(start);
        buf.get(publicKeyFields);
        return publicKeyFields;
    }

    private static List<String> parsePrincipals(byte[] blob) {
        if (blob.length == 0) return List.of();
        ByteBuffer buf = ByteBuffer.wrap(blob);
        List<String> principals = new ArrayList<>();
        while (buf.hasRemaining()) {
            principals.add(SshTransportCodec.readString(buf));
        }
        return List.copyOf(principals);
    }

    private static byte[] encodePrincipals(List<String> principals) {
        if (principals.isEmpty()) return new byte[0];
        int size = 0;
        for (String p : principals) {
            size += 4 + p.length();
        }
        ByteBuffer buf = ByteBuffer.allocate(size);
        for (String p : principals) {
            SshTransportCodec.writeString(buf, p);
        }
        return buf.array();
    }
}
