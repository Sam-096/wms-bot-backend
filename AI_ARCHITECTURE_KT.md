# Godown AI — AI Response Architecture
## Knowledge Transfer Document

---

## 1. Overview

Every chat message in Godown AI goes through a 5-stage pipeline before the user sees any response:

```
User Message
     │
     ▼
[1] Intent Classification  ─── < 5ms, pure in-memory
     │
     ├── GREETING    ──► Greeting Cache              ─── < 50ms
     ├── NAVIGATION  ──► Navigation Resolver         ─── < 100ms
     ├── QUICK_QUERY ──► Quick Responder (DB lookup) ─── < 300ms
     │
     └── AI_QUERY / UNKNOWN
              │
              ▼
         [2] Context Building
              ├── PromptContextService  (live DB stats)
              ├── ContextBuilderService (RAG/history)
              └── EmbeddingService      (semantic docs)
              │
              ▼
         [3] System Prompt Assembly (WMSPromptBuilder)
              │
              ▼
         [4] 4-Tier LLM Fallback Chain
              ├── Tier 1: Ollama    (local,  10s timeout)
              ├── Tier 2: Groq      (cloud,   8s timeout + circuit breaker)
              ├── Tier 3: Sarvam    (Indian,  5s timeout + circuit breaker)
              └── Tier 4: Rule-Based (always works, no external deps)
              │
              ▼
         [5] Response Cleaning (ResponseCleanerUtil)
              └── Strips <think>...</think> blocks
              │
              ▼
         SSE Token Stream → Frontend
```

---

## 2. Stage 1 — Intent Classification

**File:** `intent/IntentClassifierService.java`

Before any LLM is called, every message is classified by keyword matching in under 5ms.

| Intent | Trigger | Handler | Latency |
|---|---|---|---|
| `GREETING` | "hi", "hello", "namaste", "నమస్కారం", "नमस्ते" | `GreetingResponseCache` | < 50ms |
| `NAVIGATION` | "go to", "open", "show me" + module name | `NavigationResolver` | < 100ms |
| `QUICK_QUERY` | "how many", "count", "pending", "low stock" | `QuickResponder` (DB) | < 300ms |
| `AI_QUERY` | everything else warehouse-related | Full LLM Pipeline | 1–5s |
| `UNKNOWN` | nothing matches | Full LLM Pipeline | 1–5s |

**Key design principle:** The LLM is NEVER called for greetings or navigation. This keeps 60–70% of requests under 100ms.

---

## 3. Stage 2 — Context Building (AI queries only)

**Files:** `ai/service/PromptContextService.java`, `context/ContextBuilderService.java`, `embedding/EmbeddingService.java`

Three data sources are combined:

### 3a. Live Warehouse Data (`PromptContextService`)
Fetches real-time data from the database before every AI request. This is what was added to fix "bot says sample data".

```java
// 4 DB queries, single boundedElastic thread, ~20-50ms
LiveWarehouseContext {
    int  vehiclesInside      // from gate_pass WHERE status='OPEN'
    int  pendingInward       // from inward_transactions WHERE status='PENDING'
    int  todayDispatched     // from outward_transactions WHERE date=today AND status='APPROVED'
    List lowStockItems       // from stock_inventory WHERE current < min_threshold (max 10)
}
```

If the DB is slow or unavailable, returns `LiveWarehouseContext.empty()` — the prompt pipeline never blocks.

### 3b. RAG Context (`ContextBuilderService` + `EmbeddingService`)
Fetches relevant historical data and semantic document matches for the query.

### 3c. Combining
Both contexts are fetched in **parallel** using `Mono.zip()` to minimize latency, then concatenated into a single context string passed to the prompt builder.

---

## 4. Stage 3 — System Prompt Assembly

**File:** `ai/service/WMSPromptBuilder.java`

The system prompt is built fresh for every AI request. It has 6 sections:

