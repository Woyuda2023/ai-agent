package com.example.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
import java.util.List;

/**
 * 重排序服务：调用 Ollama /api/rerank（模型如 bge-reranker-v2-m3），
 * 对向量检索候选按相关性精排，取 TopN。
 * 调用失败时降级为原始顺序，保证链路可用。
 */
@Slf4j
@Service
public class RerankService {

    private final RestClient restClient;

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${rag.rerank.enabled:true}")
    private boolean enabled;

    @Value("${rag.rerank.model:bge-reranker-v2-m3}")
    private String model;

    public RerankService() {
        this.restClient = RestClient.create();
    }

    public List<Document> rerank(String query, List<Document> candidates, int topN) {
        if (!enabled || candidates.isEmpty()) {
            return candidates.stream().limit(topN).toList();
        }
        try {
            RerankRequest request = new RerankRequest(
                    model,
                    query,
                    candidates.stream().map(Document::getText).toList());

            RerankResponse response = restClient.post()
                    .uri(ollamaBaseUrl + "/api/rerank")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(RerankResponse.class);

            if (response == null || response.results() == null || response.results().isEmpty()) {
                return candidates.stream().limit(topN).toList();
            }

            return response.results().stream()
                    .sorted(Comparator.comparingDouble(RerankResult::relevanceScore).reversed())
                    .limit(topN)
                    .map(r -> candidates.get(r.index()))
                    .toList();
        } catch (Exception e) {
            log.warn("Rerank 调用失败，降级为原始顺序: {}", e.getMessage());
            return candidates.stream().limit(topN).toList();
        }
    }

    /** Ollama /api/rerank 请求体 */
    public record RerankRequest(String model, String query, List<String> documents) {
    }

    /** Ollama /api/rerank 响应体 */
    public record RerankResponse(List<RerankResult> results) {
    }

    public record RerankResult(int index, double relevanceScore) {
    }
}
