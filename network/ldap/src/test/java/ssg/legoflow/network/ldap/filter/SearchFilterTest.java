package ssg.legoflow.network.ldap.filter;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SearchFilterTest {

    @Test
    void testEqualityMatch() {
        var filter = new SearchFilter.EqualityMatch("cn", "John".getBytes());
        assertThat(filter.tagNumber()).isEqualTo(3);
        assertThat(filter.attribute()).isEqualTo("cn");
    }

    @Test
    void testAndFilter() {
        var f1 = new SearchFilter.EqualityMatch("cn", "John".getBytes());
        var f2 = new SearchFilter.EqualityMatch("oc", "person".getBytes());
        var and = new SearchFilter.And(java.util.List.of(f1, f2));
        assertThat(and.tagNumber()).isEqualTo(0);
    }

    @Test
    void testOrFilter() {
        var f1 = new SearchFilter.EqualityMatch("cn", "John".getBytes());
        var f2 = new SearchFilter.EqualityMatch("cn", "Jane".getBytes());
        var or = new SearchFilter.Or(java.util.List.of(f1, f2));
        assertThat(or.tagNumber()).isEqualTo(1);
    }

    @Test
    void testNotFilter() {
        var inner = new SearchFilter.EqualityMatch("disabled", "true".getBytes());
        var not = new SearchFilter.Not(inner);
        assertThat(not.tagNumber()).isEqualTo(2);
    }

    @Test
    void testPresentFilter() {
        var filter = new SearchFilter.Present("mail");
        assertThat(filter.tagNumber()).isEqualTo(7);
    }

    @Test
    void testGreaterOrEqual() {
        var filter = new SearchFilter.GreaterOrEqual("age", "18".getBytes());
        assertThat(filter.tagNumber()).isEqualTo(5);
    }

    @Test
    void testLessOrEqual() {
        var filter = new SearchFilter.LessOrEqual("score", "100".getBytes());
        assertThat(filter.tagNumber()).isEqualTo(6);
    }

    @Test
    void testApproxMatch() {
        var filter = new SearchFilter.ApproxMatch("cn", "John".getBytes());
        assertThat(filter.tagNumber()).isEqualTo(8);
    }

    @Test
    void testSubstringsFilter() {
        var substrings = new SearchFilter.Substrings("sn", "Smi", 
            java.util.List.of(), "th");
        assertThat(substrings.tagNumber()).isEqualTo(4);
    }

    @Test
    void testAndWithEmptyListThrows() {
        assertThatThrownBy(() -> new SearchFilter.And(java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testStaticEqualityMatch() {
        var filter = SearchFilter.equalityMatch("cn", "John");
        assertThat(filter.attribute()).isEqualTo("cn");
    }

    @Test
    void testStaticPresent() {
        var filter = SearchFilter.present("mail");
        assertThat(filter.attribute()).isEqualTo("mail");
    }

    @Test
    void testStaticAnd() {
        var f1 = SearchFilter.equalityMatch("cn", "John");
        var f2 = SearchFilter.equalityMatch("oc", "person");
        var and = SearchFilter.and(f1, f2);
    }

    @Test
    void testStaticOr() {
        var f1 = SearchFilter.equalityMatch("cn", "John");
        var f2 = SearchFilter.equalityMatch("cn", "Jane");
        var or = SearchFilter.or(f1, f2);
    }
}