```
┌─────────────────────────────────────────────────────────┐
│  IDENTITY: "You are GODOWN AI, assistant for {name}"    │
│  LANGUAGE: {detected language instruction}              │
│  ROLE: {user's role and access level}                   │
├─────────────────────────────────────────────────────────┤
│  LIVE WAREHOUSE DATA (as of 06-Mar-2025 14:30):         │
│  - Vehicles currently inside gate : 3                   │
│  - Pending inward GRNs            : 12                  │
│  - Today approved dispatches      : 5                   │
│  - Low stock items (2):                                 │
│      * Rice — current: 200 QTL | min: 500 QTL           │
│      * Wheat — current: 50 MT | min: 100 MT             │
│  STRICT RULES: use ONLY these figures...                │
├─────────────────────────────────────────────────────────┤
│  MODULES: (10 modules with exact navigation paths)      │
│  RESPONSE RULES: (brevity, emoji, no guessing)          │
│  ACTION FORMAT: ```action{...}``` for UI buttons        │
│  ROLE RESTRICTIONS: (per-role access policy)            │
└─────────────────────────────────────────────────────────┘
```

### Language Handling in the Prompt
The prompt tells the model which language to respond in:

```java
"te" → "Telugu - use warehouse talk, mix English terms like Inward, Gate Pass, Bond"
"hi" → "Hindi - simple warehouse Hindi, keep English for: Entry, Pass, Stock, Bond"
"en" → "English - simple, clear, no jargon"
// + 6 more Indian languages (ta, kn, mr, bn, gu, pa, or)
```

The model receives the user's message in their language AND gets instructed to respond in that language. This is why multilingual works naturally without any translation layer.

### Action Button Format
When the bot wants to show navigation buttons, it appends:
```
```action{"actions":[{"label":"New Inward","route":"inward/new","icon":"📦"}]}```
```
The frontend parses this JSON block and renders clickable buttons.

---

## 5. Stage 4 — 4-Tier LLM Fallback Chain

**Files:** `ai/LLMFallbackChain.java`, `ai/port/LLMProvider.java`

This is the core reliability mechanism. Every request goes through `LLMFallbackChain.stream()`:

```java
Tier1 (Ollama)
  .onErrorResume → Tier2 (Groq)
    .onErrorResume → Tier3 (Sarvam)
      .onErrorResume → Tier4 (RuleBased)
```

Each tier logs which provider responded on the first token: `"Response from: GROQ"`.

---

### Tier 1 — Ollama (Local LLM)

**File:** `ai/provider/OllamaProvider.java`

| Property | Value |
|---|---|
| Endpoint | `POST /api/chat` (localhost:11434) |
| Model | `llama3.2:3b` (configurable via `ollama.model`) |
| Timeout | 10 seconds |
| Streaming | True token-by-token (Ollama native streaming) |
| Cloud deploy | Disabled (`ollama.enabled=false` in production) |

**Startup check:** `@PostConstruct` calls `GET /api/tags` to verify the model is downloaded. If Ollama is not running, logs a WARNING and continues — does **not** crash the app.

**When it is used:** Local development only. Set `OLLAMA_BASE_URL` and `ollama.model` in `application.yml`.

**Token extraction:**
```java
// Ollama returns: {"message":{"role":"assistant","content":"word"},"done":false}
int start = json.indexOf("\"content\":\"") + 11;
int end   = json.indexOf("\"", start);
// returns raw token string
```

---

### Tier 2 — Groq (Cloud, Primary for Production)

**File:** `ai/provider/GroqProvider.java`

| Property | Value |
|---|---|
| Endpoint | `POST /chat/completions` (api.groq.com) |
| Model | `llama3-8b-8192` (OpenAI-compatible) |
| Timeout | 8 seconds |
| Auth | `Authorization: Bearer ${GROQ_API_KEY}` |
| Streaming | OpenAI SSE format (`data: {...}`) |
| Circuit Breaker | Yes — Resilience4j "groq" instance |

