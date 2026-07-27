package ssg.legoflow.messaging.kafka.broker.storage;

import java.nio.file.Path;

/**
 * Factory for creating {@link LogStorage} instances for each partition.
 *
 * <p>Used by {@code KafkaBroker} to select the storage backend when creating
 * new partition logs. Two built-in factories are provided via static methods:
 * <ul>
 *   <li>{@link #inMemory()} — volatile in-memory storage (default)</li>
 *   <li>{@link #mappedFile(Path)} — durable memory-mapped file storage</li>
 * </ul>
 *
 * <p>Custom implementations can be provided as a lambda or method reference.
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface LogStorageFactory {

    /**
     * Creates a new storage instance for the given topic and partition.
     *
     * @param topic     the topic name
     * @param partition the partition index
     * @return a new log storage instance
     */
    LogStorage create(String topic, int partition);

    /**
     * Returns a factory that creates {@link InMemoryLogStorage} instances.
     *
     * @return an in-memory storage factory
     */
    static LogStorageFactory inMemory() {
        return (topic, partition) -> new InMemoryLogStorage();
    }

    /**
     * Returns a factory that creates {@link MappedFileLogStorage} instances
     * with the default segment size.
     *
     * <p>Files are stored under {@code <logDir>/<topic>-<partition>/}.
     *
     * @param logDir the root directory for all partition log files
     * @return a mapped-file storage factory
     */
    static LogStorageFactory mappedFile(Path logDir) {
        return (topic, partition) -> new MappedFileLogStorage(
                logDir.resolve(topic + "-" + partition));
    }

    /**
     * Returns a factory that creates {@link MappedFileLogStorage} instances
     * with a custom segment size.
     *
     * <p>Files are stored under {@code <logDir>/<topic>-<partition>/}.
     *
     * @param logDir       the root directory for all partition log files
     * @param segmentBytes the maximum segment file size in bytes
     * @return a mapped-file storage factory
     */
    static LogStorageFactory mappedFile(Path logDir, long segmentBytes) {
        return (topic, partition) -> new MappedFileLogStorage(
                logDir.resolve(topic + "-" + partition), segmentBytes);
    }
}
