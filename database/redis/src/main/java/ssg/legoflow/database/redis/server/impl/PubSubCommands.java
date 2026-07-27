package ssg.legoflow.database.redis.server.impl;

import ssg.legoflow.database.redis.command.CommandArgs;
import ssg.legoflow.database.redis.command.CommandRegistry;
import ssg.legoflow.database.redis.protocol.RespCodec;
import ssg.legoflow.database.redis.protocol.RespType;
import ssg.legoflow.database.redis.server.ClientConnection;
import ssg.legoflow.database.redis.server.PubSubManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implements Redis pub/sub commands: SUBSCRIBE, UNSUBSCRIBE, PSUBSCRIBE,
 * PUNSUBSCRIBE, PUBLISH, PUBSUB.
 *
 * @since 1.0.0
 */
public final class PubSubCommands {

    private PubSubCommands() {}

    /**
     * Registers all pub/sub commands with the given registry.
     *
     * @param registry the command registry
     */
    public static void register(CommandRegistry registry) {
        registry.register("SUBSCRIBE", PubSubCommands::subscribe);
        registry.register("UNSUBSCRIBE", PubSubCommands::unsubscribe);
        registry.register("PSUBSCRIBE", PubSubCommands::psubscribe);
        registry.register("PUNSUBSCRIBE", PubSubCommands::punsubscribe);
        registry.register("PUBLISH", PubSubCommands::publish);
        registry.register("PUBSUB", PubSubCommands::pubsub);
    }

    private static RespType subscribe(CommandArgs args, ClientConnection client) {
        PubSubManager psm = client.pubSubManager();
        String[] channels = new String[args.size() - 1];
        for (int i = 1; i < args.size(); i++) {
            channels[i - 1] = args.getString(i);
        }
        List<Long> counts = psm.subscribe(client, channels);

        // Send individual subscription confirmations
        for (int i = 0; i < channels.length; i++) {
            List<RespType> msg = List.of(
                    RespType.BulkString.of("subscribe"),
                    RespType.BulkString.of(channels[i]),
                    new RespType.Integer(counts.get(i)));
            client.writeRaw(RespCodec.encode(new RespType.Array(msg)));
        }
        return null; // Already sent directly
    }

    private static RespType unsubscribe(CommandArgs args, ClientConnection client) {
        PubSubManager psm = client.pubSubManager();
        String[] channels = new String[Math.max(0, args.size() - 1)];
        for (int i = 1; i < args.size(); i++) {
            channels[i - 1] = args.getString(i);
        }
        List<Long> counts = psm.unsubscribe(client, channels);

        String[] targets = channels.length > 0 ? channels : client.subscriptions().toArray(String[]::new);
        for (int i = 0; i < counts.size(); i++) {
            String ch = i < targets.length ? targets[i] : "";
            List<RespType> msg = List.of(
                    RespType.BulkString.of("unsubscribe"),
                    RespType.BulkString.of(ch),
                    new RespType.Integer(counts.get(i)));
            client.writeRaw(RespCodec.encode(new RespType.Array(msg)));
        }
        return null;
    }

    private static RespType psubscribe(CommandArgs args, ClientConnection client) {
        PubSubManager psm = client.pubSubManager();
        String[] patterns = new String[args.size() - 1];
        for (int i = 1; i < args.size(); i++) {
            patterns[i - 1] = args.getString(i);
        }
        List<Long> counts = psm.psubscribe(client, patterns);

        for (int i = 0; i < patterns.length; i++) {
            List<RespType> msg = List.of(
                    RespType.BulkString.of("psubscribe"),
                    RespType.BulkString.of(patterns[i]),
                    new RespType.Integer(counts.get(i)));
            client.writeRaw(RespCodec.encode(new RespType.Array(msg)));
        }
        return null;
    }

    private static RespType punsubscribe(CommandArgs args, ClientConnection client) {
        PubSubManager psm = client.pubSubManager();
        String[] patterns = new String[Math.max(0, args.size() - 1)];
        for (int i = 1; i < args.size(); i++) {
            patterns[i - 1] = args.getString(i);
        }
        List<Long> counts = psm.punsubscribe(client, patterns);

        String[] targets = patterns.length > 0 ? patterns : client.patternSubscriptions().toArray(String[]::new);
        for (int i = 0; i < counts.size(); i++) {
            String pat = i < targets.length ? targets[i] : "";
            List<RespType> msg = List.of(
                    RespType.BulkString.of("punsubscribe"),
                    RespType.BulkString.of(pat),
                    new RespType.Integer(counts.get(i)));
            client.writeRaw(RespCodec.encode(new RespType.Array(msg)));
        }
        return null;
    }

    private static RespType publish(CommandArgs args, ClientConnection client) {
        String channel = args.getString(1);
        String message = args.getString(2);
        long count = client.pubSubManager().publish(channel, message);
        return new RespType.Integer(count);
    }

    private static RespType pubsub(CommandArgs args, ClientConnection client) {
        String subcommand = args.getString(1).toUpperCase();
        PubSubManager psm = client.pubSubManager();

        return switch (subcommand) {
            case "CHANNELS" -> {
                String pattern = args.size() > 2 ? args.getString(2) : null;
                List<String> channels = psm.channels(pattern);
                List<RespType> result = new ArrayList<>();
                for (String ch : channels) {
                    result.add(RespType.BulkString.of(ch));
                }
                yield new RespType.Array(result);
            }
            case "NUMSUB" -> {
                String[] channels = new String[args.size() - 2];
                for (int i = 2; i < args.size(); i++) {
                    channels[i - 2] = args.getString(i);
                }
                Map<String, Long> counts = psm.numsub(channels);
                List<RespType> result = new ArrayList<>();
                for (var entry : counts.entrySet()) {
                    result.add(RespType.BulkString.of(entry.getKey()));
                    result.add(new RespType.Integer(entry.getValue()));
                }
                yield new RespType.Array(result);
            }
            case "NUMPAT" -> new RespType.Integer(psm.numpat());
            default -> new RespType.Error("ERR", "unknown PUBSUB subcommand '" + subcommand + "'");
        };
    }
}