**Circuit Breaker settings:**
```yaml
resilience4j.circuitbreaker.instances.groq:
  sliding-window-size: 5            # evaluate last 5 calls
  failure-rate-threshold: 60        # open if 60%+ fail
  wait-duration-in-open-state: 30s  # wait before retrying
  permitted-number-of-calls-in-half-open-state: 2
```

**When GROQ_API_KEY is missing:** Immediately returns `Flux.error(...)` — the chain falls through to Tier 3 in < 1ms. No network call is made.

**Token extraction (OpenAI SSE format):**
```java
// Raw SSE line: data: {"choices":[{"delta":{"content":"word"}}]}
line.substring(5)  // strip "data: "
// extract "content":"word" → "word"
```

**Why Groq for production?** Free tier gives 14,400 requests/day on llama3-8b. Fast (~1s TTFT). No GPU needed on the server.

---

### Tier 3 — Sarvam AI (Indian Language Specialist)

**File:** `ai/provider/SarvamProvider.java`

| Property | Value |
|---|---|
| Endpoint | `POST /v1/chat/completions` (api.sarvam.ai) |
| Model | `sarvam-m` |
| Timeout | 5 seconds |
| Auth | `api-subscription-key: ${SARVAM_API_KEY}` (header, not Bearer) |
| Streaming | OpenAI-compatible SSE |
| Circuit Breaker | Yes — Resilience4j "sarvam" instance |

**Why Sarvam?** Built specifically for Indian languages. When a user writes in Telugu or Hindi and Groq is unavailable, Sarvam's model understands the nuance of Indian warehouse terminology better than a generic English LLM.

**Note on Auth:** Sarvam uses a custom header `api-subscription-key` instead of the standard `Authorization: Bearer` — this is set at WebClient construction time.

**When SARVAM_API_KEY is missing:** Same as Groq — immediate Flux.error, falls through to Tier 4.

---

### Tier 4 — Rule-Based (Always Works)

**File:** `ai/provider/RuleBasedProvider.java`

The last-resort fallback. No external API. No network call. No failure possible.

**How it works:**
```java
// 1. Detect language from Unicode ranges
'\u0C00'–'\u0C7F' → Telugu
'\u0900'–'\u097F' → Hindi
otherwise         → English

// 2. Match keyword in message
"stock"   → inventory page navigation message (in detected language)
"inward"  → inward receipts page message
"outward" → outward dispatch page message
"gate"    → gate pass management message
"bond"    → bond management message
default   → generic "AI unavailable" message

// 3. Emit word-by-word to keep streaming feel
response.split("(?<=\\s)|(?=\\s)")  // split on whitespace boundaries
Flux.fromArray(words)               // emit each word as a token
```

**Example output in Telugu (when all LLMs are down):**
> "క్షమించండి, AI సేవ తాత్కాలికంగా అందుబాటులో లేదు. స్టాక్ స్థాయిలను తనిఖీ చేయడానికి దయచేసి స్టాక్ ఇన్వెంటరీ పేజీని సందర్శించండి."

**Example output in Hindi:**
> "क्षमा करें, AI सेवा अस्थायी रूप से अनुपलब्ध है। स्टॉक स्तर जांचने के लिए कृपया स्टॉक इन्वेंटरी पेज देखें।"

This tier ensures the app **never** returns a 500 error or blank response to the user.

---

## 6. Stage 5 — Response Cleaning

**File:** `ai/util/ResponseCleanerUtil.java`

Some models (DeepSeek-R1, Qwen-QwQ) emit internal reasoning wrapped in `<think>` tags before the actual answer. These must never reach the frontend.

### String mode (for complete responses)
```java
Pattern.compile("(?s)<think>.*?</think>\\s*").replaceAll("").trim()
```

### Streaming mode (SSE tokens)
Applied as `.transform(responseCleaner::cleanStream)` on the token `Flux<String>`.

Uses Reactor's `scan` operator to carry immutable state across tokens. The state machine has 3 phases:

