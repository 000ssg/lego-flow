import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.ByteBuffer;

public class AmqpTest3 {
    public static void main(String[] args) throws Exception {
        // Use a plain Socket with a short timeout
        Socket s = new Socket();
        s.setSoTimeout(3000);
        s.connect(new InetSocketAddress("rabbitmq", 5672), 5000);
        System.out.println("Connected");
        
        InputStream is = s.getInputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        int n;
        
        // Read in a loop until no more data
        while ((n = is.read(buf, total, buf.length - total)) > 0) {
            total += n;
            System.out.println("Read " + n + " bytes (total=" + total + ")");
        }
        
        System.out.println("Total: " + total + " bytes");
        
        if (total >= 5) {
            int size = ((buf[0] & 0xFF) << 24) | ((buf[1] & 0xFF) << 16) | 
                       ((buf[2] & 0xFF) << 8) | (buf[3] & 0xFF);
            byte type = buf[4];
            System.out.println("Frame: size=" + size + " type=0x" + Integer.toHexString(type & 0xFF));
            System.out.println("First 120 chars:");
            for (int i = 0; i < Math.min(total, 120); i++) {
                int b = buf[i] & 0xFF;
                if (b >= 32 && b < 127) System.out.print((char)b);
                else System.out.print('.');
            }
            System.out.println();
        }
        
        // Build start-ok frame
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
        // type = method
        frame[4] = 0x08;
        // channel = 0
        frame[5] = 0x00;
        frame[6] = 0x00;
        // payload
        System.arraycopy(pl, 0, frame, 7, pl.length);
        // end
        frame[frame.length - 1] = (byte)0xCE;
        
        OutputStream os = s.getOutputStream();
        os.write(frame);
        os.flush();
        System.out.println("Sent start-ok (" + frame.length + " bytes)");
        
        // Read response
        int resp = is.read(buf);
        if (resp > 0) {
            System.out.println("Response: " + resp + " bytes");
            for (int i = 0; i < Math.min(resp, 50); i++) {
                int b = buf[i] & 0xFF;
                System.out.print(String.format("%02x ", b));
            }
            System.out.println("\nSUCCESS!");
        } else {
            System.out.println("No response");
        }
        
        s.close();
    }
}
