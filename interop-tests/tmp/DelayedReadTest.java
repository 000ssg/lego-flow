import java.io.*;
import java.net.*;

public class DelayedReadTest {
    public static void main(String[] args) throws Exception {
        System.out.println("=== DelayedReadTest ===");
        
        for (int delay : new int[]{50, 100, 200, 500, 1000}) {
            Socket s = new Socket();
            s.setSoTimeout(10000);
            try {
                s.connect(new InetSocketAddress("rabbitmq", 5672), 5000);
                System.out.println("delay=" + delay + "ms: Connected");
                
                Thread.sleep(delay);
                
                InputStream is = s.getInputStream();
                byte[] buf = new byte[4096];
                int n = is.read(buf);
                System.out.println("  Read: " + n + " bytes");
                
                if (n > 0) {
                    int size = ((buf[0] & 0xFF) << 24) | ((buf[1] & 0xFF) << 16) |
                               ((buf[2] & 0xFF) << 8) | (buf[3] & 0xFF);
                    byte type = buf[4];
                    System.out.println("  Frame: size=" + size + " type=0x" + Integer.toHexString(type & 0xFF));
                    
                    if (type == 0x08) {
                        System.out.println("  SUCCESS: Got connection.start!");
                        s.close();
                        return;
                    }
                }
            } catch (Exception e) {
                System.out.println("  delay=" + delay + "ms: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                try { s.close(); } catch (Exception ignored) {}
            }
        }
        System.out.println("FAILED");
    }
}