```
UNDETERMINED ──(no <think> found, buffer >= 50 chars)──► PASS_THROUGH
             ──(<think> found in buffer)──────────────► IN_THINK
IN_THINK     ──(</think> found)───────────────────────► PASS_THROUGH
PASS_THROUGH ──(steady state, emit all tokens)────────► PASS_THROUGH
```

**Why stateful scan?** The `<think>` and `</think>` tags may arrive split across multiple tokens (e.g., `</thi` + `nk>`). The 9-character tail buffer in `IN_THINK` state detects partial closing tags across token boundaries.

**User experience:**
- For responses without `<think>`: ~50 character preamble buffer (~10 tokens delay), then normal streaming
- For responses with `<think>`: user sees blank screen while think block processes, then streaming starts — much better than seeing raw `<think>reasoning...</think>` garbage

---

## 7. Multilingual Architecture — Why It Works

Godown AI supports 10 Indian languages with **zero translation infrastructure**:

```
Telugu (te)  Hindi (hi)   Tamil (ta)   Kannada (kn)   Marathi (mr)
Bengali (bn) Gujarati (gu) Punjabi (pa) Odia (or)      English (en)
```

### Language Detection — 3 layers:

**Layer 1 — Frontend sends `language` field**
```json
{"message": "stock chekku", "language": "te"}
```
The frontend detects the user's language and sends the `language` code in `ChatRequest`.

**Layer 2 — Unicode detection in OllamaLLMAdapter**
```java
for (char c : message.toCharArray()) {
    if (c >= '\u0C00' && c <= '\u0C7F') return "te";  // Telugu Unicode block
    if (c >= '\u0900' && c <= '\u097F') return "hi";  // Devanagari Unicode block
}
```
Even if `language` is missing, the message script is detected automatically.

**Layer 3 — Rule-Based fallback uses same Unicode detection**
```java
private String resolveLanguage(String message, String hint) {
    if (hint != null && !hint.isBlank()) return hint;  // trust frontend first
    // then scan message chars for Unicode ranges
}
```

### Why the LLM responds in the right language

The system prompt includes:
```
LANGUAGE: Telugu - use warehouse talk, mix English terms like Inward, Gate Pass, Bond
```

Modern LLMs (llama3, Groq) are multilingual by training. When the system prompt says "respond in Telugu" and the user message is in Telugu, the model naturally responds in Telugu — no translation needed.

**The warehouse terminology approach:** Indian warehouse workers mix English technical terms naturally. The prompt instructs: *"keep English for: Entry, Pass, Stock, Bond"* — so the bot says "Inward Receipt కి వెళ్ళండి" (Go to Inward Receipt) instead of translating "Inward Receipt" into Telugu, which would be unnatural.

---

## 8. SSE Streaming to Frontend

**File:** `chat/ChatController.java`

The frontend connects via `EventSource` (Server-Sent Events). Each token is sent as:

```
event: message
data: {"type":"TOKEN","content":"word"}

event: message
data: {"type":"TOKEN","content":" counts"}

event: message
data: {"type":"DONE","intent":"AI_QUERY","aiProvider":"GROQ","processingTimeMs":1240}
```

For instant/navigation responses:
```
event: message
data: {"type":"INSTANT","content":"3 vehicles inside gate","responseType":"TEXT","suggestions":["View gate pass","Check overstay"]}

event: message
data: {"type":"NAVIGATION","content":"Inward Receipts","route":"/inward"}
```

After the stream completes, the full response is persisted to `chat_messages` asynchronously (fire-and-forget on `boundedElastic`) — it never delays the SSE stream.

---

## 9. Configuration Reference

### `application.yml` (development)
```yaml
ollama:
  enabled: true
  base-url: http://localhost:11434
  model: llama3.2:3b

groq:
  api-key: ${GROQ_API_KEY:}       # blank = Tier 2 disabled
  model: llama3-8b-8192
  timeout: 8000

sarvam:
  api.key: ${SARVAM_API_KEY:}     # blank = Tier 3 disabled
  model: sarvam-m
  timeout: 5000
```

