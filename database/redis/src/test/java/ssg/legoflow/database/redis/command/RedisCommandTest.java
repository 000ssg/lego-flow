package ssg.legoflow.database.redis.command;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RedisCommandTest {
    @Test void testCommandEnumValues() {
        var cmds = RedisCommand.values();
        assertThat(cmds).isNotEmpty();
    }
}
