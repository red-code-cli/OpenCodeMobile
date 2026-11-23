# OpenCode Server Notes (v2.3.2 Verification)

Generated during Phase 0 audit against live OpenCode v2.3.2 server.

## 1. Server Environment & API Generation
- **Version**: OpenCode 2.3.2 (`opencode --version`)
- **API Generation**: **V2 API** (`/api/*`).
  - V1 legacy routes (`/global/health`, `/session`) return web frontend HTML fallback.
  - V2 routes (`/api/info`, `/api/session`, `/api/project`, `/api/model`, etc.) are fully supported.
- **OpenAPI 3.1 Specification**: Retrieved from `GET /openapi.json` (requires Basic Auth) and saved to `docs/openapi.json` (~448 KB).

## 2. Authentication & Pairing
- **HTTP Basic Auth**: Username `opencode` (or configured env `OPENCODE_SERVER_USERNAME`), password set via `OPENCODE_SERVER_PASSWORD` or auto-generated.
  Header: `Authorization: Basic base64(user:pass)`.
- **Pairing (V2)**:
  - Code redemption: `GET /auth/connect/{code}` with `Accept: application/json` returns `{ "token": "<session_token>" }`.
  - Token is used as the password in HTTP Basic Auth.
- **Server Identity & Health**:
  - `GET /api/info` -> `{ "version": "2.3.2", "pid": ..., "hostname": "...", "urls": ["..."], "paths": {"tmp": "..."}, "capabilities": {"persistentPty": true} }`

## 3. Endpoints & Schemas

### Projects
- `GET /api/project`: Returns list of project objects:
  ```json
  [
    {
      "id": "cd31f2124f23b5c0556ebda23ab6bff6bdc91712",
      "canonical": "/root",
      "time": { "created": 1795010834907, "updated": 1795010834907, "active": 1795413893497 }
    }
  ]
  ```
  Project name is derived from canonical directory basename (e.g. `root`, `pyclogy`, `Redcode`).

### Sessions
- `GET /api/session`: Returns `{ "data": [Session], "cursor": { "previous": ..., "next": ... } }`.
- Session object:
  ```json
  {
    "id": "ses_df9121730ffepeqO7ntc75UP6d",
    "projectID": "cd31f2124f23b5c0556ebda23ab6bff6bdc91712",
    "agent": "build",
    "model": { "id": "deepseek-v4.1-flash-free", "providerID": "opencode", "variant": "max" },
    "cost": 0,
    "tokens": { "input": 62351, "output": 4843, "reasoning": 4552, "cache": { "read": 944896, "write": 0 } },
    "outcome": "interrupted",
    "time": {
      "created": 1795412584773,
      "updated": 1795413893506,
      "busy": 1795413748376,
      "idle": 1795413893564,
      "viewed": 1795413893564
    },
    "title": "Run the peakcut repo",
    "location": { "directory": "/root" }
  }
  ```
  - **Running/Busy detection**: When `time.busy > time.idle` (or `time.idle == null`), the session is currently active/busy.
  - **Viewed / Catch-up**: `time.viewed < time.idle` indicates an unread completion. Mark read via `POST /api/session/:id/view`.
- **Create Session**: `POST /api/session` body `{ "location": { "directory": "<dir>" }, "title": "..." }` or empty body.
- **Rename Session**: `PATCH /api/session/:id` with `{ "title": "New Title" }`.
- **Delete Session**: `DELETE /api/session/:id`.
- **Fork Session**: `POST /api/session/:id/fork`.
- **Interrupt / Abort**: `POST /api/session/:id/interrupt`.

### Messages
- `GET /api/session/:id/message`: Returns `{ "data": [Message] }` or `[Message]`.
- User message:
  ```json
  {
    "id": "msg_206f13315001GleFvsvgeABDR4",
    "type": "user",
    "text": "hinglish bolo ",
    "time": { "created": 1795412800535 }
  }
  ```
- Assistant message:
  ```json
  {
    "id": "msg_207001041001aMTN1pdNiZtnAF",
    "type": "assistant",
    "agent": "build",
    "model": { "id": "...", "providerID": "...", "variant": "..." },
    "content": [
      { "type": "reasoning", "text": "...", "time": { "created": ..., "completed": ... } },
      { "type": "text", "text": "..." },
      {
        "type": "tool",
        "id": "chatcmpl-tool-...",
        "name": "shell",
        "state": {
          "status": "completed" | "error" | "running" | "pending",
          "input": { "command": "..." },
          "content": [{ "type": "text", "text": "..." }],
          "error": { "message": "..." }
        }
      }
    ],
    "tokens": { "input": ..., "output": ..., "reasoning": ..., "cache": { "read": ..., "write": ... } },
    "cost": 0.0
  }
  ```
- Prompt submission: `POST /api/session/:id/prompt` with `{ "text": "...", "agent": "...", "model": { ... } }`.

### Agents & Models
- `GET /api/agent`: Returns `{ "data": [Agent] }`. Agents have `id`, `name`, `mode` ("primary" vs "subagent"), `hidden` (boolean), `description`. Filter for primary/non-hidden in UI selector.
- `GET /api/model`: Returns `{ "data": [Model] }` (540+ models catalog).
- `GET /api/model/default`: Returns default model for current directory.
- Model variant options come from `model.variants` (e.g. `none`, `low`, `medium`, `high`, `xhigh`, `max`).

### Permissions & Questions/Forms
- `GET /api/permission`: Returns pending tool permission requests.
  - Reply: `POST /api/session/:sid/permission/:rid/reply` with `{ "decision": "allow" | "always" | "deny" }`.
- `GET /api/form`: Returns pending interactive agent questions/forms.
  - Reply: `POST /api/session/:sid/form/:fid/reply` with `{ "answers": ... }`.

### Real-Time SSE Stream
- `GET /api/event`: Header `Accept: text/event-stream`.
- Format:
  `data: {"id":"evt_...","type":"<event_type>","data":{...}}`
  `: heartbeat`
- Event types: `server.connected`, `session.created`, `session.updated`, `session.deleted`, `session.status`, `message.updated`, `session.text.delta`, `provider.updated`, `model.updated`, `agent.updated`, `command.updated`.