### `application-production.yml` (Render)
```yaml
ollama:
  enabled: false    # no GPU on Render — Groq takes over immediately

groq:
  api-key: ${GROQ_API_KEY}    # REQUIRED on Render

sarvam:
  api.key: ${SARVAM_API_KEY}  # optional but recommended for Indian users
```

### Environment Variables (Render Dashboard)
| Variable | Required | Purpose |
|---|---|---|
| `GROQ_API_KEY` | Yes (production) | Enables Tier 2 LLM |
| `SARVAM_API_KEY` | Optional | Enables Tier 3 Indian LLM |
| `OLLAMA_BASE_URL` | No | Custom Ollama URL (dev only) |

---

## 10. Failure Scenarios

| Scenario | What Happens | User Experience |
|---|---|---|
| Ollama not running | `OllamaProvider` errors → Groq takes over | Normal response, slightly slower TTFT |
| GROQ_API_KEY missing | `GroqProvider` errors immediately → Sarvam | Normal response |
| Groq API rate-limited | Circuit breaker opens after 3 failures → Sarvam | Normal response |
| All LLMs down | `RuleBasedProvider` serves keyword response in user's language | Degraded but not broken |
| DB slow for context | `PromptContextService` returns `empty()` after error | AI responds without live stats |
| Model sends `<think>` tags | `ResponseCleanerUtil` strips them before frontend receives | Clean response, no leaked reasoning |
| User sends Telugu with wrong language code | Unicode detection overrides language hint | Correct language response |

---

## 11. Code Flow — Trace of One Request

**Request:** "how many vehicles are inside?" (language: "en", warehouseId: "WH001")

```
ChatController.chat()
  └── ChatOrchestratorImpl.handle()
        ├── IntentClassifier.classify("how many vehicles are inside?")
        │     └── returns IntentResult(type=AI_QUERY, confidence=0.85)
        │
        └── streamWithContext(req, start)
              ├── ContextBuilderService.buildContext()  ─── parallel
              ├── EmbeddingService.findRelevantDocs()   ─── parallel
              └── Mono.zip() waits for both
                    │
                    └── streamWithLiveContext(req, ragContext, start)
                          ├── PromptContextService.fetchContext("WH001")
                          │     ├── gatePassRepo.findActiveByWarehouseId("WH001") → [3 passes]
                          │     ├── inwardRepo.countByWarehouseIdAndStatus("WH001","PENDING") → 12
                          │     ├── outwardRepo.countTodayDispatched("WH001", today) → 5
                          │     └── stockRepo.findLowStockItems("WH001") → [rice, wheat]
                          │
                          └── streamLLM(req, ragContext, live, start)
                                ├── WMSPromptBuilder.build(...)
                                │     └── injects "Vehicles currently inside gate : 3"
                                │
                                ├── OllamaLLMAdapter.streamChat(prompt, "how many vehicles...")
                                │     └── LLMFallbackChain.stream()
                                │           ├── OllamaProvider → ERROR (not running in prod)
                                │           └── GroqProvider → SUCCESS → Flux<"3","vehicles","are","inside">
                                │
                                ├── .transform(responseCleaner::cleanStream)
                                │     └── UNDETERMINED → no <think> → PASS_THROUGH (all tokens emitted)
                                │
                                └── .map(ChatResponse::token)
                                      └── SSE: TOKEN("3") TOKEN(" vehicles") ... DONE(aiProvider=GROQ)

ChatController (doFinally)
  └── persistAsync() → saves to chat_messages (fire-and-forget)
```

**Total time:** ~1.2s (20ms context + 50ms prompt build + 1150ms Groq TTFT)
**Response:** "3 vehicles are currently inside the gate. You can view details at Gate Operations > Vehicle Status."


---

## 12. Voice Transcription — Technical Deep Dive

### How It Works End-to-End

The voice pipeline converts spoken warehouse queries into text and feeds them directly
into the chat pipeline. The entire flow is fully reactive (no blocking threads).

