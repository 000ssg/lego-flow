package ssg.legoflow.database.redis.server.impl;

import ssg.legoflow.database.redis.command.CommandArgs;
import ssg.legoflow.database.redis.command.CommandRegistry;
import ssg.legoflow.database.redis.command.RedisCommand;
import ssg.legoflow.database.redis.protocol.RespType;
import ssg.legoflow.database.redis.protocol.RespVersion;
import ssg.legoflow.database.redis.server.ClientConnection;
import java.util.*;
/**
 * Implements Redis server commands: PING, ECHO, INFO, DBSIZE, FLUSHDB, FLUSHALL,
 * SELECT, COMMAND, CLIENT, CONFIG, DEBUG, HELLO, QUIT, RESET, EVAL, EVALSHA, CLUSTER.
 *
 * @since 0.1.0
 */
public final class ServerCommands {

    private static final RespType OK = new RespType.SimpleString("OK");
    private static final RespType PONG = new RespType.SimpleString("PONG");

    private ServerCommands() {}

    /**
     * Registers all server commands with the given registry.
     *
     * @param registry the command registry
     */
    public static void register(CommandRegistry registry) {
        registry.register("PING", ServerCommands::ping);
        registry.register("ECHO", ServerCommands::echo);
        registry.register("INFO", ServerCommands::info);
        registry.register("DBSIZE", ServerCommands::dbsize);
        registry.register("FLUSHDB", ServerCommands::flushdb);
        registry.register("FLUSHALL", ServerCommands::flushall);
        registry.register("SELECT", ServerCommands::select);
        registry.register("COMMAND", ServerCommands::command);
        registry.register("CLIENT", ServerCommands::client);
        registry.register("CONFIG", ServerCommands::config);
        registry.register("DEBUG", ServerCommands::debug);
        registry.register("HELLO", ServerCommands::hello);
        registry.register("QUIT", ServerCommands::quit);
        registry.register("RESET", ServerCommands::reset);
        registry.register("EVAL", ServerCommands::eval);
        registry.register("EVALSHA", ServerCommands::evalsha);
        registry.register("CLUSTER", ServerCommands::cluster);
    }

    private static RespType ping(CommandArgs args, ClientConnection client) {
        if (args.size() > 1) {
            return RespType.BulkString.of(args.getString(1));
        }
        return PONG;
    }

    private static RespType echo(CommandArgs args, ClientConnection client) {
        return RespType.BulkString.of(args.getString(1));
    }

    private static RespType info(CommandArgs args, ClientConnection client) {
        String section = args.size() > 1 ? args.getString(1).toLowerCase() : "all";
        StringBuilder sb = new StringBuilder();

        if ("all".equals(section) || "server".equals(section)) {
            sb.append("# Server\r\n");
            sb.append("redis_version:7.0.0-legoflow\r\n");
            sb.append("redis_mode:standalone\r\n");
            sb.append("os:").append(System.getProperty("os.name")).append("\r\n");
            sb.append("tcp_port:").append(client.server().port()).append("\r\n");
            sb.append("\r\n");
        }
        if ("all".equals(section) || "keyspace".equals(section)) {
            sb.append("# Keyspace\r\n");
            for (int i = 0; i < 16; i++) {
                int size = client.server().getDatabase(i).size();
                if (size > 0) {
                    sb.append("db").append(i).append(":keys=").append(size).append(",expires=0\r\n");
                }
            }
            sb.append("\r\n");
        }
        if ("all".equals(section) || "clients".equals(section)) {
            sb.append("# Clients\r\n");
            sb.append("connected_clients:").append(client.server().connectedClients()).append("\r\n");
            sb.append("\r\n");
        }
        if ("all".equals(section) || "memory".equals(section)) {
            sb.append("# Memory\r\n");
            sb.append("used_memory:").append(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()).append("\r\n");
            sb.append("used_memory_human:").append(formatBytes(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())).append("\r\n");
            sb.append("\r\n");
        }

        return RespType.BulkString.of(sb.toString());
    }

    private static RespType dbsize(CommandArgs args, ClientConnection client) {
        return new RespType.Integer(client.database().size());
    }

