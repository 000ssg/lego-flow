package ssg.legoflow.database.redis.client;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RedisClientTest {
    @Test void testClientCreation() {
        var client = new RedisClient("localhost", 6379);
        assertThat(client).isNotNull();
    }
}
