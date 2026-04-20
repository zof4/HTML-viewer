# HTML Viewer (Collaborative React + Java)

This repository now contains a working implementation of a collaborative HTML editor/viewer:
- **Frontend:** React + Vite, mobile-friendly UI, paste/edit HTML, live preview.
- **Backend:** Spring Boot (Java 21), global in-memory document store, versioning, conflict handling, websocket broadcast.

## Project layout

- `backend/` Spring Boot API + websocket server
- `frontend/` React app
- `docs/architecture.md` architecture blueprint

## Implemented capabilities

1. Paste HTML into editor and preview in sandboxed iframe.
2. Create/load documents globally by ID.
3. Auto-save with debounced writes.
4. Version conflict handling (`409 VERSION_CONFLICT`) with automatic reload.
5. Version history endpoint and restore endpoint.
6. Real-time update broadcasts over websocket topic per document.
7. Mobile-friendly interface with edit/preview tabs.

## Local run

### 1) Start backend

```bash
cd backend
mvn spring-boot:run
```

Backend defaults to `http://localhost:8080`.

### 2) Start frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend defaults to `http://localhost:5173` and calls backend at `http://localhost:8080`.

## API quick examples

### Create document
```bash
curl -X POST http://localhost:8080/api/documents \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: alice' \
  -d '{"name":"Home","html":"<h1>Hello</h1>"}'
```

### Save document
```bash
curl -X PUT http://localhost:8080/api/documents/{id} \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: alice' \
  -d '{"baseVersion":1,"html":"<h1>Updated</h1>","clientId":"alice-web"}'
```

### List versions
```bash
curl http://localhost:8080/api/documents/{id}/versions
```

## Oracle Java server deployment guide

The backend is built for Java 21 and runs cleanly with Oracle JDK.

### A) Build artifact

```bash
cd backend
mvn -DskipTests package
```

Artifact output:
- `backend/target/html-viewer-backend-1.0.0.jar`

### B) Provision Oracle host

1. Install **Oracle JDK 21**.
2. Create service user:
   - `sudo useradd --system --create-home htmlviewer`
3. Copy jar to `/opt/htmlviewer/app.jar`.
4. Open firewall for app port (e.g., 8080) only to reverse proxy.

### C) Systemd service

Create `/etc/systemd/system/htmlviewer.service`:

```ini
[Unit]
Description=HTML Viewer Backend
After=network.target

[Service]
User=htmlviewer
WorkingDirectory=/opt/htmlviewer
ExecStart=/usr/bin/java -jar /opt/htmlviewer/app.jar --server.port=8080
Restart=always
RestartSec=5
Environment=JAVA_OPTS=-Xms256m -Xmx1024m

[Install]
WantedBy=multi-user.target
```

Then:

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now htmlviewer
sudo systemctl status htmlviewer
```

### D) Reverse proxy (recommended)

Use NGINX/Oracle HTTP Server in front of Spring Boot for TLS termination and websocket upgrades.

Required proxy behavior:
- Route `/api/*` to `http://127.0.0.1:8080`
- Route `/ws/*` with websocket upgrade headers

### E) Frontend deploy

```bash
cd frontend
npm ci
npm run build
```

Serve `frontend/dist/` from your web tier (NGINX/OHS/Object Storage static site).

Set backend URL at build time if needed:

```bash
VITE_API_BASE=https://your-domain.example.com npm run build
```

### F) Production hardening next steps

- Replace in-memory store with Oracle DB + Redis.
- Add real authentication (OIDC/JWT) and permission checks.
- Add CSP headers and request-size limits at proxy and app layer.
- Add monitoring (OpenTelemetry/Prometheus) and centralized logs.
