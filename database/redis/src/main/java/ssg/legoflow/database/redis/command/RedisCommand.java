package ssg.legoflow.database.redis.command;

import java.util.Set;

/**
 * Redis command definitions with arity and flags.
 *
 * <p>Arity follows Redis conventions: positive means exact argument count,
 * negative means minimum. The count includes the command name itself.
 * For example, GET has arity 2 (GET key), SET has arity -3 (SET key value [options...]).
 *
 * @since 0.1.0
 */
public enum RedisCommand {

    // String commands
    SET("set", -3, Set.of(Flag.WRITE, Flag.DENYOOM)),
    GET("get", 2, Set.of(Flag.READONLY, Flag.FAST)),
    MSET("mset", -3, Set.of(Flag.WRITE, Flag.DENYOOM)),
    MGET("mget", -2, Set.of(Flag.READONLY, Flag.FAST)),
    APPEND("append", 3, Set.of(Flag.WRITE, Flag.DENYOOM)),
    STRLEN("strlen", 2, Set.of(Flag.READONLY, Flag.FAST)),
    INCR("incr", 2, Set.of(Flag.WRITE, Flag.FAST)),
    DECR("decr", 2, Set.of(Flag.WRITE, Flag.FAST)),
    INCRBY("incrby", 3, Set.of(Flag.WRITE, Flag.FAST)),
    DECRBY("decrby", 3, Set.of(Flag.WRITE, Flag.FAST)),
    INCRBYFLOAT("incrbyfloat", 3, Set.of(Flag.WRITE, Flag.FAST)),
    GETSET("getset", 3, Set.of(Flag.WRITE)),
    SETNX("setnx", 3, Set.of(Flag.WRITE, Flag.FAST)),
    SETEX("setex", 4, Set.of(Flag.WRITE, Flag.DENYOOM)),
    PSETEX("psetex", 4, Set.of(Flag.WRITE, Flag.DENYOOM)),
    GETRANGE("getrange", 4, Set.of(Flag.READONLY)),
    SETRANGE("setrange", 4, Set.of(Flag.WRITE, Flag.DENYOOM)),
    GETDEL("getdel", 2, Set.of(Flag.WRITE, Flag.FAST)),

    // List commands
    LPUSH("lpush", -3, Set.of(Flag.WRITE, Flag.DENYOOM, Flag.FAST)),
    RPUSH("rpush", -3, Set.of(Flag.WRITE, Flag.DENYOOM, Flag.FAST)),
    LPOP("lpop", -2, Set.of(Flag.WRITE, Flag.FAST)),
    RPOP("rpop", -2, Set.of(Flag.WRITE, Flag.FAST)),
    LLEN("llen", 2, Set.of(Flag.READONLY, Flag.FAST)),
    LRANGE("lrange", 4, Set.of(Flag.READONLY)),
    LINDEX("lindex", 3, Set.of(Flag.READONLY)),
    LSET("lset", 4, Set.of(Flag.WRITE)),
    LREM("lrem", 4, Set.of(Flag.WRITE)),
    LINSERT("linsert", 5, Set.of(Flag.WRITE)),
    LTRIM("ltrim", 4, Set.of(Flag.WRITE)),
    BLPOP("blpop", -3, Set.of(Flag.WRITE)),
    BRPOP("brpop", -3, Set.of(Flag.WRITE)),
    LMOVE("lmove", 5, Set.of(Flag.WRITE)),
    BLMOVE("blmove", 6, Set.of(Flag.WRITE)),

    // Set commands
    SADD("sadd", -3, Set.of(Flag.WRITE, Flag.DENYOOM, Flag.FAST)),
    SREM("srem", -3, Set.of(Flag.WRITE, Flag.FAST)),
    SMEMBERS("smembers", 2, Set.of(Flag.READONLY)),
    SISMEMBER("sismember", 3, Set.of(Flag.READONLY, Flag.FAST)),
    SCARD("scard", 2, Set.of(Flag.READONLY, Flag.FAST)),
    SINTER("sinter", -2, Set.of(Flag.READONLY)),
    SUNION("sunion", -2, Set.of(Flag.READONLY)),
    SDIFF("sdiff", -2, Set.of(Flag.READONLY)),
    SRANDMEMBER("srandmember", -2, Set.of(Flag.READONLY)),
    SPOP("spop", -2, Set.of(Flag.WRITE, Flag.FAST)),
    SMOVE("smove", 4, Set.of(Flag.WRITE, Flag.FAST)),

