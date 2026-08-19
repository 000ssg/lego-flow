package ssg.legoflow.http3.quic;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link QuicStreamManager} — ID allocation, max streams enforcement,
 * close, and stream counting.
 *
 * @since 0.1.0
 */
class QuicStreamManagerTest {

    @Test
    void testCreateClientBidiStream() {
        var mgr = new QuicStreamManager(false, 100, 100, 65535, 65535);

        var stream = mgr.createBidiStream();

        assertThat(stream.streamId()).isEqualTo(0x00); // first client bidi
        assertThat(stream.isClientInitiated()).isTrue();
        assertThat(stream.isBidirectional()).isTrue();
        assertThat(stream.state()).isEqualTo(QuicStreamState.OPEN);
    }

    @Test
    void testCreateServerBidiStream() {
        var mgr = new QuicStreamManager(true, 100, 100, 65535, 65535);

        var stream = mgr.createBidiStream();

        assertThat(stream.streamId()).isEqualTo(0x01); // first server bidi
        assertThat(stream.isServerInitiated()).isTrue();
        assertThat(stream.isBidirectional()).isTrue();
    }

    @Test
    void testCreateClientUniStream() {
        var mgr = new QuicStreamManager(false, 100, 100, 65535, 65535);

        var stream = mgr.createUniStream();

        assertThat(stream.streamId()).isEqualTo(0x02); // first client uni
        assertThat(stream.isClientInitiated()).isTrue();
        assertThat(stream.isUnidirectional()).isTrue();
    }

    @Test
    void testCreateServerUniStream() {
        var mgr = new QuicStreamManager(true, 100, 100, 65535, 65535);

        var stream = mgr.createUniStream();

        assertThat(stream.streamId()).isEqualTo(0x03); // first server uni
        assertThat(stream.isServerInitiated()).isTrue();
        assertThat(stream.isUnidirectional()).isTrue();
    }

    @Test
    void testStreamIdIncrement() {
        var mgr = new QuicStreamManager(false, 100, 100, 65535, 65535);

        var s1 = mgr.createBidiStream();
        var s2 = mgr.createBidiStream();
        var s3 = mgr.createBidiStream();

        assertThat(s1.streamId()).isEqualTo(0); // 0x00
        assertThat(s2.streamId()).isEqualTo(4); // 0x04
        assertThat(s3.streamId()).isEqualTo(8); // 0x08
    }

    @Test
    void testMaxBidiStreamsEnforced() {
        var mgr = new QuicStreamManager(false, 2, 100, 65535, 65535);

        mgr.createBidiStream();
        mgr.createBidiStream();

        assertThatThrownBy(mgr::createBidiStream)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Max bidirectional streams");
    }

    @Test
    void testMaxUniStreamsEnforced() {
        var mgr = new QuicStreamManager(false, 100, 1, 65535, 65535);

        mgr.createUniStream();

        assertThatThrownBy(mgr::createUniStream)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Max unidirectional streams");
    }

    @Test
    void testCloseStreamAllowsNew() {
        var mgr = new QuicStreamManager(false, 1, 100, 65535, 65535);

        var stream = mgr.createBidiStream();
        mgr.closeStream(stream.streamId());

        // Closed stream should not count against limit
        var stream2 = mgr.createBidiStream();
        assertThat(stream2).isNotNull();
    }

    @Test
    void testGetOrCreateStream() {
        var mgr = new QuicStreamManager(false, 100, 100, 65535, 65535);

        var stream = mgr.getOrCreateStream(42);

        assertThat(stream).isNotNull();
        assertThat(stream.streamId()).isEqualTo(42);

        // Same stream returned on subsequent call
        var same = mgr.getOrCreateStream(42);
        assertThat(same).isSameAs(stream);
    }

    @Test
    void testGetStream() {
        var mgr = new QuicStreamManager(false, 100, 100, 65535, 65535);

        assertThat(mgr.getStream(42)).isNull();

        mgr.getOrCreateStream(42);
        assertThat(mgr.getStream(42)).isNotNull();
    }

    @Test
    void testStreamCount() {
        var mgr = new QuicStreamManager(false, 100, 100, 65535, 65535);

        assertThat(mgr.getStreamCount()).isEqualTo(0);

        mgr.createBidiStream();
        mgr.createBidiStream();

        assertThat(mgr.getStreamCount()).isEqualTo(2);
    }

    @Test
    void testActiveStreamCount() {
        var mgr = new QuicStreamManager(false, 100, 100, 65535, 65535);

        var s1 = mgr.createBidiStream();
        mgr.createBidiStream();

        assertThat(mgr.getActiveStreamCount()).isEqualTo(2);

        mgr.closeStream(s1.streamId());
        assertThat(mgr.getActiveStreamCount()).isEqualTo(1);
    }

    @Test
    void testGetActiveStreams() {
        var mgr = new QuicStreamManager(false, 100, 100, 65535, 65535);

        var s1 = mgr.createBidiStream();
        var s2 = mgr.createBidiStream();
        mgr.closeStream(s1.streamId());

        var active = mgr.getActiveStreams();
        assertThat(active).hasSize(1);
        assertThat(active.iterator().next().streamId()).isEqualTo(s2.streamId());
    }

    @Test
    void testUpdateMaxStreams() {
        var mgr = new QuicStreamManager(false, 1, 1, 65535, 65535);

        mgr.createBidiStream();
        assertThatThrownBy(mgr::createBidiStream).isInstanceOf(IllegalStateException.class);

        mgr.setMaxStreamsBidi(10);
        assertThat(mgr.maxStreamsBidi()).isEqualTo(10);
        // Now should succeed
        mgr.createBidiStream();
    }

    @Test
    void testIsServer() {
        var client = new QuicStreamManager(false, 100, 100, 65535, 65535);
        var server = new QuicStreamManager(true, 100, 100, 65535, 65535);

        assertThat(client.isServer()).isFalse();
        assertThat(server.isServer()).isTrue();
    }
}
