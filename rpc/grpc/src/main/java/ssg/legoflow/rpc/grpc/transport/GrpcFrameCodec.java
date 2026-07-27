package ssg.legoflow.rpc.grpc.transport;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.io.ByteArrayInputStream;

/**
 * Encodes and decodes gRPC length-prefixed message frames.
 * Format: 1-byte compressed flag + 4-byte big-endian length + data.
 */
public final class GrpcFrameCodec {

    /** Maximum message size (4 MB default). */
    public static final int DEFAULT_MAX_MESSAGE_SIZE = 4 * 1024 * 1024;

    private GrpcFrameCodec() {
    }

    /**
     * Encodes a message into a gRPC frame (uncompressed).
     *
     * @param message the serialized protobuf message bytes
     * @return the framed message
     */
    public static byte[] encode(byte[] message) {
        return encode(message, false);
    }

    /**
     * Encodes a message into a gRPC frame.
     *
     * @param message    the serialized protobuf message bytes
     * @param compressed whether the data is compressed
     * @return the framed message
     */
    public static byte[] encode(byte[] message, boolean compressed) {
        var out = new byte[5 + message.length];
        out[0] = compressed ? (byte) 1 : (byte) 0;
        out[1] = (byte) ((message.length >> 24) & 0xFF);
        out[2] = (byte) ((message.length >> 16) & 0xFF);
        out[3] = (byte) ((message.length >> 8) & 0xFF);
        out[4] = (byte) (message.length & 0xFF);
        System.arraycopy(message, 0, out, 5, message.length);
        return out;
    }

    /**
     * Encodes with gzip compression.
     */
    public static byte[] encodeCompressed(byte[] message, GrpcEncoding encoding) {
        if (encoding == GrpcEncoding.IDENTITY) {
            return encode(message, false);
        }
        byte[] compressed = compress(message, encoding);
        return encode(compressed, true);
    }

    /**
     * A decoded gRPC frame.
     */
    public record DecodedFrame(boolean compressed, byte[] data) {}

    /**
     * Decodes a single gRPC frame from the buffer.
     *
     * @param buf the buffer containing framed data
     * @return the decoded frame, or null if insufficient data
     */
    public static DecodedFrame decodeFrame(ByteBuffer buf) {
        return decodeFrame(buf, DEFAULT_MAX_MESSAGE_SIZE);
    }

    /**
     * Decodes a single gRPC frame from the buffer with a size limit.
     */
    public static DecodedFrame decodeFrame(ByteBuffer buf, int maxMessageSize) {
        if (buf.remaining() < 5) {
            return null;
        }
        int startPos = buf.position();
        boolean compressed = buf.get() != 0;
        int length = ((buf.get() & 0xFF) << 24)
                | ((buf.get() & 0xFF) << 16)
                | ((buf.get() & 0xFF) << 8)
                | (buf.get() & 0xFF);

        if (length < 0 || length > maxMessageSize) {
            throw new IllegalArgumentException(
                    "Message size " + length + " exceeds maximum " + maxMessageSize);
        }

        if (buf.remaining() < length) {
            buf.position(startPos);
            return null;
        }

        byte[] data = new byte[length];
        buf.get(data);
        return new DecodedFrame(compressed, data);
    }

    /**
     * Decodes all frames from the buffer.
     */
    public static List<DecodedFrame> decodeAllFrames(ByteBuffer buf) {
        var frames = new ArrayList<DecodedFrame>();
        while (buf.hasRemaining()) {
            var frame = decodeFrame(buf);
            if (frame == null) break;
            frames.add(frame);
        }
        return frames;
    }

    /**
     * Decompresses frame data if compressed.
     */
    public static byte[] decompressIfNeeded(DecodedFrame frame, GrpcEncoding encoding) {
        if (!frame.compressed() || encoding == GrpcEncoding.IDENTITY) {
            return frame.data();
        }
        return decompress(frame.data(), encoding);
    }

    /**
     * Compresses data with the given encoding.
     */
    public static byte[] compress(byte[] data, GrpcEncoding encoding) {
        return switch (encoding) {
            case IDENTITY -> data;
            case GZIP -> gzipCompress(data);
            case DEFLATE -> deflateCompress(data);
        };
    }

    /**
     * Decompresses data with the given encoding.
     */
    public static byte[] decompress(byte[] data, GrpcEncoding encoding) {
        return switch (encoding) {
            case IDENTITY -> data;
            case GZIP -> gzipDecompress(data);
            case DEFLATE -> deflateDecompress(data);
        };
    }

    private static byte[] gzipCompress(byte[] data) {
        try {
            var baos = new ByteArrayOutputStream();
            try (var gos = new GZIPOutputStream(baos)) {
                gos.write(data);
            }
            return baos.toByteArray();
        } catch (java.io.IOException e) {
            throw new RuntimeException("gzip compression failed", e);
        }
    }

    private static byte[] gzipDecompress(byte[] data) {
        try {
            var bais = new ByteArrayInputStream(data);
            try (var gis = new GZIPInputStream(bais)) {
                return gis.readAllBytes();
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException("gzip decompression failed", e);
        }
    }

    private static byte[] deflateCompress(byte[] data) {
        var deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();
        var baos = new ByteArrayOutputStream(data.length);
        byte[] buf = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buf);
            baos.write(buf, 0, count);
        }
        deflater.end();
        return baos.toByteArray();
    }

    private static byte[] deflateDecompress(byte[] data) {
        try {
            var inflater = new Inflater();
            inflater.setInput(data);
            var baos = new ByteArrayOutputStream(data.length);
            byte[] buf = new byte[1024];
            while (!inflater.finished()) {
                int count = inflater.inflate(buf);
                baos.write(buf, 0, count);
            }
            inflater.end();
            return baos.toByteArray();
        } catch (java.util.zip.DataFormatException e) {
            throw new RuntimeException("deflate decompression failed", e);
        }
    }
}
