package ssg.legoflow.messaging.kafka.broker.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class LogStorageFactoryTest {

    @TempDir
    Path tempDir;

    @Test
    void testInMemoryFactory() {
        var factory = LogStorageFactory.inMemory();
        try (LogStorage storage = factory.create("test-topic", 0)) {
            assertThat(storage).isInstanceOf(InMemoryLogStorage.class);
            assertThat(storage.isEmpty()).isTrue();
        }
    }

    @Test
    void testInMemoryFactoryCreatesDistinctInstances() {
        var factory = LogStorageFactory.inMemory();
        try (LogStorage s1 = factory.create("topic", 0);
             LogStorage s2 = factory.create("topic", 1)) {
            assertThat(s1).isNotSameAs(s2);
        }
    }

    @Test
    void testMappedFileFactory() {
        var factory = LogStorageFactory.mappedFile(tempDir);
        try (LogStorage storage = factory.create("test-topic", 0)) {
            assertThat(storage).isInstanceOf(MappedFileLogStorage.class);
            assertThat(storage.isEmpty()).isTrue();
        }
    }

    @Test
    void testMappedFileFactoryWithSegmentSize() {
        var factory = LogStorageFactory.mappedFile(tempDir, 1024 * 1024);
        try (LogStorage storage = factory.create("test-topic", 0)) {
            assertThat(storage).isInstanceOf(MappedFileLogStorage.class);
        }
    }

    @Test
    void testMappedFileFactoryCreatesPartitionSubdirectories() {
        var factory = LogStorageFactory.mappedFile(tempDir);
        try (LogStorage s1 = factory.create("orders", 0);
             LogStorage s2 = factory.create("orders", 1)) {
            // Both should work independently
            s1.append(0, 1, new byte[]{1}, 1000L);
            s2.append(0, 1, new byte[]{2}, 2000L);

            assertThat(s1.fetch(0, 1024).getFirst().data()).containsExactly(1);
            assertThat(s2.fetch(0, 1024).getFirst().data()).containsExactly(2);
        }
    }

    @Test
    void testCustomLambdaFactory() {
        LogStorageFactory factory = (topic, partition) -> new InMemoryLogStorage();
        try (LogStorage storage = factory.create("test", 0)) {
            assertThat(storage).isInstanceOf(InMemoryLogStorage.class);
        }
    }
}
