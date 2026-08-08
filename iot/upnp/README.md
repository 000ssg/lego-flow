
# Lego Flow UPnP/DLNA Module

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](../LICENSE)
[![Tests](https://img.shields.io/badge/Tests-412-brightgreen.svg)]()
[![Version](https://img.shields.io/badge/Version-0.1.0-blue.svg)]()

UPnP/DLNA module for the Lego Flow framework, providing device discovery, media sharing, and playback control on local networks.

## Overview

This module implements the UPnP Device Architecture (UDA) and DLNA guidelines, enabling Java applications to discover, control, and expose media devices on a LAN. The architecture flows through layered protocols:

```
SSDP (UDP multicast discovery)
  → Device Description (HTTP GET XML)
    → SOAP (action invocation) / GENA (event subscription)
      → Media Services (ContentDirectory, AVTransport, RenderingControl)
        → Control Point (orchestrates all of the above)
```

## Features

- **SSDP multicast discovery** — M-SEARCH requests and NOTIFY advertisements over UDP 239.255.255.250:1900; multi-interface support for Wi-Fi + Ethernet simultaneously with VPN-tolerant error handling
- **SOAP action invocation** — XML envelope construction, action dispatch, fault handling; remote device control via SoapClient with automatic control URL resolution from device description XML
- **GENA event subscription** — SUBSCRIBE/UNSUBSCRIBE/NOTIFY for state variable change events
- **ContentDirectory** — Browse and Search actions with DIDL-Lite XML responses, container/item hierarchy
- **AVTransport** — Play, Pause, Stop, Seek, SetAVTransportURI; full state machine (STOPPED, PLAYING, PAUSED_PLAYBACK, TRANSITIONING)
- **RenderingControl** — GetVolume, SetVolume, GetMute, SetMute per channel (Master, LF, RF)
- **DIDL-Lite XML** — parsing and generation of media metadata (title, artist, album, genre, resolution, protocolInfo)
- **DLNA profiles** — media format profiles (DLNA.ORG_PN) and protocol info negotiation; 30+ profiles including MP3, FLAC, WAV, WMA, OGG, AAC, AVC HD, MPEG-TS, AVI, WMV
- **Complex devices** — smart TV support with both MediaServer and MediaRenderer services in a single device; embedded device registration
- **Streaming media proxy** — HTTP reverse proxy for DLNA content to browser; uses InputStream piping (no full-file buffering) with Range request forwarding (206 Partial Content) and DLNA transfer mode headers
- **Browser playback** — HTML5 audio/video/img elements with expand/collapse overlay, volume control slider, and media support info in footer; Renderer/Browser mode toggle
- **Audio codecs** — MP3 (mp3spi) and FLAC (jflac-codec) decoding via javax.sound.sampled SPI providers; explicit SPI registration for JDK 25 module system compatibility
- **Media support info** — status line displaying which library/framework provides playback support for current media type (shown in Swing StatusBar and web UI footer)
- **XML sanitizer** — character-level scanner for HTML void elements in device/DIDL-Lite XML (handles quoted attributes, case-insensitive tags)
- **UPnP message logging** — centralized protocol message capture (SSDP, HTTP, SOAP) with enable/disable toggle, live UI in both Swing and React apps
- **Unrecognized devices** — devices that fail discovery are tracked and displayed with error details and raw response text
- **Version-agnostic device matching** — recognizes MediaServer/MediaRenderer at any UPnP version (:1, :2, :3) via prefix matching
- **Atomic device deduplication** — prevents duplicate discovery of the same device across concurrent virtual threads using atomic pending-fetch guards
- **Dual API** — sync + async (CompletableFuture), procedural + functional styles

## Quick Start

### Discover all devices on the network (including routers, printers, etc.)

```java
// Single interface
var controlPoint = new ControlPoint(NetworkInterface.getByName("en0"));

// Or: multi-interface (tries all physical interfaces, skips failures)
var interfaces = List.of(
    NetworkInterface.getByName("en0"),  // Wi-Fi
    NetworkInterface.getByName("en1")); // Ethernet
var controlPoint = new ControlPoint(interfaces);

controlPoint.start(); // starts SSDP multicast listener + initial M-SEARCH

// All discovered devices (media servers, renderers, routers, etc.)
controlPoint.getDevices().forEach(device ->
    System.out.println(device.getFriendlyName() + " [" + device.getDeviceType() + "]"));

// Media-specific queries
controlPoint.discoverMediaServers();
controlPoint.discoverMediaRenderers();

// Refresh (triggers new M-SEARCH ssdp:all)
controlPoint.refresh();
```

### Browse a media server library

```java
var contentDirectory = controlPoint.service(device, ContentDirectory.class);
var result = contentDirectory.browse("0", BrowseFlag.DIRECT_CHILDREN, "*", 0, 50);
result.items().forEach(item ->
    System.out.println(item.title() + " — " + item.protocolInfo()));
```

### Play content on a renderer

```java
var avTransport = controlPoint.service(renderer, AVTransport.class);
avTransport.setAVTransportURI(0, contentUri, metadata);
avTransport.play(0, "1");
```

### Control playback volume

```java
var renderingControl = controlPoint.service(renderer, RenderingControl.class);
renderingControl.setVolume(0, "Master", 50);
renderingControl.setMute(0, "Master", false);
```

## Package Structure

```
ssg.legoflow.upnp/
├── ssdp/              — SSDP discovery (M-SEARCH, NOTIFY, device advertisements)
├── device/            — Device/service description model, UDN, device types
├── soap/              — SOAP envelope construction, action invocation, fault handling
├── gena/              — GENA event subscription and notification
├── dlna/              — DLNA profiles, protocolInfo, media format negotiation
├── mediaserver/       — Media server (ContentDirectory, ConnectionManager)
├── mediarenderer/     — Media renderer (AVTransport, RenderingControl)
├── controlpoint/      — Control point orchestration (discover, describe, invoke, subscribe)
└── demo/              — Demo applications and examples
```

## Demo Applications

1. **DeviceDiscoveryDemo** — Discovers all UPnP devices on the local network and prints their details
2. **MediaBrowserDemo** — Connects to a media server and browses its content library hierarchy
3. **MediaPlaybackDemo** — Discovers a renderer, selects content from a server, and plays it
4. **EventSubscriptionDemo** — Subscribes to AVTransport and RenderingControl events, logs state changes
5. **MediaServerDemo** — Exposes a local directory as a UPnP/DLNA media server on the network
6. **Media Control Center (Swing)** — Full desktop GUI with dark theme matching the web variant (slate color palette, flat three-column layout, 1px separators). Device list with Servers/Renderers/All tabs (accent-underlined active tab, emoji device icons, green selection border). Content browser (tree+table) with dark styling and drag-and-drop from both tree and table. Circular playback control buttons (⏮⏪▶⏸⏹⏩⏭) with hover effects. Local player with real image display (ImageIO, aspect-ratio scaling), WAV/AIFF/MP3/FLAC audio playback (via SPI), and metadata panels for unsupported formats. Gradient album art in Now Playing panel with seek-on-click progress bar. Scans real network via SSDP with Refresh button. All device types shown including routers. Drag-and-drop from content browser (tree or table) to renderer or local player; cross-sync between renderer and local player. Per-type device details: server protocol info, renderer transport state, generic device services. Remote media server browsing via SOAP. Diagnostic error messages on browse/search failures. Proper JVM exit on window close.
7. **Media Control Center (Web)** — React SPA served by TCP HTTP server with virtual threads: same features as Swing variant with REST API. Three-tab device panel (Servers/Renderers/All), content browser, transport controls, volume. Real network scanning with refresh. HTML5 drag-and-drop for playing content on renderer or local player. Per-type device details panel with manufacturer/model/services info. **Browser playback mode**: Renderer/Browser toggle switches between DLNA renderer control and in-browser HTML5 playback via media proxy. Independent column scrolling for devices, content, and player areas. Accessible at `http://host:port/` (URL printed on startup).

## Troubleshooting

### No devices discovered on the network

SSDP multicast discovery can be blocked by VPN software or network security extensions. Diagnostic steps:

```bash
# Check multicast routing (most common issue)
netstat -rn -f inet | grep 224
# Should show your physical interface (en0) without ! flag

# If VPN captured the route or it shows ! (rejected):
sudo route delete -net 224.0.0.0/4
sudo route add -net 224.0.0.0/4 -interface en0
```

The module handles this gracefully: `NoRouteToHostException` is caught with a diagnostic log message, and multi-interface mode skips failed interfaces automatically. See [Architecture — Network Diagnostics](doc/ARCHITECTURE.md#upnpssdp-network-diagnostics) for the full diagnostic guide.

### Built-in diagnostics

- Enable UPnP message logging via "Diagnostics" menu (Swing) or toggle button (Web) to capture all SSDP/SOAP/HTTP protocol messages
- Check the "Unrecognized" tab for devices that failed during discovery (shows error details and raw XML)

## Dependencies

This module depends on:
- `lego-flow-service` — UDP transport (UdpDataChannel, MulticastDataChannel) for SSDP
- `lego-flow-http` — HTTP server for device descriptions, SOAP endpoints, GENA callbacks
- `lego-flow-web-services` — XML content negotiation and endpoint routing

---

**Part of the [Lego Flow](../README.md) framework.**

## Documentation

- [Architecture](doc/ARCHITECTURE.md) | [Requirements](doc/REQUIREMENTS.md) | [Compliance](doc/COMPLIANCE.md)
- [Root README](../README.md) | [Root Architecture](../doc/ARCHITECTURE.md)
