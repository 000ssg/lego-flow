package ssg.legoflow.network.dns.transport;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Timeout;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for DohTransport covering constructor validation and basic behavior.
 */
@Timeout(10)
class DohTransportTest {

    @Test
    void testConstructorWithValidUri() throws Exception {
        URI uri = URI.create("https://dns.google/dns-query");
        DohTransport transport = new DohTransport(uri, Duration.ofSeconds(5));
        try {
            assertThat(transport).isNotNull();
        } finally {
            transport.close();
        }
    }

    @Test
    void testConstructorWithNullUriThrows() {
        assertThatThrownBy(() -> new DohTransport(null, Duration.ofSeconds(5)))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("serverUri");
    }

    @Test
    void testConstructorWithCustomHttpClient() throws Exception {
        URI uri = URI.create("https://dns.google/dns-query");
        HttpClient customClient = HttpClient.newBuilder().build();
        DohTransport transport = new DohTransport(uri, customClient, Duration.ofSeconds(5));
        try {
            assertThat(transport).isNotNull();
        } finally {
            transport.close();
        }
    }

    @Test
    void testConstructorWithNullHttpClientThrows() {
        URI uri = URI.create("https://dns.google/dns-query");
        assertThatThrownBy(() -> new DohTransport(uri, null, Duration.ofSeconds(5)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testCloseDoesNotThrow() throws Exception {
        URI uri = URI.create("https://dns.google/dns-query");
        DohTransport transport = new DohTransport(uri, Duration.ofSeconds(1));
        assertThatCode(transport::close).doesNotThrowAnyException();
    }

    @Test
    void testDoubleCloseDoesNotThrow() throws Exception {
        URI uri = URI.create("https://dns.google/dns-query");
        DohTransport transport = new DohTransport(uri, Duration.ofSeconds(1));
        transport.close();
        transport.close(); // Idempotent
    }

    @Test
    void testAutoCloseableCompliance() throws Exception {
        URI uri = URI.create("https://dns.google/dns-query");
        try (DohTransport transport = new DohTransport(uri, Duration.ofSeconds(1))) {
            assertThat(transport).isNotNull();
        }
    }

    @Test
    void testConstructorWithCloudflareDoH() throws Exception {
        URI uri = URI.create("https://cloudflare-dns.com/dns-query");
        DohTransport transport = new DohTransport(uri, Duration.ofSeconds(3));
        try {
            assertThat(transport).isNotNull();
        } finally {
            transport.close();
        }
    }

    @Test
    void testConstructorWithQuad9DoH() throws Exception {
        URI uri = URI.create("https://dns.quad9.net/dns-query");
        DohTransport transport = new DohTransport(uri, Duration.ofSeconds(3));
        try {
            assertThat(transport).isNotNull();
        } finally {
            transport.close();
        }
    }

    @Test
    void testConstructorWithLocalhost() throws Exception {
        URI uri = URI.create("https://localhost:443/dns-query");
        DohTransport transport = new DohTransport(uri, Duration.ofSeconds(1));
        try {
            assertThat(transport).isNotNull();
        } finally {
            transport.close();
        }
    }

    @Test
    void testConstructorWithShortTimeout() throws Exception {
        URI uri = URI.create("https://dns.google/dns-query");
        DohTransport transport = new DohTransport(uri, Duration.ofMillis(100));
        try {
            assertThat(transport).isNotNull();
        } finally {
            transport.close();
        }
    }
}
