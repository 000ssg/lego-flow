package ssg.legoflow.service.cluster.coordination;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class EtcdKVStoreTest {

    private EtcdClient client;
    private EtcdKVStore store;

    @BeforeEach
    void setUp() {
        EtcdConfig config = EtcdConfig.builder().build();
        client = new EtcdClient(config);
        store = new EtcdKVStore(client);
    }

    @Test
    void put_and_get() {
        byte[] value = "hello".getBytes(StandardCharsets.UTF_8);
        store.put("/test/key", value).join();

        byte[] result = store.get("/test/key").join();
        assertThat(result).isEqualTo(value);
    }

    @Test
    void get_nonExistent_returnsNull() {
        assertThat(store.get("/missing").join()).isNull();
    }

    @Test
    void getAsString() {
        store.put("/test/string", "world".getBytes(StandardCharsets.UTF_8)).join();
        String result = store.getAsString("/test/string").join();
        assertThat(result).isEqualTo("world");
    }

    @Test
    void getAsString_nonExistent_returnsNull() {
        assertThat(store.getAsString("/missing").join()).isNull();
    }

    @Test
    void delete_existing() {
        store.put("/test/key", "value".getBytes(StandardCharsets.UTF_8)).join();
        boolean existed = store.delete("/test/key").join();

        assertThat(existed).isTrue();
        assertThat(store.get("/test/key").join()).isNull();
    }

    @Test
    void delete_nonExistent() {
        boolean existed = store.delete("/missing").join();
        assertThat(existed).isFalse();
    }

    @Test
    void range_prefix() {
        store.put("/app/config/color", "blue".getBytes(StandardCharsets.UTF_8)).join();
        store.put("/app/config/size", "large".getBytes(StandardCharsets.UTF_8)).join();
        store.put("/app/data/item", "x".getBytes(StandardCharsets.UTF_8)).join();

        Map<String, byte[]> result = store.range("/app/config/").join();
        assertThat(result).hasSize(2);
        assertThat(new String(result.get("/app/config/color"))).isEqualTo("blue");
        assertThat(new String(result.get("/app/config/size"))).isEqualTo("large");
    }

    @Test
    void range_noMatch() {
        store.put("/a/key", "v".getBytes(StandardCharsets.UTF_8)).join();
        Map<String, byte[]> result = store.range("/b/").join();
        assertThat(result).isEmpty();
    }

    @Test
    void revision_increments() {
        assertThat(store.revision()).isZero();

        store.put("/k1", "v1".getBytes(StandardCharsets.UTF_8)).join();
        assertThat(store.revision()).isEqualTo(1);

        store.put("/k2", "v2".getBytes(StandardCharsets.UTF_8)).join();
        assertThat(store.revision()).isEqualTo(2);

        store.delete("/k1").join();
        assertThat(store.revision()).isEqualTo(3);
    }

    @Test
    void put_with_lease() throws Exception {
        try (EtcdLease lease = EtcdLease.grant(client, 30).join()) {
            store.put("/leased", "data".getBytes(StandardCharsets.UTF_8), lease).join();
            byte[] result = store.get("/leased").join();
            assertThat(result).isEqualTo("data".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void put_nullKey_throws() {
        assertThatThrownBy(() -> store.put(null, "v".getBytes(StandardCharsets.UTF_8)).join())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void put_nullValue_throws() {
        assertThatThrownBy(() -> store.put("/key", (byte[]) null).join())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void get_nullKey_throws() {
        assertThatThrownBy(() -> store.get(null).join())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void delete_nullKey_throws() {
        assertThatThrownBy(() -> store.delete(null).join())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void range_nullPrefix_throws() {
        assertThatThrownBy(() -> store.range(null).join())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void close_clearsStore() {
        store.put("/k", "v".getBytes(StandardCharsets.UTF_8)).join();
        store.close();

        assertThat(store.get("/k").join()).isNull();
        assertThat(store.revision()).isZero();
    }

    @Test
    void returns_clonedValues() {
        byte[] original = "data".getBytes(StandardCharsets.UTF_8);
        store.put("/key", original).join();

        byte[] result = store.get("/key").join();
        assertThat(result).isNotSameAs(original);
        result[0] = 'X';

        byte[] after = store.get("/key").join();
        assertThat(after[0]).isEqualTo((byte) 'd');
    }

    @Test
    void put_nullLease_ok() {
        store.put("/key", "v".getBytes(StandardCharsets.UTF_8), null).join();
        assertThat(store.get("/key").join()).isEqualTo("v".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void toString_containsInfo() {
        store.put("/k", "v".getBytes(StandardCharsets.UTF_8)).join();
        String s = store.toString();
        assertThat(s).contains("EtcdKVStore");
        assertThat(s).contains("size=1");
        assertThat(s).contains("revision=1");
    }
}
