package ssg.legoflow.messaging.mqtt.codec;

import ssg.legoflow.messaging.mqtt.MqttMessage;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Codec for encoding and decoding MQTT (Message Queuing Telemetry Transport) messages.
 * 
 * @since 0.1.0
 */
public final class MqttCodec {
    
    private static final Queue<ByteBuffer> BUFFER_POOL = new ConcurrentLinkedQueue<>();
    private static final int MAX_BUFFER_POOL_SIZE = 100;
    private static final int DEFAULT_BUFFER_SIZE = 1024;
    
    private MqttCodec() {}
    
    /**
     * Encodes an MQTT message to bytes.
     * 
     * @param message the message to encode
     * @return the encoded bytes
     * @since 0.1.0
     */
    public static byte[] encode(MqttMessage message) {
        // Try to get a buffer from the pool
        ByteBuffer buf = BUFFER_POOL.poll();
        if (buf == null) {
            buf = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE);
        } else {
            buf.clear();
        }
        
        try {
            // Encode fixed header
            int flags = (message.isDupFlag() ? 0x08 : 0) |
                       (message.getQosLevel() << 1) |
                       (message.isRetainFlag() ? 1 : 0);
            
            buf.put((byte) (message.getMessageType() << 4 | flags));
            
            // For simplicity in this basic implementation
            // In a real implementation, would properly encode variable-length fields
            
            // Encode topic
            byte[] topicBytes = message.getTopic().getBytes(StandardCharsets.UTF_8);
            buf.putShort((short) topicBytes.length);
            buf.put(topicBytes);
            
            // Encode payload
            if (message.hasPayload()) {
                buf.put(message.getPayload());
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
     * Decodes bytes to an MQTT message.
     * 
     * @param data the raw bytes
     * @return the decoded message
     * @since 0.1.0
     */
    public static MqttMessage decode(byte[] data) {
        // Simplified implementation - in practice would need proper MQTT parsing
        ByteBuffer buf = ByteBuffer.wrap(data);
        
        // Read fixed header
        byte fixedHeader = buf.get();
        int messageType = (fixedHeader >> 4) & 0x0F;
        boolean dupFlag = (fixedHeader & 0x08) != 0;
        int qosLevel = (fixedHeader & 0x06) >> 1;
        boolean retainFlag = (fixedHeader & 0x01) != 0;
        
        // Simplified topic reading
        String topic = "";
        if (buf.remaining() >= 2) {
            int topicLength = buf.getShort() & 0xFFFF;
            if (buf.remaining() >= topicLength) {
                byte[] topicBytes = new byte[topicLength];
                buf.get(topicBytes);
                topic = new String(topicBytes, StandardCharsets.UTF_8);
            }
        }
        
        // Get payload
        byte[] payload = new byte[buf.remaining()];
        buf.get(payload);
        
        return new MqttMessage(messageType, dupFlag, qosLevel, retainFlag, topic, payload);
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
