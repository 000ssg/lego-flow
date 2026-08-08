package ssg.legoflow.database.redis.server.impl;

import ssg.legoflow.database.redis.command.CommandArgs;
import ssg.legoflow.database.redis.command.CommandRegistry;
import ssg.legoflow.database.redis.protocol.RespType;
import ssg.legoflow.database.redis.server.ClientConnection;
import ssg.legoflow.database.redis.server.DataType;
import ssg.legoflow.database.redis.server.Database;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements Redis string commands: SET, GET, MSET, MGET, APPEND, STRLEN,
 * INCR, DECR, INCRBY, DECRBY, INCRBYFLOAT, GETSET, SETNX, SETEX, PSETEX,
 * GETRANGE, SETRANGE, GETDEL.
 *
 * @since 0.1.0
 */
public final class StringCommands {

    private static final RespType OK = new RespType.SimpleString("OK");

    private StringCommands() {}

    /**
     * Registers all string commands with the given registry.
     *
     * @param registry the command registry
     */
    public static void register(CommandRegistry registry) {
        registry.register("SET", StringCommands::set);
        registry.register("GET", StringCommands::get);
        registry.register("MSET", StringCommands::mset);
        registry.register("MGET", StringCommands::mget);
        registry.register("APPEND", StringCommands::append);
        registry.register("STRLEN", StringCommands::strlen);
        registry.register("INCR", StringCommands::incr);
        registry.register("DECR", StringCommands::decr);
        registry.register("INCRBY", StringCommands::incrby);
        registry.register("DECRBY", StringCommands::decrby);
        registry.register("INCRBYFLOAT", StringCommands::incrbyfloat);
        registry.register("GETSET", StringCommands::getset);
        registry.register("SETNX", StringCommands::setnx);
        registry.register("SETEX", StringCommands::setex);
        registry.register("PSETEX", StringCommands::psetex);
        registry.register("GETRANGE", StringCommands::getrange);
        registry.register("SETRANGE", StringCommands::setrange);
        registry.register("GETDEL", StringCommands::getdel);
    }

    private static RespType set(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        byte[] value = args.getBytes(2);

        boolean nx = false, xx = false;
        long exSeconds = -1, pxMillis = -1;
        boolean get = false;

        for (int i = 3; i < args.size(); i++) {
            String opt = args.getString(i).toUpperCase();
            switch (opt) {
                case "NX" -> nx = true;
                case "XX" -> xx = true;
                case "GET" -> get = true;
                case "EX" -> exSeconds = args.getLong(++i);
                case "PX" -> pxMillis = args.getLong(++i);
                case "EXAT" -> {
                    long ts = args.getLong(++i);
                    db.expiration().setExpiration(key, ts * 1000);
                }
                case "PXAT" -> {
                    long ts = args.getLong(++i);
                    db.expiration().setExpiration(key, ts);
                }
                case "KEEPTTL" -> {} // no-op, just don't clear TTL
                default -> {}
            }
        }

        byte[] oldValue = get ? db.getString(key) : null;

        if (nx && db.exists(key)) {
            return get ? (oldValue != null ? new RespType.BulkString(oldValue) : RespType.BulkString.NULL) : RespType.BulkString.NULL;
        }
        if (xx && !db.exists(key)) {
            return get ? RespType.BulkString.NULL : RespType.BulkString.NULL;
        }

        db.setString(key, value);
        client.server().transactionExecutor().touchKey(key);

        if (exSeconds > 0) db.expiration().setTtlSeconds(key, exSeconds);
        else if (pxMillis > 0) db.expiration().setTtlMillis(key, pxMillis);

        return get ? (oldValue != null ? new RespType.BulkString(oldValue) : RespType.BulkString.NULL) : OK;
    }

    private static RespType get(CommandArgs args, ClientConnection client) {
        byte[] value = client.database().getString(args.getString(1));
        return value != null ? new RespType.BulkString(value) : RespType.BulkString.NULL;
    }

    private static RespType mset(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        for (int i = 1; i < args.size(); i += 2) {
            String key = args.getString(i);
            byte[] value = args.getBytes(i + 1);
            db.setString(key, value);
            client.server().transactionExecutor().touchKey(key);
        }
        return OK;
    }

    private static RespType mget(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        List<RespType> results = new ArrayList<>();
        for (int i = 1; i < args.size(); i++) {
            byte[] value = db.getString(args.getString(i));
            results.add(value != null ? new RespType.BulkString(value) : RespType.BulkString.NULL);
        }
        return new RespType.Array(results);
    }

    private static RespType append(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        byte[] appendValue = args.getBytes(2);
        byte[] existing = db.getString(key);

        byte[] newValue;
        if (existing == null) {
            newValue = appendValue;
        } else {
            newValue = new byte[existing.length + appendValue.length];
            System.arraycopy(existing, 0, newValue, 0, existing.length);
            System.arraycopy(appendValue, 0, newValue, existing.length, appendValue.length);
        }
        db.setString(key, newValue);
        client.server().transactionExecutor().touchKey(key);
        return new RespType.Integer(newValue.length);
    }

