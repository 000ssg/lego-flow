package ssg.legoflow.database.redis;

import java.util.List;
import java.util.ArrayList;

/**
 * Represents a Redis command.
 * 
 * Redis commands are sent as RESP (Redis Serialization Protocol) arrays.
 * 
 * @since 0.1.0
 */
public class RedisCommand {
    
    private final String command;
    private final List<String> arguments;
    
    public RedisCommand(String command, List<String> arguments) {
        this.command = command != null ? command.toUpperCase() : "";
        this.arguments = arguments != null ? new ArrayList<>(arguments) : new ArrayList<>();
    }
    
    public String getCommand() {
        return command;
    }
    
    public List<String> getArguments() {
        return arguments;
    }
    
    public int getArgumentCount() {
        return arguments.size();
    }
    
    @Override
    public String toString() {
        return "RedisCommand{" +
                "command='" + command + '\'' +
                ", arguments=" + arguments +
                '}';
    }
}
