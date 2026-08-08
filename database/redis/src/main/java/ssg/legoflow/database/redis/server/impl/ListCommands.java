package ssg.legoflow.database.redis.server.impl;

import ssg.legoflow.database.redis.command.CommandArgs;
import ssg.legoflow.database.redis.command.CommandRegistry;
import ssg.legoflow.database.redis.protocol.RespType;
import ssg.legoflow.database.redis.server.ClientConnection;
import ssg.legoflow.database.redis.server.Database;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Implements Redis list commands: LPUSH, RPUSH, LPOP, RPOP, LLEN, LRANGE,
 * LINDEX, LSET, LREM, LINSERT, LTRIM, BLPOP, BRPOP, LMOVE, BLMOVE.
 *
 * @since 0.1.0
 */
public final class ListCommands {

    private static final RespType OK = new RespType.SimpleString("OK");

    private ListCommands() {}

    /**
     * Registers all list commands with the given registry.
     *
     * @param registry the command registry
     */
    public static void register(CommandRegistry registry) {
        registry.register("LPUSH", ListCommands::lpush);
        registry.register("RPUSH", ListCommands::rpush);
        registry.register("LPOP", ListCommands::lpop);
        registry.register("RPOP", ListCommands::rpop);
        registry.register("LLEN", ListCommands::llen);
        registry.register("LRANGE", ListCommands::lrange);
        registry.register("LINDEX", ListCommands::lindex);
        registry.register("LSET", ListCommands::lset);
        registry.register("LREM", ListCommands::lrem);
        registry.register("LINSERT", ListCommands::linsert);
        registry.register("LTRIM", ListCommands::ltrim);
        registry.register("BLPOP", ListCommands::blpop);
        registry.register("BRPOP", ListCommands::brpop);
        registry.register("LMOVE", ListCommands::lmove);
        registry.register("BLMOVE", ListCommands::blmove);
    }

    private static RespType lpush(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        Deque<byte[]> list = db.getOrCreateList(key);
        for (int i = 2; i < args.size(); i++) {
            list.addFirst(args.getBytes(i));
        }
        client.server().transactionExecutor().touchKey(key);
        return new RespType.Integer(list.size());
    }

    private static RespType rpush(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        Deque<byte[]> list = db.getOrCreateList(key);
        for (int i = 2; i < args.size(); i++) {
            list.addLast(args.getBytes(i));
        }
        client.server().transactionExecutor().touchKey(key);
        return new RespType.Integer(list.size());
    }

    private static RespType lpop(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        Deque<byte[]> list = db.getList(key);
        if (list == null || list.isEmpty()) return RespType.BulkString.NULL;

        int count = args.size() > 2 ? args.getInt(2) : 1;
        if (count == 1) {
            byte[] value = list.pollFirst();
            db.removeIfEmpty(key);
            client.server().transactionExecutor().touchKey(key);
            return value != null ? new RespType.BulkString(value) : RespType.BulkString.NULL;
        }

        List<RespType> results = new ArrayList<>();
        for (int i = 0; i < count && !list.isEmpty(); i++) {
            results.add(new RespType.BulkString(list.pollFirst()));
        }
        db.removeIfEmpty(key);
        client.server().transactionExecutor().touchKey(key);
        return new RespType.Array(results);
    }

    private static RespType rpop(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        Deque<byte[]> list = db.getList(key);
        if (list == null || list.isEmpty()) return RespType.BulkString.NULL;

        int count = args.size() > 2 ? args.getInt(2) : 1;
        if (count == 1) {
            byte[] value = list.pollLast();
            db.removeIfEmpty(key);
            client.server().transactionExecutor().touchKey(key);
            return value != null ? new RespType.BulkString(value) : RespType.BulkString.NULL;
        }

        List<RespType> results = new ArrayList<>();
        for (int i = 0; i < count && !list.isEmpty(); i++) {
            results.add(new RespType.BulkString(list.pollLast()));
        }
        db.removeIfEmpty(key);
        client.server().transactionExecutor().touchKey(key);
        return new RespType.Array(results);
    }

    private static RespType llen(CommandArgs args, ClientConnection client) {
        Deque<byte[]> list = client.database().getList(args.getString(1));
        return new RespType.Integer(list == null ? 0 : list.size());
    }

    private static RespType lrange(CommandArgs args, ClientConnection client) {
        Deque<byte[]> list = client.database().getList(args.getString(1));
        if (list == null) return new RespType.Array(List.of());

        List<byte[]> asList = new ArrayList<>(list);
        int start = args.getInt(2);
        int stop = args.getInt(3);
        int size = asList.size();

        if (start < 0) start = Math.max(0, size + start);
        if (stop < 0) stop = size + stop;
        stop = Math.min(stop, size - 1);

        if (start > stop || start >= size) return new RespType.Array(List.of());

        List<RespType> results = new ArrayList<>();
        for (int i = start; i <= stop; i++) {
            results.add(new RespType.BulkString(asList.get(i)));
        }
        return new RespType.Array(results);
    }

    private static RespType lindex(CommandArgs args, ClientConnection client) {
        Deque<byte[]> list = client.database().getList(args.getString(1));
        if (list == null) return RespType.BulkString.NULL;

        List<byte[]> asList = new ArrayList<>(list);
        int index = args.getInt(2);
        if (index < 0) index = asList.size() + index;
        if (index < 0 || index >= asList.size()) return RespType.BulkString.NULL;
        return new RespType.BulkString(asList.get(index));
    }

