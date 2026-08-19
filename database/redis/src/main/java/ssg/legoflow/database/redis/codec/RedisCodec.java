package ssg.legoflow.database.redis.codec;

import ssg.legoflow.database.redis.RedisCommand;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
/**
 * Codec for encoding and decoding Redis commands.
 * 
 * Implements RESP (Redis Serialization Protocol).
 * 
 * @since 0.1.0
 */
public final class RedisCodec {
    
    private static final Queue<ByteBuffer> BUFFER_POOL = new ConcurrentLinkedQueue<>();
    private static final int MAX_BUFFER_POOL_SIZE = 100;
    private static final int DEFAULT_BUFFER_SIZE = 1024;
    
    private RedisCodec() {}
    
    /**
     * Encodes a Redis command to bytes in RESP format.
     * 
     * @param command the command to encode
     * @return the encoded bytes
     * @since 0.1.0
     */
    public static byte[] encode(RedisCommand command) {
        // Try to get a buffer from the pool
        ByteBuffer buf = BUFFER_POOL.poll();
        if (buf == null) {
            buf = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE);
        } else {
            buf.clear();
        }
        
        try {
            // RESP array format: *<number of arguments + 1>\r\n
            int argCount = command.getArgumentCount() + 1; // +1 for command name
            buf.put("*".getBytes(StandardCharsets.US_ASCII));
            buf.put(String.valueOf(argCount).getBytes(StandardCharsets.US_ASCII));
            buf.put("\r\n".getBytes(StandardCharsets.US_ASCII));
            
            // Encode command name
            String cmd = command.getCommand();
            buf.put("$".getBytes(StandardCharsets.US_ASCII));
            buf.put(String.valueOf(cmd.length()).getBytes(StandardCharsets.US_ASCII));
            buf.put("\r\n".getBytes(StandardCharsets.US_ASCII));
            buf.put(cmd.getBytes(StandardCharsets.US_ASCII));
            buf.put("\r\n".getBytes(StandardCharsets.US_ASCII));
            
            // Encode arguments
            for (String arg : command.getArguments()) {
                buf.put("$".getBytes(StandardCharsets.US_ASCII));
                buf.put(String.valueOf(arg.length()).getBytes(StandardCharsets.US_ASCII));
                buf.put("\r\n".getBytes(StandardCharsets.US_ASCII));
                buf.put(arg.getBytes(StandardCharsets.US_ASCII));
                buf.put("\r\n".getBytes(StandardCharsets.US_ASCII));
            }
            
            // Prepare result
            byte[] result = new byte[buf.position()];
            buf.flip();
            buf.get(result);
            
            return result;
        } finally {
            // Return buffer to pool if space available
            if (BUFFER_POOL.size() < MAX_BUFFER_POOL_SIZE) {
                BUFFER_POOL.offer(buf);
            }
        }
    }
    
    /**
     * Decodes bytes to a Redis command (simplified).
     * 
     * @param data the raw bytes
     * @return the decoded command
     * @since 0.1.0
     */
    public static RedisCommand decode(byte[] data) {
        // Simplified parsing - real implementation would need full RESP parsing
        String text = new String(data, StandardCharsets.UTF_8);
        
        // Parse simple command format for demonstration
        String[] lines = text.split("\r?\n");
        if (lines.length > 0 && lines[0].startsWith("*")) {
            // Multi-bulk reply, ignore for now
            return new RedisCommand("UNKNOWN", new ArrayList<>());
        }
        
        return new RedisCommand("UNKNOWN", new ArrayList<>());
    }
    
    /**
     * Gets a buffer from the pool, or creates a new one if none available.
     * 
     * @param requiredSize minimum required buffer size
     * @return a ByteBuffer ready for use
     */
    private static ByteBuffer getBufferFromPool(int requiredSize) {
        ByteBuffer buffer = BUFFER_POOL.poll();
        if (buffer == null || buffer.capacity() < requiredSize) {
            return ByteBuffer.allocate(Math.max(requiredSize, DEFAULT_BUFFER_SIZE));
        }
        buffer.clear();
        return buffer;
    }
    
    /**
     * Returns a buffer to the pool for reuse.
     * 
     * @param buffer the buffer to return
     */
    private static void returnBufferToPool(ByteBuffer buffer) {
        if (buffer != null && BUFFER_POOL.size() < MAX_BUFFER_POOL_SIZE) {
            BUFFER_POOL.offer(buffer);
        }
    }
}
