import com.rabbitmq.client.*;

public class RabbitTest {
    public static void main(String[] args) throws Exception {
        System.out.println("Creating factory...");
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("rabbitmq");
        factory.setPort(5672);
        factory.setUsername("guest");
        factory.setPassword("guest");
        factory.setConnectionTimeout(10000);
        factory.setHandshakeTimeout(10000);
        factory.setNetworkRecoveryInterval(1000);
        
        System.out.println("Connecting...");
        Connection conn = factory.newConnection();
        System.out.println("Connected: " + conn);
        
        System.out.println("Creating channel...");
        Channel ch = conn.createChannel();
        System.out.println("Channel created");
        
        // Declare exchange and queue
        String exchange = "test-ex";
        String queue = "test-queue";
        ch.exchangeDeclare(exchange, BuiltinExchangeType.DIRECT, false);
        String qName = ch.queueDeclare(queue, false, true, true, null).getQueue();
        System.out.println("Declared queue: " + qName);
        
        // Bind queue to exchange
        ch.queueBind(qName, exchange, qName);
        System.out.println("Bound queue to exchange");
        
        // Publish message
        String msg = "hello amqp 091 test";
        ch.basicPublish(exchange, qName, null, msg.getBytes());
        System.out.println("Published: " + msg);
        
        // Consume message
        System.out.println("Consuming...");
        final String[] received = new String[1];
        GetResponse response = ch.basicGet(qName, false);
        if (response != null) {
            received[0] = new String(response.getBody());
            ch.basicAck(response.getEnvelope().getDeliveryTag(), false);
            System.out.println("Received: " + received[0]);
        } else {
            System.out.println("No message received");
        }
        
        ch.close();
        conn.close();
        System.out.println("SUCCESS!");
    }
}
