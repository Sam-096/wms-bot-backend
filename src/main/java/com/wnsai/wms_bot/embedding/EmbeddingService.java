package com.wnsai.wms_bot.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

/**
 * pgvector similarity search for RAG.
 * Only invoked for AI_QUERY + UNKNOWN intents.
 *
 * Flow:
 *  1. Generate embedding via Ollama /api/embeddings
 *  2. Query pgvector for top-3 similar FAQ/doc chunks
 *  3. Return formatted context string injected into prompt
 */
@Slf4j
@Service
public class EmbeddingService {

    private final WebClient ollamaClient;
    private final String embeddingModel;

    @Autowired(required = false)
    private JdbcTemplate jdbc;

    public EmbeddingService(
            @Value("${ollama.base-url:http://localhost:11434}") String ollamaBaseUrl,
            @Value("${ollama.model:llama3.2:3b}") String model) {
        this.embeddingModel = model;
        this.ollamaClient = WebClient.builder().baseUrl(ollamaBaseUrl).build();
    }

    /**
     * Find top-3 relevant FAQ/doc chunks for the given query.
     * Returns empty string if DB not connected or Ollama offline.
     */
    public Mono<String> findRelevantDocs(String query) {
        return generateEmbedding(query)
            .flatMap(vector -> Mono.fromCallable(() -> searchPgVector(vector))
                .subscribeOn(Schedulers.boundedElastic()))
            .onErrorResume(e -> {
                log.warn("RAG lookup failed: {}", e.getMessage());
                return Mono.just("");
            });
    }

    // ─── Ollama Embedding ─────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Mono<float[]> generateEmbedding(String text) {
        return ollamaClient.post()
            .uri("/api/embeddings")
            .bodyValue(Map.of("model", embeddingModel, "prompt", text))
            .retrieve()
            .bodyToMono(Map.class)
            .map(body -> {
                List<Number> raw = (List<Number>) body.get("embedding");
                float[] vec = new float[raw.size()];
                for (int i = 0; i < raw.size(); i++) vec[i] = raw.get(i).floatValue();
                return vec;
            })
            .onErrorResume(e -> {
                log.warn("Ollama embedding failed: {}", e.getMessage());
                return Mono.just(new float[0]);
            });
    }

    // ─── pgvector Search ──────────────────────────────────────────────────────

    private String searchPgVector(float[] vector) {
        if (jdbc == null || vector.length == 0) return "";

        try {
            String pgVec = toPgVectorString(vector);
            // TODO: update table/column names to match your schema
            List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT content, source
                FROM wms_embeddings
                ORDER BY embedding <-> ?::vector
                LIMIT 3
                """, pgVec);

            if (rows.isEmpty()) return "";

            StringBuilder sb = new StringBuilder("═══ RELATED DOCS (RAG) ═══\n");
            for (Map<String, Object> row : rows) {
                sb.append(String.format("• [%s] %s\n",
                    row.get("source"), row.get("content")));
            }
            sb.append("═══════════════════════════");
            return sb.toString();

        } catch (Exception e) {
            log.warn("pgvector search failed: {}", e.getMessage());
            return "";
        }
    }

    private String toPgVectorString(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vec[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
