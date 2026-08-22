import com.rabbitmq.client.*;

public class OfficialClientTest {
    public static void main(String[] args) throws Exception {
        System.out.println("=== OfficialClientTest ===");
        
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("rabbitmq");
        factory.setPort(5672);
        factory.setUsername("guest");
        factory.setPassword("guest");
        factory.setConnectionTimeout(30000);
        factory.setHandshakeTimeout(30000);
        factory.setRequestedHeartbeat(60);
        
        System.out.println("Creating connection...");
        Connection conn = factory.newConnection();
        System.out.println("Connected: " + conn.getAddress() + ":" + conn.getPort());
        
        Channel ch = conn.createChannel();
        System.out.println("Channel created");
        
        String exchange = "test-ex";
        String queue = "test-q-" + System.currentTimeMillis();
        ch.exchangeDeclare(exchange, BuiltinExchangeType.DIRECT, false);
        String qName = ch.queueDeclare(queue, false, true, true, null).getQueue();
        ch.queueBind(qName, exchange, qName);
        
        String msg = "hello from official client";
        ch.basicPublish(exchange, qName, null, msg.getBytes());
        System.out.println("Published: " + msg);
        
        GetResponse resp = ch.basicGet(qName, false);
        if (resp != null) {
            System.out.println("Received: " + new String(resp.getBody()));
            ch.basicAck(resp.getEnvelope().getDeliveryTag(), false);
        }
        
        ch.close();
        conn.close();
        System.out.println("SUCCESS!");
    }
}
