package ssg.legoflow.messaging.stomp;

import java.util.Map;
import java.util.HashMap;

/**
 * Represents a STOMP (Simple Text Oriented Messaging Protocol) message.
 * 
 * STOMP is a simple text-oriented messaging protocol for message brokers.
 * 
 * @since 0.1.0
 */
public class StompMessage {
    
    private final String command;
    private final Map<String, String> headers;
    private final byte[] body;
    
    public StompMessage(String command, Map<String, String> headers, byte[] body) {
        this.command = command;
        this.headers = headers != null ? new HashMap<>(headers) : new HashMap<>();
        this.body = body != null ? body.clone() : new byte[0];
    }
    
    public String getCommand() {
        return command;
    }
    
    public Map<String, String> getHeaders() {
        return headers;
    }
    
    public byte[] getBody() {
        return body.clone();
    }
    
    public boolean hasBody() {
        return body != null && body.length > 0;
    }
    
    public int getBodyLength() {
        return body != null ? body.length : 0;
    }
    
    @Override
    public String toString() {
        return "StompMessage{" +
                "command='" + command + '\'' +
                ", headers=" + headers +
                ", bodyLength=" + getBodyLength() +
                '}';
    }
}
