package ssg.legoflow.upnp.demo.mccweb;

import ssg.legoflow.http.core.HttpContext;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;

/**
 * Serves the React single-page application for the Media Control Center.
 *
 * <p>Contains the complete React application as embedded Java String
 * constants. The SPA is served as three resources: an HTML page with
 * CDN-loaded React and Babel, a JSX application script, and a CSS
 * stylesheet. No build tooling is required.
 *
 * @since 1.0.0
 */
public class MccReactApp {

    /**
     * Creates a new React app server.
     *
     * @since 1.0.0
     */
    public MccReactApp() {
    }

    /**
     * Handles GET / - serves the main HTML page.
     *
     * @param ctx     the HTTP context
     * @param request the HTTP request
     * @return the HTTP response with HTML content
     * @since 1.0.0
     */
    public HttpResponse serveIndex(HttpContext ctx, HttpRequest request) {
        String html = INDEX_HTML_TEMPLATE.formatted(BUILD_TS, BUILD_TS);
        HttpResponse response = HttpResponse.of(HttpStatus.OK, html);
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "text/html; charset=utf-8");
        addNoCacheHeaders(response);
        return response;
    }

    /**
     * Handles GET /app.js - serves the React application JavaScript.
     *
     * @param ctx     the HTTP context
     * @param request the HTTP request
     * @return the HTTP response with JavaScript content
     * @since 1.0.0
     */
    public HttpResponse serveAppJs(HttpContext ctx, HttpRequest request) {
        HttpResponse response = HttpResponse.of(HttpStatus.OK, APP_JS);
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "text/babel; charset=utf-8");
        addNoCacheHeaders(response);
        return response;
    }

    /**
     * Handles GET /app.css - serves the application CSS stylesheet.
     *
     * @param ctx     the HTTP context
     * @param request the HTTP request
     * @return the HTTP response with CSS content
     * @since 1.0.0
     */
    public HttpResponse serveAppCss(HttpContext ctx, HttpRequest request) {
        HttpResponse response = HttpResponse.of(HttpStatus.OK, APP_CSS);
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "text/css; charset=utf-8");
        addNoCacheHeaders(response);
        return response;
    }

    /**
     * Adds cache-prevention headers so the browser always fetches the latest SPA code.
     *
     * @param response the HTTP response
     */
    private static void addNoCacheHeaders(HttpResponse response) {
        response.getHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
        response.getHeaders().set("Pragma", "no-cache");
        response.getHeaders().set("Expires", "0");
    }

    // --- Embedded HTML ---

    /** Build-time timestamp for cache-busting static resources. */
    private static final long BUILD_TS = System.currentTimeMillis();

    private static final String INDEX_HTML_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Media Control Center - Lego Flow</title>
                <link rel="stylesheet" href="/app.css?v=%d">
                <script crossorigin src="https://unpkg.com/react@18/umd/react.production.min.js"></script>
                <script crossorigin src="https://unpkg.com/react-dom@18/umd/react-dom.production.min.js"></script>
                <script crossorigin src="https://unpkg.com/@babel/standalone/babel.min.js"></script>
            </head>
            <body>
                <div id="root"></div>
                <script type="text/babel" src="/app.js?v=%d"></script>
            </body>
            </html>
            """;

    // --- Embedded JSX ---

    private static final String APP_JS = """
            const { useState, useEffect, useRef, useCallback } = React;

            // --- API helpers ---
            const api = {
                get: (url) => fetch(url).then(r => r.json()),
                post: (url, body) => fetch(url, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: body ? JSON.stringify(body) : undefined
                }).then(r => r.json()),
                put: (url, body) => fetch(url, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(body)
                }).then(r => r.json())
            };

            // --- DevicePanel ---
            function DevicePanel({ servers, renderers, allDevices, failedDevices, selectedServer, selectedRenderer,
                                   selectedDevice, selectedFailedDevice, onSelectServer, onSelectRenderer,
                                   onSelectDevice, onSelectFailedDevice, onRefresh, refreshing }) {
                const [tab, setTab] = useState('servers');
                const deviceIcon = (d) => {
                    if (d.isServer === 'true') return '\\uD83D\\uDCBE';
                    if (d.isRenderer === 'true') return '\\uD83D\\uDD0A';
                    const dt = (d.deviceType || '').toLowerCase();
                    if (dt.includes('internetgateway') || dt.includes('wandevice')) return '\\uD83C\\uDF10';
                    if (dt.includes('printer')) return '\\uD83D\\uDDA8';
                    if (dt.includes('light') || dt.includes('switch')) return '\\uD83D\\uDCA1';
                    return '\\uD83D\\uDCE1';
                };
                return (
                    <div className="panel device-panel">
                        <div className="panel-header">
                            <h2>Devices</h2>
                            <button className="btn btn-sm" onClick={onRefresh} disabled={refreshing}>
                                {refreshing ? 'Scanning...' : 'Refresh'}
                            </button>
                        </div>
                        <div className="tabs">
                            <button className={`tab ${tab === 'servers' ? 'active' : ''}`}
                                    onClick={() => setTab('servers')}>
                                Servers ({servers.length})
                            </button>
                            <button className={`tab ${tab === 'renderers' ? 'active' : ''}`}
                                    onClick={() => setTab('renderers')}>
                                Renderers ({renderers.length})
                            </button>
                            <button className={`tab ${tab === 'all' ? 'active' : ''}`}
                                    onClick={() => setTab('all')}>
                                All ({allDevices.length})
                            </button>
                            <button className={`tab ${tab === 'unrecognized' ? 'active' : ''} ${failedDevices.length > 0 ? 'tab-warn' : ''}`}
                                    onClick={() => setTab('unrecognized')}>
                                Unrecognized ({failedDevices.length})
                            </button>
                        </div>
                        <div className="device-list">
                            {tab === 'servers' && servers.map(d => (
                                <div key={d.udn}
                                     className={`device-item ${selectedServer?.udn === d.udn ? 'selected' : ''}`}
                                     onClick={() => onSelectServer(d)}>
                                    <span className="device-icon">&#128190;</span>
                                    <span className="device-name">{d.friendlyName}</span>
                                </div>
                            ))}
                            {tab === 'renderers' && renderers.map(d => (
                                <div key={d.udn}
                                     className={`device-item ${selectedRenderer?.udn === d.udn ? 'selected' : ''}`}
                                     onClick={() => onSelectRenderer(d)}>
                                    <span className="device-icon">&#128266;</span>
                                    <span className="device-name">{d.friendlyName}</span>
                                </div>
                            ))}
                            {tab === 'all' && allDevices.map(d => (
                                <div key={d.udn}
                                     className={`device-item ${selectedDevice?.udn === d.udn ? 'selected' : ''}`}
                                     onClick={() => {
                                         if (d.isServer === 'true') onSelectServer(d);
                                         else if (d.isRenderer === 'true') onSelectRenderer(d);
                                         onSelectDevice(d);
                                     }}>
                                    <span className="device-icon">{deviceIcon(d)}</span>
                                    <div className="device-info">
                                        <span className="device-name">{d.friendlyName}</span>
                                        <span className="device-type-label">{d.deviceType}</span>
                                    </div>
                                </div>
                            ))}
                            {tab === 'unrecognized' && failedDevices.map(d => (
                                <div key={d.udn}
                                     className={`device-item failed-device ${selectedFailedDevice?.udn === d.udn ? 'selected' : ''}`}
                                     onClick={() => onSelectFailedDevice(d)}>
                                    <span className="device-icon">&#9888;</span>
                                    <div className="device-info">
                                        <span className="device-name">{d.udn}</span>
                                        <span className="device-type-label device-error-hint">{d.errorMessage}</span>
                                    </div>
                                </div>
                            ))}
                            {tab === 'servers' && servers.length === 0 && (
                                <div className="empty-state">No servers discovered</div>
                            )}
                            {tab === 'renderers' && renderers.length === 0 && (
                                <div className="empty-state">No renderers discovered</div>
                            )}
                            {tab === 'all' && allDevices.length === 0 && (
                                <div className="empty-state">No devices discovered</div>
                            )}
                            {tab === 'unrecognized' && failedDevices.length === 0 && (
                                <div className="empty-state">No unrecognized devices</div>
                            )}
                        </div>
                    </div>
                );
            }

            // --- BrowserPlayerPanel ---
            function BrowserPlayerPanel({ currentItem, playbackMode, selectedServer, expanded, onToggleExpand, onMediaSupport }) {
                const mediaRef = useRef(null);
                const [volume, setVolume] = useState(1.0);
                const [muted, setMuted] = useState(false);

                // Derive computed values (no hooks below depend on early returns)
                const isActive = playbackMode === 'browser' && currentItem && currentItem.type !== 'CONTAINER';
                const isVideo = isActive && currentItem.type === 'VIDEO_ITEM';
                const isImage = isActive && currentItem.type === 'IMAGE_ITEM';
                const isAudio = isActive && !isVideo && !isImage;

                // Build proxy URL (safe to compute even when not active — just null)
                const proxyUrl = (() => {
                    if (!isActive) return null;
                    if (currentItem.resourceUrl) {
                        return `/api/media/stream?url=${encodeURIComponent(currentItem.resourceUrl)}&mime=${encodeURIComponent(currentItem.mimeType || '')}`;
                    } else if (selectedServer) {
                        return `/api/servers/${selectedServer.udn}/stream/${currentItem.id}`;
                    }
                    return null;
                })();

                // Determine media support description for footer
                const mediaSupportText = (() => {
                    if (!isActive) return '';
                    if (isVideo) return 'Video via HTML5 <video> — sound depends on audio codec (AAC/MP3=OK, AC3/DTS=no sound)';
                    if (isImage) return 'Image via HTML5 <img> (browser built-in)';
                    const mime = (currentItem.mimeType || '').toLowerCase();
                    if (mime.includes('mp3') || mime.includes('mpeg')) return 'Audio via HTML5 <audio> (browser built-in, MP3)';
                    if (mime.includes('ogg')) return 'Audio via HTML5 <audio> (browser built-in, OGG)';
                    if (mime.includes('wav')) return 'Audio via HTML5 <audio> (browser built-in, WAV)';
                    if (mime.includes('flac')) return 'Audio via HTML5 <audio> (browser built-in, FLAC)';
                    if (mime.includes('aac') || mime.includes('mp4')) return 'Audio via HTML5 <audio> (browser built-in, AAC)';
                    return 'Media via HTML5 (browser built-in)';
                })();

                // ALL hooks MUST be above any early returns (React Rules of Hooks)
                useEffect(() => {
                    if (mediaRef.current) {
                        mediaRef.current.volume = volume;
                        mediaRef.current.muted = muted;
                    }
                }, [volume, muted, currentItem]);

                useEffect(() => {
                    if (onMediaSupport) onMediaSupport(mediaSupportText);
                }, [mediaSupportText]);

                // Event handlers (safe before early returns since they're just function definitions)
                const handleVolumeChange = (e) => {
                    const v = parseFloat(e.target.value);
                    setVolume(v);
                    if (mediaRef.current) mediaRef.current.volume = v;
                };
                const toggleMute = () => {
                    const newMuted = !muted;
                    setMuted(newMuted);
                    if (mediaRef.current) mediaRef.current.muted = newMuted;
                };

                // --- Early returns (after all hooks) ---
                if (!isActive) {
                    return (
                        <div className={`panel browser-player-panel ${expanded ? 'browser-player-expanded' : ''}`}>
                            <div className="panel-header"><h2>Browser Player</h2></div>
                            <div className="browser-player empty">
                                <div className="browser-player-placeholder">
                                    {playbackMode !== 'browser' ? '\\uD83D\\uDD0A Renderer Mode' : '\\uD83C\\uDFB5 No Media Selected'}
                                </div>
                            </div>
                        </div>
                    );
                }

                if (!proxyUrl) {
                    return (
                        <div className={`panel browser-player-panel ${expanded ? 'browser-player-expanded' : ''}`}>
                            <div className="panel-header"><h2>Browser Player</h2></div>
                            <div className="browser-player empty">
                                <div className="browser-player-placeholder">\\u26A0 No stream URL available</div>
                            </div>
                        </div>
                    );
                }

                return (
                    <div className={`panel browser-player-panel ${expanded ? 'browser-player-expanded' : ''}`}>
                        <div className="panel-header">
                            <h2>Browser Player</h2>
                            {(isVideo || isImage) && (
                                <button className="btn btn-sm" onClick={onToggleExpand}
                                        title={expanded ? 'Collapse' : 'Expand to fill'}>
                                    {expanded ? '\\u2716' : '\\u26F6'}
                                </button>
                            )}
                        </div>
                        <div className={`browser-player ${expanded ? 'browser-player-fill' : ''}`}>
                            {isImage ? (
                                <img src={proxyUrl} alt={currentItem.title}
                                         className={`browser-player-image ${expanded ? 'browser-player-image-expanded' : ''}`} />
                            ) : isVideo ? (
                                <video ref={mediaRef} src={proxyUrl} controls preload="auto"
                                       onError={(e) => console.error('Video error:', e.target.error, 'src:', proxyUrl)}
                                       className={`browser-player-media ${expanded ? 'browser-player-media-expanded' : ''}`} />
                            ) : (
                                <audio ref={mediaRef} src={proxyUrl} controls autoPlay className="browser-player-media"
                                       onError={(e) => console.error('Audio error:', e.target.error, 'src:', proxyUrl)} />
                            )}
                            <div className="browser-player-controls">
                                <div className="browser-player-info">
                                    <span className="browser-player-title">{currentItem.title}</span>
                                    {currentItem.creator && (
                                        <span className="browser-player-artist">{currentItem.creator}</span>
                                    )}
                                    {currentItem.duration && (
                                        <span className="browser-player-duration">{currentItem.duration}</span>
                                    )}
                                </div>
                                <div className="browser-player-volume">
                                    <button className="ctrl-btn mute-btn" onClick={toggleMute} title={muted ? 'Unmute' : 'Mute'}>
                                        {muted ? '\\uD83D\\uDD07' : (volume > 0.5 ? '\\uD83D\\uDD0A' : volume > 0 ? '\\uD83D\\uDD09' : '\\uD83D\\uDD07')}
                                    </button>
                                    <input type="range" min="0" max="1" step="0.05"
                                           value={volume}
                                           onChange={handleVolumeChange}
                                           className="volume-slider" />
                                    <span className="volume-value">{Math.round(volume * 100)}</span>
                                </div>
                            </div>
                        </div>
                    </div>
                );
            }

            // --- BrowserPanel ---
            function BrowserPanel({ server, selectedRenderer, playbackMode, onPlayItem }) {
                const [items, setItems] = useState([]);
                const [breadcrumb, setBreadcrumb] = useState([{ id: '0', title: 'Root' }]);
                const [loading, setLoading] = useState(false);
                const [searchQuery, setSearchQuery] = useState('');

                const currentId = breadcrumb[breadcrumb.length - 1].id;

                const browse = useCallback((objectId) => {
                    if (!server) return;
                    setLoading(true);
                    api.get(`/api/servers/${server.udn}/browse?id=${objectId}&start=0&count=100`)
                        .then(data => { setItems(data.items || []); setLoading(false); })
                        .catch(() => setLoading(false));
                }, [server]);

                useEffect(() => {
                    if (server) {
                        setBreadcrumb([{ id: '0', title: 'Root' }]);
                        browse('0');
                    } else {
                        setItems([]);
                    }
                }, [server, browse]);

                const navigateTo = (item) => {
                    if (item.type === 'CONTAINER') {
                        setBreadcrumb(prev => [...prev, { id: item.id, title: item.title }]);
                        browse(item.id);
                    } else if (playbackMode === 'browser') {
                        if (onPlayItem) onPlayItem(item);
                    } else if (selectedRenderer && item.resourceUrl) {
                        api.post(`/api/renderers/${selectedRenderer.udn}/play`, {
                            itemUri: item.resourceUrl,
                            itemMetadata: ''
                        });
                        if (onPlayItem) onPlayItem(item);
                    }
                };

                const navigateBreadcrumb = (index) => {
                    const newCrumbs = breadcrumb.slice(0, index + 1);
                    setBreadcrumb(newCrumbs);
                    browse(newCrumbs[newCrumbs.length - 1].id);
                };

                const doSearch = () => {
                    if (!server || !searchQuery.trim()) return;
                    setLoading(true);
                    api.get(`/api/servers/${server.udn}/search?query=${encodeURIComponent(searchQuery)}`)
                        .then(data => { setItems(data || []); setLoading(false); })
                        .catch(() => setLoading(false));
                };

                const formatDuration = (dur) => dur || '--:--';
                const formatSize = (bytes) => {
                    if (!bytes || bytes === 0) return '';
                    if (bytes > 1e9) return (bytes / 1e9).toFixed(1) + ' GB';
                    if (bytes > 1e6) return (bytes / 1e6).toFixed(1) + ' MB';
                    if (bytes > 1e3) return (bytes / 1e3).toFixed(0) + ' KB';
                    return bytes + ' B';
                };
                const typeIcon = (type) => {
                    switch (type) {
                        case 'CONTAINER': return '\\uD83D\\uDCC1';
                        case 'AUDIO_ITEM': return '\\uD83C\\uDFB5';
                        case 'VIDEO_ITEM': return '\\uD83C\\uDFAC';
                        case 'IMAGE_ITEM': return '\\uD83D\\uDDBC';
                        default: return '\\uD83D\\uDCC4';
                    }
                };

                if (!server) {
                    return (
                        <div className="panel browser-panel">
                            <div className="panel-header"><h2>Content Browser</h2></div>
                            <div className="empty-state">Select a media server to browse</div>
                        </div>
                    );
                }

                return (
                    <div className="panel browser-panel">
                        <div className="panel-header">
                            <h2>Content Browser</h2>
                        </div>
                        <div className="search-bar">
                            <input type="text" placeholder="Search content..."
                                   value={searchQuery}
                                   onChange={e => setSearchQuery(e.target.value)}
                                   onKeyDown={e => e.key === 'Enter' && doSearch()} />
                            <button className="btn btn-sm" onClick={doSearch}>Search</button>
                        </div>
                        <div className="breadcrumb">
                            {breadcrumb.map((crumb, i) => (
                                <span key={crumb.id}>
                                    {i > 0 && <span className="breadcrumb-sep"> / </span>}
                                    <span className="breadcrumb-item" onClick={() => navigateBreadcrumb(i)}>
                                        {crumb.title}
                                    </span>
                                </span>
                            ))}
                        </div>
                        {loading ? (
                            <div className="loading-spinner">Loading...</div>
                        ) : (
                            <div className="content-table">
                                <div className="content-header">
                                    <span className="col-icon"></span>
                                    <span className="col-title">Title</span>
                                    <span className="col-artist">Artist</span>
                                    <span className="col-duration">Duration</span>
                                    <span className="col-size">Size</span>
                                </div>
                                {items.map(item => (
                                    <div key={item.id}
                                         className="content-row"
                                         draggable={item.type !== 'CONTAINER'}
                                         onClick={() => navigateTo(item)}
                                         onDragStart={e => {
                                             if (item.type === 'CONTAINER') { e.preventDefault(); return; }
                                             e.dataTransfer.setData('application/json', JSON.stringify(item));
                                             e.dataTransfer.effectAllowed = 'copy';
                                             e.currentTarget.classList.add('dragging');
                                         }}
                                         onDragEnd={e => e.currentTarget.classList.remove('dragging')}>
                                        <span className="col-icon">{typeIcon(item.type)}</span>
                                        <span className="col-title">{item.title}</span>
                                        <span className="col-artist">{item.creator}</span>
                                        <span className="col-duration">{formatDuration(item.duration)}</span>
                                        <span className="col-size">{formatSize(item.size)}</span>
                                    </div>
                                ))}
                                {items.length === 0 && (
                                    <div className="empty-state">No items found</div>
                                )}
                            </div>
                        )}
                    </div>
                );
            }

            // --- LocalPlayerPanel ---
            function LocalPlayerPanel({ currentItem, onItemChange }) {
                const [dragOver, setDragOver] = useState(false);

                const handleDragOver = (e) => { e.preventDefault(); e.dataTransfer.dropEffect = 'copy'; setDragOver(true); };
                const handleDragLeave = () => setDragOver(false);
                const handleDrop = (e) => {
                    e.preventDefault();
                    setDragOver(false);
                    try {
                        const item = JSON.parse(e.dataTransfer.getData('application/json'));
                        if (item) onItemChange(item);
                    } catch (err) {}
                };

                const typeIcon = (type) => {
                    switch (type) {
                        case 'AUDIO_ITEM': return '\\uD83C\\uDFB5';
                        case 'VIDEO_ITEM': return '\\uD83C\\uDFAC';
                        case 'IMAGE_ITEM': return '\\uD83D\\uDDBC';
                        default: return '\\uD83D\\uDCC4';
                    }
                };

                return (
                    <div className={`panel local-player-panel drop-zone ${dragOver ? 'drag-over' : ''}`}
                         onDragOver={handleDragOver}
                         onDragLeave={handleDragLeave}
                         onDrop={handleDrop}>
                        <div className="panel-header"><h2>Local Player</h2></div>
                        {currentItem ? (
                            <div className="local-player-content"
                                 draggable="true"
                                 onDragStart={e => {
                                     e.dataTransfer.setData('application/json', JSON.stringify(currentItem));
                                     e.dataTransfer.effectAllowed = 'copy';
                                 }}>
                                <div className="local-player-icon">{typeIcon(currentItem.type)}</div>
                                <div className="local-player-info">
                                    <div className="local-player-title">{currentItem.title}</div>
                                    <div className="local-player-artist">{currentItem.creator || 'Unknown Artist'}</div>
                                </div>
                            </div>
                        ) : (
                            <div className="local-player-empty">
                                <div className="drop-hint">\\uD83C\\uDFB5</div>
                                <div>Drop a track here</div>
                            </div>
                        )}
                    </div>
                );
            }

            // --- NowPlayingPanel ---
            function NowPlayingPanel({ renderer, renderers, onSelectRenderer, onDropItem }) {
                const [transport, setTransport] = useState(null);
                const [volume, setVolume] = useState({ volume: 50, muted: false });
                const intervalRef = useRef(null);
                const [dragOver, setDragOver] = useState(false);

                const pollState = useCallback(() => {
                    if (!renderer) return;
                    api.get(`/api/renderers/${renderer.udn}/transport`)
                        .then(data => setTransport(data))
                        .catch(() => {});
                    api.get(`/api/renderers/${renderer.udn}/volume`)
                        .then(data => setVolume(data))
                        .catch(() => {});
                }, [renderer]);

                useEffect(() => {
                    if (renderer) {
                        pollState();
                        intervalRef.current = setInterval(pollState, 1000);
                    }
                    return () => { if (intervalRef.current) clearInterval(intervalRef.current); };
                }, [renderer, pollState]);

                const doPlay = () => renderer && api.post(`/api/renderers/${renderer.udn}/play`);
                const doPause = () => renderer && api.post(`/api/renderers/${renderer.udn}/pause`);
                const doStop = () => renderer && api.post(`/api/renderers/${renderer.udn}/stop`);
                const doPrev = () => renderer && api.post(`/api/renderers/${renderer.udn}/previous`);
                const doNext = () => renderer && api.post(`/api/renderers/${renderer.udn}/next`);
                const doSeek = (e) => {
                    if (!renderer || !transport) return;
                    const rect = e.currentTarget.getBoundingClientRect();
                    const pct = (e.clientX - rect.left) / rect.width;
                    const parts = (transport.trackDuration || '0:00:00').split(':');
                    const totalSec = parseInt(parts[0]) * 3600 + parseInt(parts[1]) * 60 + parseInt(parts[2]);
                    const seekSec = Math.floor(pct * totalSec);
                    const h = Math.floor(seekSec / 3600);
                    const m = Math.floor((seekSec % 3600) / 60);
                    const s = seekSec % 60;
                    const pos = `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
                    api.post(`/api/renderers/${renderer.udn}/seek`, { position: pos });
                };
                const doVolume = (e) => {
                    if (!renderer) return;
                    api.put(`/api/renderers/${renderer.udn}/volume`, { volume: parseInt(e.target.value) });
                };
                const doMute = () => {
                    if (!renderer) return;
                    api.put(`/api/renderers/${renderer.udn}/mute`, { muted: !volume.muted });
                };

                const stateColor = (st) => {
                    if (st === 'PLAYING') return '#4ade80';
                    if (st === 'PAUSED_PLAYBACK') return '#fbbf24';
                    return '#6b7280';
                };

                const progressPct = () => {
                    if (!transport || !transport.trackDuration || !transport.relTime) return 0;
                    const parse = (t) => {
                        const p = (t || '0:00:00').split(':');
                        return parseInt(p[0]) * 3600 + parseInt(p[1]) * 60 + parseFloat(p[2]);
                    };
                    const total = parse(transport.trackDuration);
                    return total > 0 ? (parse(transport.relTime) / total) * 100 : 0;
                };

                const handleDragOver = (e) => { e.preventDefault(); e.dataTransfer.dropEffect = 'copy'; setDragOver(true); };
                const handleDragLeave = () => setDragOver(false);
                const handleDrop = (e) => {
                    e.preventDefault();
                    setDragOver(false);
                    try {
                        const item = JSON.parse(e.dataTransfer.getData('application/json'));
                        if (item && renderer && item.resourceUrl) {
                            api.post(`/api/renderers/${renderer.udn}/play`, {
                                itemUri: item.resourceUrl,
                                itemMetadata: ''
                            });
                        }
                        if (item && onDropItem) onDropItem(item);
                    } catch (err) {}
                };

                return (
                    <div className={`panel now-playing-panel drop-zone ${dragOver ? 'drag-over' : ''}`}
                         onDragOver={handleDragOver}
                         onDragLeave={handleDragLeave}
                         onDrop={handleDrop}>
                        <div className="panel-header"><h2>Now Playing</h2></div>
                        <div className="renderer-selector">
                            <select value={renderer?.udn || ''}
                                    onChange={e => {
                                        const r = renderers.find(d => d.udn === e.target.value);
                                        if (r) onSelectRenderer(r);
                                    }}>
                                <option value="">Select Renderer</option>
                                {renderers.map(r => (
                                    <option key={r.udn} value={r.udn}>{r.friendlyName}</option>
                                ))}
                            </select>
                        </div>
                        <div className="album-art">
                            <div className="album-art-placeholder" />
                        </div>
                        <div className="track-info"
                             draggable={!!transport?.trackUri}
                             onDragStart={e => {
                                 if (!transport?.trackUri) { e.preventDefault(); return; }
                                 const item = { title: transport.trackTitle || transport.trackUri.split('/').pop(), resourceUrl: transport.trackUri, type: 'AUDIO_ITEM', creator: '' };
                                 e.dataTransfer.setData('application/json', JSON.stringify(item));
                                 e.dataTransfer.effectAllowed = 'copy';
                             }}>
                            <div className="track-title">{transport?.trackTitle || 'No Track'}</div>
                            <div className="track-state">
                                <span className="state-dot" style={{ background: stateColor(transport?.state) }} />
                                {transport?.state || 'STOPPED'}
                            </div>
                        </div>
                        <div className="progress-bar" onClick={doSeek}>
                            <div className="progress-fill" style={{ width: progressPct() + '%' }} />
                        </div>
                        <div className="time-display">
                            <span>{transport?.relTime || '0:00:00'}</span>
                            <span>{transport?.trackDuration || '0:00:00'}</span>
                        </div>
                        <div className="transport-controls">
                            <button className="ctrl-btn" onClick={doPrev} title="Previous">&#9198;</button>
                            <button className="ctrl-btn" onClick={doPlay} title="Play">&#9654;</button>
                            <button className="ctrl-btn" onClick={doPause} title="Pause">&#9208;</button>
                            <button className="ctrl-btn" onClick={doStop} title="Stop">&#9209;</button>
                            <button className="ctrl-btn" onClick={doNext} title="Next">&#9197;</button>
                        </div>
                        <div className="volume-control">
                            <button className="ctrl-btn mute-btn" onClick={doMute}>
                                {volume.muted ? '\\uD83D\\uDD07' : '\\uD83D\\uDD0A'}
                            </button>
                            <input type="range" min="0" max="100"
                                   value={volume.volume}
                                   onChange={doVolume}
                                   className="volume-slider" />
                            <span className="volume-value">{volume.volume}</span>
                        </div>
                    </div>
                );
            }

            // --- DeviceDetailsPanel ---
            function DeviceDetailsPanel({ device }) {
                const [details, setDetails] = useState(null);
                const [loading, setLoading] = useState(false);

                useEffect(() => {
                    if (!device) { setDetails(null); return; }
                    setLoading(true);
                    api.get('/api/devices/' + device.udn)
                        .then(data => { setDetails(data); setLoading(false); })
                        .catch(() => setLoading(false));
                }, [device]);

                if (!device) {
                    return (
                        <div className="panel device-details-panel">
                            <div className="panel-header"><h2>Device Details</h2></div>
                            <div className="empty-state">Select a device to view details</div>
                        </div>
                    );
                }
                if (loading || !details) {
                    return (
                        <div className="panel device-details-panel">
                            <div className="panel-header"><h2>Device Details</h2></div>
                            <div className="loading-spinner">Loading...</div>
                        </div>
                    );
                }

                const isServer = details.isServer === 'true';
                const isRenderer = details.isRenderer === 'true';
                const stateColor = (st) => {
                    if (st === 'PLAYING') return '#4ade80';
                    if (st === 'PAUSED_PLAYBACK') return '#fbbf24';
                    if (st === 'STOPPED') return '#ef4444';
                    return '#6b7280';
                };

                return (
                    <div className="panel device-details-panel">
                        <div className="panel-header">
                            <h2>Device Details</h2>
                            <span className="device-details-badge">
                                {isServer ? 'Media Server' : isRenderer ? 'Media Renderer' : 'Device'}
                            </span>
                        </div>
                        <div className="device-details-content">
                            <div className="details-section">
                                <h3>General</h3>
                                <div className="details-grid">
                                    <span className="details-label">Name</span>
                                    <span className="details-value">{details.friendlyName}</span>
                                    <span className="details-label">UDN</span>
                                    <span className="details-value details-mono">{details.udn}</span>
                                    <span className="details-label">Type</span>
                                    <span className="details-value details-mono">{details.deviceType}</span>
                                    <span className="details-label">Base URL</span>
                                    <span className="details-value details-mono">{details.baseUrl}</span>
                                    {details.manufacturer && (
                                        <React.Fragment>
                                            <span className="details-label">Manufacturer</span>
                                            <span className="details-value">{details.manufacturer}</span>
                                        </React.Fragment>
                                    )}
                                    {details.modelName && (
                                        <React.Fragment>
                                            <span className="details-label">Model</span>
                                            <span className="details-value">
                                                {details.modelName}{details.modelNumber ? ' (' + details.modelNumber + ')' : ''}
                                            </span>
                                        </React.Fragment>
                                    )}
                                    {details.serialNumber && (
                                        <React.Fragment>
                                            <span className="details-label">Serial</span>
                                            <span className="details-value">{details.serialNumber}</span>
                                        </React.Fragment>
                                    )}
                                </div>
                            </div>

                            {isRenderer && details.transportState && (
                                <div className="details-section">
                                    <h3>Transport</h3>
                                    <div className="details-grid">
                                        <span className="details-label">State</span>
                                        <span className="details-value">
                                            <span className="state-dot" style={{ background: stateColor(details.transportState) }} />
                                            {' '}{details.transportState}
                                        </span>
                                        <span className="details-label">Track</span>
                                        <span className="details-value">{details.trackTitle || details.trackUri || 'None'}</span>
                                        <span className="details-label">Position</span>
                                        <span className="details-value">{details.position} / {details.duration}</span>
                                        <span className="details-label">Volume</span>
                                        <span className="details-value">{details.rendererVolume}{details.rendererMuted ? ' (Muted)' : ''}</span>
                                    </div>
                                </div>
                            )}

                            {isServer && details.protocolInfo && details.protocolInfo.length > 0 && (
                                <div className="details-section">
                                    <h3>Protocol Info</h3>
                                    <div className="details-table">
                                        <div className="details-table-header">
                                            <span>Protocol</span>
                                            <span>Format</span>
                                            <span>Additional</span>
                                        </div>
                                        {details.protocolInfo.map((pi, i) => (
                                            <div key={i} className="details-table-row">
                                                <span>{pi.protocol}</span>
                                                <span>{pi.contentFormat}</span>
                                                <span>{pi.additionalInfo}</span>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            )}

                            {details.services && details.services.length > 0 && (
                                <div className="details-section">
                                    <h3>Services</h3>
                                    <div className="details-table">
                                        <div className="details-table-header">
                                            <span>Service Type</span>
                                            <span>Service ID</span>
                                        </div>
                                        {details.services.map((svc, i) => (
                                            <div key={i} className="details-table-row">
                                                <span className="details-mono">{svc.serviceType}</span>
                                                <span className="details-mono">{svc.serviceId}</span>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>
                );
            }

            // --- FailedDeviceDetailsPanel ---
            function FailedDeviceDetailsPanel({ failedDevice }) {
                if (!failedDevice) {
                    return (
                        <div className="panel device-details-panel">
                            <div className="panel-header"><h2>Unrecognized Device</h2></div>
                            <div className="empty-state">Select an unrecognized device to view errors</div>
                        </div>
                    );
                }

                const timestamp = new Date(failedDevice.timestamp).toLocaleString();

                return (
                    <div className="panel device-details-panel">
                        <div className="panel-header">
                            <h2>Unrecognized Device</h2>
                            <span className="device-details-badge failed-badge">Discovery Failed</span>
                        </div>
                        <div className="device-details-content">
                            <div className="details-section">
                                <h3>Device Info</h3>
                                <div className="details-grid">
                                    <span className="details-label">UDN</span>
                                    <span className="details-value details-mono">{failedDevice.udn}</span>
                                    <span className="details-label">Location</span>
                                    <span className="details-value details-mono">{failedDevice.location}</span>
                                    <span className="details-label">Failed At</span>
                                    <span className="details-value">{timestamp}</span>
                                </div>
                            </div>
                            <div className="details-section">
                                <h3>Error</h3>
                                <div className="failed-error-box">{failedDevice.errorMessage}</div>
                            </div>
                            {failedDevice.responseText && (
                                <div className="details-section">
                                    <h3>Response Text</h3>
                                    <pre className="failed-response-text">{failedDevice.responseText}</pre>
                                </div>
                            )}
                        </div>
                    </div>
                );
            }

            // --- App ---
            function App() {
                const [servers, setServers] = useState([]);
                const [renderers, setRenderers] = useState([]);
                const [allDevices, setAllDevices] = useState([]);
                const [failedDevices, setFailedDevices] = useState([]);
                const [selectedServer, setSelectedServer] = useState(null);
                const [selectedRenderer, setSelectedRenderer] = useState(null);
                const [connected, setConnected] = useState(true);
                const [refreshing, setRefreshing] = useState(false);
                const [localPlayerItem, setLocalPlayerItem] = useState(null);
                const [selectedDevice, setSelectedDevice] = useState(null);
                const [selectedFailedDevice, setSelectedFailedDevice] = useState(null);
                const [playbackMode, setPlaybackMode] = useState('renderer');
                const [browserPlayerItem, setBrowserPlayerItem] = useState(null);
                const [logEnabled, setLogEnabled] = useState(false);
                const [logVisible, setLogVisible] = useState(false);
                const [logEntries, setLogEntries] = useState([]);
                const [browserPlayerExpanded, setBrowserPlayerExpanded] = useState(false);
                const [mediaSupportText, setMediaSupportText] = useState('');

                const loadDevices = useCallback(() => {
                    api.get('/api/devices/servers')
                        .then(data => {
                            setServers(data);
                            setConnected(true);
                            // Auto-select first server if none selected
                            setSelectedServer(prev => {
                                if (prev) {
                                    // Restore previous selection if still present
                                    const stillExists = data.find(d => d.udn === prev.udn);
                                    return stillExists || (data.length > 0 ? data[0] : null);
                                }
                                return data.length > 0 ? data[0] : null;
                            });
                        })
                        .catch(() => setConnected(false));
                    api.get('/api/devices/renderers')
                        .then(data => {
                            setRenderers(data);
                            // Auto-select first renderer if none selected
                            setSelectedRenderer(prev => {
                                if (prev) {
                                    const stillExists = data.find(d => d.udn === prev.udn);
                                    return stillExists || (data.length > 0 ? data[0] : null);
                                }
                                return data.length > 0 ? data[0] : null;
                            });
                        })
                        .catch(() => {});
                    api.get('/api/devices')
                        .then(data => setAllDevices(data))
                        .catch(() => {});
                    api.get('/api/devices/unrecognized')
                        .then(data => setFailedDevices(data))
                        .catch(() => {});
                }, []);

                useEffect(() => {
                    loadDevices();
                    const pollId = setInterval(() => {
                        fetch('/api/events').then(r => r.text()).then(text => {
                            const lines = text.split('\\n');
                            let needRefresh = false;
                            for (const line of lines) {
                                if (line.startsWith('data: ')) {
                                    try {
                                        const evt = JSON.parse(line.substring(6));
                                        if (evt.type === 'deviceAdded' || evt.type === 'deviceRemoved') {
                                            needRefresh = true;
                                        }
                                    } catch (e) {}
                                }
                            }
                            if (needRefresh) loadDevices();
                        }).catch(() => {});
                    }, 2000);
                    return () => clearInterval(pollId);
                }, [loadDevices]);

                const refresh = () => {
                    setRefreshing(true);
                    api.post('/api/devices/refresh').then(() => {
                        setTimeout(() => {
                            loadDevices();
                            setRefreshing(false);
                        }, 3000);
                    }).catch(() => setRefreshing(false));
                };

                const toggleLog = () => {
                    const newState = !logEnabled;
                    api.post(newState ? '/api/log/enable' : '/api/log/disable')
                        .then(() => {
                            setLogEnabled(newState);
                            if (newState) setLogVisible(true);
                        }).catch(() => {});
                };

                const refreshLog = () => {
                    api.get('/api/log').then(data => {
                        setLogEntries(data.entries || []);
                        setLogEnabled(data.enabled);
                    }).catch(() => {});
                };

                const clearLog = () => {
                    api.post('/api/log/clear').then(() => setLogEntries([])).catch(() => {});
                };

                useEffect(() => {
                    if (logVisible && logEnabled) {
                        const id = setInterval(refreshLog, 2000);
                        return () => clearInterval(id);
                    }
                }, [logVisible, logEnabled]);

                return (
                    <div className="app">
                        <header className="app-header">
                            <h1>Media Control Center</h1>
                            <div className="header-actions">
                                <button className={`btn btn-sm log-toggle ${logEnabled ? 'log-on' : ''}`}
                                        onClick={toggleLog}
                                        title={logEnabled ? 'Disable UPnP logging' : 'Enable UPnP logging'}>
                                    {logEnabled ? '\\uD83D\\uDFE2 Logging' : '\\u26AA Logging'}
                                </button>
                                {logEnabled && (
                                    <button className="btn btn-sm" onClick={() => setLogVisible(!logVisible)}>
                                        {logVisible ? 'Hide Log' : 'Show Log'}
                                    </button>
                                )}
                                <span className={`connection-status ${connected ? 'connected' : 'disconnected'}`}>
                                    {connected ? 'Connected' : 'Disconnected'}
                                </span>
                            </div>
                        </header>
                        <main className="app-main">
                            <DevicePanel servers={servers} renderers={renderers}
                                         allDevices={allDevices}
                                         failedDevices={failedDevices}
                                         selectedServer={selectedServer}
                                         selectedRenderer={selectedRenderer}
                                         selectedDevice={selectedDevice}
                                         selectedFailedDevice={selectedFailedDevice}
                                         onSelectServer={(d) => { setSelectedServer(d); setSelectedDevice(null); setSelectedFailedDevice(null); }}
                                         onSelectRenderer={(d) => { setSelectedRenderer(d); setSelectedDevice(null); setSelectedFailedDevice(null); }}
                                         onSelectDevice={(d) => { setSelectedDevice(d); setSelectedFailedDevice(null); }}
                                         onSelectFailedDevice={(d) => { setSelectedFailedDevice(d); setSelectedDevice(null); }}
                                         onRefresh={refresh}
                                         refreshing={refreshing} />
                            {selectedFailedDevice ? (
                                <FailedDeviceDetailsPanel failedDevice={selectedFailedDevice} />
                            ) : selectedDevice && selectedDevice.isServer !== 'true' ? (
                                <DeviceDetailsPanel device={selectedDevice} />
                            ) : (
                                <BrowserPanel server={selectedServer}
                                              selectedRenderer={selectedRenderer}
                                              playbackMode={playbackMode}
                                              onPlayItem={(item) => {
                                                  setBrowserPlayerItem(item);
                                                  setLocalPlayerItem(item);
                                              }} />
                            )}
                            <div className="right-column">
                                <div className="playback-mode-toggle">
                                    <button className={`mode-btn ${playbackMode === 'renderer' ? 'active' : ''}`}
                                            onClick={() => setPlaybackMode('renderer')}>
                                        &#128266; Renderer
                                    </button>
                                    <button className={`mode-btn ${playbackMode === 'browser' ? 'active' : ''}`}
                                            onClick={() => setPlaybackMode('browser')}>
                                        &#127760; Browser
                                    </button>
                                </div>
                                {playbackMode === 'renderer' ? (
                                    <NowPlayingPanel renderer={selectedRenderer}
                                                     renderers={renderers}
                                                     onSelectRenderer={setSelectedRenderer}
                                                     onDropItem={(item) => setBrowserPlayerItem(item)} />
                                ) : (
                                    <BrowserPlayerPanel currentItem={browserPlayerItem}
                                                        playbackMode={playbackMode}
                                                        selectedServer={selectedServer}
                                                        expanded={browserPlayerExpanded}
                                                        onToggleExpand={() => setBrowserPlayerExpanded(e => !e)}
                                                        onMediaSupport={setMediaSupportText} />
                                )}
                                <LocalPlayerPanel currentItem={localPlayerItem}
                                                  onItemChange={setLocalPlayerItem} />
                            </div>
                        </main>
                        {logVisible && (
                            <div className="log-panel">
                                <div className="log-panel-header">
                                    <span className="log-panel-title">UPnP Message Log ({logEntries.length} entries)</span>
                                    <div className="log-panel-actions">
                                        <button className="btn btn-sm" onClick={refreshLog}>Refresh</button>
                                        <button className="btn btn-sm" onClick={clearLog}>Clear</button>
                                        <button className="btn btn-sm" onClick={() => setLogVisible(false)}>Close</button>
                                    </div>
                                </div>
                                <div className="log-panel-body">
                                    {logEntries.length === 0 ? (
                                        <div className="log-empty">
                                            {logEnabled ? 'Waiting for UPnP messages...' : 'Logging is disabled. Enable it to capture messages.'}
                                        </div>
                                    ) : (
                                        logEntries.map((entry, i) => (
                                            <div key={i} className={`log-entry ${entry.dir === '>>>' ? 'log-outgoing' : 'log-incoming'}`}>
                                                <span className="log-time">{entry.time}</span>
                                                <span className="log-dir">{entry.dir}</span>
                                                <span className="log-proto">{entry.proto}</span>
                                                <span className="log-summary">{entry.summary}</span>
                                                {entry.body && (
                                                    <pre className="log-body">{entry.body}</pre>
                                                )}
                                            </div>
                                        ))
                                    )}
                                </div>
                            </div>
                        )}
                        <footer className="app-footer">
                            <span>Lego Flow Media Control Center</span>
                            {mediaSupportText && playbackMode === 'browser' && (
                                <span className="footer-media-support">{mediaSupportText}</span>
                            )}
                            <span>{allDevices.length} devices ({servers.length} servers, {renderers.length} renderers{failedDevices.length > 0 ? ', ' + failedDevices.length + ' unrecognized' : ''})</span>
                        </footer>
                    </div>
                );
            }

            const root = ReactDOM.createRoot(document.getElementById('root'));
            root.render(<App />);
            """;

    // --- Embedded CSS ---

    private static final String APP_CSS = """
            *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

            html, body { height: 100%; overflow: hidden; }
            body {
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                background: #0f172a; color: #e2e8f0;
            }

            .app { display: flex; flex-direction: column; height: 100vh; }

            .app-header {
                display: flex; justify-content: space-between; align-items: center;
                padding: 16px 24px; background: #1e293b; border-bottom: 1px solid #334155;
            }
            .app-header h1 { font-size: 20px; font-weight: 600; color: #f1f5f9; }
            .connection-status {
                font-size: 13px; padding: 4px 12px; border-radius: 12px;
            }
            .connection-status.connected { background: #065f46; color: #6ee7b7; }
            .connection-status.disconnected { background: #7f1d1d; color: #fca5a5; }

            .app-main {
                display: flex; flex: 1; gap: 1px; background: #334155;
                overflow: hidden; min-height: 0; position: relative;
            }

            .panel {
                background: #1e293b; display: flex; flex-direction: column;
                overflow: hidden; min-height: 0;
            }
            .device-panel { flex: 0 0 250px; }
            .browser-panel { flex: 1 1 auto; min-height: 0; }
            .right-column {
                display: flex; flex-direction: column; flex: 0 0 300px;
                gap: 1px; background: #334155; overflow: hidden; min-height: 0;
            }
            .now-playing-panel { flex: 1 1 auto; overflow-y: auto; }
            .local-player-panel { flex: 0 0 160px; }

            .panel-header {
                display: flex; justify-content: space-between; align-items: center;
                padding: 12px 16px; border-bottom: 1px solid #334155;
            }
            .panel-header h2 { font-size: 14px; font-weight: 600; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.5px; }

            .btn { background: #334155; color: #e2e8f0; border: 1px solid #475569; border-radius: 6px; padding: 6px 12px; cursor: pointer; font-size: 13px; transition: background 0.15s; }
            .btn:hover { background: #475569; }
            .btn-sm { padding: 4px 8px; font-size: 12px; }

            .tabs { display: flex; border-bottom: 1px solid #334155; }
            .tab {
                flex: 1; padding: 8px; text-align: center; background: none; border: none;
                color: #94a3b8; cursor: pointer; font-size: 13px; border-bottom: 2px solid transparent;
                transition: all 0.15s;
            }
            .tab.active { color: #60a5fa; border-bottom-color: #60a5fa; }
            .tab:hover { color: #e2e8f0; }

            .device-list { overflow-y: auto; flex: 1; }
            .device-item {
                display: flex; align-items: center; gap: 8px; padding: 10px 16px;
                cursor: pointer; transition: background 0.15s; border-left: 3px solid transparent;
            }
            .device-item:hover { background: #334155; }
            .device-item.selected { background: #1e3a5f; border-left-color: #4ade80; }
            .device-icon { font-size: 18px; }
            .device-name { font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
            .device-info { display: flex; flex-direction: column; overflow: hidden; }
            .device-type-label { font-size: 10px; color: #64748b; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

            .empty-state {
                display: flex; align-items: center; justify-content: center;
                padding: 32px; color: #64748b; font-size: 14px; font-style: italic;
            }

            .search-bar {
                display: flex; gap: 8px; padding: 8px 16px; border-bottom: 1px solid #334155;
            }
            .search-bar input {
                flex: 1; background: #0f172a; border: 1px solid #334155; border-radius: 6px;
                padding: 6px 12px; color: #e2e8f0; font-size: 13px; outline: none;
            }
            .search-bar input:focus { border-color: #60a5fa; }

            .breadcrumb { padding: 8px 16px; font-size: 13px; color: #94a3b8; border-bottom: 1px solid #334155; }
            .breadcrumb-item { cursor: pointer; color: #60a5fa; }
            .breadcrumb-item:hover { text-decoration: underline; }
            .breadcrumb-sep { color: #475569; }

            .content-table { overflow-y: auto; flex: 1; min-height: 0; }
            .content-header, .content-row {
                display: flex; align-items: center; padding: 6px 16px; font-size: 13px;
            }
            .content-header { color: #64748b; border-bottom: 1px solid #334155; font-weight: 600; position: sticky; top: 0; background: #1e293b; }
            .content-row { cursor: pointer; transition: background 0.1s; }
            .content-row:hover { background: #334155; }
            .col-icon { flex: 0 0 32px; text-align: center; }
            .col-title { flex: 1 1 auto; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
            .col-artist { flex: 0 0 140px; color: #94a3b8; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
            .col-duration { flex: 0 0 80px; text-align: right; color: #94a3b8; }
            .col-size { flex: 0 0 80px; text-align: right; color: #94a3b8; }

            .loading-spinner {
                display: flex; align-items: center; justify-content: center;
                padding: 32px; color: #60a5fa; font-size: 14px;
            }

            .renderer-selector { padding: 12px 16px; }
            .renderer-selector select {
                width: 100%; background: #0f172a; border: 1px solid #334155; border-radius: 6px;
                padding: 8px; color: #e2e8f0; font-size: 13px;
            }

            .album-art { padding: 16px; display: flex; justify-content: center; }
            .album-art-placeholder {
                width: 200px; height: 200px; border-radius: 8px;
                background: linear-gradient(135deg, #1e3a5f 0%, #312e81 50%, #581c87 100%);
            }

            .track-info { text-align: center; padding: 0 16px 12px; }
            .track-title { font-size: 15px; font-weight: 600; margin-bottom: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
            .track-state { font-size: 12px; color: #94a3b8; display: flex; align-items: center; justify-content: center; gap: 6px; }
            .state-dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }

            .progress-bar {
                height: 6px; background: #334155; margin: 0 16px; border-radius: 3px; cursor: pointer;
                overflow: hidden;
            }
            .progress-fill { height: 100%; background: #60a5fa; border-radius: 3px; transition: width 0.3s linear; }

            .time-display {
                display: flex; justify-content: space-between; padding: 4px 16px;
                font-size: 11px; color: #64748b;
            }

            .transport-controls {
                display: flex; justify-content: center; gap: 8px; padding: 12px;
            }
            .ctrl-btn {
                width: 40px; height: 40px; border-radius: 50%; background: #334155;
                border: 1px solid #475569; color: #e2e8f0; font-size: 16px;
                cursor: pointer; display: flex; align-items: center; justify-content: center;
                transition: background 0.15s;
            }
            .ctrl-btn:hover { background: #475569; }

            .volume-control {
                display: flex; align-items: center; gap: 8px; padding: 8px 16px;
            }
            .mute-btn { width: 32px; height: 32px; font-size: 14px; }
            .volume-slider {
                flex: 1; appearance: none; height: 4px; background: #334155; border-radius: 2px; outline: none;
            }
            .volume-slider::-webkit-slider-thumb {
                appearance: none; width: 14px; height: 14px; border-radius: 50%;
                background: #60a5fa; cursor: pointer;
            }
            .volume-value { font-size: 12px; color: #94a3b8; min-width: 28px; text-align: right; }

            .playback-mode-toggle {
                display: flex; gap: 4px; padding: 8px 12px;
                border-bottom: 1px solid #334155; background: #1e293b;
            }
            .mode-btn {
                flex: 1; padding: 6px 12px; border: 1px solid #334155; border-radius: 6px;
                background: #0f172a; color: #94a3b8; cursor: pointer; font-size: 12px;
                transition: all 0.2s; text-align: center;
            }
            .mode-btn:hover { background: #334155; color: #e2e8f0; }
            .mode-btn.active { background: #1e3a5f; color: #60a5fa; border-color: #60a5fa; }

            .browser-player-panel { flex: 1 1 auto; overflow: hidden; }
            .browser-player-expanded {
                position: absolute; top: 0; left: 0; right: 0; bottom: 0; z-index: 100;
                background: #1e293b;
            }
            .browser-player { padding: 12px; display: flex; flex-direction: column; align-items: center; flex: 1; overflow: auto; }
            .browser-player.empty {
                display: flex; align-items: center; justify-content: center;
                flex: 1; min-height: 120px;
            }
            .browser-player-fill { justify-content: center; height: 100%; }
            .browser-player-placeholder { color: #64748b; font-size: 14px; }
            .browser-player-media { width: 100%; max-height: 300px; border-radius: 4px; outline: none; }
            .browser-player-media-expanded { max-height: none; height: calc(100% - 80px); object-fit: contain; }
            .browser-player-image { max-width: 100%; max-height: 300px; object-fit: contain; border-radius: 4px; }
            .browser-player-image-expanded { max-height: none; height: calc(100% - 80px); max-width: 100%; object-fit: contain; }
            .browser-player-controls { display: flex; justify-content: space-between; align-items: center; width: 100%; margin-top: 8px; gap: 12px; }
            .browser-player-info { text-align: left; flex: 1; overflow: hidden; }
            .browser-player-title { display: block; color: #e2e8f0; font-weight: 600; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
            .browser-player-artist { display: block; color: #94a3b8; font-size: 12px; margin-top: 2px; }
            .browser-player-duration { display: block; color: #64748b; font-size: 11px; margin-top: 2px; }
            .browser-player-volume { display: flex; align-items: center; gap: 6px; flex-shrink: 0; }

            .drop-zone { transition: box-shadow 0.2s, background 0.2s; }
            .drop-zone.drag-over { box-shadow: inset 0 0 0 2px #60a5fa; background: rgba(96, 165, 250, 0.08); }

            .content-row[draggable="true"] { cursor: grab; }
            .content-row[draggable="true"]:active { cursor: grabbing; }
            .content-row.dragging { opacity: 0.4; }

            .local-player-content {
                display: flex; align-items: center; gap: 12px; padding: 16px;
                cursor: grab; user-select: none;
            }
            .local-player-content:active { cursor: grabbing; }
            .local-player-icon { font-size: 32px; }
            .local-player-info { overflow: hidden; }
            .local-player-title { font-size: 14px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
            .local-player-artist { font-size: 12px; color: #94a3b8; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
            .local-player-empty {
                display: flex; flex-direction: column; align-items: center; justify-content: center;
                flex: 1; color: #475569; font-size: 13px; gap: 4px; user-select: none;
            }
            .drop-hint { font-size: 28px; opacity: 0.5; }

            .track-info[draggable="true"] { cursor: grab; }
            .track-info[draggable="true"]:active { cursor: grabbing; }

            .app-footer {
                display: flex; justify-content: space-between; padding: 8px 24px;
                background: #1e293b; border-top: 1px solid #334155;
                font-size: 12px; color: #64748b; gap: 16px;
            }
            .footer-media-support { color: #60a5fa; font-style: italic; }

            .device-details-panel { flex: 1 1 auto; overflow-y: auto; }
            .device-details-badge {
                font-size: 11px; padding: 2px 8px; border-radius: 10px;
                background: #334155; color: #94a3b8; font-weight: 500;
            }
            .device-details-content { padding: 16px; overflow-y: auto; flex: 1; }
            .details-section { margin-bottom: 20px; }
            .details-section h3 {
                font-size: 12px; font-weight: 600; text-transform: uppercase;
                letter-spacing: 0.5px; color: #64748b; margin-bottom: 10px;
                padding-bottom: 4px; border-bottom: 1px solid #334155;
            }
            .details-grid {
                display: grid; grid-template-columns: 120px 1fr; gap: 6px 12px;
                font-size: 13px;
            }
            .details-label { color: #94a3b8; font-weight: 500; }
            .details-value { color: #e2e8f0; word-break: break-all; }
            .details-mono { font-family: 'SF Mono', Menlo, monospace; font-size: 12px; }
            .details-table { font-size: 12px; }
            .details-table-header {
                display: flex; gap: 8px; padding: 6px 0; border-bottom: 1px solid #334155;
                color: #64748b; font-weight: 600;
            }
            .details-table-header span { flex: 1; }
            .details-table-row {
                display: flex; gap: 8px; padding: 4px 0;
                border-bottom: 1px solid rgba(51, 65, 85, 0.5);
            }
            .details-table-row span { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

            .tab-warn { color: #fbbf24; }
            .tab-warn.active { color: #f59e0b; border-bottom-color: #f59e0b; }
            .failed-device .device-icon { color: #f59e0b; }
            .device-error-hint { color: #ef4444; font-size: 10px; }
            .failed-badge { background: #7f1d1d; color: #fca5a5; }
            .failed-error-box {
                background: #1c1917; border: 1px solid #7f1d1d; border-radius: 6px;
                padding: 12px; color: #fca5a5; font-size: 13px; font-family: 'SF Mono', Menlo, monospace;
                white-space: pre-wrap; word-break: break-all;
            }
            .failed-response-text {
                background: #0f172a; border: 1px solid #334155; border-radius: 6px;
                padding: 12px; color: #94a3b8; font-size: 11px; font-family: 'SF Mono', Menlo, monospace;
                white-space: pre-wrap; word-break: break-all; max-height: 400px; overflow-y: auto;
            }

            .header-actions { display: flex; align-items: center; gap: 8px; }
            .log-toggle { font-weight: 500; }
            .log-toggle.log-on { background: #065f46; border-color: #059669; color: #6ee7b7; }
            .log-toggle.log-on:hover { background: #047857; }

            .log-panel {
                display: flex; flex-direction: column; max-height: 260px;
                background: #0f172a; border-top: 2px solid #334155;
            }
            .log-panel-header {
                display: flex; justify-content: space-between; align-items: center;
                padding: 6px 16px; background: #1e293b; border-bottom: 1px solid #334155;
            }
            .log-panel-title { font-size: 12px; font-weight: 600; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.5px; }
            .log-panel-actions { display: flex; gap: 4px; }
            .log-panel-body { overflow-y: auto; flex: 1; padding: 4px 0; font-family: 'SF Mono', Menlo, monospace; font-size: 11px; }
            .log-empty { padding: 16px; text-align: center; color: #475569; font-style: italic; font-family: -apple-system, sans-serif; }
            .log-entry { padding: 3px 12px; border-bottom: 1px solid rgba(51, 65, 85, 0.3); display: flex; flex-wrap: wrap; gap: 6px; align-items: baseline; }
            .log-entry:hover { background: #1e293b; }
            .log-outgoing .log-dir { color: #f59e0b; }
            .log-incoming .log-dir { color: #22d3ee; }
            .log-time { color: #475569; min-width: 80px; }
            .log-dir { font-weight: 700; min-width: 28px; }
            .log-proto { color: #a78bfa; min-width: 44px; }
            .log-summary { color: #e2e8f0; flex: 1; }
            .log-body {
                width: 100%; margin: 4px 0 2px 0; padding: 6px 8px; background: #1e293b;
                border: 1px solid #334155; border-radius: 4px; color: #94a3b8;
                white-space: pre-wrap; word-break: break-all; font-size: 10px; max-height: 120px; overflow-y: auto;
            }

            @media (max-width: 900px) {
                .app-main { flex-direction: column; }
                .device-panel, .right-column { flex: none; max-height: 300px; }
            }
            """;
}
