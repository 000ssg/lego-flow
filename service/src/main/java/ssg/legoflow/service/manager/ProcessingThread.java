package ssg.legoflow.service.manager;

import ssg.legoflow.service.channel.ChannelPipeline;
import ssg.legoflow.service.channel.DataChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
/**
 * Processes I/O events for a single channel by dispatching reads, writes, and
 * connects through the associated {@link ChannelPipeline}.
 *
 * <h2>Stream contract</h2>
 * <p>{@link #processReadable()} passes a single read's worth of data to the pipeline
 * via {@link ChannelPipeline#fireRead}. It intentionally does <em>not</em> accumulate
 * bytes across reads — that responsibility belongs to the codec layer within the
 * pipeline. Codecs (e.g. {@code Http2FrameCodec}, {@code LdapCodec}) are expected to
 * be stateful stream transformers that maintain an internal accumulator, combine each
 * incoming chunk with any previously buffered partial data, extract complete protocol
 * units, and save the remainder for subsequent reads. Partial data arriving in a single
 * read is a normal condition, not an error.
 */
public class ProcessingThread {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessingThread.class);
    private static final ThreadFactory THREAD_FACTORY = 
        Thread.ofVirtual().name("processing-thread-").factory();
    private static final ExecutorService PROCESSING_POOL = 
        Executors.newThreadPerTaskExecutor(THREAD_FACTORY);

    private final DataChannel channel;
    private final ChannelPipeline pipeline;
    final ByteBuffer readBuffer;

    public ProcessingThread(DataChannel channel, ChannelPipeline pipeline, int bufferSize) {
        this.channel = channel;
        this.pipeline = pipeline;
        this.readBuffer = ByteBuffer.allocate(bufferSize);
    }

    /**
     * Reads available data from the channel and forwards it to the pipeline.
     *
     * <p>Used by {@link ServiceGroup} (separate public path from
     * {@code SelectableChannelManager}, which reads synchronously in the selector thread).
     * Reads a single chunk (up to the buffer capacity); on EOF fires disconnect instead.
     * Any bytes not consumed by the pipeline stay in {@code readBuffer} and are drained
     * on the next call (partial-consumption contract: handlers advance buffer position).
     */
    public void processReadable() {
        PROCESSING_POOL.submit(() -> {
            try {
                // Drain any remainder from a previous partially-consumed flush first
                if (readBuffer.position() > 0 && readBuffer.hasRemaining()) {
                    pipeline.fireRead(channel, readBuffer);
                    if (readBuffer.hasRemaining()) return; // backpressure: wait for consumption
                }
                readBuffer.clear();
                int n;
                while ((n = channel.read(readBuffer)) > 0) {
                    if (!readBuffer.hasRemaining()) break;
                }
                if (n < 0) {
                    pipeline.fireDisconnect(channel);
                    return;
                }
                readBuffer.flip();
                if (readBuffer.hasRemaining()) {
                    pipeline.fireRead(channel, readBuffer);
                    // remainder stays in readBuffer for the next cycle
                }
            } catch (Exception e) {
                LOG.error("Error processing read", e);
                pipeline.fireError(channel, e);
            }
        });
    }

    public void processDisconnect() {
        PROCESSING_POOL.submit(() -> {
            try {
                pipeline.fireDisconnect(channel);
            } catch (Exception e) {
                LOG.error("Error firing disconnect on {}", channel, e);
                pipeline.fireError(channel, e);
            }
        });
    }

    public void processWritable() {
        PROCESSING_POOL.submit(() -> {
            try {
                pipeline.fireWrite(channel);
            } catch (Exception e) {
                LOG.error("Error processing write", e);
                pipeline.fireError(channel, e);
            }
        });
    }

    public void processConnectable() {
        PROCESSING_POOL.submit(() -> {
            try {
                pipeline.fireConnect(channel);
            } catch (Exception e) {
                LOG.error("Error processing connect", e);
                pipeline.fireError(channel, e);
            }
        });
    }
}