```
Browser / Mobile App
  │
  │  POST /api/v1/voice/transcribe
  │  Content-Type: multipart/form-data
  │  Fields:
  │    audio  = <binary WebM/OGG/WAV blob>   (FilePart)
  │    lang   = "te" | "hi" | "en" | ""      (optional hint)
  │
  ▼
VoiceController.transcribe(FilePart audio, String lang)
  │
  ├── [1] DataBufferUtils.join(audio.content())
  │         Merges the chunked Netty DataBuffer stream into one buffer.
  │         Must call DataBufferUtils.release(buffer) in doFinally — failure
  │         causes off-heap memory leak (Netty allocates outside JVM heap).
  │
  ├── [2] buffer.asByteBuffer().array() → byte[]
  │         Copies the pooled Netty buffer into a plain Java byte array.
  │         Release happens AFTER this copy, inside doFinally.
  │
  ├── [3] SarvamService.transcribe(byte[] audioBytes, String lang)
  │         Builds multipart HTTP request to Sarvam AI STT API.
  │
  ▼
SarvamService — Sarvam AI Speech-to-Text
  │
  ├── Model:   saarika:v2   (Sarvam's multilingual Indian ASR model)
  ├── URL:     https://api.sarvam.ai/speech-to-text
  ├── Auth:    api-subscription-key: ${sarvam.api-key}
  │            (NOT Bearer token — Sarvam uses a custom header)
  │
  ├── Request body (multipart/form-data):
  │     file           = audioBytes   (binary audio data)
  │     model          = saarika:v2
  │     language_code  = "unknown"    ← KEY DECISION
  │
  │   WHY "unknown"?
  │   Setting language_code="unknown" activates Sarvam's automatic
  │   language identification (LangID). The model analyses the first
  │   few hundred milliseconds of audio and picks the best matching
  │   Indian language from its vocabulary. This means users can speak
  │   in Telugu, Hindi, Tamil, Marathi, Kannada — without the app
  │   needing to know which language in advance.
  │
  ├── Sarvam API Response (JSON):
  │     {
  │       "transcript":     "ఇప్పుడు గోడౌన్‌లో ఎన్ని వాహనాలు ఉన్నాయి",
  │       "language_code":  "te",     ← detected language
  │       "request_id":     "srv_..."
  │     }
  │
  ├── Parsed to: TranscriptResult(transcript, languageCode)
  │
  ▼
VoiceController — Response back to frontend
  │
  └── Returns JSON:
        {
          "transcript":     "ఇప్పుడు గోడౌన్‌లో ఎన్ని వాహనాలు ఉన్నాయి",
          "language_code":  "te"
        }
```

### Language Code Feedback Loop

The detected `language_code` from Sarvam is returned to the frontend.
The frontend then includes it in the next chat request:

```json
POST /api/v1/chat
{
  "message":    "ఇప్పుడు గోడౌన్‌లో ఎన్ని వాహనాలు ఉన్నాయి",
  "language":   "te",          ← fed from STT response
  "warehouseId": "WH001"
}
```

`WMSPromptBuilder` picks up `language = "te"` and switches the entire
system prompt to Telugu, instructing the LLM to respond in Telugu.

### Reactive Buffer Safety Pattern

```java
// VoiceController — exact pattern used
return DataBufferUtils.join(audio.content())           // Mono<DataBuffer>
    .flatMap(buffer -> {
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        DataBufferUtils.release(buffer);               // MUST release before flatMap exits
        return sarvamService.transcribe(bytes, lang);
    })
    .map(result -> Map.of(
        "transcript",    result.transcript(),
        "language_code", result.languageCode()
    ));
```

Key rules:
- `DataBufferUtils.join()` collects all Netty chunks — safe for audio files up to ~10MB
- Release must happen synchronously inside the `flatMap` lambda, not in `doFinally`
  (the buffer scope ends when the lambda returns)
- If release is skipped, each voice request leaks ~1-4MB of off-heap Netty memory


---

## 13. WMS Context Building — Technical Deep Dive

### Three Independent Context Systems

