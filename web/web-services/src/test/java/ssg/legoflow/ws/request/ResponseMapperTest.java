package ssg.legoflow.ws.request;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import ssg.legoflow.http.core.HttpStatus;

class ResponseMapperTest {

    @Test void testJsonResponse() {
        var mapper = new ResponseMapper();
        var response = mapper.json(HttpStatus.OK, "{\"key\":\"value\"}");
        assertThat(response).isNotNull();
    }

    @Test void testTextResponse() {
        var mapper = new ResponseMapper();
        var response = mapper.text(HttpStatus.OK, "Hello World");
        assertThat(response).isNotNull();
    }

    @Test void testXmlResponse() {
        var mapper = new ResponseMapper();
        var response = mapper.xml(HttpStatus.OK, "<root>data</root>");
        assertThat(response).isNotNull();
    }

    @Test void testNoContent() {
        var mapper = new ResponseMapper();
        var response = mapper.noContent();
        assertThat(response).isNotNull();
    }

    @Test void testNotFound() {
        var mapper = new ResponseMapper();
        var response = mapper.notFound("Resource not found");
        assertThat(response).isNotNull();
    }

    @Test void testBadRequest() {
        var mapper = new ResponseMapper();
        var response = mapper.badRequest("Invalid input");
        assertThat(response).isNotNull();
    }

    @Test void testJsonWithDifferentStatuses() {
        var mapper = new ResponseMapper();
        for (var status : HttpStatus.values()) {
            var response = mapper.json(status, "{}");
            assertThat(response).isNotNull();
        }
    }

    @Test void testResponseContentTypes() {
        var mapper = new ResponseMapper();
        var jsonResp = mapper.json(HttpStatus.OK, "{}");
        // Content-Type is a header, check it through headers
        assertThat(jsonResp.getHeaders().get("Content-Type")).contains("json");
        
        var textResp = mapper.text(HttpStatus.OK, "text");
        assertThat(textResp.getHeaders().get("Content-Type")).contains("text");
    }

    @Test void testJsonResponseStatusInResponse() {
        var mapper = new ResponseMapper();
        var response = mapper.json(HttpStatus.CREATED, "{}");
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED);
        
        response = mapper.text(HttpStatus.NOT_FOUND, "missing");
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test void testResponseWithLargeBody() {
        var mapper = new ResponseMapper();
        String largeJson = "{\"data\":\"" + "x".repeat(1000) + "\"}";
        var response = mapper.json(HttpStatus.OK, largeJson);
        assertThat(response).isNotNull();
    }

    @Test void testBadRequestStatus() {
        var mapper = new ResponseMapper();
        var response = mapper.badRequest("Invalid");
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test void testNoContentStatus() {
        var mapper = new ResponseMapper();
        var response = mapper.noContent();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
