package ssg.legoflow.network.dns.resolver;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Timeout;
import java.net.InetSocketAddress;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for StubResolver covering constructor validation and cache access.
 */
@Timeout(10)
class StubResolverTest {

    @Test
    void testDefaultConstructor() {
        InetSocketAddress upstream = new InetSocketAddress("8.8.8.8", 53);
        StubResolver resolver = new StubResolver(upstream, Duration.ofSeconds(5));
        try {
            assertThat(resolver.cache()).isNotNull();
        } finally {
            resolver.close();
        }
    }

    @Test
    void testNullUpstreamThrows() {
        assertThatThrownBy(() -> new StubResolver(null, Duration.ofSeconds(5)))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNullTimeoutThrows() {
        InetSocketAddress upstream = new InetSocketAddress("8.8.8.8", 53);
        assertThatThrownBy(() -> new StubResolver(upstream, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNullCacheThrows() {
        InetSocketAddress upstream = new InetSocketAddress("8.8.8.8", 53);
        Duration timeout = Duration.ofSeconds(5);
        assertThatThrownBy(() -> new StubResolver(upstream, timeout, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testCacheAccessReturnsNonNull() {
        InetSocketAddress upstream = new InetSocketAddress("8.8.8.8", 53);
        StubResolver resolver = new StubResolver(upstream, Duration.ofSeconds(5));
        try {
            DnsCache cache = resolver.cache();
            assertThat(cache).isNotNull();
        } finally {
            resolver.close();
        }
    }

    @Test
    void testCloseDoesNotThrow() throws Exception {
        InetSocketAddress upstream = new InetSocketAddress("8.8.8.8", 53);
        StubResolver resolver = new StubResolver(upstream, Duration.ofSeconds(5));
        assertThatCode(resolver::close).doesNotThrowAnyException();
    }

    @Test
    void testDoubleCloseDoesNotThrow() throws Exception {
        InetSocketAddress upstream = new InetSocketAddress("8.8.8.8", 53);
        StubResolver resolver = new StubResolver(upstream, Duration.ofSeconds(5));
        resolver.close();
        resolver.close();
    }

    @Test
    void testLocalhostConstructor() {
        StubResolver resolver = new StubResolver(new InetSocketAddress("localhost", 53), 
            Duration.ofSeconds(1));
        try {
            assertThat(resolver.cache()).isNotNull();
        } finally {
            resolver.close();
        }
    }

    @Test
    void testGoogleDnsConstructor() {
        StubResolver resolver = new StubResolver(new InetSocketAddress("8.8.4.4", 53), 
            Duration.ofSeconds(2));
        try {
            assertThat(resolver.cache()).isNotNull();
        } finally {
            resolver.close();
        }
    }
}
