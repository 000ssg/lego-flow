package ssg.legoflow.service;

import ssg.legoflow.service.scope.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ScopeTest {

    @Test
    void testScopeIdGenerated() {
        var scope = new SessionScope();
        assertThat(scope.getId()).isNotNull().isNotEmpty();
    }

    @Test
    void testScopeCustomId() {
        var scope = new SessionScope("custom-id");
        assertThat(scope.getId()).isEqualTo("custom-id");
    }

    @Test
    void testScopeAttributes() {
        var scope = new RequestScope();
        scope.setAttribute("key", "value");
        assertThat(scope.<String>getAttribute("key")).isEqualTo("value");
    }

    @Test
    void testScopeRemoveAttribute() {
        var scope = new RequestScope();
        scope.setAttribute("key", "value");
        scope.setAttribute("key", null);
        assertThat(scope.<String>getAttribute("key")).isNull();
    }

    @Test
    void testScopeGetAttributes() {
        var scope = new ApplicationScope();
        scope.setAttribute("a", 1);
        scope.setAttribute("b", 2);
        assertThat(scope.getAttributes()).hasSize(2).containsEntry("a", 1).containsEntry("b", 2);
    }

    @Test
    void testScopeDestroy() {
        var scope = new SiteScope();
        scope.setAttribute("key", "value");
        scope.destroy();
        assertThat(scope.getAttributes()).isEmpty();
    }

    @Test
    void testDifferentScopeTypes() {
        assertThat(new SiteScope()).isInstanceOf(Scope.class);
        assertThat(new ApplicationScope()).isInstanceOf(Scope.class);
        assertThat(new SessionScope()).isInstanceOf(Scope.class);
        assertThat(new RequestScope()).isInstanceOf(Scope.class);
    }

    @Test
    void testScopeIsolation() {
        var session1 = new SessionScope();
        var session2 = new SessionScope();
        session1.setAttribute("key", "val1");
        session2.setAttribute("key", "val2");
        assertThat(session1.<String>getAttribute("key")).isEqualTo("val1");
        assertThat(session2.<String>getAttribute("key")).isEqualTo("val2");
    }
}
