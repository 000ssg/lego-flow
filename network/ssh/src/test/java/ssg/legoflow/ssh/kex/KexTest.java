package ssg.legoflow.ssh.kex;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.util.List;

class KexTest {

    @Test void testCurve25519Sha256Name() {
        var algo = new Curve25519Sha256();
        assertThat(algo.name()).isEqualTo("curve25519-sha256");
    }

    @Test void testDhGroup14Name() {
        var algo = new DiffieHellmanGroup14();
        assertThat(algo.name()).isEqualTo("diffie-hellman-group14-sha256");
    }

    @Test void testKexResult() {
        byte[] secret = {1, 2, 3};
        var result = new KexResult(secret, new byte[]{4, 5, 6}, new byte[]{7});
        assertThat(result.sharedSecret()).isEqualTo(secret);
    }

    @Test void testKexInitConstruction() {
        byte[] cookie = new byte[16];
        java.util.Arrays.fill(cookie, (byte) 42);
        var kexList = List.of("curve25519-sha256");
        var init = new KexInit(cookie, kexList, kexList, kexList, kexList,
                kexList, kexList, kexList, kexList, kexList, kexList, false);
        assertThat(init.cookie()).hasSize(16);
    }
}
