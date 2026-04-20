# HTML + React Collaborative Viewer/Editor Design

## 1) Goals & Non-Goals

### Goals
- Let any authenticated user paste HTML and render it immediately in the app.
- Persist every change globally so all users see updates across sessions/devices.
- Support collaborative edits (near real-time sync) with conflict-safe history.
- Be mobile friendly (phones/tablets) with responsive editing and preview.
- Run with a Java backend deployable on Oracle Java server infrastructure.

### Non-Goals (v1)
- Full WYSIWYG visual drag/drop page builder.
- Arbitrary server-side execution from pasted HTML/JS.

---

## 2) Recommended High-Level Architecture

```text
[React Web App (PWA)]
  - HTML Editor + Preview
  - WebSocket client (presence + live updates)
  - REST client (load/save/versioning)
        |
        | HTTPS + WSS
        v
[Java API Service (Spring Boot on Oracle JDK)]
  - AuthN/AuthZ (JWT + RBAC)
  - Document CRUD + Versions
  - Operational transform/CRDT coordinator (or patch merge)
  - Validation/sanitization pipeline
  - WebSocket broadcast hub
        |
        +--> [Redis] pub/sub + short-lived collaboration state (optional, recommended)
        |
        +--> [Oracle DB] durable storage for documents, versions, audit trail
```

### Why this works for your use case
- **Global sync:** updates are written to shared durable storage and pushed to connected clients.
- **Works with pasted HTML:** editor accepts raw HTML string and stores canonical source.
- **Oracle Java server compatibility:** Spring Boot/Jakarta stack runs cleanly on Oracle JDK and standard Java server environments.
- **Mobile readiness:** React responsive layout + PWA behavior.

---

## 3) Frontend (React) Design

## Core screens
1. **Workspace list**: select document/page.
2. **Editor screen**:
   - Left: code editor (Monaco or CodeMirror).
   - Right: live preview pane in sandboxed iframe.
   - Top bar: Save status, collaborators online, version timestamp.
3. **History panel**:
   - version list + restore action.

## Mobile-first UX rules
- Breakpoint behavior:
  - `>=1024px`: split editor/preview.
  - `600-1023px`: tab switch (Edit | Preview).
  - `<600px`: single-pane with sticky action bar.
- Large touch targets (`>=44px`).
- Auto-save indicator always visible.
- Keyboard-safe layout (avoid hidden controls when mobile keyboard is open).

## Rendering pasted HTML safely
- Render inside sandboxed iframe:
  - `sandbox="allow-forms allow-same-origin"` (do **not** allow top navigation/popups by default).
- Optionally strip dangerous tags/attrs before preview (DOMPurify configured for your policies).
- Keep raw original in version history; store sanitized preview output separately if needed.

---

## 4) Backend (Java) Design

## Suggested stack
- Java 21 (Oracle JDK)
- Spring Boot 3.x
- Spring Web + WebSocket
- Spring Security + JWT/OIDC
- JPA/Hibernate + Oracle JDBC driver
- Flyway/Liquibase for migrations
- Redis (optional but recommended for scale-out websocket fanout)

## Domain model (minimum)

### `documents`
- `id` (UUID)
- `name`
- `current_html` (CLOB)
- `current_version` (int)
- `updated_by`
- `updated_at`

### `document_versions`
- `id` (UUID)
- `document_id`
- `version`
- `html` (CLOB)
- `patch_json` (optional)
- `created_by`
- `created_at`

### `document_permissions`
- `document_id`
- `user_id`
- `role` (owner/editor/viewer)

### `audit_log`
- `id`
- `entity_type`
- `entity_id`
- `action`
- `actor_id`
- `payload_json`
- `created_at`

## API endpoints (v1)
- `POST /api/documents` create doc
- `GET /api/documents/{id}` load current doc
- `PUT /api/documents/{id}` save full html with optimistic locking
- `GET /api/documents/{id}/versions` list versions
- `POST /api/documents/{id}/restore/{version}` rollback
- `GET /api/documents/{id}/ws-token` websocket auth bootstrap

## Live sync via WebSocket
- Channel/topic per document: `/topic/documents/{id}`
- Event types:
  - `DOCUMENT_UPDATED`
  - `PRESENCE_CHANGED`
  - `LOCK_STATE_CHANGED` (if section lock strategy used)
- Include `baseVersion` in update payload to detect conflicts.

## Conflict strategy
Start simple:
1. Client sends `{html, baseVersion}`.
2. Server accepts only if `baseVersion == currentVersion`.
3. On mismatch, return `409 Conflict` with latest document.
4. Client shows “remote updates detected” and offers merge/overwrite.

Scale to richer collab later:
- CRDT/OT (Yjs, Automerge, or custom OT adapter) with server persistence.

---

## 5) Security & Governance (Important)

Because users can paste arbitrary HTML:
- Enforce strict auth and document-level authorization.
- Sanitize content before display in shared contexts.
- Use sandboxed iframe for preview isolation.
- Add CSP headers (`default-src 'self'`, controlled script/style/image policies).
- Validate and limit payload size.
- Keep immutable version history + audit trail.
- Rate limit write endpoints and websocket messages.

If embedded scripts are required for internal trusted use cases, add a per-workspace trust mode and isolate on dedicated subdomain.

---

## 6) Performance & Scale

- Use ETag/version-based caching for GET document.
- Debounce editor updates (e.g., send every 300-800ms, max buffer size).
- Persist snapshots every N operations + incremental patches between snapshots.
- Horizontal scale API nodes; use Redis pub/sub for websocket fanout.
- Store large HTML in CLOB and index document metadata only.

---

## 7) Deployment on Oracle Java Server

Two practical options:
1. **Spring Boot JAR service** behind Oracle load balancer/reverse proxy.
2. **WAR deployment** on app server if your org requires it.

Minimum environment:
- Oracle JDK 21 runtime
- Oracle DB connection pool
- Redis endpoint (optional but recommended)
- TLS certs for HTTPS/WSS
- Centralized logs + metrics (Prometheus/OpenTelemetry)

---

## 8) MVP Delivery Plan (6 weeks example)

### Week 1-2
- Auth, document CRUD, Oracle schema, base React shell.

### Week 3
- Editor + preview + save/versioning.

### Week 4
- WebSocket live updates + optimistic conflict handling.

### Week 5
- Mobile UX polish + performance tuning.

### Week 6
- Security hardening, audit trail, load testing, release prep.

---

## 9) Example update payloads

### Save request
```json
{
  "baseVersion": 12,
  "html": "<div><h1>Hello</h1></div>",
  "clientId": "web-abc-123"
}
```

### Save success
```json
{
  "documentId": "8e3125b6-...",
  "newVersion": 13,
  "updatedAt": "2026-04-20T17:45:00Z"
}
```

### Conflict response (`409`)
```json
{
  "error": "VERSION_CONFLICT",
  "currentVersion": 14,
  "currentHtml": "<div>...</div>"
}
```

---

## 10) Tech choices summary

- **Frontend:** React + TypeScript + Vite + Monaco/CodeMirror + Zustand/Redux Toolkit + Tailwind/Material UI.
- **Backend:** Spring Boot + WebSocket + Spring Security + JPA.
- **Data:** Oracle DB for durable state; Redis for collaboration fanout.
- **Ops:** Docker/Kubernetes (optional), CI/CD, observability, structured audit logs.

This gives you a production-ready path for a globally synchronized HTML editing/viewing platform that works on mobile and aligns with Oracle Java server environments.
