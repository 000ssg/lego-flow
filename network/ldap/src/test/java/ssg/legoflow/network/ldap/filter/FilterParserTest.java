package ssg.legoflow.network.ldap.filter;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link FilterParser}.
 *
 * @since 1.0.0
 */
class FilterParserTest {

    @Test
    void testParseEqualityMatch() {
        SearchFilter filter = FilterParser.parse("(cn=John Doe)");
        assertThat(filter).isInstanceOf(SearchFilter.EqualityMatch.class);
        var eq = (SearchFilter.EqualityMatch) filter;
        assertThat(eq.attribute()).isEqualTo("cn");
        assertThat(new String(eq.value())).isEqualTo("John Doe");
    }

    @Test
    void testParsePresenceFilter() {
        SearchFilter filter = FilterParser.parse("(objectClass=*)");
        assertThat(filter).isInstanceOf(SearchFilter.Present.class);
        assertThat(((SearchFilter.Present) filter).attribute()).isEqualTo("objectClass");
    }

    @Test
    void testParseSubstringInitial() {
        SearchFilter filter = FilterParser.parse("(cn=John*)");
        assertThat(filter).isInstanceOf(SearchFilter.Substrings.class);
        var sub = (SearchFilter.Substrings) filter;
        assertThat(sub.attribute()).isEqualTo("cn");
        assertThat(sub.initial()).isEqualTo("John");
        assertThat(sub.finalStr()).isNull();
    }

    @Test
    void testParseSubstringFinal() {
        SearchFilter filter = FilterParser.parse("(cn=*Doe)");
        assertThat(filter).isInstanceOf(SearchFilter.Substrings.class);
        var sub = (SearchFilter.Substrings) filter;
        assertThat(sub.initial()).isNull();
        assertThat(sub.finalStr()).isEqualTo("Doe");
    }

    @Test
    void testParseSubstringAny() {
        SearchFilter filter = FilterParser.parse("(cn=*mid*)");
        assertThat(filter).isInstanceOf(SearchFilter.Substrings.class);
        var sub = (SearchFilter.Substrings) filter;
        assertThat(sub.initial()).isNull();
        assertThat(sub.any()).containsExactly("mid");
        assertThat(sub.finalStr()).isNull();
    }

    @Test
    void testParseSubstringComplex() {
        SearchFilter filter = FilterParser.parse("(cn=Jo*n D*e)");
        assertThat(filter).isInstanceOf(SearchFilter.Substrings.class);
        var sub = (SearchFilter.Substrings) filter;
        assertThat(sub.initial()).isEqualTo("Jo");
        assertThat(sub.any()).containsExactly("n D");
        assertThat(sub.finalStr()).isEqualTo("e");
    }

    @Test
    void testParseGreaterOrEqual() {
        SearchFilter filter = FilterParser.parse("(age>=18)");
        assertThat(filter).isInstanceOf(SearchFilter.GreaterOrEqual.class);
        var ge = (SearchFilter.GreaterOrEqual) filter;
        assertThat(ge.attribute()).isEqualTo("age");
        assertThat(new String(ge.value())).isEqualTo("18");
    }

    @Test
    void testParseLessOrEqual() {
        SearchFilter filter = FilterParser.parse("(age<=65)");
        assertThat(filter).isInstanceOf(SearchFilter.LessOrEqual.class);
    }

    @Test
    void testParseApproxMatch() {
        SearchFilter filter = FilterParser.parse("(cn~=John)");
        assertThat(filter).isInstanceOf(SearchFilter.ApproxMatch.class);
    }

    @Test
    void testParseAndFilter() {
        SearchFilter filter = FilterParser.parse("(&(objectClass=person)(cn=John*))");
        assertThat(filter).isInstanceOf(SearchFilter.And.class);
        var and = (SearchFilter.And) filter;
        assertThat(and.filters()).hasSize(2);
        assertThat(and.filters().get(0)).isInstanceOf(SearchFilter.EqualityMatch.class);
        assertThat(and.filters().get(1)).isInstanceOf(SearchFilter.Substrings.class);
    }

    @Test
    void testParseOrFilter() {
        SearchFilter filter = FilterParser.parse("(|(cn=John)(cn=Jane))");
        assertThat(filter).isInstanceOf(SearchFilter.Or.class);
        var or = (SearchFilter.Or) filter;
        assertThat(or.filters()).hasSize(2);
    }

    @Test
    void testParseNotFilter() {
        SearchFilter filter = FilterParser.parse("(!(cn=John))");
        assertThat(filter).isInstanceOf(SearchFilter.Not.class);
        var not = (SearchFilter.Not) filter;
        assertThat(not.filter()).isInstanceOf(SearchFilter.EqualityMatch.class);
    }

    @Test
    void testParseNestedFilters() {
        SearchFilter filter = FilterParser.parse("(&(objectClass=person)(|(cn=John)(cn=Jane)))");
        assertThat(filter).isInstanceOf(SearchFilter.And.class);
        var and = (SearchFilter.And) filter;
        assertThat(and.filters()).hasSize(2);
        assertThat(and.filters().get(1)).isInstanceOf(SearchFilter.Or.class);
    }

    @Test
    void testParseAndWithWhitespace() {
        SearchFilter filter = FilterParser.parse("(& (objectClass=person) (cn=John*))");
        assertThat(filter).isInstanceOf(SearchFilter.And.class);
        var and = (SearchFilter.And) filter;
        assertThat(and.filters()).hasSize(2);
    }

    @Test
    void testParseNullThrows() {
        assertThatThrownBy(() -> FilterParser.parse(null))
                .isInstanceOf(FilterParseException.class);
    }

    @Test
    void testParseEmptyThrows() {
        assertThatThrownBy(() -> FilterParser.parse(""))
                .isInstanceOf(FilterParseException.class);
    }

    @Test
    void testFilterToString() {
        SearchFilter filter = SearchFilter.and(
                SearchFilter.equalityMatch("objectClass", "person"),
                SearchFilter.present("cn")
        );
        assertThat(filter.toString()).isEqualTo("(&(objectClass=person)(cn=*))");
    }
}
