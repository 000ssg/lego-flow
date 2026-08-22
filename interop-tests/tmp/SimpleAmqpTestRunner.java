import java.lang.reflect.*;
import java.util.Map;

public class SimpleAmqpTestRunner {
    public static void main(String[] args) throws Exception {
        System.out.println("=== AMQP 0-9-1 Interop Test ===");
        
        Class<?> clientClass = Class.forName("ssg.legoflow.messaging.amqp091.client.Amqp091Client");
        Class<?> configClass = Class.forName("ssg.legoflow.messaging.amqp091.client.ClientConfig");
        
        System.out.println("Loaded client: " + clientClass.getName());
        System.out.println("Loaded config: " + configClass.getName());
        
        // Build config: ClientConfig.builder().host("rabbitmq").port(5672).username("guest").password("guest").build()
        Object configBuilder = configClass.getDeclaredMethod("builder").invoke(null);
        
        Method hostMethod = configBuilder.getClass().getMethod("host", String.class);
        hostMethod.invoke(configBuilder, "rabbitmq");
        
        Method portMethod = configBuilder.getClass().getMethod("port", int.class);
        portMethod.invoke(configBuilder, Integer.valueOf(5672));
        
        Method userMethod = configBuilder.getClass().getMethod("username", String.class);
        userMethod.invoke(configBuilder, "guest");
        
        Method passMethod = configBuilder.getClass().getMethod("password", String.class);
        passMethod.invoke(configBuilder, "guest");
        
        Object config = configBuilder.getClass().getMethod("build").invoke(configBuilder);
        System.out.println("Config built");
        
        // Build client
        Object clientBuilder = clientClass.getDeclaredMethod("builder").invoke(null);
        Method configMethod = clientBuilder.getClass().getMethod("config", configClass);
        configMethod.invoke(clientBuilder, config);
        
        Object client = clientBuilder.getClass().getMethod("build").invoke(clientBuilder);
        System.out.println("Client built: " + client.getClass().getName());
        
        // Connect
        client.getClass().getMethod("connect").invoke(client);
        boolean connected = (Boolean) client.getClass().getMethod("isConnected").invoke(client);
        System.out.println("Connected: " + connected);
        if (!connected) throw new RuntimeException("Not connected!");
        
        // Open channel
        int channelId = (Integer) client.getClass().getMethod("openChannel").invoke(client);
        System.out.println("Channel opened: " + channelId);
        
        // Declare exchange
        client.getClass().getMethod("declareExchange", int.class, String.class, String.class, boolean.class, boolean.class)
            .invoke(client, channelId, "interop-test-ex", "direct", false, false);
        System.out.println("Exchange declared");
        
        // Declare queue
        Object qResult = client.getClass().getMethod("declareQueue", int.class, String.class, boolean.class, boolean.class, boolean.class)
            .invoke(client, channelId, "interop-test-q", false, true, true);
        String queueName = (String) qResult.getClass().getMethod("queueName").invoke(qResult);
        System.out.println("Queue declared: " + queueName);
        
        // Publish message
        String testMessage = "test-amqp091-" + System.currentTimeMillis();
        client.getClass().getMethod("publish", int.class, String.class, String.class, byte[].class, Map.class)
            .invoke(client, channelId, "interop-test-ex", queueName, testMessage.getBytes(), null);
        System.out.println("Published: " + testMessage);
        
        // Clean up
        try { client.getClass().getMethod("closeChannel", int.class).invoke(client, channelId); } catch (Exception ignored) {}
        client.getClass().getMethod("close").invoke(client);
        
        System.out.println("=== ALL TESTS PASSED ===");
    }
}