    private static RespType flushdb(CommandArgs args, ClientConnection client) {
        client.database().flush();
        return OK;
    }

    private static RespType flushall(CommandArgs args, ClientConnection client) {
        for (int i = 0; i < 16; i++) {
            client.server().getDatabase(i).flush();
        }
        return OK;
    }

    private static RespType select(CommandArgs args, ClientConnection client) {
        int index = args.getInt(1);
        if (index < 0 || index > 15) {
            return new RespType.Error("ERR", "DB index is out of range");
        }
        client.selectDb(index);
        return OK;
    }

    private static RespType command(CommandArgs args, ClientConnection client) {
        if (args.size() == 1) {
            // COMMAND — list all commands
            List<RespType> result = new ArrayList<>();
            for (RedisCommand cmd : RedisCommand.values()) {
                result.add(new RespType.Array(List.of(
                        RespType.BulkString.of(cmd.commandName()),
                        new RespType.Integer(cmd.arity()),
                        new RespType.Array(List.of(RespType.BulkString.of("fast"))),
                        new RespType.Integer(0),
                        new RespType.Integer(0),
                        new RespType.Integer(0))));
            }
            return new RespType.Array(result);
        }

        String sub = args.getString(1).toUpperCase();
        if ("COUNT".equals(sub)) {
            return new RespType.Integer(RedisCommand.values().length);
        }
        if ("DOCS".equals(sub) || "INFO".equals(sub)) {
            return new RespType.Array(List.of());
        }
        return new RespType.Error("ERR", "unknown COMMAND subcommand '" + sub + "'");
    }

    private static RespType client(CommandArgs args, ClientConnection c) {
        String sub = args.getString(1).toUpperCase();
        return switch (sub) {
            case "ID" -> new RespType.Integer(c.id());
            case "SETNAME" -> {
                c.setClientName(args.getString(2));
                yield OK;
            }
            case "GETNAME" -> {
                String name = c.clientName();
                yield name != null ? RespType.BulkString.of(name) : RespType.BulkString.NULL;
            }
            case "LIST" -> {
                StringBuilder sb = new StringBuilder();
                sb.append("id=").append(c.id());
                sb.append(" addr=").append(c.address());
                sb.append(" name=").append(c.clientName() != null ? c.clientName() : "");
                sb.append(" db=").append(c.selectedDb());
                yield RespType.BulkString.of(sb.toString());
            }
            default -> new RespType.Error("ERR", "unknown CLIENT subcommand '" + sub + "'");
        };
    }

    private static RespType config(CommandArgs args, ClientConnection client) {
        String sub = args.getString(1).toUpperCase();
        return switch (sub) {
            case "GET" -> {
                String param = args.getString(2);
                List<RespType> result = new ArrayList<>();
                // Return empty for most configs, handle known ones
                if ("save".equals(param) || "*".equals(param)) {
                    result.add(RespType.BulkString.of("save"));
                    result.add(RespType.BulkString.of(""));
                }
                if ("databases".equals(param) || "*".equals(param)) {
                    result.add(RespType.BulkString.of("databases"));
                    result.add(RespType.BulkString.of("16"));
                }
                yield new RespType.Array(result);
            }
            case "SET" -> OK;
            case "RESETSTAT" -> OK;
            default -> new RespType.Error("ERR", "unknown CONFIG subcommand '" + sub + "'");
        };
    }

