import java.io.*;
import java.net.*;

public class SocketTest {
    public static void main(String[] args) throws Exception {
        System.out.println("=== SocketTest ===");
        
        Socket s = new Socket();
        s.setSoTimeout(15000);
        s.connect(new InetSocketAddress("rabbitmq", 5672), 10000);
        System.out.println("Connected");
        
        // Use InputStream.read() to read data
        InputStream is = s.getInputStream();
        byte[] buf = new byte[4096];
        
        // Try reading with available() check first
        System.out.println("Server available: " + is.available());
        
        // Read all available data first
        int n = is.read(buf);
        System.out.println("First read: " + n + " bytes");
        
        if (n > 0) {
            // Keep reading until no more data
            int total = n;
            int maxReads = 5;
            int readCount = 0;
            while (readCount++ < maxReads) {
                int extra = is.read(buf, total, buf.length - total);
                if (extra <= 0) break;
                total += extra;
            }
            
            System.out.println("Total read: " + total + " bytes");
            
            // Parse frame
            int size = ((buf[0] & 0xFF) << 24) | ((buf[1] & 0xFF) << 16) |
                       ((buf[2] & 0xFF) << 8) | (buf[3] & 0xFF);
            byte type = buf[4];
            short channel = (short)((buf[5] << 8) | (buf[6] & 0xFF));
            System.out.println("Frame: size=" + size + " type=0x" + Integer.toHexString(type & 0xFF) + " channel=" + channel);
            
            // Print payload
            int start = 7;
            if (total >= start + size + 1) {
                StringBuilder sb = new StringBuilder();
                for (int i = start; i < Math.min(start + size, start + 200) && i < total; i++) {
                    int b = buf[i] & 0xFF;
                    if (b >= 32 && b < 127) sb.append((char)b);
                    else sb.append('.');
                }
                System.out.println("Payload: " + sb.toString());
            }
            
            // Send start-ok
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeShort(0); dos.writeShort(9);
            dos.writeInt(131072); dos.writeShort(0); dos.writeShort(0);
            byte[] sa = new byte[] { 0, 'g', 'u', 'e', 's', 't', 0, 'g', 'u', 'e', 's', 't' };
            dos.writeInt(sa.length); dos.write(sa);
            byte[] lo = "en_US".getBytes();
            dos.writeInt(lo.length); dos.write(lo);
            dos.close();
            
            byte[] pl = baos.toByteArray();
            byte[] frame = new byte[4 + 1 + 2 + pl.length + 1];
            // Big-endian size
            frame[0] = (byte)((pl.length >> 24) & 0xFF);
            frame[1] = (byte)((pl.length >> 16) & 0xFF);
            frame[2] = (byte)((pl.length >> 8) & 0xFF);
            frame[3] = (byte)(pl.length & 0xFF);
            frame[4] = 0x08; // method
            frame[5] = 0x00; // channel
            frame[6] = 0x00;
            System.arraycopy(pl, 0, frame, 7, pl.length);
            frame[frame.length - 1] = (byte)0xCE; // end
            
            OutputStream os = s.getOutputStream();
            os.write(frame);
            os.flush();
            System.out.println("Sent start-ok (" + pl.length + " bytes)");
            
            // Read response
            int resp = is.read(buf);
            if (resp > 0) {
                System.out.println("Response: " + resp + " bytes");
                int respSize = ((buf[0] & 0xFF) << 24) | ((buf[1] & 0xFF) << 16) |
                               ((buf[2] & 0xFF) << 8) | (buf[3] & 0xFF);
                byte respType = buf[4];
                short respChan = (short)((buf[5] << 8) | (buf[6] & 0xFF));
                System.out.println("Response frame: size=" + respSize + " type=0x" + Integer.toHexString(respType & 0xFF) + " channel=" + respChan);
                System.out.println("SUCCESS!");
            } else {
                System.out.println("No response");
            }
        }
        
        s.close();
    }
}
