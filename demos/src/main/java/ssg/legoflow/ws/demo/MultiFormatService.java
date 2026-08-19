package ssg.legoflow.ws.demo;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http.header.MediaType;
import ssg.legoflow.ws.WebService;
import ssg.legoflow.ws.WebServiceContext;
import ssg.legoflow.ws.WebServiceDescriptor;
import ssg.legoflow.ws.content.ContentNegotiator;
import ssg.legoflow.ws.content.JsonCodec;
import ssg.legoflow.ws.content.XmlCodec;
import ssg.legoflow.ws.request.ResponseMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
public class MultiFormatService implements WebService {

    private final WebServiceDescriptor descriptor = new WebServiceDescriptor(
            "/info",
            Set.of(HttpMethod.GET),
            List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_XML, MediaType.TEXT_PLAIN),
            List.of(MediaType.APPLICATION_JSON));

    private final ContentNegotiator negotiator = new ContentNegotiator(
            List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_XML, MediaType.TEXT_PLAIN));
    private final JsonCodec jsonCodec = new JsonCodec();
    private final XmlCodec xmlCodec = new XmlCodec();
    private final ResponseMapper responseMapper = new ResponseMapper();

    @Override
    public WebServiceDescriptor getDescriptor() { return descriptor; }

    @Override
    public HttpResponse handle(WebServiceContext ctx, HttpRequest request) {
        var data = new LinkedHashMap<String, String>();
        data.put("service", "MultiFormat");
        data.put("version", "1.0");
        data.put("status", "running");

        var mediaType = negotiator.negotiate(request);
        if (mediaType == null) {
            return HttpResponse.of(HttpStatus.NOT_ACCEPTABLE, "Supported: application/json, text/xml, text/plain");
        }

        if (mediaType.matches(MediaType.APPLICATION_JSON)) {
            return responseMapper.json(HttpStatus.OK, jsonCodec.encode(data));
        } else if (mediaType.matches(MediaType.TEXT_XML)) {
            return responseMapper.xml(HttpStatus.OK, xmlCodec.encode("info", data));
        } else {
            var sb = new StringBuilder();
            for (var entry : data.entrySet()) {
                sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
            }
            return responseMapper.text(HttpStatus.OK, sb.toString());
        }
    }
}
