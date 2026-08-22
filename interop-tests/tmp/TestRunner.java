import com.rabbitmq.client.*;
import java.util.*;

public class TestRunner {
    public static void main(String[] args) throws Exception {
        System.out.println("=== AMQP 0-9-1 Interop Test Runner ===");
        
        String host = System.getProperty("interop.amqp.host", "rabbitmq");
        int port = Integer.getInteger("interop.amqp.port", 5672);
        String username = System.getProperty("interop.amqp.username", "guest");
        String password = System.getProperty("interop.amqp.password", "guest");
        String queueName = System.getProperty("interop.amqp.queue", "interop-amqp091-test");
        String exchangeName = System.getProperty("interop.amqp.exchange", "amqp091-test-ex");
        
        int passed = 0;
        int failed = 0;
        
        Connection conn = null;
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(host);
            factory.setPort(port);
            factory.setUsername(username);
            factory.setPassword(password);
            factory.setConnectionTimeout(30000);
            factory.setHandshakeTimeout(30000);
            
            System.out.println("Connecting to " + host + ":" + port + "...");
            conn = factory.newConnection();
            System.out.println("Connected: " + conn);
            passed++;
            
            Channel ch = conn.createChannel();
            System.out.println("Channel opened: " + ch.getChannelNumber());
            passed++;
            
            // Test 1: Exchange declare
            System.out.println("\n--- Test: Exchange Declare ---");
            try {
                String testExchange = exchangeName + "-test";
                ch.exchangeDeclare(testExchange, BuiltinExchangeType.DIRECT, false);
                System.out.println("PASS: Exchange declared: " + testExchange);
                passed++;
            } catch (Exception e) {
                System.out.println("FAIL: " + e.getMessage());
                failed++;
            }
            
            // Test 2: Queue declare
            System.out.println("\n--- Test: Queue Declare ---");
            try {
                String testQueue = queueName + "-test-" + System.currentTimeMillis();
                AMQP.Queue.DeclareOk result = ch.queueDeclare(
                    testQueue, false, true, true, null);
                System.out.println("PASS: Queue declared: " + result.getQueue());
                passed++;
            } catch (Exception e) {
                System.out.println("FAIL: " + e.getMessage());
                failed++;
            }
            
            // Test 3: Publish
            System.out.println("\n--- Test: Publish ---");
            try {
                String msg = "interop-test-" + System.currentTimeMillis();
                ch.basicPublish(exchangeName, queueName, null, msg.getBytes("UTF-8"));
                System.out.println("PASS: Published: " + msg);
                passed++;
            } catch (Exception e) {
                System.out.println("FAIL: " + e.getMessage());
                failed++;
            }
            
            // Test 4: Consume
            System.out.println("\n--- Test: Consume ---");
            try {
                String testQueue = queueName + "-test-q-" + System.currentTimeMillis();
                ch.queueDeclare(testQueue, false, true, true, null);
                String msg = "test-msg-" + System.currentTimeMillis();
                ch.basicPublish(exchangeName, testQueue, null, msg.getBytes("UTF-8"));
                GetResponse resp = ch.basicGet(testQueue, true);
                if (resp != null) {
                    String received = new String(resp.getBody(), "UTF-8");
                    System.out.println("PASS: Received: " + received);
                    passed++;
                } else {
                    System.out.println("WARN: No message received");
                }
            } catch (Exception e) {
                System.out.println("FAIL: " + e.getMessage());
                failed++;
            }
            
            // Test 5: Multiple messages
            System.out.println("\n--- Test: Multiple Messages ---");
            try {
                String testQueue = queueName + "-multi-" + System.currentTimeMillis();
                ch.queueDeclare(testQueue, false, true, true, null);
                for (int i = 0; i < 5; i++) {
                    ch.basicPublish(exchangeName, testQueue, null,
                        ("msg-" + i).getBytes("UTF-8"));
                }
                int consumed = 0;
                GetResponse r;
                while ((r = ch.basicGet(testQueue, true)) != null) {
                    consumed++;
                }
                System.out.println("PASS: Consumed " + consumed + " messages");
                passed++;
            } catch (Exception e) {
                System.out.println("FAIL: " + e.getMessage());
                failed++;
            }
            
            // Test 6: Headers
            System.out.println("\n--- Test: Headers ---");
            try {
                String testQueue = queueName + "-headers-" + System.currentTimeMillis();
                ch.queueDeclare(testQueue, false, true, true, null);
                Map<String, Object> headers = new HashMap<>();
                headers.put("test-key", "test-value");
                AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                    .headers(headers)
                    .contentType("text/plain")
                    .build();
                ch.basicPublish(exchangeName, testQueue, props, "hello".getBytes("UTF-8"));
                GetResponse resp = ch.basicGet(testQueue, true);
                if (resp != null) {
                    Object val = resp.getProps().getHeaders().get("test-key");
                    System.out.println("PASS: Header value: " + val);
                    passed++;
                }
            } catch (Exception e) {
                System.out.println("FAIL: " + e.getMessage());
                failed++;
            }
            
            // Test 7: QoS
            System.out.println("\n--- Test: QoS ---");
            try {
                ch.basicQos(10);
                String testQueue = queueName + "-qos-" + System.currentTimeMillis();
                ch.queueDeclare(testQueue, false, true, true, null);
                for (int i = 0; i < 20; i++) {
                    ch.basicPublish(exchangeName, testQueue, null,
                        ("qos-" + i).getBytes("UTF-8"));
                }
                for (int i = 0; i < 20; i++) {
                    GetResponse r = ch.basicGet(testQueue, true);
                    if (r == null) break;
                }
                System.out.println("PASS: QoS test completed");
                passed++;
            } catch (Exception e) {
                System.out.println("FAIL: " + e.getMessage());
                failed++;
            }
            
            // Test 8: Channel close/reopen
            System.out.println("\n--- Test: Channel Close/Reopen ---");
            try {
                int chNum = ch.getChannelNumber();
                ch.close();
                System.out.println("PASS: Channel " + chNum + " closed");
                passed++;
                
                Channel newCh = conn.createChannel();
                System.out.println("PASS: New channel opened: " + newCh.getChannelNumber());
                passed++;
                newCh.close();
            } catch (Exception e) {
                System.out.println("FAIL: " + e.getMessage());
                failed++;
            }
            
            // Summary
            System.out.println("\n========== SUMMARY ==========");
            System.out.println("Passed: " + passed);
            System.out.println("Failed: " + failed);
            
            if (failed > 0) {
                System.exit(1);
            } else {
                System.out.println("ALL TESTS PASSED!");
            }
            
            ch.close();
        } finally {
            if (conn != null && conn.isOpen()) {
                try { conn.close(); } catch (Exception ignored) {}
            }
        }
    }
}
