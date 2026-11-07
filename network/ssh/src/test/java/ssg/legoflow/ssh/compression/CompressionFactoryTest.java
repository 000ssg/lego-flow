package ssg.legoflow.ssh.compression;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CompressionFactory}.
 */
class CompressionFactoryTest {

    @Test
    void createsNoneCompression() {
        SshCompression compression = CompressionFactory.create("none");
        assertNotNull(compression);
        assertTrue(compression instanceof NoneCompression);
    }

    @Test
    void createsZlibCompression() {
        SshCompression compression = CompressionFactory.create("zlib");
        assertNotNull(compression);
        assertTrue(compression instanceof ZlibCompression);
    }

    @Test
    void createsZlibOpenSshCompression() {
        SshCompression compression = CompressionFactory.create("zlib@openssh.com");
        assertNotNull(compression);
        assertTrue(compression instanceof ZlibOpenSshCompression);
    }

    @Test
    void throwsForUnsupportedAlgorithm() {
        assertThrows(IllegalArgumentException.class, () -> {
            CompressionFactory.create("unknown");
        });
    }

    @Test
    void isSupportedReturnsTrueForSupportedAlgorithms() {
        assertTrue(CompressionFactory.isSupported("none"));
        assertTrue(CompressionFactory.isSupported("zlib"));
        assertTrue(CompressionFactory.isSupported("zlib@openssh.com"));
    }

    @Test
    void isSupportedReturnsFalseForUnsupportedAlgorithms() {
        assertFalse(CompressionFactory.isSupported("unknown"));
        assertFalse(CompressionFactory.isSupported(""));
    }

    @Test
    void supportedAlgorithmsAreConsistent() {
        assertEquals(3, CompressionFactory.supportedAlgorithms().size());
        assertTrue(CompressionFactory.supportedAlgorithms().contains("none"));
        assertTrue(CompressionFactory.supportedAlgorithms().contains("zlib"));
        assertTrue(CompressionFactory.supportedAlgorithms().contains("zlib@openssh.com"));
    }
}
