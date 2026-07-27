# UPnP/DLNA Module — Development Guide

## Module Purpose

The `upnp` module implements UPnP Device Architecture (UDA) and DLNA guidelines for device discovery, media sharing, and playback control on local networks. It builds on `service` (UDP/multicast), `http` (server/descriptions), and `web-services` (XML endpoints).

## Key Interfaces

- `ControlPoint` — orchestrates discovery, description, invocation, and event subscription
- `ContentDirectory` — Browse/Search with DIDL-Lite XML responses
- `AVTransport` — playback control with state machine
- `RenderingControl` — volume and mute control
- `SsdpDiscovery` — multicast M-SEARCH and NOTIFY handling (single and multi-interface)
- `MultiInterfaceSsdpService` — aggregates SSDP discovery across multiple network interfaces
- `SsdpService` (ServiceGroup integration) — opt-in constructor `SsdpService(NetworkInterface, ServiceGroup)` registers with ServiceGroup event loop instead of blocking receive thread; `SsdpChannelHandler` bridges pipeline events to `processMessage()`
- `SoapClient` / `SoapEndpoint` — SOAP action invocation
- `GenaSubscription` — event subscription lifecycle

## Package Breakdown

| Package | Purpose |
|---------|---------|
| `ssdp` | SSDP multicast discovery: M-SEARCH requests, NOTIFY advertisements, device cache with expiry, multi-interface support via `MultiInterfaceSsdpService` |
| `device` | Device and service description model: UDN, device type URNs, service type URNs, SCPD parsing |
| `soap` | SOAP 1.1 envelope construction, action dispatch, fault parsing, XML namespaces |
| `gena` | GENA event subscription: SUBSCRIBE/UNSUBSCRIBE HTTP methods, NOTIFY callback handling, SID management |
| `dlna` | DLNA profiles (DLNA.ORG_PN), protocolInfo parsing, media format capabilities |
| `mediaserver` | Media server implementation: ContentDirectory provider, ConnectionManager, DIDL-Lite generation |
| `mediarenderer` | Media renderer implementation: AVTransport state machine, RenderingControl channel management |
| `controlpoint` | Control point: device discovery flow, description cache, action invocation helpers, subscription lifecycle |
| `demo` | Demo applications: discovery, browsing, playback, events, server |

## UPnP-Specific Coding Conventions

### XML Namespaces
- Device descriptions: `urn:schemas-upnp-org:device-1-0`
- Service descriptions (SCPD): `urn:schemas-upnp-org:service-1-0`
- SOAP envelopes: `http://schemas.xmlsoap.org/soap/envelope/`
- DIDL-Lite: `urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/`
- Dublin Core: `http://purl.org/dc/elements/1.1/`
- UPnP metadata: `urn:schemas-upnp-org:metadata-1-0/upnp/`

### Standard URNs
- `urn:schemas-upnp-org:device:MediaServer:1`
- `urn:schemas-upnp-org:device:MediaRenderer:1`
- `urn:schemas-upnp-org:service:ContentDirectory:1`
- `urn:schemas-upnp-org:service:AVTransport:1`
- `urn:schemas-upnp-org:service:RenderingControl:1`
- `urn:schemas-upnp-org:service:ConnectionManager:1`

### DIDL-Lite Conventions
- Items have `<res>` elements with protocolInfo attributes
- protocolInfo format: `http-get:*:audio/mpeg:DLNA.ORG_PN=MP3`
- Containers have `childCount` attribute and `@parentID` reference
- Use `dc:title`, `upnp:class`, `dc:creator` for metadata

### State Machine (AVTransport)
- States: `NO_MEDIA_PRESENT`, `STOPPED`, `PLAYING`, `PAUSED_PLAYBACK`, `TRANSITIONING`
- Transitions: SetAVTransportURI -> STOPPED, Play -> PLAYING, Pause -> PAUSED_PLAYBACK, Stop -> STOPPED
- `TransportState` is a sealed interface with pattern matching

## Testing Practices

- Unit tests for each protocol layer (SSDP, SOAP, GENA) independently
- Integration tests that compose multiple layers (control point -> SSDP -> description -> SOAP)
- Demo functional tests exercising complete workflows (discover -> browse -> play)
- All tests use in-memory or loopback transports (no actual network multicast required)
- DIDL-Lite XML round-trip tests: generate -> parse -> verify
- AVTransport state machine tests: verify all valid/invalid transitions
- GENA subscription lifecycle tests: subscribe -> notify -> renew -> unsubscribe
- Test count: 418

## Network Diagnostics

When SSDP discovery fails to find real devices, the most common cause is VPN/security software capturing the multicast route. Key diagnostic commands:

```bash
# Check multicast route (most critical)
netstat -rn -f inet | grep 224
# Should show en0 without ! flag

# Fix VPN-captured multicast route
sudo route delete -net 224.0.0.0/4
sudo route add -net 224.0.0.0/4 -interface en0

# Check interfaces
ifconfig | grep -E "^[a-z]|inet |status:"
```

The code handles this gracefully:
- `SsdpService` catches `NoRouteToHostException` with diagnostic log messages
- `ControlPoint(List<NetworkInterface>)` tries all interfaces, skips failures
- `findAllPhysicalInterfaces()` prefers `en*`/`eth*`/`wlan*` over `utun*` (VPN tunnels)

See [ARCHITECTURE.md — UPnP/SSDP Network Diagnostics](doc/ARCHITECTURE.md#upnpssdp-network-diagnostics) for the full troubleshooting guide.

---

**Last Updated**: 2026-07-07
**For AI assistant versions**
