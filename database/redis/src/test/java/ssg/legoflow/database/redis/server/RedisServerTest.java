package ssg.legoflow.database.redis.server;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RedisServerTest {
    @Test void testServerCreation() {
        var server = new RedisServer();
        assertThat(server).isNotNull();
    }
}
