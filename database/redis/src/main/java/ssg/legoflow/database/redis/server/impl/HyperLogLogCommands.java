package ssg.legoflow.database.redis.server.impl;

import ssg.legoflow.database.redis.command.CommandArgs;
import ssg.legoflow.database.redis.command.CommandRegistry;
import ssg.legoflow.database.redis.protocol.RespType;
import ssg.legoflow.database.redis.server.ClientConnection;
import ssg.legoflow.database.redis.server.Database;
import ssg.legoflow.database.redis.server.HyperLogLog;
import java.nio.charset.StandardCharsets;
/**
 * Implements Redis HyperLogLog commands: PFADD, PFCOUNT, PFMERGE.
 *
 * <p>HyperLogLog is a probabilistic data structure used for cardinality estimation
 * with a standard error of 0.81%. Uses 2^14 registers matching the Redis standard.
 *
 * @since 0.1.0
 */
public final class HyperLogLogCommands {

    private static final RespType OK = new RespType.SimpleString("OK");

    private HyperLogLogCommands() {}

    /**
     * Registers all HyperLogLog commands with the given registry.
     *
     * @param registry the command registry
     */
    public static void register(CommandRegistry registry) {
        registry.register("PFADD", HyperLogLogCommands::pfadd);
        registry.register("PFCOUNT", HyperLogLogCommands::pfcount);
        registry.register("PFMERGE", HyperLogLogCommands::pfmerge);
    }

    /**
     * PFADD key element [element ...]
     * Adds elements to a HyperLogLog. Returns 1 if internal state changed, 0 otherwise.
     */
    private static RespType pfadd(CommandArgs args, ClientConnection client) {
        if (args.size() < 2) {
            return new RespType.Error("ERR", "wrong number of arguments for 'pfadd' command");
        }
        Database db = client.database();
        String key = args.getString(1);
        HyperLogLog hll = db.getOrCreateHyperLogLog(key);

        boolean changed = false;
        for (int i = 2; i < args.size(); i++) {
            byte[] element = args.getString(i).getBytes(StandardCharsets.UTF_8);
            if (hll.add(element)) {
                changed = true;
            }
        }

        return new RespType.Integer(changed ? 1 : 0);
    }

    /**
     * PFCOUNT key [key ...]
     * Returns the cardinality estimate. For multiple keys, returns the union cardinality.
     */
    private static RespType pfcount(CommandArgs args, ClientConnection client) {
        if (args.size() < 2) {
            return new RespType.Error("ERR", "wrong number of arguments for 'pfcount' command");
        }
        Database db = client.database();

        if (args.size() == 2) {
            // Single key
            String key = args.getString(1);
            HyperLogLog hll = db.getHyperLogLog(key);
            if (hll == null) {
                return new RespType.Integer(0);
            }
            return new RespType.Integer(hll.count());
        }

        // Multiple keys: merge into a temporary HyperLogLog
        HyperLogLog merged = new HyperLogLog();
        for (int i = 1; i < args.size(); i++) {
            String key = args.getString(i);
            HyperLogLog hll = db.getHyperLogLog(key);
            if (hll != null) {
                merged.merge(hll);
            }
        }
        return new RespType.Integer(merged.count());
    }

    /**
     * PFMERGE destkey sourcekey [sourcekey ...]
     * Merges one or more source HyperLogLogs into a destination.
     */
    private static RespType pfmerge(CommandArgs args, ClientConnection client) {
        if (args.size() < 3) {
            return new RespType.Error("ERR", "wrong number of arguments for 'pfmerge' command");
        }
        Database db = client.database();
        String destKey = args.getString(1);
        HyperLogLog dest = db.getOrCreateHyperLogLog(destKey);

        for (int i = 2; i < args.size(); i++) {
            String sourceKey = args.getString(i);
            HyperLogLog source = db.getHyperLogLog(sourceKey);
            if (source != null) {
                dest.merge(source);
            }
        }

        return OK;
    }
}
