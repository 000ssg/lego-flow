package ssg.legoflow.service.manager;

import ssg.legoflow.service.channel.ChannelPipeline;
import ssg.legoflow.service.channel.DataChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
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
    private final ByteBuffer readBuffer;

    public ProcessingThread(DataChannel channel, ChannelPipeline pipeline, int bufferSize) {
        this.channel = channel;
        this.pipeline = pipeline;
        this.readBuffer = ByteBuffer.allocate(bufferSize);
    }

    public void processReadable() {
        PROCESSING_POOL.submit(() -> {
            try {
                readBuffer.clear();
                int bytesRead = channel.read(readBuffer);
                if (bytesRead > 0) {
                    readBuffer.flip();
                    pipeline.fireRead(channel, readBuffer);
                } else if (bytesRead < 0) {
                    pipeline.fireDisconnect(channel);
                }
            } catch (Exception e) {
                LOG.error("Error processing read", e);
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
