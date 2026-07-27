package ssg.legoflow.ssh.kex;

import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import static org.assertj.core.api.Assertions.*;

class DiffieHellmanGroup14Test {

    @Test
    void testName() {
        DiffieHellmanGroup14 dh = new DiffieHellmanGroup14();
        assertThat(dh.name()).isEqualTo("diffie-hellman-group14-sha256");
    }

    @Test
    void testHashAlgorithm() {
        assertThat(new DiffieHellmanGroup14().hashAlgorithm()).isEqualTo("SHA-256");
    }

    @Test
    void testInit() {
        DiffieHellmanGroup14 dh = new DiffieHellmanGroup14();
        dh.init();
        byte[] pubVal = dh.localPublicValue();
        assertThat(pubVal).isNotNull();
        assertThat(pubVal.length).isGreaterThan(4);
    }

    @Test
    void testKeyExchange() {
        DiffieHellmanGroup14 client = new DiffieHellmanGroup14();
        DiffieHellmanGroup14 server = new DiffieHellmanGroup14();
        client.init();
        server.init();
        byte[] clientSecret = client.computeSharedSecret(server.localPublicValue());
        byte[] serverSecret = server.computeSharedSecret(client.localPublicValue());
        assertThat(clientSecret).isEqualTo(serverSecret);
    }

    @Test
    void testInvalidPublicValueZero() {
        DiffieHellmanGroup14 dh = new DiffieHellmanGroup14();
        dh.init();
        byte[] zero = DiffieHellmanGroup14.toMpint(BigInteger.ZERO);
        assertThatThrownBy(() -> dh.computeSharedSecret(zero))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testInvalidPublicValueOne() {
        DiffieHellmanGroup14 dh = new DiffieHellmanGroup14();
        dh.init();
        byte[] one = DiffieHellmanGroup14.toMpint(BigInteger.ONE);
        assertThatThrownBy(() -> dh.computeSharedSecret(one))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testExchangeHashComputation() {
        DiffieHellmanGroup14 dh = new DiffieHellmanGroup14();
        dh.init();
        byte[] hash = dh.computeExchangeHash(
                "SSH-2.0-client", "SSH-2.0-server",
                new byte[]{1, 2, 3}, new byte[]{4, 5, 6},
                new byte[]{7, 8, 9},
                dh.localPublicValue(), dh.localPublicValue(),
                new byte[]{10, 11, 12});
        assertThat(hash).hasSize(32); // SHA-256
    }

    @Test
    void testMpintRoundTrip() {
        BigInteger value = new BigInteger("123456789012345678901234567890");
        byte[] mpint = DiffieHellmanGroup14.toMpint(value);
        BigInteger decoded = DiffieHellmanGroup14.fromMpint(mpint);
        assertThat(decoded).isEqualTo(value);
    }

    @Test
    void testGroupPrimeIsPrime() {
        assertThat(DiffieHellmanGroup14.P.isProbablePrime(20)).isTrue();
    }

    @Test
    void testGeneratorIsTwo() {
        assertThat(DiffieHellmanGroup14.G).isEqualTo(BigInteger.TWO);
    }

    @Test
    void testDifferentKeysEachInit() {
        DiffieHellmanGroup14 dh1 = new DiffieHellmanGroup14();
        DiffieHellmanGroup14 dh2 = new DiffieHellmanGroup14();
        dh1.init();
        dh2.init();
        assertThat(dh1.localPublicValue()).isNotEqualTo(dh2.localPublicValue());
    }
}