    private static RespType strlen(CommandArgs args, ClientConnection client) {
        byte[] value = client.database().getString(args.getString(1));
        return new RespType.Integer(value == null ? 0 : value.length);
    }

    private static RespType incr(CommandArgs args, ClientConnection client) {
        return incrByAmount(client.database(), args.getString(1), 1, client);
    }

    private static RespType decr(CommandArgs args, ClientConnection client) {
        return incrByAmount(client.database(), args.getString(1), -1, client);
    }

    private static RespType incrby(CommandArgs args, ClientConnection client) {
        return incrByAmount(client.database(), args.getString(1), args.getLong(2), client);
    }

    private static RespType decrby(CommandArgs args, ClientConnection client) {
        return incrByAmount(client.database(), args.getString(1), -args.getLong(2), client);
    }

    private static RespType incrByAmount(Database db, String key, long amount, ClientConnection client) {
        byte[] existing = db.getString(key);
        long current = 0;
        if (existing != null) {
            try {
                current = Long.parseLong(new String(existing, StandardCharsets.UTF_8));
            } catch (NumberFormatException e) {
                return new RespType.Error("ERR", "value is not an integer or out of range");
            }
        }
        long result = current + amount;
        db.setString(key, Long.toString(result).getBytes(StandardCharsets.UTF_8));
        client.server().transactionExecutor().touchKey(key);
        return new RespType.Integer(result);
    }

    private static RespType incrbyfloat(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        double increment = args.getDouble(2);

        byte[] existing = db.getString(key);
        double current = 0;
        if (existing != null) {
            try {
                current = Double.parseDouble(new String(existing, StandardCharsets.UTF_8));
            } catch (NumberFormatException e) {
                return new RespType.Error("ERR", "value is not a valid float");
            }
        }
        double result = current + increment;
        String resultStr = formatDouble(result);
        db.setString(key, resultStr.getBytes(StandardCharsets.UTF_8));
        client.server().transactionExecutor().touchKey(key);
        return RespType.BulkString.of(resultStr);
    }

    private static RespType getset(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        byte[] oldValue = db.getString(key);
        db.setString(key, args.getBytes(2));
        client.server().transactionExecutor().touchKey(key);
        return oldValue != null ? new RespType.BulkString(oldValue) : RespType.BulkString.NULL;
    }

    private static RespType setnx(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        if (db.exists(key)) {
            return new RespType.Integer(0);
        }
        db.setString(key, args.getBytes(2));
        client.server().transactionExecutor().touchKey(key);
        return new RespType.Integer(1);
    }

    private static RespType setex(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        long seconds = args.getLong(2);
        byte[] value = args.getBytes(3);
        if (seconds <= 0) {
            return new RespType.Error("ERR", "invalid expire time in 'setex' command");
        }
        db.setString(key, value);
        db.expiration().setTtlSeconds(key, seconds);
        client.server().transactionExecutor().touchKey(key);
        return OK;
    }

    private static RespType psetex(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        long millis = args.getLong(2);
        byte[] value = args.getBytes(3);
        if (millis <= 0) {
            return new RespType.Error("ERR", "invalid expire time in 'psetex' command");
        }
        db.setString(key, value);
        db.expiration().setTtlMillis(key, millis);
        client.server().transactionExecutor().touchKey(key);
        return OK;
    }

    private static RespType getrange(CommandArgs args, ClientConnection client) {
        byte[] value = client.database().getString(args.getString(1));
        if (value == null) {
            return RespType.BulkString.of("");
        }
        int start = args.getInt(2);
        int end = args.getInt(3);
        if (start < 0) start = Math.max(0, value.length + start);
        if (end < 0) end = value.length + end;
        end = Math.min(end, value.length - 1);
        if (start > end || start >= value.length) {
            return RespType.BulkString.of("");
        }
        byte[] result = new byte[end - start + 1];
        System.arraycopy(value, start, result, 0, result.length);
        return new RespType.BulkString(result);
    }

    private static RespType setrange(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        int offset = args.getInt(2);
        byte[] patch = args.getBytes(3);
        byte[] existing = db.getString(key);
        if (existing == null) existing = new byte[0];

        int newLen = Math.max(existing.length, offset + patch.length);
        byte[] result = new byte[newLen];
        System.arraycopy(existing, 0, result, 0, existing.length);
        System.arraycopy(patch, 0, result, offset, patch.length);
        db.setString(key, result);
        client.server().transactionExecutor().touchKey(key);
        return new RespType.Integer(result.length);
    }

    private static RespType getdel(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        byte[] value = db.getString(key);
        if (value != null) {
            db.delete(key);
            client.server().transactionExecutor().touchKey(key);
        }
        return value != null ? new RespType.BulkString(value) : RespType.BulkString.NULL;
    }

    private static String formatDouble(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) {
            return String.valueOf((long) d);
        }
        return String.valueOf(d);
    }
}
