package ssg.legoflow.ssh.hostkey;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * SSH public key encoding and decoding per RFC 4253 section 6.6.
 *
 * <p>Handles conversion between SSH wire format (binary blob) and OpenSSH
 * authorized_keys format (type base64-key comment).
 *
 * @since 0.1.0
 */
public final class SshPublicKey {

    private final String keyType;
    private final byte[] keyBlob;
    private final String comment;

    /**
     * Creates a new SSH public key.
     *
     * @param keyType the key type (e.g., "ssh-rsa", "ssh-ed25519")
     * @param keyBlob the SSH wire format key blob
     * @param comment optional comment
     */
    public SshPublicKey(String keyType, byte[] keyBlob, String comment) {
        this.keyType = Objects.requireNonNull(keyType, "keyType");
        this.keyBlob = Objects.requireNonNull(keyBlob, "keyBlob").clone();
        this.comment = comment;
    }

    /**
     * Parses an OpenSSH authorized_keys line.
     *
     * @param line the authorized_keys line (e.g., "ssh-ed25519 AAAA... comment")
     * @return the parsed public key
     */
    public static SshPublicKey parse(String line) {
        String[] parts = line.trim().split("\\s+", 3);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid SSH public key format");
        }
        String keyType = parts[0];
        byte[] blob = Base64.getDecoder().decode(parts[1]);
        String comment = parts.length > 2 ? parts[2] : null;
        return new SshPublicKey(keyType, blob, comment);
    }

    /**
     * Extracts the key type from a key blob.
     *
     * @param blob the SSH wire format key blob
     * @return the key type string
     */
    public static String keyTypeFromBlob(byte[] blob) {
        ByteBuffer buf = ByteBuffer.wrap(blob);
        int len = buf.getInt();
        byte[] typeBytes = new byte[len];
        buf.get(typeBytes);
        return new String(typeBytes, StandardCharsets.US_ASCII);
    }

    /**
     * Formats this key in OpenSSH authorized_keys format.
     *
     * @return the formatted key line
     */
    public String toAuthorizedKeysLine() {
        StringBuilder sb = new StringBuilder();
        sb.append(keyType).append(' ');
        sb.append(Base64.getEncoder().encodeToString(keyBlob));
        if (comment != null && !comment.isEmpty()) {
            sb.append(' ').append(comment);
        }
        return sb.toString();
    }

    /**
     * Returns the key type.
     *
     * @return the key type string
     */
    public String keyType() { return keyType; }

    /**
     * Returns the key blob.
     *
     * @return copy of the SSH wire format key blob
     */
    public byte[] keyBlob() { return keyBlob.clone(); }

    /**
     * Returns the comment.
     *
     * @return the comment, or null
     */
    public String comment() { return comment; }

    /**
     * Returns the Base64-encoded fingerprint (SHA-256).
     *
     * @return the fingerprint string
     */
    public String fingerprint() {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(keyBlob);
            return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    @Override
    public String toString() {
        return keyType + " " + fingerprint();
    }
}
