package ssg.legoflow.database.redis.server.impl;

import ssg.legoflow.database.redis.command.CommandArgs;
import ssg.legoflow.database.redis.command.CommandRegistry;
import ssg.legoflow.database.redis.protocol.RespType;
import ssg.legoflow.database.redis.server.ClientConnection;
import ssg.legoflow.database.redis.server.Database;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Implements Redis key commands: DEL, EXISTS, EXPIRE, EXPIREAT, PEXPIRE,
 * TTL, PTTL, PERSIST, TYPE, KEYS, SCAN, RENAME, RANDOMKEY, UNLINK.
 *
 * @since 1.0.0
 */
public final class KeyCommands {

    private static final RespType OK = new RespType.SimpleString("OK");

    private KeyCommands() {}

    /**
     * Registers all key commands with the given registry.
     *
     * @param registry the command registry
     */
    public static void register(CommandRegistry registry) {
        registry.register("DEL", KeyCommands::del);
        registry.register("EXISTS", KeyCommands::exists);
        registry.register("EXPIRE", KeyCommands::expire);
        registry.register("EXPIREAT", KeyCommands::expireat);
        registry.register("PEXPIRE", KeyCommands::pexpire);
        registry.register("TTL", KeyCommands::ttl);
        registry.register("PTTL", KeyCommands::pttl);
        registry.register("PERSIST", KeyCommands::persist);
        registry.register("TYPE", KeyCommands::type);
        registry.register("KEYS", KeyCommands::keys);
        registry.register("SCAN", KeyCommands::scan);
        registry.register("RENAME", KeyCommands::rename);
        registry.register("RANDOMKEY", KeyCommands::randomkey);
        registry.register("UNLINK", KeyCommands::unlink);
    }

    private static RespType del(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        int deleted = 0;
        for (int i = 1; i < args.size(); i++) {
            String key = args.getString(i);
            if (db.delete(key)) {
                deleted++;
                client.server().transactionExecutor().touchKey(key);
            }
        }
        return new RespType.Integer(deleted);
    }

    private static RespType exists(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        int count = 0;
        for (int i = 1; i < args.size(); i++) {
            if (db.exists(args.getString(i))) count++;
        }
        return new RespType.Integer(count);
    }

    private static RespType expire(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        if (!db.exists(key)) return new RespType.Integer(0);
        long seconds = args.getLong(2);
        db.expiration().setTtlSeconds(key, seconds);
        return new RespType.Integer(1);
    }

    private static RespType expireat(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        if (!db.exists(key)) return new RespType.Integer(0);
        long timestamp = args.getLong(2);
        db.expiration().setExpiration(key, timestamp * 1000);
        return new RespType.Integer(1);
    }

    private static RespType pexpire(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        if (!db.exists(key)) return new RespType.Integer(0);
        long millis = args.getLong(2);
        db.expiration().setTtlMillis(key, millis);
        return new RespType.Integer(1);
    }

    private static RespType ttl(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        if (!db.exists(key) && !db.expiration().hasExpiration(key)) {
            return new RespType.Integer(-2);
        }
        return new RespType.Integer(db.expiration().ttlSeconds(key));
    }

    private static RespType pttl(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        if (!db.exists(key) && !db.expiration().hasExpiration(key)) {
            return new RespType.Integer(-2);
        }
        return new RespType.Integer(db.expiration().ttlMillis(key));
    }

    private static RespType persist(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        return new RespType.Integer(db.expiration().persist(key) ? 1 : 0);
    }

    private static RespType type(CommandArgs args, ClientConnection client) {
        return new RespType.SimpleString(client.database().type(args.getString(1)).typeName());
    }

    private static RespType keys(CommandArgs args, ClientConnection client) {
        Set<String> matched = client.database().keys(args.getString(1));
        List<RespType> result = new ArrayList<>();
        for (String key : matched) {
            result.add(RespType.BulkString.of(key));
        }
        return new RespType.Array(result);
    }

    private static RespType scan(CommandArgs args, ClientConnection client) {
        int cursor = args.getInt(1);
        String pattern = null;
        int count = 10;
        for (int i = 2; i < args.size(); i++) {
            String opt = args.getString(i).toUpperCase();
            if ("MATCH".equals(opt)) {
                pattern = args.getString(++i);
            } else if ("COUNT".equals(opt)) {
                count = args.getInt(++i);
            }
        }

        Database.ScanResult result = client.database().scan(cursor, pattern, count);
        List<RespType> keys = new ArrayList<>();
        for (String key : result.keys()) {
            keys.add(RespType.BulkString.of(key));
        }
        return new RespType.Array(List.of(
                RespType.BulkString.of(String.valueOf(result.cursor())),
                new RespType.Array(keys)));
    }

    private static RespType rename(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String oldKey = args.getString(1);
        String newKey = args.getString(2);
        if (!db.rename(oldKey, newKey)) {
            return new RespType.Error("ERR", "no such key");
        }
        client.server().transactionExecutor().touchKey(oldKey);
        client.server().transactionExecutor().touchKey(newKey);
        return OK;
    }

    private static RespType randomkey(CommandArgs args, ClientConnection client) {
        String key = client.database().randomKey();
        return key != null ? RespType.BulkString.of(key) : RespType.BulkString.NULL;
    }

    private static RespType unlink(CommandArgs args, ClientConnection client) {
        // Same as DEL in our in-memory implementation
        return del(args, client);
    }
}
