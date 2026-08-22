package ssg.legoflow.messaging.amqp091.client;

import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

class Amqp091GreetingTest {
    @Test
    void testRawGreetingExchange() throws Exception {
        String host = System.getProperty("amqp.debug.host", "localhost");
        int port = Integer.parseInt(System.getProperty("amqp.debug.port", "5672"));
        
        SocketChannel sc = SocketChannel.open();
        sc.configureBlocking(true);
        sc.connect(new InetSocketAddress(host, port));
        
        // Send greeting
        byte[] greeting = {(byte)0x41, (byte)0x4D, (byte)0x51, (byte)0x50, 0x00, 0x00, 0x09, 0x01};
        sc.write(ByteBuffer.wrap(greeting));
        
        // Read greeting separately
        ByteBuffer greetBuf = ByteBuffer.allocate(8);
        int offset = 0;
        while (offset < 8) {
            int n = sc.read(greetBuf);
            if (n <= 0) throw new RuntimeException("Failed to read greeting");
            offset += n;
        }
        System.out.println("Greeting bytes: " + Arrays.toString(new byte[8]));
        
        // Now read frame data
        ByteBuffer frameBuf = ByteBuffer.allocate(65536);
        int n = sc.read(frameBuf);
        System.out.println("Frame read: " + n + " bytes");
        
        if (n > 0) {
            frameBuf.flip();
            System.out.println("Position: " + frameBuf.position());
            System.out.println("Remaining: " + frameBuf.remaining());
            if (frameBuf.remaining() >= 7) {
                // Save position
                int savedPos = frameBuf.position();
                
                // Read SIZE (first 4 bytes)
                frameBuf.get(new byte[4]);
                frameBuf.position(savedPos);
                
                // Print all bytes
                byte[] all = new byte[frameBuf.remaining()];
                frameBuf.get(all);
                System.out.println("All data:");
                for (int i = 0; i < all.length && i < 60; i++) {
                    System.out.print(String.format("%02X ", all[i]));
                    if ((i+1) % 16 == 0) System.out.println();
                }
                System.out.println();
                System.out.println("First byte: 0x" + String.format("%02X", all[0]));
                System.out.println("Fifth byte (type): 0x" + String.format("%02X", all[4]));
            }
        }
        sc.close();
    }
}
