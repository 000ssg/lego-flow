package ssg.legoflow.ssh.kex;

import ssg.legoflow.ssh.transport.SshTransportCodec;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * SSH_MSG_KEXINIT message (type 20) for algorithm negotiation per RFC 4253 section 7.1.
 *
 * <p>Contains 16 bytes of random cookie and ten name-lists for algorithm preferences:
 * kex, server host key, encryption (c2s/s2c), MAC (c2s/s2c), compression (c2s/s2c),
 * languages (c2s/s2c), plus first_kex_packet_follows flag.
 *
 * @since 0.1.0
 */
public record KexInit(
        byte[] cookie,
        List<String> kexAlgorithms,
        List<String> serverHostKeyAlgorithms,
        List<String> encryptionAlgorithmsClientToServer,
        List<String> encryptionAlgorithmsServerToClient,
        List<String> macAlgorithmsClientToServer,
        List<String> macAlgorithmsServerToClient,
        List<String> compressionAlgorithmsClientToServer,
        List<String> compressionAlgorithmsServerToClient,
        List<String> languagesClientToServer,
        List<String> languagesServerToClient,
        boolean firstKexPacketFollows
) {

    /**
     * Returns the SSH message type code for KEXINIT.
     *
     * @return the message type byte value (20)
     */
    public byte messageType() {
        return 20;
    }

    /**
     * Encodes this KEXINIT message to SSH binary format.
     *
     * @return the encoded payload bytes
     */
    public byte[] encode() {
        ByteBuffer buf = ByteBuffer.allocate(8192);
        buf.put((byte) 20); // SSH_MSG_KEXINIT
        buf.put(cookie);
        SshTransportCodec.writeNameList(buf, kexAlgorithms);
        SshTransportCodec.writeNameList(buf, serverHostKeyAlgorithms);
        SshTransportCodec.writeNameList(buf, encryptionAlgorithmsClientToServer);
        SshTransportCodec.writeNameList(buf, encryptionAlgorithmsServerToClient);
        SshTransportCodec.writeNameList(buf, macAlgorithmsClientToServer);
        SshTransportCodec.writeNameList(buf, macAlgorithmsServerToClient);
        SshTransportCodec.writeNameList(buf, compressionAlgorithmsClientToServer);
        SshTransportCodec.writeNameList(buf, compressionAlgorithmsServerToClient);
        SshTransportCodec.writeNameList(buf, languagesClientToServer);
        SshTransportCodec.writeNameList(buf, languagesServerToClient);
        SshTransportCodec.writeBoolean(buf, firstKexPacketFollows);
        buf.putInt(0); // reserved
        buf.flip();
        byte[] result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    /**
     * Decodes a KEXINIT message from SSH binary payload.
     *
     * @param payload the raw payload bytes (starting with message type byte)
     * @return the decoded KEXINIT message
     */
    public static KexInit decode(byte[] payload) {
        ByteBuffer buf = ByteBuffer.wrap(payload);
        byte msgType = buf.get();
        if (msgType != 20) {
            throw new IllegalArgumentException("Not a KEXINIT message: type " + msgType);
        }

        byte[] cookie = new byte[16];
        buf.get(cookie);

        List<String> kexAlgs = SshTransportCodec.readNameList(buf);
        List<String> hostKeyAlgs = SshTransportCodec.readNameList(buf);
        List<String> encC2s = SshTransportCodec.readNameList(buf);
        List<String> encS2c = SshTransportCodec.readNameList(buf);
        List<String> macC2s = SshTransportCodec.readNameList(buf);
        List<String> macS2c = SshTransportCodec.readNameList(buf);
        List<String> compC2s = SshTransportCodec.readNameList(buf);
        List<String> compS2c = SshTransportCodec.readNameList(buf);
        List<String> langC2s = SshTransportCodec.readNameList(buf);
        List<String> langS2c = SshTransportCodec.readNameList(buf);
        boolean firstFollows = SshTransportCodec.readBoolean(buf);
        buf.getInt(); // reserved

        return new KexInit(cookie, kexAlgs, hostKeyAlgs, encC2s, encS2c,
                macC2s, macS2c, compC2s, compS2c, langC2s, langS2c, firstFollows);
    }

    /**
     * Creates a default KEXINIT with standard algorithm preferences.
     *
     * @return a new KEXINIT with default algorithm lists
     */
    public static KexInit defaultKexInit() {
        byte[] cookie = new byte[16];
        new SecureRandom().nextBytes(cookie);

        return new KexInit(
                cookie,
                List.of("curve25519-sha256", "ecdh-sha2-nistp256", "ecdh-sha2-nistp384",
                        "ecdh-sha2-nistp521", "diffie-hellman-group16-sha512",
                        "diffie-hellman-group14-sha256"),
                List.of("ssh-ed25519", "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384",
                        "rsa-sha2-512", "rsa-sha2-256"),
                List.of("chacha20-poly1305@openssh.com", "aes256-gcm@openssh.com",
                        "aes128-gcm@openssh.com", "aes256-ctr", "aes192-ctr", "aes128-ctr"),
                List.of("chacha20-poly1305@openssh.com", "aes256-gcm@openssh.com",
                        "aes128-gcm@openssh.com", "aes256-ctr", "aes192-ctr", "aes128-ctr"),
                List.of("hmac-sha2-512-etm@openssh.com", "hmac-sha2-256-etm@openssh.com",
                        "hmac-sha2-512", "hmac-sha2-256"),
                List.of("hmac-sha2-512-etm@openssh.com", "hmac-sha2-256-etm@openssh.com",
                        "hmac-sha2-512", "hmac-sha2-256"),
                List.of("none", "zlib@openssh.com", "zlib"),
                List.of("none", "zlib@openssh.com", "zlib"),
                List.of(),
                List.of(),
                false
        );
    }
}
