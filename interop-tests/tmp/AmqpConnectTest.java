import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.ByteOrder;

public class AmqpConnectTest {
    public static void main(String[] args) throws Exception {
        SocketChannel ch = SocketChannel.open();
        ch.configureBlocking(true);
        ch.connect(new InetSocketAddress("rabbitmq", 5672));
        System.out.println("Connected");
        
        ch.socket().setSoTimeout(5000);
        
        ByteBuffer buf = ByteBuffer.allocate(4096);
        buf.clear();
        int total = 0;
        while (buf.remaining() > 0) {
            int n = ch.read(buf);
            if (n == -1) break;
            total += n;
        }
        buf.flip();
        System.out.println("Read " + total + " bytes");
        
        if (total >= 7) {
            buf.rewind();
            int size = buf.getInt();
            byte type = buf.get();
            short chan = buf.getShort();
            System.out.println("size=" + size + " type=0x" + Integer.toHexString(type & 0xFF) + " chan=" + chan);
            int displayLen = Math.min(size, 200);
            for (int i = 0; i < displayLen && buf.hasRemaining(); i++) {
                System.out.print((char)buf.get());
            }
            System.out.println();
        }
        
        // Build start-ok frame
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeShort(0); dos.writeShort(9);
        dos.writeInt(131072); dos.writeShort(0); dos.writeShort(0);
        
        // SASL response for PLAIN: \0username\0password
        byte[] saslResp = new byte[] { 0, 'g', 'u', 'e', 's', 't', 0, 'g', 'u', 'e', 's', 't' };
        dos.writeInt(saslResp.length);
        dos.write(saslResp);
        
        // Locale
        byte[] locale = "en_US".getBytes();
        dos.writeInt(locale.length);
        dos.write(locale);
        dos.close();
        
        byte[] payload = baos.toByteArray();
        ByteBuffer frame = ByteBuffer.allocate(4 + 1 + 2 + payload.length + 1);
        frame.order(ByteOrder.BIG_ENDIAN);
        frame.putInt(payload.length);
        frame.put((byte)0x08); // method
        frame.putShort((short)0); // channel
        frame.put(payload);
        frame.put((byte)0xCE); // end
        frame.flip();
        
        ch.write(frame);
        System.out.println("Sent start-ok (" + payload.length + " bytes)");
        
        // Read response
        buf.clear();
        int n = ch.read(buf);
        buf.flip();
        if (n > 0) {
            System.out.println("Response: " + n + " bytes");
            System.out.println("SUCCESS!");
        } else {
            System.out.println("No response");
        }
        ch.close();
    }
}
