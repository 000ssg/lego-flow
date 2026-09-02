package ssg.legoflow.network.cluster.dns;

import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
class DnsSdConflictResolutionTest {

    private static final InetAddress LOCAL_ADDR;

    static {
        try {
            LOCAL_ADDR = InetAddress.getByName("127.0.0.1");
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void constructor_withDefaults() {
        MdnsConflictResolver resolver = new MdnsConflictResolver();
        // Should not throw
    }

    @Test
    void constructor_withCustomValues() {
        MdnsConflictResolver resolver = new MdnsConflictResolver(5, Duration.ofMillis(100), null);
        // Should not throw
    }

    @Test
    void zeroProbeCount_throws() {
        assertThatThrownBy(() -> new MdnsConflictResolver(0, Duration.ofMillis(100), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeProbeCount_throws() {
        assertThatThrownBy(() -> new MdnsConflictResolver(-1, Duration.ofMillis(100), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroProbeInterval_throws() {
        assertThatThrownBy(() -> new MdnsConflictResolver(3, Duration.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generateUniqueName_appendsSuffix() {
        MdnsConflictResolver resolver = new MdnsConflictResolver();

        String name1 = resolver.generateUniqueName("MyServer");
        String name2 = resolver.generateUniqueName("MyServer");

        assertThat(name1).startsWith("MyServer-");
        assertThat(name2).startsWith("MyServer-");

        // The suffixes should be random hex (4 chars)
        String suffix1 = name1.substring("MyServer-".length());
        String suffix2 = name2.substring("MyServer-".length());
        assertThat(suffix1).hasSize(4);
        assertThat(suffix2).hasSize(4);
    }

    @Test
    void generateUniqueName_nullBase_throws() {
        MdnsConflictResolver resolver = new MdnsConflictResolver();
        assertThatThrownBy(() -> resolver.generateUniqueName(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void probe_returnsFuture() {
        MdnsConflictResolver resolver = new MdnsConflictResolver(1, Duration.ofMillis(100), null);
        var future = resolver.probe("test._http._tcp.local.", LOCAL_ADDR);
        assertThat(future).isNotNull();
    }

    @Test
    void probe_nullArgs_throw() {
        MdnsConflictResolver resolver = new MdnsConflictResolver();
        assertThatThrownBy(() -> resolver.probe(null, LOCAL_ADDR))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> resolver.probe("test", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void resolveConflict_noConflict_returnsOriginalConfig() {
        MdnsConflictResolver resolver = new MdnsConflictResolver(1, Duration.ofMillis(100), null);

        DnsSdConfig config = DnsSdConfig.builder()
                .serviceType("_http._tcp")
                .instanceName("UniqueName12345")
                .port(8080)
                .build();

        var future = resolver.resolveConflict(config, LOCAL_ADDR);
        assertThatNoException().isThrownBy(() -> future.join());
    }

    @Test
    void resolveConflict_nullConfig_throws() {
        MdnsConflictResolver resolver = new MdnsConflictResolver();
        assertThatThrownBy(() -> resolver.resolveConflict(null, LOCAL_ADDR))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void resolveConflict_nullAddress_throws() {
        MdnsConflictResolver resolver = new MdnsConflictResolver();
        DnsSdConfig config = DnsSdConfig.defaultsFor("_http._tcp", "test", 80);
        assertThatThrownBy(() -> resolver.resolveConflict(config, null))
                .isInstanceOf(NullPointerException.class);
    }
}
