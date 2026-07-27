package ssg.legoflow.ssh.kex;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DiffieHellmanGroup16Test {

    @Test
    void testName() {
        DiffieHellmanGroup16 dh = new DiffieHellmanGroup16();
        assertThat(dh.name()).isEqualTo("diffie-hellman-group16-sha512");
    }

    @Test
    void testHashAlgorithm() {
        assertThat(new DiffieHellmanGroup16().hashAlgorithm()).isEqualTo("SHA-512");
    }

    @Test
    void testInit() {
        DiffieHellmanGroup16 dh = new DiffieHellmanGroup16();
        dh.init();
        byte[] pubVal = dh.localPublicValue();
        assertThat(pubVal).isNotNull();
        assertThat(pubVal.length).isGreaterThan(4);
    }

    @Test
    void testKeyExchange() {
        DiffieHellmanGroup16 client = new DiffieHellmanGroup16();
        DiffieHellmanGroup16 server = new DiffieHellmanGroup16();
        client.init();
        server.init();
        byte[] clientSecret = client.computeSharedSecret(server.localPublicValue());
        byte[] serverSecret = server.computeSharedSecret(client.localPublicValue());
        assertThat(clientSecret).isEqualTo(serverSecret);
    }

    @Test
    void testDifferentKeysEachInit() {
        DiffieHellmanGroup16 dh1 = new DiffieHellmanGroup16();
        DiffieHellmanGroup16 dh2 = new DiffieHellmanGroup16();
        dh1.init();
        dh2.init();
        assertThat(dh1.localPublicValue()).isNotEqualTo(dh2.localPublicValue());
    }

    @Test
    void testExchangeHash() {
        DiffieHellmanGroup16 dh = new DiffieHellmanGroup16();
        dh.init();
        byte[] hash = dh.computeExchangeHash(
                "SSH-2.0-client", "SSH-2.0-server",
                new byte[]{1, 2}, new byte[]{3, 4}, new byte[]{5, 6},
                dh.localPublicValue(), dh.localPublicValue(),
                new byte[]{7, 8});
        assertThat(hash).hasSize(64); // SHA-512
    }
}
