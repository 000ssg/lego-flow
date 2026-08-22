package ssg.legoflow.database.redis.server.impl;

import ssg.legoflow.database.redis.command.CommandArgs;
import ssg.legoflow.database.redis.command.CommandRegistry;
import ssg.legoflow.database.redis.protocol.RespType;
import ssg.legoflow.database.redis.server.ClientConnection;
import ssg.legoflow.database.redis.server.Database;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
/**
 * Implements Redis hash commands: HSET, HGET, HMSET, HMGET, HDEL, HEXISTS,
 * HLEN, HKEYS, HVALS, HGETALL, HINCRBY, HINCRBYFLOAT, HSETNX.
 *
 * @since 0.1.0
 */
public final class HashCommands {

    private static final RespType OK = new RespType.SimpleString("OK");

    private HashCommands() {}

    /**
     * Registers all hash commands with the given registry.
     *
     * @param registry the command registry
     */
    public static void register(CommandRegistry registry) {
        registry.register("HSET", HashCommands::hset);
        registry.register("HGET", HashCommands::hget);
        registry.register("HMSET", HashCommands::hmset);
        registry.register("HMGET", HashCommands::hmget);
        registry.register("HDEL", HashCommands::hdel);
        registry.register("HEXISTS", HashCommands::hexists);
        registry.register("HLEN", HashCommands::hlen);
        registry.register("HKEYS", HashCommands::hkeys);
        registry.register("HVALS", HashCommands::hvals);
        registry.register("HGETALL", HashCommands::hgetall);
        registry.register("HINCRBY", HashCommands::hincrby);
        registry.register("HINCRBYFLOAT", HashCommands::hincrbyfloat);
        registry.register("HSETNX", HashCommands::hsetnx);
    }

    private static RespType hset(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        Map<String, byte[]> hash = db.getOrCreateHash(key);
        int added = 0;
        for (int i = 2; i + 1 < args.size(); i += 2) {
            String field = args.getString(i);
            byte[] value = args.getBytes(i + 1);
            if (hash.put(field, value) == null) added++;
        }
        client.server().transactionExecutor().touchKey(key);
        return new RespType.Integer(added);
    }

    private static RespType hget(CommandArgs args, ClientConnection client) {
        Map<String, byte[]> hash = client.database().getHash(args.getString(1));
        if (hash == null) return RespType.BulkString.NULL;
        byte[] value = hash.get(args.getString(2));
        return value != null ? new RespType.BulkString(value) : RespType.BulkString.NULL;
    }

    private static RespType hmset(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        Map<String, byte[]> hash = db.getOrCreateHash(key);
        for (int i = 2; i + 1 < args.size(); i += 2) {
            hash.put(args.getString(i), args.getBytes(i + 1));
        }
        client.server().transactionExecutor().touchKey(key);
        return OK;
    }

    private static RespType hmget(CommandArgs args, ClientConnection client) {
        Map<String, byte[]> hash = client.database().getHash(args.getString(1));
        List<RespType> results = new ArrayList<>();
        for (int i = 2; i < args.size(); i++) {
            byte[] value = hash != null ? hash.get(args.getString(i)) : null;
            results.add(value != null ? new RespType.BulkString(value) : RespType.BulkString.NULL);
        }
        return new RespType.Array(results);
    }

    private static RespType hdel(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        Map<String, byte[]> hash = db.getHash(key);
        if (hash == null) return new RespType.Integer(0);
        int removed = 0;
        for (int i = 2; i < args.size(); i++) {
            if (hash.remove(args.getString(i)) != null) removed++;
        }
        db.removeIfEmpty(key);
        client.server().transactionExecutor().touchKey(key);
        return new RespType.Integer(removed);
    }

    private static RespType hexists(CommandArgs args, ClientConnection client) {
        Map<String, byte[]> hash = client.database().getHash(args.getString(1));
        boolean exists = hash != null && hash.containsKey(args.getString(2));
        return new RespType.Integer(exists ? 1 : 0);
    }

    private static RespType hlen(CommandArgs args, ClientConnection client) {
        Map<String, byte[]> hash = client.database().getHash(args.getString(1));
        return new RespType.Integer(hash == null ? 0 : hash.size());
    }

    private static RespType hkeys(CommandArgs args, ClientConnection client) {
        Map<String, byte[]> hash = client.database().getHash(args.getString(1));
        if (hash == null) return new RespType.Array(List.of());
        List<RespType> result = new ArrayList<>();
        for (String field : hash.keySet()) {
            result.add(RespType.BulkString.of(field));
        }
        return new RespType.Array(result);
    }

    private static RespType hvals(CommandArgs args, ClientConnection client) {
        Map<String, byte[]> hash = client.database().getHash(args.getString(1));
        if (hash == null) return new RespType.Array(List.of());
        List<RespType> result = new ArrayList<>();
        for (byte[] value : hash.values()) {
            result.add(new RespType.BulkString(value));
        }
        return new RespType.Array(result);
    }

    private static RespType hgetall(CommandArgs args, ClientConnection client) {
        Map<String, byte[]> hash = client.database().getHash(args.getString(1));
        if (hash == null) return new RespType.Array(List.of());
        List<RespType> result = new ArrayList<>();
        for (var entry : hash.entrySet()) {
            result.add(RespType.BulkString.of(entry.getKey()));
            result.add(new RespType.BulkString(entry.getValue()));
        }
        return new RespType.Array(result);
    }

    private static RespType hincrby(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        String field = args.getString(2);
        long increment = args.getLong(3);
        Map<String, byte[]> hash = db.getOrCreateHash(key);
        byte[] existing = hash.get(field);
        long current = 0;
        if (existing != null) {
            try {
                current = Long.parseLong(new String(existing, StandardCharsets.UTF_8));
            } catch (NumberFormatException e) {
                return new RespType.Error("ERR", "hash value is not an integer");
            }
        }
        long result = current + increment;
        hash.put(field, Long.toString(result).getBytes(StandardCharsets.UTF_8));
        client.server().transactionExecutor().touchKey(key);
        return new RespType.Integer(result);
    }

    private static RespType hincrbyfloat(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        String field = args.getString(2);
        double increment = args.getDouble(3);
        Map<String, byte[]> hash = db.getOrCreateHash(key);
        byte[] existing = hash.get(field);
        double current = 0;
        if (existing != null) {
            try {
                current = Double.parseDouble(new String(existing, StandardCharsets.UTF_8));
            } catch (NumberFormatException e) {
                return new RespType.Error("ERR", "hash value is not a valid float");
            }
        }
        double result = current + increment;
        String resultStr = String.valueOf(result);
        hash.put(field, resultStr.getBytes(StandardCharsets.UTF_8));
        client.server().transactionExecutor().touchKey(key);
        return RespType.BulkString.of(resultStr);
    }

    private static RespType hsetnx(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        String field = args.getString(2);
        Map<String, byte[]> hash = db.getOrCreateHash(key);
        if (hash.containsKey(field)) return new RespType.Integer(0);
        hash.put(field, args.getBytes(3));
        client.server().transactionExecutor().touchKey(key);
        return new RespType.Integer(1);
    }
}
