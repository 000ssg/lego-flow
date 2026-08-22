package ssg.legoflow.messaging.mqtt;


/**
 * Represents an MQTT message.
 * 
 * MQTT (Message Queuing Telemetry Transport) is a lightweight publish-subscribe messaging protocol.
 * 
 * @since 0.1.0
 */
public class MqttMessage {
    
    private final int messageType;
    private final boolean dupFlag;
    private final int qosLevel;
    private final boolean retainFlag;
    private final String topic;
    private final byte[] payload;
    
    public MqttMessage(int messageType, boolean dupFlag, int qosLevel, boolean retainFlag, 
                      String topic, byte[] payload) {
        this.messageType = messageType;
        this.dupFlag = dupFlag;
        this.qosLevel = qosLevel;
        this.retainFlag = retainFlag;
        this.topic = topic != null ? topic : "";
        this.payload = payload != null ? payload.clone() : new byte[0];
    }
    
    public int getMessageType() {
        return messageType;
    }
    
    public boolean isDupFlag() {
        return dupFlag;
    }
    
    public int getQosLevel() {
        return qosLevel;
    }
    
    public boolean isRetainFlag() {
        return retainFlag;
    }
    
    public String getTopic() {
        return topic;
    }
    
    public byte[] getPayload() {
        return payload.clone();
    }
    
    public boolean hasPayload() {
        return payload != null && payload.length > 0;
    }
    
    public int getPayloadLength() {
        return payload != null ? payload.length : 0;
    }
    
    @Override
    public String toString() {
        return "MqttMessage{" +
                "messageType=" + messageType +
                ", dupFlag=" + dupFlag +
                ", qosLevel=" + qosLevel +
                ", retainFlag=" + retainFlag +
                ", topic='" + topic + '\'' +
                ", payloadLength=" + getPayloadLength() +
                '}';
    }
}
