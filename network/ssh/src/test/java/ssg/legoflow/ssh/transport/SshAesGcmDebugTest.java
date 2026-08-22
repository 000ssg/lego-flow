package ssg.legoflow.ssh.transport;

import ssg.legoflow.ssh.cipher.SshCipher;
import ssg.legoflow.ssh.cipher.CipherFactory;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip AES-GCM cipher-level unit test.
 *
 * <p>Tests the codec-level encrypt/decrypt path using the same data flow
 * as production: codec receives FULL WIRE DATA with 4-byte plaintext
 * pktLen prefix (matching SshTransport.readPacket wire).
 *
 * @since 0.2.0
 */
public class SshAesGcmDebugTest {

    @Test
    void testAesGcmEncryptDecryptRoundTrip() throws Exception {
        // Fixed key/IV
        byte[] keyC2S = new byte[32];
        Arrays.fill(keyC2S, (byte)0xAB);
        byte[] ivC2S = new byte[]{0x3d, (byte)0xea, 0x48, 0x4d, (byte)0x80, 0x52, (byte)0x99, (byte)0x5f};

        byte[] keyS2C = new byte[32];
        Arrays.fill(keyS2C, (byte)0xAC);
        byte[] ivS2C = new byte[]{0x11, 0x22, (byte)0xff, (byte)0xc4, 0x48, (byte)0xcd, (byte)0xd1, 0x07};

        // Create separate cipher instances for client and server
        SshCipher clientC2s = CipherFactory.create("aes256-gcm@openssh.com");
        SshCipher clientS2c = CipherFactory.create("aes256-gcm@openssh.com");
        SshCipher serverC2s = CipherFactory.create("aes256-gcm@openssh.com");
        SshCipher serverS2c = CipherFactory.create("aes256-gcm@openssh.com");

        clientC2s.init(keyC2S, ivC2S, true);
        clientS2c.init(keyS2C, ivS2C, true);
        serverC2s.init(keyC2S, ivC2S, true);
        serverS2c.init(keyS2C, ivS2C, true);

        // Create codecs
        SshTransportCodec clientCodec = new SshTransportCodec();
        SshTransportCodec serverCodec = new SshTransportCodec();

        clientCodec.setEncodeCipher(clientC2s);
        clientCodec.setDecodeCipher(clientS2c);
        serverCodec.setEncodeCipher(serverS2c);
        serverCodec.setDecodeCipher(serverC2s);

        // NEWKEYS payload (msg type 0x15)
        byte[] newKeysPayload = new byte[]{(byte)0x15};

        // === Forward: client → server ===
        // codec.encode() produces full wire with prefix
        byte[] clientWire = clientCodec.encode(newKeysPayload);

        // Production path: codec.decode() receives full wire WITH prefix
        byte[] serverPayload = serverCodec.decode(clientWire);

        assertArrayEquals(newKeysPayload, serverPayload,
            "Forward encrypt/decrypt should round-trip NEWKEYS payload");

        // === Reverse: server → client ===
        byte[] replyPayload = new byte[]{(byte)0x15};
        byte[] serverWire = serverCodec.encode(replyPayload);
        byte[] clientPayload = clientCodec.decode(serverWire);

        assertArrayEquals(replyPayload, clientPayload,
            "Reverse encrypt/decrypt should round-trip NEWKEYS payload");

        // === Multi-packet seq tracking ===
        byte[] p0 = new byte[]{(byte)0x15};  // NEWKEYS
        byte[] p1 = ByteBuffer.allocate(10).put((byte)0x02).putInt(0).array(); // USERAUTH_REQUEST
        byte[] p2 = new byte[]{(byte)0x6};  // SERVICE_ACCEPT

        byte[] w0 = clientCodec.encode(p0);
        byte[] d0 = serverCodec.decode(w0);
        assertArrayEquals(p0, d0);

        byte[] w1 = serverCodec.encode(p1);
        byte[] d1 = clientCodec.decode(w1);
        assertArrayEquals(p1, d1);

        byte[] w2 = clientCodec.encode(p2);
        byte[] d2 = serverCodec.decode(w2);
        assertArrayEquals(p2, d2);

        assertEquals(3, clientCodec.outputSequenceNumber(), "Client output seq should be 3");
        assertEquals(3, serverCodec.inputSequenceNumber(), "Server input seq should be 3");
    }

    @Test
    void testAes128GcmEncryptDecryptRoundTrip() throws Exception {
        byte[] key = new byte[16];
        Arrays.fill(key, (byte)0xAB);
        byte[] iv = new byte[12];
        Arrays.fill(iv, (byte)0xCD);

        SshCipher enc = CipherFactory.create("aes128-gcm@openssh.com");
        SshCipher dec = CipherFactory.create("aes128-gcm@openssh.com");
        enc.init(key, iv, true);
        dec.init(key, iv, true);

        SshTransportCodec codecEnc = new SshTransportCodec();
        SshTransportCodec codecDec = new SshTransportCodec();
        codecEnc.setEncodeCipher(enc);
        codecDec.setDecodeCipher(dec);

        byte[] payload = new byte[]{(byte)0x15, (byte)0x16, (byte)0x17};
        byte[] wire = codecEnc.encode(payload);
        byte[] decrypted = codecDec.decode(wire);
        assertArrayEquals(payload, decrypted, "AES-128-GCM round-trip should preserve payload");
    }

    @Test
    void testAesGcmSequenceNumberIndependence() throws Exception {
        byte[] keyC2S = new byte[32];
        Arrays.fill(keyC2S, (byte)0xAB);
        byte[] ivC2S = new byte[]{0x3d, (byte)0xea, 0x48, 0x4d, (byte)0x80, 0x52, (byte)0x99, (byte)0x5f};

        byte[] keyS2C = new byte[32];
        Arrays.fill(keyS2C, (byte)0xAC);
        byte[] ivS2C = new byte[]{0x11, 0x22, (byte)0xff, (byte)0xc4, 0x48, (byte)0xcd, (byte)0xd1, 0x07};

        SshCipher clientC2s = CipherFactory.create("aes256-gcm@openssh.com");
        SshCipher serverC2s = CipherFactory.create("aes256-gcm@openssh.com");
        clientC2s.init(keyC2S, ivC2S, true);
        serverC2s.init(keyC2S, ivC2S, true);

        SshTransportCodec clientCodec = new SshTransportCodec();
        SshTransportCodec serverCodec = new SshTransportCodec();
        clientCodec.setEncodeCipher(clientC2s);
        serverCodec.setDecodeCipher(serverC2s);

        byte[] payload = new byte[]{(byte)0x15};

        byte[] wire0 = clientCodec.encode(payload);
        serverCodec.decode(wire0);

        byte[] wire1 = clientCodec.encode(payload);
        serverCodec.decode(wire1);

        assertEquals(2, clientCodec.outputSequenceNumber());
        assertEquals(2, serverCodec.inputSequenceNumber());
    }
}
