package ssg.legoflow.network.ldap.filter;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FilterParserExtendedTest {

    @Test void parseSimpleEquality() throws Exception {
        var parsed = FilterParser.parse("(cn=test)");
        assertThat(parsed).isNotNull();
    }

    @Test void parsePresentFilter() throws Exception {
        var parsed = FilterParser.parse("(mail=*)");
        assertThat(parsed).isNotNull();
    }

    @Test void parseAndFilter() throws Exception {
        var parsed = FilterParser.parse("(&(cn=test)(mail=*))");
        assertThat(parsed).isNotNull();
    }

    @Test void parseOrFilter() throws Exception {
        var parsed = FilterParser.parse("(|(cn=a)(cn=b))");
        assertThat(parsed).isNotNull();
    }

    @Test void parseNotFilter() throws Exception {
        var parsed = FilterParser.parse("(!(cn=test))");
        assertThat(parsed).isNotNull();
    }
}
