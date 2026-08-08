package ssg.legoflow.ssh.kex;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class KexExtendedTest {

    @Test void curve25519Sha256ImplementsKexAlgorithm() throws Exception {
        var kex = new Curve25519Sha256();
        assertThat(kex).isInstanceOf(KexAlgorithm.class);
    }

    @Test void diffieHellmanGroup14ImplementsKexAlgorithm() throws Exception {
        var kex = new DiffieHellmanGroup14();
        assertThat(kex).isInstanceOf(KexAlgorithm.class);
    }

    @Test void ecdhSha2Nistp256ImplementsKexAlgorithm() throws Exception {
        var kex = new EcdhSha2Nistp256();
        assertThat(kex).isInstanceOf(KexAlgorithm.class);
    }
}
