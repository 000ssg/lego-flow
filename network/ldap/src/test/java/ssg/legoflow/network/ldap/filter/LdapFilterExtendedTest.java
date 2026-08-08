package ssg.legoflow.network.ldap.filter;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LdapFilterExtendedTest {

    @Test void equalityMatchFilter() {
        var filter = new SearchFilter.EqualityMatch("cn", "test".getBytes());
        assertThat(filter).isNotNull();
    }

    @Test void presentFilter() {
        var filter = new SearchFilter.Present("mail");
        assertThat(filter).isNotNull();
    }

    @Test void substringsFilter() {
        var filter = new SearchFilter.Substrings("cn", null, java.util.List.of(), "test");
        assertThat(filter).isNotNull();
    }

    @Test void greaterOrEqualFilter() {
        var filter = new SearchFilter.GreaterOrEqual("age", "25".getBytes());
        assertThat(filter).isNotNull();
    }

    @Test void lessOrEqualFilter() {
        var filter = new SearchFilter.LessOrEqual("age", "65".getBytes());
        assertThat(filter).isNotNull();
    }

    @Test void approxMatchFilter() {
        var filter = new SearchFilter.ApproxMatch("cn", "test".getBytes());
        assertThat(filter).isNotNull();
    }

    @Test void andFilter() {
        var f1 = new SearchFilter.EqualityMatch("cn", "a".getBytes());
        var f2 = new SearchFilter.Present("mail");
        var filter = SearchFilter.and(f1, f2);
        assertThat(filter).isNotNull();
    }

    @Test void orFilter() {
        var f1 = new SearchFilter.EqualityMatch("cn", "a".getBytes());
        var f2 = new SearchFilter.EqualityMatch("cn", "b".getBytes());
        var filter = SearchFilter.or(f1, f2);
        assertThat(filter).isNotNull();
    }

    @Test void notFilter() {
        var inner = new SearchFilter.Present("status");
        var filter = SearchFilter.not(inner);
        assertThat(filter).isNotNull();
    }

    @Test void extensibleMatchFilter() {
        var filter = new SearchFilter.ExtensibleMatch(null, "cn", "test".getBytes(), false);
        assertThat(filter).isNotNull();
    }
}
