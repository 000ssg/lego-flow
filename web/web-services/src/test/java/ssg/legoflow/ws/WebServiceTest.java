package ssg.legoflow.ws;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http.header.MediaType;
import ssg.legoflow.ws.demo.HelloWorldService;
import ssg.legoflow.ws.demo.EchoWebService;
import ssg.legoflow.ws.demo.TodoApiService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class WebServiceTest {

    @Test
    void testHelloWorldDescriptor() {
        var service = new HelloWorldService();
        var descriptor = service.getDescriptor();
        assertThat(descriptor.path()).isEqualTo("/hello");
        assertThat(descriptor.methods()).containsExactly(HttpMethod.GET);
    }

    @Test
    void testEchoDescriptor() {
        var service = new EchoWebService();
        var descriptor = service.getDescriptor();
        assertThat(descriptor.path()).isEqualTo("/echo");
        assertThat(descriptor.methods()).containsExactly(HttpMethod.POST);
    }

    @Test
    void testTodoDescriptor() {
        var service = new TodoApiService();
        var descriptor = service.getDescriptor();
        assertThat(descriptor.path()).isEqualTo("/todos");
        assertThat(descriptor.methods()).contains(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE);
    }

    @Test
    void testDescriptorDefaultProducesJson() {
        var descriptor = new WebServiceDescriptor("/test", java.util.Set.of(HttpMethod.GET));
        assertThat(descriptor.produces()).containsExactly(MediaType.APPLICATION_JSON);
        assertThat(descriptor.consumes()).containsExactly(MediaType.APPLICATION_JSON);
    }

    @Test
    void testDescriptorSinglePathDefaults() {
        var descriptor = new WebServiceDescriptor("/simple");
        assertThat(descriptor.path()).isEqualTo("/simple");
        assertThat(descriptor.methods()).containsExactly(HttpMethod.GET);
    }

    @Test
    void testWebServiceIsInterface() {
        assertThat(WebService.class.isInterface()).isTrue();
    }
}
