package ssg.legoflow.acl.key;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

record SshKeyPair(String algorithm, String wireFormat, KeyPair keyPair) {

    public String publicKeyOpenSsh() {
        var pubKey = keyPair.getPublic();
        byte[] encoded;
        switch (algorithm.toLowerCase()) {
            case "rsa": encoded = encodeRsaPublicKey((RSAPublicKey) pubKey); break;
            case "ed25519": encoded = encodeEd25519PublicKey(pubKey); break;
            case "ecdsa": encoded = encodeEcDsaPublicKey((ECPublicKey) pubKey); break;
            default: throw new IllegalStateException("Unknown algorithm: " + algorithm);
        }
        return wireFormat + " " + Base64.getEncoder().encodeToString(encoded);
    }

    public void writePrivateKey(Path path) {
        try { Files.writeString(path, privateKeyPem()); } catch (Exception e) { throw new RuntimeException(e); }
    }

    public void writePublicKey(Path path) {
        try { Files.writeString(path, publicKeyOpenSsh()); } catch (Exception e) { throw new RuntimeException(e); }
    }

    public String privateKeyPem() {
        var encoded = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        var sb = new StringBuilder();
        sb.append("-----BEGIN OPENSSH PRIVATE KEY-----\n");
        for (int i = 0; i < encoded.length(); i += 70) {
            sb.append(encoded, i, Math.min(i + 70, encoded.length())).append("\n");
        }
        sb.append("-----END OPENSSH PRIVATE KEY-----\n");
        return sb.toString();
    }

    private static byte[] encodeRsaPublicKey(RSAPublicKey key) {
        var os = new java.io.ByteArrayOutputStream();
        sshString(os, "ssh-rsa");
        sshMpint(os, key.getPublicExponent());
        sshMpint(os, key.getModulus());
        return os.toByteArray();
    }

    private static byte[] encodeEd25519PublicKey(PublicKey key) {
        var os = new java.io.ByteArrayOutputStream();
        sshString(os, "ssh-ed25519");
        var pubBytes = key.getEncoded();
        var raw = new byte[32];
        System.arraycopy(pubBytes, 12, raw, 0, 32);
        sshBytes(os, raw);
        return os.toByteArray();
    }

    private static byte[] encodeEcDsaPublicKey(ECPublicKey key) {
        var os = new java.io.ByteArrayOutputStream();
        sshString(os, "ecdsa-sha2-nistp256");
        sshString(os, "nistp256");
        var pubBytes = key.getEncoded();
        var raw = new byte[pubBytes.length - 1];
        System.arraycopy(pubBytes, 1, raw, 0, raw.length);
        sshBytes(os, raw);
        return os.toByteArray();
    }

    private static void sshString(java.io.ByteArrayOutputStream os, String s) {
        try { os.write(s.getBytes(StandardCharsets.UTF_8)); } catch (java.io.IOException e) { throw new RuntimeException(e); }
    }

    private static void sshBytes(java.io.ByteArrayOutputStream os, byte[] b) {
        try { os.write(b); } catch (java.io.IOException e) { throw new RuntimeException(e); }
    }

    private static void sshMpint(java.io.ByteArrayOutputStream os, BigInteger n) {
        try { os.write(n.toByteArray()); } catch (java.io.IOException e) { throw new RuntimeException(e); }
    }
}
