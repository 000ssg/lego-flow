package ssg.legoflow.interop.http;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import ssg.legoflow.http.core.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.net.Socket;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Interoperability test: Lego Flow HTTP client ↔ real nginx server.
 */
@Tag("web-protocols")
class HttpNginxInteropTest {

    private final String nginxHost = System.getProperty("interop.nginx.host", "localhost");
    private final int nginxPort = Integer.parseInt(System.getProperty("interop.nginx.port", "8080"));

    private final HttpProtocolCodec codec = new HttpProtocolCodec();

    @Test
    void testHealthEndpointReturnsOk() throws IOException {
        var request = HttpRequest.of(HttpMethod.GET, "/health");
        request.getHeaders().set("Host", nginxHost);
        request.getHeaders().set("Connection", "close");
        request.getHeaders().set("Connection", "close");
        HttpResponse response = sendAndReceive(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void testJsonApiEndpoint() throws IOException {
        var request = HttpRequest.of(HttpMethod.GET, "/api/data");
        request.getHeaders().set("Host", nginxHost);
        request.getHeaders().set("Connection", "close");
        HttpResponse response = sendAndReceive(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        String body = response.getBodyAsString();
        assertThat(body).contains("lego-flow-interop");
    }

    @Test
    void testEchoEndpointReflectsMethod() throws IOException {
        var request = HttpRequest.of(HttpMethod.POST, "/echo");
        request.getHeaders().set("Host", nginxHost);
        request.getHeaders().set("Connection", "close");
        HttpResponse response = sendAndReceive(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        String body = response.getBodyAsString();
        assertThat(body).contains("POST");
    }

    @Test
    void testHtmlHomepage() throws IOException {
        var request = HttpRequest.of(HttpMethod.GET, "/");
        request.getHeaders().set("Host", nginxHost);
        request.getHeaders().set("Connection", "close");
        HttpResponse response = sendAndReceive(request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        String body = response.getBodyAsString();
        assertThat(body).contains("<html>");
    }

    /**
     * Opens a fresh TCP socket, sends the request, reads the complete response,
     * and parses it with HttpProtocolCodec.
     *
     * NOTE: Do NOT close OutputStream/InputStream independently — closing either
     * one also closes the underlying Socket in Java. The try-with-resources on
     * the Socket itself handles cleanup for both streams.
     */
    private HttpResponse sendAndReceive(HttpRequest request) throws IOException {
        // Serialize request to bytes
        ByteBuffer requestData = codec.serializeRequest(request);
        requestData.rewind();
        byte[] reqBytes = new byte[requestData.remaining()];
        requestData.get(reqBytes);

        try (Socket socket = new Socket(nginxHost, nginxPort)) {
            socket.setSoTimeout(5000);

            // Write request — DO NOT use try-with-resources on the output stream
            OutputStream out = socket.getOutputStream();
            out.write(reqBytes);
            out.flush();

            // Read complete response — DO NOT use try-with-resources on input stream
            StringBuilder responseText = new StringBuilder();
            InputStream in = socket.getInputStream();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));

            String line;

            // Read status line
            line = reader.readLine();
            if (line == null || line.isEmpty()) {
                throw new IOException("Empty response from " + nginxHost + ":" + nginxPort);
            }
            responseText.append(line).append("\r\n");

            // Read headers until blank line
            while (!(line = reader.readLine()).isEmpty()) {
                responseText.append(line).append("\r\n");
            }
            responseText.append("\r\n");

            // Read body — accumulate all remaining data (nginx closes connection)
            char[] buf = new char[8192];
            int read;
            while ((read = reader.read(buf)) > 0) {
                responseText.append(buf, 0, read);
            }

            return codec.parseResponse(ByteBuffer.wrap(responseText.toString().getBytes(StandardCharsets.UTF_8)));
        } // Socket closes here, closing both in/out streams
    }
}
