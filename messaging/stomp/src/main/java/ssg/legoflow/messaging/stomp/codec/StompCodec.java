package ssg.legoflow.messaging.stomp.codec;

import ssg.legoflow.messaging.stomp.StompMessage;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
/**
 * Codec for encoding and decoding STOMP (Simple Text Oriented Messaging Protocol) messages.
 * 
 * STOMP is a simple text-oriented protocol for message brokers.
 * 
 * @since 0.1.0
 */
public final class StompCodec {
    
    private static final Queue<ByteBuffer> BUFFER_POOL = new ConcurrentLinkedQueue<>();
    private static final int MAX_BUFFER_POOL_SIZE = 100;
    private static final int DEFAULT_BUFFER_SIZE = 1024;
    
    private StompCodec() {}
    
    /**
     * Encodes a STOMP message to bytes.
     * 
     * @param message the message to encode
     * @return the encoded bytes
     * @since 0.1.0
     */
    public static byte[] encode(StompMessage message) {
        // Try to get a buffer from the pool
        ByteBuffer buf = BUFFER_POOL.poll();
        if (buf == null) {
            buf = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE);
        } else {
            buf.clear();
        }
        
        try {
            // Encode command
            buf.put(message.getCommand().getBytes(StandardCharsets.UTF_8));
            buf.put((byte) '\n');
            
            // Encode headers
            for (Map.Entry<String, String> header : message.getHeaders().entrySet()) {
                buf.put(header.getKey().getBytes(StandardCharsets.UTF_8));
                buf.put((byte) ':');
                buf.put(header.getValue().getBytes(StandardCharsets.UTF_8));
                buf.put((byte) '\n');
            }
            
            // Empty line to separate headers from body
            buf.put((byte) '\n');
            
            // Encode body if present
            if (message.hasBody()) {
                buf.put(message.getBody());
            }
            
            // Add frame terminator
            buf.put((byte) 0x00);
            
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
     * Decodes bytes to a STOMP message.
     * 
     * @param data the raw bytes
     * @return the decoded message
     * @since 0.1.0
     */
    public static StompMessage decode(byte[] data) {
        // This is a simplified implementation - in practice would need to handle
        // frame boundaries and proper parsing
        String text = new String(data, StandardCharsets.UTF_8);
        
        // Find first empty line to separate headers from body
        int headerEnd = text.indexOf("\n\n");
        if (headerEnd == -1) {
            headerEnd = text.indexOf("\r\n\r\n");
        }
        
        // Parse command
        String command = "";
        Map<String, String> headers = new HashMap<>();
        byte[] body = new byte[0];
        
        if (headerEnd != -1) {
            // Extract command and headers
            String headerSection = text.substring(0, headerEnd);
            String[] lines = headerSection.split("\r?\n");
            
            if (lines.length > 0) {
                command = lines[0];
                for (int i = 1; i < lines.length; i++) {
                    String line = lines[i];
                    int colonIndex = line.indexOf(':');
                    if (colonIndex > 0) {
                        String key = line.substring(0, colonIndex).trim();
                        String value = line.substring(colonIndex + 1).trim();
                        headers.put(key, value);
                    }
                }
            }
            
            // Extract body
            if (headerEnd + 2 < text.length()) {
                body = text.substring(headerEnd + 2).getBytes(StandardCharsets.UTF_8);
            }
        } else {
            // Simple case: just command
            command = text.trim();
        }
        
        return new StompMessage(command, headers, body);
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
