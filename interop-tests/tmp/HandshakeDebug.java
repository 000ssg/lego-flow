import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.ByteOrder;

public class HandshakeDebug {
    public static void main(String[] args) throws Exception {
        SocketChannel ch = SocketChannel.open();
        ch.configureBlocking(true);
        ch.socket().setSoTimeout(15000);
        ch.connect(new InetSocketAddress("rabbitmq", 5672));
        System.out.println("Connected");
        
        // Read using the same logic as readFrameFromBuffer
        ByteBuffer readBuffer = ByteBuffer.allocate(65536);
        
        for (int attempt = 0; attempt < 5; attempt++) {
            System.out.println("Read attempt " + attempt);
            
            if (readBuffer.position() > 0) {
                readBuffer.compact();
            } else {
                readBuffer.clear();
            }
            
            int bytesRead = ch.read(readBuffer);
            System.out.println("  read() returned: " + bytesRead);
            
            if (bytesRead == -1) {
                System.out.println("  Stream closed");
                break;
            }
            if (bytesRead == 0) {
                System.out.println("  Read 0 bytes, returning");
                return;
            }
            
            readBuffer.flip();
            System.out.println("  Buffer remaining: " + readBuffer.remaining());
            
            if (readBuffer.remaining() >= 7) {
                // Parse frame header
                int savedPos = readBuffer.position();
                int size = readBuffer.getInt();
                byte type = readBuffer.get();
                short channel = readBuffer.getShort();
                System.out.println("  Frame: size=" + size + " type=0x" + Integer.toHexString(type & 0xFF) + " channel=" + channel);
                readBuffer.position(savedPos);
                
                if (readBuffer.remaining() >= 4 + 1 + 2 + size + 1) {
                    // Full frame available
                    readBuffer.getShort(); // skip channel
                    byte[] payload = new byte[size];
                    readBuffer.get(payload);
                    byte end = readBuffer.get();
                    System.out.println("  End octet: 0x" + Integer.toHexString(end & 0xFF));
                    System.out.println("  Payload preview:");
                    int previewLen = Math.min(size, 100);
                    for (int i = 0; i < previewLen; i++) {
                        int b = payload[i] & 0xFF;
                        if (b >= 32 && b < 127) System.out.print((char)b);
                        else System.out.print('.');
                    }
                    System.out.println();
                    System.out.println("  connection.start received!");
                    
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
                    ByteBuffer frame = ByteBuffer.allocate(4 + 1 + 2 + pl.length + 1);
                    frame.order(ByteOrder.BIG_ENDIAN);
                    frame.putInt(pl.length);
                    frame.put((byte)0x08);
                    frame.putShort((short)0);
                    frame.put(pl);
                    frame.put((byte)0xCE);
                    frame.flip();
                    
                    ch.write(frame);
                    System.out.println("  Sent start-ok (" + pl.length + " bytes)");
                    
                    // Read response (connection.tune)
                    readBuffer.clear();
                    int respBytes = ch.read(readBuffer);
                    System.out.println("  Response read: " + respBytes + " bytes");
                    if (respBytes > 0) {
                        readBuffer.flip();
                        int respSize = readBuffer.getInt();
                        byte respType = readBuffer.get();
                        System.out.println("  Response: size=" + respSize + " type=0x" + Integer.toHexString(respType & 0xFF));
                        System.out.println("  SUCCESS: Handshake working!");
                    } else {
                        System.out.println("  No response from server");
                    }
                    break;
                }
            }
        }
        
        ch.close();
    }
}
