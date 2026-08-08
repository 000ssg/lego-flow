package ssg.legoflow.ssh.compression;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CompressionExtendedTest {

    @Test void zlibCompression() throws Exception {
        var comp = new ZlibCompression();
        assertThat(comp).isNotNull();
    }

    @Test void noneCompression() throws Exception {
        var comp = new NoneCompression();
        assertThat(comp).isNotNull();
    }
}
