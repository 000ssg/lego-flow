package ssg.legoflow.upnp.controlpoint;

import org.junit.jupiter.api.*;
import ssg.legoflow.upnp.soap.SoapMessage;
import static org.assertj.core.api.Assertions.*;
import java.util.Map;
/**
 * Extended SOAP message tests for additional coverage.
 */
class DeviceProxyExtendedTest {

    @Test void testSoapRequestWithArguments() {
        var soap = SoapMessage.request(
                "urn:schemas-upnp-org:service:ContentDirectory:1",
                "Browse",
                Map.of("ObjectID", "0", "BrowseFlag", "BrowseDirectChildren")
        );
        
        assertThat(soap).isNotNull();
    }

    @Test void testSoapRequestWithSingleArgument() {
        var soap = SoapMessage.request(
                "urn:schemas-upnp-org:service:AVTransport:1",
                "Stop",
                Map.of("InstanceID", "0")
        );
        
        assertThat(soap).isNotNull();
    }

    @Test void testSoapRequestToStringContainsAction() {
        var soap = SoapMessage.request(
                "urn:schemas-upnp-org:service:RenderingControl:1",
                "SetVolume",
                Map.of("InstanceID", "0", "Channel", "Master")
        );
        
        String str = soap.serializeRequest();
        assertThat(str).isNotBlank().contains("SetVolume");
    }

    @Test void testSoapRequestMultipleParameters() {
        var soap = SoapMessage.request(
                "test:service:1",
                "MultiParamAction",
                Map.of("P1", "V1", "P2", "V2", "P3", "V3")
        );
        
        assertThat(soap).isNotNull();
    }

    @Test void testSoapResponseMessage() {
        var response = SoapMessage.response(
                "urn:schemas-upnp-org:service:ContentDirectory:1",
                "Browse",
                Map.of("Result", "...")
        );
        
        assertThat(response).isNotNull();
    }

}
