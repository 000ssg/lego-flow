import java.io.*;
import java.net.*;

public class DelayTest {
    public static void main(String[] args) throws Exception {
        System.out.println("=== DelayTest ===");
        
        Socket s = new Socket();
        s.setSoTimeout(15000);
        s.connect(new InetSocketAddress("rabbitmq", 5672), 10000);
        System.out.println("Connected");
        
        // Try different delays
        for (int delay : new int[]{0, 10, 50, 100, 500}) {
            Thread.sleep(delay);
            
            InputStream is = s.getInputStream();
            int avail = is.available();
            byte[] buf = new byte[avail > 0 ? avail : 4096];
            
            try {
                is.mark(4096);
                int n = is.read(buf);
                System.out.println("delay=" + delay + "ms available=" + avail + " read=" + n + " bytes");
                
                if (n > 0) {
                    int size = ((buf[0] & 0xFF) << 24) | ((buf[1] & 0xFF) << 16) |
                               ((buf[2] & 0xFF) << 8) | (buf[3] & 0xFF);
                    byte type = buf[4];
                    System.out.println("  Frame: size=" + size + " type=0x" + Integer.toHexString(type & 0xFF));
                    
                    if (type == 0x08) {
                        System.out.println("connection.start received!");
                        // Print payload
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < Math.min(size, 150) && 7 + i < n; i++) {
                            int b = buf[7 + i] & 0xFF;
                            if (b >= 32 && b < 127) sb.append((char)b);
                            else sb.append('.');
                        }
                        System.out.println("  Payload: " + sb.toString());
                        break;
                    }
                }
            } catch (SocketTimeoutException e) {
                System.out.println("delay=" + delay + "ms TIMEOUT");
            } catch (Exception e) {
                System.out.println("delay=" + delay + "ms ERROR: " + e.getMessage());
            }
        }
        
        s.close();
    }
}
