package ssg.legoflow.network.dns;

/**
 * Represents a DNS question section entry.
 * 
 * @since 0.1.0
 */
public class DnsQuestion {
    
    private final String name;
    private final int type;
    private final int clazz;
    
    public DnsQuestion(String name, int type, int clazz) {
        this.name = name != null ? name : "";
        this.type = type;
        this.clazz = clazz;
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
    
    @Override
    public String toString() {
        return "DnsQuestion{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", clazz=" + clazz +
                '}';
    }
}
