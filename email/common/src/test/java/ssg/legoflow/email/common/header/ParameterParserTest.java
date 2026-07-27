package ssg.legoflow.email.common.header;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ParameterParser}.
 */
class ParameterParserTest {

    @Test
    void testParseSimpleParameter() {
        Map<String, String> params = ParameterParser.parse("text/plain; charset=utf-8");
        assertThat(params.get("charset")).isEqualTo("utf-8");
    }

    @Test
    void testParseQuotedParameter() {
        Map<String, String> params = ParameterParser.parse(
                "multipart/mixed; boundary=\"----=_Part_123\"");
        assertThat(params.get("boundary")).isEqualTo("----=_Part_123");
    }

    @Test
    void testParseMultipleParameters() {
        Map<String, String> params = ParameterParser.parse(
                "text/plain; charset=utf-8; name=\"test.txt\"");
        assertThat(params.get("charset")).isEqualTo("utf-8");
        assertThat(params.get("name")).isEqualTo("test.txt");
    }

    @Test
    void testParseRfc2231Continuation() {
        // RFC 2231 parameter value continuation
        Map<String, String> params = ParameterParser.parse(
                "attachment; filename*0=\"very\"; filename*1=\"long\"; filename*2=\"name.txt\"");
        assertThat(params.get("filename")).isEqualTo("verylongname.txt");
    }

    @Test
    void testParseRfc2231CharsetEncoded() {
        // RFC 2231 charset-encoded parameter
        Map<String, String> params = ParameterParser.parse(
                "attachment; filename*=UTF-8''t%C3%A9st.txt");
        assertThat(params.get("filename")).isEqualTo("tést.txt");
    }

    @Test
    void testParseEmpty() {
        assertThat(ParameterParser.parse(null)).isEmpty();
        assertThat(ParameterParser.parse("")).isEmpty();
    }

    @Test
    void testSerialize() {
        Map<String, String> params = Map.of("charset", "utf-8");
        String result = ParameterParser.serialize(params);
        assertThat(result).isEqualTo("; charset=utf-8");
    }

    @Test
    void testSerializeWithQuoting() {
        Map<String, String> params = Map.of("boundary", "----=_Part_123");
        String result = ParameterParser.serialize(params);
        assertThat(result).contains("boundary=\"----=_Part_123\"");
    }

    @Test
    void testSerializeEmpty() {
        assertThat(ParameterParser.serialize(null)).isEmpty();
        assertThat(ParameterParser.serialize(Map.of())).isEmpty();
    }

    @Test
    void testUnquote() {
        assertThat(ParameterParser.unquote("\"hello\"")).isEqualTo("hello");
        assertThat(ParameterParser.unquote("\"he\\\"llo\"")).isEqualTo("he\"llo");
        assertThat(ParameterParser.unquote("hello")).isEqualTo("hello");
    }

    @Test
    void testParseParameterWithEscapedQuotes() {
        Map<String, String> params = ParameterParser.parse(
                "attachment; filename=\"file \\\"name\\\".txt\"");
        assertThat(params.get("filename")).isEqualTo("file \"name\".txt");
    }

    @Test
    void testParseCaseInsensitiveNames() {
        Map<String, String> params = ParameterParser.parse(
                "text/plain; Charset=UTF-8");
        assertThat(params.get("charset")).isEqualTo("UTF-8");
    }
}
