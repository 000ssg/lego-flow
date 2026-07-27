package ssg.legoflow.ssh.kex;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EcdhKexTest {

    @Test
    void testNistp256Name() {
        assertThat(new EcdhSha2Nistp256().name()).isEqualTo("ecdh-sha2-nistp256");
    }

    @Test
    void testNistp384Name() {
        assertThat(new EcdhSha2Nistp384().name()).isEqualTo("ecdh-sha2-nistp384");
    }

    @Test
    void testNistp521Name() {
        assertThat(new EcdhSha2Nistp521().name()).isEqualTo("ecdh-sha2-nistp521");
    }

    @Test
    void testNistp256HashAlgorithm() {
        assertThat(new EcdhSha2Nistp256().hashAlgorithm()).isEqualTo("SHA-256");
    }

    @Test
    void testNistp384HashAlgorithm() {
        assertThat(new EcdhSha2Nistp384().hashAlgorithm()).isEqualTo("SHA-384");
    }

    @Test
    void testNistp521HashAlgorithm() {
        assertThat(new EcdhSha2Nistp521().hashAlgorithm()).isEqualTo("SHA-512");
    }

    @Test
    void testNistp256KeyExchange() {
        EcdhSha2Nistp256 client = new EcdhSha2Nistp256();
        EcdhSha2Nistp256 server = new EcdhSha2Nistp256();
        client.init();
        server.init();
        byte[] clientSecret = client.computeSharedSecret(server.localPublicValue());
        byte[] serverSecret = server.computeSharedSecret(client.localPublicValue());
        assertThat(clientSecret).isEqualTo(serverSecret);
    }

    @Test
    void testNistp384KeyExchange() {
        EcdhSha2Nistp384 client = new EcdhSha2Nistp384();
        EcdhSha2Nistp384 server = new EcdhSha2Nistp384();
        client.init();
        server.init();
        byte[] clientSecret = client.computeSharedSecret(server.localPublicValue());
        byte[] serverSecret = server.computeSharedSecret(client.localPublicValue());
        assertThat(clientSecret).isEqualTo(serverSecret);
    }

    @Test
    void testNistp521KeyExchange() {
        EcdhSha2Nistp521 client = new EcdhSha2Nistp521();
        EcdhSha2Nistp521 server = new EcdhSha2Nistp521();
        client.init();
        server.init();
        byte[] clientSecret = client.computeSharedSecret(server.localPublicValue());
        byte[] serverSecret = server.computeSharedSecret(client.localPublicValue());
        assertThat(clientSecret).isEqualTo(serverSecret);
    }

    @Test
    void testNistp256PublicValueFormat() {
        EcdhSha2Nistp256 kex = new EcdhSha2Nistp256();
        kex.init();
        byte[] pub = kex.localPublicValue();
        assertThat(pub[0]).isEqualTo((byte) 0x04); // uncompressed
        assertThat(pub.length).isEqualTo(65); // 1 + 32 + 32
    }

    @Test
    void testNistp384PublicValueFormat() {
        EcdhSha2Nistp384 kex = new EcdhSha2Nistp384();
        kex.init();
        byte[] pub = kex.localPublicValue();
        assertThat(pub[0]).isEqualTo((byte) 0x04);
        assertThat(pub.length).isEqualTo(97); // 1 + 48 + 48
    }

    @Test
    void testNistp256ExchangeHash() {
        EcdhSha2Nistp256 kex = new EcdhSha2Nistp256();
        kex.init();
        byte[] hash = kex.computeExchangeHash("SSH-2.0-c", "SSH-2.0-s",
                new byte[10], new byte[10], new byte[10],
                kex.localPublicValue(), kex.localPublicValue(), new byte[10]);
        assertThat(hash).hasSize(32);
    }

    @Test
    void testCurve25519Name() {
        assertThat(new Curve25519Sha256().name()).isEqualTo("curve25519-sha256");
    }

    @Test
    void testCurve25519HashAlgorithm() {
        assertThat(new Curve25519Sha256().hashAlgorithm()).isEqualTo("SHA-256");
    }

    @Test
    void testCurve25519KeyExchange() {
        Curve25519Sha256 client = new Curve25519Sha256();
        Curve25519Sha256 server = new Curve25519Sha256();
        client.init();
        server.init();
        byte[] clientSecret = client.computeSharedSecret(server.localPublicValue());
        byte[] serverSecret = server.computeSharedSecret(client.localPublicValue());
        assertThat(clientSecret).isEqualTo(serverSecret);
    }

    @Test
    void testCurve25519PublicValueSize() {
        Curve25519Sha256 kex = new Curve25519Sha256();
        kex.init();
        assertThat(kex.localPublicValue()).hasSize(32);
    }
}
