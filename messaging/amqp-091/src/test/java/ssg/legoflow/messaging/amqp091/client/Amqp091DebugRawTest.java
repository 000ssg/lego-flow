package ssg.legoflow.messaging.amqp091.client;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

class Amqp091DebugRawTest {
    private static final Logger LOG = LoggerFactory.getLogger(Amqp091DebugRawTest.class);

    @Test
    void testSimpleConnect() throws Exception {
        Socket socket = new Socket("localhost", 5672);
        socket.setSoTimeout(10000);
        socket.setTcpNoDelay(true);
        
        InputStream rawIn = socket.getInputStream();
        OutputStream rawOut = socket.getOutputStream();
        
        // Send greeting
        byte[] greeting = {0x41, 0x4D, 0x51, 0x50, 0x00, 0x00, 0x09, 0x01};
        rawOut.write(greeting);
        rawOut.flush();
        LOG.info("Sent greeting");
        
        // RabbitMQ 4.x does NOT echo greeting - sends connection.start frame directly
        // Read frame header (7 bytes: type + chan + size)
        byte[] hdr = new byte[7];
        drain(rawIn, hdr);
        LOG.info("Frame header: {}", bytesToHex(hdr, 7));
        int type = hdr[0] & 0xFF;
        int chan = ((hdr[1] & 0xFF) << 8) | (hdr[2] & 0xFF);
        int size = ((hdr[3] & 0xFF) << 24) | ((hdr[4] & 0xFF) << 16) |
                   ((hdr[5] & 0xFF) << 8) | (hdr[6] & 0xFF);
        LOG.info("Frame: type=0x{}, chan={}, size={}", type, chan, size);
        
        if (size > 0 && size < 10000) {
            byte[] pl = new byte[size];
            drain(rawIn, pl);
            LOG.info("Payload: {} bytes", size);
            byte end = (byte) rawIn.read();
            LOG.info("Frame end: 0x{}", end & 0xFF);
            LOG.info("Payload first 30: {}", bytesToHex(pl, Math.min(size, 30)));
        }
        
        // Build AMQP 0-9-1 client properties table
        byte[] clientProps = buildClientProps();
        byte[] mechanism = "PLAIN".getBytes(StandardCharsets.US_ASCII);
        byte[] response = ("\0guest\0guest").getBytes(StandardCharsets.UTF_8);
        byte[] locale = "en_US".getBytes(StandardCharsets.US_ASCII);
        
        int sizeOk = 1 + clientProps.length + 1 + mechanism.length + 4 + response.length + 1 + locale.length;
        ByteBuffer payloadBuf = ByteBuffer.allocate(sizeOk);
        payloadBuf.put((byte) clientProps.length); payloadBuf.put(clientProps);
        payloadBuf.put((byte) mechanism.length); payloadBuf.put(mechanism);
        payloadBuf.putInt(response.length); payloadBuf.put(response);
        payloadBuf.put((byte) locale.length); payloadBuf.put(locale);
        payloadBuf.flip();
        byte[] p = new byte[payloadBuf.remaining()];
        payloadBuf.get(p);
        
        // Method frame: type(0x01) + chan(2) + size(4) + payload + end(0xCE)
        ByteBuffer frame = ByteBuffer.allocate(1 + 2 + 4 + p.length + 1);
        frame.put((byte) 0x01); frame.putShort((short) 0);
        frame.putInt(p.length); frame.put(p); frame.put((byte) 0xCE);
        frame.flip();
        rawOut.write(frame.array(), 0, frame.remaining());
        rawOut.flush();
        LOG.info("Sent connection.start-ok: {} bytes", frame.remaining());
        
        // Wait for connection.tune
        Thread.sleep(3000);
        
        if (rawIn.available() > 0) {
            byte[] nextHdr = new byte[7];
            drain(rawIn, nextHdr);
            int nextType = nextHdr[0] & 0xFF;
            int nextChan = (nextHdr[1] & 0xFF) << 8 | (nextHdr[2] & 0xFF);
            int nextSize = (nextHdr[3] & 0xFF) << 24 | (nextHdr[4] & 0xFF) << 16 | 
                           (nextHdr[5] & 0xFF) << 8 | (nextHdr[6] & 0xFF);
            LOG.info("Next frame: type=0x{}, chan={}, size={}", nextType, nextChan, nextSize);
            if (nextSize > 0 && nextSize < 10000) {
                byte[] nextPl = new byte[nextSize];
                drain(rawIn, nextPl);
                byte nextEnd = (byte) rawIn.read();
                LOG.info("Next payload: {} bytes, end=0x{}", nextSize, nextEnd & 0xFF);
                LOG.info("Next payload hex: {}", bytesToHex(nextPl, Math.min(nextSize, 80)));
            }
        } else {
            LOG.warn("No data available from server");
        }
        
        socket.close();
    }

    private void drain(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) throw new IOException("EOF after reading " + off + " bytes");
            off += n;
        }
    }

    private byte[] buildClientProps() {
        StringBuilder sb = new StringBuilder();
        sb.append("productStringLego-Flow-AMQP\0");
        sb.append("platformStringJava\0");
        sb.append("versionString0.2.0-SNAPSHOT\0");
        byte[] raw = sb.toString().getBytes(StandardCharsets.US_ASCII);
        return new byte[]{(byte) raw.length};
    }

    private String bytesToHex(byte[] data, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(data.length, max); i++)
            sb.append(String.format("%02x ", data[i] & 0xFF));
        return sb.toString().trim();
    }
}
