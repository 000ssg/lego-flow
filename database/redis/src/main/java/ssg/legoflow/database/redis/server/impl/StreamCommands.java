package ssg.legoflow.database.redis.server.impl;

import ssg.legoflow.database.redis.command.CommandArgs;
import ssg.legoflow.database.redis.command.CommandRegistry;
import ssg.legoflow.database.redis.protocol.RespType;
import ssg.legoflow.database.redis.server.ClientConnection;
import ssg.legoflow.database.redis.server.Database;
import ssg.legoflow.database.redis.server.StreamStore;
import java.util.*;
/**
 * Implements Redis stream commands: XADD, XLEN, XRANGE, XREVRANGE, XREAD,
 * XGROUP, XREADGROUP, XACK, XPENDING, XCLAIM, XTRIM.
 *
 * @since 0.1.0
 */
public final class StreamCommands {

    private static final RespType OK = new RespType.SimpleString("OK");

    private StreamCommands() {}

    /**
     * Registers all stream commands with the given registry.
     *
     * @param registry the command registry
     */
    public static void register(CommandRegistry registry) {
        registry.register("XADD", StreamCommands::xadd);
        registry.register("XLEN", StreamCommands::xlen);
        registry.register("XRANGE", StreamCommands::xrange);
        registry.register("XREVRANGE", StreamCommands::xrevrange);
        registry.register("XREAD", StreamCommands::xread);
        registry.register("XGROUP", StreamCommands::xgroup);
        registry.register("XREADGROUP", StreamCommands::xreadgroup);
        registry.register("XACK", StreamCommands::xack);
        registry.register("XPENDING", StreamCommands::xpending);
        registry.register("XCLAIM", StreamCommands::xclaim);
        registry.register("XTRIM", StreamCommands::xtrim);
    }

    private static RespType xadd(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        StreamStore stream = db.getOrCreateStream(key);

        // Parse optional MAXLEN/MINID and ID
        int i = 2;
        long maxLen = -1;
        while (i < args.size()) {
            String opt = args.getString(i).toUpperCase();
            if ("MAXLEN".equals(opt)) {
                i++;
                if ("~".equals(args.getString(i))) i++; // approximate
                maxLen = args.getLong(i);
                i++;
            } else if ("NOMKSTREAM".equals(opt)) {
                i++;
            } else {
                break;
            }
        }

        String id = args.getString(i++);
        Map<String, String> fields = new LinkedHashMap<>();
        while (i + 1 < args.size()) {
            fields.put(args.getString(i), args.getString(i + 1));
            i += 2;
        }

        String entryId = stream.add(id, fields);
        if (maxLen > 0) stream.trim(maxLen);
        client.server().transactionExecutor().touchKey(key);
        return RespType.BulkString.of(entryId);
    }

    private static RespType xlen(CommandArgs args, ClientConnection client) {
        StreamStore stream = client.database().getStream(args.getString(1));
        return new RespType.Integer(stream == null ? 0 : stream.length());
    }

    private static RespType xrange(CommandArgs args, ClientConnection client) {
        StreamStore stream = client.database().getStream(args.getString(1));
        if (stream == null) return new RespType.Array(List.of());

        String start = args.getString(2);
        String end = args.getString(3);
        int count = 0;
        if (args.size() > 5 && "COUNT".equalsIgnoreCase(args.getString(4))) {
            count = args.getInt(5);
        }

        List<StreamStore.StreamEntry> entries = stream.range(start, end, count);
        return encodeStreamEntries(entries);
    }

    private static RespType xrevrange(CommandArgs args, ClientConnection client) {
        StreamStore stream = client.database().getStream(args.getString(1));
        if (stream == null) return new RespType.Array(List.of());

        String end = args.getString(2);
        String start = args.getString(3);
        int count = 0;
        if (args.size() > 5 && "COUNT".equalsIgnoreCase(args.getString(4))) {
            count = args.getInt(5);
        }

        List<StreamStore.StreamEntry> entries = stream.revRange(end, start, count);
        return encodeStreamEntries(entries);
    }

