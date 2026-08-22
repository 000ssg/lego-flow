import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.ByteOrder;
import java.util.*;

public class AmqpConnectTest2 {
    public static void main(String[] args) throws Exception {
        Selector selector = Selector.open();
        SocketChannel ch = SocketChannel.open();
        ch.configureBlocking(false);
        
        ch.connect(new InetSocketAddress("rabbitmq", 5672));
        System.out.println("Initiating connect...");
        
        // Wait for connect to complete
        while (!ch.finishConnect()) {
            Set<SelectionKey> keys = selector.selectNow();
            Thread.sleep(10);
        }
        System.out.println("Connected!");
        
        // Read incoming data
        ch.register(selector, SelectionKey.OP_READ);
        ByteBuffer buf = ByteBuffer.allocate(8192);
        
        while (ch.isOpen()) {
            int n = selector.select(5000);
            if (n == 0) {
                System.out.println("Timeout");
                break;
            }
            
            Set<SelectionKey> ready = selector.selectedKeys();
            for (SelectionKey key : ready) {
                if (key.isReadable()) {
                    int bytesRead = ch.read(buf);
                    if (bytesRead == -1) {
                        System.out.println("Stream closed");
                        ch.close();
                        return;
                    }
                    buf.flip();
                    System.out.println("Read " + bytesRead + " bytes");
                    StringBuilder hex = new StringBuilder();
                    for (int i = 0; i < Math.min(bytesRead, 80) && buf.hasRemaining(); i++) {
                        int b = buf.get();
                        hex.append(String.format("%02x ", b));
                    }
                    System.out.println("HEX: " + hex);
                    
                    // Check frame header
                    if (bytesRead >= 5) {
                        buf.rewind();
                        int size = buf.getInt();
                        byte type = buf.get();
                        System.out.println("Frame header: size=" + size + " type=0x" + Integer.toHexString(type & 0xFF));
                    }
                    buf.clear();
                    
                    // Send connection.start-ok
                    if (totalRead >= 8) { // after reading enough
                        sendStartOk(ch);
                    }
                }
            }
            ready.clear();
            break; // just one read
        }
        
        ch.close();
        selector.close();
    }
    
    static int totalRead = 0;
    
    static void sendStartOk(SocketChannel ch) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeShort(0); dos.writeShort(9);
        dos.writeInt(131072); dos.writeShort(0); dos.writeShort(0);
        
        byte[] saslResp = new byte[] { 0, 'g', 'u', 'e', 's', 't', 0, 'g', 'u', 'e', 's', 't' };
        dos.writeInt(saslResp.length);
        dos.write(saslResp);
        
        byte[] locale = "en_US".getBytes();
        dos.writeInt(locale.length);
        dos.write(locale);
        dos.close();
        
        byte[] payload = baos.toByteArray();
        ByteBuffer frame = ByteBuffer.allocate(4 + 1 + 2 + payload.length + 1);
        frame.order(ByteOrder.BIG_ENDIAN);
        frame.putInt(payload.length);
        frame.put((byte)0x08);
        frame.putShort((short)0);
        frame.put(payload);
        frame.put((byte)0xCE);
        frame.flip();
        
        ch.write(frame);
        System.out.println("Sent start-ok (" + payload.length + " bytes)");
    }
}
