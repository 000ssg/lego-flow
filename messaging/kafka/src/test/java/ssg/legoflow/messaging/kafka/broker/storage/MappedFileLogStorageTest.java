package ssg.legoflow.messaging.kafka.broker.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class MappedFileLogStorageTest {

    @TempDir
    Path tempDir;

    private Path partitionDir() {
        return tempDir.resolve("test-topic-0");
    }

    @Test
    void testAppendAndFetch() {
        try (var storage = new MappedFileLogStorage(partitionDir())) {
            storage.append(0, 3, new byte[]{1, 2, 3}, 1000L);
            storage.append(3, 2, new byte[]{4, 5}, 2000L);

            List<StoredBatch> fetched = storage.fetch(0, 1024);
            assertThat(fetched).hasSize(2);
            assertThat(fetched.get(0).baseOffset()).isZero();
            assertThat(fetched.get(0).recordCount()).isEqualTo(3);
            assertThat(fetched.get(0).data()).containsExactly(1, 2, 3);
            assertThat(fetched.get(1).baseOffset()).isEqualTo(3);
        }
    }

    @Test
    void testFetchFromOffset() {
        try (var storage = new MappedFileLogStorage(partitionDir())) {
            storage.append(0, 2, new byte[]{1, 2}, 1000L);
            storage.append(2, 2, new byte[]{3, 4}, 2000L);
            storage.append(4, 2, new byte[]{5, 6}, 3000L);

            List<StoredBatch> fetched = storage.fetch(3, 1024);
            assertThat(fetched).hasSize(2);
            assertThat(fetched.get(0).baseOffset()).isEqualTo(2);
            assertThat(fetched.get(1).baseOffset()).isEqualTo(4);
        }
    }

    @Test
    void testFetchMaxBytes() {
        try (var storage = new MappedFileLogStorage(partitionDir())) {
            storage.append(0, 1, new byte[50], 1000L);
            storage.append(1, 1, new byte[50], 2000L);
            storage.append(2, 1, new byte[50], 3000L);

            List<StoredBatch> fetched = storage.fetch(0, 60);
            assertThat(fetched).hasSize(1);
        }
    }

    @Test
    void testDataSurvivesCloseAndReopen() {
        Path dir = partitionDir();

        // Write data
        try (var storage = new MappedFileLogStorage(dir)) {
            storage.append(0, 2, new byte[]{10, 20}, 1000L);
            storage.append(2, 3, new byte[]{30, 40, 50}, 2000L);
        }

        // Reopen and verify data persists
        try (var storage = new MappedFileLogStorage(dir)) {
            assertThat(storage.isEmpty()).isFalse();
            assertThat(storage.size()).isEqualTo(2);
            assertThat(storage.earliestOffset()).isZero();

            List<StoredBatch> fetched = storage.fetch(0, 1024);
            assertThat(fetched).hasSize(2);
            assertThat(fetched.get(0).data()).containsExactly(10, 20);
            assertThat(fetched.get(1).data()).containsExactly(30, 40, 50);
        }
    }

    @Test
    void testSegmentRotation() {
        Path dir = partitionDir();
        // Use a very small segment size to force rotation
        long smallSegment = 100; // 100 bytes per segment

        try (var storage = new MappedFileLogStorage(dir, smallSegment)) {
            // Each entry is 4 (length) + 24 (header) + data.length bytes
            // With 50-byte data: 78 bytes per entry. Second append should trigger rotation.
            storage.append(0, 1, new byte[50], 1000L);
            storage.append(1, 1, new byte[50], 2000L);
            storage.append(2, 1, new byte[50], 3000L);

            assertThat(storage.size()).isEqualTo(3);

            // Fetch all
            List<StoredBatch> fetched = storage.fetch(0, 10000);
            assertThat(fetched).hasSize(3);
            assertThat(fetched.get(0).baseOffset()).isZero();
            assertThat(fetched.get(1).baseOffset()).isEqualTo(1);
            assertThat(fetched.get(2).baseOffset()).isEqualTo(2);
        }
    }

    @Test
    void testSegmentRotationSurvivesReopen() {
        Path dir = partitionDir();
        long smallSegment = 100;

        try (var storage = new MappedFileLogStorage(dir, smallSegment)) {
            storage.append(0, 1, new byte[50], 1000L);
            storage.append(1, 1, new byte[50], 2000L);
            storage.append(2, 1, new byte[50], 3000L);
        }

        try (var storage = new MappedFileLogStorage(dir, smallSegment)) {
            assertThat(storage.size()).isEqualTo(3);
            List<StoredBatch> fetched = storage.fetch(0, 10000);
            assertThat(fetched).hasSize(3);
        }
    }

    @Test
    void testTruncateBefore() {
        try (var storage = new MappedFileLogStorage(partitionDir())) {
            storage.append(0, 2, new byte[]{1, 2}, 1000L);
            storage.append(2, 2, new byte[]{3, 4}, 2000L);
            storage.append(4, 2, new byte[]{5, 6}, 3000L);

            storage.truncateBefore(3);

            List<StoredBatch> fetched = storage.fetch(0, 1024);
            assertThat(fetched).isNotEmpty();
            assertThat(fetched.getFirst().baseOffset()).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void testTruncateBeforeAll() {
        try (var storage = new MappedFileLogStorage(partitionDir())) {
            storage.append(0, 2, new byte[]{1, 2}, 1000L);

            storage.truncateBefore(5);
            assertThat(storage.isEmpty()).isTrue();
        }
    }

    @Test
    void testReplaceBatches() {
        try (var storage = new MappedFileLogStorage(partitionDir())) {
            storage.append(0, 1, new byte[]{1}, 1000L);
            storage.append(1, 1, new byte[]{2}, 2000L);

            storage.replaceBatches(List.of(
                    new StoredBatch(5, 3, new byte[]{10, 20, 30}, 5000L)));

            assertThat(storage.size()).isEqualTo(1);
            assertThat(storage.earliestOffset()).isEqualTo(5);

            List<StoredBatch> fetched = storage.fetch(0, 1024);
            assertThat(fetched).hasSize(1);
            assertThat(fetched.getFirst().data()).containsExactly(10, 20, 30);
        }
    }

    @Test
    void testAllBatches() {
        try (var storage = new MappedFileLogStorage(partitionDir())) {
            storage.append(0, 1, new byte[]{1}, 1000L);
            storage.append(1, 1, new byte[]{2}, 2000L);

            List<StoredBatch> all = storage.allBatches();
            assertThat(all).hasSize(2);
        }
    }

    @Test
    void testIsEmpty() {
        try (var storage = new MappedFileLogStorage(partitionDir())) {
            assertThat(storage.isEmpty()).isTrue();

            storage.append(0, 1, new byte[]{1}, 1000L);
            assertThat(storage.isEmpty()).isFalse();
        }
    }

    @Test
    void testEarliestOffsetEmpty() {
        try (var storage = new MappedFileLogStorage(partitionDir())) {
            assertThat(storage.earliestOffset()).isEqualTo(-1);
        }
    }

    @Test
    void testLargeData() {
        try (var storage = new MappedFileLogStorage(partitionDir())) {
            byte[] largeData = new byte[64 * 1024]; // 64 KB
            for (int i = 0; i < largeData.length; i++) {
                largeData[i] = (byte) (i & 0xFF);
            }
            storage.append(0, 100, largeData, 1000L);

            List<StoredBatch> fetched = storage.fetch(0, 1024 * 1024);
            assertThat(fetched).hasSize(1);
            assertThat(fetched.getFirst().data()).isEqualTo(largeData);
        }
    }

    @Test
    void testMultipleAppendsAndRecovery() {
        Path dir = partitionDir();
        int batchCount = 50;

        try (var storage = new MappedFileLogStorage(dir)) {
            for (int i = 0; i < batchCount; i++) {
                storage.append(i, 1, new byte[]{(byte) i}, 1000L + i);
            }
        }

        try (var storage = new MappedFileLogStorage(dir)) {
            assertThat(storage.size()).isEqualTo(batchCount);

            List<StoredBatch> fetched = storage.fetch(0, 1024 * 1024);
            assertThat(fetched).hasSize(batchCount);
            for (int i = 0; i < batchCount; i++) {
                assertThat(fetched.get(i).baseOffset()).isEqualTo(i);
                assertThat(fetched.get(i).data()).containsExactly((byte) i);
            }
        }
    }

    @Test
    void testFetchBeyondData() {
        try (var storage = new MappedFileLogStorage(partitionDir())) {
            storage.append(0, 2, new byte[]{1, 2}, 1000L);

            List<StoredBatch> fetched = storage.fetch(10, 1024);
            assertThat(fetched).isEmpty();
        }
    }
}
