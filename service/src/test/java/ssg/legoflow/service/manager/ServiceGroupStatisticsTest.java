package ssg.legoflow.service.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Unit tests for {@link ServiceGroupStatistics}.
 *
 * @since 0.1.0
 */
class ServiceGroupStatisticsTest {

    private ServiceGroupStatistics stats;

    @BeforeEach
    void setUp() {
        stats = new ServiceGroupStatistics(3); // 1 connector + 2 data
    }

    @Test
    void testConstructorValidation() {
        assertThatThrownBy(() -> new ServiceGroupStatistics(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ServiceGroupStatistics(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSelectorCount() {
        assertThat(stats.getSelectorCount()).isEqualTo(3);
    }

    @Test
    void testConnectionCounter() {
        assertThat(stats.getConnections()).isZero();
        stats.addConnection();
        stats.addConnection();
        assertThat(stats.getConnections()).isEqualTo(2);
    }

    @Test
    void testTcpReadTracking() {
        stats.setSelectorIndex(1);
        stats.addTcpRead(100, 5000);
        stats.addTcpRead(200, 3000);

        assertThat(stats.getTcpBytes()[0]).isEqualTo(300);
        assertThat(stats.getTcpBytes()[1]).isZero();

        var selectorReads = stats.getSelectorReadBytes();
        assertThat(selectorReads[1]).isEqualTo(300);
        assertThat(selectorReads[0]).isZero();
    }

    @Test
    void testTcpWriteTracking() {
        stats.setSelectorIndex(2);
        stats.addTcpWrite(150, 2000);

        assertThat(stats.getTcpBytes()[1]).isEqualTo(150);
        assertThat(stats.getTcpBytes()[0]).isZero();

        var selectorWrites = stats.getSelectorWriteBytes();
        assertThat(selectorWrites[2]).isEqualTo(150);
    }

    @Test
    void testUdpReadTracking() {
        stats.setSelectorIndex(1);
        stats.addUdpRead(50, 1000);
        stats.addUdpRead(75, 2000);

        assertThat(stats.getUdpPackets()[0]).isEqualTo(2);
        assertThat(stats.getUdpBytes()[0]).isEqualTo(125);
        assertThat(stats.getSelectorReadBytes()[1]).isEqualTo(125);
    }

    @Test
    void testUdpWriteTracking() {
        stats.setSelectorIndex(2);
        stats.addUdpWrite(200, 4000);

        assertThat(stats.getUdpPackets()[1]).isEqualTo(1);
        assertThat(stats.getUdpBytes()[1]).isEqualTo(200);
        assertThat(stats.getSelectorWriteBytes()[2]).isEqualTo(200);
    }

    @Test
    void testKeyProcessedTracking() {
        stats.setSelectorIndex(0);
        stats.addKeyProcessed(ServiceGroupStatistics.ACCEPT, 1000);
        stats.addKeyProcessed(ServiceGroupStatistics.ACCEPT, 2000);

        stats.setSelectorIndex(1);
        stats.addKeyProcessed(ServiceGroupStatistics.READ, 500);
        stats.addKeyProcessed(ServiceGroupStatistics.WRITE, 300);

        var counts = stats.getKeyCounts();
        assertThat(counts[ServiceGroupStatistics.ACCEPT]).isEqualTo(2);
        assertThat(counts[ServiceGroupStatistics.READ]).isEqualTo(1);
        assertThat(counts[ServiceGroupStatistics.WRITE]).isEqualTo(1);
        assertThat(counts[ServiceGroupStatistics.CONNECT]).isZero();

        var durations = stats.getKeyDurations();
        assertThat(durations[ServiceGroupStatistics.ACCEPT]).isEqualTo(3000);

        var selectorKeys = stats.getSelectorKeyCounts();
        assertThat(selectorKeys[0]).isEqualTo(2);
        assertThat(selectorKeys[1]).isEqualTo(2);
    }

    @Test
    void testSelectorDuration() {
        stats.addSelectorDuration(0, 10000);
        stats.addSelectorDuration(1, 20000);
        stats.addSelectorDuration(2, 30000);

        var durations = stats.getSelectorDurations();
        assertThat(durations[0]).isEqualTo(10000);
        assertThat(durations[1]).isEqualTo(20000);
        assertThat(durations[2]).isEqualTo(30000);
    }

    @Test
    void testSnapshot() {
        stats.setSelectorIndex(1);
        stats.addConnection();
        stats.addUdpRead(100, 5000);
        stats.addUdpWrite(200, 3000);
        stats.addKeyProcessed(ServiceGroupStatistics.READ, 1000);

        var snap = stats.snapshot();
        assertThat(snap.connections()).isEqualTo(1);
        assertThat(snap.udpBytes()[0]).isEqualTo(100);
        assertThat(snap.udpBytes()[1]).isEqualTo(200);
        assertThat(snap.udpPackets()[0]).isEqualTo(1);
        assertThat(snap.udpPackets()[1]).isEqualTo(1);
        assertThat(snap.selectorReadBytes()[1]).isEqualTo(100);
        assertThat(snap.selectorWriteBytes()[1]).isEqualTo(200);
        assertThat(snap.keyCounts()[ServiceGroupStatistics.READ]).isEqualTo(1);
    }

    @Test
    void testReset() {
        stats.setSelectorIndex(1);
        stats.addConnection();
        stats.addTcpRead(100, 5000);
        stats.addUdpWrite(200, 3000);
        stats.addKeyProcessed(ServiceGroupStatistics.READ, 1000);
        stats.addSelectorDuration(1, 10000);

        stats.reset();

        assertThat(stats.getConnections()).isZero();
        assertThat(stats.getTcpBytes()[0]).isZero();
        assertThat(stats.getUdpBytes()[1]).isZero();
        assertThat(stats.getKeyCounts()[ServiceGroupStatistics.READ]).isZero();
        assertThat(stats.getSelectorDurations()[1]).isZero();
        assertThat(stats.getSelectorReadBytes()[1]).isZero();
    }

    @Test
    void testToStringContainsAllSections() {
        stats.setSelectorIndex(1);
        stats.addConnection();
        stats.addUdpRead(1024, 1000000);

        String text = stats.toString();
        assertThat(text).contains("ServiceGroupStatistics{");
        assertThat(text).contains("connections=1");
        assertThat(text).contains("udpBytes");
        assertThat(text).contains("selector[0/connector]");
        assertThat(text).contains("selector[1/data]");
        assertThat(text).contains("selector[2/data]");
    }

    @Test
    void testFormatRate() {
        // 1024 bytes in 1 second = 1.00 KB/s
        String rate = ServiceGroupStatistics.formatRate(1024, 1_000_000_000L);
        assertThat(rate).contains("KB/s");
    }

    @Test
    void testSelectorIndexThreadLocal() {
        // Default is null
        var freshStats = new ServiceGroupStatistics(2);
        assertThat(freshStats.getSelectorIndex()).isNull();

        freshStats.setSelectorIndex(1);
        assertThat(freshStats.getSelectorIndex()).isEqualTo(1);
    }
}
