# Frontend Changes — Phase 1 (JSON Leak Fix + Server-Side Role Gate)

Ship alongside backend commit `phase-1-json-leak-fix`.

## Context

Users were seeing raw text like this appear inside chat bubbles:

```
{"type":"INFO","content":"ప్రస్తుతం Inward డేటా...","data":[{"label":"Inward Receipts","route":"/operations/inward-receipts"}]}
```

Root cause: prompt templates instructed the LLM to emit JSON envelopes for ACCESS_DENIED /
INFO / action cards. The model copied that pattern into free-form replies. Backend now:

1. Removed all JSON-envelope examples from the prompt.
2. Emits `ACCESS_DENIED` server-side (new `RoleAccessPolicy`) — never via the LLM.
3. `ResponseCleanerUtil` strips any JSON envelope or code fence that still slips through.

The frontend needs three adjustments.

---

## 1. Handle the `ACCESS_DENIED` SSE event

**Event shape** (already documented in `ChatController` Javadoc):

```json
{
  "type": "ACCESS_DENIED",
  "content": "You don't have access to /reports. You can work on Inward, Outward, or Gate Pass.",
  "responseType": "ACTION",
  "intent": "ACCESS_CONTROL",
  "data": [
    { "label": "New Inward",  "route": "/inward/new" },
    { "label": "New Outward", "route": "/outward/new" },
    { "label": "Gate Pass",   "route": "/gate-operations" }
  ],
  "aiProvider": "RULE_BASED",
  "timestamp": "2026-04-14T17:49:00+05:30"
}
```

It arrives over the existing SSE stream, immediately followed by a `DONE` event
(no `TOKEN` stream). Treat it like `INSTANT` for bubble placement.

**Render rule** — show a dismissible warning card:

```
┌──────────────────────────────────────────────┐
│  ⚠  {content}                                │
│                                               │
│  [ New Inward ] [ New Outward ] [ Gate Pass ] │
└──────────────────────────────────────────────┘
```

Clicking a button should call the router (`navigate(route)`) and close the card.

**Pseudo-code (React/Vue-agnostic)**:

```ts
onSseEvent((evt) => {
  const msg = JSON.parse(evt.data);
  switch (msg.type) {
    case "ACCESS_DENIED":
      appendMessage({
        kind: "access-denied",
        text: msg.content,
        actions: msg.data,   // [{label, route}]
      });
      return;
    // existing TOKEN / INSTANT / NAVIGATION / DONE / ERROR handlers unchanged
  }
});
```

---

## 2. Defense-in-depth: drop inline JSON leakage from TOKEN streams

The backend cleaner catches leakage, but add a belt-and-braces filter on the client
in case an older build is deployed or a new leak pattern appears.

When assembling text from `TOKEN` events, **strip any substring matching**:

```regex
/\{\s*\\?"type\\?"\s*:[^}]*\}(?:\s*\])?\s*/gs
/```[a-zA-Z]*\n?[\s\S]*?```\s*/g
```

If you use a markdown renderer, also strip triple-backtick code fences —
the bot should never emit code.

---

## 3. Always send `language` on every chat request

Language continuity is now resolved as: request → session → `"en"`. Zero FE changes
are *required*, but for consistency:

- Store the user's selected language in local state (`selectedLanguage`).
- Send it on **every** `POST /api/v1/chat` request body:

  ```json
  { "message": "...", "sessionId": "...", "warehouseId": "...", "language": "te" }
  ```

- When the user changes the language, the NEXT request updates the session and
  every subsequent reply stays in that language.
- Supported codes: `en, te, hi, ta, kn, mr, bn, gu, pa, or`.

---

## 4. Event contract reference (no changes, for quick scan)

| Type            | Fields populated                                                      | Render as            |
|-----------------|-----------------------------------------------------------------------|----------------------|
| `TOKEN`         | `content`                                                             | Append to bubble     |
| `INSTANT`       | `content`, `responseType`, (`data`, `suggestions`)                    | Full bubble          |
| `NAVIGATION`    | `content` (label), `route`                                            | "Go to …" card + nav |
| `ACCESS_DENIED` | `content`, `data[{label,route}]`, `responseType="ACTION"`             | **NEW** warn card    |
| `DONE`          | `intent`, `aiProvider`, `processingTimeMs`                            | Close stream         |
| `ERROR`         | `content` (code: `AI_OFFLINE`, `INTERNAL_ERROR`, `SERIALIZATION_ERROR`)| Toast / retry prompt |

---

## 5. Phase 2 preview (do NOT implement yet)

Phase 2 introduces `SUGGEST_NAV` with Yes/No confirmation for complex actions
("Take me to create a new bond?"). Event shape will be:

```json
{
  "type": "SUGGEST_NAV",
  "content": "Open the New Bond form?",
  "route": "/bonds/new",
  "data": [
    { "label": "Yes", "action": "confirm" },
    { "label": "No",  "action": "cancel"  }
  ]
}
```

Tracking in Phase 2 issue — no work needed now. Just don't crash if this event
type appears in preview builds.
