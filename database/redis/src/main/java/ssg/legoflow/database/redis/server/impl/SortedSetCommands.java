package ssg.legoflow.database.redis.server.impl;

import ssg.legoflow.database.redis.command.CommandArgs;
import ssg.legoflow.database.redis.command.CommandRegistry;
import ssg.legoflow.database.redis.protocol.RespType;
import ssg.legoflow.database.redis.server.ClientConnection;
import ssg.legoflow.database.redis.server.Database;

import java.util.*;

/**
 * Implements Redis sorted set commands: ZADD, ZREM, ZSCORE, ZRANK, ZREVRANK,
 * ZRANGE, ZRANGEBYSCORE, ZRANGEBYLEX, ZCARD, ZCOUNT, ZINCRBY, ZINTERSTORE,
 * ZUNIONSTORE, ZPOPMIN, ZPOPMAX.
 *
 * @since 0.1.0
 */
public final class SortedSetCommands {

    private SortedSetCommands() {}

    /**
     * Registers all sorted set commands with the given registry.
     *
     * @param registry the command registry
     */
    public static void register(CommandRegistry registry) {
        registry.register("ZADD", SortedSetCommands::zadd);
        registry.register("ZREM", SortedSetCommands::zrem);
        registry.register("ZSCORE", SortedSetCommands::zscore);
        registry.register("ZRANK", SortedSetCommands::zrank);
        registry.register("ZREVRANK", SortedSetCommands::zrevrank);
        registry.register("ZRANGE", SortedSetCommands::zrange);
        registry.register("ZRANGEBYSCORE", SortedSetCommands::zrangebyscore);
        registry.register("ZRANGEBYLEX", SortedSetCommands::zrangebylex);
        registry.register("ZCARD", SortedSetCommands::zcard);
        registry.register("ZCOUNT", SortedSetCommands::zcount);
        registry.register("ZINCRBY", SortedSetCommands::zincrby);
        registry.register("ZINTERSTORE", SortedSetCommands::zinterstore);
        registry.register("ZUNIONSTORE", SortedSetCommands::zunionstore);
        registry.register("ZPOPMIN", SortedSetCommands::zpopmin);
        registry.register("ZPOPMAX", SortedSetCommands::zpopmax);
    }

    private static RespType zadd(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);

        boolean nx = false, xx = false, gt = false, lt = false, ch = false;
        int i = 2;
        while (i < args.size()) {
            String opt = args.getString(i).toUpperCase();
            switch (opt) {
                case "NX" -> { nx = true; i++; }
                case "XX" -> { xx = true; i++; }
                case "GT" -> { gt = true; i++; }
                case "LT" -> { lt = true; i++; }
                case "CH" -> { ch = true; i++; }
                default -> { break; }
            }
            if (!Set.of("NX", "XX", "GT", "LT", "CH").contains(opt)) break;
        }

        NavigableMap<Double, Set<String>> zset = db.getOrCreateZSet(key);
        Map<String, Double> scores = db.getOrCreateZSetScores(key);
        int added = 0, changed = 0;

        while (i + 1 < args.size()) {
            double score = args.getDouble(i);
            String member = args.getString(i + 1);
            i += 2;

            Double existing = scores.get(member);
            if (existing != null) {
                if (nx) continue;
                if (gt && score <= existing) continue;
                if (lt && score >= existing) continue;
                // Update
                Set<String> oldBucket = zset.get(existing);
                if (oldBucket != null) {
                    oldBucket.remove(member);
                    if (oldBucket.isEmpty()) zset.remove(existing);
                }
                zset.computeIfAbsent(score, k -> new LinkedHashSet<>()).add(member);
                scores.put(member, score);
                if (existing != score) changed++;
            } else {
                if (xx) continue;
                zset.computeIfAbsent(score, k -> new LinkedHashSet<>()).add(member);
                scores.put(member, score);
                added++;
                changed++;
            }
        }

