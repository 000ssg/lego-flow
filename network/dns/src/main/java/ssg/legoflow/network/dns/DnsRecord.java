package ssg.legoflow.network.dns;

/**
 * Represents a DNS resource record.
 * 
 * @since 0.1.0
 */
public class DnsRecord {
    
    private final String name;
    private final int type;
    private final int clazz;
    private final int ttl;
    private final byte[] rdata;
    
    public DnsRecord(String name, int type, int clazz, int ttl, byte[] rdata) {
        this.name = name != null ? name : "";
        this.type = type;
        this.clazz = clazz;
        this.ttl = ttl;
        this.rdata = rdata != null ? rdata.clone() : new byte[0];
    }
    
    public String getName() {
        return name;
    }
    
    public int getType() {
        return type;
    }
    
    public int getClazz() {
        return clazz;
    }
    
    public int getTtl() {
        return ttl;
    }
    
    public byte[] getRdata() {
        return rdata.clone();
    }
    
    @Override
    public String toString() {
        return "DnsRecord{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", ttl=" + ttl +
                '}';
    }
}
