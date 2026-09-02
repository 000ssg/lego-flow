package ssg.legoflow.service.cluster.coordination;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
class EtcdTransactionTest {

    private EtcdClient client;
    private EtcdKVStore store;

    @BeforeEach
    void setUp() {
        EtcdConfig config = EtcdConfig.builder().build();
        client = new EtcdClient(config);
        store = new EtcdKVStore(client);
    }

    @Test
    void cas_succeeds_on_emptyKey() {
        boolean result = EtcdTransaction.create(store, "/key", null)
                .thenPut("/key", "value".getBytes(StandardCharsets.UTF_8))
                .execute().join();

        assertThat(result).isTrue();
        assertThat(store.get("/key").join()).isEqualTo("value".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void cas_succeeds_on_matchingValue() {
        store.put("/key", "old".getBytes(StandardCharsets.UTF_8)).join();

        boolean result = EtcdTransaction.create(store, "/key", "old".getBytes(StandardCharsets.UTF_8))
                .thenPut("/key", "new".getBytes(StandardCharsets.UTF_8))
                .execute().join();

        assertThat(result).isTrue();
        assertThat(store.get("/key").join()).isEqualTo("new".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void cas_fails_on_mismatchedValue() {
        store.put("/key", "actual".getBytes(StandardCharsets.UTF_8)).join();

        boolean result = EtcdTransaction.create(store, "/key", "expected".getBytes(StandardCharsets.UTF_8))
                .thenPut("/key", "new".getBytes(StandardCharsets.UTF_8))
                .execute().join();

        assertThat(result).isFalse();
        assertThat(store.get("/key").join()).isEqualTo("actual".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void cas_fails_on_existingKey_whenExpectingNull() {
        store.put("/key", "existing".getBytes(StandardCharsets.UTF_8)).join();

        boolean result = EtcdTransaction.create(store, "/key", null)
                .thenPut("/key", "value".getBytes(StandardCharsets.UTF_8))
                .execute().join();

        assertThat(result).isFalse();
        assertThat(store.get("/key").join()).isEqualTo("existing".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void thenPut_withLease() throws Exception {
        try (EtcdLease lease = EtcdLease.grant(client, 30).join()) {
            boolean result = EtcdTransaction.create(store, "/leased", null)
                    .thenPutWithLease("/leased", "data".getBytes(StandardCharsets.UTF_8), lease)
                    .execute().join();

            assertThat(result).isTrue();
            assertThat(store.get("/leased").join()).isEqualTo("data".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void thenDelete() {
        store.put("/key", "value".getBytes(StandardCharsets.UTF_8)).join();

        boolean result = EtcdTransaction.create(store, "/key", "value".getBytes(StandardCharsets.UTF_8))
                .thenDelete("/key")
                .execute().join();

        assertThat(result).isTrue();
        assertThat(store.get("/key").join()).isNull();
    }

    @Test
    void multiple_ops_inOneTransaction() {
        boolean result = EtcdTransaction.create(store, "/coord", null)
                .thenPut("/coord", "acquired".getBytes(StandardCharsets.UTF_8))
                .thenPut("/status", "running".getBytes(StandardCharsets.UTF_8))
                .execute().join();

        assertThat(result).isTrue();
        assertThat(store.get("/coord").join()).isEqualTo("acquired".getBytes(StandardCharsets.UTF_8));
        assertThat(store.get("/status").join()).isEqualTo("running".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void nullStore_throws() {
        assertThatThrownBy(() -> EtcdTransaction.create(null, "/key", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullCompareKey_throws() {
        assertThatThrownBy(() -> EtcdTransaction.create(store, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullPutKey_throws() {
        assertThatThrownBy(() ->
                EtcdTransaction.create(store, "/key", null)
                        .thenPut(null, "v".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullPutValue_throws() {
        assertThatThrownBy(() ->
                EtcdTransaction.create(store, "/key", null)
                        .thenPut("/key", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullDeleteKey_throws() {
        assertThatThrownBy(() ->
                EtcdTransaction.create(store, "/key", null)
                        .thenDelete(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullLease_inThenPutWithLease_throws() {
        assertThatThrownBy(() ->
                EtcdTransaction.create(store, "/key", null)
                        .thenPutWithLease("/key", "v".getBytes(StandardCharsets.UTF_8), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void operationType_enum() {
        assertThat(EtcdTransaction.OperationType.PUT.name()).isEqualTo("PUT");
        assertThat(EtcdTransaction.OperationType.DELETE.name()).isEqualTo("DELETE");
        assertThat(EtcdTransaction.OperationType.PUT_WITH_LEASE.name()).isEqualTo("PUT_WITH_LEASE");
    }
}