    // Sorted set commands
    ZADD("zadd", -4, Set.of(Flag.WRITE, Flag.DENYOOM, Flag.FAST)),
    ZREM("zrem", -3, Set.of(Flag.WRITE, Flag.FAST)),
    ZSCORE("zscore", 3, Set.of(Flag.READONLY, Flag.FAST)),
    ZRANK("zrank", 3, Set.of(Flag.READONLY, Flag.FAST)),
    ZREVRANK("zrevrank", 3, Set.of(Flag.READONLY, Flag.FAST)),
    ZRANGE("zrange", -4, Set.of(Flag.READONLY)),
    ZRANGEBYSCORE("zrangebyscore", -4, Set.of(Flag.READONLY)),
    ZRANGEBYLEX("zrangebylex", -4, Set.of(Flag.READONLY)),
    ZCARD("zcard", 2, Set.of(Flag.READONLY, Flag.FAST)),
    ZCOUNT("zcount", 4, Set.of(Flag.READONLY, Flag.FAST)),
    ZINCRBY("zincrby", 4, Set.of(Flag.WRITE, Flag.FAST)),
    ZINTERSTORE("zinterstore", -4, Set.of(Flag.WRITE)),
    ZUNIONSTORE("zunionstore", -4, Set.of(Flag.WRITE)),
    ZPOPMIN("zpopmin", -2, Set.of(Flag.WRITE, Flag.FAST)),
    ZPOPMAX("zpopmax", -2, Set.of(Flag.WRITE, Flag.FAST)),

    // Hash commands
    HSET("hset", -4, Set.of(Flag.WRITE, Flag.DENYOOM, Flag.FAST)),
    HGET("hget", 3, Set.of(Flag.READONLY, Flag.FAST)),
    HMSET("hmset", -4, Set.of(Flag.WRITE, Flag.DENYOOM)),
    HMGET("hmget", -3, Set.of(Flag.READONLY, Flag.FAST)),
    HDEL("hdel", -3, Set.of(Flag.WRITE, Flag.FAST)),
    HEXISTS("hexists", 3, Set.of(Flag.READONLY, Flag.FAST)),
    HLEN("hlen", 2, Set.of(Flag.READONLY, Flag.FAST)),
    HKEYS("hkeys", 2, Set.of(Flag.READONLY)),
    HVALS("hvals", 2, Set.of(Flag.READONLY)),
    HGETALL("hgetall", 2, Set.of(Flag.READONLY)),
    HINCRBY("hincrby", 4, Set.of(Flag.WRITE, Flag.FAST)),
    HINCRBYFLOAT("hincrbyfloat", 4, Set.of(Flag.WRITE, Flag.FAST)),
    HSETNX("hsetnx", 4, Set.of(Flag.WRITE, Flag.DENYOOM, Flag.FAST)),

    // Key commands
    DEL("del", -2, Set.of(Flag.WRITE)),
    EXISTS("exists", -2, Set.of(Flag.READONLY, Flag.FAST)),
    EXPIRE("expire", 3, Set.of(Flag.WRITE, Flag.FAST)),
    EXPIREAT("expireat", 3, Set.of(Flag.WRITE, Flag.FAST)),
    PEXPIRE("pexpire", 3, Set.of(Flag.WRITE, Flag.FAST)),
    TTL("ttl", 2, Set.of(Flag.READONLY, Flag.FAST)),
    PTTL("pttl", 2, Set.of(Flag.READONLY, Flag.FAST)),
    PERSIST("persist", 2, Set.of(Flag.WRITE, Flag.FAST)),
    TYPE("type", 2, Set.of(Flag.READONLY, Flag.FAST)),
    KEYS("keys", 2, Set.of(Flag.READONLY)),
    SCAN("scan", -2, Set.of(Flag.READONLY)),
    RENAME("rename", 3, Set.of(Flag.WRITE)),
    RANDOMKEY("randomkey", 1, Set.of(Flag.READONLY)),
    UNLINK("unlink", -2, Set.of(Flag.WRITE, Flag.FAST)),

