import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;

public class QuickTest {
    public static void main(String[] args) throws Exception {
        System.out.println("Starting test...");
        
        // Use a plain socket with a 5-second timeout
        Socket s = new Socket();
        s.setSoTimeout(5000);
        System.out.println("Connecting...");
        s.connect(new InetSocketAddress("rabbitmq", 5672), 5000);
        System.out.println("Connected");
        
        // Read immediately
        InputStream is = s.getInputStream();
        byte[] buf = new byte[4096];
        int n = is.read(buf);
        System.out.println("Read: " + n + " bytes");
        
        if (n > 0) {
            System.out.println("HEX:");
            for (int i = 0; i < Math.min(n, 100); i++) {
                System.out.print(String.format("%02x ", buf[i] & 0xFF));
            }
            System.out.println();
            
            // Parse connection.start
            int size = ((buf[0] & 0xFF) << 24) | ((buf[1] & 0xFF) << 16) |
                       ((buf[2] & 0xFF) << 8) | (buf[3] & 0xFF);
            byte type = buf[4];
            System.out.println("Frame: size=" + size + " type=0x" + Integer.toHexString(type & 0xFF));
            
            // Print payload preview
            int payloadStart = 7; // 4 + 1 + 2 (skip channel)
            if (n >= payloadStart + size + 1) {
                System.out.print("Payload: ");
                for (int i = payloadStart; i < Math.min(payloadStart + size, payloadStart + 200); i++) {
                    int b = buf[i] & 0xFF;
                    if (b >= 32 && b < 127) System.out.print((char)b);
                    else System.out.print('.');
                }
                System.out.println();
            }
            
            System.out.println("SUCCESS!");
        }
        
        s.close();
    }
}
