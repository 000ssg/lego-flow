package ssg.legoflow.database.redis.client;

import ssg.legoflow.database.redis.protocol.RespCodec;
import ssg.legoflow.database.redis.protocol.RespType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Pipelines multiple Redis commands for batch execution.
 *
 * <p>Commands are buffered locally and sent in a single TCP write
 * when {@link #execute()} is called, then all responses are read
 * in order. This reduces round-trip latency.
 *
 * @since 0.1.0
 */
public final class RedisPipeline {

    private final RedisClient client;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(1024);
    private int commandCount = 0;

    RedisPipeline(RedisClient client) {
        this.client = client;
    }

    /**
     * Adds a command to the pipeline.
     *
     * @param args command name and arguments
     * @return this pipeline for chaining
     */
    public RedisPipeline add(String... args) {
        byte[] encoded = RespCodec.encodeCommand(args);
        buffer.writeBytes(encoded);
        commandCount++;
        return this;
    }

    /**
     * Returns the number of queued commands.
     *
     * @return command count
     */
    public int size() {
        return commandCount;
    }

    /**
     * Executes all queued commands and returns their responses in order.
     *
     * @return list of responses, one per command
     * @throws IOException if I/O fails
     */
    public List<RespType> execute() throws IOException {
        if (commandCount == 0) return List.of();

        // Send all commands in one write
        client.sendRaw(buffer.toByteArray());

        // Read all responses
        List<RespType> responses = new ArrayList<>(commandCount);
        for (int i = 0; i < commandCount; i++) {
            responses.add(client.receive());
        }

        // Reset
        buffer.reset();
        int count = commandCount;
        commandCount = 0;

        return responses;
    }

    /**
     * Discards all queued commands without executing them.
     */
    public void discard() {
        buffer.reset();
        commandCount = 0;
    }
}
