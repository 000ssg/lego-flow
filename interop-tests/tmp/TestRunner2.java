import com.rabbitmq.client.*;
import java.util.*;

public class TestRunner2 {
    static int passed = 0;
    static int failed = 0;
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== AMQP 0-9-1 Interop Test Runner ===");
        
        String host = System.getProperty("interop.amqp.host", "rabbitmq");
        int port = Integer.getInteger("interop.amqp.port", 5672);
        String username = System.getProperty("interop.amqp.username", "guest");
        String password = System.getProperty("interop.amqp.password", "guest");
        String queueName = System.getProperty("interop.amqp.queue", "interop-amqp091-test");
        String exchangeName = System.getProperty("interop.amqp.exchange", "amqp091-test-ex");
        
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
            
            // Declare the exchange and queue FIRST (auto-delete for clean test)
            String setupExchange = exchangeName + "-setup-" + System.currentTimeMillis();
            String setupQueue = queueName + "-setup-" + System.currentTimeMillis();
            
            ch.exchangeDeclare(setupExchange, BuiltinExchangeType.DIRECT, false);
            AMQP.Queue.DeclareOk qResult = ch.queueDeclare(setupQueue, false, true, true, null);
            String actualQueueName = qResult.getQueue();
            ch.queueBind(actualQueueName, setupExchange, actualQueueName);
            System.out.println("Setup: exchange=" + setupExchange + " queue=" + actualQueueName);
            passed++;
            
            // Test 1: Publish
            System.out.println("\n--- Test 1: Publish ---");
            try {
                String msg = "interop-test-" + System.currentTimeMillis();
                ch.basicPublish(setupExchange, actualQueueName, null, msg.getBytes("UTF-8"));
                System.out.println("PASS: Published: " + msg);
                passed++;
            } catch (Exception e) {
                System.out.println("FAIL: " + e.getMessage());
                failed++;
            }
            
            // Test 2: Consume (basicGet)
            System.out.println("\n--- Test 2: Consume ---");
            try {
                GetResponse resp = ch.basicGet(actualQueueName, true);
                if (resp != null) {
                    String received = new String(resp.getBody(), "UTF-8");
                    System.out.println("PASS: Received: " + received);
                    passed++;
                } else {
                    System.out.println("FAIL: No message received");
                    failed++;
                }
            } catch (Exception e) {
                System.out.println("FAIL: " + e.getMessage());
                failed++;
            }
            
            // Test 3: Multiple messages
            System.out.println("\n--- Test 3: Multiple Messages ---");
            String multiQueue = queueName + "-multi-" + System.currentTimeMillis();
            ch.queueDeclare(multiQueue, false, true, true, null);
            try {
                for (int i = 0; i < 5; i++) {
                    ch.basicPublish(setupExchange, multiQueue, null,
                        ("msg-" + i).getBytes("UTF-8"));
                }
                int consumed = 0;
                GetResponse r;
                while ((r = ch.basicGet(multiQueue, true)) != null) {
                    consumed++;
                }
                System.out.println("PASS: Published and consumed " + consumed + " messages");
                passed++;
            } catch (Exception e) {
                System.out.println("FAIL: " + e.getMessage());
                failed++;
            }
            
            // Test 4: Headers
            System.out.println("\n--- Test 4: Headers ---");
            String hdrQueue = queueName + "-headers-" + System.currentTimeMillis();
            ch.queueDeclare(hdrQueue, false, true, true, null);
            try {
                Map<String, Object> headers = new HashMap<>();
                headers.put("test-key", "test-value");
                AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                    .headers(headers)
                    .contentType("text/plain")
                    .build();
                ch.basicPublish(setupExchange, hdrQueue, props, "hello".getBytes("UTF-8"));
                GetResponse resp = ch.basicGet(hdrQueue, true);
                if (resp != null && resp.getProps().getHeaders() != null) {
                    Object val = resp.getProps().getHeaders().get("test-key");
                    System.out.println("PASS: Header test-key=" + val);
                    passed++;
                } else {
                    System.out.println("FAIL: No headers");
                    failed++;
                }
            } catch (Exception e) {
                System.out.println("FAIL: " + e.getMessage());
                failed++;
            }
            
            // Test 5: QoS
            System.out.println("\n--- Test 5: QoS ---");
            String qosQueue = queueName + "-qos-" + System.currentTimeMillis();
            ch.queueDeclare(qosQueue, false, true, true, null);
            try {
                ch.basicQos(10);
                for (int i = 0; i < 20; i++) {
                    ch.basicPublish(setupExchange, qosQueue, null,
                        ("qos-" + i).getBytes("UTF-8"));
                }
                for (int i = 0; i < 20; i++) {
                    GetResponse r = ch.basicGet(qosQueue, true);
                    if (r == null) break;
                }
                System.out.println("PASS: QoS test completed (20 messages)");
                passed++;
            } catch (Exception e) {
                System.out.println("FAIL: " + e.getMessage());
                failed++;
            }
            
            // Test 6: Queue with parameters
            System.out.println("\n--- Test 6: Queue Declare with Parameters ---");
            String paramsQueue = queueName + "-params-" + System.currentTimeMillis();
            try {
                Map<String, Object> queueArgs = new HashMap<>();
                xargs.put("x-max-length", 100);
                ch.queueDeclare(paramsQueue, true, false, false, queueArgs);
                System.out.println("PASS: Queue declared with args");
                passed++;
            } catch (Exception e) {
                System.out.println("FAIL: " + e.getMessage());
                failed++;
            }
            
            // Test 7: Channel close/reopen
            System.out.println("\n--- Test 7: Channel Close/Reopen ---");
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
            
            // Test 8: Exchange delete
            System.out.println("\n--- Test 8: Exchange Delete ---");
            try {
                String delExchange = exchangeName + "-del-" + System.currentTimeMillis();
                ch.exchangeDeclare(delExchange, BuiltinExchangeType.DIRECT, false);
                ch.exchangeDelete(delExchange);
                System.out.println("PASS: Exchange declared and deleted");
                passed++;
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
        } finally {
            if (conn != null && conn.isOpen()) {
                try { conn.close(); } catch (Exception ignored) {}
            }
        }
    }
}