    private static RespType xread(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        int count = 0;
        int i = 1;

        while (i < args.size()) {
            String opt = args.getString(i).toUpperCase();
            if ("COUNT".equals(opt)) { count = args.getInt(++i); i++; }
            else if ("BLOCK".equals(opt)) { i += 2; } // blocking not implemented
            else if ("STREAMS".equals(opt)) { i++; break; }
            else { i++; }
        }

        int numStreams = (args.size() - i) / 2;
        List<String> keys = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int j = 0; j < numStreams; j++) {
            keys.add(args.getString(i + j));
        }
        for (int j = 0; j < numStreams; j++) {
            ids.add(args.getString(i + numStreams + j));
        }

        List<RespType> results = new ArrayList<>();
        for (int j = 0; j < keys.size(); j++) {
            StreamStore stream = db.getStream(keys.get(j));
            if (stream == null) continue;
            List<StreamStore.StreamEntry> entries = stream.read(ids.get(j), count);
            if (!entries.isEmpty()) {
                results.add(new RespType.Array(List.of(
                        RespType.BulkString.of(keys.get(j)),
                        encodeStreamEntries(entries))));
            }
        }
        return results.isEmpty() ? RespType.Array.NULL : new RespType.Array(results);
    }

    private static RespType xgroup(CommandArgs args, ClientConnection client) {
        String subcommand = args.getString(1).toUpperCase();
        return switch (subcommand) {
            case "CREATE" -> {
                Database db = client.database();
                String key = args.getString(2);
                String groupName = args.getString(3);
                String id = args.getString(4);
                boolean mkstream = args.size() > 5 && "MKSTREAM".equalsIgnoreCase(args.getString(5));
                StreamStore stream = mkstream ? db.getOrCreateStream(key) : db.getStream(key);
                if (stream == null) {
                    yield new RespType.Error("ERR", "The XGROUP subcommand requires the key to exist");
                }
                stream.createGroup(groupName, id);
                yield OK;
            }
            case "DESTROY" -> {
                StreamStore stream = client.database().getStream(args.getString(2));
                if (stream == null) yield new RespType.Integer(0);
                yield new RespType.Integer(stream.destroyGroup(args.getString(3)) ? 1 : 0);
            }
            case "DELCONSUMER" -> {
                StreamStore stream = client.database().getStream(args.getString(2));
                if (stream == null) yield new RespType.Integer(0);
                yield new RespType.Integer(stream.deleteConsumer(args.getString(3), args.getString(4)));
            }
            default -> new RespType.Error("ERR", "unknown XGROUP subcommand '" + subcommand + "'");
        };
    }

    private static RespType xreadgroup(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        // XREADGROUP GROUP group consumer [COUNT count] [BLOCK ms] STREAMS key [key...] id [id...]
        String group = null, consumer = null;
        int count = 0;
        int i = 1;

        while (i < args.size()) {
            String opt = args.getString(i).toUpperCase();
            if ("GROUP".equals(opt)) {
                group = args.getString(++i);
                consumer = args.getString(++i);
                i++;
            } else if ("COUNT".equals(opt)) { count = args.getInt(++i); i++; }
            else if ("BLOCK".equals(opt)) { i += 2; }
            else if ("STREAMS".equals(opt)) { i++; break; }
            else { i++; }
        }

        int numStreams = (args.size() - i) / 2;
        List<String> keys = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int j = 0; j < numStreams; j++) {
            keys.add(args.getString(i + j));
        }
        for (int j = 0; j < numStreams; j++) {
            ids.add(args.getString(i + numStreams + j));
        }

        List<RespType> results = new ArrayList<>();
        for (int j = 0; j < keys.size(); j++) {
            StreamStore stream = db.getStream(keys.get(j));
            if (stream == null) continue;
            List<StreamStore.StreamEntry> entries = stream.readGroup(group, consumer, count, ids.get(j));
            if (!entries.isEmpty()) {
                results.add(new RespType.Array(List.of(
                        RespType.BulkString.of(keys.get(j)),
                        encodeStreamEntries(entries))));
            }
        }
        return results.isEmpty() ? RespType.Array.NULL : new RespType.Array(results);
    }

    private static RespType xack(CommandArgs args, ClientConnection client) {
        StreamStore stream = client.database().getStream(args.getString(1));
        if (stream == null) return new RespType.Integer(0);
        String group = args.getString(2);
        String[] ids = new String[args.size() - 3];
        for (int i = 3; i < args.size(); i++) {
            ids[i - 3] = args.getString(i);
        }
        return new RespType.Integer(stream.acknowledge(group, ids));
    }

    private static RespType xpending(CommandArgs args, ClientConnection client) {
        StreamStore stream = client.database().getStream(args.getString(1));
        if (stream == null) return new RespType.Array(List.of());
        String group = args.getString(2);
        Collection<StreamStore.PendingEntry> pending = stream.pending(group);

        // Summary form: count, min-id, max-id, consumers
        List<RespType> result = new ArrayList<>();
        result.add(new RespType.Integer(pending.size()));
        if (pending.isEmpty()) {
            result.add(RespType.BulkString.NULL);
            result.add(RespType.BulkString.NULL);
            result.add(new RespType.Array(List.of()));
        } else {
            String minId = null, maxId = null;
            Map<String, Long> consumerCounts = new LinkedHashMap<>();
            for (var pe : pending) {
                if (minId == null || pe.id().compareTo(minId) < 0) minId = pe.id();
                if (maxId == null || pe.id().compareTo(maxId) > 0) maxId = pe.id();
                consumerCounts.merge(pe.consumer(), 1L, Long::sum);
            }
            result.add(RespType.BulkString.of(minId));
            result.add(RespType.BulkString.of(maxId));
            List<RespType> consumers = new ArrayList<>();
            for (var entry : consumerCounts.entrySet()) {
                consumers.add(new RespType.Array(List.of(
                        RespType.BulkString.of(entry.getKey()),
                        RespType.BulkString.of(String.valueOf(entry.getValue())))));
            }
            result.add(new RespType.Array(consumers));
        }
        return new RespType.Array(result);
    }

    private static RespType xclaim(CommandArgs args, ClientConnection client) {
        StreamStore stream = client.database().getStream(args.getString(1));
        if (stream == null) return new RespType.Array(List.of());
        String group = args.getString(2);
        String consumer = args.getString(3);
        long minIdleTime = args.getLong(4);
        String[] ids = new String[args.size() - 5];
        for (int i = 5; i < args.size(); i++) {
            ids[i - 5] = args.getString(i);
        }
        List<StreamStore.StreamEntry> claimed = stream.claim(group, consumer, minIdleTime, ids);
        return encodeStreamEntries(claimed);
    }

    private static RespType xtrim(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        StreamStore stream = db.getStream(key);
        if (stream == null) return new RespType.Integer(0);

        int i = 2;
        String strategy = args.getString(i++).toUpperCase();
        if ("~".equals(args.getString(i))) i++; // approximate
        long threshold = args.getLong(i);

        if ("MAXLEN".equals(strategy)) {
            return new RespType.Integer(stream.trim(threshold));
        }
        return new RespType.Integer(0);
    }

    private static RespType encodeStreamEntries(List<StreamStore.StreamEntry> entries) {
        List<RespType> result = new ArrayList<>();
        for (var entry : entries) {
            List<RespType> fields = new ArrayList<>();
            for (var fv : entry.fields().entrySet()) {
                fields.add(RespType.BulkString.of(fv.getKey()));
                fields.add(RespType.BulkString.of(fv.getValue()));
            }
            result.add(new RespType.Array(List.of(
                    RespType.BulkString.of(entry.id()),
                    new RespType.Array(fields))));
        }
        return new RespType.Array(result);
    }
}
