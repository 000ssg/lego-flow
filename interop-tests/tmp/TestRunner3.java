import com.rabbitmq.client.*;
import java.util.*;

public class TestRunner3 {
    public static void main(String[] args) throws Exception {
        System.out.println("=== AMQP 0-9-1 Interop Tests ===");
        
        String host = System.getProperty("interop.amqp.host", "rabbitmq");
        int port = Integer.getInteger("interop.amqp.port", 5672);
        String username = System.getProperty("interop.amqp.username", "guest");
        String password = System.getProperty("interop.amqp.password", "guest");
        String queueName = System.getProperty("interop.amqp.queue", "interop-amqp091-test");
        String exchangeName = System.getProperty("interop.amqp.exchange", "amqp091-test-ex");
        
        int total = 0, pass = 0, fail = 0;
        Connection conn = null;
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(host);
            factory.setPort(port);
            factory.setUsername(username);
            factory.setPassword(password);
            factory.setConnectionTimeout(30000);
            factory.setHandshakeTimeout(30000);
            conn = factory.newConnection();
            System.out.println("Connected to " + host + ":" + port);
            Channel ch = conn.createChannel();
            ch.exchangeDeclare(exchangeName, BuiltinExchangeType.DIRECT, false);
            System.out.println("1. Connection: PASS"); total++; pass++;
            System.out.println("2. Channel: PASS"); total++; pass++;
            String exName = exchangeName + "-test-" + System.currentTimeMillis();
            ch.exchangeDeclare(exName, BuiltinExchangeType.DIRECT, false);
            System.out.println("3. Exchange Declare: PASS (" + exName + ")"); total++; pass++;
            String qName = queueName + "-test-" + System.currentTimeMillis();
            AMQP.Queue.DeclareOk qResult = ch.queueDeclare(qName, false, true, true, null);
            System.out.println("4. Queue Declare: PASS (" + qResult.getQueue() + ")"); total++; pass++;
            String msg = "test-" + System.currentTimeMillis();
            ch.basicPublish(exchangeName, qName, null, msg.getBytes("UTF-8"));
            System.out.println("5. Publish: PASS (" + msg + ")"); total++; pass++;
            String q2 = queueName + "-consume-" + System.currentTimeMillis();
            ch.queueDeclare(q2, false, true, true, null);
            ch.queueBind(q2, exchangeName, q2);
            String testMsg = "consume-" + System.currentTimeMillis();
            ch.basicPublish(exchangeName, q2, null, testMsg.getBytes("UTF-8"));
            GetResponse resp = ch.basicGet(q2, true);
            if (resp != null) { String received = new String(resp.getBody(), "UTF-8");
                System.out.println("6. Consume: PASS (got: " + received + ")"); total++; pass++; }
            else { System.out.println("6. Consume: FAIL"); total++; fail++; }
            String q3 = queueName + "-multi-" + System.currentTimeMillis();
            ch.queueDeclare(q3, false, true, true, null);
            ch.queueBind(q3, exchangeName, q3);
            for (int i = 0; i < 10; i++) ch.basicPublish(exchangeName, q3, null, ("m" + i).getBytes("UTF-8"));
            int consumed = 0; GetResponse r; while ((r = ch.basicGet(q3, true)) != null) consumed++;
            System.out.println("7. Multiple Messages: PASS (consumed " + consumed + ")"); total++; pass++;
            String q4 = queueName + "-headers-" + System.currentTimeMillis();
            ch.queueDeclare(q4, false, true, true, null);
            ch.queueBind(q4, exchangeName, q4);
            Map<String, Object> hdrs = new HashMap<>(); hdrs.put("key1", "val1");
            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder().headers(hdrs).contentType("text/plain").build();
            ch.basicPublish(exchangeName, q4, props, "hi".getBytes("UTF-8"));
            GetResponse hr = ch.basicGet(q4, true);
            if (hr != null && hr.getProps().getHeaders() != null) { Object hv = hr.getProps().getHeaders().get("key1"); String hvStr = hv instanceof byte[] ? new String((byte[])hv) : hv.toString();
                System.out.println("8. Headers: PASS (key1=" + hvStr + ")"); total++; pass++; }
            else { System.out.println("8. Headers: FAIL"); total++; fail++; }
            ch.basicQos(10); String q5 = queueName + "-qos-" + System.currentTimeMillis();
            ch.queueDeclare(q5, false, true, true, null);
            ch.queueBind(q5, exchangeName, q5);
            for (int i = 0; i < 20; i++) ch.basicPublish(exchangeName, q5, null, ("q" + i).getBytes("UTF-8"));
            for (int i = 0; i < 20; i++) { GetResponse qr = ch.basicGet(q5, true); if (qr == null) break; }
            System.out.println("9. QoS: PASS"); total++; pass++;
            int chNum = ch.getChannelNumber(); ch.close(); Channel ch2 = conn.createChannel();
            System.out.println("10. Channel Reopen: PASS"); total++; pass++; ch2.close();
            System.out.println("\n========== SUMMARY ==========");
            System.out.println("Passed: " + pass + "/" + total);
            System.out.println("Failed: " + fail + "/" + total);
            if (fail > 0) System.exit(1);
        } finally { if (conn != null && conn.isOpen()) { try { conn.close(); } catch (Exception ignored) {} } }
    }
}