package ssg.legoflow.media.rtsp.interleaved;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link InterleavedTransport}.
 */
class InterleavedTransportTest {

    @Test
    void testRegisterAndDispatch() {
        var transport = new InterleavedTransport();
        List<byte[]> received = new ArrayList<>();
        transport.registerChannel(0, received::add);

        byte[] data = {0x01, 0x02, 0x03};
        transport.dispatch(new InterleavedFrame(0, data));

        assertThat(received).hasSize(1);
        assertThat(received.getFirst()).isEqualTo(data);
    }

    @Test
    void testDispatchUnregisteredChannel() {
        var transport = new InterleavedTransport();
        // Should not throw, just log warning
        assertThatCode(() -> transport.dispatch(new InterleavedFrame(5, new byte[]{0x01})))
                .doesNotThrowAnyException();
    }

    @Test
    void testMultipleChannels() {
        var transport = new InterleavedTransport();
        List<byte[]> rtpData = new ArrayList<>();
        List<byte[]> rtcpData = new ArrayList<>();
        transport.registerChannel(0, rtpData::add);
        transport.registerChannel(1, rtcpData::add);

        transport.dispatch(new InterleavedFrame(0, new byte[]{0x01}));
        transport.dispatch(new InterleavedFrame(1, new byte[]{0x02}));
        transport.dispatch(new InterleavedFrame(0, new byte[]{0x03}));

        assertThat(rtpData).hasSize(2);
        assertThat(rtcpData).hasSize(1);
    }

    @Test
    void testUnregisterChannel() {
        var transport = new InterleavedTransport();
        List<byte[]> received = new ArrayList<>();
        transport.registerChannel(0, received::add);
        transport.unregisterChannel(0);

        transport.dispatch(new InterleavedFrame(0, new byte[]{0x01}));
        assertThat(received).isEmpty();
    }

    @Test
    void testSendFrame() throws IOException {
        var transport = new InterleavedTransport();
        var out = new ByteArrayOutputStream();
        byte[] data = {0x01, 0x02};
        transport.send(out, new InterleavedFrame(0, data));

        byte[] written = out.toByteArray();
        assertThat(written[0]).isEqualTo((byte) '$');
        assertThat(written[1]).isEqualTo((byte) 0);
        assertThat(written.length).isEqualTo(6); // 4 header + 2 data
    }

    @Test
    void testSendByChannel() throws IOException {
        var transport = new InterleavedTransport();
        var out = new ByteArrayOutputStream();
        transport.send(out, 2, new byte[]{0x10, 0x20, 0x30});

        byte[] written = out.toByteArray();
        assertThat(written[0]).isEqualTo((byte) '$');
        assertThat(written[1]).isEqualTo((byte) 2);
    }

    @Test
    void testSendAfterCloseThrows() {
        var transport = new InterleavedTransport();
        transport.close();
        var out = new ByteArrayOutputStream();
        assertThatThrownBy(() -> transport.send(out, new InterleavedFrame(0, new byte[]{0x01})))
                .isInstanceOf(IOException.class);
    }

    @Test
    void testChannelCount() {
        var transport = new InterleavedTransport();
        assertThat(transport.channelCount()).isEqualTo(0);
        transport.registerChannel(0, data -> {});
        transport.registerChannel(1, data -> {});
        assertThat(transport.channelCount()).isEqualTo(2);
    }

    @Test
    void testCloseClears() {
        var transport = new InterleavedTransport();
        transport.registerChannel(0, data -> {});
        transport.close();
        assertThat(transport.channelCount()).isEqualTo(0);
    }

    @Test
    void testToString() {
        var transport = new InterleavedTransport();
        transport.registerChannel(0, data -> {});
        assertThat(transport.toString()).contains("InterleavedTransport");
    }
}