        client.server().transactionExecutor().touchKey(key);
        return new RespType.Integer(ch ? changed : added);
    }

    private static RespType zrem(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        NavigableMap<Double, Set<String>> zset = db.getZSet(key);
        Map<String, Double> scores = db.getZSetScores(key);
        if (zset == null || scores == null) return new RespType.Integer(0);

        int removed = 0;
        for (int i = 2; i < args.size(); i++) {
            String member = args.getString(i);
            Double score = scores.remove(member);
            if (score != null) {
                Set<String> bucket = zset.get(score);
                if (bucket != null) {
                    bucket.remove(member);
                    if (bucket.isEmpty()) zset.remove(score);
                }
                removed++;
            }
        }
        db.removeIfEmpty(key);
        client.server().transactionExecutor().touchKey(key);
        return new RespType.Integer(removed);
    }

    private static RespType zscore(CommandArgs args, ClientConnection client) {
        Map<String, Double> scores = client.database().getZSetScores(args.getString(1));
        if (scores == null) return RespType.BulkString.NULL;
        Double score = scores.get(args.getString(2));
        if (score == null) return RespType.BulkString.NULL;
        return RespType.BulkString.of(String.valueOf(score));
    }

    private static RespType zrank(CommandArgs args, ClientConnection client) {
        return rankHelper(args, client, false);
    }

    private static RespType zrevrank(CommandArgs args, ClientConnection client) {
        return rankHelper(args, client, true);
    }

    private static RespType rankHelper(CommandArgs args, ClientConnection client, boolean rev) {
        Database db = client.database();
        String key = args.getString(1);
        String member = args.getString(2);
        NavigableMap<Double, Set<String>> zset = db.getZSet(key);
        Map<String, Double> scores = db.getZSetScores(key);
        if (zset == null || scores == null || !scores.containsKey(member)) {
            return RespType.BulkString.NULL;
        }

        List<String> ordered = flattenZSet(rev ? zset.descendingMap() : zset);
        int rank = ordered.indexOf(member);
        return rank < 0 ? RespType.BulkString.NULL : new RespType.Integer(rank);
    }

    private static RespType zrange(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        NavigableMap<Double, Set<String>> zset = db.getZSet(key);
        if (zset == null) return new RespType.Array(List.of());

        int start = args.getInt(2);
        int stop = args.getInt(3);
        boolean withScores = args.size() > 4 && "WITHSCORES".equalsIgnoreCase(args.getString(4));

        List<String> ordered = flattenZSet(zset);
        int size = ordered.size();
        if (start < 0) start = Math.max(0, size + start);
        if (stop < 0) stop = size + stop;
        stop = Math.min(stop, size - 1);

        if (start > stop || start >= size) return new RespType.Array(List.of());

        Map<String, Double> scores = db.getZSetScores(key);
        List<RespType> result = new ArrayList<>();
        for (int i = start; i <= stop; i++) {
            String member = ordered.get(i);
            result.add(RespType.BulkString.of(member));
            if (withScores && scores != null) {
                result.add(RespType.BulkString.of(String.valueOf(scores.get(member))));
            }
        }
        return new RespType.Array(result);
    }

    private static RespType zrangebyscore(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        NavigableMap<Double, Set<String>> zset = db.getZSet(key);
        if (zset == null) return new RespType.Array(List.of());

        double min = parseScoreBound(args.getString(2), Double.NEGATIVE_INFINITY);
        double max = parseScoreBound(args.getString(3), Double.POSITIVE_INFINITY);
        boolean minExclusive = args.getString(2).startsWith("(");
        boolean maxExclusive = args.getString(3).startsWith("(");

        boolean withScores = false;
        int offset = 0, count = Integer.MAX_VALUE;
        for (int i = 4; i < args.size(); i++) {
            String opt = args.getString(i).toUpperCase();
            if ("WITHSCORES".equals(opt)) withScores = true;
            else if ("LIMIT".equals(opt)) {
                offset = args.getInt(i + 1);
                count = args.getInt(i + 2);
                i += 2;
            }
        }

        NavigableMap<Double, Set<String>> sub = zset.subMap(min, !minExclusive, max, !maxExclusive);
        Map<String, Double> scores = db.getZSetScores(key);
        List<RespType> result = new ArrayList<>();
        int pos = 0;
        outer:
        for (var entry : sub.entrySet()) {
            for (String member : entry.getValue()) {
                if (pos >= offset) {
                    result.add(RespType.BulkString.of(member));
                    if (withScores && scores != null) {
                        result.add(RespType.BulkString.of(String.valueOf(scores.get(member))));
                    }
                    if (result.size() / (withScores ? 2 : 1) >= count) break outer;
                }
                pos++;
            }
        }
        return new RespType.Array(result);
    }

    private static RespType zrangebylex(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        NavigableMap<Double, Set<String>> zset = db.getZSet(key);
        if (zset == null) return new RespType.Array(List.of());

        String minArg = args.getString(2);
        String maxArg = args.getString(3);

        int offset = 0, count = Integer.MAX_VALUE;
        for (int i = 4; i < args.size(); i++) {
            if ("LIMIT".equalsIgnoreCase(args.getString(i))) {
                offset = args.getInt(i + 1);
                count = args.getInt(i + 2);
                i += 2;
            }
        }

        List<String> ordered = flattenZSet(zset);
        List<RespType> result = new ArrayList<>();
        int pos = 0;
        for (String member : ordered) {
            if (lexInRange(member, minArg, maxArg)) {
                if (pos >= offset) {
                    result.add(RespType.BulkString.of(member));
                    if (result.size() >= count) break;
                }
                pos++;
            }
        }
        return new RespType.Array(result);
    }

    private static RespType zcard(CommandArgs args, ClientConnection client) {
        Map<String, Double> scores = client.database().getZSetScores(args.getString(1));
        return new RespType.Integer(scores == null ? 0 : scores.size());
    }

    private static RespType zcount(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        NavigableMap<Double, Set<String>> zset = db.getZSet(key);
        if (zset == null) return new RespType.Integer(0);

        double min = parseScoreBound(args.getString(2), Double.NEGATIVE_INFINITY);
        double max = parseScoreBound(args.getString(3), Double.POSITIVE_INFINITY);
        boolean minExcl = args.getString(2).startsWith("(");
        boolean maxExcl = args.getString(3).startsWith("(");

        NavigableMap<Double, Set<String>> sub = zset.subMap(min, !minExcl, max, !maxExcl);
        long count = 0;
        for (Set<String> bucket : sub.values()) {
            count += bucket.size();
        }
        return new RespType.Integer(count);
    }

    private static RespType zincrby(CommandArgs args, ClientConnection client) {
        Database db = client.database();
        String key = args.getString(1);
        double increment = args.getDouble(2);
        String member = args.getString(3);

        NavigableMap<Double, Set<String>> zset = db.getOrCreateZSet(key);
        Map<String, Double> scores = db.getOrCreateZSetScores(key);

        Double existing = scores.get(member);
        double newScore = (existing != null ? existing : 0) + increment;

        if (existing != null) {
            Set<String> bucket = zset.get(existing);
            if (bucket != null) {
                bucket.remove(member);
                if (bucket.isEmpty()) zset.remove(existing);
            }
        }
        zset.computeIfAbsent(newScore, k -> new LinkedHashSet<>()).add(member);
        scores.put(member, newScore);
        client.server().transactionExecutor().touchKey(key);
        return RespType.BulkString.of(String.valueOf(newScore));
    }

    private static RespType zinterstore(CommandArgs args, ClientConnection client) {
        return storeOperation(args, client, true);
    }

    private static RespType zunionstore(CommandArgs args, ClientConnection client) {
        return storeOperation(args, client, false);
    }

    private static RespType storeOperation(CommandArgs args, ClientConnection client, boolean intersect) {
        Database db = client.database();
        String destKey = args.getString(1);
        int numKeys = args.getInt(2);

        List<String> keys = new ArrayList<>();
        for (int i = 3; i < 3 + numKeys; i++) {
            keys.add(args.getString(i));
        }

        double[] weights = new double[numKeys];
        Arrays.fill(weights, 1.0);
        String aggregate = "SUM";

        for (int i = 3 + numKeys; i < args.size(); i++) {
            String opt = args.getString(i).toUpperCase();
            if ("WEIGHTS".equals(opt)) {
                for (int j = 0; j < numKeys; j++) {
                    weights[j] = args.getDouble(i + 1 + j);
                }
                i += numKeys;
            } else if ("AGGREGATE".equals(opt)) {
                aggregate = args.getString(++i).toUpperCase();
            }
        }

        // Compute result
        final String finalAggregate = aggregate;
        Map<String, Double> resultScores = new LinkedHashMap<>();
        for (int k = 0; k < keys.size(); k++) {
            Map<String, Double> scores = db.getZSetScores(keys.get(k));
            if (scores == null) {
                if (intersect) {
                    resultScores.clear();
                    break;
                }
                continue;
            }
            double weight = weights[k];
            if (k == 0 || !intersect) {
                for (var e : scores.entrySet()) {
                    double s = e.getValue() * weight;
                    resultScores.merge(e.getKey(), s, (a, b) -> aggregateScores(a, b, finalAggregate));
                }
            } else {
                // Intersect: only keep members present in current set
                resultScores.keySet().retainAll(scores.keySet());
                for (var e : resultScores.entrySet()) {
                    Double srcScore = scores.get(e.getKey());
                    if (srcScore != null) {
                        e.setValue(aggregateScores(e.getValue(), srcScore * weight, aggregate));
                    }
                }
            }
        }

        // Store result
        db.delete(destKey);
        if (!resultScores.isEmpty()) {
            NavigableMap<Double, Set<String>> destZSet = db.getOrCreateZSet(destKey);
            Map<String, Double> destScores = db.getOrCreateZSetScores(destKey);
            for (var e : resultScores.entrySet()) {
                destZSet.computeIfAbsent(e.getValue(), s -> new LinkedHashSet<>()).add(e.getKey());
                destScores.put(e.getKey(), e.getValue());
            }
        }
        client.server().transactionExecutor().touchKey(destKey);
        return new RespType.Integer(resultScores.size());
    }

    private static RespType zpopmin(CommandArgs args, ClientConnection client) {
        return zpop(args, client, true);
    }

    private static RespType zpopmax(CommandArgs args, ClientConnection client) {
        return zpop(args, client, false);
    }

    private static RespType zpop(CommandArgs args, ClientConnection client, boolean min) {
        Database db = client.database();
        String key = args.getString(1);
        NavigableMap<Double, Set<String>> zset = db.getZSet(key);
        Map<String, Double> scores = db.getZSetScores(key);
        if (zset == null || zset.isEmpty()) return new RespType.Array(List.of());

        int count = args.size() > 2 ? args.getInt(2) : 1;
        List<RespType> result = new ArrayList<>();

        for (int i = 0; i < count && !zset.isEmpty(); i++) {
            var entry = min ? zset.firstEntry() : zset.lastEntry();
            if (entry == null) break;
            Iterator<String> it = entry.getValue().iterator();
            if (it.hasNext()) {
                String member = it.next();
                it.remove();
                if (entry.getValue().isEmpty()) zset.remove(entry.getKey());
                if (scores != null) scores.remove(member);
                result.add(RespType.BulkString.of(member));
                result.add(RespType.BulkString.of(String.valueOf(entry.getKey())));
            }
        }
        db.removeIfEmpty(key);
        client.server().transactionExecutor().touchKey(key);
        return new RespType.Array(result);
    }

    // ---- Helpers ----

    private static List<String> flattenZSet(NavigableMap<Double, Set<String>> zset) {
        List<String> result = new ArrayList<>();
        for (Set<String> bucket : zset.values()) {
            result.addAll(bucket);
        }
        return result;
    }

    private static double parseScoreBound(String s, double defaultVal) {
        if ("-inf".equals(s)) return Double.NEGATIVE_INFINITY;
        if ("+inf".equals(s)) return Double.POSITIVE_INFINITY;
        if (s.startsWith("(")) return Double.parseDouble(s.substring(1));
        return Double.parseDouble(s);
    }

    private static boolean lexInRange(String member, String minArg, String maxArg) {
        if (!"-".equals(minArg)) {
            boolean exclusive = minArg.startsWith("(");
            String bound = minArg.startsWith("(") || minArg.startsWith("[") ? minArg.substring(1) : minArg;
            int cmp = member.compareTo(bound);
            if (exclusive ? cmp <= 0 : cmp < 0) return false;
        }
        if (!"+".equals(maxArg)) {
            boolean exclusive = maxArg.startsWith("(");
            String bound = maxArg.startsWith("(") || maxArg.startsWith("[") ? maxArg.substring(1) : maxArg;
            int cmp = member.compareTo(bound);
            if (exclusive ? cmp >= 0 : cmp > 0) return false;
        }
        return true;
    }

    private static double aggregateScores(double a, double b, String aggregate) {
        return switch (aggregate) {
            case "MIN" -> Math.min(a, b);
            case "MAX" -> Math.max(a, b);
            default -> a + b; // SUM
        };
    }
}
