import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.ByteOrder;
import java.util.*;

public class NonBlockingTest {
    public static void main(String[] args) throws Exception {
        System.out.println("=== Non-blocking AMQP test ===");
        
        Selector selector = Selector.open();
        SocketChannel ch = SocketChannel.open();
        ch.configureBlocking(false);
        
        ch.connect(new InetSocketAddress("rabbitmq", 5672));
        System.out.println("Connecting...");
        
        // Wait for connection to complete
        while (!ch.finishConnect()) {
            Thread.sleep(10);
        }
        System.out.println("Connected");
        
        // Register for READ
        ch.register(selector, SelectionKey.OP_READ);
        
        ByteBuffer readBuf = ByteBuffer.allocate(4096);
        int totalRead = 0;
        
        // Wait up to 10 seconds for data
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            int n = selector.select(1000);
            if (n > 0) {
                readBuf.clear();
                int bytesRead = ch.read(readBuf);
                if (bytesRead == -1) {
                    System.out.println("Stream closed");
                    break;
                }
                totalRead += bytesRead;
                readBuf.flip();
                System.out.println("Read " + bytesRead + " bytes (total=" + totalRead + ")");
                
                if (totalRead >= 5) {
                    readBuf.rewind();
                    int size = readBuf.getInt();
                    byte type = readBuf.get();
                    short channel = readBuf.getShort();
                    System.out.println("Frame: size=" + size + " type=0x" + Integer.toHexString(type & 0xFF) + " channel=" + channel);
                    
                    // Need to read payload and end
                    if (totalRead >= 4 + 1 + 2 + size + 1) {
                        byte[] payload = new byte[size];
                        readBuf.get(payload);
                        byte end = readBuf.get();
                        System.out.println("End octet: 0x" + Integer.toHexString(end & 0xFF));
                        
                        // Print first 200 chars of payload
                        System.out.print("Payload: ");
                        for (int i = 0; i < Math.min(size, 200); i++) {
                            int b = payload[i] & 0xFF;
                            if (b >= 32 && b < 127) System.out.print((char)b);
                            else System.out.print('.');
                        }
                        System.out.println();
                        System.out.println("connection.start parsed successfully!");
                        System.out.println("SUCCESS!");
                    }
                    break;
                }
            }
        }
        
        if (totalRead < 5) {
            System.out.println("Timeout: only " + totalRead + " bytes received");
        }
        
        selector.close();
    }
}
