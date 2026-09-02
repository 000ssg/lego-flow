package ssg.legoflow.network.dns.codec;

import ssg.legoflow.network.dns.DnsMessage;
import ssg.legoflow.network.dns.DnsQuestion;
import ssg.legoflow.network.dns.DnsRecord;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
/**
 * Codec for encoding and decoding DNS messages.
 * 
 * Implements DNS protocol with standard wire format.
 * 
 * @since 0.1.0
 */
public final class DnsCodec {
    
    private static final Queue<ByteBuffer> BUFFER_POOL = new ConcurrentLinkedQueue<>();
    private static final int MAX_BUFFER_POOL_SIZE = 100;
    private static final int DEFAULT_BUFFER_SIZE = 1024;
    
    private DnsCodec() {}
    
    /**
     * Encodes a DNS message to bytes.
     * 
     * @param message the message to encode
     * @return the encoded bytes
     * @since 0.1.0
     */
    public static byte[] encode(DnsMessage message) {
        // Try to get a buffer from the pool
        ByteBuffer buf = BUFFER_POOL.poll();
        if (buf == null) {
            buf = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE);
        } else {
            buf.clear();
        }
        
        try {
            // Encode header
            buf.putShort((short) message.getTransactionId());
            
            int flags = (message.isResponse() ? 0x8000 : 0) |
                       (message.getOpcode() << 11) |
                       (message.isAuthoritativeAnswer() ? 0x0400 : 0) |
                       (message.isTruncated() ? 0x0200 : 0) |
                       (message.isRecursionDesired() ? 0x0100 : 0) |
                       (message.isRecursionAvailable() ? 0x0080 : 0) |
                       message.getRcode();
            
            buf.putShort((short) flags);
            
            // Encode counts
            buf.putShort((short) message.getQuestions().size());
            buf.putShort((short) message.getAnswers().size());
            buf.putShort((short) message.getAuthorities().size());
            buf.putShort((short) message.getAdditional().size());
            
            // Encode questions (simplified)
            for (DnsQuestion question : message.getQuestions()) {
                // Encode name (simplified for demonstration)
                buf.put(question.getName().getBytes(StandardCharsets.UTF_8));
                buf.putShort((short) question.getType());
                buf.putShort((short) question.getClazz());
            }
            
            // Encode answers (simplified)
            for (DnsRecord record : message.getAnswers()) {
                // Encode name, type, class, ttl, and rdata
                buf.put(record.getName().getBytes(StandardCharsets.UTF_8));
                buf.putShort((short) record.getType());
                buf.putShort((short) record.getClazz());
                buf.putInt(record.getTtl());
                buf.putShort((short) record.getRdata().length);
                buf.put(record.getRdata());
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
     * Decodes bytes to a DNS message (simplified).
     * 
     * @param data the raw bytes
     * @return the decoded message
     * @since 0.1.0
     */
    public static DnsMessage decode(byte[] data) {
        // Simplified parsing - real implementation would need full DNS parsing
        ByteBuffer buf = ByteBuffer.wrap(data);
        
        // Parse header
        int transactionId = buf.getShort() & 0xFFFF;
        int flags = buf.getShort() & 0xFFFF;
        boolean isResponse = (flags & 0x8000) != 0;
        int opcode = (flags >> 11) & 0x0F;
        boolean authoritativeAnswer = (flags & 0x0400) != 0;
        boolean truncated = (flags & 0x0200) != 0;
        boolean recursionDesired = (flags & 0x0100) != 0;
        boolean recursionAvailable = (flags & 0x0080) != 0;
        int rcode = flags & 0x000F;
        
        // Parse counts (simplified)
        int questionCount = buf.getShort() & 0xFFFF;
        int answerCount = buf.getShort() & 0xFFFF;
        int authorityCount = buf.getShort() & 0xFFFF;
        int additionalCount = buf.getShort() & 0xFFFF;
        
        // Create dummy structures for demonstration
        List<DnsQuestion> questions = new ArrayList<>();
        List<DnsRecord> answers = new ArrayList<>();
        List<DnsRecord> authorities = new ArrayList<>();
        List<DnsRecord> additional = new ArrayList<>();
        
        return new DnsMessage(transactionId, isResponse, opcode, authoritativeAnswer, 
                             truncated, recursionDesired, recursionAvailable, rcode, 
                             questions, answers, authorities, additional);
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
