package ssg.legoflow.messaging.amqp091.transport;
import java.io.*;

public class TransportTest {
    public static void main(String[] args) throws Exception {
        System.out.println("Creating transport...");
        SocketAmqp091Transport transport = new SocketAmqp091Transport("localhost", 5672, 10000, 0);
        
        System.out.println("Opening transport...");
        transport.open();
        System.out.println("Transport open: " + transport.isOpen());
        
        System.out.println("Getting streams...");
        DataInputStream in = transport.getInputStream();
        DataOutputStream out = transport.getOutputStream();
        
        System.out.println("Writing 8-byte version string...");
        byte[] greeting = new byte[]{0x41, 0x4D, 0x51, 0x50, 0x00, 0x00, 0x09, 0x01};
        out.write(greeting);
        out.flush();
        System.out.println("Version string written and flushed");
        
        System.out.println("Waiting for server response...");
        byte[] hdr = new byte[7];
        in.readFully(hdr);
        System.out.println("Read header: " + hex(hdr, 7));
        
        byte ft = hdr[0];
        int ch = ((hdr[1]&0xFF)<<8)|(hdr[2]&0xFF);
        int sz = ((hdr[3]&0xFF)<<24)|((hdr[4]&0xFF)<<16)|((hdr[5]&0xFF)<<8)|(hdr[6]&0xFF);
        System.out.println("Frame: type=0x"+Integer.toHexString(ft&0xFF)+" chan="+ch+" size="+sz);
        
        byte[] payload = new byte[sz];
        in.readFully(payload);
        byte end = (byte)in.read();
        System.out.println("End: 0x"+Integer.toHexString(end&0xFF));
        System.out.println("Payload ("+sz+"B): " + hex(payload, Math.min(sz, 40)));
        System.out.println("\n=== SUCCESS ===");
        
        transport.close();
    }
    
    static String hex(byte[] data, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(data.length, max); i++)
            sb.append(String.format("%02x ", data[i] & 0xFF));
        return sb.toString().trim();
    }
}
