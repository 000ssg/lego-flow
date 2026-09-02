package ssg.legoflow.rpc.grpc.metadata;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class MetadataTest {

    @Test
    void testPutAndGet() {
        var md = new Metadata();
        md.put("key", "value");
        assertThat(md.get("key")).isEqualTo("value");
    }

    @Test
    void testCaseInsensitive() {
        var md = new Metadata();
        md.put("X-Custom", "value");
        assertThat(md.get("x-custom")).isEqualTo("value");
    }

    @Test
    void testAddMultipleValues() {
        var md = new Metadata();
        md.add("key", "v1");
        md.add("key", "v2");
        assertThat(md.getAll("key")).containsExactly("v1", "v2");
        assertThat(md.get("key")).isEqualTo("v1");
    }

    @Test
    void testPutReplacesExisting() {
        var md = new Metadata();
        md.put("key", "v1");
        md.put("key", "v2");
        assertThat(md.get("key")).isEqualTo("v2");
        assertThat(md.getAll("key")).containsExactly("v2");
    }

    @Test
    void testContainsKey() {
        var md = new Metadata();
        md.put("key", "value");
        assertThat(md.containsKey("key")).isTrue();
        assertThat(md.containsKey("missing")).isFalse();
    }

    @Test
    void testRemove() {
        var md = new Metadata();
        md.put("key", "value");
        md.remove("key");
        assertThat(md.containsKey("key")).isFalse();
    }

    @Test
    void testKeys() {
        var md = new Metadata();
        md.put("a", "1");
        md.put("b", "2");
        assertThat(md.keys()).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void testSize() {
        var md = new Metadata();
        assertThat(md.size()).isEqualTo(0);
        md.put("a", "1");
        assertThat(md.size()).isEqualTo(1);
        md.put("b", "2");
        assertThat(md.size()).isEqualTo(2);
    }

    @Test
    void testIsEmpty() {
        var md = new Metadata();
        assertThat(md.isEmpty()).isTrue();
        md.put("key", "value");
        assertThat(md.isEmpty()).isFalse();
    }

    @Test
    void testGetNonExistent() {
        var md = new Metadata();
        assertThat(md.get("missing")).isNull();
    }

    @Test
    void testGetAllNonExistent() {
        var md = new Metadata();
        assertThat(md.getAll("missing")).isEmpty();
    }

    @Test
    void testMerge() {
        var md1 = new Metadata().put("a", "1");
        var md2 = new Metadata().put("b", "2");
        md1.merge(md2);
        assertThat(md1.get("a")).isEqualTo("1");
        assertThat(md1.get("b")).isEqualTo("2");
    }

    @Test
    void testMergeNull() {
        var md = new Metadata().put("a", "1");
        md.merge(null);
        assertThat(md.size()).isEqualTo(1);
    }

    @Test
    void testTypedPutAndGet() {
        var md = new Metadata();
        var key = MetadataKey.of("x-request-id");
        md.put(key, "abc123");
        assertThat(md.get(key)).isEqualTo("abc123");
    }

    @Test
    void testFluentApi() {
        var md = new Metadata()
                .put("a", "1")
                .put("b", "2")
                .add("c", "3");
        assertThat(md.size()).isEqualTo(3);
    }
}
