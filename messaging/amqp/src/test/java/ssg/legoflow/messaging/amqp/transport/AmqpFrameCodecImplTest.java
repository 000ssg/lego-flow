package ssg.legoflow.messaging.amqp.transport;

import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.messaging.amqp.transport.AmqpFrameCodecImpl.FrameExtractor;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

class AmqpFrameCodecImplTest {

    private final List<ByteBuffer> extracted = new ArrayList<>();
    private final FrameExtractor extractor = (ch, buf) -> {
        byte[] data = new byte[buf.remaining()];
        buf.get(data);
        extracted.add(ByteBuffer.wrap(data));
    };

    @Test
    void singleFrameInOneRead() {
        AmqpFrameCodecImpl codec = new AmqpFrameCodecImpl(extractor);
        // Build a 20-byte frame: size(4) + doff(1) + type(1) + channel(2) + body(12)
        ByteBuffer frame = ByteBuffer.allocate(20);
        frame.putInt(20); // size
        frame.put((byte) 2); // doff
        frame.put((byte) 0); // type
        frame.putShort((short) 0); // channel
        frame.put("hello world!".getBytes()); // body
        frame.flip();
        codec.onRead(null, frame);
        assertEquals(1, extracted.size());
        assertEquals(20, extracted.get(0).remaining());
    }

    @Test
    void partialFrameAccumulation() {
        AmqpFrameCodecImpl codec = new AmqpFrameCodecImpl(extractor);
        // First read: only 4 bytes (size header)
        ByteBuffer partial = ByteBuffer.allocate(4);
        partial.putInt(20);
        partial.flip();
        codec.onRead(null, partial);
        assertTrue(extracted.isEmpty()); // partial — no frame yet

        // Second read: remaining 16 bytes
        ByteBuffer rest = ByteBuffer.allocate(16);
        rest.put((byte) 2); rest.put((byte) 0); rest.putShort((short) 0);
        rest.put("hello world!".getBytes());
        rest.flip();
        codec.onRead(null, rest);
        assertEquals(1, extracted.size());
    }

    @Test
    void multiFrameInOneRead() {
        AmqpFrameCodecImpl codec = new AmqpFrameCodecImpl(extractor);
        // Two 20-byte frames back to back
        ByteBuffer two = ByteBuffer.allocate(40);
        // Frame 1
        two.putInt(20); two.put((byte) 2); two.put((byte) 0);
        two.putShort((short) 0); two.put("frame one   ".getBytes());
        // Frame 2
        two.putInt(20); two.put((byte) 2); two.put((byte) 0);
        two.putShort((short) 1); two.put("frame two   ".getBytes());
        two.flip();
        codec.onRead(null, two);
        assertEquals(2, extracted.size());
    }

    @Test
    void frameSpanningThreeReads() {
        AmqpFrameCodecImpl codec = new AmqpFrameCodecImpl(extractor);
        // Build a 21-byte frame: size(4) + doff(1) + type(1) + channel(2) + body(13)
        byte[] fullFrame = new byte[21];
        fullFrame[0] = 0; fullFrame[1] = 0; fullFrame[2] = 0; fullFrame[3] = 21; // size
        fullFrame[4] = 2;  // doff
        fullFrame[5] = 0;  // type
        // channel = 0
        fullFrame[6] = 0; fullFrame[7] = 0;
        System.arraycopy("hello world!!".getBytes(), 0, fullFrame, 8, 13); // body

        // Read 1: first 2 bytes
        ByteBuffer r1 = ByteBuffer.wrap(fullFrame, 0, 2);
        codec.onRead(null, r1);
        assertTrue(extracted.isEmpty());

        // Read 2: next 5 bytes
        ByteBuffer r2 = ByteBuffer.wrap(fullFrame, 2, 5);
        codec.onRead(null, r2);
        assertTrue(extracted.isEmpty());

        // Read 3: remaining 14 bytes
        ByteBuffer r3 = ByteBuffer.wrap(fullFrame, 7, 14);
        codec.onRead(null, r3);
        assertEquals(1, extracted.size());
    }

    @Test
    void resetOnDisconnect() {
        AmqpFrameCodecImpl codec = new AmqpFrameCodecImpl(extractor);
        // Send partial data
        ByteBuffer partial = ByteBuffer.allocate(4);
        partial.putInt(20);
        partial.flip();
        codec.onRead(null, partial);
        assertTrue(extracted.isEmpty());
        // Disconnect should reset
        codec.onDisconnect(null);
        // After reset, more data won't combine with old partial
        ByteBuffer rest = ByteBuffer.allocate(16);
        rest.put((byte) 2); rest.put((byte) 0); rest.putShort((short) 0);
        rest.put("no match".getBytes());
        rest.flip();
        // This data alone is 16 bytes, but the size header says 20 — incomplete
        codec.onRead(null, rest);
        assertTrue(extracted.isEmpty());
    }
}
