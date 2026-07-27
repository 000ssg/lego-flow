package ssg.legoflow.messaging.kafka.common;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ApiKeyTest {

    @Test
    void testProduceKey() {
        assertThat(ApiKey.PRODUCE.key()).isZero();
        assertThat(ApiKey.PRODUCE.apiName()).isEqualTo("Produce");
    }

    @Test
    void testFetchKey() {
        assertThat(ApiKey.FETCH.key()).isEqualTo((short) 1);
    }

    @Test
    void testApiVersionsKey() {
        assertThat(ApiKey.API_VERSIONS.key()).isEqualTo((short) 18);
    }

    @Test
    void testForKey() {
        assertThat(ApiKey.forKey((short) 0)).isEqualTo(ApiKey.PRODUCE);
        assertThat(ApiKey.forKey((short) 18)).isEqualTo(ApiKey.API_VERSIONS);
        assertThat(ApiKey.forKey((short) 999)).isNull();
    }

    @Test
    void testVersionRanges() {
        for (ApiKey ak : ApiKey.values()) {
            assertThat(ak.minVersion()).isLessThanOrEqualTo(ak.maxVersion());
        }
    }

    @Test
    void testAllKeysUnique() {
        var keys = new java.util.HashSet<Short>();
        for (ApiKey ak : ApiKey.values()) {
            assertThat(keys.add(ak.key())).as("Duplicate key: " + ak.key()).isTrue();
        }
    }
}
