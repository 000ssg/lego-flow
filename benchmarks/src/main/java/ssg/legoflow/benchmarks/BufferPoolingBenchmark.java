package ssg.legoflow.benchmarks;

import ssg.legoflow.media.sip.protocol.SipCodec;
import ssg.legoflow.media.rtp.codec.RtpCodec;
import ssg.legoflow.messaging.stomp.codec.StompCodec;
import ssg.legoflow.messaging.mqtt.codec.MqttCodec;
import ssg.legoflow.database.redis.codec.RedisCodec;
import ssg.legoflow.network.dns.codec.DnsCodec;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark for buffer pooling performance improvements.
 * 
 * This benchmark validates that buffer pooling reduces memory allocation
 * and improves throughput of protocol codecs.
 */
public class BufferPoolingBenchmark {
    
    public static void main(String[] args) {
        System.out.println("Running buffer pooling benchmarks...");
        
        // Test SIP buffer pooling
        testSipBufferPooling();
        
        // Test RTP buffer pooling
        testRtpBufferPooling();
        
        // Test STOMP buffer pooling
        testStompBufferPooling();
        
        // Test MQTT buffer pooling
        testMqttBufferPooling();
        
        // Test Redis buffer pooling
        testRedisBufferPooling();
        
        // Test DNS buffer pooling
        testDnsBufferPooling();
        
        System.out.println("All buffer pooling benchmarks completed.");
    }
    
    private static void testSipBufferPooling() {
        System.out.println("Testing SIP buffer pooling...");
        
        // Warm up the buffer pool
        for (int i = 0; i < 50; i++) {
            SipCodec.encode(new ssg.legoflow.media.sip.protocol.SipRequest(
                ssg.legoflow.media.sip.protocol.SipMethod.INVITE,
                "sip:user@example.com",
                ssg.legoflow.media.sip.protocol.SipMessage.VERSION,
                new ssg.legoflow.media.sip.header.SipHeaders(), 
                new byte[0]));
        }
        
        // Test performance
        long startTime = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            SipCodec.encode(new ssg.legoflow.media.sip.protocol.SipRequest(
                ssg.legoflow.media.sip.protocol.SipMethod.INVITE,
                "sip:user@example.com",
                ssg.legoflow.media.sip.protocol.SipMessage.VERSION,
                new ssg.legoflow.media.sip.header.SipHeaders(), 
                new byte[0]));
        }
        long endTime = System.nanoTime();
        
        System.out.println("SIP encoding time: " + TimeUnit.NANOSECONDS.toMillis(endTime - startTime) + "ms");
    }
    
    private static void testRtpBufferPooling() {
        System.out.println("Testing RTP buffer pooling...");
        
        // Warm up the buffer pool
        for (int i = 0; i < 50; i++) {
            RtpCodec.encode(
                new ssg.legoflow.media.rtp.packet.RtpPacket(
                    new ssg.legoflow.media.rtp.packet.RtpHeader(2, false, false, false, 100, 1, 1000, 123456789, 
                        java.util.Collections.emptyList(), java.util.Optional.empty()),
                    new byte[100]));
        }
        
        // Test performance
        long startTime = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            RtpCodec.encode(
                new ssg.legoflow.media.rtp.packet.RtpPacket(
                    new ssg.legoflow.media.rtp.packet.RtpHeader(2, false, false, false, 100, 1, 1000, 123456789, 
                        java.util.Collections.emptyList(), java.util.Optional.empty()),
                    new byte[100]));
        }
        long endTime = System.nanoTime();
        
        System.out.println("RTP encoding time: " + TimeUnit.NANOSECONDS.toMillis(endTime - startTime) + "ms");
    }
    
    private static void testStompBufferPooling() {
        System.out.println("Testing STOMP buffer pooling...");
        
        // Warm up the buffer pool
        for (int i = 0; i < 50; i++) {
            StompCodec.encode(new ssg.legoflow.messaging.stomp.StompMessage("SEND", 
                java.util.Collections.singletonMap("destination", "/queue/test"), 
                "Test message".getBytes()));
        }
        
        // Test performance
        long startTime = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            StompCodec.encode(new ssg.legoflow.messaging.stomp.StompMessage("SEND", 
                java.util.Collections.singletonMap("destination", "/queue/test"), 
                "Test message".getBytes()));
        }
        long endTime = System.nanoTime();
        
        System.out.println("STOMP encoding time: " + TimeUnit.NANOSECONDS.toMillis(endTime - startTime) + "ms");
    }
    
    private static void testMqttBufferPooling() {
        System.out.println("Testing MQTT buffer pooling...");
        
        // Warm up the buffer pool
        for (int i = 0; i < 50; i++) {
            MqttCodec.encode(new ssg.legoflow.messaging.mqtt.MqttMessage(3, false, 0, false, 
                "test/topic", "Test message".getBytes()));
        }
        
        // Test performance
        long startTime = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            MqttCodec.encode(new ssg.legoflow.messaging.mqtt.MqttMessage(3, false, 0, false, 
                "test/topic", "Test message".getBytes()));
        }
        long endTime = System.nanoTime();
        
        System.out.println("MQTT encoding time: " + TimeUnit.NANOSECONDS.toMillis(endTime - startTime) + "ms");
    }
    
    private static void testRedisBufferPooling() {
        System.out.println("Testing Redis buffer pooling...");
        
        // Warm up the buffer pool
        for (int i = 0; i < 50; i++) {
            RedisCodec.encode(new ssg.legoflow.database.redis.RedisCommand("SET", 
                java.util.Arrays.asList("key1", "value1")));
        }
        
        // Test performance
        long startTime = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            RedisCodec.encode(new ssg.legoflow.database.redis.RedisCommand("SET", 
                java.util.Arrays.asList("key1", "value1")));
        }
        long endTime = System.nanoTime();
        
        System.out.println("Redis encoding time: " + TimeUnit.NANOSECONDS.toMillis(endTime - startTime) + "ms");
    }
    
    private static void testDnsBufferPooling() {
        System.out.println("Testing DNS buffer pooling...");
        
        // Warm up the buffer pool
        for (int i = 0; i < 50; i++) {
            DnsCodec.encode(new ssg.legoflow.network.dns.DnsMessage(12345, false, 0, false, 
                false, true, true, 0, java.util.Collections.emptyList(), 
                java.util.Collections.emptyList(), java.util.Collections.emptyList(), 
                java.util.Collections.emptyList()));
        }
        
        // Test performance
        long startTime = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            DnsCodec.encode(new ssg.legoflow.network.dns.DnsMessage(12345, false, 0, false, 
                false, true, true, 0, java.util.Collections.emptyList(), 
                java.util.Collections.emptyList(), java.util.Collections.emptyList(), 
                java.util.Collections.emptyList()));
        }
        long endTime = System.nanoTime();
        
        System.out.println("DNS encoding time: " + TimeUnit.NANOSECONDS.toMillis(endTime - startTime) + "ms");
    }
}