    // Pub/Sub commands
    SUBSCRIBE("subscribe", -2, Set.of(Flag.PUBSUB)),
    UNSUBSCRIBE("unsubscribe", -1, Set.of(Flag.PUBSUB)),
    PSUBSCRIBE("psubscribe", -2, Set.of(Flag.PUBSUB)),
    PUNSUBSCRIBE("punsubscribe", -1, Set.of(Flag.PUBSUB)),
    PUBLISH("publish", 3, Set.of(Flag.PUBSUB, Flag.FAST)),
    PUBSUB("pubsub", -2, Set.of(Flag.PUBSUB)),

    // Stream commands
    XADD("xadd", -5, Set.of(Flag.WRITE, Flag.DENYOOM, Flag.FAST)),
    XLEN("xlen", 2, Set.of(Flag.READONLY, Flag.FAST)),
    XRANGE("xrange", -4, Set.of(Flag.READONLY)),
    XREVRANGE("xrevrange", -4, Set.of(Flag.READONLY)),
    XREAD("xread", -4, Set.of(Flag.READONLY)),
    XGROUP("xgroup", -2, Set.of(Flag.WRITE)),
    XREADGROUP("xreadgroup", -7, Set.of(Flag.WRITE)),
    XACK("xack", -4, Set.of(Flag.WRITE, Flag.FAST)),
    XPENDING("xpending", -3, Set.of(Flag.READONLY)),
    XCLAIM("xclaim", -6, Set.of(Flag.WRITE, Flag.FAST)),
    XTRIM("xtrim", -4, Set.of(Flag.WRITE)),

    // Transaction commands
    MULTI("multi", 1, Set.of(Flag.FAST)),
    EXEC("exec", 1, Set.of(Flag.SLOW)),
    DISCARD("discard", 1, Set.of(Flag.FAST)),
    WATCH("watch", -2, Set.of(Flag.FAST)),
    UNWATCH("unwatch", 1, Set.of(Flag.FAST)),

    // Server commands
    PING("ping", -1, Set.of(Flag.FAST)),
    ECHO("echo", 2, Set.of(Flag.FAST)),
    INFO("info", -1, Set.of(Flag.FAST)),
    DBSIZE("dbsize", 1, Set.of(Flag.READONLY, Flag.FAST)),
    FLUSHDB("flushdb", -1, Set.of(Flag.WRITE)),
    FLUSHALL("flushall", -1, Set.of(Flag.WRITE)),
    SELECT("select", 2, Set.of(Flag.FAST)),
    COMMAND("command", -1, Set.of(Flag.FAST)),
    CLIENT("client", -2, Set.of(Flag.ADMIN)),
    CONFIG("config", -2, Set.of(Flag.ADMIN)),
    DEBUG("debug", -2, Set.of(Flag.ADMIN)),
    HELLO("hello", -1, Set.of(Flag.FAST)),
    QUIT("quit", 1, Set.of(Flag.FAST)),
    RESET("reset", 1, Set.of(Flag.FAST)),

    // Scripting commands
    EVAL("eval", -3, Set.of(Flag.WRITE)),
    EVALSHA("evalsha", -3, Set.of(Flag.WRITE)),

    // Cluster commands
    CLUSTER("cluster", -2, Set.of(Flag.ADMIN));

    private final String name;
    private final int arity;
    private final Set<Flag> flags;

    RedisCommand(String name, int arity, Set<Flag> flags) {
        this.name = name;
        this.arity = arity;
        this.flags = flags;
    }

    /**
     * Returns the lowercase command name.
     *
     * @return command name
     */
    public String commandName() {
        return name;
    }

    /**
     * Returns the command arity. Positive = exact, negative = minimum.
     *
     * @return arity value
     */
    public int arity() {
        return arity;
    }

    /**
     * Returns the command flags.
     *
     * @return immutable set of flags
     */
    public Set<Flag> flags() {
        return flags;
    }

    /**
     * Command flags indicating behavior characteristics.
     */
    public enum Flag {
        WRITE, READONLY, DENYOOM, FAST, SLOW, ADMIN, PUBSUB
    }
}
