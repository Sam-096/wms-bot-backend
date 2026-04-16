# SESSION_CONTEXT — wms-bot-backend

**Purpose:** Skip re-exploration. Read this first in every new Claude session before touching code.
Last updated: 2026-04-16

---

## 1. What shipped in Phase 1 (already in `main`)

- **JSON-leak fix** — prompt templates no longer instruct the LLM to emit JSON envelopes.
  - [WMSPromptBuilder.java](src/main/java/com/wnsai/wms_bot/ai/service/WMSPromptBuilder.java) — JSON role-restriction templates removed, hard-ban rule added to RESPONSE RULES.
  - [ResponseCleanerUtil.java](src/main/java/com/wnsai/wms_bot/ai/util/ResponseCleanerUtil.java) — now strips `<think>`, ```` ```action{...}``` ````, `{"type":...}` envelopes, and code fences in both string + streaming modes.
- **Server-side RBAC gate** — `ACCESS_DENIED` events are produced by the server, never by the LLM.
  - [RoleAccessPolicy.java](src/main/java/com/wnsai/wms_bot/security/RoleAccessPolicy.java) — `check(role, route)` → `Optional<ChatResponse>`.
  - Wired in [ChatOrchestratorImpl.java:107-123](src/main/java/com/wnsai/wms_bot/chat/orchestration/ChatOrchestratorImpl.java) `NAVIGATION` branch before emitting nav.
- **Missing DONE events fixed** — GREETING, NAVIGATION, QUICK_QUERY now emit `ChatResponse.done()`. Previously FE waited ~60s for EventSource timeout.
- **Language continuity** — `resolveEffectiveLanguage` in orchestrator: request → session → `"en"`.
- **FE spec delivered** — [FRONTEND_CHANGES_PHASE1.md](FRONTEND_CHANGES_PHASE1.md) ready to paste into FE Claude.

## 2. What shipped post-Phase 1 (this session)

- **Redis causing 503 on `/actuator/health`** — UptimeRobot was seeing DOWN because `spring-boot-starter-data-redis-reactive` auto-configured a health indicator that failed (no Redis provisioned on Render).
  - Fix in [application.yml](src/main/resources/application.yml): excluded `RedisAutoConfiguration` + `RedisReactiveAutoConfiguration`, added `management.health.redis.enabled: false`.
  - [CacheService.java](src/main/java/com/wnsai/wms_bot/service/CacheService.java) is `@ConditionalOnBean(ReactiveRedisTemplate.class)` — inert when Redis excluded. Nothing else references it.