    private static RespType lset(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        Deque<byte[]> deque = db.getList(key);
        if (deque == null) return new RespType.Error("ERR", "no such key");

        List<byte[]> asList = new ArrayList<>(deque);
        int index = args.getInt(2);
        if (index < 0) index = asList.size() + index;
        if (index < 0 || index >= asList.size()) {
            return new RespType.Error("ERR", "index out of range");
        }
        asList.set(index, args.getBytes(3));
        deque.clear();
        deque.addAll(asList);
        client.server().transactionExecutor().touchKey(key);
        return OK;
    }

    private static RespType lrem(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        Deque<byte[]> deque = db.getList(key);
        if (deque == null) return new RespType.Integer(0);

        int count = args.getInt(2);
        byte[] value = args.getBytes(3);
        String target = new String(value, StandardCharsets.UTF_8);

        List<byte[]> asList = new ArrayList<>(deque);
        int removed = 0;

        if (count > 0) {
            Iterator<byte[]> it = asList.iterator();
            while (it.hasNext() && removed < count) {
                if (new String(it.next(), StandardCharsets.UTF_8).equals(target)) {
                    it.remove();
                    removed++;
                }
            }
        } else if (count < 0) {
            ListIterator<byte[]> it = asList.listIterator(asList.size());
            int absCount = -count;
            while (it.hasPrevious() && removed < absCount) {
                if (new String(it.previous(), StandardCharsets.UTF_8).equals(target)) {
                    it.remove();
                    removed++;
                }
            }
        } else {
            Iterator<byte[]> it = asList.iterator();
            while (it.hasNext()) {
                if (new String(it.next(), StandardCharsets.UTF_8).equals(target)) {
                    it.remove();
                    removed++;
                }
            }
        }

        deque.clear();
        deque.addAll(asList);
        db.removeIfEmpty(key);
        client.server().transactionExecutor().touchKey(key);
        return new RespType.Integer(removed);
    }

    private static RespType linsert(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        Deque<byte[]> deque = db.getList(key);
        if (deque == null) return new RespType.Integer(0);

        String position = args.getString(2).toUpperCase();
        byte[] pivot = args.getBytes(3);
        byte[] element = args.getBytes(4);
        String pivotStr = new String(pivot, StandardCharsets.UTF_8);

        List<byte[]> asList = new ArrayList<>(deque);
        int idx = -1;
        for (int i = 0; i < asList.size(); i++) {
            if (new String(asList.get(i), StandardCharsets.UTF_8).equals(pivotStr)) {
                idx = i;
                break;
            }
        }
        if (idx < 0) return new RespType.Integer(-1);

        if ("AFTER".equals(position)) idx++;
        asList.add(idx, element);
        deque.clear();
        deque.addAll(asList);
        client.server().transactionExecutor().touchKey(key);
        return new RespType.Integer(asList.size());
    }

    private static RespType ltrim(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        Deque<byte[]> deque = db.getList(key);
        if (deque == null) return OK;

        List<byte[]> asList = new ArrayList<>(deque);
        int start = args.getInt(2);
        int stop = args.getInt(3);
        int size = asList.size();

        if (start < 0) start = Math.max(0, size + start);
        if (stop < 0) stop = size + stop;
        stop = Math.min(stop, size - 1);

        deque.clear();
        if (start <= stop && start < size) {
            for (int i = start; i <= stop; i++) {
                deque.addLast(asList.get(i));
            }
        }
        db.removeIfEmpty(key);
        client.server().transactionExecutor().touchKey(key);
        return OK;
    }

    private static RespType blpop(CommandArgs args, ClientConnection client) {
        // Non-blocking implementation: check keys immediately
        Database db = client.database();
        for (int i = 1; i < args.size() - 1; i++) {
            String key = args.getString(i);
            Deque<byte[]> list = db.getList(key);
            if (list != null && !list.isEmpty()) {
                byte[] value = list.pollFirst();
                db.removeIfEmpty(key);
                client.server().transactionExecutor().touchKey(key);
                return new RespType.Array(List.of(
                        RespType.BulkString.of(key),
                        new RespType.BulkString(value)));
            }
        }
        return RespType.Array.NULL;
    }

    private static RespType brpop(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        for (int i = 1; i < args.size() - 1; i++) {
            String key = args.getString(i);
            Deque<byte[]> list = db.getList(key);
            if (list != null && !list.isEmpty()) {
                byte[] value = list.pollLast();
                db.removeIfEmpty(key);
                client.server().transactionExecutor().touchKey(key);
                return new RespType.Array(List.of(
                        RespType.BulkString.of(key),
                        new RespType.BulkString(value)));
            }
        }
        return RespType.Array.NULL;
    }

    private static RespType lmove(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String src = args.getString(1);
        String dst = args.getString(2);
        String whereFrom = args.getString(3).toUpperCase();
        String whereTo = args.getString(4).toUpperCase();

        Deque<byte[]> srcList = db.getList(src);
        if (srcList == null || srcList.isEmpty()) return RespType.BulkString.NULL;

        byte[] value = "LEFT".equals(whereFrom) ? srcList.pollFirst() : srcList.pollLast();
        Deque<byte[]> dstList = db.getOrCreateList(dst);
        if ("LEFT".equals(whereTo)) {
            dstList.addFirst(value);
        } else {
            dstList.addLast(value);
        }
        db.removeIfEmpty(src);
        client.server().transactionExecutor().touchKey(src);
        client.server().transactionExecutor().touchKey(dst);
        return new RespType.BulkString(value);
    }

    private static RespType blmove(CommandArgs args, ClientConnection client) {
        // Non-blocking: same as LMOVE
        return lmove(new CommandArgs(args.elements().subList(0, 5)), client);
    }
}