    private static RespType debug(CommandArgs args, ClientConnection client) {
        String sub = args.getString(1).toUpperCase();
        if ("SLEEP".equals(sub) && args.size() > 2) {
            double seconds = args.getDouble(2);
            try {
                Thread.sleep((long) (seconds * 1000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return OK;
        }
        return OK;
    }

    private static RespType hello(CommandArgs args, ClientConnection client) {
        int protoVersion = args.size() > 1 ? args.getInt(1) : 2;
        if (protoVersion != 2 && protoVersion != 3) {
            return new RespType.Error("NOPROTO", "unsupported protocol version");
        }

        // Process AUTH and SETNAME if present
        for (int i = 2; i < args.size(); i++) {
            String opt = args.getString(i).toUpperCase();
            if ("SETNAME".equals(opt) && i + 1 < args.size()) {
                client.setClientName(args.getString(++i));
            }
        }

        client.setRespVersion(RespVersion.of(protoVersion));

        if (protoVersion == 3) {
            // RESP3: return a map
            Map<RespType, RespType> map = new LinkedHashMap<>();
            map.put(new RespType.SimpleString("server"), new RespType.SimpleString("redis"));
            map.put(new RespType.SimpleString("version"), new RespType.SimpleString("7.0.0-legoflow"));
            map.put(new RespType.SimpleString("proto"), new RespType.Integer(3));
            map.put(new RespType.SimpleString("id"), new RespType.Integer(client.id()));
            map.put(new RespType.SimpleString("mode"), new RespType.SimpleString("standalone"));
            map.put(new RespType.SimpleString("role"), new RespType.SimpleString("master"));
            map.put(new RespType.SimpleString("modules"), new RespType.Array(List.of()));
            return new RespType.RespMap(map);
        } else {
            // RESP2: return a flat array
            return new RespType.Array(List.of(
                    RespType.BulkString.of("server"), RespType.BulkString.of("redis"),
                    RespType.BulkString.of("version"), RespType.BulkString.of("7.0.0-legoflow"),
                    RespType.BulkString.of("proto"), new RespType.Integer(2),
                    RespType.BulkString.of("id"), new RespType.Integer(client.id()),
                    RespType.BulkString.of("mode"), RespType.BulkString.of("standalone"),
                    RespType.BulkString.of("role"), RespType.BulkString.of("master"),
                    RespType.BulkString.of("modules"), new RespType.Array(List.of())));
        }
    }

    private static RespType quit(CommandArgs args, ClientConnection client) {
        return OK;
    }

    private static RespType reset(CommandArgs args, ClientConnection client) {
        client.selectDb(0);
        client.setRespVersion(RespVersion.RESP2);
        client.setClientName(null);
        client.transactionState().reset();
        client.pubSubManager().removeClient(client);
        return new RespType.SimpleString("RESET");
    }

    private static RespType eval(CommandArgs args, ClientConnection client) {
        return new RespType.Error("ERR",
                "Lua scripting is not supported in this implementation. " +
                "No Lua or JavaScript engine available.");
    }

    private static RespType evalsha(CommandArgs args, ClientConnection client) {
        return new RespType.Error("NOSCRIPT", "No matching script. Use EVAL instead.");
    }

    private static RespType cluster(CommandArgs args, ClientConnection client) {
        String sub = args.getString(1).toUpperCase();
        return switch (sub) {
            case "INFO" -> RespType.BulkString.of(
                    "cluster_enabled:0\r\n" +
                    "cluster_state:ok\r\n" +
                    "cluster_slots_assigned:16384\r\n" +
                    "cluster_slots_ok:16384\r\n" +
                    "cluster_known_nodes:1\r\n" +
                    "cluster_size:1\r\n");
            case "MYID" -> RespType.BulkString.of("legoflow-node-0001");
            case "NODES" -> RespType.BulkString.of(
                    "legoflow-node-0001 127.0.0.1:" + client.server().port() +
                    "@" + (client.server().port() + 10000) +
                    " myself,master - 0 0 1 connected 0-16383\r\n");
            case "SLOTS" -> new RespType.Array(List.of(
                    new RespType.Array(List.of(
                            new RespType.Integer(0),
                            new RespType.Integer(16383),
                            new RespType.Array(List.of(
                                    RespType.BulkString.of("127.0.0.1"),
                                    new RespType.Integer(client.server().port()),
                                    RespType.BulkString.of("legoflow-node-0001")))))));
            case "KEYSLOT" -> {
                String key = args.getString(2);
                yield new RespType.Integer(
                        ssg.legoflow.database.redis.cluster.HashSlot.slot(key));
            }
            default -> new RespType.Error("ERR", "unknown CLUSTER subcommand '" + sub + "'");
        };
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.2fK", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.2fM", bytes / (1024.0 * 1024));
        return String.format("%.2fG", bytes / (1024.0 * 1024 * 1024));
    }
}
