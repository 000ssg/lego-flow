package ssg.legoflow.http.transfer;

import ssg.legoflow.blocks.AbstractDataFilter;
import ssg.legoflow.blocks.Context;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class ChunkedCodec extends AbstractDataFilter<ByteBuffer> {

    private final Mode mode;
    private ByteBuffer accumulator;

    public enum Mode { ENCODE, DECODE }

    public ChunkedCodec(Mode mode) {
        super(ByteBuffer.class);
        this.mode = mode;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ByteBuffer[] doFilter(Context ctx, ByteBuffer... data) {
        return switch (mode) {
            case ENCODE -> encodeChunks(data);
            case DECODE -> decodeChunks(data);
        };
    }

    private ByteBuffer[] encodeChunks(ByteBuffer[] data) {
        var results = new ByteBuffer[data.length];
        for (int i = 0; i < data.length; i++) {
            var buf = data[i].duplicate();
            var chunk = new byte[buf.remaining()];
            buf.get(chunk);
            var size = Integer.toHexString(chunk.length);
            var encoded = new ByteArrayOutputStream();
            encoded.writeBytes(size.getBytes(StandardCharsets.US_ASCII));
            encoded.writeBytes("\r\n".getBytes(StandardCharsets.US_ASCII));
            encoded.writeBytes(chunk);
            encoded.writeBytes("\r\n".getBytes(StandardCharsets.US_ASCII));
            results[i] = ByteBuffer.wrap(encoded.toByteArray());
        }
        return results;
    }

    private ByteBuffer[] decodeChunks(ByteBuffer[] data) {
        var combined = combineWithAccumulator(data);
        var results = new ArrayList<ByteBuffer>();

        while (combined.hasRemaining()) {
            combined.mark();

            // Try to find the chunk size line ending with \r\n
            int sizeLineEnd = findCrLf(combined);
            if (sizeLineEnd < 0) {
                // Not enough data for a complete size line
                combined.reset();
                break;
            }

            // Read the chunk size line
            int sizeLineStart = combined.position();
            // sizeLineEnd is the position of \r in \r\n relative to buffer start
            // We already scanned past it in findCrLf, so reset and read manually
            combined.reset();
            combined.mark();
            var sizeBytes = new byte[sizeLineEnd - combined.position()];
            combined.get(sizeBytes);
            combined.get(); // skip \r
            combined.get(); // skip \n

            var sizeLine = new String(sizeBytes, StandardCharsets.US_ASCII).trim();
            // Handle chunk extensions (;ext=value) by taking only the size part
            int semiColon = sizeLine.indexOf(';');
            if (semiColon >= 0) {
                sizeLine = sizeLine.substring(0, semiColon).trim();
            }

            int chunkSize;
            try {
                chunkSize = Integer.parseInt(sizeLine, 16);
            } catch (NumberFormatException e) {
                // Malformed chunk size — save remainder and stop
                combined.reset();
                break;
            }

            // Terminal chunk: size 0
            if (chunkSize == 0) {
                // Need at least \r\n after the 0-size chunk (terminal \r\n)
                if (combined.remaining() < 2) {
                    combined.reset();
                    break;
                }
                combined.get(); // skip \r
                combined.get(); // skip \n
                // Terminal chunk consumed successfully
                continue;
            }

            // Need chunkSize bytes + trailing \r\n
            if (combined.remaining() < chunkSize + 2) {
                combined.reset();
                break;
            }

            // Read chunk body
            var body = new byte[chunkSize];
            combined.get(body);
            combined.get(); // skip trailing \r
            combined.get(); // skip trailing \n

            results.add(ByteBuffer.wrap(body));
        }

        // Save remainder to accumulator
        if (combined.hasRemaining()) {
            accumulator = ByteBuffer.allocate(combined.remaining());
            accumulator.put(combined);
            accumulator.flip();
        } else {
            accumulator = null;
        }

        return results.toArray(ByteBuffer[]::new);
    }

    private ByteBuffer combineWithAccumulator(ByteBuffer[] data) {
        int totalSize = (accumulator != null ? accumulator.remaining() : 0);
        for (var buf : data) {
            totalSize += buf.remaining();
        }
        var combined = ByteBuffer.allocate(totalSize);
        if (accumulator != null) {
            combined.put(accumulator.duplicate());
            accumulator = null;
        }
        for (var buf : data) {
            combined.put(buf.duplicate());
        }
        combined.flip();
        return combined;
    }

    /**
     * Returns whether this codec has buffered partial data from a previous decode call.
     *
     * @return true if there is buffered data awaiting more input
     * @since 1.0.0
     */
    public boolean hasBufferedData() {
        return accumulator != null && accumulator.hasRemaining();
    }

    /**
     * Scans forward from the current position looking for \r\n.
     * Returns the position of \r if found, or -1 if not found.
     * The buffer position is left after the \n if found, or at the end if not found.
     */
    private static int findCrLf(ByteBuffer buf) {
        int start = buf.position();
        while (buf.remaining() >= 2) {
            int pos = buf.position();
            byte b = buf.get();
            if (b == '\r' && buf.hasRemaining()) {
                byte next = buf.get();
                if (next == '\n') {
                    return pos;
                }
                // Not \r\n, back up one position
                buf.position(buf.position() - 1);
            }
        }
        // If there's one byte left, check if we stopped at a \r (incomplete \r\n)
        return -1;
    }
}
