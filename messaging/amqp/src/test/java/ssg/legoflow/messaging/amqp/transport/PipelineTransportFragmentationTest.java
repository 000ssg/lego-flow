package ssg.legoflow.messaging.amqp.transport;

import org.junit.jupiter.api.Test;
import ssg.legoflow.service.channel.DataChannel;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests PipelineTransport ring buffer correctness under network fragmentation.
 *
 * <p>Per user mandate: the ring buffer is a dumb byte queue — NOT frame-aware.
 * The consumer reads raw bytes via receive()/fetch() and drives the protocol
 * state machine. This verifies that bytes flow through the buffer correctly
 * regardless of how the network splits them.
 *
 * <p>Test scenarios are based on actual AMQP 1.0 wire captures from
 * Apache ActiveMQ Artemis (amqp10-reference-capture-artemis-*.txt).
 */
class PipelineTransportFragmentationTest {

    private static byte[] hex(String h) {
        return java.util.HexFormat.of().parseHex(h);
    }

    private DataChannel mockChannel() {
        return new DataChannel() {
            @Override public SelectionKey getSelectionKey() { return null; }
            @Override public int read(ByteBuffer buf) throws IOException { return 0; }
            @Override public int write(ByteBuffer buf) throws IOException { return buf.remaining(); }
            @Override public void close() throws IOException {}
            @Override public boolean isOpen() { return true; }
        };
    }

    // ── Reference capture data ──

    private static final byte[] AMQP_HEADER_PROTO0 = hex("414d515000010000");
    private static final byte[] SASL_HEADER = hex("414d515003010000");
    private static final byte[] SASL_MECHANISMS = hex(
        "0000002202010000005340c01501e01202a305504c41494e09414e4f4e594d4f5553");
    private static final byte[] SASL_INIT = hex(
        "0000002e02010000005341c02103a305504c41494ea00c006775657374006775657374a1096c6f63616c686f7374");
    private static final byte[] SASL_OUTCOME = hex("0000001002010000005344c003015000");
    private static final byte[] CLIENT_OPEN = hex(
        "0000012502000000005310d0000001150000000aa12949443a62636433326436652d373539652d343539612d383561662d3538616433376139623663323a32a1096c6f63616c686f73747000100000607fff7000007530404040e04d04a31d736f6c652d636f6e6e656374696f6e2d666f722d636f6e7461696e65721044454c415945445f44454c49564552590f414e4f4e594d4f55532d52454c41590b5348415245442d53554253c17a06a30770726f64756374a107517069644a4d53a30776657273696f6ea106312e31362e30a308706c6174666f726da14a4a564d3a2032352e302e332c2032352e302e332b392d4c54532c2045636c697073652041646f707469756d2c204f533a204d6163204f5320582c2032362e362e322c2061617263683634");
    private static final byte[] SERVER_OPEN = hex(
        "000000ca02000000005310c0bd0aa12438316262626164342d613233622d313166312d626366302d30616464643764623335656240700002000060ffff70000075304040e04d04a31d736f6c652d636f6e6e656374696f6e2d666f722d636f6e7461696e65721044454c415945445f44454c49564552590b5348415245442d535542530f414e4f4e594d4f55532d52454c415940c13404a30770726f64756374a1176170616368652d6163746976656d712d617274656d6973a30776657273696f6ea106322e35352e30");
    private static final byte[] BEGIN = hex("0000000c0200000000531845");

    // ── Tests ──

    @Test
    void testSinglePacketCompleteRead() {
        var transport = new PipelineTransport(mockChannel());
        transport.onRead(transport.getChannel(), ByteBuffer.wrap(BEGIN));

        ByteBuffer dst = ByteBuffer.allocate(12);
        int read = transport.receiveWithTimeout(dst, 1, TimeUnit.SECONDS);
        assertEquals(12, read);
        assertEquals(0, transport.peek());
        assertArrayEquals(BEGIN, dst.array());
    }

