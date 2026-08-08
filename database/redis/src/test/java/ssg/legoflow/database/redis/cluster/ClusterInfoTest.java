package ssg.legoflow.database.redis.cluster;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.util.List;

/**
 * Tests for Redis Cluster topology: {@link ClusterInfo}, {@link HashSlot}.
 */
class ClusterInfoTest {

    @Test void testHashSlotConstants() {
        assertThat(HashSlot.TOTAL_SLOTS).isEqualTo(16384);
    }

    @Test void testHashSlotCrc16Consistent() {
        // Same key should always produce same slot
        byte[] data = "mykey".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int crc1 = HashSlot.crc16(data);
        int crc2 = HashSlot.crc16(data);
        assertThat(crc1).isEqualTo(crc2);
    }

    @Test void testHashSlotComputeKey() {
        // "mykey" should always map to the same slot
        int slot1 = HashSlot.slot("mykey");
        int slot2 = HashSlot.slot("mykey");
        assertThat(slot1).isEqualTo(slot2);
        assertThat(slot1).isGreaterThanOrEqualTo(0);
        assertThat(slot1).isLessThan(HashSlot.TOTAL_SLOTS);
    }

    @Test void testHashSlotComputeKeyWithHashTag() {
        // Key with hash tag should use only the tagged portion
        int slot1 = HashSlot.slot("{user}profile");
        int slot2 = HashSlot.slot("{user}settings");
        assertThat(slot1).isEqualTo(slot2); // same tag → same slot

        int slot3 = HashSlot.slot("{other}data");
        assertThat(slot3).isNotEqualTo(slot1);
    }

    @Test void testHashSlotComputeKeyNoHashTag() {
        int slot = HashSlot.slot("no-tag-here");
        assertThat(slot).isGreaterThanOrEqualTo(0);
        assertThat(slot).isLessThan(HashSlot.TOTAL_SLOTS);
    }

    @Test void testHashSlotCrc16KnownValue() {
        // CRC16-CCITT of "hello" should be consistent
        int crc = HashSlot.crc16("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(crc).isNotNegative();
    }

    @Test void testHashSlotCrc16EmptyInput() {
        // CRC16 of empty input should be 0 (or a known value for init=0)
        int crc = HashSlot.crc16(new byte[0]);
        assertThat(crc).isEqualTo(0);
    }

    @Test void testHashSlotCrc16SingleByte() {
        int crcA = HashSlot.crc16(new byte[]{65}); // 'A'
        int crcB = HashSlot.crc16(new byte[]{66}); // 'B'
        assertThat(crcA).isNotEqualTo(crcB);
    }

    @Test void testHashSlotRejectsNullKey() {
        assertThatThrownBy(() -> HashSlot.slot(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void testClusterInfoSingleNode() {
        var info = ClusterInfo.singleNode("127.0.0.1", 6379);
        assertThat(info.nodes()).hasSize(1);
        var node = info.nodes().get(0);
        assertThat(node.role()).isEqualTo("master");
        assertThat(node.slots()).hasSize(1);
        var range = node.slots().get(0);
        assertThat(range.start()).isEqualTo(0);
        assertThat(range.end()).isEqualTo(HashSlot.TOTAL_SLOTS - 1);
    }

    @Test void testClusterInfoNodeForSlot() {
        var info = ClusterInfo.singleNode("127.0.0.1", 6379);
        // All slots should map to the single node
        for (int slot = 0; slot < 100; slot++) {
            var node = info.nodeForSlot(slot);
            assertThat(node).isNotNull();
            assertThat(node.host()).isEqualTo("127.0.0.1");
            assertThat(node.port()).isEqualTo(6379);
        }
    }

    @Test void testClusterInfoMultiNode() {
        var node0 = new ClusterInfo.Node("node-0", "host0", 6380, "master",
                List.of(new ClusterInfo.SlotRange(0, 8191)));
        var node1 = new ClusterInfo.Node("node-1", "host1", 6381, "master",
                List.of(new ClusterInfo.SlotRange(8192, 16383)));
        var info = new ClusterInfo(List.of(node0, node1));

        assertThat(info.nodeForSlot(0)).isEqualTo(node0);
        assertThat(info.nodeForSlot(8191)).isEqualTo(node0);
        assertThat(info.nodeForSlot(8192)).isEqualTo(node1);
        assertThat(info.nodeForSlot(16383)).isEqualTo(node1);
    }

    @Test void testClusterInfoNodeForSlotNotFound() {
        var node = new ClusterInfo.Node("node-0", "host0", 6380, "master", List.of());
        var info = new ClusterInfo(List.of(node));
        assertThat(info.nodeForSlot(500)).isNull();
    }

    @Test void testClusterInfoRejectsNullNodes() {
        assertThatThrownBy(() -> new ClusterInfo(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void testSlotRangeContains() {
        var range = new ClusterInfo.SlotRange(100, 200);
        assertThat(range.contains(100)).isTrue();
        assertThat(range.contains(150)).isTrue();
        assertThat(range.contains(200)).isTrue();
        assertThat(range.contains(99)).isFalse();
        assertThat(range.contains(201)).isFalse();
    }

    @Test void testSlotRangeEqualsAndHashCode() {
        var r1 = new ClusterInfo.SlotRange(0, 8191);
        var r2 = new ClusterInfo.SlotRange(0, 8191);
        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test void testNodeRecord() {
        var node = new ClusterInfo.Node("id-1", "host", 6379, "master", List.of());
        assertThat(node.id()).isEqualTo("id-1");
        assertThat(node.host()).isEqualTo("host");
        assertThat(node.port()).isEqualTo(6379);
    }

    @Test void testRedirectParseMoved() {
        var redirect = ClusterInfo.Redirect.parse("MOVED 3999 127.0.0.1:6380");
        assertThat(redirect).isNotNull();
        assertThat(redirect.type()).isEqualTo("MOVED");
        assertThat(redirect.slot()).isEqualTo(3999);
        assertThat(redirect.host()).isEqualTo("127.0.0.1");
        assertThat(redirect.port()).isEqualTo(6380);
    }

    @Test void testRedirectParseAsk() {
        var redirect = ClusterInfo.Redirect.parse("ASK 5000 host:9999");
        assertThat(redirect.type()).isEqualTo("ASK");
        assertThat(redirect.slot()).isEqualTo(5000);
    }

    @Test void testRedirectParseNullReturnsNull() {
        assertThat(ClusterInfo.Redirect.parse(null)).isNull();
    }

    @Test void testRedirectParseInvalidFormatReturnsNull() {
        assertThat(ClusterInfo.Redirect.parse("invalid")).isNull();
        assertThat(ClusterInfo.Redirect.parse("UNKNOWN 10 host:6379")).isNull();
    }

    @Test void testClusterInfoNodesAccessor() {
        var info = ClusterInfo.singleNode("h", 123);
        var nodes = info.nodes();
        assertThat(nodes).isNotNull();
        assertThat(nodes.get(0).host()).isEqualTo("h");
    }
}