Every AI_QUERY assembles a system prompt from three data sources, each serving a
different purpose:

```
+========================+====================+================================+
| System                 | Data source        | What it provides               |
+========================+====================+================================+
| PromptContextService   | JPA repositories   | Live counts (vehicles, orders,  |
|                        | (4 queries)        | stock) — grounding numbers      |
+------------------------+--------------------+--------------------------------+
| ContextBuilderService  | JdbcTemplate       | Row-level transactional detail  |
|                        | (up to 5 queries,  | — recent inward/outward,        |
|                        | keyword-gated)     | low-stock items, gate status    |
+------------------------+--------------------+--------------------------------+
| EmbeddingService       | pgvector (RAG)     | Semantically similar historical |
|                        | cosine distance    | training data / past answers    |
+========================+====================+================================+
```

All three run in parallel (zipped Mono) and land in the system prompt as
distinct labelled sections.

---

### System 1 — PromptContextService (Live Counts)

**Location:** `ai/service/PromptContextService.java`

Fetches exactly 4 queries, all in one `Mono.fromCallable` on `boundedElastic`:

```java
int vehiclesInside  = gatePassRepo.findActiveByWarehouseId(warehouseId).size();
int pendingInward   = (int) inwardRepo.countByWarehouseIdAndStatus(warehouseId, "PENDING");
int todayDispatched = (int) outwardRepo.countTodayDispatched(warehouseId, LocalDate.now());
List<StockEntry> low = stockRepo.findLowStockItems(warehouseId).stream().limit(10).toList();
```

What lands in the system prompt (via `WMSPromptBuilder.formatLiveContext()`):

```
=== LIVE WAREHOUSE DATA (as of 2026-04-11T14:32:01) ===
Vehicles currently inside gate : 3
Pending inward shipments       : 12
Dispatched today               : 5
Low-stock items (top 10)       :
  - Rice Basmati : 45.000 kg  (threshold 100.000 kg)
  - Wheat Flour  : 12.500 kg  (threshold 50.000 kg)
==========================================

STRICT DATA RULES:
- These numbers are REAL. Use them exactly as shown.
- NEVER say "sample data", "example", or invent numbers.
- If a field is 0, say 0 — do not guess.
```

**Failure safety:** `onErrorResume` returns `LiveWarehouseContext.empty()` —
the pipeline continues with zeros rather than failing the entire chat request.

---

### System 2 — ContextBuilderService (Row-Level Detail)

**Location:** `ai/service/ContextBuilderService.java`
Uses `JdbcTemplate` (not JPA) for full SQL flexibility.

#### Keyword-Gating — Why Only Query What's Needed

Running all 5 queries for every message would add ~80-150ms latency.
Instead, the message is scanned for keywords before any DB query executes:

```java
// English
if (msg.contains("inward") || msg.contains("shipment") || msg.contains("received"))
    sb.append(fetchRecentInward(warehouseId));

if (msg.contains("outward") || msg.contains("dispatch") || msg.contains("delivery"))
    sb.append(fetchRecentOutward(warehouseId));

if (msg.contains("stock") || msg.contains("inventory") || msg.contains("low"))
    sb.append(fetchLowStock(warehouseId));

if (msg.contains("gate") || msg.contains("vehicle") || msg.contains("truck"))
    sb.append(fetchGatePasses(warehouseId));

// Telugu keywords (Unicode ranges checked)
if (msg.contains("ఎంత") ||   // "ఎంత" (how much)
    msg.contains("మాల"))      // "మాల" (goods)
    sb.append(fetchLowStock(warehouseId));
```

This means a greeting ("Hello") triggers zero DB queries from this service.
A Telugu stock question triggers exactly one.

#### The 5 SQL Queries (exact columns fetched)