    @Test
    void testTwoPacketFragmentation() {
        var transport = new PipelineTransport(mockChannel());
        byte[] part1 = java.util.Arrays.copyOfRange(SERVER_OPEN, 0, 100);
        byte[] part2 = java.util.Arrays.copyOfRange(SERVER_OPEN, 100, SERVER_OPEN.length);

        transport.onRead(transport.getChannel(), ByteBuffer.wrap(part1));
        assertEquals(100, transport.peek());

        transport.onRead(transport.getChannel(), ByteBuffer.wrap(part2));
        assertEquals(202, transport.peek());

        ByteBuffer dst = ByteBuffer.allocate(202);
        int read = transport.receiveWithTimeout(dst, 1, TimeUnit.SECONDS);
        assertEquals(202, read);
        assertArrayEquals(SERVER_OPEN, dst.array());
    }

    @Test
    void testMultipleFramesSinglePacket() {
        var transport = new PipelineTransport(mockChannel());
        byte[] combined = new byte[BEGIN.length + AMQP_HEADER_PROTO0.length];
        System.arraycopy(BEGIN, 0, combined, 0, BEGIN.length);
        System.arraycopy(AMQP_HEADER_PROTO0, 0, combined, BEGIN.length, AMQP_HEADER_PROTO0.length);

        transport.onRead(transport.getChannel(), ByteBuffer.wrap(combined));
        assertEquals(BEGIN.length + AMQP_HEADER_PROTO0.length, transport.peek());

        ByteBuffer dst1 = ByteBuffer.allocate(12);
        int read1 = transport.receiveWithTimeout(dst1, 1, TimeUnit.SECONDS);
        assertEquals(12, read1);
        assertArrayEquals(BEGIN, dst1.array());

        ByteBuffer dst2 = ByteBuffer.allocate(8);
        int read2 = transport.receiveWithTimeout(dst2, 1, TimeUnit.SECONDS);
        assertEquals(8, read2);
        assertArrayEquals(AMQP_HEADER_PROTO0, dst2.array());
    }

    @Test
    void testMicroFragmentation() {
        var transport = new PipelineTransport(mockChannel());
        byte[] frame = CLIENT_OPEN;
        int chunk = 42;

        for (int i = 0; i < frame.length; i += chunk) {
            int len = Math.min(chunk, frame.length - i);
            byte[] part = java.util.Arrays.copyOfRange(frame, i, i + len);
            transport.onRead(transport.getChannel(), ByteBuffer.wrap(part));
        }

        assertEquals(frame.length, transport.peek());

        ByteBuffer dst = ByteBuffer.allocate(frame.length);
        int read = transport.receiveWithTimeout(dst, 1, TimeUnit.SECONDS);
        assertEquals(frame.length, read);
        assertArrayEquals(frame, dst.array());
    }

    @Test
    void testInterleavedTraffic() {
        var transport = new PipelineTransport(mockChannel());

        transport.onRead(transport.getChannel(), ByteBuffer.wrap(SASL_MECHANISMS));
        transport.onRead(transport.getChannel(), ByteBuffer.wrap(SASL_OUTCOME));
        transport.onRead(transport.getChannel(), ByteBuffer.wrap(AMQP_HEADER_PROTO0));
        transport.onRead(transport.getChannel(), ByteBuffer.wrap(SERVER_OPEN));

        assertEquals(260, transport.peek());

        ByteBuffer dst1 = ByteBuffer.allocate(34);
        assertEquals(34, transport.receiveWithTimeout(dst1, 1, TimeUnit.SECONDS));
        assertArrayEquals(SASL_MECHANISMS, dst1.array());

        ByteBuffer dst2 = ByteBuffer.allocate(16);
        assertEquals(16, transport.receiveWithTimeout(dst2, 1, TimeUnit.SECONDS));
        assertArrayEquals(SASL_OUTCOME, dst2.array());

        ByteBuffer dst3 = ByteBuffer.allocate(8);
        assertEquals(8, transport.receiveWithTimeout(dst3, 1, TimeUnit.SECONDS));
        assertArrayEquals(AMQP_HEADER_PROTO0, dst3.array());

        ByteBuffer dst4 = ByteBuffer.allocate(202);
        assertEquals(202, transport.receiveWithTimeout(dst4, 1, TimeUnit.SECONDS));
        assertArrayEquals(SERVER_OPEN, dst4.array());

        assertEquals(0, transport.peek());
    }