- **Groq 400 Bad Request fix** — model `llama3-8b-8192` was **decommissioned by Groq in Jan 2025**.
  - Swapped to `llama-3.1-8b-instant` in [application.yml](src/main/resources/application.yml), [application-production.yml](src/main/resources/application-production.yml), and [GroqProvider.java:44](src/main/java/com/wnsai/wms_bot/ai/provider/GroqProvider.java#L44) default.
  - Before fix: every AI_QUERY fell through Ollama (disabled) → Groq (400) → Sarvam (worked, ~1.5s). Now Groq should answer in ~300-500ms.
- **Seed data loaded** — [db/seed-test-data.sql](db/seed-test-data.sql) inserted 375 rows into WH-001 (45 stock, 120 inward, 120 outward, 70 gate passes, 20 bonds). All rows tagged `SEED-` for cleanup.

## 3. Known pending issues (not yet fixed)

| # | Issue | Evidence | Likely cause |
|---|---|---|---|
| 1 | GREETING total latency 4407ms despite internal 92ms | Render log 2026-04-15 07:53 | ~3.4s spent before controller — filter-chain overhead. Suspect `JwtAuthFilter` doing sync DB lookup or first-request reactive pipeline compile. |
| 2 | Intent classifier runs **twice** per request | Render log — duplicate `Intent=GREETING` logs on same message | Something calls `IntentClassifierService.classify()` before `ChatOrchestratorImpl.dispatch()`. Check `ChatController` + `IntentRouter`. |
| 3 | Spring Boot cold start 177-198s on Render | Every restart in logs | Free tier constraints + JPA metadata init. UptimeRobot now keeps warm after fix #2 above ships. Need JPA warmup `ApplicationReadyEvent` listener to run 1 dummy query. |
| 4 | Groq SSE parsing may be fragile | Hasn't been tested live since model fix | `GroqProvider.doStream()` uses `.bodyToFlux(String.class)` which may not align with SSE line boundaries. Test after deploy; if broken, switch to `ServerSentEvent` parser. |

## 4. Architecture — LLM chain (production reality)

**In production:** Groq is primary (chat). Sarvam is STT + translation only, but still wired as a chat fallback. Ollama is **disabled** via `ollama.enabled=false` but still iterated in [LLMFallbackChain.java](src/main/java/com/wnsai/wms_bot/ai/LLMFallbackChain.java) — logs "Tier 1 failed" every request.

**Clean-up candidate (not urgent):** gate `OllamaProvider` with `@Profile("!production")` OR make `LLMFallbackChain` skip providers where `isEnabled()==false`. Either removes log noise + a few ms per request.

## 5. SSE event contract (frozen — do not change without FE migration)

| Type | Fields populated | Render as |
|---|---|---|
| `TOKEN` | `content` | Append to bubble |
| `INSTANT` | `content`, `responseType`, (`data`, `suggestions`) | Full bubble |
| `NAVIGATION` | `content` (label), `route` | Go-to card + nav |
| `ACCESS_DENIED` | `content`, `data[{label,route}]`, `responseType="ACTION"` | Warn card |
| `DONE` | `intent`, `aiProvider`, `processingTimeMs` | Close stream |
| `ERROR` | `content` (code: `AI_OFFLINE`, `INTERNAL_ERROR`, `SERIALIZATION_ERROR`) | Toast |

## 6. Key file map (jump here before grepping)

```
chat/orchestration/ChatOrchestratorImpl.java  — central pipeline, intent dispatch
chat/ChatController.java                       — SSE endpoint, session CRUD
chat/IntentRouter.java                         — routes intent → handler type
intent/IntentClassifierService.java            — keyword + embedding intent detect
ai/service/WMSPromptBuilder.java               — builds system prompt (role + lang + live ctx)
ai/service/PromptContextService.java           — fetches LiveWarehouseContext stats
ai/adapter/OllamaLLMAdapter.java               — unified LLM facade → fallback chain
ai/LLMFallbackChain.java                       — Ollama → Groq → Sarvam tier walker
ai/provider/GroqProvider.java                  — Groq client + circuit breaker
ai/provider/SarvamProvider.java                — Sarvam AI client + circuit breaker
ai/util/ResponseCleanerUtil.java               — strips <think>, JSON envelopes, code fences
security/RoleAccessPolicy.java                 — server-side route gate
security/JwtAuthFilter.java                    — JWT → ReactiveSecurityContextHolder
quick/QuickResponder.java                      — GREETING + QUICK_QUERY fast path
cache/GreetingResponseCache.java               — 10-language greeting cache
embedding/EmbeddingService.java                — RAG retrieval (Ollama, fails in prod — not critical)
context/ContextBuilder.java                    — assembles DB context for AI_QUERY
```

## 7. Phase 2 preview (blocked, do not start)

`SUGGEST_NAV` Yes/No confirmation for complex intents. Event shape and FE preview are already documented in [FRONTEND_CHANGES_PHASE1.md](FRONTEND_CHANGES_PHASE1.md) §5. No backend work until user greenlights.

## 8. Dev commands

- `kill-and-run.bat` — kills java.exe, runs `mvnw spring-boot:run -DskipTests`
- Seed cleanup (SQL): see comment block at bottom of [db/seed-test-data.sql](db/seed-test-data.sql)
- Health check: `curl -sS https://wms-bot-backend.onrender.com/actuator/health` — should return 200 UP after Redis-exclusion deploy.