```sql
-- fetchRecentInward
SELECT supplier_name, item_name, quantity, unit, inward_date, status
FROM inward_transactions
WHERE warehouse_id = ? AND inward_date >= CURRENT_DATE - INTERVAL '7 days'
ORDER BY inward_date DESC LIMIT 5

-- fetchRecentOutward
SELECT recipient_name, item_name, quantity, unit, outward_date, status
FROM outward_transactions
WHERE warehouse_id = ? AND outward_date >= CURRENT_DATE - INTERVAL '7 days'
ORDER BY outward_date DESC LIMIT 5

-- fetchLowStock
SELECT item_name, current_stock, min_threshold, unit
FROM stock_inventory
WHERE warehouse_id = ? AND current_stock < min_threshold
ORDER BY (current_stock / NULLIF(min_threshold, 0)) ASC LIMIT 10

-- fetchGatePasses
SELECT vehicle_number, driver_name, purpose, entry_time, status
FROM gate_pass
WHERE warehouse_id = ? AND status = 'ACTIVE'
ORDER BY entry_time DESC LIMIT 5

-- fetchPendingCounts
SELECT
  COUNT(CASE WHEN status='PENDING' THEN 1 END) AS pending_inward,
  COUNT(CASE WHEN status='PENDING' THEN 1 END) AS pending_outward
FROM inward_transactions, outward_transactions
WHERE warehouse_id = ?
```

#### Hard Limits

- Total context string capped at **2000 characters** — prevents prompt bloat
- Low-stock items tagged with `⚠` for visual salience in the prompt
- Empty results → section skipped entirely (no "No data found" noise)

---

### System 3 — EmbeddingService (Semantic RAG)

**Location:** `ai/service/EmbeddingService.java`

#### Vector Generation

```java
// Calls Ollama locally (model: nomic-embed-text or similar)
POST http://localhost:11434/api/embeddings
{
  "model":  "nomic-embed-text",
  "prompt": "how many vehicles are inside"
}
// Response: { "embedding": [0.023, -0.187, 0.441, ... ] }  (768 or 1024 dims)
```

The float[] array is converted to pgvector string format:

```java
String toPgVectorString(float[] embedding) {
    // Produces: "[0.023,-0.187,0.441,...]"
    // pgvector column type: vector(768)
}
```

#### Similarity Search

```sql
SELECT content, metadata
FROM bot_training_data
WHERE warehouse_id = :wid
ORDER BY embedding <-> :queryVector::vector   -- cosine distance operator
LIMIT 3
```

The `<->` operator is pgvector's cosine distance. The 3 closest training
data rows are concatenated and injected as `ADDITIONAL CONTEXT:` in the prompt.

**When RAG is skipped:** If Ollama is offline (production), `EmbeddingService`
returns `Mono.just("")` — the prompt gets no RAG section but still has the
live counts from System 1 and the row-detail from System 2.

---

### How All Three Combine in the Final System Prompt

```
[ROLE + LANGUAGE INSTRUCTION]
You are Godown AI, a warehouse management assistant.
Respond ONLY in Telugu (te). ...

[LIVE DATA — from PromptContextService]
=== LIVE WAREHOUSE DATA (as of 2026-04-11T14:32:01) ===
Vehicles currently inside gate : 3
Pending inward shipments       : 12
...

[ROW-LEVEL CONTEXT — from ContextBuilderService]
Recent Gate Passes (ACTIVE):
  TN-01-AB-1234 | Ravi Kumar | Delivery | entered 13:45 | ACTIVE
  AP-09-CD-5678 | Suresh     | Pickup   | entered 12:10 | ACTIVE

Low Stock Items:
  ⚠ Rice Basmati: 45 kg (threshold: 100 kg)

[ADDITIONAL CONTEXT — from EmbeddingService / RAG]
Similar past queries answered:
  Q: How many trucks came today?
  A: 3 trucks entered today, 1 is still inside.

[USER MESSAGE]
Human: ఇప్పుడు గోడౌన్‌లో ఎన్ని వాహనాలు ఉన్నాయి
```

The LLM sees a fully grounded prompt with exact numbers, recent row data,
and semantically relevant historical context — eliminating hallucination of
warehouse-specific facts.