    @Test
    void testReadLessThanAvailable() {
        var transport = new PipelineTransport(mockChannel());
        transport.onRead(transport.getChannel(), ByteBuffer.wrap(SERVER_OPEN));

        ByteBuffer sizeBuf = ByteBuffer.allocate(4);
        int read = transport.receiveWithTimeout(sizeBuf, 1, TimeUnit.SECONDS);
        assertEquals(4, read);
        assertEquals(198, transport.peek());

        ByteBuffer rest = ByteBuffer.allocate(198);
        read = transport.receiveWithTimeout(rest, 1, TimeUnit.SECONDS);
        assertEquals(198, read);
        assertEquals(0, transport.peek());

        ByteBuffer full = ByteBuffer.allocate(202);
        full.put(sizeBuf.array());
        full.put(rest.array());
        assertArrayEquals(SERVER_OPEN, full.array());
    }

    @Test
    void testReadMoreThanAvailableWaits() throws InterruptedException {
        var transport = new PipelineTransport(mockChannel());
        byte[] part1 = java.util.Arrays.copyOfRange(SERVER_OPEN, 0, 100);
        transport.onRead(transport.getChannel(), ByteBuffer.wrap(part1));

        Thread writer = new Thread(() -> {
            try {
                Thread.sleep(100);
                byte[] part2 = java.util.Arrays.copyOfRange(SERVER_OPEN, 100, SERVER_OPEN.length);
                transport.onRead(transport.getChannel(), ByteBuffer.wrap(part2));
            } catch (InterruptedException ignored) {}
        });
        writer.start();

        ByteBuffer dst = ByteBuffer.allocate(202);
        int read = transport.receiveWithTimeout(dst, 2, TimeUnit.SECONDS);
        writer.join(1000);

        assertEquals(202, read);
        assertArrayEquals(SERVER_OPEN, dst.array());
    }

    @Test
    void testRingBufferWrapAround() {
        var transport = new PipelineTransport(mockChannel());

        byte[] big = new byte[65436];
        java.util.Arrays.fill(big, (byte) 0xAA);
        transport.onRead(transport.getChannel(), ByteBuffer.wrap(big));
        assertEquals(65436, transport.peek());

        ByteBuffer read1 = ByteBuffer.allocate(65400);
        int r1 = transport.receiveWithTimeout(read1, 1, TimeUnit.SECONDS);
        assertEquals(65400, r1);
        assertEquals(36, transport.peek());

        byte[] wrap = new byte[100];
        java.util.Arrays.fill(wrap, (byte) 0xBB);
        transport.onRead(transport.getChannel(), ByteBuffer.wrap(wrap));
        assertEquals(136, transport.peek());

        ByteBuffer dst = ByteBuffer.allocate(136);
        int read = transport.receiveWithTimeout(dst, 1, TimeUnit.SECONDS);
        assertEquals(136, read);

        byte[] result = dst.array();
        for (int i = 0; i < 36; i++) {
            assertEquals(0xAA, result[i] & 0xFF, "Byte " + i + " should be 0xAA");
        }
        for (int i = 36; i < 136; i++) {
            assertEquals(0xBB, result[i] & 0xFF, "Byte " + i + " should be 0xBB");
        }
    }

    @Test
    void testNoDataLossUnderFragmentation() {
        var transport = new PipelineTransport(mockChannel());

        byte[][] packets = {SASL_HEADER, SASL_MECHANISMS, SASL_INIT, SASL_OUTCOME,
                             AMQP_HEADER_PROTO0, CLIENT_OPEN, SERVER_OPEN, BEGIN};
        byte[] expected = new byte[0];
        for (byte[] pkt : packets) {
            expected = java.util.Arrays.copyOf(expected, expected.length + pkt.length);
            System.arraycopy(pkt, 0, expected, expected.length - pkt.length, pkt.length);
        }

        for (byte[] pkt : packets) {
            int pos = 0;
            while (pos < pkt.length) {
                int len = Math.min(pkt.length - pos, (int)(Math.random() * 32) + 1);
                byte[] frag = java.util.Arrays.copyOfRange(pkt, pos, pos + len);
                transport.onRead(transport.getChannel(), ByteBuffer.wrap(frag));
                pos += len;
            }
        }

        assertEquals(expected.length, transport.peek());

        ByteBuffer dst = ByteBuffer.allocate(expected.length);
        int read = transport.receiveWithTimeout(dst, 1, TimeUnit.SECONDS);
        assertEquals(expected.length, read);
        assertArrayEquals(expected, dst.array());
    }
}
