package ssg.legoflow.wamp.demo.base;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class MultiRealmDemoTest {

    @Test
    void testMultiRealmDemoCreatesExpectedRealms() {
        var demo = new MultiRealmDemo();
        var result = demo.run();

        assertThat(result.realmCount()).isEqualTo(2);
    }

    @Test
    void testRealmAlphaRpcDoubles() {
        var demo = new MultiRealmDemo();
        var result = demo.run();

        var alphaResult = result.rpcResultsByRealm().get("realm.alpha");
        assertThat(alphaResult).hasSize(1);
        assertThat(((Number) alphaResult.getFirst()).intValue()).isEqualTo(20);
    }

    @Test
    void testRealmBetaRpcNegates() {
        var demo = new MultiRealmDemo();
        var result = demo.run();

        var betaResult = result.rpcResultsByRealm().get("realm.beta");
        assertThat(betaResult).hasSize(1);
        assertThat(((Number) betaResult.getFirst()).intValue()).isEqualTo(-7);
    }

    @Test
    void testRealmAlphaPubSubReceivesAlphaEvents() {
        var demo = new MultiRealmDemo();
        var result = demo.run();

        assertThat(result.pubSubEventsByRealm().get("realm.alpha"))
                .containsExactly("alpha-msg");
    }

    @Test
    void testRealmBetaPubSubReceivesBetaEvents() {
        var demo = new MultiRealmDemo();
        var result = demo.run();

        assertThat(result.pubSubEventsByRealm().get("realm.beta"))
                .containsExactly("beta-msg");
    }

    @Test
    void testRealmIsolationNoCrossRealmProcedures() {
        var demo = new MultiRealmDemo();
        assertThat(demo.verifyRealmIsolation()).isTrue();
    }

    @Test
    void testEachRealmHasOwnRpcResults() {
        var demo = new MultiRealmDemo();
        var result = demo.run();

        assertThat(result.rpcResultsByRealm()).containsKeys("realm.alpha", "realm.beta");
        assertThat(result.rpcResultsByRealm().get("realm.alpha"))
                .isNotEqualTo(result.rpcResultsByRealm().get("realm.beta"));
    }

    @Test
    void testEachRealmHasOwnPubSubEvents() {
        var demo = new MultiRealmDemo();
        var result = demo.run();

        assertThat(result.pubSubEventsByRealm()).containsKeys("realm.alpha", "realm.beta");
        assertThat(result.pubSubEventsByRealm().get("realm.alpha"))
                .isNotEqualTo(result.pubSubEventsByRealm().get("realm.beta"));
    }
}
