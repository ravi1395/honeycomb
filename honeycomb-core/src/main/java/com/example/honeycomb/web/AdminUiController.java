package com.example.honeycomb.web;

import com.example.honeycomb.util.HoneycombConstants;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(HoneycombConstants.Paths.HONEYCOMB_ADMIN)
public class AdminUiController {
    private static final String ADMIN_HTML = String.format("""
                <!doctype html>
                <html lang=\"en\">
                <head>
                  <meta charset=\"utf-8\"/>
                  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"/>
                  <title>Honeycomb Admin</title>
                  <style>
                    body { font-family: Inter, system-ui, sans-serif; background:#0b0f17; color:#e5e7eb; margin:0; }
                    header { padding:20px 28px; border-bottom:1px solid #1f2937; }
                    h1 { margin:0; font-size:22px; }
                    .grid { display:grid; grid-template-columns: repeat(auto-fit,minmax(280px,1fr)); gap:16px; padding:20px 28px; }
                    .card { background:#111827; border:1px solid #1f2937; border-radius:12px; padding:16px; }
                    .card h2 { margin:0 0 8px; font-size:16px; }
                    button { background:#2563eb; color:white; border:0; border-radius:8px; padding:6px 10px; cursor:pointer; }
                    table { width:100%%; border-collapse: collapse; font-size:13px; }
                    td, th { border-bottom:1px solid #1f2937; padding:6px; text-align:left; }
                    .muted { color:#9ca3af; }
                    .events { max-height:240px; overflow:auto; font-size:12px; }
                    .badge { display:inline-block; padding:2px 6px; border-radius:6px; background:#1f2937; }
                  </style>
                </head>
                <body>
                  <header>
                    <h1>Honeycomb Admin</h1>
                    <div class=\"muted\">Live status, metrics, and audit events</div>
                  </header>
                  <div class=\"grid\">
                    <div class=\"card\">
                      <h2>Cells</h2>
                      <div id=\"cells\" class=\"muted\">Loading…</div>
                    </div>
                    <div class=\"card\">
                      <h2>Per-cell Requests</h2>
                      <div id=\"metrics\" class=\"muted\">Loading…</div>
                    </div>
                    <div class=\"card\">
                      <h2>Audit Events</h2>
                      <div id=\"events\" class=\"events muted\">Connecting…</div>
                    </div>
                  </div>
                  <script>
                    async function loadCells(){
                      const res = await fetch('%s');
                      const data = await res.json();
                      const rows = data.map(c => `<tr><td>${c.name}</td><td>${c.running ? 'UP':'DOWN'}</td><td>${c.runningPort ?? ''}</td></tr>`).join('');
                      document.getElementById('cells').innerHTML = `<table><tr><th>Name</th><th>Status</th><th>Port</th></tr>${rows}</table>`;
                    }
                    async function loadMetrics(){
                      const res = await fetch('%s');
                      const data = await res.json();
                      const rows = Object.entries(data).map(([k,v]) => `<tr><td>${k}</td><td>${v}</td></tr>`).join('');
                      document.getElementById('metrics').innerHTML = `<table><tr><th>Cell</th><th>Requests</th></tr>${rows}</table>`;
                    }
                    function connectEvents(){
                      const box = document.getElementById('events');
                      const ws = new WebSocket((location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '%s');
                      ws.onmessage = (e) => {
                        const item = document.createElement('div');
                        try {
                          const evt = JSON.parse(e.data);
                          item.innerHTML = `<span class=\"badge\">${evt.action}</span> <span>${evt.cell ?? ''}</span> <span class=\"muted\">${evt.status}</span>`;
                        } catch {
                          item.textContent = e.data;
                        }
                        box.prepend(item);
                      };
                      ws.onopen = () => { box.textContent = ''; };
                      ws.onerror = () => { box.textContent = 'Event stream unavailable'; };
                    }
                    loadCells();
                    loadMetrics();
                    connectEvents();
                    setInterval(loadCells, 5000);
                    setInterval(loadMetrics, 8000);
                  </script>
                </body>
                </html>
                """,
            HoneycombConstants.Paths.HONEYCOMB_CELLS,
            HoneycombConstants.Paths.HONEYCOMB_METRICS + "/" + HoneycombConstants.Paths.CELLS,
            HoneycombConstants.Paths.HONEYCOMB_WS_EVENTS
    );
    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public String index() {
        return ADMIN_HTML;
    }
}
