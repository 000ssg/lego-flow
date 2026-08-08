package ssg.legoflow.network.ldap.filter;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FilterParserTest {

    @Test void testParseSimpleFilter() {
        var filter = FilterParser.parse("(cn=John)");
        assertThat(filter).isInstanceOf(SearchFilter.EqualityMatch.class);
    }

    @Test void testParseAndFilter() {
        var filter = FilterParser.parse("(&(cn=John)(sn=Doe))");
        assertThat(filter).isInstanceOf(SearchFilter.And.class);
    }

    @Test void testParseOrFilter() {
        var filter = FilterParser.parse("(|(cn=John)(sn=Doe))");
        assertThat(filter).isInstanceOf(SearchFilter.Or.class);
    }

    @Test void testParseNotFilter() {
        var filter = FilterParser.parse("(!(cn=John))");
        assertThat(filter).isInstanceOf(SearchFilter.Not.class);
    }

    @Test void testParseSubstringFilter() {
        var filter = FilterParser.parse("(cn=John*)");
        assertThat(filter).isInstanceOf(SearchFilter.Substrings.class);
    }

    @Test void testParseApproxMatch() {
        var filter = FilterParser.parse("(cn~=John)");
        assertThat(filter).isInstanceOf(SearchFilter.ApproxMatch.class);
    }

    @Test void testParseGreaterThanOrEqual() {
        var filter = FilterParser.parse("(age>=25)");
        assertThat(filter).isInstanceOf(SearchFilter.GreaterOrEqual.class);
    }

    @Test void testParseLessThanOrEqual() {
        var filter = FilterParser.parse("(age<=65)");
        assertThat(filter).isInstanceOf(SearchFilter.LessOrEqual.class);
    }

    @Test void testParsePresenceFilter() {
        var filter = FilterParser.parse("(mail=*)");
        assertThat(filter).isInstanceOf(SearchFilter.Present.class);
    }

    @Test void testParseNestedAndFilters() {
        var filter = FilterParser.parse("(&(&(cn=*)(sn=*))(objectClass=person))");
        assertThat(filter).isInstanceOf(SearchFilter.And.class);
    }

    @Test void testParseNullFilterThrows() {
        assertThatThrownBy(() -> FilterParser.parse(null))
                .isInstanceOf(FilterParseException.class);
    }

    @Test void testParseEmptyFilterThrows() {
        assertThatThrownBy(() -> FilterParser.parse(""))
                .isInstanceOf(FilterParseException.class);
    }

    @Test void testParseMissingParenthesisThrows() {
        assertThatThrownBy(() -> FilterParser.parse("(cn=John"))
                .isInstanceOf(FilterParseException.class);
    }

    @Test void testParseTrailingContentThrows() {
        assertThatThrownBy(() -> FilterParser.parse("(cn=John)extra"))
                .isInstanceOf(FilterParseException.class);
    }

    @Test void testParseEmptyAndFilterThrows() {
        assertThatThrownBy(() -> FilterParser.parse("(&)"))
                .isInstanceOf(FilterParseException.class);
    }

    @Test void testParseEmptyOrFilterThrows() {
        assertThatThrownBy(() -> FilterParser.parse("(|)"))
                .isInstanceOf(FilterParseException.class);
    }

    @Test void testParseSingleSubfilterInAnd() {
        var filter = FilterParser.parse("(&(cn=John))");
        assertThat(filter).isInstanceOf(SearchFilter.And.class);
        assertThat(((SearchFilter.And)filter).filters()).hasSize(1);
    }

    @Test void testParseOrWithMixedTypes() {
        var filter = FilterParser.parse("(|(cn=John)(sn=Doe))");
        assertThat(filter).isInstanceOf(SearchFilter.Or.class);
        assertThat(((SearchFilter.Or)filter).filters()).hasSize(2);
    }

    @Test void testParseComplexFilter() {
        var filter = FilterParser.parse(
                "(&(!(disabled=true))(objectClass=person)(|(title=Manager)(department=IT)))");
        assertThat(filter).isNotNull();
    }

    @Test void testParseSubstringWithMultipleParts() {
        var filter = FilterParser.parse("(name=J*e*nes*)");
        assertThat(filter).isInstanceOf(SearchFilter.Substrings.class);
    }

    @Test void testParseTrimmedInput() {
        var filter = FilterParser.parse("  (cn=John)  ");
        assertThat(filter).isNotNull();
    }
}
