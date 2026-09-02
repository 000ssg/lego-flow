package ssg.legoflow.database.redis.server.impl;

import ssg.legoflow.database.redis.command.CommandArgs;
import ssg.legoflow.database.redis.command.CommandRegistry;
import ssg.legoflow.database.redis.protocol.RespType;
import ssg.legoflow.database.redis.server.ClientConnection;
import ssg.legoflow.database.redis.server.Database;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
/**
 * Implements Redis set commands: SADD, SREM, SMEMBERS, SISMEMBER, SCARD,
 * SINTER, SUNION, SDIFF, SRANDMEMBER, SPOP, SMOVE.
 *
 * @since 0.1.0
 */
public final class SetCommands {

    private SetCommands() {}

    /**
     * Registers all set commands with the given registry.
     *
     * @param registry the command registry
     */
    public static void register(CommandRegistry registry) {
        registry.register("SADD", SetCommands::sadd);
        registry.register("SREM", SetCommands::srem);
        registry.register("SMEMBERS", SetCommands::smembers);
        registry.register("SISMEMBER", SetCommands::sismember);
        registry.register("SCARD", SetCommands::scard);
        registry.register("SINTER", SetCommands::sinter);
        registry.register("SUNION", SetCommands::sunion);
        registry.register("SDIFF", SetCommands::sdiff);
        registry.register("SRANDMEMBER", SetCommands::srandmember);
        registry.register("SPOP", SetCommands::spop);
        registry.register("SMOVE", SetCommands::smove);
    }

    private static RespType sadd(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        Set<String> set = db.getOrCreateSet(key);
        int added = 0;
        for (int i = 2; i < args.size(); i++) {
            if (set.add(args.getString(i))) added++;
        }
        client.server().transactionExecutor().touchKey(key);
        return new RespType.Integer(added);
    }

    private static RespType srem(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        Set<String> set = db.getSet(key);
        if (set == null) return new RespType.Integer(0);
        int removed = 0;
        for (int i = 2; i < args.size(); i++) {
            if (set.remove(args.getString(i))) removed++;
        }
        db.removeIfEmpty(key);
        client.server().transactionExecutor().touchKey(key);
        return new RespType.Integer(removed);
    }

    private static RespType smembers(CommandArgs args, ClientConnection client) {
        Set<String> set = client.database().getSet(args.getString(1));
        if (set == null) return new RespType.Array(List.of());
        List<RespType> result = new ArrayList<>();
        for (String member : set) {
            result.add(RespType.BulkString.of(member));
        }
        return new RespType.Array(result);
    }

    private static RespType sismember(CommandArgs args, ClientConnection client) {
        Set<String> set = client.database().getSet(args.getString(1));
        boolean isMember = set != null && set.contains(args.getString(2));
        return new RespType.Integer(isMember ? 1 : 0);
    }

    private static RespType scard(CommandArgs args, ClientConnection client) {
        Set<String> set = client.database().getSet(args.getString(1));
        return new RespType.Integer(set == null ? 0 : set.size());
    }

    private static RespType sinter(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        Set<String> result = null;
        for (int i = 1; i < args.size(); i++) {
            Set<String> set = db.getSet(args.getString(i));
            if (set == null) return new RespType.Array(List.of());
            if (result == null) {
                result = new HashSet<>(set);
            } else {
                result.retainAll(set);
            }
        }
        if (result == null) return new RespType.Array(List.of());
        List<RespType> elements = new ArrayList<>();
        for (String m : result) {
            elements.add(RespType.BulkString.of(m));
        }
        return new RespType.Array(elements);
    }

    private static RespType sunion(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        Set<String> result = new HashSet<>();
        for (int i = 1; i < args.size(); i++) {
            Set<String> set = db.getSet(args.getString(i));
            if (set != null) result.addAll(set);
        }
        List<RespType> elements = new ArrayList<>();
        for (String m : result) {
            elements.add(RespType.BulkString.of(m));
        }
        return new RespType.Array(elements);
    }

    private static RespType sdiff(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        Set<String> result = null;
        for (int i = 1; i < args.size(); i++) {
            Set<String> set = db.getSet(args.getString(i));
            if (i == 1) {
                result = set == null ? new HashSet<>() : new HashSet<>(set);
            } else if (set != null) {
                result.removeAll(set);
            }
        }
        if (result == null) return new RespType.Array(List.of());
        List<RespType> elements = new ArrayList<>();
        for (String m : result) {
            elements.add(RespType.BulkString.of(m));
        }
        return new RespType.Array(elements);
    }

    private static RespType srandmember(CommandArgs args, ClientConnection client) {
        Set<String> set = client.database().getSet(args.getString(1));
        if (set == null || set.isEmpty()) return RespType.BulkString.NULL;

        int count = args.size() > 2 ? args.getInt(2) : 0;
        List<String> members = new ArrayList<>(set);

        if (count == 0) {
            // Return single random member
            String member = members.get(ThreadLocalRandom.current().nextInt(members.size()));
            return RespType.BulkString.of(member);
        }

        List<RespType> results = new ArrayList<>();
        if (count > 0) {
            Collections.shuffle(members);
            for (int i = 0; i < Math.min(count, members.size()); i++) {
                results.add(RespType.BulkString.of(members.get(i)));
            }
        } else {
            // Negative count: allow duplicates
            int absCount = -count;
            for (int i = 0; i < absCount; i++) {
                results.add(RespType.BulkString.of(members.get(ThreadLocalRandom.current().nextInt(members.size()))));
            }
        }
        return new RespType.Array(results);
    }

    private static RespType spop(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        Set<String> set = db.getSet(key);
        if (set == null || set.isEmpty()) return RespType.BulkString.NULL;

        int count = args.size() > 2 ? args.getInt(2) : 1;
        List<String> members = new ArrayList<>(set);
        Collections.shuffle(members);

        if (count == 1) {
            String member = members.getFirst();
            set.remove(member);
            db.removeIfEmpty(key);
            client.server().transactionExecutor().touchKey(key);
            return RespType.BulkString.of(member);
        }

        List<RespType> results = new ArrayList<>();
        for (int i = 0; i < Math.min(count, members.size()); i++) {
            String member = members.get(i);
            set.remove(member);
            results.add(RespType.BulkString.of(member));
        }
        db.removeIfEmpty(key);
        client.server().transactionExecutor().touchKey(key);
        return new RespType.Array(results);
    }

    private static RespType smove(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String srcKey = args.getString(1);
        String dstKey = args.getString(2);
        String member = args.getString(3);

        Set<String> srcSet = db.getSet(srcKey);
        if (srcSet == null || !srcSet.remove(member)) {
            return new RespType.Integer(0);
        }
        db.getOrCreateSet(dstKey).add(member);
        db.removeIfEmpty(srcKey);
        client.server().transactionExecutor().touchKey(srcKey);
        client.server().transactionExecutor().touchKey(dstKey);
        return new RespType.Integer(1);
    }
}
